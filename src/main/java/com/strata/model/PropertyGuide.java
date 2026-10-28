package com.strata.model;

import java.util.HashMap;
import java.util.Map;

/** What each property in the new variant property tables means, in plain words. */
public final class PropertyGuide {

    private PropertyGuide() {
    }

    /**
     * One property explained.
     *
     * @param name a short name
     * @param what what it does, and what its numbers mean
     */
    public record Entry(String name, String what) {
    }

    /** Sound properties (group "sounds"). */
    public static final Map<String, Entry> SOUNDS = new HashMap<>();

    /** Visual properties (group "visuals"). */
    public static final Map<String, Entry> VISUALS = new HashMap<>();

    /** Physics properties (group "physics"). */
    public static final Map<String, Entry> PHYSICS = new HashMap<>();

    /** Flag properties (group "flags"). */
    public static final Map<String, Entry> FLAGS = new HashMap<>();

    /** Combat properties (group "combat"). */
    public static final Map<String, Entry> COMBAT = new HashMap<>();

    /** Particle properties (group "particles"). */
    public static final Map<String, Entry> PARTICLES = new HashMap<>();

    /** Loot/XP properties (group "loot_xp"). */
    public static final Map<String, Entry> LOOT_XP = new HashMap<>();

    /** Boss bar properties (group "boss"). */
    public static final Map<String, Entry> BOSS = new HashMap<>();

    /** Terrain costs (group "pathfinding"), one per PathType. */
    public static final Map<String, Entry> PATHFINDING = new HashMap<>();

    /** All property groups, keyed by GameLayer group name. */
    public static final Map<String, Map<String, Entry>> ALL = new HashMap<>();

    private static void put(Map<String, Entry> map, String key, String name, String what) {
        map.put(key, new Entry(name, what));
    }

