package com.strata.domain;

import com.strata.data.StrataConfig;

/** Where players spawn, a debug tool. */
public final class PlayerSpawnOps {

    private PlayerSpawnOps() {
    }

    /** What the target is. */
    public enum Kind { OFF, BIOME, TIER }

    /** A spawn target: its kind, and a biome id or a tier name ("" for off). */
    public record Target(Kind kind, String id) {
        public static final Target OFF = new Target(Kind.OFF, "");
    }

    private static final String BIOME = "biome:";
    private static final String TIER = "tier:";

    /** The stored value as a target. */
    public static Target parse(String stored) {
        String s = stored == null ? "" : stored.strip();
        if (s.startsWith(BIOME) && s.length() > BIOME.length()) {
            return new Target(Kind.BIOME, s.substring(BIOME.length()));
        }
        if (s.startsWith(TIER) && s.length() > TIER.length()) {
            return new Target(Kind.TIER, s.substring(TIER.length()));
        }
        return Target.OFF;
    }

    /** The target as it is stored. */
    public static String stored(Target t) {
        return switch (t.kind()) {
            case OFF -> "";
            case BIOME -> BIOME + t.id();
            case TIER -> TIER + t.id();
        };
    }

    /** The target now. */
    public static Target current() {
        return parse(StrataConfig.DEBUG_PLAYER_SPAWN.get());
    }

    /** Spawns in a biome: the world spawn moves to the nearest place of it. */
    public static WriteReport setBiome(String biomeId) {
        return OptionsOps.set(OptionsOps.DEBUG_PLAYER_SPAWN, stored(new Target(Kind.BIOME, biomeId)));
    }

    /** Spawns in a tier: the world spawn moves to the nearest of its biomes. */
    public static WriteReport setTier(String tier) {
        if (!TierOps.names().contains(tier)) {
            return WriteReport.refused("No tier named " + tier);
        }
        if (TierOps.biomesIn(tier).isEmpty()) {
            return WriteReport.refused("The tier " + tier + " has no biomes yet: add biomes to it first");
        }
        return OptionsOps.set(OptionsOps.DEBUG_PLAYER_SPAWN, stored(new Target(Kind.TIER, tier)));
    }

    /** The game's own spawn again. */
    public static WriteReport off() {
        return OptionsOps.set(OptionsOps.DEBUG_PLAYER_SPAWN, "");
    }
}
