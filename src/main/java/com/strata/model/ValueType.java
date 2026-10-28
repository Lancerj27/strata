package com.strata.model;

import java.util.List;
import java.util.Locale;

/**
 * WHAT KIND OF VALUE A SHEET ROW TAKES.
 *
 * @param shape which control
 * @param choices the words a BOOL or ENUM takes (BOOL: true, false)
 * @param kind the registry a REGISTRY picks from, else null
 * @param min the lowest a WHOLE or NUMBER takes
 * @param max the highest a WHOLE or NUMBER takes
 */
public record ValueType(Shape shape, List<String> choices, Registry kind, double min, double max) {

    /** The controls. */
    public enum Shape { BOOL, ENUM, REGISTRY, WHOLE, NUMBER, TEXT }

    /** The registries a REGISTRY value is picked from. */
    public enum Registry { ITEM, BLOCK, ENTITY, LIVING_MOB, EFFECT, ATTRIBUTE, PARTICLE, SOUND, BIOME, STRUCTURE,
        DAMAGE_TYPE, ENCHANTMENT, TEXTURE }

    public static final List<String> BOOLEANS = List.of("true", "false");

    /** A switch: true or false. */
    public static final ValueType BOOL = new ValueType(Shape.BOOL, BOOLEANS, null, 0, 0);
    /** Free text. */
    public static final ValueType TEXT = new ValueType(Shape.TEXT, List.of(), null, 0, 0);
    /** Any number. */
    public static final ValueType NUMBER = number(-Double.MAX_VALUE, Double.MAX_VALUE);
    /** Any whole number. */
    public static final ValueType WHOLE = whole(Long.MIN_VALUE, Long.MAX_VALUE);

    /** One of a short list of words. */
    public static ValueType choice(List<String> words) {
        return new ValueType(Shape.ENUM, List.copyOf(words), null, 0, 0);
    }

    /** One entry of a registry, from the searchable picker. */
    public static ValueType registry(Registry kind) {
        return new ValueType(Shape.REGISTRY, List.of(), kind, 0, 0);
    }

    /** A whole number from min to max. */
    public static ValueType whole(long min, long max) {
        return new ValueType(Shape.WHOLE, List.of(), null, min, max);
    }

    /** A number from min to max. */
    public static ValueType number(double min, double max) {
        return new ValueType(Shape.NUMBER, List.of(), null, min, max);
    }

    /** True when the value is chosen from a known set (a dropdown or a picker), not typed. */
    public boolean isChosen() {
        return shape == Shape.BOOL || shape == Shape.ENUM || shape == Shape.REGISTRY;
    }

    /** The type of a value that is true or false, or free text for any other. */
    public static ValueType ofSwitchWord(String shown) {
        String s = shown == null ? "" : shown.strip().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("false") ? BOOL : TEXT;
    }

    /** Why a typed value is outside this type, or null. */
    public String problem(String typed) {
        String v = typed == null ? "" : typed.strip();
        switch (shape) {
            case WHOLE -> {
                try {
                    long n = Long.parseLong(v);
                    if (n < min || n > max) {
                        return "A whole number from " + (long) min + " to " + (long) max;
                    }
                } catch (NumberFormatException e) {
                    return "A whole number, like 5";
                }
            }
            case NUMBER -> {
                try {
                    double n = Double.parseDouble(v);
                    if (!Double.isFinite(n) || n < min || n > max) {
                        return min <= -Double.MAX_VALUE ? "A number up to " + trim(max)
                                : max >= Double.MAX_VALUE ? "A number from " + trim(min) + " up"
                                : "A number from " + trim(min) + " to " + trim(max);
                    }
                } catch (NumberFormatException e) {
                    return "A number, like 1.5";
                }
            }
            case BOOL, ENUM -> {
                if (!choices.contains(v)) {
                    return "One of: " + String.join(", ", choices);
                }
            }
            default -> {
            }
        }
        return null;
    }

    private static String trim(double d) {
        return d >= Double.MAX_VALUE ? "any" : d <= -Double.MAX_VALUE ? "any"
                : d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
