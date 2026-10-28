package com.strata.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** THE GAME'S SPECIAL SPAWNERS, EDITABLE. */
public final class SpecialSpawners {

    private SpecialSpawners() {
    }

    /** What a value is. */
    public enum Kind { SWITCH, WHOLE, PERCENT, MOB, VILLAGER_TYPE, PROFESSION }

    /**
     * One setting.
     *
     * @param spawner the spawner's id: phantoms, patrols, sieges, wandering_trader, cats, villagers
     * @param key the setting's id
     * @param name its name in the editor
     * @param kind what its value is
     * @param game the game's value, as the editor shows it ("" for the game's own choice)
     * @param what what it does, in words
     * @param globalOnly true for a setting of the spawner's clock: Global only
     */
    public record Setting(String spawner, String key, String name, Kind kind, String game, String what,
                          boolean globalOnly) {
    }

    public static final String PHANTOMS = "phantoms";
    public static final String PATROLS = "patrols";
    public static final String SIEGES = "sieges";
    public static final String TRADER = "wandering_trader";
    public static final String CATS = "cats";
    public static final String VILLAGERS = "villagers";

    public static final String ON = "On";
    public static final String OFF = "Off";

    /** Each spawner's name in words, in the editor's order. */
    public static final Map<String, String> SPAWNERS = new LinkedHashMap<>();

    /**
     * THE SWITCH OVER THEM ALL: Off stops every special spawner there and every mob set to Spawn as Special.
     */
    public static final String SPECIAL = "special";

    /**
     * The mob a spawner makes only as a Special mob: set to Normal, the spawner stops making it and it spawns
     * from the biome lists like any mob.
     */
    public static final Map<String, String> MOB_OF = Map.of(PHANTOMS, "minecraft:phantom",
            TRADER, "minecraft:wandering_trader", CATS, "minecraft:cat");

    /** The mobs that start as Special: the ones the game makes only by special means. */
    public static final java.util.Set<String> SPECIAL_BY_DEFAULT = java.util.Set.of("minecraft:phantom",
            "minecraft:wandering_trader", "minecraft:trader_llama", "minecraft:cat", "minecraft:iron_golem");

    /** The table's spawner id for a mob's Spawn as value (Global only; the setting is the mob's id). */
    public static final String MOBS = "mobs";

    /** THE SPECIAL FLAG. */
    public static final String FLAGGED = "flagged";

    /** The mobs that start flagged Special: the ones that start as Special, and the villager. */
    public static final java.util.Set<String> FLAGGED_BY_DEFAULT;

    static {
        java.util.Set<String> flagged = new java.util.TreeSet<>(SPECIAL_BY_DEFAULT);
        flagged.add("minecraft:villager");
        FLAGGED_BY_DEFAULT = java.util.Collections.unmodifiableSet(flagged);
    }

    /** A mob's Special flag (pure): its own value on Global, else whether it starts flagged. */
    public static boolean flagged(Map<String, String> rows, String entity) {
        String own = rows.get("||" + FLAGGED + "|" + entity);
        return own == null || own.isBlank() ? FLAGGED_BY_DEFAULT.contains(entity) : Boolean.parseBoolean(own.strip());
    }

    /** Every setting, in the editor's order. */
    public static final List<Setting> SETTINGS = new ArrayList<>();

    private static void spawner(String id, String name) {
        SPAWNERS.put(id, name);
    }

    private static void add(String spawner, String key, String name, Kind kind, String game, String what) {
        SETTINGS.add(new Setting(spawner, key, name, kind, game, what, false));
    }

    private static void clock(String spawner, String key, String name, Kind kind, String game, String what) {
        SETTINGS.add(new Setting(spawner, key, name, kind, game, what, true));
    }

