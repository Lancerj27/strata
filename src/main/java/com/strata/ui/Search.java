package com.strata.ui;

import java.util.Locale;

/**
 * A LIST'S SEARCH MATCHES WHAT THE ROW SHOWS ("zombie villager" did not find Zombie Villager,
 * because the mob list searched only its id, minecraft:zombie_villager).
 */
public final class Search {

    private Search() {
    }

    /** True when the query is blank, or any text holds it. */
    public static boolean matches(String query, String... texts) {
        String q = norm(query);
        if (q.isEmpty()) {
            return true;
        }
        for (String text : texts) {
            if (text != null && norm(text).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private static String norm(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
