package com.strata.model;

/** A player experience-level test, shared by loot conditions, spawn rules and equipment rows. */
public final class LevelRange {

    /** The lowest level a test can name: a player's experience level never goes below 0. */
    public static final int MIN = 0;

    private final int min;
    private final int max;

    private LevelRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    /** The range a value names, or null when it is blank or malformed. */
    public static LevelRange parse(String value) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            return null;
        }
        if (v.endsWith("+")) {
            Integer n = intOf(v.substring(0, v.length() - 1));
            return n == null || n < MIN ? null : new LevelRange(n, Integer.MAX_VALUE);
        }
        int dash = v.indexOf('-');
        if (dash > 0) {
            Integer lo = intOf(v.substring(0, dash));
            Integer hi = intOf(v.substring(dash + 1));
            if (lo == null || hi == null || lo < MIN || hi < lo) {
                return null;
            }
            return new LevelRange(lo, hi);
        }
        Integer n = intOf(v);
        return n == null || n < MIN ? null : new LevelRange(n, n);
    }

    /** True when a player at this experience level passes the test. */
    public boolean matches(int level) {
        return level >= min && level <= max;
    }

    /** The range in words: "5", "3-10", "5+". */
    public String text() {
        if (max == Integer.MAX_VALUE) {
            return min + "+";
        }
        return min == max ? String.valueOf(min) : min + "-" + max;
    }

    /** The lowest level that passes. */
    public int min() {
        return min;
    }

    /** The highest level that passes, or {@link Integer#MAX_VALUE} for an open top. */
    public int max() {
        return max;
    }

    /** Why a value cannot be read, in the designer's words, or null when it can. */
    public static String problem(String value, boolean allowBlank) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            return allowBlank ? null
                    : "Player level must be a number like 5, a range like 3-10, or a minimum like 5+";
        }
        return parse(v) == null
                ? "Player level must be a number like 5, a range like 3-10, or a minimum like 5+"
                : null;
    }

    private static Integer intOf(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return text();
    }
}
