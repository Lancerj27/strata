package com.strata.runtime;

import net.minecraft.server.level.ServerPlayer;

/** VANILLA_XP mode: power level = the player's own vanilla experience level. */
public final class PowerLevel {

    private PowerLevel() {}

    /** The player's level, which is their vanilla XP level and nothing else. */
    public static int of(ServerPlayer player) {
        return player.experienceLevel;
    }
}
