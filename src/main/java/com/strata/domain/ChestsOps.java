package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * THE CHESTS: every chest loot table the game layer holds (runtime.LootExtractor reads each table under
 * chests/, of every namespace, at game start), grouped by structure for Client > Chest loot.
 */
public final class ChestsOps {

    private ChestsOps() {
    }
    /** One chest loot table: its id, its group (the structure), its name in words, its values, whether edited. */
    public record ChestTable(String table, String group, String name, int values, boolean edited) {
    }

    /**
     * EVERY LOOT TABLE: each table the game layer holds, grouped by what it is (#lootGroup), sorted by group
     * and name.
     */
    /** A table's first folder: "chests", "entities", "blocks", "gameplay"... */
    public static String folderOf(String table) {
        String path = table.contains(":") ? table.substring(table.indexOf(':') + 1) : table;
        return path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
    }

    /**
     * Items > Loot tables and Items > Chest loot.
     *
     * @param chests true: the chests' tables only; false: every other table; null: all
     * @param duplicates also the tables another screen edits (a mob's Loot, a block's Loot), Options > Show
     *     duplicate editors
     */
    public static List<ChestTable> tables(Boolean chests, boolean duplicates) {
        return allTablesRead().stream().filter(t -> {
            String folder = folderOf(t.table());
            boolean chest = folder.equals("chests");
            if (chests != null && chests != chest) {
                return false;
            }
            return duplicates || !(folder.equals("entities") || folder.equals("blocks"));
        }).toList();
    }

    private static List<ChestTable> allTablesRead() {
        List<GameLayer.Row> rows = GameLayer.read(GameLayer.dir().resolve(GameLayer.LOOT));
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        List<String> chestIds = new ArrayList<>();
        for (GameLayer.Row r : rows) {
            if (GameLayer.LOOT_VALUE.equals(r.group())) {
                counts.merge(r.entity(), 1, Integer::sum);
            } else if (GameLayer.CHEST.equals(r.group())) {
                chestIds.add(r.value());
            }
        }
        java.util.Set<String> edited = new java.util.HashSet<>();
        CsvTable.read(Paths.table(Schema.LootTables.FILE)).forEach(row ->
                edited.add(row.getOrDefault(Schema.LootTables.TABLE, "").strip()));
        List<ChestTable> out = new ArrayList<>();
        counts.forEach((id, n) -> out.add(new ChestTable(id, lootGroup(id, chestIds), lootName(id), n, edited.contains(id))));
        out.sort(java.util.Comparator.comparing(ChestTable::group).thenComparing(ChestTable::name));
        return out;
    }

    /**
     * What a loot table is, in words (pure): a chest's is "Chests: " and its structure (#chestGroup); a mob's
     * "Mobs", a block's "Blocks"; any other its first folder ("gameplay/fishing" is Gameplay).
     */
    public static String lootGroup(String table, java.util.Collection<String> chests) {
        String ns = table.contains(":") ? table.substring(0, table.indexOf(':')) : "minecraft";
        String path = table.contains(":") ? table.substring(table.indexOf(':') + 1) : table;
        String folder = path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
        if (folder.equals("chests")) {
            String g = chestGroup(table, chests);
            return ("minecraft".equals(ns) ? "" : ns + ": ") + "Chests: "
                    + ("minecraft".equals(ns) ? g : g.substring(g.indexOf(':') + 2));
        }
        String words = switch (folder) {
            case "entities" -> "Mobs";
            case "blocks" -> "Blocks";
            case "" -> "Other";
            default -> words(folder);
        };
        return "minecraft".equals(ns) ? words : ns + ": " + words;
    }

    /** A loot table's name in words: its last part ("gameplay/fishing/fish" is Fish). */
    public static String lootName(String table) {
        String path = table.contains(":") ? table.substring(table.indexOf(':') + 1) : table;
        return words(path.substring(path.lastIndexOf('/') + 1));
    }