    static {
        spawner(SPECIAL, "Special mob spawns");
        add(SPECIAL, "enabled", "Special mob spawns", Kind.SWITCH, ON,
                "Off: no special spawner works here, and no mob set to Spawn as Special spawns here.");
        spawner(PHANTOMS, "Phantoms");
        add(PHANTOMS, "enabled", "Enabled", Kind.SWITCH, ON,
                "Off: the phantom spawner makes no phantoms here.");
        clock(PHANTOMS, "check_seconds", "Tries every (seconds)", Kind.WHOLE, "60",
                "How often it tries, at the least (Global only).");
        clock(PHANTOMS, "check_extra_seconds", "Plus up to (seconds)", Kind.WHOLE, "59",
                "A random wait added to each try (Global only).");
        add(PHANTOMS, "night_only", "Only at night or in a storm", Kind.SWITCH, ON,
                "On: only while the sky is dark (the game's sky darkness 5 or more).");
        add(PHANTOMS, "open_sky", "Only under open sky", Kind.SWITCH, ON,
                "On: only over a player above sea level who sees the sky.");

        add(PHANTOMS, "difficulty_roll", "Local difficulty roll", Kind.SWITCH, ON,
                "On: the harder the area (local difficulty 0 to 3), the likelier each try.");
        add(PHANTOMS, "count_min", "Fewest at once", Kind.WHOLE, "1", "The fewest phantoms in a group.");
        add(PHANTOMS, "count_per_difficulty", "More per difficulty", Kind.WHOLE, "1",
                "The most is Fewest + this x the difficulty (Easy 1, Normal 2, Hard 3).");

        spawner(PATROLS, "Pillager patrols");
        add(PATROLS, "enabled", "Enabled", Kind.SWITCH, ON, "Off: no patrols here.");
        clock(PATROLS, "check_seconds", "Tries every (seconds)", Kind.WHOLE, "600",
                "How often it tries, at the least (Global only).");
        clock(PATROLS, "check_extra_seconds", "Plus up to (seconds)", Kind.WHOLE, "60",
                "A random wait added to each try (Global only).");

        add(PATROLS, "chance", "Chance %", Kind.PERCENT, "20", "Each try's chance to send a patrol.");
        add(PATROLS, "not_near_village", "Not near a village", Kind.SWITCH, ON,
                "On: no patrol while the player is in or near a village.");
        add(PATROLS, "distance_min", "Nearest (blocks)", Kind.WHOLE, "24",
                "How near the player, on each side, a patrol starts, at the least.");
        add(PATROLS, "distance_max", "Farthest (blocks)", Kind.WHOLE, "47",
                "How far from the player, on each side, a patrol starts, at the most.");
        add(PATROLS, "members_base", "Members", Kind.WHOLE, "1",
                "Members with no local difficulty; each point of it, rounded up, adds the next value.");
        add(PATROLS, "members_per_difficulty", "More per difficulty", Kind.WHOLE, "1",
                "Members added for each point of local difficulty (0 to 6.75), rounded up.");
        add(PATROLS, "leader", "Leader", Kind.MOB, "minecraft:pillager",
                "The mob that leads, with the ominous banner.");
        add(PATROLS, "member", "Member", Kind.MOB, "minecraft:pillager", "The mob of the other members.");

        spawner(SIEGES, "Village sieges");
        add(SIEGES, "enabled", "Enabled", Kind.SWITCH, ON, "Off: no sieges here. A siege comes only in the dark, as the game's.");
        clock(SIEGES, "chance", "Chance % each night", Kind.PERCENT, "10",
                "At midnight, the chance of a siege that night (Global only).");
        add(SIEGES, "zombies", "Mobs", Kind.WHOLE, "20", "How many mobs a siege sends, one each tick or two.");
        add(SIEGES, "mob", "Mob", Kind.MOB, "minecraft:zombie", "The mob a siege sends.");
        add(SIEGES, "distance", "Distance (blocks)", Kind.WHOLE, "32",
                "How far from the player, in the village, the siege starts.");

        spawner(TRADER, "Wandering trader");
        add(TRADER, "enabled", "Enabled", Kind.SWITCH, ON, "Off: no wandering trader here.");
        clock(TRADER, "check_seconds", "Tries every (seconds)", Kind.WHOLE, "1200",
                "How often it tries: a game day (Global only).");
        clock(TRADER, "chance_start", "Chance % at first", Kind.PERCENT, "25",
                "The first try's chance, and the chance after a trader comes (Global only).");
        clock(TRADER, "chance_step", "Chance % added", Kind.PERCENT, "25",
                "Added to the chance after each try with no trader (Global only).");
        clock(TRADER, "chance_max", "Chance % at most", Kind.PERCENT, "75",
                "The chance never goes over this (Global only).");
        add(TRADER, "spawn_chance", "Chance % at the player", Kind.PERCENT, "10",
                "Once the try passes, the chance it comes to the player picked.");
        add(TRADER, "llamas", "Trader llamas", Kind.WHOLE, "2", "How many trader llamas come with it.");
        add(TRADER, "stay_seconds", "Stays (seconds)", Kind.WHOLE, "2400", "How long it stays before it leaves.");
        add(TRADER, "distance", "Distance (blocks)", Kind.WHOLE, "48",
                "How far from the player's village bell (or the player), at the most, it comes.");

        spawner(CATS, "Village cats");
        add(CATS, "enabled", "Enabled", Kind.SWITCH, ON, "Off: no cats come to villages or witch huts here.");
        clock(CATS, "check_seconds", "Tries every (seconds)", Kind.WHOLE, "60", "How often it tries (Global only).");
        add(CATS, "village_beds", "Village beds", Kind.WHOLE, "5",
                "A village needs this many claimed beds (within 48 blocks) before a cat comes.");
        add(CATS, "village_max", "Cats per village", Kind.WHOLE, "5",
                "No cat comes while this many are within 48 blocks.");
        add(CATS, "hut_max", "Cats per witch hut", Kind.WHOLE, "1",
                "No cat comes while this many are within 16 blocks of the hut.");

        spawner(VILLAGERS, "Villagers");
        add(VILLAGERS, "golems", "Iron golem summoning", Kind.SWITCH, ON,
                "Off: villagers summon no iron golems here (when they panic, or gossip, and sleep).");
        add(VILLAGERS, "breeding", "Breeding", Kind.SWITCH, ON, "Off: villagers here have no babies.");
        add(VILLAGERS, "villager_type", "Villager type", Kind.VILLAGER_TYPE, "",
                "The look of a villager new to the world here. Default: the game's, by its biome.");
        add(VILLAGERS, "profession", "Profession", Kind.PROFESSION, "",
                "The profession of a grown villager new to the world here, kept without a job site."
                        + " Default: the game's, from a job site. A villager that has traded keeps its own.");
    }

