package com.strata.data;

import com.strata.Strata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal CSV reader for config/strata/*.csv files — the exact "editable values live in a table, not in
 * Java" mechanism the original design doc specifies (Part 8.1/8.2: "CSV — Numeric curves and tables —
 * config/<modid>/*.csv").
 */
public final class CsvTable {

    /** True when the config table has at least one row. */
    public static boolean hasRows(String fileName) {
        return !read(Paths.table(fileName)).isEmpty();
    }

    private CsvTable() {}

    /** Reads a CSV file, returning one Map<header, value> per data row. */
    public static List<Map<String, String>> read(Path path) {
        if (!Files.exists(path)) {
            // DEBUG: a fresh install reads some tables before Defaults writes them.
            Strata.LOGGER.debug("CSV table file not found: {} — treating as empty", path);
            return List.of();
        }
        // READ ONCE WHILE THE FILE STAYS THE SAME: an editor action read the same tables
        // many times, and a batch did so for every mob.
        Path key = path.toAbsolutePath().normalize();
        FileTime time;
        long size;
        try {
            time = Files.getLastModifiedTime(path);
            size = Files.size(path);
        } catch (IOException e) {
            Strata.LOGGER.error("Failed to read table {}: {}", path, e.getMessage());
            return List.of();
        }
        Cached cached = CACHE.get(key);
        if (cached == null || !cached.time().equals(time) || cached.size() != size) {
            cached = new Cached(time, size, parse(path));
            CACHE.put(key, cached);
        }
        List<Map<String, String>> out = new ArrayList<>(cached.rows().size());
        for (Map<String, String> row : cached.rows()) {
            out.add(new HashMap<>(row));
        }
        return List.copyOf(out);
    }

    /** A parsed file, and the time and size it had. */
    private record Cached(FileTime time, long size, List<Map<String, String>> rows) {
    }

    private static final Map<Path, Cached> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Drops a file's parse: every Strata write to a table calls this. */
    public static void forget(Path path) {
        CACHE.remove(path.toAbsolutePath().normalize());
    }

    /** The file's rows, parsed. */
    private static List<Map<String, String>> parse(Path path) {
        List<Map<String, String>> rows = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String[] header = null;

            for (String rawLine : lines) {
                String line = rawLine.strip();
                // A Windows editor may save the file with a UTF-8 BOM.
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1).strip();
                }
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (header == null) {
                    header = Parse.row(line);
                    continue;
                }

                String[] values = Parse.row(line);
                if (values.length != header.length) {
                    // Previously this was silently tolerated: the loop below stops at the shorter of the two,
                    // so a row missing its last column produced a Map with that key absent, which surfaced
                    // much later as an opaque NumberFormatException on null in whichever curve class parsed
                    // it.
                    Strata.LOGGER.warn("{}: row has {} columns but the header has {} — \"{}\"",
                            path, values.length, header.length, line);
                }
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length && i < values.length; i++) {
                    row.put(header[i], values[i]);
                }
                rows.add(row);
            }
        } catch (IOException e) {
            Strata.LOGGER.error("Failed to read table {}: {}", path, e.getMessage());
            return List.of();
        }

        return List.copyOf(rows);
    }
}