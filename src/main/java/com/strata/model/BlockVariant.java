package com.strata.model;

import java.util.Collection;
import java.util.List;

/** A BLOCK VARIANT, AS RULES. */
public final class BlockVariant {

    private BlockVariant() {
    }

    /** The name its item shows. */
    public static final String DISPLAY_NAME = "display_name";

    /** The look keys, in the editor's order. */
    public static final List<String> LOOK_KEYS = List.of(DISPLAY_NAME);

    /** The most a display name may hold. */
    public static final int MAX_NAME = 50;

    /** Why a new variant's name cannot be used, or null. */
    public static String nameProblem(String name, Collection<String> existing) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return "Type a name";
        }
        if (!n.matches("[a-z0-9_]{1,40}")) {
            return "A name is 1 to 40 of a to z, 0 to 9, and _ (for example reinforced_oak_door)";
        }
        return existing.contains(n) ? "A block variant named " + n + " exists" : null;
    }

    /** The name in words: "reinforced_oak_door" -> "Reinforced Oak Door". */
    public static String words(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.split("_")) {
            if (w.isEmpty()) continue;
            sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** What a look value does. */
    public static String what(String key) {
        return DISPLAY_NAME.equals(key) ? "The name its item shows. Blank: the variant's name in words." : "";
    }

    /** Why a typed value cannot be saved, or null: a look key, else a block stats key. */
    public static String problem(String key, String typed) {
        if (DISPLAY_NAME.equals(key)) {
            String v = typed == null ? "" : typed.strip();
            return v.length() > MAX_NAME ? "A display name is at most 50 letters" : null;
        }
        return BlockStats.problem(key, typed);
    }
}
