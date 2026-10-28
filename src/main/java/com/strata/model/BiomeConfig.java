package com.strata.model;

import java.util.List;
import java.util.Map;


/**
 * Per-biome overrides (a row in biome_overrides.csv).
 *
 * @param biome the Minecraft biome id
 * @param tier the tier name to belong to, or null if none
 * @param level per-biome level override, or null to inherit
 * @param scaleMobs whether to scale mob XP/health/damage, or null to inherit
 * @param restrictOres whether to restrict ores, or null to inherit
 * @param substituteSpawns whether to substitute spawns, or null to inherit
 * @param mobs spawn rules per category
 * @param ores ore rules (each carries its frequency_pct)
 * @param loot the loot entries for this biome
 * @param spawnRules spawn conditions scoped to this biome
 * @param coefficient per-biome mob coefficient override, or null to inherit
 */
public record BiomeConfig(
        String biome,
        String tier,
        Integer level,
        Boolean scaleMobs,
        Boolean restrictOres,
        Boolean substituteSpawns,
        Map<SpawnCategory, List<Spawn>> mobs,
        List<OreRule> ores,
        List<MobLoot> loot,
        List<SpawnRule> spawnRules,
        Double coefficient
) {

    /** A biome with no overrides: every value inherits. */
    public static BiomeConfig empty(String biome) {
        return new BiomeConfig(biome, null, null, null, null, null, Map.of(), List.of(), List.of(), List.of(), null);
    }

    /** The spawn rules for one category, empty when it has none. */
    public List<Spawn> mobs(SpawnCategory category) {
        return mobs.getOrDefault(category, List.of());
    }
}
