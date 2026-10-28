package com.strata.domain;

import com.google.gson.JsonParser;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.LootJson;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Everything the loot table editor reads and does. */
public final class LootTableOps {

    private LootTableOps() {
    }

    /** One value of a table: its path, the game's JSON literal, and the user's or "". */
    public record Value(String path, String game, String own) {
        public String effective() {
            return own.isEmpty() ? game : own;
        }
    }

    /** A mob's table id ("" when it has none) and its values. */
    public record Sheet(String table, List<Value> values) {
    }

    public static Sheet sheet(String entity) {
        return sheetForTable(tableOf(GameLayer.read(GameLayer.dir().resolve(GameLayer.LOOT)), entity));
    }

    /** Any table's sheet by its id (a chest's); "" gives an empty sheet. */
    public static Sheet sheetForTable(String table) {
        List<GameLayer.Row> rows = GameLayer.read(GameLayer.dir().resolve(GameLayer.LOOT));
        Map<String, String> own = new java.util.LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.LootTables.FILE))) {
            if (table.equals(row.getOrDefault(Schema.LootTables.TABLE, "").strip())) {
                own.put(row.getOrDefault(Schema.LootTables.PATH, "").strip(),
                        row.getOrDefault(Schema.LootTables.VALUE, "").strip());
            }
        }
        return new Sheet(table, values(rows, table, own));
    }

    /** The mob's table id from the sheet, or "". */
    public static String tableOf(List<GameLayer.Row> rows, String entity) {
        for (GameLayer.Row r : rows) {
            if (r.entity().equals(entity) && GameLayer.LOOT_TABLE.equals(r.group())) {
                return r.value();
            }
        }
        return "";
    }

    /**
     * The table's values with the user's on top, in the table's order, then the values of the items the user
     * added, which have no game value.
     */
    public static List<Value> values(List<GameLayer.Row> rows, String table, Map<String, String> own) {
        List<Value> out = new ArrayList<>();
        java.util.Set<String> game = new java.util.HashSet<>();
        for (GameLayer.Row r : rows) {
            if (r.entity().equals(table) && GameLayer.LOOT_VALUE.equals(r.group())) {
                out.add(new Value(r.key(), r.value(), own.getOrDefault(r.key(), "")));
                game.add(r.key());
            }
        }
        own.forEach((path, value) -> {
            if (!game.contains(path) && LootJson.addedPath(path, game)) {
                out.add(new Value(path, "", value));
            }
        });
        return out;
    }

    /** True for a value of an item the user added: the game has no value there. */
    public static boolean added(Value v) {
        return v.game().isEmpty();
    }

    // ---- items: add and remove ----

    private static final java.util.regex.Pattern ENTRY = java.util.regex.Pattern.compile("^pools\\.(\\d+)\\.entries\\.(\\d+)");

    /** The entry a value belongs to, "pools.P.entries.N", or null. */
    public static String entryOf(String path) {
        java.util.regex.Matcher m = ENTRY.matcher(path == null ? "" : path);
        return m.find() ? m.group() : null;
    }

    /** The pool a value belongs to, from 0, or 0 when it is in none. */
    public static int poolOf(String path) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^pools\\.(\\d+)").matcher(path == null ? "" : path);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** The place the next item of a pool takes: one after its last entry, the game's and the added. */
    public static int nextEntry(List<Value> values, int pool) {
        int next = 0;
        for (Value v : values) {
            String entry = entryOf(v.path());
            if (entry != null && poolOf(entry) == pool) {
                next = Math.max(next, Integer.parseInt(entry.substring(entry.lastIndexOf('.') + 1)) + 1);
            }
        }
        return next;
    }

    /**
     * The values of a new item entry, path to JSON literal: the item, weight 1, and a count from 1 to 1, so
     * each is a value to edit after.
     */
    public static Map<String, String> itemRows(int pool, int index, String item) {
        String p = "pools." + pool + ".entries." + index;
        Map<String, String> out = new java.util.LinkedHashMap<>();
        out.put(p + ".type", "\"minecraft:item\"");
        out.put(p + ".name", new com.google.gson.JsonPrimitive(item.strip()).toString());
        out.put(p + ".weight", "1");
        out.put(p + ".functions.0.function", "\"minecraft:set_count\"");
        out.put(p + ".functions.0.count.type", "\"minecraft:uniform\"");
        out.put(p + ".functions.0.count.min", "1");
        out.put(p + ".functions.0.count.max", "1");
        return out;
    }

    /** Why an item cannot be added to this pool of the table, or null. */
    public static String addProblem(Sheet sheet, int pool, String item) {
        if (sheet.table().isEmpty()) {
            return "There is no loot table to add to";
        }
        if (sheet.values().stream().noneMatch(v -> v.path().startsWith("pools." + pool + "."))) {
            return "The table has no pool " + (pool + 1);
        }
        String i = item == null ? "" : item.strip();
        return i.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? null : "Pick an item";
    }

    /**
     * What removing the selected value's item writes (pure): an added item's rows go, and each later added
     * item of its pool moves up one place, so the list keeps no gap; a game item gets weight 0, which the
     * game never picks (LootPool: an entry with no weight is skipped), and Restore on its weight puts it
     * back.
     *
     * @return the paths to delete and the rows to write, or null when the value is in no item
     */
    public static Removal removal(Sheet sheet, Value selected) {
        String entry = selected == null ? null : entryOf(selected.path());
        if (entry == null) {
            return null;
        }
        int pool = poolOf(entry);
        int index = Integer.parseInt(entry.substring(entry.lastIndexOf('.') + 1));
        boolean added = sheet.values().stream().filter(v -> entry.equals(entryOf(v.path()))).allMatch(LootTableOps::added);
        List<String> delete = new ArrayList<>();
        Map<String, String> write = new java.util.LinkedHashMap<>();
        if (!added) {
            write.put(entry + ".weight", "0");
            return new Removal(List.of(), write);
        }
        String poolPrefix = "pools." + pool + ".entries.";
        for (Value v : sheet.values()) {
            String e = entryOf(v.path());
            if (e == null || !added(v) || poolOf(e) != pool) {
                continue;
            }
            int at = Integer.parseInt(e.substring(e.lastIndexOf('.') + 1));
            if (at == index) {
                delete.add(v.path());
            } else if (at > index) {
                delete.add(v.path());
                write.put(poolPrefix + (at - 1) + v.path().substring(e.length()), v.own());
            }
        }
        return new Removal(delete, write);
    }


    /** The paths a removal deletes and the rows it writes. */
    public record Removal(List<String> delete, Map<String, String> write) {
    }


    /** Why a value cannot be edited, or null. */
    public static String lockReason(Value v) {
        String last = v.path().substring(v.path().lastIndexOf('.') + 1);
        if (last.equals("type") || last.equals("function") || last.equals("condition")) {
            return "This name decides which other values exist, so it stays the game's";
        }
        if (v.game().equals("[]") || v.game().equals("{}")) {
            return "An empty list has no value to change";
        }
        return null;
    }

    /** Why a typed value cannot replace the game's, or null: it keeps the game value's kind. */
    public static String problem(Value v, String typed) {
        String lock = lockReason(v);
        if (lock != null) {
            return lock;
        }
        String t = typed == null ? "" : typed.strip();
        if (t.isEmpty() || t.contains("\n")) {
            return "Type one value";
        }
        // A game value that is not one plain value (a JSON null) is refused, not thrown: a throw here stopped
        // the game.
        com.google.gson.JsonPrimitive game;
        // An added item's value has no game value: its own is the kind.
        String kind = added(v) ? v.own() : v.game();
        try {
            var parsed = JsonParser.parseString(kind);
            if (!parsed.isJsonPrimitive()) {
                return "This value is not one number or text, so it stays the game's";
            }
            game = parsed.getAsJsonPrimitive();
        } catch (RuntimeException e) {
            return "This value is not one number or text, so it stays the game's";
        }
        if (game.isBoolean()) {
            return t.equals("true") || t.equals("false") ? null : "The value must be true or false";
        }
        if (game.isNumber()) {
            try {
                if (!Double.isFinite(Double.parseDouble(t))) {
                    return "The value must be a number";
                }
            } catch (NumberFormatException e) {
                return "The value must be a number";
            }
            return !kind.contains(".") && !kind.contains("E") && t.contains(".")
                    ? "The value must be a whole number" : null;
        }
        if (v.path().endsWith(".name") && !t.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            return "The value must be an id, like minecraft:bone";
        }
        return null;
    }

    /**
     * What a loot value is, for the sheet's control.
     *
     * @param all the sheet's values, to read the entry's type beside its name
     */
    public static com.strata.model.ValueType typeOf(Value v, List<Value> all) {
        String kind = added(v) ? v.own() : v.game();
        com.google.gson.JsonPrimitive primitive;
        try {
            var parsed = JsonParser.parseString(kind);
            if (!parsed.isJsonPrimitive()) {
                return com.strata.model.ValueType.TEXT;
            }
            primitive = parsed.getAsJsonPrimitive();
        } catch (RuntimeException e) {
            return com.strata.model.ValueType.TEXT;
        }
        if (primitive.isBoolean()) {
            return com.strata.model.ValueType.BOOL;
        }
        if (primitive.isNumber()) {
            return kind.contains(".") || kind.contains("E") ? com.strata.model.ValueType.NUMBER
                    : com.strata.model.ValueType.WHOLE;
        }
        String entry = entryOf(v.path());
        if (entry != null && v.path().equals(entry + ".name")) {
            String type = all.stream().filter(x -> x.path().equals(entry + ".type")).map(Value::effective)
                    .findFirst().map(LootJson::shown).orElse("minecraft:item");
            if (type.equals("minecraft:item") || type.equals("item")) {
                return com.strata.model.ValueType.registry(com.strata.model.ValueType.Registry.ITEM);
            }
        }
        return com.strata.model.ValueType.TEXT;
    }

    /** The editor's name for a path: "Pool 1, Entry 2, Function 1: count max". */
    public static String label(String path) {
        String[] parts = path.split("\\.");
        StringBuilder out = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            boolean indexed = i + 1 < parts.length && parts[i + 1].chars().allMatch(Character::isDigit)
                    && !parts[i + 1].isEmpty();
            String word = switch (p) {
                case "pools" -> "Pool";
                case "entries" -> "Entry";
                case "children" -> "Child";
                case "functions" -> "Function";
                case "conditions" -> "Condition";
                default -> null;
            };
            if (word != null && indexed) {
                if (!out.isEmpty()) {
                    out.append(", ");
                }
                out.append(word).append(' ').append(Integer.parseInt(parts[i + 1]) + 1);
                i++;
            } else {
                if (!tail.isEmpty()) {
                    tail.append(' ');
                }
                tail.append(p.replace('_', ' ').replace("%2E", "."));
            }
        }
        return out.isEmpty() ? tail.toString() : out + ": " + tail;
    }

    /**
     * The name, with each entry named by the item it drops: "Pool 1, Rotten Flesh: count max".
     *
     * @param all the table's values, where the entries' items are
     * @param words an item id in words (the screen's Labels)
     */
    public static String label(String path, List<Value> all, java.util.function.Function<String, String> words) {
        String plain = label(path);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^pools\\.(\\d+)\\.entries\\.(\\d+)")
                .matcher(path);
        if (!m.find()) {
            return plain;
        }
        String itemPath = m.group() + ".name";
        for (Value v : all) {
            if (v.path().equals(itemPath)) {
                String item = LootJson.shown(v.effective());
                String entry = "Entry " + (Integer.parseInt(m.group(2)) + 1);
                return plain.replaceFirst(java.util.regex.Pattern.quote(entry), words.apply(item));
            }
        }
        return plain;
    }

    /** What a value of a loot table does, by its last names. */
    public static String what(String path) {
        String p = path.replace("%2E", ".");
        if (p.endsWith(".bonus_rolls")) return "Extra picks for each point of the killer's luck.";
        if (p.endsWith(".rolls") || p.contains(".rolls.")) return "How many times this pool picks an entry.";
        if (p.endsWith(".weight")) return "This entry's share of the pool's picks: higher drops more often.";
        if (p.endsWith(".quality")) return "Extra share for each point of the killer's luck.";
        if (p.matches(".*entries\\.\\d+\\.name")) return "The item this entry drops.";
        if (p.endsWith(".limit")) return "The most items it can drop with Looting. 0: no limit.";
        if (p.contains(".count.") || p.endsWith(".count")) {
            return p.contains("enchant") || p.contains("looting")
                    ? "Extra items for each level of Looting (from min to max)."
                    : "How many items it drops (from min to max).";
        }
        if (p.endsWith(".add")) return "true: the count adds to the items; false: it sets them.";
        if (p.endsWith("chance")) return "The chance it passes, from 0 to 1.";
        if (p.contains(".levels")) return "The enchantment levels it puts on the item.";
        return "A value of the game's loot table.";
    }

    public static WriteReport save(String table, Value v, String typed) {
        String problem = problem(v, typed);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return Commit.reloadIfOk(new WriteReport().record("the loot value", TableWriter.upsert(
                Schema.LootTables.FILE, keys(table, v), Map.of(Schema.LootTables.VALUE,
                        LootJson.literal(added(v) ? v.own() : v.game(), typed)))));
    }

    /** Removes the user's value, so the game's applies again. */
    public static WriteReport restore(String table, Value v) {
        if (added(v)) {
            return WriteReport.refused("This item is yours, with no game value: Remove item takes it out");
        }
        return Commit.reloadIfOk(new WriteReport().cascaded("your loot value",
                TableWriter.delete(Schema.LootTables.FILE, keys(table, v))));
    }

    private static Map<String, String> keys(String table, Value v) {
        return Map.of(Schema.LootTables.TABLE, table, Schema.LootTables.PATH, v.path());
    }
}
