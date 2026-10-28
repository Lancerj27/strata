package com.strata.domain;

import com.strata.data.Config;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.BiomeConfig;
import com.strata.model.Tier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the biome editor reads and writes: a biome's tier assignment and its overrides. */
public final class BiomeOps {

    private BiomeOps() {
    }

    /** What the biome editor shows and saves. */
    public record BiomeForm(String tier, String level, Boolean scaleMobs,
                            Boolean restrictOres, Boolean substituteSpawns,
                            String coefficient, Boolean blockOtherSpawns) {
    }

    /** The editor form for a biome, from the loaded config. */
    public static BiomeForm formFor(String biomeId) {
        BiomeConfig biome = Config.tables().biomes().get(biomeId);
        // Whether a Replace biome blocks other spawns: its own cell.
        Boolean block = null;
        for (Map<String, String> row : com.strata.data.CsvTable.read(
                com.strata.data.Paths.table(Schema.BiomeOverrides.FILE))) {
            if (biomeId != null && biomeId.equals(row.getOrDefault(Schema.BiomeOverrides.BIOME, "").strip())) {
                block = com.strata.runtime.OtherSpawns.cell(row.get(Schema.BiomeOverrides.BLOCK_OTHER_SPAWNS));
            }
        }
        if (biome == null) {
            return new BiomeForm(null, "", null, null, null, "", block);
        }
        return new BiomeForm(biome.tier(),
                biome.level() == null ? "" : String.valueOf(biome.level()),
                biome.scaleMobs(), biome.restrictOres(), biome.substituteSpawns(),
                biome.coefficient() == null ? "" : String.valueOf(biome.coefficient()), block);
    }

    /** The tier a biome is assigned to, or null when it has none. */
    public static String tierOf(String biomeId) {
        BiomeConfig biome = Config.tables().biomes().get(biomeId);
        return biome == null ? null : biome.tier();
    }

    /** The level a tier gives its biomes, or null when the tier does not exist. */
    public static Integer tierLevel(String tierName) {
        Tier tier = tierName == null ? null : Config.tables().tiers().get(tierName);
        return tier == null ? null : tier.level();
    }

    /**
     * How many of the biome's own settings override its tier: the level, the three switches, and the
     * coefficient.
     */
    public static int overrideCount(String biomeId) {
        BiomeConfig biome = Config.tables().biomes().get(biomeId);
        if (biome == null || biome.tier() == null) {
            return 0;
        }
        int n = 0;
        if (biome.level() != null) n++;
        if (biome.scaleMobs() != null) n++;
        if (biome.restrictOres() != null) n++;
        if (biome.substituteSpawns() != null) n++;
        if (biome.coefficient() != null) n++;
        return n;
    }

    /** Why these biome values cannot be saved, or null when they can. */
    public static String valuesProblem(String level, String coefficient) {
        String lv = level == null ? "" : level.strip();
        if (!lv.isEmpty() && !lv.matches("\\d+")) {
            return "The level must be a whole number of 0 or more";
        }
        String co = coefficient == null ? "" : coefficient.strip();
        if (!co.isEmpty() && !co.matches("\\d+(\\.\\d+)?")) {
            return "The coefficient must be a number like 1 or 1.5";
        }
        return null;
    }

