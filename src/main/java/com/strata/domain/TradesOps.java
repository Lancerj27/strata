package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.Trade;
import com.strata.runtime.TradesRuntime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the trade editor reads and does. */
public final class TradesOps {

    private TradesOps() {
    }

    /** The level names, 1 to 5. */
    public static final List<String> LEVELS = List.of("Novice", "Apprentice", "Journeyman", "Expert", "Master");

    /** A list's name in words: "Farmer, Novice"; the wandering trader's "Common" and "Rare". */
    public static String listName(String profession, int level) {
        if (TradesRuntime.WANDERER.equals(profession)) {
            return "Wandering trader, " + (level == 1 ? "common (5 picked)" : "rare (1 picked)");
        }
        String name = profession.substring(profession.indexOf(':') + 1).replace('_', ' ');
        name = name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return name + ", " + (level >= 1 && level <= 5 ? LEVELS.get(level - 1) : "level " + level);
    }

    /** The game's trades: "profession|level" to the list, from the game layer. */
    private static Map<String, List<Trade>> gameLists() {
        Map<String, List<Trade>> out = new LinkedHashMap<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.TRADES))) {
            if (!GameLayer.TRADE.equals(r.group())) {
                continue;
            }
            Trade t = Trade.decode(r.value());
            String level = r.key().substring(0, r.key().indexOf('|'));
            if (t != null) {
                out.computeIfAbsent(r.entity() + "|" + level, k -> new ArrayList<>()).add(t);
            }
        }
        return out;
    }

    /** True for a mob that trades: the villager and the wandering trader. */
    public static boolean trades(String entity) {
        return "minecraft:villager".equals(entity) || TradesRuntime.WANDERER.equals(entity);
    }

    /** The lists a trading mob has, "profession|level", in order. */
    public static List<String> listsFor(String entity) {
        List<String> out = new ArrayList<>();
        for (String key : gameLists().keySet()) {
            boolean wanderer = key.startsWith(TradesRuntime.WANDERER + "|");
            if (TradesRuntime.WANDERER.equals(entity) ? wanderer : "minecraft:villager".equals(entity) && !wanderer) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * One trade as the editor lists it.
     *
     * @param list "profession|level"
     * @param key its key in the list ("g3", "a1@global")
     * @param layer who set it: "" the game, else "global", "tier", "biome"
     * @param here this scope set it (added, changed, or removed here)
     * @param removed removed at this scope (listed so Restore can bring it back)
     */
    public record TradeRow(String list, String key, Trade trade, String layer, boolean here, boolean removed) {
        public String profession() {
            return list.substring(0, list.lastIndexOf('|'));
        }

        public int level() {
            return Integer.parseInt(list.substring(list.lastIndexOf('|') + 1));
        }

        /** Added at this scope: its row goes on Remove. */
        public boolean addedHere() {
            // "a1@biome" at the biome: added here.
            return here && !removed && key.startsWith("a") && key.endsWith("@" + layer);
        }
    }

    /** "global", "tier", or "biome", for a scope as the editor names it. */
    public static String layerOf(String scope) {
        String s = scope == null ? "" : scope.strip();
        return s.isEmpty() || "global".equals(s) ? "global" : s;
    }

    private static String scopeKey(String scope, String scopeId) {
        String layer = layerOf(scope);
        return "global".equals(layer) ? "|" : layer + "|" + (scopeId == null ? "" : scopeId.strip());
    }

    /** The layers a scope's list is built from. */
    private static Map<String, String> layersFor(String scope, String scopeId) {
        String layer = layerOf(scope);
        if ("tier".equals(layer)) {
            return TradesRuntime.layers(scopeId, "");
        }
        if ("biome".equals(layer)) {
            return TradesRuntime.layers(com.strata.data.Config.resolve(scopeId).tier(), scopeId);
        }
        return TradesRuntime.layers("", "");
    }

    /** Every trade of a trading mob's lists, as this scope serves them, with this scope's removals. */
    public static List<TradeRow> rows(String entity, String scope, String scopeId) {
        Map<String, List<Trade>> game = gameLists();
        var edits = TradesRuntime.index(CsvTable.read(Paths.table(Schema.Trades.FILE)));
        return rows(listsFor(entity), game, edits, layersFor(scope, scopeId), layerOf(scope), scopeKey(scope, scopeId));
    }

    /** The rows, from the game lists and the edits. */
    public static List<TradeRow> rows(List<String> lists, Map<String, List<Trade>> game,
                                      Map<String, Map<String, List<Trade.Edit>>> edits, Map<String, String> layers,
                                      String here, String hereKey) {
        List<TradeRow> out = new ArrayList<>();
        for (String list : lists) {
            Map<String, List<Trade.Edit>> byScope = edits.getOrDefault(list, Map.of());
            // What the wider layers leave, to name what this scope removed.
            Map<String, String> wider = new LinkedHashMap<>(layers);
            wider.remove(here);
            List<Trade.Entry> before = TradesRuntime.effective(game.getOrDefault(list, List.of()), byScope, wider);
            for (Trade.Entry e : TradesRuntime.effective(game.getOrDefault(list, List.of()), byScope, layers)) {
                out.add(new TradeRow(list, e.key(), e.trade(), e.layer(), here.equals(e.layer()), false));
            }
            for (Trade.Edit edit : byScope.getOrDefault(hereKey, List.of())) {
                if (edit.remove()) {
                    before.stream().filter(b -> b.key().equals(edit.key())).findFirst()
                            .ifPresent(b -> out.add(new TradeRow(list, b.key(), b.trade(), here, true, true)));
                }
            }
        }
        return out;
    }

    /** The trade form, as typed. */
    public record TradeForm(String costA, String costACount, String costB, String costBCount, String result,
                            String resultCount, String maxUses, String xp, String priceMultiplier) {
    }

    /** The form for a trade, or a new one (1 emerald for 1 item, 12 uses, 1 XP, 0.05). */
    public static TradeForm formFor(Trade t) {
        if (t == null) {
            return new TradeForm("minecraft:emerald", "1", "", "0", "", "1", "12", "1", "0.05");
        }
        return new TradeForm(t.costA(), String.valueOf(t.costACount()), t.costB(), String.valueOf(t.costBCount()),
                t.result(), String.valueOf(t.resultCount()), String.valueOf(t.maxUses()), String.valueOf(t.xp()),
                com.strata.data.Parse.number(t.priceMultiplier()));
    }

    private static final String ID = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    private static Integer whole(String s, int least) {
        try {
            int n = Integer.parseInt(s == null ? "" : s.strip());
            return n >= least ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Why the form cannot be saved, or null. */
    public static String problem(TradeForm f) {
        if (!f.costA().strip().matches(ID)) {
            return "Pick what the trader wants";
        }
        if (!f.result().strip().matches(ID)) {
            return "Pick what the trader gives";
        }
        if (whole(f.costACount(), 1) == null || whole(f.resultCount(), 1) == null) {
            return "The counts must be whole numbers, 1 or more";
        }
        Integer b = whole(f.costBCount(), 0);
        if (b == null || (b > 0 && !f.costB().strip().matches(ID))) {
            return "The second item needs an item, or a count of 0 for none";
        }
        if (whole(f.maxUses(), 1) == null) {
            return "Max uses must be a whole number, 1 or more";
        }
        if (whole(f.xp(), 0) == null) {
            return "The XP must be a whole number, 0 or more";
        }
        try {
            double m = Double.parseDouble(f.priceMultiplier().strip());
            return m >= 0 && Double.isFinite(m) ? null : "The price multiplier must be 0 or more";
        } catch (NumberFormatException e) {
            return "The price multiplier must be a number";
        }
    }

    private static Map<String, String> keys(String list, String scope, String scopeId, String rowKey) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.Trades.PROFESSION, list.substring(0, list.lastIndexOf('|')));
        keys.put(Schema.Trades.LEVEL, list.substring(list.lastIndexOf('|') + 1));
        keys.put(Schema.Trades.SCOPE, "global".equals(layerOf(scope)) ? "" : layerOf(scope));
        keys.put(Schema.Trades.SCOPE_ID, "global".equals(layerOf(scope)) ? "" : scopeId.strip());
        keys.put(Schema.Trades.ROW, rowKey);
        return keys;
    }

    private static Map<String, String> values(String mode, TradeForm f) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put(Schema.Trades.MODE, mode);
        v.put(Schema.Trades.COST_A, f == null ? "" : f.costA().strip());
        v.put(Schema.Trades.COST_A_COUNT, f == null ? "" : f.costACount().strip());
        boolean noB = f == null || whole(f.costBCount(), 0) == null || whole(f.costBCount(), 0) == 0;
        v.put(Schema.Trades.COST_B, noB ? "" : f.costB().strip());
        v.put(Schema.Trades.COST_B_COUNT, noB ? "0" : f.costBCount().strip());
        v.put(Schema.Trades.RESULT, f == null ? "" : f.result().strip());
        v.put(Schema.Trades.RESULT_COUNT, f == null ? "" : f.resultCount().strip());
        v.put(Schema.Trades.MAX_USES, f == null ? "" : f.maxUses().strip());
        v.put(Schema.Trades.XP, f == null ? "" : f.xp().strip());
        v.put(Schema.Trades.PRICE_MULTIPLIER, f == null ? "" : f.priceMultiplier().strip());
        return v;
    }

    /** The key an edit of this row writes: its own for one added here ("a1"), else the row's ("g3", "a1@global"). */
    private static String writeKey(TradeRow row) {
        return row.addedHere() ? row.key().substring(0, row.key().indexOf('@')) : row.key();
    }

    /** An edit here needs Strata's trade lists (Options > Trade changes), which ship Off: it goes On. */
    private static void switchOn() {
        OptionsOps.turnOn(OptionsOps.TRADES, com.strata.data.StrataConfig.ENABLE_TRADES);
    }

    /** Saves the form: a new trade into the list (row null), or a change to one at this scope. */
    public static WriteReport save(String scope, String scopeId, String list, TradeRow row, TradeForm f) {
        String problem = problem(f);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String key;
        String mode;
        if (row == null) {
            int n = 1;
            for (Map<String, String> r : CsvTable.read(Paths.table(Schema.Trades.FILE))) {
                Map<String, String> k = keys(list, scope, scopeId, "");
                boolean same = r.getOrDefault(Schema.Trades.PROFESSION, "").strip().equals(k.get(Schema.Trades.PROFESSION))
                        && r.getOrDefault(Schema.Trades.LEVEL, "").strip().equals(k.get(Schema.Trades.LEVEL))
                        && r.getOrDefault(Schema.Trades.SCOPE, "").strip().equals(k.get(Schema.Trades.SCOPE))
                        && r.getOrDefault(Schema.Trades.SCOPE_ID, "").strip().equals(k.get(Schema.Trades.SCOPE_ID));
                String rk = r.getOrDefault(Schema.Trades.ROW, "").strip();
                if (same && rk.matches("a\\d+")) {
                    n = Math.max(n, Integer.parseInt(rk.substring(1)) + 1);
                }
            }
            key = "a" + n;
            mode = "add";
        } else {
            key = writeKey(row);
            mode = row.addedHere() ? "add" : "edit";
        }
        switchOn();
        return Commit.reloadIfOk(new WriteReport().record("the trade",
                TableWriter.upsert(Schema.Trades.FILE, keys(list, scope, scopeId, key), values(mode, f))));
    }

    /** Removes trades at this scope: one added here goes; any other is removed here (Restore brings it back). */
    public static WriteReport remove(String scope, String scopeId, List<TradeRow> rows) {
        if (rows.isEmpty()) {
            return WriteReport.refused("Pick a trade first");
        }
        switchOn();
        WriteReport report = new WriteReport();
        for (TradeRow row : rows) {
            if (!report.ok() || row.removed()) {
                continue;
            }
            Map<String, String> keys = keys(row.list(), scope, scopeId, writeKey(row));
            report.record("the trade", row.addedHere() ? TableWriter.delete(Schema.Trades.FILE, keys)
                    : TableWriter.upsert(Schema.Trades.FILE, keys, values("remove", null)));
        }
        return Commit.reloadIfOk(report);
    }

    /** Takes this scope's change to a trade away, so the wider scope's (or the game's) applies again. */
    public static WriteReport restore(String scope, String scopeId, TradeRow row) {
        if (row.addedHere()) {
            return WriteReport.refused("This trade was added here: Remove takes it out");
        }
        if (!row.here()) {
            return WriteReport.refused("This trade is not changed here");
        }
        return Commit.reloadIfOk(new WriteReport().cascaded("the change",
                TableWriter.delete(Schema.Trades.FILE, keys(row.list(), scope, scopeId, writeKey(row)))));
    }

    /** Takes every change of this scope to a trading mob's lists away. */
    public static WriteReport restoreAll(String entity, String scope, String scopeId) {
        WriteReport report = new WriteReport();
        List<String> lists = listsFor(entity);
        for (Map<String, String> r : CsvTable.read(Paths.table(Schema.Trades.FILE))) {
            String list = r.getOrDefault(Schema.Trades.PROFESSION, "").strip() + "|"
                    + r.getOrDefault(Schema.Trades.LEVEL, "").strip();
            String sk = r.getOrDefault(Schema.Trades.SCOPE, "").strip() + "|" + r.getOrDefault(Schema.Trades.SCOPE_ID, "").strip();
            if (lists.contains(list) && sk.equals(scopeKey(scope, scopeId))) {
                report.cascaded("a trade change", TableWriter.delete(Schema.Trades.FILE,
                        keys(list, scope, scopeId, r.getOrDefault(Schema.Trades.ROW, "").strip())));
            }
        }
        return Commit.reloadIfOk(report);
    }

    // ---- the game's villager types and professions, in words (set in Special mobs) ----

    public static final String DEFAULT = "Default";


    private static List<String> gameIds(String group) {
        List<String> out = new ArrayList<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.TRADES))) {
            if (group.equals(r.group())) {
                out.add(r.value());
            }
        }
        return out;
    }

    /** The villager types the game has (from the game layer). */
    public static List<String> villagerTypes() {
        return gameIds(GameLayer.VILLAGER_TYPE);
    }

    /** The professions the game has (from the game layer). */
    public static List<String> professions() {
        return gameIds(GameLayer.PROFESSION);
    }

    /** An id in words: "Default" for "", "Desert" for minecraft:desert, "somemod: Tower" for a mod's. */
    public static String word(String id) {
        if (id == null || id.isBlank()) {
            return DEFAULT;
        }
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        String name = path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return "minecraft".equals(ns) ? name : ns + ": " + name;
    }

    /** A dropdown's words: Default, then each id's. */
    public static List<String> choices(List<String> ids) {
        List<String> out = new ArrayList<>();
        out.add(DEFAULT);
        ids.forEach(id -> out.add(word(id)));
        return out;
    }

    /** The id a dropdown word names, or "" for Default. */
    public static String idOf(String word, List<String> ids) {
        return ids.stream().filter(id -> word(id).equals(word)).findFirst().orElse("");
    }

    /** A trade in words: "12 Wheat -> 1 Emerald"; a random kind by what it gives. */
    public static String text(Trade t, java.util.function.Function<String, String> words) {
        if (!t.plain() && t.costA().isEmpty()) {
            return t.note();
        }
        String cost = t.costACount() + " " + words.apply(t.costA())
                + (t.costB().isEmpty() || t.costBCount() <= 0 ? "" : " + " + t.costBCount() + " " + words.apply(t.costB()));
        return cost + " -> " + (t.plain() ? t.resultCount() + " " + words.apply(t.result()) : t.note());
    }
}
