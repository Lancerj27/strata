package com.strata.domain;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.OverrideMode;
import com.strata.model.Tier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the editor can DO to a tier, and everything it READS about one. */
public final class TierOps {

    private TierOps() {
    }

    /**
     * What the tier editor shows and saves, as typed.
     *
     * @param blockOtherSpawns one of {@link #OTHER_SPAWN_CHOICES}
     */
    public record TierForm(String name, String level, String spawnMode, String coefficient, String blockOtherSpawns) {
    }

    /**
     * Whether a Replace tier blocks the spawns not from its list (a structure's, a patrol's, a new chunk's):
     * the global option, on, or off.
     */
    public static final List<String> OTHER_SPAWN_CHOICES = List.of("default", "on", "off");

    /** What the setting does, for its tooltip. */
    public static final String OTHER_SPAWNS_HELP = "For a Replace tier: on, only mobs on its list spawn, so no structure"
            + " mobs (fortress, village, hut), patrols, or new-chunk mobs. Off lets those spawn. Default follows"
            + " Options > Replace blocks other spawns. A biome can set its own.";

    /** The starting form for a new tier. */
    public static final TierForm NEW_TIER = new TierForm("", "1", OverrideMode.ADD.lower(), "1.0", "default");

    /** A stored cell as the dropdown's word. */
    public static String otherSpawnsWord(String cell) {
        Boolean v = com.strata.runtime.OtherSpawns.cell(cell);
        return v == null ? "default" : v ? "on" : "off";
    }

    /** A dropdown word as the stored cell: "true", "false", or "". */
    public static String otherSpawnsCell(String word) {
        Boolean v = com.strata.runtime.OtherSpawns.cell(word);
        return v == null ? "" : String.valueOf(v);
    }

    /** One row of the tier list, and the counts the tier editor footer shows. */
    public record TierSummary(String name, int level, String spawnMode, double coefficient,
                              long biomes, int spawnRules, int oreRules) {
    }

    /**
     * How many rows of this tier the tables hold.
     *
     * @param edits the tier's mob edits: its variants, equipment, abilities, stats, AI lists, and mob data
     */
    public record TierImpact(long biomes, long mobs, long ores, long spawnRules, long loot, long edits) {
        public long total() {
            return biomes + mobs + ores + spawnRules + loot + edits;
        }
    }

    /** The tables that hold a mob instance's edits in a scope. */
    public static final List<String> INSTANCE_TABLES = java.util.stream.Stream.concat(java.util.stream.Stream.of(
            Schema.Variants.FILE, Schema.Equipment.FILE,
            Schema.Abilities.FILE, Schema.VariantStats.FILE, Schema.MobGoals.FILE, Schema.MobGoalSets.FILE,
            Schema.MobData.FILE, Schema.AreaRules.FILE, Schema.Replacements.FILE, Schema.BlockReplacements.FILE,
            Schema.SpecialSpawns.FILE, Schema.Trades.FILE),
            Schema.MOB_PROPERTY_FILES.values().stream().sorted()).toList();

    /** The spawn modes a tier offers: add to the game's spawns, or replace them. */
    public static final List<String> SPAWN_MODES = List.of(OverrideMode.ADD.lower(), OverrideMode.REPLACE.lower());

