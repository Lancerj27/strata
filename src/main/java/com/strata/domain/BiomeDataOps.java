package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.runtime.BiomeValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the biome data editor reads and does: a biome's creature probability and each spawn cost's
 * charge and energy budget, the game's from the game layer (game/spawns.csv) with the user's from
 * biome_data.csv on top.
 */
public final class BiomeDataOps {

    private BiomeDataOps() {
    }

    /**
     * One value of a biome.
     *
     * @param group {@link BiomeValues#BIOME} or {@link BiomeValues#COST}
     * @param key creature_probability, or entity|charge, entity|energy_budget
     */
    public record Value(String group, String key, String game, String own) {
        public String effective() {
            return own.isEmpty() ? game : own;
        }
    }

    public static List<Value> values(String biome) {
        Map<String, String> own = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BiomeData.FILE))) {
            if (biome.equals(row.getOrDefault(Schema.BiomeData.BIOME, "").strip())) {
                own.put(row.getOrDefault(Schema.BiomeData.GROUP, "").strip() + "|"
                        + row.getOrDefault(Schema.BiomeData.KEY, "").strip(),
                        row.getOrDefault(Schema.BiomeData.VALUE, "").strip());
            }
        }
        return values(GameLayer.read(GameLayer.dir().resolve(GameLayer.SPAWNS)), biome, own);
    }

    /**
     * The biome's values from the spawning sheet, the user's on top: the creature probability, then for each
     * spawn cost its charge and its energy budget.
     */
    public static List<Value> values(List<GameLayer.Row> sheet, String biome, Map<String, String> own) {
        List<Value> out = new ArrayList<>();
        for (GameLayer.Row r : sheet) {
            if (!r.entity().equals(biome)) {
                continue;
            }
            if (GameLayer.BIOME.equals(r.group())) {
                out.add(value(BiomeValues.BIOME, r.key(), r.value(), own));
            } else if (GameLayer.COST.equals(r.group())) {
                String[] parts = r.value().split("\\|");
                if (parts.length == 2) {
                    out.add(value(BiomeValues.COST, r.key() + "|" + BiomeValues.CHARGE, parts[0], own));
                    out.add(value(BiomeValues.COST, r.key() + "|" + BiomeValues.BUDGET, parts[1], own));
                }
            }
        }
        return out;
    }

    private static Value value(String group, String key, String game, Map<String, String> own) {
        return new Value(group, key, game, own.getOrDefault(group + "|" + key, ""));
    }

    /** Why a typed value cannot be saved, or null: a number 0 or more, a probability below 1. */
    public static String problem(Value v, String typed) {
        Double n = BiomeValues.number(typed == null ? "" : typed.strip(), null);
        if (n == null) {
            return "The value must be a number, 0 or more";
        }
        return v.key().equals(BiomeValues.PROBABILITY) && n >= 1 ? "A probability must be below 1" : null;
    }

    /** What a value does, in words. */
    public static String what(Value v) {
        if (v.key().equals(BiomeValues.PROBABILITY)) {
            return "The chance a new chunk of this biome gets its animals as it is made (0 to below 1).";
        }
        return v.key().endsWith("|" + BiomeValues.CHARGE)
                ? "How much crowding one of these mobs makes around it. 0: it makes none."
                : "A new one spawns only where the crowding it would feel stays at or under this.";
    }

    /** The entity a spawn cost value belongs to, or "" for the creature probability. */
    public static String entityOf(Value v) {
        int bar = v.key().lastIndexOf('|');
        return v.key().equals(BiomeValues.PROBABILITY) || bar < 0 ? "" : v.key().substring(0, bar);
    }

    /** A value's own name: "Creature probability", "Spawn cost: charge". */
    public static String name(Value v) {
        if (v.key().equals(BiomeValues.PROBABILITY)) {
            return "Creature probability";
        }
        return v.key().endsWith("|" + BiomeValues.CHARGE) ? "Spawn cost: charge" : "Spawn cost: energy budget";
    }

    public static WriteReport save(String biome, Value v, String typed) {
        String problem = problem(v, typed);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return Commit.reloadIfOk(new WriteReport().record("the biome value", TableWriter.upsert(Schema.BiomeData.FILE,
                keys(biome, v), Map.of(Schema.BiomeData.VALUE, typed.strip()))));
    }

    /** Removes the user's value, so the game's applies again. */
    public static WriteReport restore(String biome, Value v) {
        return Commit.reloadIfOk(new WriteReport().cascaded("your biome value",
                TableWriter.delete(Schema.BiomeData.FILE, keys(biome, v))));
    }

    private static Map<String, String> keys(String biome, Value v) {
        return Map.of(Schema.BiomeData.BIOME, biome, Schema.BiomeData.GROUP, v.group(), Schema.BiomeData.KEY, v.key());
    }
}
