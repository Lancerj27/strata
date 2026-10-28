package com.strata.data;

import com.strata.model.GoalRow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** goal_sets.csv: the archetype library. */
public final class GoalSetTable {

    private GoalSetTable() {
    }

    public static final String HEADER = String.join(",",
            Schema.GoalSets.GOAL_SET, Schema.GoalSets.ROW, Schema.GoalSets.LIST, Schema.GoalSets.PRIORITY,
            Schema.GoalSets.GOAL, Schema.GoalSets.KIND, Schema.GoalSets.SETTINGS, Schema.GoalSets.CONDITION,
            Schema.GoalSets.CONDITION_VALUE, Schema.GoalSets.ENABLED);

    /** The library in a file. */
    public static Map<String, List<GoalRow>> load(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        return fromRows(CsvTable.read(file));
    }

    /** Table rows to archetypes, by name, rows in order. */
    public static Map<String, List<GoalRow>> fromRows(List<Map<String, String>> rows) {
        Map<String, List<Map<String, String>>> byName = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String name = row.getOrDefault(Schema.GoalSets.GOAL_SET, "").strip();
            if (!name.isEmpty()) {
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(row);
            }
        }
        Map<String, List<GoalRow>> out = new LinkedHashMap<>();
        byName.forEach((name, cells) -> out.put(name, List.copyOf(GoalTable.rows(cells))));
        return out;
    }

    /** One archetype's table rows. */
    public static List<Map<String, String>> toRows(String name, List<GoalRow> rows) {
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> cells = new LinkedHashMap<>();
            cells.put(Schema.GoalSets.GOAL_SET, name);
            cells.put(Schema.GoalSets.ROW, String.valueOf(i));
            cells.putAll(GoalTable.cells(rows.get(i)));
            out.add(cells);
        }
        return out;
    }

    /** The table text for a library: the header, then each archetype's rows. */
    public static String csv(Map<String, List<GoalRow>> library) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        library.forEach((name, rows) -> {
            for (Map<String, String> row : toRows(name, rows)) {
                text.append(String.join(",", row.values())).append('\n');
            }
        });
        return text.toString();
    }
}
