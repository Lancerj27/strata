package com.strata.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.strata.Strata;
import com.strata.model.DefaultEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a mob's OWN default loot table so the UI can show what Minecraft gives a creature without requiring a
 * singleplayer server.
 */
public final class VanillaData {

    private VanillaData() {}

    private static final Map<String, List<LootItem>> LOOT_CACHE = new ConcurrentHashMap<>();

    /** One item of a mob's default loot table. */
    public record LootItem(String item, int countMin, int countMax) {
        public String display() {
            return Labels.itemName(item)
                    + (countMin == countMax && countMin == 1 ? "" : " x" + countMin + (countMax == countMin ? "" : "-" + countMax));
        }
    }

    /** Every item the game's default equipment can give a mob: its items and each armor piece. */
    private static java.util.Set<String> equipmentItems(String entityId) {
        DefaultEquipment roll = DefaultEquipmentRuntime.gameRoll(entityId);
        java.util.Set<String> out = new java.util.HashSet<>();
        if (roll == null) {
            return out;
        }
        roll.items().forEach(i -> out.add(i.item()));
        for (String slot : DefaultEquipment.ARMOR_SLOTS) {
            roll.materials().forEach((m, w) -> {
                if (w > 0 && roll.armorChance() > 0) {
                    out.add(DefaultEquipment.armorItem(m, slot));
                }
            });
        }
        return out;
    }

    /** Default loot-table drops for an entity, from Minecraft's own data. */
    public static List<LootItem> lootItems(String entityId, Minecraft client) {
        return LOOT_CACHE.computeIfAbsent(entityId, id -> parseLoot(id, client));
    }

    private static List<LootItem> parseLoot(String entityId, Minecraft client) {
        JsonObject json = entityLootJson(entityId, client);
        if (json == null) return List.of();

        JsonArray pools = json.has("pools") ? json.getAsJsonArray("pools") : null;
        if (pools == null) return List.of();

        List<LootItem> drops = new ArrayList<>();
        for (JsonElement poolEl : pools) {
            if (!poolEl.isJsonObject()) continue;
            JsonArray entries = poolEl.getAsJsonObject().has("entries")
                    ? poolEl.getAsJsonObject().getAsJsonArray("entries") : null;
            if (entries == null) continue;
            collectItemEntries(entries, drops);
        }
        java.util.Set<String> equipItems = equipmentItems(entityId);
        if (!equipItems.isEmpty()) {
            drops.removeIf(d -> equipItems.contains(d.item()));
        }
        return List.copyOf(drops);
    }

    private static void collectItemEntries(JsonArray entries, List<LootItem> out) {
        for (JsonElement entryEl : entries) {
            if (!entryEl.isJsonObject()) continue;
            JsonObject entry = entryEl.getAsJsonObject();
            String type = entry.has("type") ? entry.get("type").getAsString() : "";
            if (type.endsWith("item")) {
                String item = findItemName(entry);
                if (item != null) {
                    int[] counts = countRange(entry);
                    out.add(new LootItem(item, counts[0], counts[1]));
                }
            }
            // Nested entry types (alternatives/sequence/group) carry items in children/values arrays —
            // recurse so the count is complete.
            JsonElement children = entry.get("children");
            if (children != null && children.isJsonArray()) {
                collectItemEntries(children.getAsJsonArray(), out);
            }
            JsonElement values = entry.get("values");
            if (values != null && values.isJsonArray()) {
                collectItemEntries(values.getAsJsonArray(), out);
            }
        }
    }

    /** Parses the set_count function of an item entry into {min, max}. */
    private static int[] countRange(JsonObject entry) {
        JsonElement functions = entry.get("functions");
        if (functions != null && functions.isJsonArray()) {
            for (JsonElement fnEl : functions.getAsJsonArray()) {
                if (!fnEl.isJsonObject()) continue;
                JsonObject fn = fnEl.getAsJsonObject();
                String fnName = fn.has("function") ? fn.get("function").getAsString() : "";
                if (fnName.endsWith("set_count") && fn.has("count")) {
                    JsonElement count = fn.get("count");
                    if (count.isJsonPrimitive()) {
                        try {
                            int single = count.getAsInt();
                            return new int[]{single, single};
                        } catch (NumberFormatException e) {
                            Strata.LOGGER.debug("Loot count was not a plain int: {}", e.toString());
                        }
                    } else if (count.isJsonObject()) {
                        try {
                            int min = count.getAsJsonObject().get("min").getAsInt();
                            int max = count.getAsJsonObject().get("max").getAsInt();
                            return new int[]{min, max};
                        } catch (NumberFormatException | IllegalStateException e) {
                            Strata.LOGGER.debug("Loot count min/max unreadable: {}", e.toString());
                        }
                    }
                }
            }
        }
        return new int[]{1, 1};
    }

