package com.strata.model;

/**
 * A single spawn condition.
 *
 * @param type condition type (time, light, y_level, weather, moon, structure, surface, distance,
 *     time_since_rest)
 * @param value type-specific value string (e.g. "night", "0-7", "100-256", "rain", "0,4",
 *     "minecraft:stronghold", "underground", "500+")
 */
public record SpawnRule(Type type, String value) {

    public enum Type {
        TIME,
        LIGHT,
        Y_LEVEL,
        WEATHER,
        MOON_PHASE,
        STRUCTURE,
        SURFACE,
        DISTANCE,
        /** Days since the nearest player last slept: the game's Time Since Last Rest. */
        TIME_SINCE_REST,
        /** The world's day count (the game's day time / 24000), as the patrols' first day. */
        WORLD_DAY,
        /**
         * The nearest player's experience level, or the named player's when a special spawner passes one.
         */
        PLAYER_LEVEL;

        public static Type parse(String raw) {
            if (raw == null) return null;
            return switch (raw.strip().toLowerCase(java.util.Locale.ROOT)) {
                case "time" -> TIME;
                case "light" -> LIGHT;
                case "y_level", "ylevel", "y" -> Y_LEVEL;
                case "weather" -> WEATHER;
                case "moon", "moon_phase" -> MOON_PHASE;
                case "structure" -> STRUCTURE;
                case "surface" -> SURFACE;
                case "distance", "distance_from_spawn" -> DISTANCE;
                case "time_since_rest", "rest" -> TIME_SINCE_REST;
                case "world_day", "day_count" -> WORLD_DAY;
                case "player_level", "player_xp", "player_xp_level" -> PLAYER_LEVEL;
                default -> null;
            };
        }
    }

    /**
     * True when every row's rules need a player awake (a Time since rest rule), so a new chunk, with no
     * player to judge, places none.
     */
    public static boolean needPlayer(java.util.List<java.util.List<SpawnRule>> rows) {
        return !rows.isEmpty() && rows.stream().allMatch(rules -> rules.stream()
                .anyMatch(r -> r.type() == Type.TIME_SINCE_REST));
    }

    /** The words the editor offers, in display order. */
    public static final java.util.List<String> TIME_CHOICES = java.util.List.of("day", "night", "dusk", "dawn",
            "daylight", "dark");
    public static final java.util.List<String> WEATHER_CHOICES = java.util.List.of("clear", "rain", "thunder", "not_clear");
    public static final java.util.List<String> SURFACE_CHOICES = java.util.List.of("surface", "underground");

    /** The editor's word choices for a type, or null for a typed or picked value. */
    public static java.util.List<String> choicesFor(Type type) {
        if (type == null) return null;
        return switch (type) {
            case TIME -> TIME_CHOICES;
            case WEATHER -> WEATHER_CHOICES;
            case SURFACE -> SURFACE_CHOICES;
            default -> null;
        };
    }

    /** The words SpawnRuleEvaluator matches for each word-valued type. */
    public static final java.util.Set<String> TIME_WORDS = java.util.Set.of("day", "night", "dusk", "dawn",
            "daylight", "dark");

    /**
     * The game's own day check in clear weather, as ticks {first, last} of the day (Level#isDay: the sky's
     * darkness under 4).
     */
    public static volatile int[] gameDaylight = daylight(d -> (float) Math.cos(d));

