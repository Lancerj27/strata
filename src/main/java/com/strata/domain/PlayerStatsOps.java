package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.model.AttributeGuide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Client > Player scaling > Player Base Stats reads and does: the player's attributes from the
 * game layer (game/mobs.csv, minecraft:player), with the user's from mob_base_stats.csv on top.
 */
public final class PlayerStatsOps {

    private PlayerStatsOps() {
    }

    /** The player's id in the game layer and in mob_base_stats.csv. */
    public static final String PLAYER = "minecraft:player";

    /** When an edit applies. */
    public static final String APPLIES = "A change reaches every player at once, and each player as they join or respawn.";

    /** One attribute: the game's base, and the user's own or "". */
    public record Value(String attribute, String game, String own) {
        public String effective() {
            return own.isEmpty() ? game : own;
        }
    }

    /** The player's attributes, the user's over the game's, in sheet order. */
    public static List<Value> values() {
        Map<String, String> own = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobBaseStats.FILE))) {
            if (PLAYER.equals(row.getOrDefault(Schema.MobBaseStats.ENTITY, "").strip())) {
                own.put(row.getOrDefault(Schema.MobBaseStats.ATTRIBUTE, "").strip(),
                        row.getOrDefault(Schema.MobBaseStats.VALUE, "").strip());
            }
        }
        return merge(GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS)), own);
    }

    /** The sheet's player attribute rows with the edits laid over them. */
    public static List<Value> merge(List<GameLayer.Row> sheet, Map<String, String> own) {
        List<Value> out = new ArrayList<>();
        for (GameLayer.Row r : sheet) {
            if (PLAYER.equals(r.entity()) && GameLayer.ATTRIBUTE.equals(r.group())) {
                out.add(new Value(r.key(), r.value(), own.getOrDefault(r.key(), "")));
            }
        }
        return out;
    }

    /** An attribute's name. */
    public static String name(Value v) {
        return AttributeGuide.of(v.attribute()).name();
    }

    /** What an attribute does. */
    public static String what(Value v) {
        return AttributeGuide.of(v.attribute()).what();
    }

    /** Why a typed value cannot be saved, or null. */
    public static String problem(Value v, String typed) {
        String problem = MobStatsOps.valueProblem(typed);
        if (problem != null) {
            return problem;
        }
        return v.attribute().endsWith("max_health") && Double.parseDouble(typed.strip()) <= 0
                ? "Max health must be over 0: a player with none dies" : null;
    }

    public static WriteReport save(Value v, String typed) {
        String problem = problem(v, typed);
        return problem != null ? WriteReport.refused(problem) : MobStatsOps.saveOverride(PLAYER, v.attribute(), typed);
    }

    /** Removes the user's value, so the game's base applies again. */
    public static WriteReport restore(Value v) {
        return MobStatsOps.resetOverride(PLAYER, v.attribute());
    }
}
