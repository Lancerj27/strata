package com.strata.ui;

import java.util.Map;

/** The words a dropdown shows for a table's codes. */
public final class ChoiceWords {

    private ChoiceWords() {
    }

    private static final Map<String, String> WORDS = Map.ofEntries(
            // A biome's switches: inherit the tier's, or set here.
            Map.entry("inherit", "Inherit"), Map.entry("on", "On"), Map.entry("off", "Off"),
            // Spawn scopes (the new mob's Scope).
            Map.entry("global", "Global"), Map.entry("tier", "Tier"), Map.entry("biome", "Biome"),
            // Override modes (loot, ores, tiers).
            Map.entry("add", "Add"), Map.entry("replace", "Replace"), Map.entry("deny", "Deny"),
            // Loot conditions.
            Map.entry("(none)", "None"), Map.entry("random_chance", "Random chance"),
            Map.entry("looting", "Looting level"),
            Map.entry("player_level", "Player XP level"),
            Map.entry("death_cause", "Cause of death"),
            // Spawn condition types and their word values.
            Map.entry("time", "Time of day"), Map.entry("light", "Light level"), Map.entry("y_level", "Y level"),
            Map.entry("weather", "Weather"), Map.entry("moon_phase", "Moon phase"),
            Map.entry("structure", "In a structure"), Map.entry("surface", "Surface"),
            Map.entry("distance", "Distance from world spawn"), Map.entry("time_since_rest", "Time since rest (days)"), Map.entry("world_day", "World day"),
            Map.entry("day", "Day"), Map.entry("night", "Night"), Map.entry("dusk", "Dusk"), Map.entry("dawn", "Dawn"), Map.entry("daylight", "Daylight (the game's)"), Map.entry("dark", "Dark (the game's)"),
            Map.entry("clear", "Clear"), Map.entry("rain", "Rain"), Map.entry("thunder", "Thunder"),
            Map.entry("not_clear", "Not clear"), Map.entry("underground", "Underground"),
            // AI goal conditions.
            Map.entry("always", "Always"), Map.entry("health_below", "Health below %"),
            Map.entry("health_above", "Health above %"), Map.entry("raining", "Raining"),
            Map.entry("in_water", "In water"), Map.entry("target_within", "Target within (blocks)"),
            // AI goal choices.
            Map.entry("hard", "Hard"), Map.entry("normal_and_hard", "Normal and hard"), Map.entry("any", "Any"),
            // Equipment slots.
            Map.entry("mainhand", "Main hand"), Map.entry("offhand", "Off hand"), Map.entry("head", "Head"),
            Map.entry("chest", "Chest"), Map.entry("legs", "Legs"), Map.entry("feet", "Feet"),
            // Ability kinds.
            Map.entry("sun_immune", "Sun immune"), Map.entry("sun_weakness", "Sun weakness"), Map.entry("heal", "Heal"), Map.entry("effect", "Effect"),
            Map.entry("aura_players", "Aura on players"), Map.entry("aura_mobs", "Aura on mobs"),
            Map.entry("on_hit_effect", "Effect on deal damage"), Map.entry("on_hurt_effect", "Effect on take damage"),
            Map.entry("damage_immunity", "Damage immunity"),
            Map.entry("apply_effect", "Apply effect"),
            Map.entry("damage_over_time", "Damage over time"),
            Map.entry("life_steal", "Life steal"),
            Map.entry("attribute_change", "Attribute change"),
            Map.entry("explode", "Explode"), Map.entry("explode_and_die", "Explode and die"),
            Map.entry("spawn", "Spawn"), Map.entry("aura", "Aura"), Map.entry("kill", "Kill"));

    /** The word for a code, or the value as it is when it is not a known code. */
    public static String word(String code) {
        if (code == null) {
            return "";
        }
        String w = WORDS.get(code);
        return w == null ? code : w;
    }

    /** True when the code has a word. */
    public static boolean has(String code) {
        return WORDS.containsKey(code);
    }
}
