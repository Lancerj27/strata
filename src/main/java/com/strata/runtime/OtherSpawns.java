package com.strata.runtime;

import com.strata.data.CsvTable;
import com.strata.data.Schema;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** WHETHER A REPLACE BIOME BLOCKS THE SPAWNS NOT FROM ITS LIST. */
public final class OtherSpawns {

    private OtherSpawns() {
    }

    /** Each tier's setting, by name; only the ones set. */
    private static volatile Map<String, Boolean> byTier = Map.of();
    /** Each biome's setting, by id; only the ones set. */
    private static volatile Map<String, Boolean> byBiome = Map.of();

    /** Reads the settings from tiers.csv and biome_overrides.csv. */
    public static void load(Path tiersFile, Path biomeOverridesFile) {
        byTier = index(CsvTable.read(tiersFile), Schema.Tiers.NAME, Schema.Tiers.BLOCK_OTHER_SPAWNS);
        byBiome = index(CsvTable.read(biomeOverridesFile), Schema.BiomeOverrides.BIOME,
                Schema.BiomeOverrides.BLOCK_OTHER_SPAWNS);
    }

    /**
     * The values the old columns hold, "tier|name" or "biome|id" to the value: what AreaRules copies into
     * area_rules.csv once.
     */
    static Map<String, Boolean> legacy() {
        Map<String, Boolean> out = new HashMap<>();
        byTier.forEach((name, v) -> out.put("tier|" + name, v));
        byBiome.forEach((id, v) -> out.put("biome|" + id, v));
        return out;
    }

    /** The set values of one column, by the row's key. */
    public static Map<String, Boolean> index(List<Map<String, String>> rows, String keyColumn, String valueColumn) {
        Map<String, Boolean> out = new HashMap<>();
        for (Map<String, String> row : rows) {
            String key = row.getOrDefault(keyColumn, "").strip();
            Boolean value = cell(row.get(valueColumn));
            if (!key.isEmpty() && value != null) {
                out.put(key, value);
            }
        }
        return Map.copyOf(out);
    }

    /** A cell as a setting: true, false, or null for unset. */
    public static Boolean cell(String raw) {
        String v = raw == null ? "" : raw.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (v) {
            case "true", "on", "yes" -> Boolean.TRUE;
            case "false", "off", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** The biome's setting, else its tier's, else the global option. */
    public static boolean decide(boolean global, Boolean tier, Boolean biome) {
        return biome != null ? biome : tier != null ? tier : global;
    }

    /** As above, for a biome and its tier: the Rules screen's values (AreaRules) since. */
    public static boolean blocks(boolean global, String tierName, String biomeId) {
        return AreaRules.on(AreaRules.BLOCK_OTHER_SPAWNS, global, tierName, biomeId);
    }
}
