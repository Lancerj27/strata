package com.strata.model;

/**
 * One override row: a value, and what it does to the layer beneath it.
 *
 * @param value the thing being added, denied or replaced with
 * @param mode how it combines - see OverrideMode
 */
public record Rule<T>(T value, OverrideMode mode) {

    public static <T> Rule<T> add(T value) {
        return new Rule<>(value, OverrideMode.ADD);
    }

    public static <T> Rule<T> deny(T value) {
        return new Rule<>(value, OverrideMode.DENY);
    }

    public static <T> Rule<T> replace(T value) {
        return new Rule<>(value, OverrideMode.REPLACE);
    }
}
