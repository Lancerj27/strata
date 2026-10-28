package com.strata.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The answer for one biome: what level it is, what spawns there, what it generates, and where each of those
 * came from.
 *
 * @param biome biome id
 * @param tier tier name, or null
 * @param level 0 means vanilla
 * @param levelSource where the level came from
 * @param mobs resolved spawns per category
 * @param ores resolved ore names
 * @param oreFrequencies frequency_pct per ore (only ores a rule shaped; anything absent keeps its normal
 *     density)
 * @param oresOverridden whether any tier or biome rule shaped that list
 * @param variants variants offered here
 * @param loot the resolved loot entries (global + tier + biome)
 * @param coefficient resolved mob coefficient for this biome (default 1.0)
 * @param scaleMobs per-biome scaling override
 * @param restrictOres per-biome ore restriction override
 * @param substituteSpawns per-biome spawn substitution override
 * @param chunkScale resolved chunk scaling: how much the chunk's local difficulty compounds the level (0 =
 *     off)
 */
public record Resolution(
        String biome,
        String tier,
        int level,
        Source levelSource,
        Map<SpawnCategory, List<Spawn>> mobs,
        List<String> ores,
        Map<String, Double> oreFrequencies,
        boolean oresOverridden,
        List<Variant> variants,
        List<MobLoot> loot,
        double coefficient,
        Boolean scaleMobs,
        Boolean restrictOres,
        Boolean substituteSpawns,
        double chunkScale
) {

    public static Resolution untouched(String biome, String tier) {
        return new Resolution(biome, tier, 0, Source.NONE,
                new EnumMap<>(SpawnCategory.class), List.of(), Map.of(), false,
                List.of(), List.of(), 1.0, null, null, null, 0.0);
    }

    public boolean scaled() {
        return level > 0;
    }

    public boolean allows(Boolean perBiome, boolean globalDefault) {
        return perBiome == null ? globalDefault : perBiome;
    }

    public boolean configured() {
        if (tier != null || level > 0 || oresOverridden) {
            return true;
        }
        for (List<Spawn> list : mobs.values()) {
            for (Spawn spawn : list) {
                if (spawn.source().layer() != Source.Layer.GAME) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Spawn> mobs(SpawnCategory category) {
        return mobs.getOrDefault(category, List.of());
    }

    public String describeLevel() {
        if (scaled()) {
            return "level " + level + " (" + levelSource.describe() + ")";
        }
        return configured()
                ? "level 0 - not scaled, but its spawns and ores still apply"
                : "level 0 - nothing set";
    }
}
