package com.strata.model;

import java.util.Map;

/**
 * A named version of a creature.
 *
 * @param id immutable identifier
 * @param baseEntity the creature it is built from
 * @param displayName what the player sees
 * @param coefficient per-variant coefficient (default 1.0)
 * @param texture optional alternate skin path
 * @param multipliers attribute id to multiplier, applied BEFORE scaling
 * @param absolutes attribute id to final value, applied AFTER scaling
 * @param isOwnEntity true = register a real EntityType for this variant
 * @param scopedMultipliers a tier or biome instance's multipliers, by {@link #scopeKey}, over the global ones
 * @param scopedAbsolutes the same for the absolutes
 */
public record Variant(
        String id,
        String baseEntity,
        String displayName,
        double coefficient,
        String texture,
        Map<String, Double> multipliers,
        Map<String, Double> absolutes,
        boolean isOwnEntity,
        Map<String, Map<String, Double>> scopedMultipliers,
        Map<String, Map<String, Double>> scopedAbsolutes,
        /** The variant's base level. */
        int startLevel
) {

    public static Variant of(String id, String baseEntity) {
        return new Variant(id, baseEntity, id, 1.0, "", Map.of(), Map.of(), false, Map.of(), Map.of(), 1);
    }

    /** The key of a tier or biome instance's stats: "tier|t1", "biome|minecraft:desert". */
    public static String scopeKey(String scope, String scopeId) {
        return scope + "|" + scopeId;
    }

    /**
     * The multipliers of the instance a mob spawned in: global, then the tier's, then the biome's, per
     * attribute (the scope was read and then dropped, so a tier's stats applied everywhere).
     */
    public Map<String, Double> multipliersIn(String tier, String biome) {
        return layered(multipliers, scopedMultipliers, tier, biome);
    }

    /** The absolutes of the instance a mob spawned in, layered as the multipliers are. */
    public Map<String, Double> absolutesIn(String tier, String biome) {
        return layered(absolutes, scopedAbsolutes, tier, biome);
    }

    /** Global, then the tier's, then the biome's, per attribute. */
    public static Map<String, Double> layered(Map<String, Double> global, Map<String, Map<String, Double>> scoped,
                                              String tier, String biome) {
        Map<String, Double> out = new java.util.LinkedHashMap<>(global);
        if (tier != null && !tier.isEmpty()) {
            out.putAll(scoped.getOrDefault(scopeKey("tier", tier), Map.of()));
        }
        if (biome != null && !biome.isEmpty()) {
            out.putAll(scoped.getOrDefault(scopeKey("biome", biome), Map.of()));
        }
        return out;
    }

    /**
     * The variant a PLAIN mob's equipment, abilities, and behavior live on: {@code minecraft:zombie} gives
     * {@code minecraft_zombie_default}.
     */
    public static String defaultNameFor(String entityId) {
        String ent = entityId == null ? "" : entityId.strip();
        return ent.isEmpty() ? "" : ent.replace(":", "_") + "_default";
    }

    /** True for the plain mob's own extras variant. */
    public boolean isPlainDefault() {
        return id.equals(defaultNameFor(baseEntity));
    }

    /** The extras variant this variant inherits from: its base mob's, or "" for the base mob's own. */
    public String inheritsFrom() {
        return isPlainDefault() ? "" : defaultNameFor(baseEntity);
    }
}
