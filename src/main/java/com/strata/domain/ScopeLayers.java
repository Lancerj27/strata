package com.strata.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * THE SCOPES AN INSTANCE READS (from EquipmentOps): its own, then the wider ones it inherits, nearest first,
 * as the runtime walks them (ScopeIndex#resolve).
 */
public final class ScopeLayers {

    private ScopeLayers() {
    }
    /**
     * One scope an instance's set can come from.
     *
     * @param from "" for the instance's own scope, else where an inherited set comes from: {@link #FROM_TIER}
     *     or {@link #FROM_GLOBAL}
     */
    public record ScopeLayer(String scope, String scopeId, String from) {
    }

    /**
     * The scopes an instance reads, its own first, then the wider ones it inherits, nearest first, as the
     * runtime walks them (ScopeIndex#resolve): a biome, its tier, global; a tier, global; global alone.
     *
     * @param biomeTier the biome's tier, or "" (read only for a biome)
     */
    public static List<ScopeLayer> scopeLayers(String scope, String scopeId, String biomeTier) {
        String s = VariantOps.normalScope(scope);
        List<ScopeLayer> out = new ArrayList<>();
        out.add(new ScopeLayer(s, VariantOps.normalScopeId(scope, scopeId), ""));
        if ("biome".equals(s) && biomeTier != null && !biomeTier.isBlank()) {
            out.add(new ScopeLayer("tier", biomeTier.strip(), FROM_TIER));
        }
        if (!s.isEmpty()) {
            out.add(new ScopeLayer("", "", FROM_GLOBAL));
        }
        return out;
    }

    /** {@link #scopeLayers} with the biome's tier read from the tables. */
    public static List<ScopeLayer> scopeLayers(String scope, String scopeId) {
        String biomeTier = "";
        if ("biome".equals(VariantOps.normalScope(scope)) && scopeId != null) {
            var biome = com.strata.data.Config.tables().biomes().get(scopeId.strip());
            biomeTier = biome == null || biome.tier() == null ? "" : biome.tier();
        }
        return scopeLayers(scope, scopeId, biomeTier);
    }

    /** The first layer with a row, or -1. */
    public static <T> int firstWithRows(List<List<T>> layers) {
        for (int i = 0; i < layers.size(); i++) {
            if (!layers.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Where an inherited row comes from: the game, or the base mob's own rows. */
    public static final String FROM_GAME = "game";
    public static final String FROM_BASE = "base";
    /** An inherited row of this instance's own, from a wider scope. */
    public static final String FROM_TIER = "tier";
    public static final String FROM_GLOBAL = "global";
}