    /** The editor form for an existing tier, or {@link #NEW_TIER} when there is none. */
    public static TierForm formFor(String name) {
        Tier tier = name == null ? null : Config.tables().tiers().get(name);
        if (tier == null) {
            return NEW_TIER;
        }
        String block = "";
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Tiers.FILE))) {
            if (tier.name().equals(row.getOrDefault(Schema.Tiers.NAME, "").strip())) {
                block = row.getOrDefault(Schema.Tiers.BLOCK_OTHER_SPAWNS, "");
            }
        }
        return new TierForm(tier.name(), String.valueOf(tier.level()),
                tier.spawnMode().lower(), String.valueOf(tier.coefficient()), otherSpawnsWord(block));
    }

    /** Every tier name, sorted. */
    public static List<String> names() {
        List<String> names = new ArrayList<>(Config.tables().tiers().keySet());
        names.sort(String::compareTo);
        return names;
    }

    /** One summary per tier, sorted by name. */
    public static List<TierSummary> summaries() {
        List<TierSummary> out = new ArrayList<>();
        for (String name : names()) {
            TierSummary summary = summary(name);
            if (summary != null) {
                out.add(summary);
            }
        }
        return out;
    }

    /** The summary for one tier, or null when it does not exist. */
    public static TierSummary summary(String name) {
        Tier tier = name == null ? null : Config.tables().tiers().get(name);
        if (tier == null) {
            return null;
        }
        int spawns = tier.mobs().values().stream().mapToInt(List::size).sum();
        return new TierSummary(tier.name(), tier.level(), tier.spawnMode().lower(),
                tier.coefficient(), biomesUsing(name), spawns, tier.ores().size());
    }

    /** The biomes assigned to this tier, sorted by id. */
    public static List<String> biomesIn(String name) {
        return Config.tables().biomes().values().stream()
                .filter(b -> name != null && name.equals(b.tier()))
                .map(com.strata.model.BiomeConfig::biome).sorted().toList();
    }

    /** How many biomes are assigned to this tier. */
    public static long biomesUsing(String name) {
        return Config.tables().biomes().values().stream()
                .filter(b -> name != null && name.equals(b.tier())).count();
    }

    /** Why a tier name cannot be used, or null when it can. */
    public static String nameProblem(String name, String originalName, Collection<String> existing) {
        String t = name == null ? "" : name.strip();
        if (t.isEmpty()) {
            return "A tier needs a name";
        }
        if (t.equals(originalName)) {
            return null;
        }
        return existing.contains(t) ? "Another tier already has this name" : null;
    }

    /** Why these tier values cannot be saved, or null when they can. */
    public static String valuesProblem(String level, String coefficient) {
        String lv = level == null ? "" : level.strip();
        if (!lv.isEmpty()) {
            try {
                Integer.parseInt(lv);
            } catch (NumberFormatException e) {
                return "The level must be a whole number";
            }
        }
        String co = coefficient == null ? "" : coefficient.strip();
        if (!co.isEmpty()) {
            try {
                if (!Double.isFinite(Double.parseDouble(co))) {
                    return "The coefficient must be a finite number";
                }
            } catch (NumberFormatException e) {
                return "The coefficient must be a number";
            }
        }
        return null;
    }

    /** Whether saving this name renames an existing tier. */
    public static boolean isRename(String originalName, String newName) {
        String original = originalName == null ? "" : originalName;
        return !original.isEmpty() && newName != null && !original.equals(newName.strip());
    }

    /**
     * Saves a tier from the editor: a new tier, an edit, or a RENAME when the name changed.
     *
     * @param originalName the tier the editor opened, or "" for a new tier
     */
    public static WriteReport save(String originalName, TierForm form) {
        String original = originalName == null ? "" : originalName;
        String problem = nameProblem(form.name(), original, Config.tables().tiers().keySet());
        if (problem == null) {
            problem = valuesProblem(form.level(), form.coefficient());
        }
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String name = form.name().strip();
        String level = form.level() == null || form.level().isBlank() ? "0" : form.level().strip();
        String coeff = form.coefficient() == null || form.coefficient().isBlank()
                ? "1.0" : form.coefficient().strip();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Tiers.LEVEL, level);
        values.put(Schema.Tiers.SPAWN_MODE, OverrideMode.parse(form.spawnMode()).lower());
        values.put(Schema.Tiers.COEFFICIENT, coeff);
        // Blank or 0 is off: the level alone.

        return Commit.reloadIfOk(isRename(original, name)
                ? rename(original, name, values) : write(name, values));
    }

    /** Writes a tier row under its own name. */
    private static WriteReport write(String name, Map<String, String> values) {
        return new WriteReport().record("the tier",
                TableWriter.upsert(Schema.Tiers.FILE, Map.of(Schema.Tiers.NAME, name), values));
    }

    /** Renames a tier and carries every pointer at it. */
    private static WriteReport rename(String oldName, String newName, Map<String, String> values) {
        WriteReport report = new WriteReport();
        report.record("the renamed tier", TableWriter.upsert(Schema.Tiers.FILE,
                Map.of(Schema.Tiers.NAME, newName), values));
        if (!report.ok()) return report;

        report.cascaded("the biome links", TableWriter.updateColumn(Schema.TierBiomes.FILE,
                Schema.TierBiomes.TIER, oldName, Schema.TierBiomes.TIER, newName));
        report.cascaded("the mob spawns", TableWriter.updateColumnIf(Schema.MobSpawns.FILE,
                Schema.MobSpawns.SCOPE_ID, oldName, Schema.MobSpawns.SCOPE_ID, newName,
                Schema.MobSpawns.SCOPE, "tier"));
        report.cascaded("the ore rules", TableWriter.updateColumnIf(Schema.Ores.FILE,
                Schema.Ores.SCOPE_ID, oldName, Schema.Ores.SCOPE_ID, newName,
                Schema.Ores.SCOPE, "tier"));
        report.cascaded("the vegetation rules", TableWriter.updateColumnIf(Schema.Vegetation.FILE,
                Schema.Vegetation.SCOPE_ID, oldName, Schema.Vegetation.SCOPE_ID, newName,
                Schema.Vegetation.SCOPE, "tier"));
        report.cascaded("the spawn rules", TableWriter.updateColumnIf(Schema.SpawnRules.FILE,
                Schema.SpawnRules.SCOPE_ID, oldName,
                Schema.SpawnRules.SCOPE_ID, newName,
                Schema.SpawnRules.SCOPE, "tier"));
        report.cascaded("the loot", TableWriter.updateColumnIf(Schema.Loot.FILE,
                Schema.Loot.SCOPE_ID, oldName,
                Schema.Loot.SCOPE_ID, newName,
                Schema.Loot.SCOPE, "tier"));
        for (String file : INSTANCE_TABLES) {
            report.cascaded("the tier's mob edits", TableWriter.updateColumnIf(file,
                    Schema.MobSpawns.SCOPE_ID, oldName, Schema.MobSpawns.SCOPE_ID, newName,
                    Schema.MobSpawns.SCOPE, "tier"));
        }

        // This one MUST land.
        return report.record("the old tier name", TableWriter.delete(Schema.Tiers.FILE,
                Map.of(Schema.Tiers.NAME, oldName)));
    }

    /**
     * The first name a copy of this tier can take: the source's name with "_copy" on the end, then "_copy_2",
     * then "_copy_3", and on for as long as the name is taken.
     */
    public static String copyName(String sourceName, Collection<String> existing) {
        String source = sourceName == null ? "" : sourceName.strip();
        String candidate = source + "_copy";
        for (int n = 2; existing.contains(candidate); n++) {
            candidate = source + "_copy_" + n;
        }
        return candidate;
    }

    /** Copies a saved tier to a new tier under a new name. */
    public static WriteReport duplicate(String sourceName, String newName) {
        Tier source = sourceName == null ? null : Config.tables().tiers().get(sourceName);
        if (source == null) {
            return WriteReport.refused("There is no saved tier called " + sourceName);
        }
        String problem = nameProblem(newName, "", Config.tables().tiers().keySet());
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String name = newName.strip();
        TierForm form = formFor(sourceName);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Tiers.LEVEL, form.level());
        values.put(Schema.Tiers.SPAWN_MODE, OverrideMode.parse(form.spawnMode()).lower());
        values.put(Schema.Tiers.COEFFICIENT, form.coefficient());

        WriteReport report = new WriteReport();
        report.record("the new tier", TableWriter.upsert(Schema.Tiers.FILE,
                Map.of(Schema.Tiers.NAME, name), values));
        if (!report.ok()) {
            return report;
        }
        report.cascaded("the biome links", copyTierRows(Schema.TierBiomes.FILE,
                Schema.TierBiomes.TIER, sourceName, name, null));
        report.cascaded("the mob spawns", copyTierRows(Schema.MobSpawns.FILE,
                Schema.MobSpawns.SCOPE_ID, sourceName, name, "tier"));
        report.cascaded("the ore rules", copyTierRows(Schema.Ores.FILE,
                Schema.Ores.SCOPE_ID, sourceName, name, "tier"));
        report.cascaded("the vegetation rules", copyTierRows(Schema.Vegetation.FILE,
                Schema.Vegetation.SCOPE_ID, sourceName, name, "tier"));
        report.cascaded("the spawn rules", copyTierRows(Schema.SpawnRules.FILE,
                Schema.SpawnRules.SCOPE_ID, sourceName, name, "tier"));
        report.cascaded("the loot", copyTierRows(Schema.Loot.FILE,
                Schema.Loot.SCOPE_ID, sourceName, name, "tier"));
        for (String file : INSTANCE_TABLES) {
            report.cascaded("the tier's mob edits", copyTierRows(file,
                    Schema.MobSpawns.SCOPE_ID, sourceName, name, "tier"));
        }
        return Commit.reloadIfOk(report);
    }

    /**
     * Copies every row of one file that belongs to a tier, under a new tier name.
     *
     * @param scope the scope a row must carry to be copied, or null when the table has no scope column
     */
    private static boolean copyTierRows(String file, String idColumn, String oldName, String newName, String scope) {
        List<Map<String, String>> copies = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(file))) {
            if (!oldName.equals(row.getOrDefault(idColumn, "").strip())) {
                continue;
            }
            if (scope != null && !scope.equals(row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip())) {
                continue;
            }
            Map<String, String> copy = new LinkedHashMap<>(row);
            copy.put(idColumn, newName);
            copies.add(copy);
        }
        return copies.isEmpty() || TableWriter.appendRows(file, copies);
    }

    /** Deletes a tier and everything pointing at it, then reloads. */
    public static WriteReport delete(String name) {
        WriteReport report = new WriteReport();
        report.cascaded("the biome links", TableWriter.delete(Schema.TierBiomes.FILE,
                Map.of(Schema.TierBiomes.TIER, name)));
        report.cascaded("the mob spawns", TableWriter.delete(Schema.MobSpawns.FILE,
                Map.of(Schema.MobSpawns.SCOPE, "tier", Schema.MobSpawns.SCOPE_ID, name)));
        report.cascaded("the ore rules", TableWriter.delete(Schema.Ores.FILE,
                Map.of(Schema.Ores.SCOPE, "tier", Schema.Ores.SCOPE_ID, name)));
        report.cascaded("the vegetation rules", TableWriter.delete(Schema.Vegetation.FILE,
                Map.of(Schema.Vegetation.SCOPE, "tier", Schema.Vegetation.SCOPE_ID, name)));
        report.cascaded("the spawn rules", TableWriter.delete(Schema.SpawnRules.FILE,
                Map.of(Schema.SpawnRules.SCOPE, "tier", Schema.SpawnRules.SCOPE_ID, name)));
        report.cascaded("the loot", TableWriter.delete(Schema.Loot.FILE,
                Map.of(Schema.Loot.SCOPE, "tier", Schema.Loot.SCOPE_ID, name)));
        for (String file : INSTANCE_TABLES) {
            report.cascaded("the tier's mob edits", TableWriter.delete(file,
                    Map.of(Schema.MobSpawns.SCOPE, "tier", Schema.MobSpawns.SCOPE_ID, name)));
        }
        // The tier itself MUST go.
        return Commit.reloadIfOk(report.record("the tier", TableWriter.delete(Schema.Tiers.FILE,
                Map.of(Schema.Tiers.NAME, name))));
    }

    /** The rows of this tier that a rename would update, or a delete would remove, for the confirmation. */
    public static TierImpact impact(String name) {
        long biomes = CsvTable.read(Paths.table(Schema.TierBiomes.FILE)).stream()
                .filter(r -> name.equals(r.getOrDefault(Schema.TierBiomes.TIER, "").strip()))
                .count();
        return new TierImpact(biomes,
                countScopedRows(Schema.MobSpawns.FILE, name),
                // The ore and vegetation rules count together: both are the tier's world rules.
                countScopedRows(Schema.Ores.FILE, name) + countScopedRows(Schema.Vegetation.FILE, name),
                countScopedRows(Schema.SpawnRules.FILE, name),
                countScopedRows(Schema.Loot.FILE, name),
                INSTANCE_TABLES.stream().mapToLong(file -> countScopedRows(file, name)).sum());
    }

    /** How many rows in a scope-keyed table belong to this tier. */
    private static long countScopedRows(String fileName, String tierName) {
        return CsvTable.read(Paths.table(fileName)).stream()
                .filter(r -> "tier".equals(r.getOrDefault(Schema.MobSpawns.SCOPE, "").strip()))
                .filter(r -> tierName.equals(r.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip()))
                .count();
    }
}
