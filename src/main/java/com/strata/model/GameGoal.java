package com.strata.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One goal a mob has in the game, as Strata reads it: which list it is on, its priority, its
 * class with the classes it extends, its exact class, and its settings read from the live goal.
 *
 * @param target true for a goal on the target selector: it picks who
 * @param priority the goal's priority, lower runs first; -1 when unknown
 * @param chain the simple class names, the goal's own first
 * @param className the goal's exact class name, or "" when not known
 * @param settings the settings read from the live goal (GoalType keys); empty for a goal Strata cannot build
 */
public record GameGoal(boolean target, int priority, List<String> chain, String className,
                       Map<String, String> settings) {

    /** A goal read without its class or settings (before ). */
    public GameGoal(boolean target, int priority, List<String> chain) {
        this(target, priority, chain, "", Map.of());
    }

    /** The goal's own simple name, or "" for none. */
    public String name() {
        return chain.isEmpty() ? "" : chain.getFirst();
    }

    /** True when the goal is, or extends, the goal with this simple name. */
    public boolean is(String simpleName) {
        return chain.contains(simpleName);
    }

    /** True when Strata can build this goal again with edited settings: its exact class is a GoalType. */
    public boolean buildable() {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        return !className.isEmpty() && GoalType.of(simple) != null && simple.equals(name());
    }

    /** "t|2|SpiderTargetGoal<NearestAttackableTargetGoal<TargetGoal|net...SpiderTargetGoal|k=v;k=v". */
    public String encode() {
        return (target ? "t" : "g") + "|" + priority + "|" + String.join("<", chain)
                + "|" + className + "|" + GoalType.encode(settings);
    }

    /** Reads {@link #encode}. */
    public static GameGoal decode(String text) {
        String s = text == null ? "" : text.strip();
        String[] parts = s.split("\\|", 5);
        if (parts.length < 3) {
            return new GameGoal(false, -1, s.isEmpty() ? List.of() : List.of(s));
        }
        int priority;
        try {
            priority = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            priority = -1;
        }
        List<String> chain = new ArrayList<>();
        for (String name : parts[2].split("<")) {
            if (!name.isBlank()) {
                chain.add(name.strip());
            }
        }
        String className = parts.length > 3 ? parts[3].strip() : "";
        Map<String, String> settings = parts.length > 4 ? GoalType.decode(parts[4]) : Map.of();
        return new GameGoal("t".equals(parts[0]), priority, List.copyOf(chain), className, Map.copyOf(settings));
    }
}
