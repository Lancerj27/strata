package com.strata.data;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Headless-safe configuration validator. */
public final class ConfigValidator {

    private ConfigValidator() {}

    public record Finding(String category, String message) {}

    private static final Set<String> REGISTRY_KEYS = Set.of(
            // "goal_set", the editor's name (it was "behavior", before that "preset").
            "entity", "item", "attribute", "biome", "structure", "ore", "goal_set");

    public static Set<String> knownRegistryKeys() {
        return REGISTRY_KEYS;
    }

    public static List<Finding> validate(Tables.Loaded loaded, Path root) {
        List<Finding> findings = new ArrayList<>();
        checkInvalidTiers(loaded, root, findings);
        checkUnknownGoalSets(root, findings);
        checkOrphanedVariants(loaded, root, findings);
        return List.copyOf(findings);
    }

    private static void checkInvalidTiers(Tables.Loaded loaded, Path root, List<Finding> findings) {
        Set<String> knownTiers = loaded.tiers().keySet();
        Path tierBiomes = root.resolve(Schema.TierBiomes.FILE);
        for (Map<String, String> row : CsvTable.read(tierBiomes)) {
            String tier = row.getOrDefault(Schema.TierBiomes.TIER, "").strip();
            if (tier.isEmpty()) continue;
            if (!knownTiers.contains(tier)) {
                String biome = row.getOrDefault(Schema.TierBiomes.BIOME, "").strip();
                findings.add(new Finding("invalid-tier",
                        "tier_biomes.csv: biome '" + biome + "' references unknown tier '" + tier + "'"));
            }
        }
    }

    private static void checkUnknownGoalSets(Path root, List<Finding> findings) {
        // The goal sets table in this folder.
        Set<String> knownGoalSets = GoalSetTable.load(root.resolve(Schema.GoalSets.FILE)).keySet();
        Set<String> reported = new LinkedHashSet<>();
        for (Map<String, String> row : CsvTable.read(root.resolve(Schema.MobGoalSets.FILE))) {
            String goalSet = row.getOrDefault(Schema.MobGoalSets.GOAL_SET, "").strip();
            // "none" is the game's own goals, not a goal set.
            if (goalSet.isEmpty() || com.strata.model.GoalSets.NONE.equals(goalSet)) continue;
            if (!knownGoalSets.contains(goalSet) && reported.add(goalSet)) {
                String variant = row.getOrDefault(Schema.MobGoalSets.VARIANT_NAME, "").strip();
                findings.add(new Finding("unknown-goal-set",
                        "mob_goal_sets.csv: variant '" + variant + "' names unknown goal set '" + goalSet + "'"));
            }
        }
    }

    private static void checkOrphanedVariants(Tables.Loaded loaded, Path root, List<Finding> findings) {
        Set<String> knownVariants = loaded.variants().keySet();
        Set<String> orphaned = new TreeSet<>();

        collectVariantRefs(root.resolve(Schema.VariantStats.FILE), Schema.VariantStats.VARIANT_NAME, knownVariants, orphaned);
        collectVariantRefs(root.resolve(Schema.Equipment.FILE), Schema.Equipment.VARIANT_NAME, knownVariants, orphaned);
        collectSpawnVariantRefs(root.resolve(Schema.MobSpawns.FILE), knownVariants, orphaned);

        for (String name : orphaned) {
            findings.add(new Finding("orphaned-variant",
                    "variant '" + name + "' referenced in CSV but not defined in variants.csv"));
        }
    }

    private static void collectVariantRefs(Path csv, String column,
                                           Set<String> known, Set<String> orphaned) {
        for (Map<String, String> row : CsvTable.read(csv)) {
            String name = row.getOrDefault(column, "").strip();
            if (!name.isEmpty() && !known.contains(name)) {
                orphaned.add(name);
            }
        }
    }

    private static void collectSpawnVariantRefs(Path csv, Set<String> known, Set<String> orphaned) {
        for (Map<String, String> row : CsvTable.read(csv)) {
            if (!Parse.flag(row.get(Schema.MobSpawns.IS_VARIANT), false)) continue;
            String name = row.getOrDefault(Schema.Equipment.VARIANT_NAME, "").strip();
            if (!name.isEmpty() && !known.contains(name)) {
                orphaned.add(name);
            }
        }
    }
}
