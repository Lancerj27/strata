package com.strata.model;

import java.util.List;

/**
 * One spawn entry: a creature that can appear in a place.
 *
 * @param category which vanilla spawn list it belongs to
 * @param entity entity id, e.g. "minecraft:skeleton"
 * @param variant variant id, or empty for the base creature
 * @param weight its share when the game picks among its category's mobs; not a chance: a mob alone on its
 *     list is always picked
 * @param minCount group size floor, 0 or more (0 spawns nothing)
 * @param maxCount group size ceiling
 * @param enabled whether this entry is active (separate from weight)
 * @param coefficient per-mob coefficient override, or null to inherit
 * @param conditions spawn conditions that must all pass for this entry
 * @param source where this entry came from
 * @param chance the chance, 0 to 100, that it spawns when the game picks it by weight; 100 for the game's
 */
public record Spawn(
        SpawnCategory category,
        String entity,
        String variant,
        int weight,
        int minCount,
        int maxCount,
        boolean enabled,
        Double coefficient,
        List<SpawnRule> spawnRules,
        Source source,
        double chance
) {

    public Spawn {
        variant = variant == null ? "" : variant;
        spawnRules = spawnRules == null ? List.of() : spawnRules;
        chance = Double.isFinite(chance) ? Math.max(0, Math.min(100, chance)) : FULL_CHANCE;
    }

    /** An entry that always spawns when picked: the game's own, and a row with no chance set. */
    public Spawn(SpawnCategory category, String entity, String variant, int weight, int minCount, int maxCount,
                 boolean enabled, Double coefficient, List<SpawnRule> spawnRules, Source source) {
        this(category, entity, variant, weight, minCount, maxCount, enabled, coefficient, spawnRules, source,
                FULL_CHANCE);
    }

    /** Always spawns when picked. */
    public static final double FULL_CHANCE = 100;

    /** The largest spawn weight. */
    public static final int MAX_WEIGHT = 1_000_000;

    public static Spawn of(SpawnCategory category, String entity, int weight,
                           int minCount, int maxCount, Source source) {
        return new Spawn(category, entity, "", weight, minCount, maxCount,
                true, null, List.of(), source);
    }

    public String key() {
        return category.id() + "/" + entity + (isVariant() ? "#" + variant : "");
    }

    public boolean isVariant() {
        return !variant.isEmpty();
    }

    public Spawn withSource(Source newSource) {
        return new Spawn(category, entity, variant, weight, minCount, maxCount,
                enabled, coefficient, spawnRules, newSource, chance);
    }

    /** Finds the per-mob coefficient override for {@code entityId} in a resolved roster. */
    /** The mob's coefficient from the first category roster that has one, or null. */
    public static Double coefficientIn(java.util.function.Function<SpawnCategory, List<Spawn>> rosters,
                                       String entityId) {
        for (SpawnCategory cat : SpawnCategory.values()) {
            Double c = coefficientFor(rosters.apply(cat), entityId);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    public static Double coefficientFor(List<Spawn> roster, String entityId) {
        for (Spawn spawn : roster) {
            if (spawn.entity().equals(entityId)
                    && !spawn.isVariant()
                    && spawn.coefficient() != null) {
                return spawn.coefficient();
            }
        }
        return null;
    }
}
