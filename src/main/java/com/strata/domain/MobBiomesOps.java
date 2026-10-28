package com.strata.domain;

import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.Resolver;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A mob's Biomes view: every biome the mob can spawn in naturally, with an On/Off for each. */
public final class MobBiomesOps {

    private MobBiomesOps() {}

    /**
     * One biome where the mob can spawn.
     *
     * @param source where the row comes from: vanilla, every biome, from a tier, or set here
     * @param on true when it spawns there now (enabled, weight above 0)
     */
    public record Row(String biome, String dimension, String category, String source,
                      int weight, int minCount, int maxCount, boolean on) {
    }

    /** A biome to look in: its id and dimension. */
    public record Place(String biome, String dimension) {
        public Place {
            dimension = dimension == null ? "" : dimension;
        }
    }

    /** The mob's rows across the biomes, sorted by dimension, then biome. */
    public static List<Row> rows(String entity, List<Place> places, Resolver resolver) {
        List<Row> out = new ArrayList<>();
        if (resolver == null || entity == null) {
            return out;
        }
        for (Place place : places) {
            for (SpawnCategory category : SpawnCategory.values()) {
                for (Spawn s : resolver.declaredSpawns(place.biome(), category)) {
                    if (!s.isVariant() && entity.equals(s.entity())) {
                        out.add(new Row(place.biome(), place.dimension(), category.id(), s.source() == null ? "vanilla" : s.source().describe(),
                                s.weight(), s.minCount(), s.maxCount(), s.enabled() && s.weight() > 0));
                    }
                }
            }
        }
        out.sort(java.util.Comparator.comparing(Row::dimension).thenComparing(Row::biome));
        return out;
    }

    /**
     * Turns the mob on or off in one biome: its plain spawn row in that biome, with the numbers the biome has
     * now.
     */
    public static WriteReport set(String entity, Row row, boolean on) {
        return Commit.reloadIfOk(new WriteReport().record("the spawn row of " + entity + " in " + row.biome(),
                write(com.strata.data.Paths.table(Schema.MobSpawns.FILE), entity, row, on)));
    }

    /** Several rows at once, one reload: all off when any is on, else all on. */
    public static WriteReport toggle(String entity, List<Row> rows) {
        boolean on = rows.stream().noneMatch(Row::on);
        WriteReport report = new WriteReport();
        for (Row row : rows) {
            if (!report.ok()) {
                break;
            }
            report.record("the spawn row of " + entity + " in " + row.biome(),
                    write(com.strata.data.Paths.table(Schema.MobSpawns.FILE), entity, row, on));
        }
        return Commit.reloadIfOk(report);
    }

    /** Writes the mob's plain spawn row in the row's biome to this table. */
    public static boolean write(java.nio.file.Path table, String entity, Row row, boolean on) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobSpawns.SCOPE, "biome");
        keys.put(Schema.MobSpawns.SCOPE_ID, VariantOps.normalScopeId("biome", row.biome()));
        keys.put(Schema.MobSpawns.ENTITY, entity);
        keys.put(Schema.MobSpawns.VARIANT_NAME, "");
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.MobSpawns.CATEGORY, row.category());
        // A row the game gave weight 0 needs a weight to turn on.
        values.put(Schema.MobSpawns.WEIGHT, String.valueOf(on && row.weight() <= 0 ? 1 : row.weight()));
        values.put(Schema.MobSpawns.MIN_COUNT, String.valueOf(row.minCount()));
        values.put(Schema.MobSpawns.MAX_COUNT, String.valueOf(row.maxCount()));
        values.put(Schema.MobSpawns.ENABLED, String.valueOf(on));
        values.put(Schema.MobSpawns.IS_VARIANT, "false");
        return TableWriter.upsert(table, keys, MobOps.stampDesigner(values));
    }
}
