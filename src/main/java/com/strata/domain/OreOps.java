package com.strata.domain;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.BiomeConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the ore editor reads and does for a scope's ore rules. */
public final class OreOps {

    private OreOps() {
    }

    /**
     * One ore as a scope sees it: its own rule, a rule inherited from the biome's tier, or no rule (the
     * default: enabled at 100%).
     *
     * @param inGame for a row with no rule: whether the game itself places this ore in the biome, or null in
     *     a tier view, where each biome keeps its own
     */
    public record OreRow(String oreId, boolean hasRule, boolean enabled, int frequencyPct,
                         boolean inherited, String inheritedFrom, Boolean inGame) {

        /** The value the row's chance cell shows. */
        public int chanceCell() {
            return chanceCellValue(hasRule, frequencyPct);
        }
    }

    /**
     * Every ore a scope can rule on, sorted by id: the known ores, plus any ore that already has a rule.
     *
     * @param knownOreIds the ores the game knows (RegistryQuery#ores), passed in so this class needs no
     *     registry
     * @param gameOres the ores the game itself places in this biome (GameData#gameOresIn), or null in a tier
     *     view
     */
    public static List<OreRow> rowsFor(String scope, String scopeId, Collection<String> knownOreIds,
                                       Collection<String> gameOres) {
        Map<String, int[]> direct = new LinkedHashMap<>();
        Map<String, int[]> inherited = new LinkedHashMap<>();

        String inheritedTier = null;
        if ("biome".equals(scope)) {
            BiomeConfig biome = Config.tables().biomes().get(scopeId);
            if (biome != null) inheritedTier = biome.tier();
        }

        for (Map<String, String> csv : CsvTable.read(Paths.table(Schema.Ores.FILE))) {
            String rowScope = csv.getOrDefault(Schema.Ores.SCOPE, "").strip();
            String rowScopeId = csv.getOrDefault(Schema.Ores.SCOPE_ID, "").strip();
            String oreId = csv.getOrDefault(Schema.Ores.ORE_ID, "").strip();
            if (oreId.isEmpty()) continue;
            // Same helpers the loader uses for these two columns.
            int[] rule = {Parse.flag(csv.get(Schema.Ores.ENABLED), true) ? 1 : 0,
                    Parse.integer(csv.get(Schema.Ores.FREQUENCY_PCT), 100)};
            if (scope.equals(rowScope) && scopeId.equals(rowScopeId)) {
                direct.put(oreId, rule);
            } else if ("biome".equals(scope) && "tier".equals(rowScope)
                    && inheritedTier != null && inheritedTier.equals(rowScopeId)) {
                inherited.put(oreId, rule);
            }
        }

        Set<String> all = new LinkedHashSet<>(knownOreIds);
        all.addAll(direct.keySet());
        all.addAll(inherited.keySet());

        List<OreRow> rows = new ArrayList<>();
        for (String oreId : all.stream().sorted().toList()) {
            int[] own = direct.get(oreId);
            int[] tier = inherited.get(oreId);
            // A row with no rule keeps what the game does there.
            Boolean inGame = gameOres == null ? null : gameOres.contains(oreId);
            if (own != null) {
                rows.add(new OreRow(oreId, true, own[0] == 1, own[1], false, null, inGame));
            } else if (tier != null) {
                rows.add(new OreRow(oreId, true, tier[0] == 1, tier[1], true, inheritedTier, inGame));
            } else {
                rows.add(new OreRow(oreId, false, true, 100, false, null, inGame));
            }
        }
        return rows;
    }

    // ---- BY DIMENSION.

