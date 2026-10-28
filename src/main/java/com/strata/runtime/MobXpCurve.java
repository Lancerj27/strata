package com.strata.runtime;

/** How much extra XP a mob drops for its level: */
public record MobXpCurve(double xpMultiplierPerLevel, double exponent) {

    public double multiplierAt(int level) {
        return com.strata.model.ScalingFormulas.eval(com.strata.model.ScalingFormulas.Kind.MOB_XP,
                com.strata.model.ScalingFormulas.vars("level", level, "per_level", xpMultiplierPerLevel, "exponent", exponent));
    }
}
