package com.strata.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Walks game data, then the tier, then the biome, and produces the answer. */
public final class Resolver {

    public interface GameFacts {
        List<Spawn> spawns(String biome, SpawnCategory category);
        List<String> ores(String biome);
    }

    private final GameFacts game;
    private final Map<String, Tier> tiers;
    private final Map<String, BiomeConfig> biomes;
    private final Map<String, Variant> variants;
    private final List<MobLoot> globalLoot;
    private final Map<String, List<MobLoot>> tierLoot;
    private final double globalCoefficient;
    private final Map<SpawnCategory, List<Spawn>> globalSpawns;

    public Resolver(GameFacts game, Map<String, Tier> tiers,
                    Map<String, BiomeConfig> biomes, Map<String, Variant> variants,
                    List<MobLoot> globalLoot,
                    Map<String, List<MobLoot>> tierLoot,
                    double globalCoefficient) {
        this(game, tiers, biomes, variants, globalLoot, tierLoot, globalCoefficient, Map.of());
    }

    public Resolver(GameFacts game, Map<String, Tier> tiers,
                    Map<String, BiomeConfig> biomes, Map<String, Variant> variants,
                    List<MobLoot> globalLoot,
                    Map<String, List<MobLoot>> tierLoot,
                    double globalCoefficient,
                    Map<SpawnCategory, List<Spawn>> globalSpawns) {
        this.game = game;
        this.tiers = tiers;
        this.biomes = biomes;
        this.variants = variants;
        this.globalLoot = globalLoot;
        this.tierLoot = tierLoot;
        this.globalCoefficient = globalCoefficient;
        this.globalSpawns = globalSpawns;
    }

