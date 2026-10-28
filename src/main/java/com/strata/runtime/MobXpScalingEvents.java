package com.strata.runtime;

import com.strata.data.Config;

import com.strata.Strata;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * Scales the XP a mob drops based on its already-stored MobLevel — the same level assigned once at spawn (see
 * MobScalingHandler) and never recomputed.
 */
public class MobXpScalingEvents {

    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity mob = event.getEntity();
        MobLevel mobLevel = mob.getData(MobLevel.MOB_LEVEL);

        // No level assigned (spawned before Strata was installed), or level 1 and below: LEVEL 1 IS THE GAME'S
        // VALUES (the mob curves start at 2), and every mob with no tier spawns at its start level, 1. The
        // formula gave 1 + per_level at level 1, so Default dropped six times the game's XP.
        if (!mobLevel.isSet() || mobLevel.level() <= 1) {
            return;
        }

        int originalXp = event.getOriginalExperience();
        double multiplier = Config.xpCurve().multiplierAt(mobLevel.level());
        int scaledXp = (int) Math.round(originalXp * multiplier);

        event.setDroppedExperience(scaledXp);

        Strata.LOGGER.debug("{} (level {}) dropped {} XP (vanilla base: {}, multiplier: {})",
                mob.getType().getDescription().getString(), mobLevel.level(), scaledXp, originalXp, multiplier);
    }
}
