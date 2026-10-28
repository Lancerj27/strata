package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.BlockStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Client > Blocks reads and does with block stats: every block's values from the game layer
 * (game/blocks.csv), with the user's from block_stats.csv on top.
 */
public final class BlockStatsOps {

    private BlockStatsOps() {
    }

    /** When an edit applies, said on both screens. */
    public static final String APPLIES = "Changes apply at once. The server and each player's game need the same profile.";

    /** One block in the list: its kind, how many values it has, how many are the user's. */
    public record Block(String id, String kind, int values, int edited) {
    }

    /** One value of a block: the game's, and the user's own or "". */
    public record Value(String block, String key, String game, String own, String note) {
        public String effective() {
            return own.isEmpty() ? game : own;
        }
    }

    /** Every block, by kind in BlockStats#KINDS order, then by id. */
    public static List<Block> blocks() {
        return blocks(GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCKS)), own());
    }

    /** The blocks of the sheet with the counts of the user's values. */
    public static List<Block> blocks(List<GameLayer.Row> sheet, Map<String, String> own) {
        Map<String, String> kinds = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> edited = new HashMap<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.KIND.equals(r.group())) {
                kinds.put(r.entity(), r.value());
            } else if (GameLayer.COMPONENT.equals(r.group())) {
                counts.merge(r.entity(), 1, Integer::sum);
                if (own.containsKey(r.entity() + "|" + r.key())) {
                    edited.merge(r.entity(), 1, Integer::sum);
                }
            }
        }
        List<Block> out = new ArrayList<>();
        kinds.forEach((id, kind) -> out.add(new Block(id, kind, counts.getOrDefault(id, 0), edited.getOrDefault(id, 0))));
        out.sort(Comparator.comparingInt((Block b) -> kindOrder(b.kind())).thenComparing(Block::id));
        return out;
    }

    private static int kindOrder(String kind) {
        int i = BlockStats.KINDS.indexOf(kind);
        return i < 0 ? BlockStats.KINDS.size() : i;
    }

    /**
     * One block's values, the user's over the game's, in sheet order: the stats, then its tool, tool level,
     * and tags (game/block_info.csv), every value the game gives a block.
     */
    public static List<Value> values(String block) {
        List<GameLayer.Row> sheet = new ArrayList<>(GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCKS)));
        sheet.addAll(GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCK_INFO)));
        return values(sheet, own(), block);
    }

    /** A block's loot table, its Drops, or "" when it has none (game/blocks.csv). */
    public static String lootTable(String block) {
        for (Value v : values(block)) {
            if (BlockStats.LOOT_TABLE.equals(v.key())) {
                return v.game().equals("minecraft:empty") ? "" : v.game();
            }
        }
        return "";
    }

    /** A value shown, not edited (Set value is greyed for it). */
    public static boolean shownOnly(Value v) {
        return BlockStats.shownOnly(v.key());
    }

    /** One block's values from the sheet, with the edits laid over them. */
    public static List<Value> values(List<GameLayer.Row> sheet, Map<String, String> own, String block) {
        Map<String, String> notes = new HashMap<>();
        List<GameLayer.Row> values = new ArrayList<>();
        for (GameLayer.Row r : sheet) {
            if (!r.entity().equals(block)) continue;
            if (GameLayer.NOTE.equals(r.group())) notes.put(r.key(), r.value());
            if (GameLayer.COMPONENT.equals(r.group())) values.add(r);
        }
        List<Value> out = new ArrayList<>();
        for (GameLayer.Row r : values) {
            out.add(new Value(block, r.key(), r.value(), own.getOrDefault(block + "|" + r.key(), ""),
                    notes.getOrDefault(r.key(), "")));
        }
        // The sheet's order: the edited values, then the shown ones; a key Strata does not know last.
        out.sort(Comparator.comparingInt(v -> {
            int i = BlockStats.ORDER.indexOf(v.key());
            return i < 0 ? BlockStats.ORDER.size() : i;
        }));
        return out;
    }

    /**
     * THE AT-A-GLANCE VALUES: every block's effective values, the user's over the game's, by block then key,
     * with its tool and tool level from game/block_info.csv.
     */
    public static Map<String, Map<String, String>> glance() {
        return glance(glanceSheet(), own());
    }

    private static List<GameLayer.Row> glanceSheet() {
        List<GameLayer.Row> sheet = new ArrayList<>(GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCKS)));
        sheet.addAll(GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCK_INFO)));
        return sheet;
    }

    /** The effective values of the sheet with the edits laid over them. */
    public static Map<String, Map<String, String>> glance(List<GameLayer.Row> sheet, Map<String, String> own) {
        Map<String, Map<String, String>> out = new HashMap<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.COMPONENT.equals(r.group())) {
                out.computeIfAbsent(r.entity(), k -> new HashMap<>())
                        .put(r.key(), own.getOrDefault(r.entity() + "|" + r.key(), r.value()));
            }
        }
        return out;
    }

    /** The keys a column can show: every key the sheet has but the tag list, in {@link BlockStats#ORDER}. */
    public static List<String> glanceKeys(List<GameLayer.Row> sheet) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.COMPONENT.equals(r.group()) && !BlockStats.TAGS.equals(r.key())) {
                keys.add(r.key());
            }
        }
        List<String> out = new ArrayList<>(keys);
        out.sort(Comparator.comparingInt((String k) -> {
            int i = BlockStats.ORDER.indexOf(k);
            return i < 0 ? BlockStats.ORDER.size() : i;
        }).thenComparing(k -> k));
        return out;
    }

    /** The keys the block columns can show, from the game layer. */
    public static List<String> glanceKeys() {
        return glanceKeys(glanceSheet());
    }

    /** The user's values, "block|key" to value. */
    private static Map<String, String> own() {
        Map<String, String> own = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockStats.FILE))) {
            own.put(row.getOrDefault(Schema.BlockStats.BLOCK, "").strip() + "|"
                    + row.getOrDefault(Schema.BlockStats.KEY, "").strip(), row.getOrDefault(Schema.BlockStats.VALUE, "").strip());
        }
        return own;
    }

    /** A value's name. */
    public static String name(Value v) {
        return BlockStats.name(v.key());
    }

    /** What a value does. */
    public static String what(Value v) {
        return BlockStats.what(v.key(), v.note());
    }

    /** Why a typed value cannot be saved, or null. */
    public static String problem(Value v, String typed) {
        String problem = BlockStats.problem(v.key(), typed);
        // The game's rule: an offset only on a block it lets things through.
        if (problem == null && BlockStats.OFFSET.equals(v.key()) && !"none".equalsIgnoreCase(typed.strip())) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(v.block());
            if (id != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)
                    && !com.strata.runtime.BlockStatsRuntime.gameLetsThrough(
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id))) {
                return "The game allows an offset only on a block it lets things through (grass, flowers):"
                        + " its own collision cannot follow the offset";
            }
        }
        return problem;
    }

    /** Saves the user's value, lower case for a word. */
    public static WriteReport save(Value v, String typed) {
        String problem = problem(v, typed);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String value = typed.strip();
        if (BlockStats.isSwitch(v.key())) {
            value = value.toLowerCase(java.util.Locale.ROOT);
        }
        return Commit.reloadIfOk(new WriteReport().record("the block value", TableWriter.upsert(Schema.BlockStats.FILE,
                keys(v), Map.of(Schema.BlockStats.VALUE, value))));
    }

    /** Removes the user's value, so the game's applies again. */
    public static WriteReport restore(Value v) {
        return Commit.reloadIfOk(new WriteReport().cascaded("your value",
                TableWriter.delete(Schema.BlockStats.FILE, keys(v))));
    }

    private static Map<String, String> keys(Value v) {
        return Map.of(Schema.BlockStats.BLOCK, v.block(), Schema.BlockStats.KEY, v.key());
    }
}