    static {
        // SOUNDS
        put(SOUNDS, "ambient_sound", "Ambient sound",
                "The sound it makes while idle. A resource location like minecraft:entity.zombie.ambient.");
        put(SOUNDS, "hurt_sound", "Hurt sound",
                "The sound it makes when hurt. A resource location like minecraft:entity.zombie.hurt.");
        put(SOUNDS, "death_sound", "Death sound",
                "The sound it makes when it dies. A resource location like minecraft:entity.zombie.death.");
        put(SOUNDS, "step_sound", "Step sound",
                "The sound its footsteps make. A resource location like minecraft:entity.zombie.step.");
        put(SOUNDS, "attack_sound", "Attack sound",
                "The sound it makes when attacking. A resource location like minecraft:entity.zombie.attack.");
        put(SOUNDS, "swim_sound", "Swim sound",
                "The sound it makes when swimming. A resource location like minecraft:entity.generic.swim.");
        put(SOUNDS, "fall_sound", "Fall sound",
                "The sound it makes when landing from a fall. A resource location like minecraft:entity.generic.big_fall.");
        put(SOUNDS, "ambient_volume", "Ambient volume",
                "Volume of the ambient sound (0.0 to 1.0). Blank = default.");
        put(SOUNDS, "ambient_pitch", "Ambient pitch",
                "Pitch of the ambient sound (0.5 to 2.0). Blank = default.");
        put(SOUNDS, "hurt_volume", "Hurt volume",
                "Volume of the hurt sound (0.0 to 1.0). Blank = default.");
        put(SOUNDS, "hurt_pitch", "Hurt pitch",
                "Pitch of the hurt sound (0.5 to 2.0). Blank = default.");
        put(SOUNDS, "death_volume", "Death volume",
                "Volume of the death sound (0.0 to 1.0). Blank = default.");
        put(SOUNDS, "death_pitch", "Death pitch",
                "Pitch of the death sound (0.5 to 2.0). Blank = default.");
        put(SOUNDS, "step_volume", "Step volume",
                "Volume of the step sound (0.0 to 1.0). Blank = default.");
        put(SOUNDS, "step_pitch", "Step pitch",
                "Pitch of the step sound (0.5 to 2.0). Blank = default.");

        // VISUALS
        put(VISUALS, "texture", "Texture",
                "The texture to use. A resource location like minecraft:textures/entity/zombie.png.");
        put(VISUALS, "model", "Model",
                "The model to draw it as. A resource location of a living mob like minecraft:zombie.");
        put(VISUALS, "glow_texture", "Glow texture",
                "An optional glow texture overlay. A resource location like minecraft:textures/entity/creeper/creeper_armor.png.");
        put(VISUALS, "emissive", "Emissive",
                "Whether it glows in the dark (true/false). Blank = default.");
        put(VISUALS, "tint", "Tint color",
                "A color multiplier as RRGGBB or AARRGGBB hex. Blank = no tint.");
        put(VISUALS, "shadow_radius", "Shadow radius",
                "The size of its shadow. Blank = default.");
        put(VISUALS, "scale_visual", "Visual scale",
                "A multiplier for its rendered size (1.0 = normal). Blank = default.");
        put(VISUALS, "silent", "Silent",
                "true = makes no sounds at all. Blank = default.");
        put(VISUALS, "glowing", "Glowing",
                "true = has the glowing effect outline. Blank = default.");
        put(VISUALS, "suppress_base_particles", "Drops the base mob's particles",
                "true = a swapped model hides the particles the base mob would have shown;"
                        + " false = it keeps them. Blank = true, which is what happened before this"
                        + " column existed. Only applies when the variant has its own model.");

        // PHYSICS
        put(PHYSICS, "width", "Width",
                "Hitbox width in blocks. Must be above zero.");
        put(PHYSICS, "height", "Height",
                "Hitbox height in blocks. Must be above zero.");
        put(PHYSICS, "eye_height", "Eye height",
                "Height of its eyes in blocks. Must be above zero.");
        put(PHYSICS, "no_gravity", "No gravity",
                "true = floats in the air, unaffected by gravity. Blank = default.");

        // FLAGS
        put(FLAGS, "burn_in_daylight", "Burn in daylight",
                "true = burns in sunlight (like zombies/skeletons). Blank = default.");
        put(FLAGS, "fire_immune", "Fire immune",
                "true = fire and lava do not hurt it. Blank = default.");
        put(FLAGS, "lava_immune", "Lava immune",
                "true = lava does not hurt it. Blank = default.");
        put(FLAGS, "water_immune", "Water immune",
                "true = water does not hurt it. Blank = default.");
        put(FLAGS, "drowning_immune", "Drowning immune",
                "true = cannot drown. Blank = default.");
        put(FLAGS, "fall_damage_immune", "Fall damage immune",
                "true = takes no fall damage. Blank = default.");
        put(FLAGS, "suffocation_immune", "Suffocation immune",
                "true = takes no suffocation damage. Blank = default.");
        put(FLAGS, "can_pick_up_items", "Can pick up items",
                "true = picks up items and armor it walks over. Blank = default.");
        put(FLAGS, "persistent", "Persistent",
                "true = never despawns naturally. Blank = default.");
        put(FLAGS, "no_despawn", "No despawn",
                "true = never despawns (stronger than persistent). Blank = default.");
        put(FLAGS, "can_climb", "Can climb",
                "true = can climb ladders and vines. Blank = default.");
        put(FLAGS, "can_walk_on_water", "Can walk on water",
                "true = walks on water surface instead of swimming. Blank = default.");
        put(FLAGS, "flying", "Flying",
                "true = flies in the air. Blank = default.");
        put(FLAGS, "swimming", "Swimming",
                "true = swims in water. Blank = default.");
        put(FLAGS, "boss", "Boss",
                "true = shows the game's boss health bar to players near it, like the Wither. Its bar's colour, style, name and range are on the Boss tab. Blank = not a boss.");
        put(FLAGS, "scoreboard_tags", "Scoreboard tags",
                "Tags put on the mob, separated by commas (used by /tag and the @e[tag=...] selector). Take one out of the list and it is taken off the mob again. Blank = none added.");

        // PATHFINDING
        for (String type : com.strata.data.Schema.PATH_TYPES) {
            put(PATHFINDING, type, type.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + type.substring(1).replace('_', ' ') + " cost",
                    "How much it dislikes walking on " + type.replace('_', ' ') + " terrain. -1 = never walks there, 0 = free, higher = avoids it more. Blank = the game's own.");
        }

        // DROP CHANCE PER SLOT
        for (String slot : com.strata.data.Schema.DROP_CHANCE_SLOTS) {
            put(LOOT_XP, "drop_chance_" + slot, "Drop chance: " + slot,
                    "Chance (0 to 1) that the item in its " + slot + " slot drops on death. Above 1 = always, and undamaged. Wins over Always drops equipment for this slot. Blank = the game's own.");
        }

        // BOSS
        put(BOSS, "bar_color", "Bar colour",
                "The boss bar's colour: pink, blue, red, green, yellow, purple or white. Blank = purple.");
        put(BOSS, "bar_style", "Bar style",
                "The boss bar's look: progress (smooth), or notched_6, notched_10, notched_12, notched_20 (that many segments). Blank = progress.");
        put(BOSS, "bar_name", "Bar name",
                "The name shown on the bar. Blank = the mob's own name.");
        put(BOSS, "bar_range", "Bar range",
                "Players within this many blocks of it see the bar. Blank = 64.");
        put(BOSS, "darken_sky", "Darken sky",
                "true = the sky darkens for players who see the bar, as with the Wither. Blank = no.");
        put(BOSS, "fog", "Fog",
                "true = fog closes in on players who see the bar, as with the Ender Dragon. Blank = no.");
        put(BOSS, "boss_music", "Boss music",
                "true = players who see the bar hear boss music. Blank = no.");
        put(BOSS, "never_despawn", "Never despawn",
                "true = the game never despawns it. Blank = the game's own.");


        // COMBAT
        put(COMBAT, "attack_cooldown", "Attack cooldown",
                "Ticks between attacks. Blank = default.");
        put(COMBAT, "reach", "Reach",
                "How far its attacks reach, in blocks. Blank = default.");
        put(COMBAT, "aoe_radius", "AoE radius",
                "Radius of area-of-effect attacks, in blocks. Blank = default.");
        put(COMBAT, "projectile_type", "Projectile type",
                "The entity type of its projectile (e.g. minecraft:arrow). Blank = default.");
        put(COMBAT, "projectile_speed", "Projectile speed",
                "Speed of its projectile. Blank = default.");
        put(COMBAT, "projectile_damage", "Projectile damage",
                "Damage of its projectile. Blank = default.");
        put(COMBAT, "projectile_explosion", "Projectile explosion",
                "Explosion power of its projectile (0 = none). Blank = default.");
        put(COMBAT, "projectile_gravity", "Projectile gravity",
                "Gravity applied to its projectile. Blank = default.");
        put(COMBAT, "ranged_attack_interval", "Ranged attack interval",
                "Ticks between ranged attacks. Blank = default.");
        put(COMBAT, "ranged_attack_range", "Ranged attack range",
                "How far away it attacks with projectiles, in blocks. Blank = default.");
        put(COMBAT, "damage_immunities", "Damage immunities",
                "Comma-separated damage types it is immune to (e.g. fire, drowning). Blank = default.");

        // PARTICLES
        put(PARTICLES, "particle_type", "Particle type",
                "The particle to spawn (e.g. minecraft:flame, minecraft:smoke). Blank = default.");
        put(PARTICLES, "particle_enabled", "Particle enabled",
                "true = shows its main particle effect. Blank = default.");
        put(PARTICLES, "ambient_enabled", "Ambient particles enabled",
                "true = shows ambient particles while idle. Blank = default.");
        put(PARTICLES, "hurt_enabled", "Hurt particles enabled",
                "true = shows particles when hurt. Blank = default.");
        put(PARTICLES, "death_enabled", "Death particles enabled",
                "true = shows particles on death. Blank = default.");
        put(PARTICLES, "attack_enabled", "Attack particles enabled",
                "true = shows particles when attacking. Blank = default.");
        put(PARTICLES, "spawn_enabled", "Spawn particles enabled",
                "true = shows particles on spawn. Blank = default.");
        put(PARTICLES, "count", "Particle count",
                "Number of particles per spawn. Blank = default.");
        put(PARTICLES, "frequency_ticks", "Frequency (ticks)",
                "How often to spawn particles, in ticks. Blank = default.");
        put(PARTICLES, "offset_x", "Offset X",
                "X offset from its center, in blocks. Blank = default.");
        put(PARTICLES, "offset_y", "Offset Y",
                "Y offset from its center, in blocks. Blank = default.");
        put(PARTICLES, "offset_z", "Offset Z",
                "Z offset from its center, in blocks. Blank = default.");
        put(PARTICLES, "velocity_x", "Velocity X",
                "Initial X velocity of particles. Blank = default.");
        put(PARTICLES, "velocity_y", "Velocity Y",
                "Initial Y velocity of particles. Blank = default.");
        put(PARTICLES, "velocity_z", "Velocity Z",
                "Initial Z velocity of particles. Blank = default.");

        // LOOT_XP
        put(LOOT_XP, "death_loot_table", "Death loot table",
                "The loot table to use on death (e.g. minecraft:entities/zombie). Blank = default.");
        put(LOOT_XP, "xp_min", "Min XP",
                "Minimum XP dropped on death. Blank = default.");
        put(LOOT_XP, "xp_max", "Max XP",
                "Maximum XP dropped on death. Blank = default.");
        put(LOOT_XP, "always_drop_equipment", "Always drop equipment",
                "true = always drops its equipment on death. Blank = default.");

        // Register all groups
        ALL.put("sounds", SOUNDS);
        ALL.put("visuals", VISUALS);
        ALL.put("physics", PHYSICS);
        ALL.put("flags", FLAGS);
        ALL.put("combat", COMBAT);
        ALL.put("particles", PARTICLES);
        ALL.put("loot_xp", LOOT_XP);
        ALL.put("boss", BOSS);
        ALL.put("pathfinding", PATHFINDING);
    }