    private static String findItemName(JsonObject entryObj) {
        for (String key : new String[]{"name", "value", "item"}) {
            if (entryObj.has(key) && entryObj.get(key).isJsonPrimitive()) {
                String val = entryObj.get(key).getAsString();
                if (val.contains(":")) return val;
            }
        }
        for (java.util.Map.Entry<String, JsonElement> e : entryObj.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                String val = e.getValue().getAsString();
                if (val.contains(":") && !e.getKey().equals("type")) return val;
            }
        }
        return null;
    }

    private static JsonObject entityLootJson(String entityId, Minecraft client) {
        ResourceLocation entityRl = ResourceLocation.tryParse(entityId);
        if (entityRl == null) {
            Strata.LOGGER.warn("VanillaData: invalid entity id '{}'", entityId);
            return null;
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityRl)) {
            Strata.LOGGER.warn("VanillaData: entity '{}' not in registry", entityId);
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityRl);

        ResourceKey<LootTable> lootKey = type.getDefaultLootTable();
        if (lootKey == null) {
            Strata.LOGGER.warn("VanillaData: entity '{}' has no default loot table", entityId);
            return null;
        }
        ResourceLocation lootLoc = lootKey.location();
        Strata.LOGGER.debug("VanillaData: entity '{}' loot key = {}", entityId, lootLoc);

        // Path A: live reloadable registries (works when a world is loaded).
        JsonObject serverJson = serverLootJson(lootKey);
        if (serverJson != null) return serverJson;

        // Path B: read directly from the MC jar on the classpath.
        String cpPath = "/data/" + lootLoc.getNamespace()
                + "/loot_table/" + lootLoc.getPath() + ".json";
        JsonObject cpJson = classpathJson(cpPath);
        if (cpJson != null) return cpJson;

        // Path B2: plural variant for older data pack layouts.
        cpJson = classpathJson("/data/" + lootLoc.getNamespace()
                + "/loot_tables/" + lootLoc.getPath() + ".json");
        if (cpJson != null) return cpJson;

        Strata.LOGGER.warn("VanillaData: all paths failed for entity '{}'", entityId);
        return null;
    }

    /**
     * A loot table from the live server's reloadable registries, encoded back to JSON exactly as the original
     * GameData#lootTableItems did it.
     */
    private static JsonObject serverLootJson(ResourceKey<LootTable> lootKey) {
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            LootTable table = server.reloadableRegistries().getLootTable(lootKey);
            if (table == null || table == LootTable.EMPTY) return null;
            JsonElement encoded = LootTable.DIRECT_CODEC.encodeStart(
                    server.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                    table).result().orElse(null);
            if (encoded != null && encoded.isJsonObject()) {
                return encoded.getAsJsonObject();
            }
        } catch (Exception e) {
            Strata.LOGGER.warn("VanillaData: server loot path failed for {}: {}",
                    lootKey.location(), e.getMessage());
        }
        return null;
    }

private static JsonObject classpathJson(String path) {
        InputStream stream = Minecraft.class.getResourceAsStream(path);
        if (stream == null) {
            stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
        }
        if (stream == null) {
            Strata.LOGGER.debug("VanillaData: classpath miss {}", path);
            return null;
        }
        try (InputStream in = stream;
             Reader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                Strata.LOGGER.debug("VanillaData: loaded from classpath {}", path);
                return parsed.getAsJsonObject();
            }
        } catch (Exception e) {
            Strata.LOGGER.debug("VanillaData: classpath read failed {}: {}", path, e.getMessage());
        }
        return null;
    }

    /** Item display name helper (avoids a hard dependency on the screen Labels util). */
    private static final class Labels {
        static String itemName(String id) {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    Item item = BuiltInRegistries.ITEM.get(rl);
                    return item.getName(ItemStack.EMPTY).getString();
                }
            } catch (RuntimeException e) {
                // Narrowed from Exception.
                Strata.LOGGER.debug("No item name for {} - using the id: {}", id, e.toString());
            }
            String s = id;
            int c = s.indexOf(':');
            if (c >= 0) s = s.substring(c + 1);
            return s.replace('_', ' ');
        }

        static String pretty(String id) {
            String s = id;
            int c = s.indexOf(':');
            if (c >= 0) s = s.substring(c + 1);
            String[] parts = s.split("_");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(' ');
                String p = parts[i];
                sb.append(p.isEmpty() ? p : Character.toUpperCase(p.charAt(0))
                        + p.substring(1));
            }
            return sb.toString();
        }
    }
}
