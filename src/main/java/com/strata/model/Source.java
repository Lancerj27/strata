package com.strata.model;

/**
 * Where a resolved value came from.
 *
 * @param layer which layer produced the value
 * @param detail the tier name, or null when the layer speaks for itself
 */
public record Source(Layer layer, String detail) {

    public enum Layer {
        /** Vanilla, or another mod. */
        GAME,
        /** A global-scope spawn rule that applies to every biome. */
        GLOBAL,
        /** A tier the value was inherited from. */
        TIER,
        /** Set directly on this biome. */
        BIOME,
        /** Nothing set it, so it does not apply. */
        NONE
    }

    public static final Source GAME = new Source(Layer.GAME, null);
    public static final Source GLOBAL = new Source(Layer.GLOBAL, null);
    public static final Source BIOME = new Source(Layer.BIOME, null);
    public static final Source NONE = new Source(Layer.NONE, null);

    public static Source tier(String tierName) {
        return new Source(Layer.TIER, tierName);
    }

    /** "from TEMPERATE", "set here", "vanilla" - what the UI actually prints. */
    public String describe() {
        return switch (layer) {
            case GAME -> "vanilla";
            case GLOBAL -> "every biome";
            case TIER -> "from " + detail;
            case BIOME -> "set here";
            case NONE -> "not set";
        };
    }
}
