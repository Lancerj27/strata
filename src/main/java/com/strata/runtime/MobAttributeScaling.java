package com.strata.runtime;

import com.strata.data.StrataConfig;
import com.strata.model.Resolution;

import net.minecraft.world.entity.LivingEntity;

/** Applies the mob scaling curves to a creature at its level. */
public final class MobAttributeScaling {

    private MobAttributeScaling() {}

    public static void apply(LivingEntity mob, int level, Resolution resolution) {
        apply(mob, level, resolution, resolution.coefficient());
    }

    public static void apply(LivingEntity mob, int level, Resolution resolution, double coefficient) {
        apply(mob, level, resolution, coefficient, null);
    }

    /**
     * As above, with the mob's own switch: On or Off decides alone, over the tier's, the biome's, and the
     * Scaling screen's; null leaves them to decide.
     */
    public static void apply(LivingEntity mob, int level, Resolution resolution, double coefficient,
                             Boolean own) {
        boolean allowed = own != null ? own
                : resolution.allows(resolution.scaleMobs(), StrataConfig.ENABLE_MOB_SCALING.get());
        if (!allowed) {
            return;
        }
        if (level <= 0 || !Double.isFinite(coefficient)) {
            return;
        }
        for (AttributeCurve curve : Scaling.mobs()) {
            curve.apply(mob, level, coefficient);
        }
    }

    /** Applies scaling from a mob's BASE level to the biome's level. */
    public static void applyFromBase(LivingEntity mob, int targetLevel, int startLevel,
                                     Resolution resolution, double coefficient, Boolean own) {
        boolean allowed = own != null ? own
                : resolution.allows(resolution.scaleMobs(), StrataConfig.ENABLE_MOB_SCALING.get());
        if (!allowed) {
            return;
        }
        if (targetLevel <= 0 || startLevel <= 0 || !Double.isFinite(coefficient)) {
            return;
        }
        for (AttributeCurve curve : Scaling.mobs()) {
            curve.applyFromBase(mob, targetLevel, startLevel, coefficient);
        }
    }
}
