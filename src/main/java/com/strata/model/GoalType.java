package com.strata.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The game goals Strata can BUILD, each with its settings. */
public final class GoalType {

    private GoalType() {
    }

    /** What a setting holds. */
    public enum Kind {
        /** A decimal number. */
        NUMBER,
        /** A whole number. */
        WHOLE,
        /** On or off. */
        FLAG,
        /**
         * Who: an entity id from the registry ("minecraft:turtle", a mod's mob), or "class:" and a class name
         * for a group the game hunts by class (any Monster).: read from the game, not a word list.
         */
        TARGET,
        /** A list of item ids, joined with "+" (a cell and a goal's text use "|" and ","). */
        ITEMS,
        /** One of the setting's choices. */
        CHOICE,
        /** A projectile: an entity id from the registry, the game's or a mod's. */
        PROJECTILE
    }

    /** What a mob must be for the goal to be built for it. */
    public enum Needs { ANY, PATHFINDER, ANIMAL, TAMABLE, BOW, CROSSBOW, RANGED, CREEPER }

    /** The mob sheet's key for the needs a mob type meets (MobExtractor). */
    public static final String NEEDS_MET = "goal_needs";

    /** What a mob must be, in words, for the editor: "a mob that walks paths". */
    public static String needsWord(Needs needs) {
        return switch (needs) {
            case ANY -> "a mob";
            case PATHFINDER -> "a mob that walks paths (bats, ghasts, phantoms and slimes do not)";
            case ANIMAL -> "an animal that breeds";
            case TAMABLE -> "a tameable animal";
            case BOW -> "a monster that shoots (as a skeleton)";
            case CROSSBOW -> "a monster that shoots crossbows (as a pillager)";
            case RANGED -> "a mob that shoots";
            case CREEPER -> "a creeper";
        };
    }

    /**
     * One setting.
     *
     * @param key the name the table and the form use, "speed"
     * @param kind what it holds
     * @param field the game's field that stores it, read from a live goal
     * @param squared true when the game stores the square (attackRadiusSqr)
     * @param dflt the game's default, as text
     * @param label the form's label
     * @param choices the choices of a CHOICE setting, else empty
     * @param min the lowest whole number the game can run; 1 for an interval the game draws a random number
     *     below
     */
    public record Param(String key, Kind kind, String field, boolean squared, String dflt, String label,
                        List<String> choices, int min) {

        public Param(String key, Kind kind, String field, boolean squared, String dflt, String label,
                     List<String> choices) {
            this(key, kind, field, squared, dflt, label, choices, 0);
        }
    }

    /** One buildable goal. */
    public record Type(String goal, boolean target, Needs needs, List<Param> params) {

        /** The setting with this key, or null. */
        public Param param(String key) {
            for (Param p : params) {
                if (p.key().equals(key)) {
                    return p;
                }
            }
            return null;
        }

        /** The game's defaults, key to text. */
        public Map<String, String> defaults() {
            Map<String, String> out = new LinkedHashMap<>();
            params.forEach(p -> out.put(p.key(), p.dflt()));
            return out;
        }
    }

    /** The player, the default target. */
    public static final String PLAYER = "minecraft:player";

    /** A class group's prefix: "class:net.minecraft.world.entity.monster.Monster". */
    public static final String CLASS = "class:";