    /** The setting of a spawner and key, or null. */
    public static Setting setting(String spawner, String key) {
        for (Setting s : SETTINGS) {
            if (s.spawner().equals(spawner) && s.key().equals(key)) {
                return s;
            }
        }
        return null;
    }

    /** The mob editor's Spawn as choices, as it shows them. */
    public static final List<String> SPAWN_AS = List.of("Normal", "Special");

    /**
     * A mob's Spawn as (pure): Normal for a mob not flagged Special; else its own value on
     * Global, else Special for the mobs the game makes only by special means, else Normal.
     *
     * @param rows as #value reads them
     */
    public static boolean special(Map<String, String> rows, String entity) {
        // Only a mob flagged Special spawns as one.
        if (!flagged(rows, entity)) {
            return false;
        }
        String own = rows.get("||" + MOBS + "|" + entity);
        return own == null || own.isBlank() ? SPECIAL_BY_DEFAULT.contains(entity) : "Special".equalsIgnoreCase(own.strip());
    }

    /** Joins a mob and its variant in a Spawn as key: "minecraft:phantom#night_bat". */
    public static final String VARIANT_SEP = "#";

    /** A VARIANT'S Spawn as (pure): its own value on Global, else its base mob's. */
    public static boolean special(Map<String, String> rows, String entity, String variant) {
        if (variant == null || variant.isEmpty()) {
            return special(rows, entity);
        }
        if (!flagged(rows, entity)) {
            return false;
        }
        String own = rows.get("||" + MOBS + "|" + entity + VARIANT_SEP + variant);
        return own == null || own.isBlank() ? special(rows, entity) : "Special".equalsIgnoreCase(own.strip());
    }

