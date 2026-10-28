package com.strata.model;

import java.util.HashMap;
import java.util.Map;

/** What each mob attribute means, in plain words. */
public final class AttributeGuide {

    private AttributeGuide() {
    }

    /**
     * One attribute explained.
     *
     * @param name a short name, "Health"
     * @param what what it does, and what its numbers mean
     */
    public record Entry(String name, String what) {
    }

    private static final Map<String, Entry> KNOWN = new HashMap<>();

    private static void put(String key, String name, String what) {
        KNOWN.put(key, new Entry(name, what));
    }

    /** Said after a player-only attribute's words: a mob does not use it. */
    private static final String PLAYER_ONLY = " Players only: it changes nothing on a mob.";

    static {
        put("max_health", "Health",
                "Health points. 2 is one heart. A player and a zombie have 20 (10 hearts).");
        put("attack_damage", "Attack damage",
                "Damage of a bare-handed hit, in health points (2 is one heart). A held weapon adds its"
                        + " own damage on top. A player has 1, a zombie 3.");
        put("movement_speed", "Move speed",
                "How fast it walks. It is not blocks per second: a player has 0.1, a zombie 0.23, a"
                        + " skeleton 0.25. Twice the number is twice as fast.");
        put("armor", "Armor",
                "Armor points, 0 to 30. 20 is a full armor bar (full diamond). Each point stops about 4%"
                        + " of a hit, up to 80%. A big hit gets through more, unless it has armor toughness."
                        + " Worn armor adds its own points on top of this number.");
        put("armor_toughness", "Armor toughness",
                "Keeps armor strong against big hits. With 0, a hard hit gets through much of the armor."
                        + " Diamond armor gives 2 per piece, netherite 3. Worn armor adds its own on top.");
        put("knockback_resistance", "Knockback resistance",
                "0 to 1: the share of knockback it ignores. 0 is full knockback, 1 is none."
                        + " Netherite armor gives 0.1 per piece.");
        put("follow_range", "Follow range",
                "How far, in blocks, it notices and follows a target. A zombie has 35, most mobs 16.");
        put("attack_knockback", "Attack knockback",
                "Extra knockback of its hits, added to the normal push.");
        // THE PLAYER'S OWN: each says what a higher number does.
        put("attack_speed", "Attack speed",
                "How fast the attack bar refills: full-strength hits per second. A player has 4, and the"
                        + " held weapon lowers it (a sword to 1.6, an axe to about 1). A hit before the bar is"
                        + " full does less damage, so a higher number means full hits more often."
                        + PLAYER_ONLY);
        put("luck", "Luck",
                "Better loot from fishing and from chests that use luck. 0 is normal; the Luck effect"
                        + " adds 1 per level." + PLAYER_ONLY);
        put("block_interaction_range", "Block reach",
                "How far away, in blocks, it can break, place, and use blocks. 4.5 is normal."
                        + PLAYER_ONLY);
        put("entity_interaction_range", "Attack reach",
                "How far away, in blocks, it can hit and use mobs. 3 is normal." + PLAYER_ONLY);
        put("block_break_speed", "Mining speed",
                "Mining speed times this number. 1 is normal, 2 breaks blocks twice as fast." + PLAYER_ONLY);
        put("mining_efficiency", "Mining efficiency",
                "Extra mining speed with the right tool, as the Efficiency enchantment gives. 0 is normal."
                        + PLAYER_ONLY);
        put("sneaking_speed", "Sneaking speed",
                "Walking speed while sneaking, as a share of normal speed. 0.3 is normal, 1 is full speed."
                        + PLAYER_ONLY);
        put("submerged_mining_speed", "Underwater mining speed",
                "Mining speed under water, as a share of normal. 0.2 is normal, 1 mines as fast as on land."
                        + PLAYER_ONLY);
        put("sweeping_damage_ratio", "Sweep damage",
                "The share of a sword hit's damage that the sweep does to mobs nearby. 0 is normal;"
                        + " Sweeping Edge raises it." + PLAYER_ONLY);
        put("flying_speed", "Flying speed",
                "How fast it flies (a bee, a parrot). Only a flying mob uses it.");
        put("spawn_reinforcements", "Reinforcement chance",
                "Zombies only. 0 to 1: the chance it calls another zombie when hurt.");
        put("max_absorption", "Max absorption",
                "The most extra yellow health it can hold from absorption effects.");
        put("scale", "Size",
                "Its size. 1 is normal, 2 is twice as big, and its hitbox grows with it.");
        put("step_height", "Step height",
                "How high a step it walks up without a jump, in blocks. 0.6 is normal.");
        put("gravity", "Gravity", "How fast it falls. 0.08 is normal.");
        put("safe_fall_distance", "Safe fall distance",
                "How many blocks it can fall before it takes damage. 3 is normal.");
        put("fall_damage_multiplier", "Fall damage multiplier",
                "Fall damage times this number. 0 is no fall damage, 1 is normal.");
        put("jump_strength", "Jump strength", "How high it jumps. 0.42 is a one-block jump.");
        put("burning_time", "Burning time",
                "How long fire keeps burning it, times this number. 1 is normal, 0 is no burning.");
        put("explosion_knockback_resistance", "Explosion knockback resistance",
                "0 to 1: the share of an explosion's push it ignores.");
        put("movement_efficiency", "Movement efficiency",
                "0 to 1: how little slow blocks (soul sand, honey) slow it. 1 is not at all.");
        put("water_movement_efficiency", "Water movement efficiency",
                "0 to 1: how little water slows it. 1 is not at all.");
        put("oxygen_bonus", "Oxygen bonus", "More air under water: a higher number drowns more slowly.");
    }

    /**
     * What a scaling curve does to this attribute, for the Scaling screen: the attribute's words, then how
     * the curve changes it.
     */
    public static String scaled(String attributeId, boolean players) {
        return of(attributeId).what() + (players
                ? " A player curve raises it with the player's XP level."
                : " A mob curve raises it with the area's level, times the mob's coefficient.");
    }

    /** The explanation for an attribute id. */
    public static Entry of(String attributeId) {
        String id = attributeId == null ? "" : attributeId.strip();
        String key = id.substring(Math.max(id.lastIndexOf(':'), id.lastIndexOf('.')) + 1);
        Entry known = KNOWN.get(key);
        if (known != null) {
            return known;
        }
        String name = key.replace('_', ' ');
        return new Entry(name.isEmpty() ? "attribute" : Character.toUpperCase(name.charAt(0)) + name.substring(1),
                "An attribute from the game or a mod. Strata has no description for it.");
    }

    /** True when the guide explains this attribute. */
    public static boolean knows(String attributeId) {
        String id = attributeId == null ? "" : attributeId.strip();
        return KNOWN.containsKey(id.substring(Math.max(id.lastIndexOf(':'), id.lastIndexOf('.')) + 1));
    }
}
