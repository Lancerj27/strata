package com.strata.model;

/**
 * A condition on a loot pool or entry.
 *
 * @param type condition type (parsed at load; unknown types are rejected)
 * @param value type-specific parameter string
 */
public record LootCondition(Type type, String value) {

    public enum Type {
        /** Roll passes with probability equal to `value` (0.0-1.0). */
        RANDOM_CHANCE,

        /** Roll passes when the killer's looting level >= threshold from `value`. */
        LOOTING,

        /**
         * Roll passes when the killer player's experience level matches `value`: a number like 5, a range
         * like 3-10, or a minimum like 5+.
         */
        PLAYER_LEVEL,

        /** Roll passes when the mob died by the damage type `value` names. */
        DEATH_CAUSE;


        public static Type parse(String raw) {
            if (raw == null) return null;
            return switch (raw.strip().toLowerCase(java.util.Locale.ROOT)) {
                case "random_chance", "minecraft:random_chance" -> RANDOM_CHANCE;
                // The old Killed by a player is the cause of death "player" (of).
                case "killed_by_player", "minecraft:killed_by_player" -> DEATH_CAUSE;
                case "looting", "minecraft:looting" -> LOOTING;
                case "player_level", "minecraft:player_level", "player_xp" -> PLAYER_LEVEL;
                case "death_cause", "minecraft:death_cause" -> DEATH_CAUSE;
                default -> null;
            };
        }
    }

    /** A condition from a loot.csv row. */
    public static LootCondition of(Type type, String rawType, String value) {
        String raw = rawType == null ? "" : rawType.strip().toLowerCase(java.util.Locale.ROOT);
        return raw.endsWith("killed_by_player") ? new LootCondition(Type.DEATH_CAUSE, PLAYER) : new LootCondition(type, value);
    }

    /** What is known about a death, for the cause check. */
    public record DeathFacts(String damageTypeId, boolean byPlayer, boolean sunlight) {
    }

    public static final String ANY = "any";
    public static final String PLAYER = "player";
    public static final String SUNLIGHT = "sunlight";
    public static final String OTHER = "other";

    /** The causes the editor offers first, in order. */
    public static final java.util.List<String> CAUSES = java.util.List.of(
            ANY, PLAYER, "player_melee", "mob", "fire", "lava", SUNLIGHT, "drowning", "fall", "explosion", OTHER);

    /** The damage types each cause group covers (bare paths of the game's ids). */
    private static final java.util.Map<String, java.util.Set<String>> GROUPS = java.util.Map.of(
            "player_melee", java.util.Set.of("player_attack"),
            "mob", java.util.Set.of("mob_attack", "mob_attack_no_aggro", "mob_projectile", "sting"),
            "fire", java.util.Set.of("in_fire", "on_fire", "campfire", "hot_floor", "fireball", "unattributed_fireball"),
            "lava", java.util.Set.of("lava"),
            "drowning", java.util.Set.of("drown"),
            "fall", java.util.Set.of("fall", "fly_into_wall"),
            "explosion", java.util.Set.of("explosion", "player_explosion", "bad_respawn_point", "fireworks"));

    /** True when the death matches the cause `value` names. */
    public static boolean checkDeathCause(String value, DeathFacts death) {
        String v = value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty() || ANY.equals(v)) {
            return true;
        }
        if (PLAYER.equals(v)) {
            return death.byPlayer();
        }
        if (SUNLIGHT.equals(v)) {
            return death.sunlight();
        }
        String d = death.damageTypeId() == null ? "" : death.damageTypeId().strip().toLowerCase(java.util.Locale.ROOT);
        String path = d.substring(d.lastIndexOf(':') + 1);
        if (OTHER.equals(v)) {
            return !death.sunlight() && GROUPS.values().stream().noneMatch(g -> g.contains(path));
        }
        java.util.Set<String> group = GROUPS.get(v);
        if (group != null) {
            return group.contains(path) && !("fire".equals(v) && death.sunlight());
        }
        return !d.isEmpty() && (v.equals(d) || v.substring(v.lastIndexOf(':') + 1).equals(path));
    }

    /** The cause check for a death known only by its damage type. */
    public static boolean checkDeathCause(String value, String damageTypeId) {
        return checkDeathCause(value, new DeathFacts(damageTypeId, false, false));
    }

    /** True for a cause group name (not a game damage type id). */
    public static boolean isCause(String value) {
        return value != null && CAUSES.contains(value.strip().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Pure looting-threshold check, headless-safe.
     *
     * @param value the condition's value string: ">=N", bare "N", or empty (empty means threshold 1 = "has
     *     any looting")
     * @param killerLootingLevel the killer's effective looting enchantment level
     * @return true when the killer's looting meets or exceeds the threshold
     */
    public static boolean checkLooting(String value, int killerLootingLevel) {
        String v = value == null ? "" : value.strip();
        if (v.startsWith(">=")) v = v.substring(2).strip();
        int threshold;
        if (v.isEmpty()) {
            threshold = 1;
        } else {
            try {
                threshold = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                threshold = 1;
            }
        }
        return killerLootingLevel >= threshold;
    }

    /**
     * Pure player-level check, headless-safe.
     *
     * @param value the condition's value string: "5", "3-10", or "5+" (the shared {@link LevelRange} grammar)
     * @param killerLevel the killer player's experience level
     * @return true when the killer's level passes the test; false for a blank or malformed value, which names
     *     no test
     */
    public static boolean checkPlayerLevel(String value, int killerLevel) {
        LevelRange range = LevelRange.parse(value);
        return range != null && range.matches(killerLevel);
    }
}