    /**
     * A setting's value at a place (pure): the biome's row, else its tier's, else Global's, else the game's.
     *
     * @param rows "scope|scope id|spawner|key" to the value; scope "" is Global
     */
    public static String value(Map<String, String> rows, Setting s, String tier, String biome) {
        List<String> scopes = new ArrayList<>();
        if (!s.globalOnly()) {
            if (biome != null && !biome.isEmpty()) scopes.add("biome|" + biome);
            if (tier != null && !tier.isEmpty()) scopes.add("tier|" + tier);
        }
        scopes.add("|");
        for (String scope : scopes) {
            String v = rows.get(scope + "|" + s.spawner() + "|" + s.key());
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return s.game();
    }

    /** Why a typed value cannot be used, or null. */
    public static String problem(Setting s, String typed) {
        String v = typed == null ? "" : typed.strip();
        return switch (s.kind()) {
            case SWITCH -> ON.equalsIgnoreCase(v) || OFF.equalsIgnoreCase(v) ? null : "The value must be On or Off";
            case WHOLE -> whole(v) >= 0 ? null : "The value must be a whole number, 0 or more";
            case PERCENT -> whole(v) >= 0 && whole(v) <= 100 ? null : "The value must be a whole number, 0 to 100";
            case MOB -> v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? null : "Pick a mob";
            case VILLAGER_TYPE, PROFESSION -> v.isEmpty() || v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? null
                    : "Pick one, or Default";
        };
    }

    /** A whole number, or -1. */
    public static int whole(String v) {
        try {
            return Integer.parseInt(v == null ? "" : v.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A game day in ticks: the game counts Time Since Last Rest in ticks. */
    public static final int DAY_TICKS = 24000;

    /** The phantom's days awake as the game's source has it (PhantomSpawner: 72000 ticks). */
    public static final int GAME_REST_DAYS = 3;

    /** The patrols' first day as the game's source has it (PatrolSpawner: day 5). */
    public static final int GAME_PATROL_DAY = 5;

    /** The pillager (PatrolSpawner makes EntityType.PILLAGER). */
    public static final String PILLAGER = "minecraft:pillager";

    /** The phantom (PhantomSpawner makes EntityType.PHANTOM). */
    public static final String PHANTOM = "minecraft:phantom";

    /** The fewest days of a Time since rest value ("3+", "2-5", "4"), or -1 when the value has none. */
    public static int restDays(String value) {
        String v = value == null ? "" : value.strip();
        try {
            if (v.endsWith("+")) {
                return Integer.parseInt(v.substring(0, v.length() - 1).strip());
            }
            int dash = v.indexOf('-', 1);
            return Integer.parseInt((dash > 0 ? v.substring(0, dash) : v).strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The game's insomnia roll with its days (PhantomSpawner): a player awake a number of ticks passes when a
     * draw in [0, those ticks) is at least the days in ticks, so the longer awake, the likelier.
     *
     * @param draw a draw in [0, max(restTicks, 1))
     */
    public static boolean restRoll(int restTicks, int days, int draw) {
        return draw >= (long) Math.max(0, days) * DAY_TICKS;
    }

    /** A switch value as a flag: On is true. */
    public static boolean on(String v) {
        return ON.equalsIgnoreCase(v == null ? "" : v.strip());
    }
}
