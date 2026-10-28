package com.strata.data;

import com.strata.Strata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a single row back into a CSV, in place. */
public final class TableWriter {

    private TableWriter() {}

    /** COMMENTS BETWEEN ROWS SURVIVE A SAVE. */
    private static final class Notes {
        private final Map<String[], List<String>> before = new java.util.IdentityHashMap<>();
        private List<String> pending = new ArrayList<>();

        void comment(String line) {
            pending.add(line);
        }

        String[] row(String[] row) {
            if (!pending.isEmpty()) {
                before.put(row, pending);
                pending = new ArrayList<>();
            }
            return row;
        }

        /** A row goes: its comments go to the next row, or to the tail when it was the last. */
        void dropped(String[] row, String[] next) {
            List<String> mine = before.remove(row);
            if (mine == null) {
                return;
            }
            if (next == null) {
                pending.addAll(0, mine);
                return;
            }
            List<String> merged = new ArrayList<>(mine);
            List<String> theirs = before.get(next);
            if (theirs != null) {
                merged.addAll(theirs);
            }
            before.put(next, merged);
        }

        void moved(String[] from, String[] to) {
            List<String> mine = before.remove(from);
            if (mine != null) {
                before.put(to, mine);
            }
        }

        void emit(List<String> out, String[] row) {
            List<String> mine = before.get(row);
            if (mine != null) {
                out.addAll(mine);
            }
        }

        void tail(List<String> out) {
            out.addAll(pending);
        }
    }

    /** A table as read: the comments above the header, the header, the rows, and the comments between them. */
    private record Table(List<String> preamble, String[] header, List<String[]> rows, Notes notes) {

        /** The lines as they are written: the preamble, the header, each row under its comments, the tail. */
        List<String> lines(String[] header, List<String[]> rows) {
            List<String> out = new ArrayList<>(preamble);
            out.add(Parse.line(header));
            for (String[] row : rows) {
                notes.emit(out, row);
                out.add(Parse.line(row));
            }
            notes.tail(out);
            return out;
        }
    }

