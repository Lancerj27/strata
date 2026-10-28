package com.strata.data;

/** Reading values out of CSV cells, and writing them back. */
public final class Parse {

    private Parse() {}

    /** Blank or malformed reads as the fallback, so a missing column means "default". */
    public static double number(String raw, double fallback) {
        if (raw == null || raw.strip().isEmpty()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(raw.strip());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int integer(String raw, int fallback) {
        return (int) number(raw, fallback);
    }

    public static float floatNumber(String raw, float fallback) {
        return (float) number(raw, fallback);
    }

    public static boolean flag(String raw, boolean fallback) {
        if (raw == null || raw.strip().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.strip());
    }

    /** Splits a CSV line into its cells, honouring RFC 4180 quoting. */
    public static String[] row(String line) {
        // A UTF-8 BOM (U+FEFF) survives strip - it is a format char, not whitespace.
        if (!line.isEmpty() && line.charAt(0) == '﻿') {
            line = line.substring(1);
        }

        java.util.List<String> cells = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean sawQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.toString().isBlank()) {
                // An opening quote: the first non-space character of the cell.
                cell.setLength(0);
                quoted = true;
                sawQuotes = true;
            } else if (c == ',') {
                cells.add(sawQuotes ? cell.toString() : cell.toString().strip());
                cell.setLength(0);
                sawQuotes = false;
            } else {
                cell.append(c);
            }
        }
        cells.add(sawQuotes ? cell.toString() : cell.toString().strip());

        return cells.toArray(new String[0]);
    }

    /** One cell as it must be written, quoted only when it has to be. */
    public static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needs = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.charAt(0) == '#'
                || !value.equals(value.strip());
        if (!needs) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** A whole row as a line, each cell quoted only if it needs to be. */
    public static String line(String[] cells) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        return out.toString();
    }

    /** Column index by NAME, so a file whose columns were reordered still lines up. */
    public static int columnIndex(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** 20.0 rather than 20, and 0.23 rather than 0.23000000000000001. */
    public static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return (long) value + ".0";
        }
        return String.valueOf(Math.round(value * 100000.0) / 100000.0);
    }
}
