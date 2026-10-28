package com.strata.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** WHAT THE VEGETATION RULES DO TO EACH BIOME. */
public final class VegetationPlan {

    private VegetationPlan() {
    }

    /** One biome's change: the features it gains and the ones it loses. */
    public record Change(List<String> add, List<String> remove) {
    }

    /**
     * Each biome's change, for the biomes a rule reaches.
     *
     * @param tierOfBiome each configured biome's tier
     * @param tierRules each tier's rules: feature to on or off
     * @param biomeRules each biome's own rules
     * @param gameVegetation each biome's plant features in the game
     */
    public static Map<String, Change> plan(Map<String, String> tierOfBiome, Map<String, Map<String, Boolean>> tierRules,
                                           Map<String, Map<String, Boolean>> biomeRules,
                                           Map<String, List<String>> gameVegetation) {
        return plan(Map.of(), tierOfBiome, tierRules, biomeRules, gameVegetation);
    }

    /**
     * As above, with the global rules under every biome the game knows, then each tier's, then each biome's.
     */
    public static Map<String, Change> plan(Map<String, Boolean> globalRules, Map<String, String> tierOfBiome,
                                           Map<String, Map<String, Boolean>> tierRules,
                                           Map<String, Map<String, Boolean>> biomeRules,
                                           Map<String, List<String>> gameVegetation) {
        Map<String, Map<String, Boolean>> byBiome = new LinkedHashMap<>();
        if (!globalRules.isEmpty()) {
            gameVegetation.keySet().forEach(biome -> byBiome.computeIfAbsent(biome, k -> new LinkedHashMap<>())
                    .putAll(globalRules));
        }
        tierOfBiome.forEach((biome, tier) -> {
            Map<String, Boolean> rules = tierRules.get(tier);
            if (rules != null && !rules.isEmpty()) {
                byBiome.computeIfAbsent(biome, k -> new LinkedHashMap<>()).putAll(rules);
            }
        });
        biomeRules.forEach((biome, rules) -> byBiome.computeIfAbsent(biome, k -> new LinkedHashMap<>()).putAll(rules));
        Map<String, Change> out = new LinkedHashMap<>();
        byBiome.forEach((biome, rules) -> {
            List<String> has = gameVegetation.getOrDefault(biome, List.of());
            List<String> add = new ArrayList<>();
            List<String> remove = new ArrayList<>();
            rules.forEach((feature, on) -> {
                if (on && !has.contains(feature)) {
                    add.add(feature);
                } else if (!on && has.contains(feature)) {
                    remove.add(feature);
                }
            });
            if (!add.isEmpty() || !remove.isEmpty()) {
                out.put(biome, new Change(List.copyOf(add), List.copyOf(remove)));
            }
        });
        return out;
    }
}