    public Resolution resolve(String biome) {
        BiomeConfig config = biomes.getOrDefault(biome, BiomeConfig.empty(biome));
        Tier tier = config.tier() == null ? null : tiers.get(config.tier());

        int level;
        Source levelSource;
        if (config.level() != null) {
            level = config.level();
            levelSource = Source.BIOME;
        } else if (tier != null) {
            level = tier.level();
            levelSource = Source.tier(tier.name());
        } else {
            level = 0;
            levelSource = Source.NONE;
        }

        Map<SpawnCategory, List<Spawn>> mobs = new EnumMap<>(SpawnCategory.class);
        for (SpawnCategory category : SpawnCategory.values()) {
            mobs.put(category, resolveSpawns(biome, tier, config, category));
        }

        double coefficient = resolveCoefficient(tier, config);
        List<MobLoot> loot = resolveLoot(tier, config);

        return new Resolution(biome, tier == null ? null : tier.name(), level, levelSource,
                mobs, resolveOres(biome, tier, config),
                resolveOreFrequencies(tier, config),
                oresOverridden(tier, config), variantsFor(biome),
                loot, coefficient,
                config.scaleMobs(), config.restrictOres(), config.substituteSpawns(),
                com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.CHUNK_SCALE));
    }

    // Chunk scale is now a global scaling parameter (config/strata-common.toml under scaling.chunkScale).

    /** Coefficient layering: global → tier → biome. */
    private double resolveCoefficient(Tier tier, BiomeConfig config) {
        if (config.coefficient() != null) {
            return config.coefficient();
        }
        if (tier != null && tier.coefficient() != 1.0) {
            return tier.coefficient();
        }
        return globalCoefficient;
    }

    /** Loot table layering: global, then tier-scoped, then biome-scoped. */
    private List<MobLoot> resolveLoot(Tier tier, BiomeConfig config) {
        List<MobLoot> resolved = new ArrayList<>(globalLoot);
        if (tier != null) {
            for (MobLoot override : tierLoot.getOrDefault(tier.name(), List.of())) {
                resolved.add(override);
            }
        }
        resolved.addAll(config.loot());
        return List.copyOf(resolved);
    }

    private List<Spawn> resolveSpawns(String biome, Tier tier, BiomeConfig config,
                                      SpawnCategory category) {
        List<Spawn> declared = merge(biome, tier, config, category);

        List<Spawn> out = new ArrayList<>();
        for (Spawn spawn : declared) {
            if (spawn.weight() > 0 && spawn.enabled()) {
                out.add(spawn);
            }
        }
        return List.copyOf(out);
    }

    /**
     * True when the biome's tier replaces the game's spawns (REPLACE, or DENY read as REPLACE): its roster is
     * all that spawns there.
     */
    public boolean replacesGame(String biome) {
        BiomeConfig config = biomes.getOrDefault(biome, BiomeConfig.empty(biome));
        Tier tier = config.tier() == null ? null : tiers.get(config.tier());
        return replaces(tier);
    }

    private static boolean replaces(Tier tier) {
        return tier != null
                && (tier.spawnMode() == OverrideMode.REPLACE || tier.spawnMode() == OverrideMode.DENY);
    }

    public List<Spawn> declaredSpawns(String biome, SpawnCategory category) {
        BiomeConfig config = biomes.getOrDefault(biome, BiomeConfig.empty(biome));
        Tier tier = config.tier() == null ? null : tiers.get(config.tier());
        return merge(biome, tier, config, category);
    }

    private List<Spawn> merge(String biome, Tier tier, BiomeConfig config,
                              SpawnCategory category) {
        // REPLACE tiers discard the game roster (design-spec System 1).
        boolean tierReplaces = replaces(tier);

        Map<String, Spawn> merged = new LinkedHashMap<>();
        if (!tierReplaces) {
            for (Spawn spawn : game.spawns(biome, category)) {
                merged.put(spawn.key(), spawn);
            }
        }
        // Global-scope spawns are the broadest explicit layer: applied after the game roster, before
        // tier/biome which are more specific.
        if (!tierReplaces) {
            for (Spawn spawn : globalSpawns.getOrDefault(category, List.of())) {
                merged.put(spawn.key(), spawn.withSource(Source.GLOBAL));
            }
        }
        if (tier != null) {
            for (Spawn spawn : tier.mobs(category)) {
                merged.put(spawn.key(), spawn.withSource(Source.tier(tier.name())));
            }
        }
        for (Spawn spawn : config.mobs(category)) {
            merged.put(spawn.key(), spawn.withSource(Source.BIOME));
        }
        return List.copyOf(merged.values());
    }

    /** THE GLOBAL ORE RULES: ores.csv rows of scope global, under every tier and biome. */
    private static volatile List<OreRule> globalOres = List.of();

    public static void setGlobalOres(List<OreRule> rules) {
        globalOres = rules == null ? List.of() : List.copyOf(rules);
    }

    private boolean oresOverridden(Tier tier, BiomeConfig config) {
        if (!globalOres.isEmpty() || !config.ores().isEmpty()) {
            return true;
        }
        return tier != null && !tier.ores().isEmpty();
    }

    private List<String> resolveOres(String biome, Tier tier, BiomeConfig config) {
        List<String> resolved = OverrideMode.apply(game.ores(biome), rules(globalOres), ore -> ore);
        if (tier != null) {
            resolved = OverrideMode.apply(resolved, rules(tier.ores()), ore -> ore);
        }
        return OverrideMode.apply(resolved, rules(config.ores()), ore -> ore);
    }

    /**
     * The effective frequency of every ore some rule shaped, layered the same way as the ore list itself:
     * tier first, then biome, most specific wins.
     */
    private Map<String, Double> resolveOreFrequencies(Tier tier, BiomeConfig config) {
        Map<String, Double> frequencies = new HashMap<>();
        applyOreLayer(frequencies, globalOres);
        if (tier != null) {
            applyOreLayer(frequencies, tier.ores());
        }
        applyOreLayer(frequencies, config.ores());
        return Map.copyOf(frequencies);
    }

    private static void applyOreLayer(Map<String, Double> frequencies, List<OreRule> layer) {
        for (OreRule rule : layer) {
            if (rule.mode() == OverrideMode.DENY) {
                frequencies.remove(rule.oreId());
            } else {
                frequencies.put(rule.oreId(), rule.frequencyPct());
            }
        }
    }

    private static List<Rule<String>> rules(List<OreRule> oreRules) {
        return oreRules.stream().map(OreRule::toRule).toList();
    }

    /**
     * Variants are now just spawn entries — this collects the variant definitions for mobs that appear in
     * this biome.
     */
    private List<Variant> variantsFor(String biome) {
        BiomeConfig config = biomes.getOrDefault(biome, BiomeConfig.empty(biome));
        Tier tier = config.tier() == null ? null : tiers.get(config.tier());

        List<Variant> found = new ArrayList<>();
        for (SpawnCategory category : SpawnCategory.values()) {
            for (Spawn spawn : merge(biome, tier, config, category)) {
                if (spawn.isVariant()) {
                    Variant v = variants.get(spawn.variant());
                    if (v != null && !found.contains(v)) {
                        found.add(v);
                    }
                }
            }
        }
        return List.copyOf(found);
    }
}
