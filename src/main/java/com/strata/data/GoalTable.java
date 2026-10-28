package com.strata.data;

import com.strata.model.GoalRow;
import com.strata.model.GoalCondition;
import com.strata.model.GoalType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI rows to table cells and back. */
public final class GoalTable {

    private GoalTable() {
    }

    public static final String TARGET = "target";
    public static final String GOAL = "goal";
    public static final String BUILT = "built";
    public static final String GAME = "game";

    /** mob_goals.csv's header. */
    public static final String HEADER = String.join(",",
            Schema.MobGoals.VARIANT_NAME, Schema.MobGoals.SCOPE, Schema.MobGoals.SCOPE_ID, Schema.MobGoals.ROW,
            Schema.MobGoals.LIST, Schema.MobGoals.PRIORITY, Schema.MobGoals.GOAL, Schema.MobGoals.KIND,
            Schema.MobGoals.SETTINGS, Schema.MobGoals.CONDITION, Schema.MobGoals.CONDITION_VALUE, Schema.MobGoals.ENABLED);

    /** One row's goal cells (no key columns). */
    public static Map<String, String> cells(GoalRow row) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(Schema.MobGoals.LIST, row.target() ? TARGET : GOAL);
        out.put(Schema.MobGoals.PRIORITY, String.valueOf(row.priority()));
        out.put(Schema.MobGoals.GOAL, row.goal());
        out.put(Schema.MobGoals.KIND, row.game() ? GAME : BUILT);
        out.put(Schema.MobGoals.SETTINGS, GoalType.encode(row.settings()));
        out.put(Schema.MobGoals.CONDITION, row.condition().type() == GoalCondition.Type.ALWAYS
                ? "" : row.condition().type().word());
        out.put(Schema.MobGoals.CONDITION_VALUE, row.condition().type().takesValue()
                ? String.valueOf(row.condition().value()) : "");
        out.put(Schema.MobGoals.ENABLED, String.valueOf(row.enabled()));
        return out;
    }

    /** One table row to an AI row. */
    public static GoalRow row(Map<String, String> cells) {
        return new GoalRow(
                TARGET.equals(cell(cells, Schema.MobGoals.LIST)),
                Parse.integer(cells.get(Schema.MobGoals.PRIORITY), 5),
                cell(cells, Schema.MobGoals.GOAL),
                GAME.equals(cell(cells, Schema.MobGoals.KIND)),
                Map.copyOf(GoalType.decode(cell(cells, Schema.MobGoals.SETTINGS))),
                GoalCondition.of(cell(cells, Schema.MobGoals.CONDITION), cell(cells, Schema.MobGoals.CONDITION_VALUE)),
                Parse.flag(cells.get(Schema.MobGoals.ENABLED), true));
    }

    /** Table rows, in row-number order, to AI rows. */
    public static List<GoalRow> rows(List<Map<String, String>> cells) {
        List<Map<String, String>> ordered = new ArrayList<>(cells);
        ordered.sort(Comparator.comparingInt(c -> Parse.integer(c.get(Schema.MobGoals.ROW), 0)));
        List<GoalRow> out = new ArrayList<>();
        ordered.forEach(c -> out.add(row(c)));
        return out;
    }

    private static String cell(Map<String, String> cells, String column) {
        String v = cells.get(column);
        return v == null ? "" : v.strip();
    }
}
