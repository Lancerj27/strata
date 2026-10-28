package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the mob caps editor reads and does: each mob category's caps from the game layer
 * (game/spawns.csv), with the user's from mob_caps.csv on top.
 */
public final class MobCapOps {

    private MobCapOps() {
    }

    /** One cap value: the game's, and the user's own or "". */
    public record Value(String category, String key, String game, String own) {
        public String effective() {
            return own.isEmpty() ? game : own;
        }
    }

    // ---- WHAT EACH VALUE DOES.

    /** Each cap value's name and what it does. */
    public static final java.util.Map<String, String[]> WORDS = java.util.Map.of(
            "max", new String[]{"Max", "How many of this kind the loaded land around the players holds at once:"
                    + " this many for each 17 x 17 chunks. New ones stop spawning at the cap."},
            "despawn_distance", new String[]{"Despawn distance", "Blocks: farther than this from every player,"
                    + " a mob of this kind despawns at once, unless it is named or kept."},
            "no_despawn_distance", new String[]{"No despawn distance", "Blocks: nearer than this to a player, a"
                    + " mob never despawns at random. Farther, an idle one has a small chance each tick."},
            "friendly", new String[]{"Friendly", "true: it spawns on Peaceful, as animals do. false: it is a"
                    + " monster kind, and needs Easy or harder."},
            "persistent", new String[]{"Persistent", "true: it spawns once every 20 seconds, not every tick,"
                    + " as animals do (most come with new land). false: it spawns all the time."});

    /** Each mob category, in words: which mobs it holds. */
    public static final java.util.Map<String, String> CATEGORIES = java.util.Map.of(
            "monster", "Monsters: zombies, skeletons, creepers, spiders, endermen",
            "creature", "Creatures: cows, pigs, sheep, chickens, horses",
            "ambient", "Ambient: bats",
            "axolotls", "Axolotls",
            "underground_water_creature", "Underground water creatures: glow squid",
            "water_creature", "Water creatures: squid, dolphins",
            "water_ambient", "Water ambient: cod, salmon, tropical fish, pufferfish",
            "misc", "Misc: never spawned by the game's spawning (boats, items, and the like)");

    /** A cap value's name. */
    public static String name(Value v) {
        String[] w = WORDS.get(v.key());
        return w == null ? v.key() : w[0];
    }

    /** What a cap value does, or "" when it is not known. */
    public static String what(Value v) {
        String[] w = WORDS.get(v.key());
        return w == null ? "" : w[1];
    }

    /** A category in words. */
    public static String category(String category) {
        String c = CATEGORIES.get(category);
        return c == null ? category : c;
    }

    /**
     * The values the editor lists: every cap but the ones the game compiles in (a change would do nothing),
     * in sheet order.
     */
    public static List<Value> editable(List<Value> values) {
        return values.stream().filter(v -> lockReason(v) == null).toList();
    }

    /** Every category's caps, the user's edits over the game's, in sheet order. */
    public static List<Value> values() {
        Map<String, String> own = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobCaps.FILE))) {
            own.put(row.getOrDefault(Schema.MobCaps.CATEGORY, "").strip() + "|"
                    + row.getOrDefault(Schema.MobCaps.KEY, "").strip(), row.getOrDefault(Schema.MobCaps.VALUE, "").strip());
        }
        return merge(GameLayer.read(GameLayer.dir().resolve(GameLayer.SPAWNS)), own);
    }

    /** The sheet's cap rows with the edits laid over them. */
    public static List<Value> merge(List<GameLayer.Row> sheet, Map<String, String> own) {
        List<Value> out = new ArrayList<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.CAP.equals(r.group())) {
                out.add(new Value(r.entity(), r.key(), r.value(), own.getOrDefault(r.entity() + "|" + r.key(), "")));
            }
        }
        return out;
    }

    /** Why a cap cannot be edited, or null. */
    public static String lockReason(Value v) {
        return com.strata.runtime.MobCapRuntime.FIELDS.containsKey(v.key()) ? null
                : "The game compiles this number into its code, so a change would do nothing";
    }

    /** Why a typed value cannot be saved, or null. */
    public static String problem(Value v, String typed) {
        String lock = lockReason(v);
        if (lock != null) {
            return lock;
        }
        String field = com.strata.runtime.MobCapRuntime.FIELDS.get(v.key());
        return com.strata.runtime.MobCapRuntime.value(field, typed == null ? "" : typed.strip()) == null
                ? (field.startsWith("is") ? "The value must be true or false" : "The value must be a whole number, 0 or more")
                : null;
    }

    /**
     * What a cap value is, for the sheet's control: friendly and persistent are true or false, the others
     * (max, despawn distance) are whole numbers, 0 or more.
     */
    public static com.strata.model.ValueType typeOf(Value v) {
        String field = com.strata.runtime.MobCapRuntime.FIELDS.get(v.key());
        return field != null && field.startsWith("is") ? com.strata.model.ValueType.BOOL
                : com.strata.model.ValueType.whole(0, Integer.MAX_VALUE);
    }

    public static WriteReport save(Value v, String typed) {
        String problem = problem(v, typed);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return Commit.reloadIfOk(new WriteReport().record("the cap", TableWriter.upsert(Schema.MobCaps.FILE,
                keys(v), Map.of(Schema.MobCaps.VALUE, typed.strip()))));
    }

    /** Removes the user's value, so the game's applies again. */
    public static WriteReport restore(Value v) {
        return Commit.reloadIfOk(new WriteReport().cascaded("your cap", TableWriter.delete(Schema.MobCaps.FILE, keys(v))));
    }

    private static Map<String, String> keys(Value v) {
        return Map.of(Schema.MobCaps.CATEGORY, v.category(), Schema.MobCaps.KEY, v.key());
    }
}
