package com.strata.data;

import com.strata.Strata;
import com.strata.model.BiomeConfig;
import com.strata.model.LootCondition;
import com.strata.model.LootEntry;
import com.strata.model.LootPool;
import com.strata.model.MobLoot;
import com.strata.model.OreFrequency;
import com.strata.model.OreRule;
import com.strata.model.OverrideMode;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;
import com.strata.model.SpawnRule;
import com.strata.model.Source;
import com.strata.model.Tier;
import com.strata.model.Variant;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the CSV tables into the model. */
public final class Tables {

    private Tables() {}

    public record Loaded(
            Map<String, Tier> tiers,
            Map<String, BiomeConfig> biomes,
            Map<String, Variant> variants,
            MobPropertyLayers properties,
            List<MobLoot> globalLoot,
            List<MobLoot> allLoot,
            double globalCoefficient,
            Map<SpawnCategory, List<Spawn>> globalSpawns
    ) {
        public static final Loaded EMPTY = new Loaded(
                Map.of(), Map.of(), Map.of(), MobPropertyLayers.EMPTY,
                List.of(), List.of(), 1.0, Map.of());

        /** Tier-scoped drops, keyed by tier name. */
        public Map<String, List<MobLoot>> tierLoot() {
            Map<String, List<MobLoot>> out = new HashMap<>();
            for (MobLoot loot : allLoot()) {
                if ("tier".equals(loot.scope())) {
                    out.computeIfAbsent(loot.scopeId(), k -> new ArrayList<>()).add(loot);
                }
            }
            return out;
        }
    }

    public static Loaded loadAll() {
        return loadAll(Paths.ROOT);
    }

    /** Path-rooted load. */
    public static Loaded loadAll(Path root) {
        Map<String, Tier> tiers = loadTiers(root);
        List<MobLoot> allLoot = loadLoot(root);
        Map<String, BiomeConfig> biomes = loadBiomes(root, tiers, allLoot);
        Map<String, Variant> variants = loadVariants(root);
        MobPropertyLayers properties = MobPropertyLayers.load(root);
        List<MobLoot> globalLoot = allLoot.stream()
                .filter(l -> "global".equals(l.scope()))
                .toList();

        // Global-scope spawns are their own layer, applied under tier/biome.
        Map<String, Map<SpawnCategory, List<Spawn>>> global = loadScopedSpawns(root, "global");
        Map<SpawnCategory, List<Spawn>> globalSpawns = global.getOrDefault("",
                new EnumMap<>(SpawnCategory.class));

        Strata.LOGGER.info("Loaded {} tier(s), {} configured biome(s), {} variant(s), {} property set(s), {} loot entries",
                tiers.size(), biomes.size(), variants.size(), properties.byInstance().size(), allLoot.size());
        return new Loaded(tiers, biomes, variants, properties, globalLoot, allLoot, 1.0, globalSpawns);
    }

    // ---- tiers ----

    private static Map<String, Tier> loadTiers(Path root) {
        Map<String, Map<SpawnCategory, List<Spawn>>> mobs = loadScopedSpawns(root, "tier");
        Map<String, List<OreRule>> ores = loadScopedOreRules(root, "tier");
        // The global layer, under every tier and biome.
        com.strata.model.Resolver.setGlobalOres(loadScopedOreRules(root, "global").getOrDefault("", List.of()));

        Map<String, Tier> out = new LinkedHashMap<>();
        for (Map<String, String> row : rows(root, Schema.Tiers.FILE)) {
            String name = row.getOrDefault(Schema.Tiers.NAME, "").strip();
            if (name.isEmpty()) continue;
            int level = Parse.integer(row.get(Schema.Tiers.LEVEL), 0);
            OverrideMode spawnMode = OverrideMode.parse(row.get(Schema.Tiers.SPAWN_MODE));
            double coefficient = Parse.number(row.get(Schema.Tiers.COEFFICIENT), 1.0);
            // Blank or 0 = off: the level alone.
            out.put(name, new Tier(name, level, spawnMode, coefficient,
                    mobs.getOrDefault(name, new EnumMap<>(SpawnCategory.class)),
                    ores.getOrDefault(name, List.of())));
        }
        return Map.copyOf(out);
    }

