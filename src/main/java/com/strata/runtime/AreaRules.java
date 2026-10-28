package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A TIER'S OR A BIOME'S SPAWN RULES: area_rules.csv, edited on the Rules screen of a tier or a biome. */
public final class AreaRules {

    private AreaRules() {
    }

    public static final String BLOCK_OTHER_SPAWNS = "block_other_spawns";
    public static final String MONSTERS_IN_CREATURE_SLOTS = "monsters_in_creature_slots";
    public static final String MONSTERS_IN_AMBIENT_SLOTS = "monsters_in_ambient_slots";
    public static final String MONSTERS_IN_WATER_SLOTS = "monsters_in_water_slots";
    public static final String MONSTERS_OUTSIDE_DARKNESS = "monsters_outside_darkness";
    /** Every rule, in the Rules screen's order. */
    public static final List<String> RULES = List.of(BLOCK_OTHER_SPAWNS, MONSTERS_IN_CREATURE_SLOTS,
            MONSTERS_IN_AMBIENT_SLOTS, MONSTERS_IN_WATER_SLOTS, MONSTERS_OUTSIDE_DARKNESS);

    /** "scope|id|rule" to its value; only the ones set. */
    private static volatile Map<String, Boolean> set = Map.of();

    private static String key(String scope, String id, String rule) {
        return scope + "|" + id + "|" + rule;
    }

    /** The rules as a map, from the table's rows. */
    public static Map<String, Boolean> index(List<Map<String, String>> rows) {
        Map<String, Boolean> out = new HashMap<>();
        for (Map<String, String> row : rows) {
            String scope = row.getOrDefault(Schema.AreaRules.SCOPE, "").strip();
            String id = row.getOrDefault(Schema.AreaRules.SCOPE_ID, "").strip();
            String rule = row.getOrDefault(Schema.AreaRules.RULE, "").strip();
            Boolean value = OtherSpawns.cell(row.get(Schema.AreaRules.VALUE));
            if (!scope.isEmpty() && !id.isEmpty() && RULES.contains(rule) && value != null) {
                out.put(key(scope, id, rule), value);
            }
        }
        return Map.copyOf(out);
    }

    /** Reads area_rules.csv, first copying in any Block other spawns value the old columns hold. */
    public static void load(Path file) {
        Map<String, Boolean> rows = index(CsvTable.read(file));
        try {
            OtherSpawns.legacy().forEach((scopeAndId, value) -> {
                String[] p = scopeAndId.split("\\|", 2);
                if (!rows.containsKey(key(p[0], p[1], BLOCK_OTHER_SPAWNS))) {
                    TableWriter.upsert(file, Map.of(Schema.AreaRules.SCOPE, p[0], Schema.AreaRules.SCOPE_ID, p[1],
                            Schema.AreaRules.RULE, BLOCK_OTHER_SPAWNS), Map.of(Schema.AreaRules.VALUE, String.valueOf(value)));
                }
            });
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Area rules: the old Block other spawns values could not be copied: {}", e.toString());
        }
        set = index(CsvTable.read(file));
    }

    /** The biome's rule, else its tier's, else the global switch. */
    public static boolean decide(Map<String, Boolean> values, String rule, boolean global, String tier, String biome) {
        Boolean b = biome == null ? null : values.get(key("biome", biome, rule));
        if (b != null) {
            return b;
        }
        Boolean t = tier == null ? null : values.get(key("tier", tier, rule));
        return t != null ? t : global;
    }

    /** As above, over the loaded rules. */
    public static boolean on(String rule, boolean global, String tier, String biome) {
        return decide(set, rule, global, tier, biome);
    }

    /** One place's own value of a rule, or null when it sets none. */
    public static Boolean own(String scope, String id, String rule) {
        return set.get(key(scope, id, rule));
    }
}