    /**
     * The structure a chest table belongs to, in words (pure): its folder ("chests/village/..." is Village);
     * else the longest start of its name, word by word, that another table of its namespace shares
     * (shipwreck_map and shipwreck_supply are Shipwreck); else its own name.
     */
    public static String chestGroup(String table, java.util.Collection<String> all) {
        String ns = table.contains(":") ? table.substring(0, table.indexOf(':')) : "minecraft";
        String rest = afterChests(table);
        String group;
        if (rest.contains("/")) {
            group = rest.substring(0, rest.indexOf('/'));
        } else {
            String[] words = rest.split("_");
            group = rest;
            for (int n = words.length; n >= 1; n--) {
                String prefix = String.join("_", java.util.Arrays.copyOf(words, n));
                boolean shared = all.stream().filter(o -> !o.equals(table))
                        .filter(o -> (o.contains(":") ? o.substring(0, o.indexOf(':')) : "minecraft").equals(ns))
                        .map(ChestsOps::afterChests)
                        .anyMatch(o -> !o.contains("/") && (o.equals(prefix) || o.startsWith(prefix + "_")));
                if (shared) {
                    group = prefix;
                    break;
                }
            }
        }
        String words = words(group);
        return "minecraft".equals(ns) ? words : ns + ": " + words;
    }

    /** A chest table's name in words: "chests/shipwreck_treasure" is Shipwreck treasure. */
    public static String chestName(String table) {
        String rest = afterChests(table);
        return words(rest.substring(rest.lastIndexOf('/') + 1));
    }

    private static String afterChests(String table) {
        String path = table.contains(":") ? table.substring(table.indexOf(':') + 1) : table;
        return path.startsWith("chests/") ? path.substring("chests/".length()) : path;
    }

    private static String words(String id) {
        String s = id.replace('_', ' ').strip();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- a chest's loot entries, as the Loot and Equipment lists show items ----

    /**
     * One loot entry of a table, read from its values (the game's with the user's on top).
     *
     * @param prefix its place, "pools.P.entries.N"
     * @param type the entry's kind: minecraft:item, or another (a tag, a table, empty)
     * @param item what it gives: the item id, else the name the entry holds, else ""
     * @param weight its share of the pool's picks; 0 never picked
     * @param countMin the fewest it gives, as the game's count says ("1" with no count)
     * @param countMax the most
     * @param countSet the count is a plain number or range this editor can set
     * @param added the user added it
     * @param edited the user changed a value of it
     */
    public record Entry(String prefix, int pool, int index, String type, String item, int weight, String countMin,
                        String countMax, boolean countSet, boolean added, boolean edited) {

        public boolean removed() {
            return weight <= 0;
        }

        /** An entry that holds other entries has no weight or item of its own. */
        public boolean single() {
            return !type.endsWith("alternatives") && !type.endsWith("group") && !type.endsWith("sequence");
        }
    }

    /** One pool: how many times it picks (rolls, min to max), and whether that is a plain number or range. */
    public record Pool(int index, String rollsMin, String rollsMax, boolean rollsSet, int totalWeight) {
    }

    /** The values path to effective literal, in the table's order. */
    private static Map<String, String> effective(LootTableOps.Sheet sheet) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        sheet.values().forEach(v -> out.put(v.path(), v.effective()));
        return out;
    }

    private static String shown(String literal) {
        return literal == null ? "" : com.strata.data.LootJson.shown(literal);
    }