    /** Reads a table; a missing file reads as one with no header. THE ONE PARSE every write goes through. */
    private static Table read(Path path) throws IOException {
        List<String> preamble = new ArrayList<>();
        Notes notes = new Notes();
        String[] header = null;
        List<String[]> rows = new ArrayList<>();
        if (Files.exists(path)) {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    // Comments above the header are the file's own documentation; ones further down stay
                    // with the row under them (Notes).
                    if (header == null) {
                        preamble.add(line);
                    } else {
                        notes.comment(line);
                    }
                    continue;
                }
                if (header == null) {
                    header = Parse.row(trimmed);
                } else {
                    rows.add(notes.row(Parse.row(trimmed)));
                }
            }
        }
        return new Table(preamble, header, rows, notes);
    }

    /** A row's cell by index, with a missing TRAILING cell read as "". */
    private static String cellAt(String[] row, int index) {
        return index < row.length ? row[index] : "";
    }

    /** True when a value can survive a round trip through this format. */
    public static boolean isCsvSafe(String value) {
        return value == null
                || (value.indexOf('\n') < 0 && value.indexOf('\r') < 0);
    }

    /** The value as it can safely be stored, with row-breaking characters turned into spaces. */
    public static String csvSafe(String value) {
        if (isCsvSafe(value)) {
            return value;
        }
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    /** Writes the whole file via a temp file and a move, so a crash cannot leave a half-written table. */
    private static void writeAtomically(Path path, List<String> lines) throws IOException {
        // The editor's undo keeps the file as it was.
        EditHistory.beforeWrite(path);
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            CsvTable.forget(path);
        }
    }

    /** Sanitises a map's values in place-ish, logging every value it had to change. */
    private static Map<String, String> csvSafe(Path path, Map<String, String> values) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue();
            if (!isCsvSafe(value)) {
                Strata.LOGGER.warn("{}: value for column '{}' contains a line break, which one row "
                        + "on one line cannot store - saved as '{}'",
                        path.getFileName(), entry.getKey(), csvSafe(value));
            }
            out.put(entry.getKey(), csvSafe(value));
        }
        return out;
    }

    /**
     * Sets values on the row matching keys, creating it if absent.
     *
     * @param fileName table in config/strata/
     * @param keys columns that identify the row, and their values
     * @param values columns to set, and their values
     * @return true if the file was written
     */
    public static boolean upsert(String fileName, Map<String, String> keys,
                                 Map<String, String> values) {
        return upsert(Paths.table(fileName), keys, values);
    }

    /** Path-rooted upsert. */
    public static boolean upsert(Path path, Map<String, String> keys,
                          Map<String, String> rawValues) {
        // Both halves: a key is written into the row too, so an unsafe key would break the row exactly as an
        // unsafe value would.
        Map<String, String> values = csvSafe(path, rawValues);
        keys = csvSafe(path, keys);
        try {
            Table table = read(path);
            String[] header = table.header();
            List<String[]> rows = table.rows();
            if (header == null) {
                Strata.LOGGER.error("{} has no header - refusing to write",
                        path.getFileName());
                return false;
            }

            int[] keyIndices = new int[keys.size()];
            String[] keyValues = new String[keys.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : keys.entrySet()) {
                int index = Parse.columnIndex(header, entry.getKey());
                if (index < 0) {
                    Strata.LOGGER.error("{} has no '{}' column - refusing to write",
                            path.getFileName(), entry.getKey());
                    return false;
                }
                keyIndices[i] = index;
                keyValues[i] = entry.getValue();
                i++;
            }

            // A VALUE column the file does not have is GROWN into the header, then the write is retried once.
            List<String> missingValueColumns = new ArrayList<>();
            for (String column : values.keySet()) {
                if (Parse.columnIndex(header, column) < 0) {
                    missingValueColumns.add(column);
                }
            }
            if (!missingValueColumns.isEmpty()) {
                for (String column : missingValueColumns) {
                    if (!addColumn(path, column)) {
                        Strata.LOGGER.error("{}: could not grow the '{}' column - refusing to write",
                                path.getFileName(), column);
                        return false;
                    }
                }
                return upsert(path, keys, rawValues);
            }

            String[] target = null;
            for (String[] row : rows) {
                boolean match = true;
                for (int k = 0; k < keyIndices.length; k++) {
                    if (!cellAt(row, keyIndices[k]).equals(keyValues[k])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    target = row;
                    break;
                }
            }

            if (target == null) {
                target = new String[header.length];
                for (int c = 0; c < header.length; c++) {
                    target[c] = "";
                }
                for (int k = 0; k < keyIndices.length; k++) {
                    target[keyIndices[k]] = keyValues[k];
                }
                // A row written by the editor is one somebody asked for, so it is on unless they said
                // otherwise.
                int enabled = Parse.columnIndex(header, "enabled");
                if (enabled >= 0) {
                    target[enabled] = "true";
                }
                rows.add(target);
            } else if (target.length < header.length) {
                String[] widened = new String[header.length];
                for (int c = 0; c < header.length; c++) {
                    widened[c] = c < target.length ? target[c] : "";
                }
                table.notes().moved(target, widened);
                rows.set(rows.indexOf(target), widened);
                target = widened;
            }

            for (Map.Entry<String, String> entry : values.entrySet()) {
                int index = Parse.columnIndex(header, entry.getKey());
                if (index >= 0) {
                    target[index] = entry.getValue();
                }
            }

            writeAtomically(path, table.lines(header, rows));
            Strata.LOGGER.info("Wrote {} ({} rows)", path.getFileName(), rows.size());
            return true;
        } catch (Exception e) {
            Strata.LOGGER.error("Could not write {}: {}", path.getFileName(),
                    e.toString(), e);
            return false;
        }
    }

    /**
     * Appends many rows in one write (the family archetypes, some hundreds of rows, seeded at game start).
     *
     * @return true if the file was written
     */
    public static boolean appendRows(String fileName, List<Map<String, String>> newRows) {
        return appendRows(Paths.table(fileName), newRows);
    }

    /** Path-rooted append (see the upsert overload for why this is public). */
    public static boolean appendRows(Path path, List<Map<String, String>> newRows) {
        try {
            if (!Files.exists(path)) {
                Strata.LOGGER.error("{} does not exist - refusing to append", path.getFileName());
                return false;
            }
            List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            String[] header = null;
            for (String line : lines) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    header = Parse.row(trimmed);
                    break;
                }
            }
            if (header == null) {
                Strata.LOGGER.error("{} has no header - refusing to append", path.getFileName());
                return false;
            }
            for (Map<String, String> raw : newRows) {
                Map<String, String> row = csvSafe(path, raw);
                String[] cells = new String[header.length];
                for (int c = 0; c < header.length; c++) {
                    cells[c] = row.getOrDefault(header[c], "");
                }
                lines.add(Parse.line(cells));
            }
            writeAtomically(path, lines);
            Strata.LOGGER.info("Appended {} row(s) to {}", newRows.size(), path.getFileName());
            return true;
        } catch (Exception e) {
            Strata.LOGGER.error("Could not append to {}: {}", path.getFileName(), e.toString(), e);
            return false;
        }
    }

    /**
     * Removes the row matching keys, if there is one.
     *
     * @return true if a row was removed and the file rewritten
     */
    public static boolean delete(String fileName, Map<String, String> keys) {
        return delete(Paths.table(fileName), keys);
    }

    /** Path-rooted delete (see the upsert overload for why this is public). */
    public static boolean delete(Path path, Map<String, String> keys) {
        if (!Files.exists(path)) {
            return false;
        }
        try {
            Table table = read(path);
            String[] header = table.header();
            if (header == null) {
                return false;
            }
            List<String[]> kept = new ArrayList<>();
            boolean removed = false;
            List<String[]> rows = table.rows();
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                boolean match = true;
                for (Map.Entry<String, String> entry : keys.entrySet()) {
                    int index = Parse.columnIndex(header, entry.getKey());
                    // A column the FILE does not have is still no match - that is a different thing from a
                    // row that is merely short.
                    if (index < 0 || !cellAt(row, index).equals(entry.getValue())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    removed = true;
                    // Its comments go to the next row, not with it.
                    table.notes().dropped(row, r + 1 < rows.size() ? rows.get(r + 1) : null);
                } else {
                    kept.add(row);
                }
            }

            if (!removed) {
                return false;
            }

            writeAtomically(path, table.lines(header, kept));
            Strata.LOGGER.info("Removed a row from {} ({} left)",
                    path.getFileName(), kept.size());
            return true;
        } catch (Exception e) {
            Strata.LOGGER.error("Could not update {}: {}", path.getFileName(),
                    e.toString(), e);
            return false;
        }
    }

    /**
     * Sets one column to a new value on EVERY row whose {@code whereColumn} currently equals {@code
     * whereValue}.
     *
     * @return true if at least one row was updated and the file rewritten
     */
    public static boolean updateColumn(String fileName, String whereColumn,
                                       String whereValue, String setColumn,
                                       String setValue) {
        return updateColumnIf(fileName, whereColumn, whereValue,
                setColumn, setValue, null, null);
    }

    /**
     * Like {@link #updateColumn(String, String, String, String, String)} but the row must ALSO satisfy one
     * extra predicate ({@code alsoWhereColumn} equals {@code alsoWhereValue}).
     *
     * @return true if at least one row was updated and the file rewritten
     */
    public static boolean updateColumnIf(String fileName, String whereColumn,
                                         String whereValue, String setColumn,
                                         String setValue, String alsoWhereColumn,
                                         String alsoWhereValue) {
        return updateColumnIf(Paths.table(fileName), whereColumn, whereValue,
                setColumn, setValue, alsoWhereColumn, alsoWhereValue);
    }

    /** Path-rooted updateColumn (see the upsert overload for why this is public). */
    public static boolean updateColumn(Path path, String whereColumn, String whereValue,
                                String setColumn, String setValue) {
        return updateColumnIf(path, whereColumn, whereValue,
                setColumn, setValue, null, null);
    }

    /** Path-rooted updateColumnIf (see the upsert overload for why this is public). */
    public static boolean updateColumnIf(Path path, String whereColumn, String whereValue,
                                  String setColumn, String rawSetValue,
                                  String alsoWhereColumn, String alsoWhereValue) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(whereColumn, whereValue);
        if (alsoWhereColumn != null) {
            keys.put(alsoWhereColumn, alsoWhereValue);
        }
        return updateColumnWhere(path, keys, setColumn, rawSetValue);
    }

    /**
     * Like {@link #updateColumn(String, String, String, String, String)} but a row is only updated when it
     * satisfies EVERY entry in {@code keys} (its column equals the value).
     *
     * @return true if at least one row was updated and the file rewritten
     */
    public static boolean updateColumnWhere(String fileName, Map<String, String> keys,
                                            String setColumn, String setValue) {
        return updateColumnWhere(Paths.table(fileName), keys, setColumn, setValue);
    }

    /** Path-rooted updateColumnWhere (see the upsert overload for why this is public). */
    public static boolean updateColumnWhere(Path path, Map<String, String> keys,
                                     String setColumn, String rawSetValue) {
        if (!Files.exists(path)) {
            return false;
        }
        if (keys == null || keys.isEmpty()) {
            Strata.LOGGER.error("{}: a WHERE map with no columns cannot update {}",
                    path.getFileName(), setColumn);
            return false;
        }
        String setValue = csvSafe(rawSetValue);
        if (!isCsvSafe(rawSetValue)) {
            Strata.LOGGER.warn("{}: value for column '{}' contains a line break, which one row "
                    + "on one line cannot store - saved as '{}'", path.getFileName(), setColumn, setValue);
        }
        try {
            Table table = read(path);
            String[] header = table.header();
            List<String[]> rows = table.rows();
            boolean updated = false;
            int updatedRows = 0;
            if (header == null) {
                return false;
            }
            int setIndex = Parse.columnIndex(header, setColumn);
            if (setIndex < 0) {
                Strata.LOGGER.error("{} has no {} column - refusing to update",
                        path.getFileName(), setColumn);
                return false;
            }
            Map<String, Integer> keyIndexes = new LinkedHashMap<>();
            for (var entry : keys.entrySet()) {
                int keyIndex = Parse.columnIndex(header, entry.getKey());
                if (keyIndex < 0) {
                    Strata.LOGGER.error("{} has no {} column - refusing to update",
                            path.getFileName(), entry.getKey());
                    return false;
                }
                keyIndexes.put(entry.getKey(), keyIndex);
            }

            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                boolean matches = true;
                for (var entry : keyIndexes.entrySet()) {
                    if (!cellAt(row, entry.getValue()).equals(keys.get(entry.getKey()))) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) {
                    continue;
                }
                if (setIndex < row.length) {
                    row[setIndex] = setValue;
                } else {
                    String[] widened = new String[setIndex + 1];
                    for (int c = 0; c < row.length; c++) {
                        widened[c] = row[c];
                    }
                    for (int c = row.length; c <= setIndex; c++) {
                        widened[c] = "";
                    }
                    widened[setIndex] = setValue;
                    table.notes().moved(rows.get(r), widened);
                    rows.set(r, widened);
                }
                updated = true;
                updatedRows++;
            }

            if (!updated) {
                return false;
            }

            writeAtomically(path, table.lines(header, rows));
            // The count is the rows actually CHANGED, not the rows in the file.
            Strata.LOGGER.info("Updated column {} in {} ({} of {} row(s))",
                    setColumn, path.getFileName(), updatedRows, rows.size());
            return true;
        } catch (Exception e) {
            Strata.LOGGER.error("Could not update column {} in {}: {}",
                    setColumn, path.getFileName(), e.toString(), e);
            return false;
        }
    }

    /** Whether the table's header has the column. */
    public static boolean hasColumn(String fileName, String column) {
        Path path = Paths.table(fileName);
        try {
            String[] header = read(path).header();
            return header != null && Parse.columnIndex(header, column) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ensures a column exists in the table, adding it empty at the end of the header (and widening every row)
     * when it is missing.
     *
     * @param fileName table in config/strata/
     * @param column column to guarantee
     * @return true if the column exists after the call (present already, or added and written)
     */
    public static boolean addColumn(String fileName, String column) {
        return addColumn(Paths.table(fileName), column);
    }

    /** Path-rooted addColumn (see the upsert overload for why this is public). */
    public static boolean addColumn(Path path, String column) {
        if (!Files.exists(path)) {
            return false;
        }
        try {
            Table table = read(path);
            String[] header = table.header();
            List<String[]> rows = table.rows();
            if (header == null) {
                return false;
            }
            if (Parse.columnIndex(header, column) >= 0) {
                return true;
            }

            String[] newHeader = Arrays.copyOf(header, header.length + 1);
            newHeader[header.length] = column;
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                // EVERY cell is filled, not just the new last one.
                int extra = Math.max(0, row.length - header.length);
                if (extra > 0) {
                    Strata.LOGGER.warn("{}: a row has more cells than the header - "
                            + "keeping them, but only the first {} are read",
                            path.getFileName(), newHeader.length);
                }
                String[] widened = new String[newHeader.length + extra];
                for (int c = 0; c < widened.length; c++) {
                    if (c < header.length) {
                        widened[c] = c < row.length ? row[c] : "";
                    } else if (c == header.length) {
                        widened[c] = "";
                    } else {
                        widened[c] = row[c - 1];
                    }
                }
                table.notes().moved(rows.get(r), widened);
                rows.set(r, widened);
            }

            writeAtomically(path, table.lines(newHeader, rows));
            Strata.LOGGER.info("Added column {} to {} ({} rows)",
                    column, path.getFileName(), rows.size());
            return true;
        } catch (Exception e) {
            Strata.LOGGER.error("Could not add column {} to {}: {}",
                    column, path.getFileName(), e.toString(), e);
            return false;
        }
    }
}