    /**
     * The dimensions each ore generates in: every dimension with a biome whose game ores hold it, in heading
     * order.
     *
     * @param oresByBiome biome id to the game's ores there
     * @param dimensionByBiome biome id to its dimension's heading
     */
    public static Map<String, List<String>> dimensionsOf(Collection<String> ores,
                                                        Map<String, List<String>> oresByBiome,
                                                        Map<String, String> dimensionByBiome,
                                                        java.util.Comparator<String> order) {
        Map<String, Set<String>> found = new LinkedHashMap<>();
        oresByBiome.forEach((biome, here) -> {
            String dimension = dimensionByBiome.getOrDefault(biome, com.strata.game.DimensionBiomes.UNCLAIMED);
            for (String ore : here) {
                found.computeIfAbsent(ore, k -> new LinkedHashSet<>()).add(dimension);
            }
        });
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String ore : ores) {
            Set<String> dims = found.get(ore);
            out.put(ore, dims == null || dims.isEmpty() ? List.of(com.strata.game.DimensionBiomes.UNCLAIMED)
                    : dims.stream().sorted(order).toList());
        }
        return out;
    }

    /** {@link #dimensionsOf} from the game layer and the bound registries. */
    public static Map<String, List<String>> dimensionsOf(Collection<String> ores) {
        Map<String, List<String>> oresByBiome = new LinkedHashMap<>();
        com.strata.data.GameSnapshot.get().forEach((biome, entry) -> oresByBiome.put(biome, entry.ores()));
        return dimensionsOf(ores, oresByBiome, com.strata.game.DimensionBiomes.byBiome(Config.boundRegistries()),
                com.strata.game.DimensionBiomes.headingOrder());
    }

    /** A biome's dimension heading, or "Other" when no dimension claims it. */
    public static String dimensionOfBiome(String biomeId) {
        return com.strata.game.DimensionBiomes.byBiome(Config.boundRegistries())
                .getOrDefault(biomeId, com.strata.game.DimensionBiomes.UNCLAIMED);
    }

    /**
     * The dimension headings a scope's list shows, in order.
     *
     * @param biomeDimension the biome's dimension, or null for a tier
     */
    public static List<String> headings(Map<String, List<String>> dimensionsByOre, String biomeDimension) {
        return headings(dimensionsByOre, biomeDimension, com.strata.game.DimensionBiomes.headingOrder());
    }

    /** As above, in the given order. */
    public static List<String> headings(Map<String, List<String>> dimensionsByOre, String biomeDimension,
                                        java.util.Comparator<String> order) {
        if (biomeDimension != null) {
            return List.of(biomeDimension);
        }
        Set<String> all = new LinkedHashSet<>();
        dimensionsByOre.values().forEach(all::addAll);
        return all.stream().sorted(order).toList();
    }

    /** The value shown in a row's inline chance cell. */
    public static int chanceCellValue(boolean hasRule, int frequencyPct) {
        return hasRule ? frequencyPct : 100;
    }

    /** Commits a typed percentage to one ore row, clamped to 0..100, then reloads. */
    public static WriteReport setChance(String scope, String scopeId, OreRow row, int pct) {
        if (row.inherited()) {
            return WriteReport.refused("This rule belongs to tier " + row.inheritedFrom()
                    + " - change it there");
        }
        int clamped = Math.max(0, Math.min(100, pct));
        boolean enabled = !row.hasRule() || row.enabled();
        return Commit.reloadIfOk(saveRule(scope, scopeId, row.oreId(), enabled,
                String.valueOf(clamped)));
    }

    /** What Enable, Disable and Default do to the picked rows. */
    public enum OreState { ENABLE, DISABLE, DEFAULT }

    /**
     * The picked rows a state change writes, and the ones it leaves: a row that belongs to a tier is left (it
     * is changed there), and so is one already in that state.
     *
     * @return the rows to write (true) and the tier's rows it leaves (false)
     */
    public static Map<Boolean, List<OreRow>> statePlan(List<OreRow> picked, OreState state) {
        Map<Boolean, List<OreRow>> out = new LinkedHashMap<>();
        out.put(true, new ArrayList<>());
        out.put(false, new ArrayList<>());
        for (OreRow row : picked) {
            if (row.inherited()) {
                out.get(false).add(row);
                continue;
            }
            boolean already = switch (state) {
                case ENABLE -> row.hasRule() && row.enabled();
                case DISABLE -> row.hasRule() && !row.enabled();
                case DEFAULT -> !row.hasRule();
            };
            if (!already) {
                out.get(true).add(row);
            }
        }
        return out;
    }

    /** Sets the picked rows to one state. */
    public static WriteReport setState(String scope, String scopeId, List<OreRow> picked, OreState state) {
        Map<Boolean, List<OreRow>> plan = statePlan(picked, state);
        if (plan.get(true).isEmpty()) {
            return WriteReport.refused(plan.get(false).isEmpty() ? "Nothing to change: the rows are "
                    + state.name().toLowerCase(java.util.Locale.ROOT) + " already"
                    : "These rules belong to their tier - change them there");
        }
        WriteReport report = new WriteReport();
        for (OreRow row : plan.get(true)) {
            WriteReport one = switch (state) {
                case ENABLE -> saveRule(scope, scopeId, row.oreId(), true, String.valueOf(row.chanceCell()));
                case DISABLE -> saveRule(scope, scopeId, row.oreId(), false, String.valueOf(row.frequencyPct()));
                case DEFAULT -> new WriteReport().record("the ore rule",
                        TableWriter.delete(Schema.Ores.FILE, ruleKeys(scope, scopeId, row.oreId())));
            };
            for (String failure : one.failures()) {
                report.record(failure, false);
            }
            if (!report.ok()) {
                return report;
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** The key of one ore's rule inside a scope. */
    private static Map<String, String> ruleKeys(String scope, String scopeId, String oreId) {
        return Map.of(Schema.Ores.SCOPE, scope,
                Schema.Ores.SCOPE_ID, scopeId,
                Schema.Ores.ORE_ID, oreId);
    }

    /** Writes an ore rule with its enabled flag and density. */
    private static WriteReport saveRule(String scope, String scopeId, String oreId,
                                        boolean enabled, String frequencyPct) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Ores.ENABLED, String.valueOf(enabled));
        values.put(Schema.Ores.FREQUENCY_PCT, frequencyPct);
        return new WriteReport().record("the ore rule",
                TableWriter.upsert(Schema.Ores.FILE, ruleKeys(scope, scopeId, oreId), values));
    }

    /** The blocks an ore places, as block ids, stone first. */
    public static List<String> blocksOf(String ore, Map<net.minecraft.resources.ResourceLocation,
            com.strata.game.OreDetector.OreBlock> blocks) {
        List<String> out = new ArrayList<>();
        blocks.forEach((id, block) -> {
            if (block.ore().equals(ore)) {
                out.add(id.toString());
            }
        });
        // Stone first, then deepslate, then the rest by id.
        out.sort(java.util.Comparator.comparing((String id) -> id.contains(":deepslate_"))
                .thenComparing(java.util.Comparator.naturalOrder()));
        return out;
    }

    /** The blocks an ore places, from what the game gave at start. */
    public static List<String> blocksOf(String ore) {
        return blocksOf(ore, com.strata.game.OreDetector.oreBlocks());
    }
}