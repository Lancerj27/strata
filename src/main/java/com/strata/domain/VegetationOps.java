package com.strata.domain;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.GameSnapshot;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.BiomeConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the flora editor reads and does. */
public final class VegetationOps {

    private VegetationOps() {
    }

    /**
     * One plant feature as a scope sees it.
     *
     * @param inGame for a biome: whether the game places it there; null in a tier
     */
    public record VegetationRow(String feature, boolean hasRule, boolean enabled, boolean inherited,
                           String inheritedFrom, Boolean inGame) {
    }

    /** Every plant feature any biome has in the game, sorted. */
    public static List<String> known() {
        Set<String> all = new java.util.TreeSet<>();
        GameSnapshot.vegetation().values().forEach(all::addAll);
        return List.copyOf(all);
    }

    /** The rows for a scope, from the tables and the game layer. */
    public static List<VegetationRow> rowsFor(String scope, String scopeId) {
        String tier = null;
        if ("biome".equals(scope)) {
            BiomeConfig biome = Config.tables().biomes().get(scopeId);
            tier = biome == null ? null : biome.tier();
        }
        return rowsFor(scope, scopeId, known(), "biome".equals(scope)
                        ? GameSnapshot.vegetation().getOrDefault(scopeId, List.of()) : null,
                CsvTable.read(Paths.table(Schema.Vegetation.FILE)), tier);
    }

    /**
     * The rows for a scope: every known feature, plus any with a rule.
     *
     * @param gameHere the features the game places in this biome, or null in a tier
     * @param biomeTier the biome's tier, or null
     */
    public static List<VegetationRow> rowsFor(String scope, String scopeId, Collection<String> known,
                                         Collection<String> gameHere, List<Map<String, String>> table,
                                         String biomeTier) {
        Map<String, Boolean> own = new LinkedHashMap<>();
        Map<String, Boolean> fromTier = new LinkedHashMap<>();
        for (Map<String, String> row : table) {
            String rowScope = row.getOrDefault(Schema.Vegetation.SCOPE, "").strip();
            String rowId = row.getOrDefault(Schema.Vegetation.SCOPE_ID, "").strip();
            String feature = row.getOrDefault(Schema.Vegetation.FEATURE, "").strip();
            if (feature.isEmpty()) {
                continue;
            }
            boolean on = Parse.flag(row.get(Schema.Vegetation.ENABLED), true);
            if (scope.equals(rowScope) && scopeId.equals(rowId)) {
                own.put(feature, on);
            } else if ("biome".equals(scope) && "tier".equals(rowScope) && rowId.equals(biomeTier)) {
                fromTier.put(feature, on);
            }
        }
        Set<String> all = new java.util.TreeSet<>(known);
        all.addAll(own.keySet());
        all.addAll(fromTier.keySet());
        List<VegetationRow> rows = new ArrayList<>();
        for (String feature : all) {
            Boolean inGame = gameHere == null ? null : gameHere.contains(feature);
            if (own.containsKey(feature)) {
                rows.add(new VegetationRow(feature, true, own.get(feature), false, null, inGame));
            } else if (fromTier.containsKey(feature)) {
                rows.add(new VegetationRow(feature, true, fromTier.get(feature), true, biomeTier, inGame));
            } else {
                rows.add(new VegetationRow(feature, false, true, false, null, inGame));
            }
        }
        return rows;
    }

    /** What Enable, Disable and Default do to the picked rows. */
    public enum VegetationState { ENABLE, DISABLE, DEFAULT }

    /**
     * The picked rows a state change writes: a tier's row is left (it is changed there), and so is one
     * already in that state.
     */
    public static List<VegetationRow> toWrite(List<VegetationRow> picked, VegetationState state) {
        List<VegetationRow> out = new ArrayList<>();
        for (VegetationRow row : picked) {
            boolean already = switch (state) {
                case ENABLE -> row.hasRule() && row.enabled();
                case DISABLE -> row.hasRule() && !row.enabled();
                case DEFAULT -> !row.hasRule();
            };
            if (!row.inherited() && !already) {
                out.add(row);
            }
        }
        return out;
    }

