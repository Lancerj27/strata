package com.strata.model;

import java.util.List;
import java.util.Map;


/**
 * A vertical band of area levels that groups biomes.
 *
 * @param name the tier's name as used in tier_biomes.csv
 * @param level the area level for its biomes
 * @param spawnMode ADD keeps game spawns, REPLACE discards them
 * @param coefficient per-tier mob coefficient (default 1.0), multiplies all scaling
 * @param mobs spawn rules per category
 * @param ores ore rules (each carries its frequency_pct)
 */
public record Tier(
        String name,
        int level,
        OverrideMode spawnMode,
        double coefficient,
        Map<SpawnCategory, List<Spawn>> mobs,
        List<OreRule> ores
) {

    /** The spawn rules for one category, empty when it has none. */
    public List<Spawn> mobs(SpawnCategory category) {
        return mobs.getOrDefault(category, List.of());
    }

    public static Tier of(String name, int level) {
        return new Tier(name, level, OverrideMode.ADD, 1.0, Map.of(), List.of());
    }
}
