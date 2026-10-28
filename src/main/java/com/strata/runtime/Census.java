package com.strata.runtime;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.strata.Strata;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** THE CENSUS. */
public final class Census {

    private Census() {
    }

    /** On server start, when the flag is on. */
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("strata.census")) {
            return;
        }
        try {
            write(event.getServer(), event.getServer().getServerDirectory().resolve("census"));
        } catch (Throwable t) {
            Strata.LOGGER.error("Census failed", t);
        }
    }

    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void write(MinecraftServer server, Path dir) throws IOException {
        Files.createDirectories(dir);
        RegistryAccess access = server.registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        List<String> summary = new ArrayList<>();

        // ---- every registry the server holds, built-in and data-driven, with its entries and tags
        Map<String, Codec<?>> codecs = new TreeMap<>();
        DataPackRegistriesHooks.getDataPackRegistriesWithDimensions()
                .forEach(d -> codecs.put(d.key().location().toString(), d.elementCodec()));
        Path registries = Files.createDirectories(dir.resolve("registries"));
        Path data = Files.createDirectories(dir.resolve("data"));
        summary.add("# REGISTRIES: id | entries | tags | data-driven (each entry dumped as JSON under data/)");
        access.registries().sorted((a, b) -> a.key().location().toString().compareTo(b.key().location().toString()))
                .forEach(entry -> {
                    String id = entry.key().location().toString();
                    Registry<?> reg = entry.value();
                    Codec<?> codec = codecs.get(id);
                    summary.add(id + " | " + reg.size() + " | " + reg.getTagNames().count() + " | " + (codec != null));
                    List<String> lines = new ArrayList<>();
                    reg.keySet().stream().map(Object::toString).sorted().forEach(lines::add);
                    reg.getTags().forEach(pair -> lines.add("#" + pair.getFirst().location() + " = "
                            + pair.getSecond().stream().map(h -> h.unwrapKey().map(k -> k.location().toString())
                            .orElse("?")).sorted().toList()));
                    try {
                        writeLines(registries.resolve(file(id) + ".txt"), lines);
                        if (codec != null) {
                            dumpEntries(data.resolve(file(id)), reg, codec, ops);
                        }
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });

        // ---- the datapack content that is not a registry: recipes by type, advancements, loot tables
        List<String> reloadable = new ArrayList<>();
        Map<String, Integer> recipeTypes = new TreeMap<>();
        server.getRecipeManager().getRecipes().forEach(h ->
                recipeTypes.merge(String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(h.value().getType())), 1, Integer::sum));
        recipeTypes.forEach((k, v) -> reloadable.add("recipe type " + k + " | " + v));
        reloadable.add("advancements | " + server.getAdvancements().getAllAdvancements().size());
        reloadable.add("loot tables | " + server.reloadableRegistries().getKeys(net.minecraft.core.registries.Registries.LOOT_TABLE).size());
        writeLines(dir.resolve("reloadable.txt"), reloadable);
        summary.add("# RECIPES, ADVANCEMENTS, LOOT TABLES: reloadable.txt");

        // ---- the game rules, their kind and the value this world has
        List<String> rules = new ArrayList<>();
        GameRules gameRules = server.getGameRules();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                rules.add(key.getId() + " | " + key.getCategory() + " | " + gameRules.getRule(key).serialize());
            }
        });
        rules.sort(null);
        writeLines(dir.resolve("game_rules.txt"), rules);
        summary.add("# GAME RULES: " + rules.size());

        // ---- the world's own saved settings: level.dat as the game writes it
        if (server.getWorldData() instanceof PrimaryLevelData level) {
            Files.writeString(dir.resolve("level_dat.snbt"),
                    net.minecraft.nbt.NbtUtils.structureToSnbt(level.createTag(access, null)), StandardCharsets.UTF_8);
            summary.add("# WORLD SETTINGS: level_dat.snbt");
        }

        // ---- every item's components, as the game encodes them; and which component kinds occur
        Path items = Files.createDirectories(dir.resolve("items"));
        Map<String, Integer> componentUse = new TreeMap<>();
        Map<String, JsonElement> itemJson = new TreeMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            String id = String.valueOf(BuiltInRegistries.ITEM.getKey(item));
            item.components().keySet().forEach(t -> componentUse.merge(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(t)), 1, Integer::sum));
            itemJson.put(id, net.minecraft.core.component.DataComponentMap.CODEC.encodeStart(ops, item.components())
                    .result().orElse(null));
        }
        Files.writeString(items.resolve("components.json"), GSON.toJson(itemJson), StandardCharsets.UTF_8);
        List<String> used = new ArrayList<>();
        componentUse.forEach((k, v) -> used.add(k + " | on " + v + " item(s)"));
        writeLines(items.resolve("component_kinds.txt"), used);
        summary.add("# ITEM COMPONENT KINDS IN USE: " + componentUse.size() + " of "
                + BuiltInRegistries.DATA_COMPONENT_TYPE.size() + " registered");

        // ---- every block's behaviour properties and state properties
        List<String> blocks = new ArrayList<>();
        Field[] behaviour = fields(BlockBehaviour.class);
        Field[] props = fields(BlockBehaviour.Properties.class);
        blocks.add("# fields of BlockBehaviour: " + names(behaviour));
        blocks.add("# fields of BlockBehaviour.Properties: " + names(props));
        for (Block block : BuiltInRegistries.BLOCK) {
            StringBuilder line = new StringBuilder(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));
            for (Field f : behaviour) {
                line.append(" | ").append(f.getName()).append('=').append(value(f, block));
            }
            line.append(" | states=").append(block.getStateDefinition().getProperties().stream()
                    .map(p -> p.getName() + p.getPossibleValues()).toList());
            blocks.add(line.toString());
        }
        writeLines(dir.resolve("blocks.txt"), blocks);
        summary.add("# BLOCKS: " + BuiltInRegistries.BLOCK.size());

        // ---- every mob's fields, every kind, from the mobs the extraction made
        Map<String, TreeSet<String>> mobFields = new TreeMap<>();
        List<net.minecraft.world.entity.Entity> made = new ArrayList<>();
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            try {
                var e = type.create(server.overworld());
                if (e != null) {
                    made.add(e);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A type that cannot be made outside its own setup (a player, a fishing hook).
            }
        }
        made.forEach(mob -> {
            for (Class<?> c = mob.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                        continue;
                    }
                    mobFields.computeIfAbsent(c.getSimpleName(), k -> new TreeSet<>())
                            .add(f.getName() + " : " + f.getType().getSimpleName()
                                    + (com.strata.data.GameLayer.simple(f.getType()) ? "" : "   (NOT EXTRACTED)"));
                }
            }
        });
        List<String> mobs = new ArrayList<>();
        mobFields.forEach((cls, fs) -> {
            mobs.add(cls);
            fs.forEach(f -> mobs.add("  " + f));
        });
        writeLines(dir.resolve("mob_fields.txt"), mobs);
        made.forEach(net.minecraft.world.entity.Entity::discard);
        summary.add("# ENTITY CLASSES: " + mobFields.size() + " (fields by declaring class in mob_fields.txt)");

        // ---- every entity type's own settings
        List<String> types = new ArrayList<>();
        Field[] typeFields = fields(net.minecraft.world.entity.EntityType.class);
        types.add("# fields of EntityType: " + names(typeFields));
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            StringBuilder line = new StringBuilder(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
            for (Field f : typeFields) {
                line.append(" | ").append(f.getName()).append('=').append(value(f, type));
            }
            types.add(line.toString());
        }
        writeLines(dir.resolve("entity_types.txt"), types);

        // ---- what is editable and has no editor in Strata
        List<String> missing = Coverage.missing(server);
        writeLines(dir.resolve("coverage.txt"), missing.isEmpty() ? List.of("Everything editable has an editor.") : missing);
        summary.add("# COVERAGE: " + (missing.isEmpty() ? "everything editable has an editor" : missing.size() + " gap(s) in coverage.txt"));

        List<String> audit = new ArrayList<>();
        Coverage.audit(server).forEach(c -> audit.add(c.line()));
        writeLines(dir.resolve("audit.txt"), audit);

        writeLines(dir.resolve("SUMMARY.txt"), summary);
        Strata.LOGGER.info("Census written to {}", dir.toAbsolutePath());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dumpEntries(Path dir, Registry<?> reg, Codec<?> codec, RegistryOps<JsonElement> ops) throws IOException {
        Map<String, JsonElement> out = new TreeMap<>();
        for (var key : reg.registryKeySet()) {
            Object value = reg.get((ResourceKey) key);
            JsonElement json;
            try {
                json = (JsonElement) ((Codec) codec).encodeStart(ops, value).result().orElse(null);
            } catch (RuntimeException e) {
                json = new com.google.gson.JsonPrimitive("could not encode: " + e);
            }
            out.put(key.location().toString(), json);
        }
        Files.createDirectories(dir.getParent());
        Files.writeString(dir.resolveSibling(dir.getFileName() + ".json"), GSON.toJson(out), StandardCharsets.UTF_8);
    }

    private static Field[] fields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out.toArray(Field[]::new);
    }

    private static String names(Field[] fs) {
        List<String> out = new ArrayList<>();
        for (Field f : fs) {
            out.add(f.getName() + ":" + f.getType().getSimpleName());
        }
        return String.join(", ", out);
    }

    private static String value(Field f, Object o) {
        try {
            Object v = f.get(o);
            String s = String.valueOf(v);
            return s.length() > 120 ? s.substring(0, 120) + "..." : s;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }

    private static String file(String id) {
        return id.replace(':', '_').replace('/', '_');
    }

    private static void writeLines(Path file, List<String> lines) throws IOException {
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