    /** Whether a target is an entity id or a class group. */
    public static boolean isTarget(String v) {
        return v != null && (v.startsWith(CLASS) ? v.length() > CLASS.length()
                : v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"));
    }

    /** The door-breaking difficulty choices. */
    public static final List<String> DOOR_DIFFICULTIES = List.of("hard", "normal_and_hard", "any");

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    private static Param number(String key, String field, String dflt, String label) {
        return new Param(key, Kind.NUMBER, field, false, dflt, label, List.of());
    }

    private static Param squared(String key, String field, String dflt, String label) {
        return new Param(key, Kind.NUMBER, field, true, dflt, label, List.of());
    }

    private static Param whole(String key, String field, String dflt, String label) {
        return new Param(key, Kind.WHOLE, field, false, dflt, label, List.of());
    }

    /**
     * A whole number of 1 or more: an interval the game passes to nextInt(interval / 2) each tick
     * (RandomStrollGoal#canUse).
     */
    private static Param period(String key, String field, String dflt, String label) {
        return new Param(key, Kind.WHOLE, field, false, dflt, label, List.of(), 1);
    }

    private static Param flag(String key, String field, String dflt, String label) {
        return new Param(key, Kind.FLAG, field, false, dflt, label, List.of());
    }

    private static Param target(String key, String field, String dflt, String label) {
        return new Param(key, Kind.TARGET, field, false, dflt, label, List.of());
    }

    private static Param projectile(String key, String dflt, String label) {
        return new Param(key, Kind.PROJECTILE, "", false, dflt, label, List.of());
    }

    private static void type(String goal, boolean target, Needs needs, Param... params) {
        TYPES.put(goal, new Type(goal, target, needs, List.of(params)));
    }

    private static final String SPEED = "Speed (1 = normal)";

    static {
        // WHO: pick a target.
        type("NearestAttackableTargetGoal", true, Needs.ANY,
                target("target", "targetType", PLAYER, "Hunts"),
                flag("must_see", "mustSee", "true", "Only what it can see"),
                flag("must_reach", "mustReach", "false", "Only what it can reach"),
                whole("interval", "randomInterval", "10", "Looks every ~N ticks"));
        type("HurtByTargetGoal", true, Needs.PATHFINDER,
                flag("alert_others", "alertSameType", "false", "Calls its kind to help"));
        // HOW: attack.
        type("MeleeAttackGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED),
                flag("keep_chasing", "followingTargetEvenIfNotSeen", "false", "Chases out of sight"));
        type("LeapAtTargetGoal", false, Needs.ANY,
                number("jump", "yd", "0.4", "Jump height"));
        type("RangedBowAttackGoal", false, Needs.BOW,
                number("speed", "speedModifier", "1.0", SPEED),
                whole("interval", "attackIntervalMin", "20", "Ticks between shots"),
                squared("range", "attackRadiusSqr", "15", "Shoots from (blocks)"));
        type("RangedCrossbowAttackGoal", false, Needs.CROSSBOW,
                number("speed", "speedModifier", "1.0", SPEED),
                squared("range", "attackRadiusSqr", "8", "Shoots from (blocks)"));
        type("RangedAttackGoal", false, Needs.RANGED,
                number("speed", "speedModifier", "1.25", SPEED),
                whole("interval_min", "attackIntervalMin", "20", "Fastest ticks between shots"),
                whole("interval_max", "attackIntervalMax", "20", "Slowest ticks between shots"),
                number("range", "attackRadius", "10", "Shoots from (blocks)"));
        // ANY MOB FIRES ANY PROJECTILE.
        type("ShootProjectileGoal", false, Needs.ANY,
                projectile("projectile", "minecraft:arrow", "Fires"),
                period("interval", "", "40", "Ticks between shots"),
                number("range", "", "16", "Shoots from (blocks)"),
                number("speed", "", "1.6", "Projectile speed"),
                number("damage", "", "2", "Arrow damage (arrows only)"));
        type("SwellGoal", false, Needs.CREEPER);
        type("OcelotAttackGoal", false, Needs.ANY);
        // HOW: stay alive.
        type("PanicGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.25", SPEED));
        type("AvoidEntityGoal", false, Needs.PATHFINDER,
                target("target", "avoidClass", PLAYER, "Runs from"),
                number("range", "maxDist", "8", "Runs when within (blocks)"),
                number("walk_speed", "walkSpeedModifier", "1.0", "Speed when far"),
                number("sprint_speed", "sprintSpeedModifier", "1.2", "Speed when close"));
        type("RestrictSunGoal", false, Needs.PATHFINDER);
        type("FleeSunGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED));
        type("FloatGoal", false, Needs.ANY);
        // HOW: move around.
        type("RandomStrollGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED),
                period("interval", "interval", "120", "Moves every ~N ticks"));
        type("WaterAvoidingRandomStrollGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED),
                number("chance", "probability", "0.001", "Chance per tick to stroll"));
        type("RandomSwimmingGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED),
                period("interval", "interval", "10", "Swims every ~N ticks"));
        type("WaterAvoidingRandomFlyingGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED));
        type("MoveTowardsRestrictionGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED));
        type("MoveThroughVillageGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED),
                flag("only_at_night", "onlyAtNight", "true", "Only at night"),
                whole("distance", "distanceToPoi", "4", "Stops this near a door (blocks)"),
                flag("doors", "canDealWithDoors", "false", "Goes through doors"));
        type("BreakDoorGoal", false, Needs.ANY,
                new Param("difficulty", Kind.CHOICE, "validDifficulties", false, "hard", "Breaks doors on",
                        DOOR_DIFFICULTIES));
        type("OpenDoorGoal", false, Needs.ANY,
                flag("close", "closeDoor", "true", "Closes the door after"));
        // HOW: look.
        type("LookAtPlayerGoal", false, Needs.ANY,
                target("target", "lookAtType", PLAYER, "Looks at"),
                number("range", "lookDistance", "8", "Looks from (blocks)"),
                number("chance", "probability", "0.02", "Chance per tick to look"));
        type("RandomLookAroundGoal", false, Needs.ANY);
        // HOW: animal life.
        type("TemptGoal", false, Needs.PATHFINDER,
                number("speed", "speedModifier", "1.0", SPEED),
                new Param("items", Kind.ITEMS, "items", false, "minecraft:wheat", "Follows a player holding",
                        List.of()),
                flag("scared", "canScare", "false", "Scared off by quick moves"));
        type("BreedGoal", false, Needs.ANIMAL,
                number("speed", "speedModifier", "1.0", SPEED));
        type("FollowParentGoal", false, Needs.ANIMAL,
                number("speed", "speedModifier", "1.1", SPEED));
        type("EatBlockGoal", false, Needs.ANY);
        type("FollowOwnerGoal", false, Needs.TAMABLE,
                number("speed", "speedModifier", "1.0", SPEED),
                number("start", "startDistance", "10", "Follows when farther than (blocks)"),
                number("stop", "stopDistance", "2", "Stops this near (blocks)"));
        type("FollowMobGoal", false, Needs.ANY,
                number("speed", "speedModifier", "1.0", SPEED),
                number("stop", "stopDistance", "3", "Stops this near (blocks)"),
                number("area", "areaSize", "7", "Looks for mobs within (blocks)"));
    }

    /** The buildable type for a simple class name, or null. */
    public static Type of(String goal) {
        String simple = goal == null ? "" : goal.substring(goal.lastIndexOf('.') + 1);
        return TYPES.get(simple);
    }

    /** Every buildable type, in the order the editor lists them. */
    public static List<Type> all() {
        return List.copyOf(TYPES.values());
    }

    // ---- settings as text: "speed=1.2;keep_chasing=true" ----

    /** Settings to the table's text. */
    public static String encode(Map<String, String> settings) {
        StringBuilder out = new StringBuilder();
        settings.forEach((k, v) -> {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(k).append('=').append(v == null ? "" : v);
        });
        return out.toString();
    }

    /** The table's text to settings. */
    public static Map<String, String> decode(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                out.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip());
            }
        }
        return out;
    }

