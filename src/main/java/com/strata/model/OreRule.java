package com.strata.model;

/**
 * One ore override row: which ore, what it does to the layer beneath it, and at what density (frequency_pct
 * from ores.csv).
 *
 * @param oreId the ore name ("iron", "copper",...)
 * @param mode how it combines — see OverrideMode
 * @param frequencyPct 100 = normal density, 50 = half, 0 (via DENY) = off.
 */
public record OreRule(String oreId, OverrideMode mode, double frequencyPct) {

    public static OreRule add(String oreId, double frequencyPct) {
        return new OreRule(oreId, OverrideMode.ADD, frequencyPct);
    }

    public static OreRule deny(String oreId) {
        return new OreRule(oreId, OverrideMode.DENY, 0);
    }

    /**
     * The same rule as a plain Rule<String> so {@link OverrideMode#apply} keeps being the ONE layering
     * implementation — the ore path does not re-implement precedence for the sake of carrying a frequency.
     */
    public Rule<String> toRule() {
        return new Rule<>(oreId, mode);
    }
}