    /**
     * Get the explanation for a property key in a group.
     *
     * @param group the GameLayer group (sounds, visuals, physics, flags, attributes, combat, particles,
     *     loot_xp)
     * @param key the property key
     * @return the explanation, or "" if unknown
     */
    public static String what(String group, String key) {
        Map<String, Entry> map = ALL.get(group);
        if (map == null) {
            return "";
        }
        Entry entry = map.get(key);
        return entry == null ? "" : entry.what();
    }

    /**
     * Get the display name for a property key in a group.
     *
     * @param group the GameLayer group
     * @param key the property key
     * @return the display name, or a formatted version of the key if unknown
     */
    public static String name(String group, String key) {
        Map<String, Entry> map = ALL.get(group);
        if (map == null) {
            return formatKey(key);
        }
        Entry entry = map.get(key);
        return entry == null ? formatKey(key) : entry.name();
    }

    /**
     * Get all known property keys for a group.
     *
     * @param group the GameLayer group
     * @return list of known keys, or empty list if group unknown
     */
    public static java.util.List<String> keys(String group) {
        Map<String, Entry> map = ALL.get(group);
        if (map == null) {
            return java.util.List.of();
        }
        return new java.util.ArrayList<>(map.keySet());
    }

    /**
     * Check if a group is a known property group.
     *
     * @param group the GameLayer group
     * @return true if the group has known properties
     */
    public static boolean knowsGroup(String group) {
        return ALL.containsKey(group);
    }

    /**
     * Check if a property key is known for a group.
     *
     * @param group the GameLayer group
     * @param key the property key
     * @return true if the key is known
     */
    public static boolean knows(String group, String key) {
        Map<String, Entry> map = ALL.get(group);
        return map != null && map.containsKey(key);
    }

    private static String formatKey(String key) {
        String[] parts = key.split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(' ');
            String p = parts[i];
            if (!p.isEmpty()) {
                out.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) out.append(p.substring(1));
            }
        }
        return out.toString();
    }
}