    // ---- biomes ----

    private static Map<String, BiomeConfig> loadBiomes(Path root, Map<String, Tier> tiers, List<MobLoot> allLoot) {
        Map<String, String> biomeTier = new LinkedHashMap<>();
        for (Map<String, String> row : rows(root, Schema.TierBiomes.FILE)) {
            String tier = row.getOrDefault(Schema.TierBiomes.TIER, "").strip();
            String biome = row.getOrDefault(Schema.TierBiomes.BIOME, "").strip();
            if (tier.isEmpty() || biome.isEmpty()) continue;
            if (!tiers.containsKey(tier)) {
                Strata.LOGGER.warn("tier_biomes.csv: {} names unknown tier '{}' - skipped", biome, tier);
                continue;
            }
            String existing = biomeTier.putIfAbsent(biome, tier);
            if (existing != null && !existing.equals(tier)) {
                Strata.LOGGER.warn("tier_biomes.csv: {} already in tier {} - row for {} ignored",
                        biome, existing, tier);
            }
        }

        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Boolean> scaleMobs = new LinkedHashMap<>();
        Map<String, Boolean> restrictOres = new LinkedHashMap<>();
        Map<String, Boolean> substituteSpawns = new LinkedHashMap<>();
        Map<String, Double> coefficients = new LinkedHashMap<>();
        for (Map<String, String> row : rows(root, Schema.BiomeOverrides.FILE)) {
            String biome = row.getOrDefault(Schema.BiomeOverrides.BIOME, "").strip();
            if (biome.isEmpty()) continue;
            String level = row.getOrDefault(Schema.BiomeOverrides.LEVEL, "").strip();
            if (!level.isEmpty()) levels.put(biome, Parse.integer(level, 0));
            flag(row, Schema.BiomeOverrides.SCALE_MOBS, biome, scaleMobs);
            flag(row, Schema.BiomeOverrides.RESTRICT_ORES, biome, restrictOres);
            flag(row, Schema.BiomeOverrides.SUBSTITUTE_SPAWNS, biome, substituteSpawns);
            String coeff = row.getOrDefault(Schema.BiomeOverrides.COEFFICIENT, "").strip();
            if (!coeff.isEmpty()) coefficients.put(biome, Parse.number(coeff, 1.0));
        }

        Map<String, Map<SpawnCategory, List<Spawn>>> mobs = loadScopedSpawns(root, "biome");
        Map<String, List<OreRule>> ores = loadScopedOreRules(root, "biome");
        Map<String, List<MobLoot>> biomeLoot = new HashMap<>();
        for (MobLoot loot : allLoot) {
            if ("biome".equals(loot.scope())) {
                biomeLoot.computeIfAbsent(loot.scopeId(), k -> new ArrayList<>()).add(loot);
            }
        }

        Map<String, List<SpawnRule>> condByBiome = loadSpawnRulesByScope(root, "biome");

        Set<String> all = new LinkedHashSet<>(biomeTier.keySet());
        all.addAll(levels.keySet());
        all.addAll(scaleMobs.keySet());
        all.addAll(restrictOres.keySet());
        all.addAll(substituteSpawns.keySet());
        all.addAll(coefficients.keySet());
        all.addAll(mobs.keySet());
        all.addAll(ores.keySet());
        all.addAll(biomeLoot.keySet());
        all.addAll(condByBiome.keySet());

        Map<String, BiomeConfig> out = new LinkedHashMap<>();
        for (String biome : all) {
            out.put(biome, new BiomeConfig(biome, biomeTier.get(biome), levels.get(biome),
                    scaleMobs.get(biome), restrictOres.get(biome), substituteSpawns.get(biome),
                    mobs.getOrDefault(biome, new EnumMap<>(SpawnCategory.class)),
                    ores.getOrDefault(biome, List.of()),
                    biomeLoot.getOrDefault(biome, List.of()),
                    condByBiome.getOrDefault(biome, List.of()),
                    coefficients.get(biome)));
        }
        return Map.copyOf(out);
    }

