package com.strata.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;


/** How an override row combines with what the game (or the tier) already provides. */

public enum OverrideMode {
    ADD,
    REPLACE,
    DENY;

    /** Applies one LAYER of rules to a base list, returning the new list. */
    public static <T> List<T> apply(List<T> base, List<Rule<T>> layer, Function<T, String> key) {
        if (layer == null || layer.isEmpty()) {
            return base == null ? List.of() : List.copyOf(base);
        }

        List<Rule<T>> replaces = new ArrayList<>();
        List<Rule<T>> adds = new ArrayList<>();
        List<Rule<T>> denies = new ArrayList<>();
        for (Rule<T> rule : layer) {
            switch (rule.mode()) {
                case REPLACE -> replaces.add(rule);
                case DENY -> denies.add(rule);
                default -> adds.add(rule);
            }
        }

        // Insertion-ordered, so the resolved list keeps a stable order a designer can predict: base first,
        // then whatever was added.
        LinkedHashMap<String, T> out = new LinkedHashMap<>();

        if (replaces.isEmpty()) {
            if (base != null) {
                for (T value : base) {
                    out.put(key.apply(value), value);
                }
            }
        } else {
            // One replace row makes the whole layer a replacement.
            for (Rule<T> rule : replaces) {
                out.put(key.apply(rule.value()), rule.value());
            }
        }

        for (Rule<T> rule : adds) {
            out.put(key.apply(rule.value()), rule.value());
        }
        for (Rule<T> rule : denies) {
            out.remove(key.apply(rule.value()));
        }

        return List.copyOf(out.values());
    }

    /** Blank, unknown or malformed all read as ADD - the harmless one. */
    public static OverrideMode parse(String raw) {
        if (raw == null) {
            return ADD;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "replace", "only", "exclusive" -> REPLACE;
            case "deny", "remove", "exclude", "block" -> DENY;
            default -> ADD;
        };
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
