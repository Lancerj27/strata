package com.strata.model;

/** Pure math behind the ores.csv frequency_pct gate. */
public final class OreFrequency {

    private OreFrequency() {}

    /** Normal density. */
    public static final double FULL = 100.0;

    /** Clamps a parsed frequency_pct to the meaningful range, 0-100. */
    public static double clamp(double pct) {
        if (Double.isNaN(pct)) {
            return FULL;
        }
        return Math.max(0.0, Math.min(FULL, pct));
    }

    /**
     * The RarityFilter argument that approximates a percentage density: attempt once every {@code 100 / pct}
     * tries, so 50 -> every 2 blocks (half density) and 100 -> every 1 (never filtered, no-op).
     */
    public static int rarityFor(double pct) {
        double p = clamp(pct);
        return p <= 0 ? Integer.MAX_VALUE : Math.max(1, (int) Math.round(FULL / p));
    }
}