    private static boolean number(String literal) {
        try {
            return literal != null && com.google.gson.JsonParser.parseString(literal).isJsonPrimitive()
                    && com.google.gson.JsonParser.parseString(literal).getAsJsonPrimitive().isNumber();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A number literal as a whole number's text when it is one: 2.0 is "2". */
    private static String whole(String literal) {
        try {
            double d = Double.parseDouble(literal);
            return d == Math.rint(d) ? String.valueOf((long) d) : literal;
        } catch (RuntimeException e) {
            return literal;
        }
    }

    /** A number provider at a path (a count, the rolls): {min, max, set}. */
    private static String[] range(Map<String, String> eff, String at) {
        String plain = eff.get(at);
        if (plain != null) {
            return number(plain) ? new String[]{whole(plain), whole(plain), "yes"} : new String[]{"?", "?", ""};
        }
        String type = shown(eff.get(at + ".type"));
        if ((type.endsWith("uniform")) && number(eff.get(at + ".min")) && number(eff.get(at + ".max"))) {
            return new String[]{whole(eff.get(at + ".min")), whole(eff.get(at + ".max")), "yes"};
        }
        if (type.endsWith("constant") && number(eff.get(at + ".value"))) {
            return new String[]{whole(eff.get(at + ".value")), whole(eff.get(at + ".value")), ""};
        }
        return new String[]{"?", "?", ""};
    }

    /** The index of the entry's last set_count function, or -1. */
    private static int lastSetCount(Map<String, String> eff, String prefix) {
        int last = -1;
        for (int k = 0; eff.containsKey(prefix + ".functions." + k + ".function") || hasUnder(eff, prefix + ".functions." + k + "."); k++) {
            if (shown(eff.get(prefix + ".functions." + k + ".function")).endsWith("set_count")) {
                last = k;
            }
        }
        return last;
    }

    private static boolean hasUnder(Map<String, String> eff, String prefix) {
        return eff.keySet().stream().anyMatch(p -> p.startsWith(prefix));
    }

    /** The rows of the strata:item_variant function at a place. */
    private static Map<String, String> variantRows(String at, String variant) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        out.put(at + ".function", new com.google.gson.JsonPrimitive(
                com.strata.runtime.ItemVariantLootFunction.ID).toString());
        out.put(at + ".variant", new com.google.gson.JsonPrimitive(variant).toString());
        return out;
    }

    /** The place of an entry's strata:item_variant function, or -1. */
    private static int variantFunction(Map<String, String> eff, String prefix) {
        for (int k = 0; k < functionCount(eff, prefix); k++) {
            if (shown(eff.get(prefix + ".functions." + k + ".function"))
                    .equals(com.strata.runtime.ItemVariantLootFunction.ID)) {
                return k;
            }
        }
        return -1;
    }

    /** The item variant an entry makes, or "". */
    private static String variantOf(Map<String, String> eff, String prefix) {
        int k = variantFunction(eff, prefix);
        return k < 0 ? "" : shown(eff.get(prefix + ".functions." + k + ".variant"));
    }

    /** How many functions an entry has: the next one's place. */
    private static int functionCount(Map<String, String> eff, String prefix) {
        int k = 0;
        while (hasUnder(eff, prefix + ".functions." + k + ".")) {
            k++;
        }
        return k;
    }

    /** The table's entries, pool by pool, in order. */
    public static List<Entry> entries(LootTableOps.Sheet sheet) {
        Map<String, String> eff = effective(sheet);
        List<String> prefixes = new ArrayList<>();
        for (LootTableOps.Value v : sheet.values()) {
            String e = LootTableOps.entryOf(v.path());
            if (e != null && !prefixes.contains(e)) {
                prefixes.add(e);
            }
        }
        List<Entry> out = new ArrayList<>();
        for (String prefix : prefixes) {
            int pool = LootTableOps.poolOf(prefix);
            int index = Integer.parseInt(prefix.substring(prefix.lastIndexOf('.') + 1));
            String type = shown(eff.get(prefix + ".type"));
            String item = shown(eff.get(prefix + ".name"));
            // An item variant's entry reads as the variant.
            String variant = variantOf(eff, prefix);
            if (!variant.isEmpty()) {
                item = com.strata.model.ItemVariant.ref(variant);
            }
            String w = eff.get(prefix + ".weight");
            int weight = number(w) ? (int) Double.parseDouble(w) : 1;
            int k = lastSetCount(eff, prefix);
            String[] count = k < 0 ? new String[]{"1", "1", "yes"} : range(eff, prefix + ".functions." + k + ".count");
            List<LootTableOps.Value> own = sheet.values().stream()
                    .filter(v -> prefix.equals(LootTableOps.entryOf(v.path()))).toList();
            boolean added = own.stream().allMatch(LootTableOps::added);
            boolean edited = !added && own.stream().anyMatch(v -> !v.own().isEmpty());
            out.add(new Entry(prefix, pool, index, type, item, weight, count[0], count[1], !count[2].isEmpty(),
                    added, edited));
        }
        out.sort(java.util.Comparator.comparingInt(Entry::pool).thenComparingInt(Entry::index));
        return out;
    }

    /** The table's pools, in order, with the total weight of what each can pick. */
    public static List<Pool> pools(LootTableOps.Sheet sheet) {
        Map<String, String> eff = effective(sheet);
        List<Entry> entries = entries(sheet);
        List<Pool> out = new ArrayList<>();
        for (int p = 0; hasUnder(eff, "pools." + p + "."); p++) {
            String[] rolls = range(eff, "pools." + p + ".rolls");
            int pool = p;
            int total = entries.stream().filter(e -> e.pool() == pool && e.single()).mapToInt(e -> Math.max(0, e.weight())).sum();
            out.add(new Pool(p, rolls[0], rolls[1], !rolls[2].isEmpty(), total));
        }
        return out;
    }

    /** The entry's share of its pool's picks, in percent, "" for a removed one. */
    public static String share(Entry e, List<Pool> pools) {
        if (e.removed() || !e.single()) {
            return "";
        }
        int total = pools.stream().filter(p -> p.index() == e.pool()).mapToInt(Pool::totalWeight).sum();
        if (total <= 0) {
            return "";
        }
        double pct = Math.round(1000.0 * e.weight() / total) / 10.0;
        return (pct == Math.rint(pct) ? String.valueOf((long) pct) : String.valueOf(pct)) + "%";
    }

    /** A table's sheet by its id, for the chest screens (they call only this class). */
    public static LootTableOps.Sheet sheet(String table) {
        return LootTableOps.sheetForTable(table);
    }

    /** The loot entry form, as typed. */
    public record EntryForm(String item, String weight, String countMin, String countMax) {
    }

    /** The form for an entry, or for a new one (item "", weight 1, count 1). */
    public static EntryForm formFor(Entry e) {
        return e == null ? new EntryForm("", "1", "1", "1")
                : new EntryForm(e.item(), String.valueOf(e.weight()), e.countMin(), e.countMax());
    }

    private static Integer whole(String typed, int least) {
        try {
            int n = Integer.parseInt(typed == null ? "" : typed.strip());
            return n >= least ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Why the form cannot be saved, or null. */
    public static String entryProblem(Entry e, EntryForm f) {
        if (e != null && !e.single()) {
            return "This entry holds other entries: edit it in Loot table";
        }
        if (!f.item().strip().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            return "Pick an item";
        }
        String variant = com.strata.model.ItemVariant.nameOf(f.item());
        if (variant != null && ItemVariantOps.baseOf(variant).isEmpty()) {
            return "There is no item variant " + variant;
        }
        if (whole(f.weight(), 0) == null) {
            return "The weight must be a whole number, 0 or more";
        }
        Integer min = whole(f.countMin(), 0);
        Integer max = whole(f.countMax(), 0);
        if (min == null || max == null) {
            return "The count must be whole numbers, 0 or more";
        }
        return min > max ? "The count's min must not be above its max" : null;
    }

    /** A number or a uniform range as a JSON literal: 3, or {"type":"minecraft:uniform","min":1,"max":3}. */
    private static String rangeLiteral(int min, int max) {
        return min == max ? String.valueOf(min)
                : "{\"type\":\"minecraft:uniform\",\"min\":" + min + ",\"max\":" + max + "}";
    }

    /** What saving the form writes, path to literal (pure). */
    public static Map<String, String> entryWrites(LootTableOps.Sheet sheet, Entry e, int pool, EntryForm f) {
        int weight = Integer.parseInt(f.weight().strip());
        int min = Integer.parseInt(f.countMin().strip());
        int max = Integer.parseInt(f.countMax().strip());
        Map<String, String> out = new java.util.LinkedHashMap<>();
        String variant = com.strata.model.ItemVariant.nameOf(f.item());
        String item = variant == null ? f.item().strip() : ItemVariantOps.baseOf(variant);
        if (e == null) {
            out.putAll(LootTableOps.itemRows(pool, LootTableOps.nextEntry(sheet.values(), pool), item));
            String prefix = out.keySet().iterator().next();
            prefix = prefix.substring(0, prefix.lastIndexOf('.'));
            out.put(prefix + ".weight", String.valueOf(weight));
            out.put(prefix + ".functions.0.count.min", String.valueOf(min));
            out.put(prefix + ".functions.0.count.max", String.valueOf(max));
            if (variant != null) {
                // After set_count: the variant's stack keeps the count.
                out.putAll(variantRows(prefix + ".functions.1", variant));
            }
            return out;
        }
        Map<String, String> eff = effective(sheet);
        String p = e.prefix();
        if (!f.item().strip().equals(e.item()) && eff.containsKey(p + ".name")) {
            out.put(p + ".name", new com.google.gson.JsonPrimitive(item).toString());
            int at = variantFunction(eff, p);
            if (variant != null || at >= 0) {
                // To a variant, another variant, or back to a plain item ("": none).
                out.putAll(variantRows(p + ".functions." + (at >= 0 ? at : functionCount(eff, p)),
                        variant == null ? "" : variant));
            }
        }
        if (weight != e.weight()) {
            out.put(p + ".weight", String.valueOf(weight));
        }
        if (!String.valueOf(min).equals(e.countMin()) || !String.valueOf(max).equals(e.countMax())) {
            int k = lastSetCount(eff, p);
            String at = p + ".functions." + k + ".count";
            if (k >= 0 && eff.containsKey(at) && number(eff.get(at))) {
                out.put(at, rangeLiteral(min, max));
            } else if (k >= 0 && shown(eff.get(at + ".type")).endsWith("uniform")
                    && number(eff.get(at + ".min")) && number(eff.get(at + ".max"))) {
                out.put(at + ".min", String.valueOf(min));
                out.put(at + ".max", String.valueOf(max));
            } else {
                String f2 = p + ".functions." + functionCount(eff, p);
                out.put(f2 + ".function", "\"minecraft:set_count\"");
                out.put(f2 + ".count.type", "\"minecraft:uniform\"");
                out.put(f2 + ".count.min", String.valueOf(min));
                out.put(f2 + ".count.max", String.valueOf(max));
            }
        }
        return out;
    }

    /** Why the pool's rolls cannot be set, or null. */
    public static String rollsProblem(Pool pool, String minTyped, String maxTyped) {
        if (!pool.rollsSet()) {
            return "This pool's rolls are not a plain number or range: edit them in Loot table";
        }
        Integer min = whole(minTyped, 0);
        Integer max = whole(maxTyped, 0);
        if (min == null || max == null) {
            return "The rolls must be whole numbers, 0 or more";
        }
        return min > max ? "The rolls' min must not be above its max" : null;
    }

    /** What setting a pool's rolls writes, path to literal. */
    public static Map<String, String> rollsWrites(LootTableOps.Sheet sheet, Pool pool, int min, int max) {
        Map<String, String> eff = effective(sheet);
        String at = "pools." + pool.index() + ".rolls";
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (eff.containsKey(at)) {
            out.put(at, rangeLiteral(min, max));
        } else {
            out.put(at + ".min", String.valueOf(min));
            out.put(at + ".max", String.valueOf(max));
        }
        return out;
    }

    // ---- the writes: each then reloads once ----

    private static void write(WriteReport report, String table, Map<String, String> rows) {
        rows.forEach((path, literal) -> {
            if (report.ok()) {
                report.record("the loot value", com.strata.data.TableWriter.upsert(Schema.LootTables.FILE,
                        Map.of(Schema.LootTables.TABLE, table, Schema.LootTables.PATH, path),
                        Map.of(Schema.LootTables.VALUE, literal)));
            }
        });
    }

    /** Saves the loot entry form: a new entry into the pool, or the changes to one. */
    public static WriteReport saveEntry(String table, Entry e, int pool, EntryForm f) {
        LootTableOps.Sheet sheet = LootTableOps.sheetForTable(table);
        String problem = entryProblem(e, f);
        if (problem == null && e == null) {
            problem = LootTableOps.addProblem(sheet, pool, f.item());
        }
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        WriteReport report = new WriteReport();
        write(report, table, entryWrites(sheet, e, pool, f));
        return Commit.reloadIfOk(report);
    }

    /** Sets a pool's rolls. */
    public static WriteReport saveRolls(String table, Pool pool, String minTyped, String maxTyped) {
        String problem = rollsProblem(pool, minTyped, maxTyped);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        WriteReport report = new WriteReport();
        write(report, table, rollsWrites(LootTableOps.sheetForTable(table), pool,
                Integer.parseInt(minTyped.strip()), Integer.parseInt(maxTyped.strip())));
        return Commit.reloadIfOk(report);
    }

    /**
     * Removes entries: a game entry gets weight 0 (never picked; Restore brings it back); an added one goes,
     * the later added ones of its pool moving up (LootTableOps#removal).
     */
    public static WriteReport removeEntries(String table, List<Entry> picked) {
        if (picked.isEmpty()) {
            return WriteReport.refused("Pick a loot entry first");
        }
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            List<Entry> order = new ArrayList<>(picked);
            order.sort(java.util.Comparator.comparingInt(Entry::pool).thenComparing(Entry::index,
                    java.util.Comparator.reverseOrder()));
            for (Entry e : order) {
                if (!report.ok()) {
                    break;
                }
                if (!e.single()) {
                    return WriteReport.refused("An entry that holds other entries is removed in Loot table");
                }
                LootTableOps.Sheet sheet = LootTableOps.sheetForTable(table);
                LootTableOps.Value any = sheet.values().stream()
                        .filter(v -> e.prefix().equals(LootTableOps.entryOf(v.path()))).findFirst().orElse(null);
                LootTableOps.Removal plan = LootTableOps.removal(sheet, any);
                if (plan == null) {
                    continue;
                }
                for (String path : plan.delete()) {
                    report.record("the entry's value", com.strata.data.TableWriter.delete(Schema.LootTables.FILE,
                            Map.of(Schema.LootTables.TABLE, table, Schema.LootTables.PATH, path)));
                }
                write(report, table, plan.write());
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Puts a game entry back as the game has it: every value of yours on it goes (with Strata's count). */
    public static WriteReport restoreEntry(String table, Entry e) {
        if (e.added()) {
            return WriteReport.refused("This entry is yours, with no game values: Remove takes it out");
        }
        WriteReport report = new WriteReport();
        for (LootTableOps.Value v : LootTableOps.sheetForTable(table).values()) {
            if (!v.own().isEmpty() && v.path().startsWith(e.prefix() + ".")) {
                report.cascaded("the entry's value", com.strata.data.TableWriter.delete(Schema.LootTables.FILE,
                        Map.of(Schema.LootTables.TABLE, table, Schema.LootTables.PATH, v.path())));
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** Puts the whole table back as the game has it: every value and entry of yours goes. */
    public static WriteReport restoreAll(String table) {
        WriteReport report = new WriteReport();
        for (LootTableOps.Value v : LootTableOps.sheetForTable(table).values()) {
            if (!v.own().isEmpty()) {
                report.cascaded("your loot value", com.strata.data.TableWriter.delete(Schema.LootTables.FILE,
                        Map.of(Schema.LootTables.TABLE, table, Schema.LootTables.PATH, v.path())));
            }
        }
        return Commit.reloadIfOk(report);
    }
}