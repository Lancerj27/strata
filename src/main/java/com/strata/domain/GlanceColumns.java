package com.strata.domain;

import com.strata.Strata;
import com.strata.data.Paths;
import com.strata.game.RegistryQuery;
import com.strata.model.BlockStats;
import com.strata.model.ItemStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** THE AT-A-GLANCE COLUMNS of the lists. */
public final class GlanceColumns {

    private GlanceColumns() {
    }

    public static final String MOBS = "mobs";
    public static final String ITEMS = "items";
    public static final String BLOCKS = "blocks";

    /** The most columns a list may have, whatever the room. */
    public static final int MAX = com.strata.ui.screen.GlanceLayout.MAX;

    private static final String FILE = "ui-glance-columns.txt";
    private static final String OLD_FILE = "ui-stat-columns.txt";

    /** The columns of a list until the user changes them. */
    public static List<String> defaults(String list) {
        return switch (list) {
            case MOBS -> StatColumns.DEFAULT;
            case ITEMS -> List.of(ItemStats.MAX_STACK_SIZE, ItemStats.MAX_DAMAGE,
                    ItemStats.attributeKey("minecraft:generic.attack_damage", "mainhand"));
            case BLOCKS -> List.of(BlockStats.HARDNESS, BlockStats.BLAST_RESISTANCE, BlockStats.TOOL);
            default -> List.of();
        };
    }

    /** The list's chosen columns, as keys. */
    public static List<String> chosen(String list) {
        return chosen(Paths.ROOT, list);
    }

    public static List<String> chosen(Path root, String list) {
        List<String> own = sections(root).get(list);
        return own == null ? defaults(list) : own;
    }

    /** Adds a column at the end. */
    public static WriteReport add(Path root, String list, String key) {
        List<String> columns = new ArrayList<>(chosen(root, list));
        String k = key == null ? "" : key.strip();
        if (k.isEmpty()) {
            return WriteReport.refused("Pick what the column shows");
        }
        if (columns.contains(k)) {
            return WriteReport.refused("There is a column for that already");
        }
        if (columns.size() >= MAX) {
            return WriteReport.refused("A list has at most " + MAX + " columns: remove one first");
        }
        columns.add(k);
        return save(root, list, columns);
    }

    public static WriteReport add(String list, String key) {
        return add(Paths.ROOT, list, key);
    }

    /** Removes the column at an index. */
    public static WriteReport remove(Path root, String list, int index) {
        List<String> columns = new ArrayList<>(chosen(root, list));
        if (index < 0 || index >= columns.size()) {
            return WriteReport.refused("There is no column " + (index + 1));
        }
        columns.remove(index);
        return save(root, list, columns);
    }

    public static WriteReport remove(String list, int index) {
        return remove(Paths.ROOT, list, index);
    }

    /** Changes the column at an index to another key (a header click). */
    public static WriteReport replace(Path root, String list, int index, String key) {
        List<String> columns = new ArrayList<>(chosen(root, list));
        String k = key == null ? "" : key.strip();
        if (index < 0 || index >= columns.size()) {
            return WriteReport.refused("There is no column " + (index + 1));
        }
        if (k.isEmpty()) {
            return WriteReport.refused("Pick what the column shows");
        }
        if (columns.contains(k) && !columns.get(index).equals(k)) {
            return WriteReport.refused("There is a column for that already");
        }
        columns.set(index, k);
        return save(root, list, columns);
    }

    public static WriteReport replace(String list, int index, String key) {
        return replace(Paths.ROOT, list, index, key);
    }

    // ------------------------------------------------------------ the file

    private static WriteReport save(Path root, String list, List<String> columns) {
        Map<String, List<String>> all = sections(root);
        all.put(list, List.copyOf(columns));
        return write(root, all);
    }

    /** The sections of the file, with the old mob columns when it has no "[mobs]" yet. */
    static Map<String, List<String>> sections(Path root) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Path file = root.resolve(FILE);
        try {
            if (Files.isRegularFile(file)) {
                out.putAll(parse(Files.readAllLines(file)));
            }
            Path old = root.resolve(OLD_FILE);
            if (!out.containsKey(MOBS) && Files.isRegularFile(old)) {
                out.put(MOBS, migrated(Files.readAllLines(old)));
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read the at-a-glance columns in {}: {}", root, e.toString());
        }
        return out;
    }

