package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Schema;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The user's biome values, as BiomeValuesModifier puts them on the biomes: biome_data.csv read at every
 * reload, and the rule for a spawn cost.
 */
public final class BiomeValues {

    private BiomeValues() {
    }

    public static final String BIOME = "biome";
    public static final String COST = "cost";
    public static final String PROBABILITY = "creature_probability";
    public static final String CHARGE = "charge";
    public static final String BUDGET = "energy_budget";

    /** biome id to (group|key to value). */
    private static volatile Map<String, Map<String, String>> edits = Map.of();

    public static Map<String, Map<String, String>> edits() {
        return edits;
    }

    public static void load(Path csv) {
        Map<String, Map<String, String>> out = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(csv)) {
            String biome = row.getOrDefault(Schema.BiomeData.BIOME, "").strip();
            String value = row.getOrDefault(Schema.BiomeData.VALUE, "").strip();
            if (!biome.isEmpty() && !value.isEmpty()) {
                out.computeIfAbsent(biome, k -> new LinkedHashMap<>()).put(
                        row.getOrDefault(Schema.BiomeData.GROUP, "").strip() + "|"
                                + row.getOrDefault(Schema.BiomeData.KEY, "").strip(), value);
            }
        }
        edits = Map.copyOf(out);
        Strata.LOGGER.info("Biome values: {} biome(s) with values of your own", out.size());
    }

    /** A spawn cost's charge and energy budget: the user's where set, else the game's. */
    public static double[] cost(Map<String, String> biomeEdits, String entity, Double gameCharge, Double gameBudget) {
        Double charge = number(biomeEdits.get(COST + "|" + entity + "|" + CHARGE), gameCharge);
        Double budget = number(biomeEdits.get(COST + "|" + entity + "|" + BUDGET), gameBudget);
        return charge == null || budget == null ? null : new double[]{charge, budget};
    }

    /** A written number 0 or more, else the fallback. */
    public static Double number(String text, Double fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            double v = Double.parseDouble(text);
            return Double.isFinite(v) && v >= 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}