    // ---- variants ----

    private static Map<String, Variant> loadVariants(Path root) {
        // variant -> instance (blank for global) -> attribute -> value.
        Map<String, Map<String, Map<String, Double>>> multipliers = new HashMap<>();
        Map<String, Map<String, Map<String, Double>>> absolutes = new HashMap<>();
        for (Map<String, String> row : rows(root, Schema.VariantStats.FILE)) {
            String variant = row.getOrDefault(Schema.VariantStats.VARIANT_NAME, "").strip();
            String attribute = row.getOrDefault(Schema.VariantStats.ATTRIBUTE, "").strip();
            if (variant.isEmpty() || attribute.isEmpty()) continue;
            // THE SCOPE IS KEPT.
            String scope = row.getOrDefault(Schema.VariantStats.SCOPE, "").strip().toLowerCase(java.util.Locale.ROOT);
            String where = scope.isEmpty() || scope.equals("global") ? ""
                    : Variant.scopeKey(scope, row.getOrDefault(Schema.VariantStats.SCOPE_ID, "").strip());
            String abs = row.getOrDefault(Schema.VariantStats.ABSOLUTE, "").strip();
            if (!abs.isEmpty()) {
                absolutes.computeIfAbsent(variant, k -> new LinkedHashMap<>())
                        .computeIfAbsent(where, k -> new LinkedHashMap<>())
                        .put(attribute, Parse.number(abs, 0));
            }
            String mult = row.getOrDefault(Schema.VariantStats.MULTIPLIER, "").strip();
            if (!mult.isEmpty()) {
                multipliers.computeIfAbsent(variant, k -> new LinkedHashMap<>())
                        .computeIfAbsent(where, k -> new LinkedHashMap<>())
                        .put(attribute, Parse.number(mult, 1));
            }
        }

        Map<String, Variant> out = new LinkedHashMap<>();
        for (Map<String, String> row : rows(root, Schema.Variants.FILE)) {
            String id = row.getOrDefault(Schema.Variants.NAME, "").strip();
            String base = row.getOrDefault(Schema.Variants.BASE_ENTITY, "").strip();
            if (id.isEmpty() || base.isEmpty()) continue;
            String displayName = row.getOrDefault(Schema.Variants.DISPLAY_NAME, "").strip();
            if (displayName.isEmpty()) displayName = id;
            double coefficient = Parse.number(row.get(Schema.Variants.COEFFICIENT), 1.0);
            String texture = row.getOrDefault(Schema.Variants.TEXTURE_PATH, "").strip();
            boolean isOwnEntity = Parse.flag(row.get(Schema.Variants.IS_OWN_ENTITY), false);

            out.put(id, new Variant(id, base, displayName, coefficient,
                    texture.isEmpty() ? "" : texture,
                    global(multipliers.get(id)),
                    global(absolutes.get(id)),
                    isOwnEntity,
                    scoped(multipliers.get(id)),
                    scoped(absolutes.get(id)),
                    Parse.integer(row.get(Schema.Variants.START_LEVEL), 1)));
        }
        return Map.copyOf(out);
    }

    /**
     * Loads all variant property tables (sounds, visuals, physics, flags, attributes, combat, particles,
     * loot_xp) - global scope only for now.
     */
    /** The global instance's stats of one variant. */
    private static Map<String, Double> global(Map<String, Map<String, Double>> byInstance) {
        return byInstance == null ? Map.of() : Map.copyOf(byInstance.getOrDefault("", Map.of()));
    }

