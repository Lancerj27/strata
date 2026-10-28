package com.strata.domain;

import java.util.List;

/** THE RULES FOR SEVERAL PICKED ROWS. */
public final class Picks {

    private Picks() {
    }

    /** The state On / Off sets for rows in these states: Off when any is On, else On. */
    public static boolean switchedOn(List<Boolean> states) {
        return states.stream().noneMatch(Boolean::booleanValue);
    }

    /** How many names a confirmation lists before "and N more". */
    public static final int NAMES_SHOWN = 6;

    /** The names a confirmation lists: "a, b, c", or "a, b,..., and 4 more". */
    public static String listed(List<String> names) {
        int show = Math.min(NAMES_SHOWN, names.size());
        String s = String.join(", ", names.subList(0, show));
        return names.size() > show ? s + ", and " + (names.size() - show) + " more" : s;
    }
}