    /** The type's defaults with the given settings over them. */
    public static Map<String, String> withDefaults(Type type, Map<String, String> settings) {
        Map<String, String> out = type.defaults();
        settings.forEach((k, v) -> {
            if (out.containsKey(k)) {
                out.put(k, v);
            }
        });
        return out;
    }

    /** Why one setting's value cannot be used, or null. */
    public static String problem(Param param, String value) {
        String v = value == null ? "" : value.strip();
        return switch (param.kind()) {
            case NUMBER -> {
                try {
                    double d = Double.parseDouble(v);
                    yield Double.isFinite(d) && d >= 0 ? null : param.label() + ": a number, 0 or more";
                } catch (NumberFormatException e) {
                    yield param.label() + ": a number";
                }
            }
            case WHOLE -> {
                try {
                    yield Integer.parseInt(v) >= param.min() ? null
                            : param.label() + ": a whole number, " + param.min() + " or more";
                } catch (NumberFormatException e) {
                    yield param.label() + ": a whole number";
                }
            }
            case FLAG -> v.equals("true") || v.equals("false") ? null : param.label() + ": on or off";
            case TARGET -> isTarget(v)
                    ? null : param.label() + ": pick who";
            case ITEMS -> v.isEmpty() ? param.label() + ": pick at least one item" : null;
            case CHOICE -> param.choices().contains(v) ? null : param.label() + ": pick one";
            case PROJECTILE -> v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? null : param.label() + ": pick a projectile";
        };
    }

    /** An ITEMS value with its last item taken off. */
    public static String withoutLastItem(String joined) {
        String v = joined == null ? "" : joined.strip();
        int plus = v.lastIndexOf('+');
        return plus < 0 ? "" : v.substring(0, plus);
    }

    /** Why a whole settings map cannot be used, or null. */
    public static String problem(Type type, Map<String, String> settings) {
        for (Map.Entry<String, String> e : withDefaults(type, settings).entrySet()) {
            String p = problem(type.param(e.getKey()), e.getValue());
            if (p != null) {
                return p;
            }
        }
        return null;
    }
}
