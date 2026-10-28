package com.strata.runtime;

import com.strata.data.Paths;
import com.strata.data.Schema;

import java.util.List;

/** The two scaling tables, held where the things that apply them can see them. */
public final class Scaling {

    private Scaling() {}

    private static volatile List<AttributeCurve> mobs = List.of();
    private static volatile List<AttributeCurve> players = List.of();

    public static void reload() {
        mobs = AttributeCurve.load(Paths.table(Schema.Scaling.MOBS_FILE));
        players = AttributeCurve.load(Paths.table(Schema.Scaling.PLAYER_FILE));
        java.util.Map<String, String> stored = new java.util.LinkedHashMap<>();
        for (java.util.Map<String, String> row : com.strata.data.CsvTable.read(Paths.table(Schema.ScalingFormulas.FILE))) {
            stored.put(row.getOrDefault(Schema.ScalingFormulas.FORMULA, ""), row.getOrDefault(Schema.ScalingFormulas.EXPRESSION, ""));
        }
        com.strata.model.ScalingFormulas.set(stored)
                .forEach(line -> com.strata.Strata.LOGGER.warn("{}: {}", Schema.ScalingFormulas.FILE, line));
    }

    public static List<AttributeCurve> mobs() {
        return mobs;
    }

    /** The player curves. */
    public static List<AttributeCurve> players() {
        return players;
    }
}
