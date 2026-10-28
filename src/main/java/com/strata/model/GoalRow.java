package com.strata.model;

import java.util.Map;

/**
 * One goal in a mob's AI list, or in an archetype.
 *
 * @param target true for the WHO list (target selector)
 * @param priority lower runs first, 0 to 99
 * @param goal a GoalType's simple name (built), or a class name (game)
 * @param game true for a GAME row
 * @param settings a built row's settings (GoalType keys); empty for a game row
 * @param condition when the goal may run
 * @param enabled false switches the goal off without losing the row
 */
public record GoalRow(boolean target, int priority, String goal, boolean game, Map<String, String> settings,
                    GoalCondition condition, boolean enabled) {

    /** The row a mob's live goal seeds: built when Strata can build it, else the game's own. */
    public static GoalRow fromGame(GameGoal goal) {
        boolean built = goal.buildable();
        return new GoalRow(goal.target(), Math.max(0, goal.priority()), built ? goal.name() : goal.className(),
                !built, built ? Map.copyOf(goal.settings()) : Map.of(), GoalCondition.ALWAYS, true);
    }

    /** A built row of a type, at the game's defaults. */
    public static GoalRow built(GoalType.Type type, int priority) {
        return new GoalRow(type.target(), priority, type.goal(), false, type.defaults(), GoalCondition.ALWAYS, true);
    }

    /** The goal's short name, from GoalGuide. */
    public String name() {
        return GoalGuide.of(goal).name();
    }

    /** The same row at another priority. */
    public GoalRow withPriority(int newPriority) {
        return new GoalRow(target, newPriority, goal, game, settings, condition, enabled);
    }

    /** The same row switched on or off. */
    public GoalRow withEnabled(boolean on) {
        return new GoalRow(target, priority, goal, game, settings, condition, on);
    }

    /** The same row with another condition. */
    public GoalRow withCondition(GoalCondition newCondition) {
        return new GoalRow(target, priority, goal, game, settings, newCondition, enabled);
    }

    /** Why this row cannot be saved, or null. */
    public String problem() {
        if (priority < 0 || priority > 99) {
            return name() + ": the priority must be 0 to 99";
        }
        if (game) {
            return goal.isBlank() ? "A game row names no goal" : null;
        }
        GoalType.Type type = GoalType.of(goal);
        if (type == null) {
            return "Strata cannot build " + goal;
        }
        if (type.target() != target) {
            return name() + " belongs on the " + (type.target() ? "target goals" : "goals") + " list";
        }
        return GoalType.problem(type, settings);
    }
}
