package com.strata.runtime;

import com.google.gson.JsonParser;
import com.strata.Strata;
import com.strata.data.GameLayer;
import com.strata.data.LootJson;
import com.strata.data.StrataDatapack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts every mob's loot table into the game layer: game/loot.csv, one row per value (LootJson), rewritten
 * at every game start from the packs Strata opens then (MobExtractor).
 */
public final class LootExtractor {

    private LootExtractor() {
    }

    private static final String COMMENT = """
            The GAME LAYER: every loot table (mobs, chests, blocks, and the rest), one row per value, rewritten at every game start. Do not edit:
            your values go in loot_tables.csv. key: the value's place in the table's JSON. value: the JSON value.""";

    /** Every mob's table from the startup packs (at game start, not world start). */
    public static void extract(ResourceManager resources) {
        List<GameLayer.Row> rows = new ArrayList<>();
        int tables = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String mob = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).map(k -> k.location().toString()).orElse("");
            ResourceLocation table = type.getDefaultLootTable().location();
            if (mob.isEmpty()) {
                continue;
            }
            Resource source = gameSource(resources, table);
            if (source == null) {
                continue;
            }
            try (Reader reader = source.openAsReader()) {
                Map<String, String> values = LootJson.flatten(JsonParser.parseReader(reader));
                rows.add(new GameLayer.Row(mob, GameLayer.LOOT_TABLE, GameLayer.LOOT_TABLE, table.toString()));
                // Two mobs may share a table: its values once.
                if (seen.add(table.toString())) {
                    values.forEach((path, value) -> rows.add(new GameLayer.Row(table.toString(), GameLayer.LOOT_VALUE, path, value)));
                }
                tables++;
            } catch (Exception e) {
                Strata.LOGGER.debug("Loot extract: could not read {}: {}", table, e.toString());
            }
        }
        // EVERY CHEST'S TABLE: each table under chests/, of every namespace.
        int chests = 0;
        for (ResourceLocation file : resources.listResources("loot_table",
                f -> f.getPath().startsWith("loot_table/chests/") && f.getPath().endsWith(".json")).keySet()) {
            String path = file.getPath();
            ResourceLocation table = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                    path.substring("loot_table/".length(), path.length() - ".json".length()));
            Resource source = gameSource(resources, table);
            if (source == null || !seen.add(table.toString())) {
                continue;
            }
            try (Reader reader = source.openAsReader()) {
                Map<String, String> values = LootJson.flatten(JsonParser.parseReader(reader));
                rows.add(new GameLayer.Row(table.toString(), GameLayer.CHEST, GameLayer.LOOT_TABLE, table.toString()));
                values.forEach((p, value) -> rows.add(new GameLayer.Row(table.toString(), GameLayer.LOOT_VALUE, p, value)));
                chests++;
            } catch (Exception e) {
                Strata.LOGGER.debug("Loot extract: could not read {}: {}", table, e.toString());
            }
        }
        // EVERY BLOCK'S TABLE, its Drops.
        int blocks = 0;
        for (ResourceLocation file : resources.listResources("loot_table",
                f -> f.getPath().startsWith("loot_table/blocks/") && f.getPath().endsWith(".json")).keySet()) {
            String path = file.getPath();
            ResourceLocation table = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                    path.substring("loot_table/".length(), path.length() - ".json".length()));
            Resource source = gameSource(resources, table);
            if (source == null || !seen.add(table.toString())) {
                continue;
            }
            try (Reader reader = source.openAsReader()) {
                Map<String, String> values = LootJson.flatten(JsonParser.parseReader(reader));
                rows.add(new GameLayer.Row(table.toString(), GameLayer.BLOCK_LOOT, GameLayer.LOOT_TABLE, table.toString()));
                values.forEach((p, value) -> rows.add(new GameLayer.Row(table.toString(), GameLayer.LOOT_VALUE, p, value)));
                blocks++;
            } catch (Exception e) {
                Strata.LOGGER.debug("Loot extract: could not read {}: {}", table, e.toString());
            }
        }
        // EVERY OTHER TABLE: fishing, archaeology, gifts, bartering, a mod's own, so World > Loot tables
        // lists them all.
        int others = 0;
        for (ResourceLocation file : resources.listResources("loot_table", f -> f.getPath().endsWith(".json")).keySet()) {
            String path = file.getPath();
            ResourceLocation table = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                    path.substring("loot_table/".length(), path.length() - ".json".length()));
            Resource source = gameSource(resources, table);
            if (source == null || !seen.add(table.toString())) {
                continue;
            }
            try (Reader reader = source.openAsReader()) {
                LootJson.flatten(JsonParser.parseReader(reader))
                        .forEach((p, value) -> rows.add(new GameLayer.Row(table.toString(), GameLayer.LOOT_VALUE, p, value)));
                others++;
            } catch (Exception e) {
                Strata.LOGGER.debug("Loot extract: could not read {}: {}", table, e.toString());
            }
        }
        if (GameLayer.write(GameLayer.dir().resolve(GameLayer.LOOT), COMMENT, rows)) {
            Strata.LOGGER.info("Game layer: extracted {} mob, {} chest, {} block and {} other loot table(s)", tables, chests,
                    blocks, others);
        }
    }

    /** The table's file from the highest pack that is not Strata's, or null. */
    private static Resource gameSource(ResourceManager resources, ResourceLocation table) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(table.getNamespace(),
                "loot_table/" + table.getPath() + ".json");
        List<Resource> stack = resources.getResourceStack(file);
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (!StrataDatapack.NAMESPACE.equals(stack.get(i).sourcePackId())) {
                return stack.get(i);
            }
        }
        return null;
    }
}