    /** The tier and biome instances' stats of one variant, by Variant#scopeKey. */
    private static Map<String, Map<String, Double>> scoped(Map<String, Map<String, Double>> byInstance) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        if (byInstance != null) {
            byInstance.forEach((where, stats) -> {
                if (!where.isEmpty()) {
                    out.put(where, Map.copyOf(stats));
                }
            });
        }
        return Map.copyOf(out);
    }

    // ---- drops ----

    private static List<MobLoot> loadLoot(Path root) {
        Map<String, List<Map<String, String>>> grouped = new LinkedHashMap<>();
        for (Map<String, String> row : rows(root, Schema.Loot.FILE)) {
            String scope = row.getOrDefault(Schema.Loot.SCOPE, "global").strip();
            String scopeId = row.getOrDefault(Schema.Loot.SCOPE_ID, "").strip();
            String entity = row.getOrDefault(Schema.Loot.ENTITY, "").strip();
            if (entity.isEmpty()) continue;
            // THE VARIANT IS PART OF THE GROUP.
            String variant = row.getOrDefault(Schema.Loot.VARIANT_NAME, "").strip();
            String key = scope + "|" + scopeId + "|" + entity + "|" + variant + "|"
                    + row.getOrDefault(Schema.Loot.MODE, "add").strip();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<MobLoot> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 5);
            String scope = parts[0];
            String scopeId = parts[1];
            String entity = parts[2];
            String variant = parts[3];
            OverrideMode mode = OverrideMode.parse(parts[4]);

            Map<Integer, List<LootEntry>> pools = new LinkedHashMap<>();
            for (Map<String, String> row : entry.getValue()) {
                int poolIdx = Parse.integer(row.get(Schema.Loot.POOL_INDEX), 0);
                String condType = row.getOrDefault(Schema.Loot.CONDITION_TYPE, "").strip();
                String condValue = row.getOrDefault(Schema.Loot.CONDITION_VALUE, "").strip();
                List<LootCondition> conditions;
                if (condType.isEmpty()) {
                    conditions = List.of();
                } else {
                    LootCondition.Type parsedType = LootCondition.Type.parse(condType);
                    if (parsedType == null) {
                        Strata.LOGGER.warn("loot.csv: unknown condition type '{}' on item {} - "
                                + "condition skipped", condType, row.getOrDefault(Schema.Loot.ITEM, "?"));
                        conditions = List.of();
                    } else {
                        conditions = List.of(LootCondition.of(parsedType, condType, condValue));
                    }
                }
                String item = row.getOrDefault(Schema.Loot.ITEM, "").strip();
                int countMin = Parse.integer(row.get(Schema.Loot.COUNT_MIN), 1);
                int countMax = Parse.integer(row.get(Schema.Loot.COUNT_MAX), 1);
                if (Math.max(countMin, countMax) > LootEntry.MAX_COUNT) {
                    Strata.LOGGER.warn("loot.csv: {} drops up to {} - capped at {}", item,
                            Math.max(countMin, countMax), LootEntry.MAX_COUNT);
                }
                pools.computeIfAbsent(poolIdx, k -> new ArrayList<>()).add(new LootEntry(
                        item,
                        Math.max(0, Math.min(countMin, LootEntry.MAX_COUNT)),
                        Math.max(0, Math.min(countMax, LootEntry.MAX_COUNT)),
                        Math.max(0, Math.min(Parse.integer(row.get(Schema.Loot.CHANCE_PCT), 100), 100)),
                        conditions));
            }

            List<LootPool> lootPools = new ArrayList<>();
            for (List<LootEntry> entries : pools.values()) {
                lootPools.add(LootPool.of(entries));
            }

            out.add(new MobLoot(scope, scopeId, entity, variant, mode, lootPools));
        }
        return List.copyOf(out);
    }

    // ---- mob spawns (scoped) ----

    /**
     * Loads one scope layer (tier/biome/global) from mob_spawns.csv into a "scope_id → category → spawns"
     * map.
     */
    private static Map<String, Map<SpawnCategory, List<Spawn>>> loadScopedSpawns(Path root, String targetScope) {
        // (scope|scopeId|entity[,variant]) -> List<SpawnRule>, from spawn_rules.csv.
        Map<String, List<SpawnRule>> condByEntity = loadSpawnRulesByRow(root);

        Map<String, Map<SpawnCategory, List<Spawn>>> out = new HashMap<>();
        for (Map<String, String> row : rows(root, Schema.MobSpawns.FILE)) {
            String scope = row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip();
            if (!targetScope.equals(scope)) continue;
            String scopeId = row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip();
            if (!"global".equals(scope) && scopeId.isEmpty()) continue;

            String entity = row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip();
            if (entity.isEmpty()) continue;

            SpawnCategory category = SpawnCategory.parse(
                    row.getOrDefault(Schema.MobSpawns.CATEGORY, ""));
            String variantName = row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip();
            boolean isVariant = Parse.flag(row.get(Schema.MobSpawns.IS_VARIANT), false);
            boolean enabled = Parse.flag(row.get(Schema.MobSpawns.ENABLED), true);
            String coeffStr = row.getOrDefault(Schema.MobSpawns.COEFFICIENT, "").strip();
            Double coefficient = coeffStr.isEmpty() ? null : Parse.number(coeffStr, 1.0);

            Source source;
            if ("tier".equals(scope)) source = Source.tier(scopeId);
            else if ("global".equals(scope)) source = Source.GLOBAL;
            else source = Source.BIOME;

            // Attach the row's spawn conditions.
            List<SpawnRule> conditions = spawnRulesFor(condByEntity, scope, scopeId, entity, variantName,
                    isVariant);

            // A group may be 0; a spawn chance is 0 to 100, blank 100.
            int min = Math.max(0, Parse.integer(row.get(Schema.MobSpawns.MIN_COUNT), 1));
            Spawn spawn = new Spawn(category, entity,
                    isVariant ? variantName : "",
                    Math.min(Parse.integer(row.get(Schema.MobSpawns.WEIGHT), 1), Spawn.MAX_WEIGHT),
                    min,
                    Math.max(min, Parse.integer(row.get(Schema.MobSpawns.MAX_COUNT), 4)),
                    enabled, coefficient, conditions, source,
                    Parse.number(row.get(Schema.MobSpawns.SPAWN_CHANCE), Spawn.FULL_CHANCE));

            out.computeIfAbsent(scopeId, k -> new EnumMap<>(SpawnCategory.class))
                    .computeIfAbsent(spawn.category(), k -> new ArrayList<>())
                    .add(spawn);
        }
        return out;
    }

    /**
     * Reads spawn_rules.csv into a map keyed like the mob_spawns.csv row each qualifies: (scope, scope_id,
     * entity, variant_name), so {@link #loadScopedSpawns} can attach conditions to the right Spawn.
     */
    public static Map<String, List<SpawnRule>> loadSpawnRulesByRow(Path root) {
        Map<String, List<SpawnRule>> out = new HashMap<>();
        for (Map<String, String> row : rows(root, Schema.SpawnRules.FILE)) {
            String scope = row.getOrDefault(Schema.SpawnRules.SCOPE, "").strip();
            String scopeId = row.getOrDefault(Schema.SpawnRules.SCOPE_ID, "").strip();
            String entity = row.getOrDefault(Schema.SpawnRules.ENTITY, "").strip();
            if (entity.isEmpty()) continue;
            String variant = row.getOrDefault(Schema.SpawnRules.VARIANT_NAME, "").strip();
            SpawnRule.Type type = SpawnRule.Type.parse(row.get(Schema.SpawnRules.RULE_TYPE));
            if (type == null) continue;
            out.computeIfAbsent(key(scope, scopeId, entity, variant, !variant.isEmpty()), k -> new ArrayList<>())
                    .add(new SpawnRule(type, row.getOrDefault(Schema.SpawnRules.VALUE, "").strip()));
        }
        return out;
    }

    /**
     * A spawn row's conditions: its own, else its base mob's in the same scope, else (for a tier or biome
     * row) the global variant's, else the global mob's.
     */
    public static List<SpawnRule> spawnRulesFor(Map<String, List<SpawnRule>> byRow, String scope,
                                                     String scopeId, String entity, String variantName,
                                                     boolean isVariant) {
        List<String> keys = new ArrayList<>(List.of(key(scope, scopeId, entity, variantName, isVariant),
                key(scope, scopeId, entity, "", false)));
        if (!"global".equals(scope)) {
            keys.add(key("global", "", entity, variantName, isVariant));
            keys.add(key("global", "", entity, "", false));
        }
        for (String k : keys) {
            List<SpawnRule> hit = byRow.get(k);
            if (hit != null) {
                return hit;
            }
        }
        return List.of();
    }

    private static String key(String scope, String scopeId, String entity,
                              String variantName, boolean isVariant) {
        return scope + "|" + scopeId + "|" + entity + (isVariant ? "#" + variantName : "");
    }

    // ---- ores (scoped) ----

    /** Loads one scope layer (tier/biome) from ores.csv into a "scope_id → ore rules" map. */
    private static Map<String, List<OreRule>> loadScopedOreRules(Path root, String targetScope) {
        Map<String, List<OreRule>> out = new HashMap<>();
        for (Map<String, String> row : rows(root, Schema.Ores.FILE)) {
            String scope = row.getOrDefault(Schema.Ores.SCOPE, "").strip();
            if (!targetScope.equals(scope)) continue;
            String scopeId = "global".equals(targetScope) ? "" : row.getOrDefault(Schema.Ores.SCOPE_ID, "").strip();
            String oreId = row.getOrDefault(Schema.Ores.ORE_ID, "").strip();
            if ((scopeId.isEmpty() && !"global".equals(targetScope)) || oreId.isEmpty()) continue;
            boolean enabled = Parse.flag(row.get(Schema.Ores.ENABLED), true);
            double frequencyPct = OreFrequency.clamp(
                    Parse.number(row.get(Schema.Ores.FREQUENCY_PCT), OreFrequency.FULL));
            OverrideMode mode = enabled ? OverrideMode.ADD : OverrideMode.DENY;
            out.computeIfAbsent(scopeId, k -> new ArrayList<>())
                    .add(new OreRule(oreId, mode, frequencyPct));
        }
        return out;
    }

    // ---- spawn conditions ----

    private static Map<String, List<SpawnRule>> loadSpawnRulesByScope(Path root, String targetScope) {
        Map<String, List<SpawnRule>> out = new HashMap<>();
        for (Map<String, String> row : rows(root, Schema.SpawnRules.FILE)) {
            String scope = row.getOrDefault(Schema.SpawnRules.SCOPE, "").strip();
            if (!targetScope.equals(scope)) continue;
            String scopeId = row.getOrDefault(Schema.SpawnRules.SCOPE_ID, "").strip();
            if (scopeId.isEmpty()) continue;
            SpawnRule.Type type = SpawnRule.Type.parse(row.get(Schema.SpawnRules.RULE_TYPE));
            if (type == null) continue;
            out.computeIfAbsent(scopeId, k -> new ArrayList<>())
                    .add(new SpawnRule(type, row.getOrDefault(Schema.SpawnRules.VALUE, "").strip()));
        }
        return out;
    }

    // ---- shared ----

    private static void flag(Map<String, String> row, String column, String key,
                             Map<String, Boolean> into) {
        String raw = row.getOrDefault(column, "").strip();
        if (!raw.isEmpty()) {
            into.put(key, Boolean.parseBoolean(raw));
        }
    }

    private static List<Map<String, String>> rows(Path root, String fileName) {
        Path path = root.resolve(fileName);
        return CsvTable.read(path);
    }
}