    /** The sections of the file's lines. */
    static Map<String, List<String>> parse(List<String> lines) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        List<String> current = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("[") && line.endsWith("]")) {
                current = new ArrayList<>();
                out.put(line.substring(1, line.length() - 1).strip(), current);
            } else if (current != null && !line.isEmpty() && !current.contains(line) && current.size() < MAX) {
                current.add(line);
            }
        }
        return out;
    }

    /** The old stat-columns file's lines as mob columns: its blank "none" columns go. */
    static List<String> migrated(List<String> oldLines) {
        List<String> out = new ArrayList<>();
        for (String raw : oldLines) {
            String line = raw.strip();
            if (!line.isEmpty() && !out.contains(line) && out.size() < MAX) {
                out.add(line);
            }
        }
        return List.copyOf(out);
    }

    private static WriteReport write(Path root, Map<String, List<String>> all) {
        StringBuilder text = new StringBuilder();
        all.forEach((list, keys) -> {
            text.append('[').append(list).append(']').append(System.lineSeparator());
            keys.forEach(k -> text.append(k).append(System.lineSeparator()));
        });
        try {
            Files.createDirectories(root);
            com.strata.data.EditHistory.beforeWrite(root.resolve(FILE));
            Files.writeString(root.resolve(FILE), text.toString());
            return new WriteReport();
        } catch (IOException e) {
            Strata.LOGGER.error("Could not save the at-a-glance columns: {}", e.toString());
            return new WriteReport().record("the columns", false);
        }
    }

    // ------------------------------------------------------- what they show

    /** A column's heading: the value's short name. */
    public static String heading(String list, String key) {
        if (key == null || key.isBlank()) {
            return "-";
        }
        return switch (list) {
            case ITEMS -> ItemStatsOps.glanceHeading(key);
            case BLOCKS -> BlockStats.name(key);
            default -> StatColumns.heading(key);
        };
    }

    /** What a column shows, in a sentence, for its heading's tooltip. */
    public static String what(String list, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return switch (list) {
            case ITEMS -> ItemStats.what(key, "");
            case BLOCKS -> BlockStats.what(key, "");
            default -> com.strata.model.AttributeGuide.of(key).what() + " Base stats, before level scaling.";
        };
    }

    /** The headings of the columns, in order. */
    public static List<String> headings(String list, List<String> columns) {
        return columns.stream().map(k -> heading(list, k)).toList();
    }

    /**
     * An item's or block's values for the columns, as text: "-" for a value it does not have.
     *
     * @param values the item's or block's effective values by key, or null for none
     */
    public static List<String> valuesFor(List<String> columns, Map<String, String> values) {
        List<String> out = new ArrayList<>();
        for (String key : columns) {
            out.add(cell(values == null ? null : values.get(key)));
        }
        return out;
    }

    /** One value as a cell: "-" for none. */
    static String cell(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String v = value.strip();
        try {
            return StatColumns.number(Double.parseDouble(v));
        } catch (NumberFormatException e) {
            return v.startsWith("minecraft:") ? v.substring("minecraft:".length()) : v;
        }
    }

    /** The values a column of the list can show, for the picker, without the ones it has already. */
    public static List<RegistryQuery.Option> choices(String list, List<String> have) {
        List<RegistryQuery.Option> out = new ArrayList<>();
        switch (list) {
            case MOBS -> out.addAll(StatColumns.choices());
            case ITEMS -> ItemStatsOps.glanceKeys().forEach(k -> out.add(new RegistryQuery.Option(k,
                    ItemStatsOps.glanceHeading(k), ItemStats.attributeParts(k) == null ? "Value" : "Attribute")));
            case BLOCKS -> BlockStatsOps.glanceKeys().forEach(k -> out.add(new RegistryQuery.Option(k,
                    BlockStats.name(k), "Value")));
            default -> {
            }
        }
        out.removeIf(o -> have.contains(o.id()));
        return out;
    }
}
