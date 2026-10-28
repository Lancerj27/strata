package com.strata.client.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Which groups of each list the designer opened or folded, for the session. */
public final class FoldMemory {

    private FoldMemory() {
    }

    private static final Map<String, Set<String>> TOGGLED = new HashMap<>();

    /** The groups toggled in the list with this key: a copy, empty for a list not seen yet. */
    public static Set<String> toggled(String key) {
        Set<String> s = key == null ? null : TOGGLED.get(key);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    /** Remembers the groups toggled in the list with this key. */
    public static void remember(String key, Set<String> toggled) {
        if (key != null) {
            TOGGLED.put(key, Set.copyOf(toggled));
        }
    }
}