    /** Saves a biome from the editor: its tier assignment, then its overrides, then reloads. */
    public static WriteReport save(String biomeId, BiomeForm form) {
        String problem = valuesProblem(form.level(), form.coefficient());
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        BiomeConfig saved = Config.tables().biomes().get(biomeId);
        WriteReport report = assignTier(biomeId, saved == null ? null : saved.tier(), form.tier());
        if (!report.ok()) {
            return report;
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put(Schema.BiomeOverrides.LEVEL, blankIfNull(form.level()));
        overrides.put(Schema.BiomeOverrides.SCALE_MOBS, cell(form.scaleMobs()));
        overrides.put(Schema.BiomeOverrides.RESTRICT_ORES, cell(form.restrictOres()));
        overrides.put(Schema.BiomeOverrides.SUBSTITUTE_SPAWNS, cell(form.substituteSpawns()));
        overrides.put(Schema.BiomeOverrides.COEFFICIENT, blankIfNull(form.coefficient()));
        // Block other spawns is the Rules screen's (area_rules.csv) since not written here.
        boolean anySet = overrides.values().stream().anyMatch(v -> !v.isEmpty());
        if (anySet) {
            report.record("the biome overrides", TableWriter.upsert(Schema.BiomeOverrides.FILE,
                    Map.of(Schema.BiomeOverrides.BIOME, biomeId), overrides));
        } else {
            // FALSE IS NORMAL HERE: most biomes never had an override row.
            report.cascaded("the biome overrides", TableWriter.delete(Schema.BiomeOverrides.FILE,
                    Map.of(Schema.BiomeOverrides.BIOME, biomeId)));
        }
        return Commit.reloadIfOk(report);
    }

    /** A switch as its CSV cell: "true", "false", or "" to inherit. */
    private static String cell(Boolean value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * Points a biome at a tier, or at none.
     *
     * @param previousTier the tier it is assigned to now, or null for none
     * @param targetTier the tier to assign, or null to clear the assignment
     */
    private static WriteReport assignTier(String biomeId, String previousTier, String targetTier) {
        return assignTier(new WriteReport(), biomeId, previousTier, targetTier);
    }

    /** As above, recording into a report that may hold earlier writes. */
    private static WriteReport assignTier(WriteReport report, String biomeId, String previousTier, String targetTier) {
        if (targetTier == null) {
            if (previousTier != null) {
                report.record("the tier assignment", TableWriter.delete(Schema.TierBiomes.FILE,
                        Map.of(Schema.TierBiomes.TIER, previousTier,
                                Schema.TierBiomes.BIOME, biomeId)));
            }
            return report;
        }
        if (previousTier != null && !previousTier.equals(targetTier)) {
            report.record("the old tier assignment", TableWriter.delete(Schema.TierBiomes.FILE,
                    Map.of(Schema.TierBiomes.TIER, previousTier,
                            Schema.TierBiomes.BIOME, biomeId)));
            if (!report.ok()) return report;
        }
        if (previousTier == null || !previousTier.equals(targetTier)) {
            report.record("the tier assignment", TableWriter.upsert(Schema.TierBiomes.FILE,
                    Map.of(Schema.TierBiomes.TIER, targetTier,
                            Schema.TierBiomes.BIOME, biomeId),
                    Map.of()));
        }
        return report;
    }

    // ---- ASSIGN MANY ----

    /**
     * What assigning biomes to a tier does, before it is done.
     *
     * @param add biomes with no tier now
     * @param move biome to the tier it leaves, for biomes in another tier
     * @param already biomes in this tier now: nothing to do
     */
    public record AssignPlan(String tier, List<String> add, Map<String, String> move, List<String> already) {

        /** How many biomes change. */
        public int changes() {
            return add.size() + move.size();
        }
    }

    /** The plan for assigning these biomes to a tier. */
    public static AssignPlan assignPlan(String tier, List<String> biomes,
                                        java.util.function.Function<String, String> tierOf) {
        List<String> add = new ArrayList<>();
        Map<String, String> move = new LinkedHashMap<>();
        List<String> already = new ArrayList<>();
        for (String biome : new java.util.LinkedHashSet<>(biomes)) {
            String now = tierOf.apply(biome);
            if (now == null) {
                add.add(biome);
            } else if (now.equals(tier)) {
                already.add(biome);
            } else {
                move.put(biome, now);
            }
        }
        return new AssignPlan(tier, List.copyOf(add), java.util.Collections.unmodifiableMap(move), List.copyOf(already));
    }

    /** The plan from the loaded config. */
    public static AssignPlan assignPlan(String tier, List<String> biomes) {
        return assignPlan(tier, biomes, BiomeOps::tierOf);
    }

    /**
     * Assigns the biomes to a tier, as the plan says: each move deletes the old link first, as the biome
     * editor does.
     */
    public static WriteReport assignAll(String tier, List<String> biomes) {
        if (tier == null || tier.isBlank() || !Config.tables().tiers().containsKey(tier)) {
            return WriteReport.refused("Pick a tier that exists");
        }
        AssignPlan plan = assignPlan(tier, biomes);
        if (plan.changes() == 0) {
            return WriteReport.refused("Every biome shown is in " + tier + " already");
        }
        WriteReport report = new WriteReport();
        for (String biome : plan.add()) {
            assignTier(report, biome, null, tier);
            if (!report.ok()) return report;
        }
        for (var e : plan.move().entrySet()) {
            assignTier(report, e.getKey(), e.getValue(), tier);
            if (!report.ok()) return report;
        }
        return Commit.reloadIfOk(report);
    }
}