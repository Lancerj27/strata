package com.strata.model;

import java.util.List;

/**
 * A loot table override at a given scope.
 *
 * @param scope global, tier, or biome
 * @param scopeId tier name or biome id (empty for global)
 * @param entity which mob's loot table this overrides
 * @param variantName which variant's drops these are, blank for the plain mob. The runtime applies an
 *     override only to a mob of that variant. Without it, a variant's drops applied to every mob of the entity in
 *     the scope.
 * @param mode ADD adds pools/entries, REPLACE discards the original
 * @param pools the loot pools in this override
 */
public record MobLoot(
        String scope,
        String scopeId,
        String entity,
        String variantName,
        OverrideMode mode,
        List<LootPool> pools
) {

    /** True when this override is for a mob of this variant. */
    public boolean appliesTo(String entityId, String mobVariant) {
        String want = mobVariant == null ? "" : mobVariant.strip();
        if (want.equals(Variant.defaultNameFor(entityId))) {
            want = "";
        }
        return variantName.equals(want);
    }

    /**
     * Whose drop rows a mob runs: a variant's own, when it has any row in the layers here, else its base
     * mob's (""), as a variant takes its base mob's equipment, behavior, and abilities.
     *
     * @param entityId the base entity the rows are keyed on
     * @param mobVariant the mob's variant, or "" for a plain mob
     */
    public static String lootOwner(List<MobLoot> overrides, String entityId, String mobVariant) {
        String want = mobVariant == null ? "" : mobVariant.strip();
        if (want.isEmpty() || want.equals(Variant.defaultNameFor(entityId))) {
            return "";
        }
        for (MobLoot o : overrides) {
            if (o.entity().equals(entityId) && o.variantName().equals(want)) {
                return want;
            }
        }
        return "";
    }
}