    /**
     * The ticks of the day the game calls day in clear weather, {first, last}, by its own math
     * (DimensionType#timeOfDay, Level#updateSkyBrightness, Level#isDay), with the cosine given (the game's
     * own is Mth#cos).
     */
    public static int[] daylight(java.util.function.DoubleUnaryOperator cos) {
        boolean[] day = new boolean[24000];
        for (int t = 0; t < 24000; t++) {
            double d0 = t / 24000.0 - 0.25;
            d0 = d0 - Math.floor(d0);
            double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
            float angle = (float) (d0 * 2.0 + d1) / 3.0F;
            double c = cos.applyAsDouble(angle * (float) (Math.PI * 2));
            double d2 = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, c));
            day[t] = (int) ((1.0 - d2) * 11.0) < 4;
        }
        int first = -1;
        for (int t = 0; t < 24000; t++) {
            if (day[t] && !day[(t + 23999) % 24000]) {
                first = t;
            }
        }
        int last = first;
        while (day[(last + 1) % 24000] && (last + 1) % 24000 != first) {
            last = (last + 1) % 24000;
        }
        return new int[]{first, last};
    }
    public static final java.util.Set<String> WEATHER_WORDS = java.util.Set.of(
            "rain", "raining", "thunder", "thundering", "clear", "not_clear", "precipitation");
    public static final java.util.Set<String> SURFACE_WORDS = java.util.Set.of(
            "surface", "above", "underground", "below");

    /** What a condition tests and the value it takes, in words, for the editor's tooltip. */
    public static String help(Type type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case TIME -> "Time of day: day (0-12999), night (13000-23999), dusk, dawn, or a tick range like"
                    + " 0-12000. daylight and dark are the game's own check (the sky's brightness): daylight is"
                    + " about " + gameDaylight[0] + " to " + gameDaylight[1] + " in clear weather, and rain and"
                    + " storms make it dark sooner.";
            case LIGHT -> "Light at the spawn spot, 0 (dark) to 15: a number or a range like 0-7.";
            case Y_LEVEL -> "Height: a number or a range like -64-0.";
            case WEATHER -> "Weather: clear, rain, thunder, or not clear.";
            case MOON_PHASE -> "Moon phase, 0 (full moon) to 7: a number, a list like 1,3, or a range like 0-4.";
            case STRUCTURE -> "Only inside this structure's pieces. Pick it from the list.";
            case SURFACE -> "Surface: under open sky. Underground: no sky above.";
            case DISTANCE -> "Blocks from the world spawn: a range like 100-500, or a minimum like 500+.";
            case TIME_SINCE_REST -> "Days since the nearest player last slept (the game's Time Since Last Rest):"
                    + " a number, a range like 1-3, or a minimum like 3+. The game's phantoms: 3+.";
            case WORLD_DAY -> "The world's day (its day count, from 0): a number, a range like 0-10, or a"
                    + " minimum like 5+. The game's patrols: 5+.";
            case PLAYER_LEVEL -> "The nearest player's experience level: a number like 5, a range like"
                    + " 3-10, or a minimum like 5+. A mob's spawner that names a player reads that"
                    + " player's level instead.";
        };
    }

    /**
     * Why SpawnRuleEvaluator could never match this value, in the designer's words, or null when the value is
     * well formed.
     */
    public static String valueProblem(Type type, String value) {
        String v = value == null ? "" : value.strip();
        String lower = v.toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case TIME -> TIME_WORDS.contains(lower) || rangeValid(v) ? null
                    : "Time must be day, night, dusk, dawn, or a tick range like 0-12000";
            case LIGHT -> rangeValid(v) ? null : "Light must be a number or a range like 0-7";
            case Y_LEVEL -> rangeValid(v) ? null : "Y level must be a number or a range like -64-0";
            case WEATHER -> WEATHER_WORDS.contains(lower) ? null
                    : "Weather must be rain, thunder, clear, or not_clear";
            case MOON_PHASE -> moonValid(v) ? null
                    : "Moon phase must be a number, a list like 1,3, or a range like 0-4";
            case STRUCTURE -> v.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+") ? null
                    : "Structure must be an id like minecraft:stronghold";
            case SURFACE -> SURFACE_WORDS.contains(lower) ? null
                    : "Surface must be surface or underground";
            case DISTANCE -> distanceValid(v) ? null
                    : "Distance must be a range like 100-500, or a minimum like 500+";
            case TIME_SINCE_REST -> distanceValid(v) && !v.startsWith("-") ? null
                    : "Time since rest must be days: a number, a range like 1-3, or a minimum like 3+";
            case WORLD_DAY -> distanceValid(v) && !v.startsWith("-") ? null
                    : "World day must be a day: a number, a range like 0-10, or a minimum like 5+";
            case PLAYER_LEVEL -> LevelRange.problem(v, false);
        };
    }

    private static boolean moonValid(String v) {
        if (v.contains(",")) {
            for (String part : v.split(",")) {
                if (!isInt(part.strip())) return false;
            }
            return true;
        }
        return rangeValid(v);
    }

    private static boolean distanceValid(String v) {
        if (v.endsWith("+")) {
            try {
                return Double.isFinite(Double.parseDouble(v.substring(0, v.length() - 1)));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return rangeValid(v);
    }

    /** True when parseRange can read the value: one integer, or an inclusive range. */
    public static boolean rangeValid(String value) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            return false;
        }
        int separator = separatorIndex(v);
        if (separator > 0) {
            return isInt(v.substring(0, separator).strip()) && isInt(v.substring(separator + 1).strip());
        }
        return isInt(v);
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * "12", "8-15", "-64-0", "-30--10" — a single value or an inclusive range, either end of which may be
     * negative.
     */
    public static boolean parseRange(String value, int actual) {
        String v = value.strip();
        if (v.isEmpty()) {
            return false;
        }
        int separator = separatorIndex(v);
        if (separator > 0) {
            try {
                int min = Integer.parseInt(v.substring(0, separator).strip());
                int max = Integer.parseInt(v.substring(separator + 1).strip());
                return actual >= min && actual <= max;
            } catch (NumberFormatException e) { return false; }
        }
        try {
            return actual == Integer.parseInt(v);
        } catch (NumberFormatException e) { return false; }
    }

    /** Where the range separator is, or -1 for a single value. */
    private static int separatorIndex(String v) {
        for (int i = 1; i < v.length(); i++) {
            if (v.charAt(i) == '-' && v.charAt(i - 1) != '-') {
                return i;
            }
        }
        return -1;
    }
}
