package com.strata.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * When one goal of a behavior may run.
 *
 * @param type the kind of condition
 * @param value its number: a health percent, or a distance in blocks
 */
public record GoalCondition(Type type, double value) {

    /** A goal that always may run. */
    public static final GoalCondition ALWAYS = new GoalCondition(Type.ALWAYS, 0);

    public enum Type {
        ALWAYS, DAY, NIGHT, HEALTH_BELOW, HEALTH_ABOVE, RAINING, IN_WATER, TARGET_WITHIN;

        /** The word a table and the editor use, "health_below". */
        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** True when the condition takes a number. */
        public boolean takesValue() {
            return this == HEALTH_BELOW || this == HEALTH_ABOVE || this == TARGET_WITHIN;
        }

        public static Type parse(String word) {
            String w = word == null ? "" : word.strip().toUpperCase(Locale.ROOT);
            if (w.isEmpty()) {
                return ALWAYS;
            }
            for (Type type : values()) {
                if (type.name().equals(w)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** The condition words, for the editor's dropdown. */
    public static final List<String> CHOICES =
            Arrays.stream(Type.values()).map(Type::word).toList();

    /**
     * What the runtime reads off the mob each time a goal asks.
     *
     * @param targetDistance blocks to the mob's target, or a negative number when it has none
     */
    public record State(boolean day, double healthPct, boolean raining, boolean inWater,
                        double targetDistance) {
    }

    /**
     * True for a day time of the world clock: from sunrise (tick 23000 of the day before) to sunset (tick
     * 13000).
     */
    public static boolean isDayTime(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t < 13000L || t >= 23000L;
    }

    /** True when the goal may run in this state. */
    public boolean test(State state) {
        return switch (type) {
            case ALWAYS -> true;
            case DAY -> state.day();
            case NIGHT -> !state.day();
            case HEALTH_BELOW -> state.healthPct() < value;
            case HEALTH_ABOVE -> state.healthPct() > value;
            case RAINING -> state.raining();
            case IN_WATER -> state.inWater();
            case TARGET_WITHIN -> state.targetDistance() >= 0 && state.targetDistance() <= value;
        };
    }

    /** Why a condition cannot be used, or null when it can. */
    public static String problem(String typeWord, String valueText) {
        Type type = Type.parse(typeWord);
        if (type == null) {
            return "Unknown condition '" + typeWord + "'";
        }
        if (!type.takesValue()) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(valueText == null ? "" : valueText.strip());
        } catch (NumberFormatException e) {
            return type == Type.TARGET_WITHIN ? "Type a distance in blocks" : "Type a health percent";
        }
        if (type == Type.TARGET_WITHIN) {
            return value > 0 ? null : "The distance must be above 0";
        }
        return value >= 0 && value <= 100 ? null : "The health percent must be 0 to 100";
    }

    /** A condition from a table's two cells. */
    public static GoalCondition of(String typeWord, String valueText) {
        if (problem(typeWord, valueText) != null) {
            return ALWAYS;
        }
        Type type = Type.parse(typeWord);
        double value = type.takesValue() ? Double.parseDouble(valueText.strip()) : 0;
        return new GoalCondition(type, value);
    }
}
