package com.strata.runtime;

import java.util.Map;

/** How a runtime index finds the right INSTANCE of a variant. */
public final class ScopeIndex {

    private ScopeIndex() {
    }

    /** Separator that cannot occur in a scope, id or variant name. */
    private static final String SEP = "\u001F";

    /** The index key for one instance. */
    public static String key(String scope, String scopeId, String variant) {
        String s = scope == null ? "" : scope.strip().toLowerCase(java.util.Locale.ROOT);
        if ("global".equals(s)) {
            s = "";
        }
        String id = s.isEmpty() ? "" : (scopeId == null ? "" : scopeId.strip());
        return s + SEP + id + SEP + (variant == null ? "" : variant.strip());
    }

    /**
     * The value for a variant as seen from one biome, walking biome -&gt; tier -&gt; global and taking the
     * first layer that has one.
     *
     * @param index an index built with {@link #key}
     * @param variant the variant's name
     * @param tier the tier the biome belongs to, or null
     * @param biome the biome id, or null
     * @return the resolved value, or null when no layer defines one
     */
    public static <T> T resolve(Map<String, T> index, String variant, String tier, String biome) {
        if (variant == null || variant.isEmpty()) {
            return null;
        }
        if (biome != null && !biome.isEmpty()) {
            T hit = index.get(key("biome", biome, variant));
            if (hit != null) {
                return hit;
            }
        }
        if (tier != null && !tier.isEmpty()) {
            T hit = index.get(key("tier", tier, variant));
            if (hit != null) {
                return hit;
            }
        }
        return index.get(key("", "", variant));
    }

    /**
     * The value for a variant, or its base mob's when the variant defines none in any layer.
     *
     * @param base the base mob's extras variant (Variant#inheritsFrom), or ""
     */
    public static <T> T resolveOrBase(Map<String, T> index, String variant, String base,
                                      String tier, String biome) {
        T own = resolve(index, variant, tier, biome);
        return own != null || base == null || base.isEmpty() ? own : resolve(index, base, tier, biome);
    }

    /** The tier and biome a mob spawned in, packed into one attachment value. */
    public static String packScope(String tier, String biome) {
        return (tier == null ? "" : tier) + SEP + (biome == null ? "" : biome);
    }

    /** The tier out of a {@link #packScope} value. */
    public static String tierOf(String packed) {
        if (packed == null || packed.isEmpty()) {
            return "";
        }
        int at = packed.indexOf(SEP);
        return at < 0 ? "" : packed.substring(0, at);
    }

    /** The biome out of a {@link #packScope} value. */
    public static String biomeOf(String packed) {
        if (packed == null || packed.isEmpty()) {
            return "";
        }
        int at = packed.indexOf(SEP);
        return at < 0 ? "" : packed.substring(at + SEP.length());
    }
}