    /** Sets the picked rows to one state, then reloads once. */
    public static WriteReport setState(String scope, String scopeId, List<VegetationRow> picked, VegetationState state) {
        List<VegetationRow> rows = toWrite(picked, state);
        if (rows.isEmpty()) {
            return WriteReport.refused(picked.stream().anyMatch(VegetationRow::inherited)
                    ? "These rules belong to their tier - change them there"
                    : "Nothing to change: the rows are " + state.name().toLowerCase(java.util.Locale.ROOT) + " already");
        }
        WriteReport report = new WriteReport();
        for (VegetationRow row : rows) {
            Map<String, String> keys = Map.of(Schema.Vegetation.SCOPE, scope, Schema.Vegetation.SCOPE_ID, scopeId,
                    Schema.Vegetation.FEATURE, row.feature());
            report.record("the vegetation rule", state == VegetationState.DEFAULT ? TableWriter.delete(Schema.Vegetation.FILE, keys)
                    : TableWriter.upsert(Schema.Vegetation.FILE, keys,
                    Map.of(Schema.Vegetation.ENABLED, String.valueOf(state == VegetationState.ENABLE))));
            if (!report.ok()) {
                return report;
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** The last group: the plants in none of the listed biomes (a rule can still add them). */
    public static final String OTHER_PLANTS = "Other plants";

    /** One biome's section of the list: its dimension, the biome, and its plants, sorted. */
    public record Section(String dimension, String biome, List<String> features) {
    }

    /**
     * THE LIST BY DIMENSION, THEN BIOME: each listed biome's plants, the dimensions in the game's heading
     * order, the biomes and the plants by id; then OTHER_PLANTS, the features in none of them.
     *
     * @param biomes the biomes to list: a tier's, one biome, or empty for every biome
     * @param vegetationByBiome the game's plants per biome (GameSnapshot#vegetation)
     * @param dimensionByBiome biome id to its dimension's heading
     * @param features every feature the list shows (the rows)
     */
    public static List<Section> sections(Collection<String> biomes, Map<String, List<String>> vegetationByBiome,
                                         Map<String, String> dimensionByBiome, java.util.Comparator<String> order,
                                         Collection<String> features) {
        Collection<String> listed = biomes.isEmpty() ? vegetationByBiome.keySet() : biomes;
        Map<String, java.util.TreeMap<String, List<String>>> byDimension = new java.util.TreeMap<>(order);
        Set<String> placed = new java.util.HashSet<>();
        for (String biome : listed) {
            List<String> here = vegetationByBiome.getOrDefault(biome, List.of()).stream()
                    .filter(features::contains).sorted().toList();
            if (here.isEmpty()) {
                continue;
            }
            String dimension = dimensionByBiome.getOrDefault(biome, com.strata.game.DimensionBiomes.UNCLAIMED);
            byDimension.computeIfAbsent(dimension, k -> new java.util.TreeMap<>()).put(biome, here);
            placed.addAll(here);
        }
        List<Section> out = new ArrayList<>();
        byDimension.forEach((dimension, byBiome) -> byBiome.forEach((biome, here) ->
                out.add(new Section(dimension, biome, here))));
        List<String> rest = features.stream().filter(f -> !placed.contains(f)).sorted().toList();
        if (!rest.isEmpty()) {
            out.add(new Section(OTHER_PLANTS, "", rest));
        }
        return out;
    }

    /** The sections for a scope: a tier's biomes (every biome when it has none), a biome's own. */
    public static List<Section> sectionsFor(String scope, String scopeId, Collection<String> features) {
        Collection<String> biomes = "biome".equals(scope) ? List.of(scopeId)
                : "tier".equals(scope) ? TierOps.biomesIn(scopeId) : List.of();
        return sections(biomes, GameSnapshot.vegetation(),
                com.strata.game.DimensionBiomes.byBiome(Config.boundRegistries()),
                com.strata.game.DimensionBiomes.headingOrder(), features);
    }

    /** Each feature's dimensions, as the ore list groups ores (OreOps#dimensionsOf). */
    public static Map<String, List<String>> dimensionsOf(Collection<String> features) {
        return OreOps.dimensionsOf(features, GameSnapshot.vegetation(),
                com.strata.game.DimensionBiomes.byBiome(Config.boundRegistries()),
                com.strata.game.DimensionBiomes.headingOrder());
    }

    /** A row's words: its state, and what the game does where it has no rule. */
    public static String subtitle(VegetationRow row) {
        if (row.inherited()) {
            return (row.enabled() ? "On" : "Off") + "  (from tier " + row.inheritedFrom() + ")";
        }
        if (row.hasRule()) {
            return row.enabled() ? (Boolean.FALSE.equals(row.inGame()) ? "On: added here" : "On")
                    : "Off: removed here";
        }
        if (row.inGame() == null) {
            return "No rule - each biome keeps the game's own";
        }
        return row.inGame() ? "No rule - the game grows it here" : "No rule - the game does not grow it here";
    }
}
