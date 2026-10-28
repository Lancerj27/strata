package com.strata.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A loot table as rows, and back. */
public final class LootJson {

    private LootJson() {
    }

    /** Every value of the JSON, path to literal, in document order. */
    public static Map<String, String> flatten(JsonElement json) {
        Map<String, String> out = new LinkedHashMap<>();
        walk("", json, false, out);
        return out;
    }

    private static void walk(String path, JsonElement e, boolean inEntries, Map<String, String> out) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.size() == 0) {
                out.put(path, "{}");
                return;
            }
            for (Map.Entry<String, JsonElement> m : o.entrySet()) {
                walk(join(path, escape(m.getKey())), m.getValue(), m.getKey().equals("entries"), out);
            }
            if (inEntries && !o.has("weight") && !o.has("children")) {
                out.put(join(path, "weight"), "1");
            }
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.isEmpty()) {
                out.put(path, "[]");
                return;
            }
            for (int i = 0; i < a.size(); i++) {
                walk(join(path, String.valueOf(i)), a.get(i), inEntries, out);
            }
        } else {
            out.put(path, e.toString());
        }
    }

    /** The JSON the rows describe. */
    public static JsonElement unflatten(Map<String, String> rows) {
        JsonElement root = null;
        for (Map.Entry<String, String> row : rows.entrySet()) {
            String[] parts = row.getKey().isEmpty() ? new String[0] : row.getKey().split("\\.");
            JsonElement value = JsonParser.parseString(row.getValue());
            if (parts.length == 0) {
                return value;
            }
            if (root == null) {
                root = container(parts[0]);
            }
            JsonElement at = root;
            for (int i = 0; i < parts.length; i++) {
                boolean last = i == parts.length - 1;
                JsonElement next = last ? value : null;
                if (at.isJsonArray()) {
                    JsonArray arr = at.getAsJsonArray();
                    int index = Integer.parseInt(parts[i]);
                    while (arr.size() <= index) {
                        arr.add(JsonNull.INSTANCE);
                    }
                    if (last) {
                        arr.set(index, value);
                    } else {
                        if (arr.get(index).isJsonNull()) {
                            arr.set(index, container(parts[i + 1]));
                        }
                        next = arr.get(index);
                    }
                } else {
                    JsonObject obj = at.getAsJsonObject();
                    String key = unescape(parts[i]);
                    if (last) {
                        obj.add(key, value);
                    } else {
                        if (!obj.has(key)) {
                            obj.add(key, container(parts[i + 1]));
                        }
                        next = obj.get(key);
                    }
                }
                at = next;
            }
        }
        return root == null ? new JsonObject() : root;
    }

    /**
     * The JSON with no empty place in any list: a gap an added item left (a place with no values) would make
     * the game refuse the whole table.
     */
    public static JsonElement compact(JsonElement e) {
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = a.size() - 1; i >= 0; i--) {
                if (a.get(i).isJsonNull()) {
                    a.remove(i);
                } else {
                    compact(a.get(i));
                }
            }
        } else if (e.isJsonObject()) {
            e.getAsJsonObject().entrySet().forEach(m -> compact(m.getValue()));
        }
        return e;
    }

    private static JsonElement container(String nextPart) {
        return nextPart.chars().allMatch(Character::isDigit) && !nextPart.isEmpty() ? new JsonArray() : new JsonObject();
    }

    private static String join(String path, String part) {
        return path.isEmpty() ? part : path + "." + part;
    }

    private static String escape(String key) {
        return key.replace("%", "%25").replace(".", "%2E");
    }

    private static String unescape(String key) {
        return key.replace("%2E", ".").replace("%25", "%");
    }

    /** A value for the editor: a text without its quotes. */
    public static String shown(String literal) {
        try {
            JsonElement e = JsonParser.parseString(literal);
            return e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : literal;
        } catch (RuntimeException ex) {
            // A value edited by hand into bad JSON shows as it is.
            return literal;
        }
    }

    /** The literal for a typed value of the same kind as the game's. */
    public static String literal(String gameLiteral, String typed) {
        String t = typed.strip();
        try {
            JsonElement g = JsonParser.parseString(gameLiteral);
            return g.isJsonPrimitive() && g.getAsJsonPrimitive().isString() ? new JsonPrimitive(t).toString() : t;
        } catch (RuntimeException e) {
            return t;
        }
    }

    private static final java.util.regex.Pattern ADDED =
            java.util.regex.Pattern.compile("^pools\\.(\\d+)\\.entries\\.(\\d+)\\.");

    /**
     * True for a value of an item the user added: a path under "pools.P.entries.N." where the game's table
     * has pool P but no entry N.
     */
    public static boolean addedEntryPath(String path, java.util.Collection<String> gamePaths) {
        java.util.regex.Matcher m = ADDED.matcher(path == null ? "" : path);
        if (!m.find()) {
            return false;
        }
        String pool = "pools." + m.group(1) + ".";
        String entry = m.group();
        return gamePaths.stream().anyMatch(p -> p.startsWith(pool))
                && gamePaths.stream().noneMatch(p -> p.startsWith(entry));
    }

    private static final java.util.regex.Pattern ADDED_FUNCTION =
            java.util.regex.Pattern.compile("^pools\\.\\d+\\.entries\\.\\d+\\.functions\\.\\d+\\.");

    /**
     * True for a value of a function the user added to a game entry (Strata's own set_count,
     * so any item can take a count range): "pools.P.entries.N.functions.M." where the game's table has entry
     * N but no function M.
     */
    public static boolean addedFunctionPath(String path, java.util.Collection<String> gamePaths) {
        java.util.regex.Matcher m = ADDED_FUNCTION.matcher(path == null ? "" : path);
        if (!m.find()) {
            return false;
        }
        String function = m.group();
        String entry = function.substring(0, function.indexOf(".functions."));
        return gamePaths.stream().anyMatch(p -> p.startsWith(entry + "."))
                && gamePaths.stream().noneMatch(p -> p.startsWith(function));
    }

    /** True for any value the user may add: an added entry's or an added function's. */
    public static boolean addedPath(String path, java.util.Collection<String> gamePaths) {
        return addedEntryPath(path, gamePaths) || addedFunctionPath(path, gamePaths);
    }

    /** The tables the user changed, each the game's rows with the user's values put in, as JSON. */
    public static Map<String, JsonElement> editedTables(List<GameLayer.Row> sheet, List<Map<String, String>> edits) {
        Map<String, Map<String, String>> game = new LinkedHashMap<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.LOOT_VALUE.equals(r.group())) {
                game.computeIfAbsent(r.entity(), k -> new LinkedHashMap<>()).put(r.key(), r.value());
            }
        }
        // An added item counts only whole: its entry has its type.
        java.util.Set<String> typed = new java.util.HashSet<>();
        for (Map<String, String> row : edits) {
            String path = row.getOrDefault(Schema.LootTables.PATH, "").strip();
            String table = row.getOrDefault(Schema.LootTables.TABLE, "").strip();
            java.util.regex.Matcher m = ADDED.matcher(path);
            if (m.find() && path.equals(m.group() + "type")) {
                typed.add(table + "|" + m.group());
            }
            // An added function counts only with its name.
            java.util.regex.Matcher f = ADDED_FUNCTION.matcher(path);
            if (f.find() && path.equals(f.group() + "function")) {
                typed.add(table + "|" + f.group());
            }
        }
        Map<String, Map<String, String>> changed = new LinkedHashMap<>();
        for (Map<String, String> row : edits) {
            String table = row.getOrDefault(Schema.LootTables.TABLE, "").strip();
            String path = row.getOrDefault(Schema.LootTables.PATH, "").strip();
            Map<String, String> values = game.get(table);
            if (values == null) {
                continue;
            }
            if (!values.containsKey(path)) {
                java.util.regex.Matcher m = ADDED.matcher(path);
                java.util.regex.Matcher f = ADDED_FUNCTION.matcher(path);
                boolean entry = addedEntryPath(path, values.keySet()) && m.find()
                        && typed.contains(table + "|" + m.group());
                boolean function = !entry && addedFunctionPath(path, values.keySet()) && f.find()
                        && typed.contains(table + "|" + f.group());
                if (!entry && !function) {
                    continue;
                }
            }
            changed.computeIfAbsent(table, k -> new LinkedHashMap<>(values))
                    .put(path, row.getOrDefault(Schema.LootTables.VALUE, "").strip());
        }
        Map<String, JsonElement> out = new LinkedHashMap<>();
        changed.forEach((table, values) -> {
            try {
                out.put(table, compact(unflatten(values)));
            } catch (RuntimeException e) {
                com.strata.Strata.LOGGER.warn("[Strata] Loot table {} has a value that is not JSON; the game's table stays: {}",
                        table, e.getMessage());
            }
        });
        return out;
    }
}
