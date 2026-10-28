package com.strata.model;

import com.strata.SelfTestHarness;
import com.strata.runtime.AttributeCurve;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixture-driven tests for the resolver. */
public final class ResolverSelfTest {

    private ResolverSelfTest() {}

    private static final class Facts implements Resolver.GameFacts {
        final Map<String, List<Spawn>> spawns = new LinkedHashMap<>();
        final Map<String, List<String>> ores = new LinkedHashMap<>();

        @Override
        public List<Spawn> spawns(String biome, SpawnCategory category) {
            return spawns.getOrDefault(biome + "/" + category.id(), List.of());
        }

        @Override
        public List<String> ores(String biome) {
            return ores.getOrDefault(biome, List.of());
        }
    }

    private static Spawn spawn(SpawnCategory category, String entity, int weight) {
        return Spawn.of(category, entity, weight, 1, 4, Source.GAME);
    }

    private static Spawn variantSpawn(SpawnCategory category, String entity,
                                      String variantId, int weight) {
        return new Spawn(category, entity, variantId, weight, 1, 4,
                true, null, List.of(), Source.GAME);
    }

    private static Spawn disabledSpawn(SpawnCategory category, String entity, int weight) {
        return new Spawn(category, entity, "", weight, 1, 4,
                false, null, List.of(), Source.GAME);
    }

    private static final String PLAINS = "minecraft:plains";
    private static final String FOREST = "minecraft:forest";
    private static final String CREEPER = "minecraft:creeper";

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];

        Facts facts = new Facts();
        facts.spawns.put(PLAINS + "/monster", List.of(
                spawn(SpawnCategory.MONSTER, "minecraft:zombie", 100),
                spawn(SpawnCategory.MONSTER, "minecraft:skeleton", 80)));
        facts.spawns.put(PLAINS + "/creature", List.of(
                spawn(SpawnCategory.CREATURE, "minecraft:cow", 8),
                spawn(SpawnCategory.CREATURE, "minecraft:sheep", 12)));
        facts.ores.put(PLAINS, List.of("iron", "coal", "copper"));

        // 1 — no tier, no level: vanilla
        SelfTestHarness.check(lines, tally, "1  no tier, no level -> vanilla",
                () -> !resolver(facts).resolve(PLAINS).scaled());

        // 2 — biome level, no tier: scaled
        SelfTestHarness.check(lines, tally, "2  biome level, no tier -> scaled", () -> {
            Resolution r = resolver(facts, tiers(),
                    biomes(biome(PLAINS, null, 3))).resolve(PLAINS);
            return r.scaled() && r.level() == 3
                    && r.levelSource().layer() == Source.Layer.BIOME;
        });

        // 3 — biome level beats tier level
        SelfTestHarness.check(lines, tally, "3  biome level and tier -> biome wins", () -> {
            Resolution r = resolver(facts, tiers(Tier.of("TEMPERATE", 2)),
                    biomes(biome(PLAINS, "TEMPERATE", 7))).resolve(PLAINS);
            return r.level() == 7 && r.levelSource().layer() == Source.Layer.BIOME;
        });

        // 4 — tier level 0: not scaled, but still configured
        SelfTestHarness.check(lines, tally, "4  tier level 0 -> unscaled, roster still resolves", () -> {
            Resolution r = resolver(facts, tiers(Tier.of("FLAT", 0)),
                    biomes(biome(PLAINS, "FLAT", null))).resolve(PLAINS);
            return !r.scaled() && r.configured()
                    && r.mobs(SpawnCategory.MONSTER).size() == 2;
        });

        // 4b — level 0 with a spawn rule
        SelfTestHarness.check(lines, tally, "4b level 0 + spawn entry -> entry applies unscaled", () -> {
            Tier flat = tierWithMobs("FLAT", 0, SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:husk", 50)));
            Resolution r = resolver(facts, tiers(flat),
                    biomes(biome(PLAINS, "FLAT", null))).resolve(PLAINS);
            return !r.scaled() && r.mobs(SpawnCategory.MONSTER).size() == 3;
        });

        // 4c — untouched biome: game roster intact
        SelfTestHarness.check(lines, tally, "4c no tier, no rules -> untouched, game roster intact", () -> {
            Resolution r = resolver(facts).resolve(PLAINS);
            boolean gameListIntact = r.mobs(SpawnCategory.MONSTER).size() == 2
                    && r.mobs(SpawnCategory.MONSTER).stream().allMatch(
                            s -> s.source().layer() == Source.Layer.GAME);
            return !r.scaled() && !r.configured() && gameListIntact;
        });

        // 5 — tier adds a hostile
        SelfTestHarness.check(lines, tally, "5  tier adds a hostile -> game list + 1", () -> {
            Tier tier = tierWithMobs("TEMPERATE", 2, SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:husk", 50)));
            Resolution r = resolver(facts, tiers(tier),
                    biomes(biome(PLAINS, "TEMPERATE", null))).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).size() == 3;
        });

        // 6 — REPLACE tier discards the game roster for every category
        SelfTestHarness.check(lines, tally, "6  tier REPLACE -> game roster replaced entirely", () -> {
            Tier tier = new Tier("HARSH", 2, OverrideMode.REPLACE, 1.0,
                    new EnumMap<>(SpawnCategory.class) {{
                        put(SpawnCategory.MONSTER, List.of(
                                spawn(SpawnCategory.MONSTER, "minecraft:husk", 50)));
                    }}, List.of());
            Resolution r = resolver(facts, tiers(tier),
                    biomes(biome(PLAINS, "HARSH", null))).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).size() == 1
                    && r.mobs(SpawnCategory.MONSTER).get(0).entity().equals("minecraft:husk")
                    && r.mobs(SpawnCategory.CREATURE).isEmpty();
        });

        // 6t — a tier saved as DENY acts as REPLACE: its tooltip said "removes vanilla spawns entirely", and
        // it did nothing.
        SelfTestHarness.check(lines, tally, "6t tier DENY -> the game roster goes, as REPLACE", () -> {
            Tier tier = new Tier("SAFE", 0, OverrideMode.DENY, 1.0, new EnumMap<>(SpawnCategory.class), List.of());
            Resolution r = resolver(facts, tiers(tier),
                    biomes(biome(PLAINS, "SAFE", null))).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).isEmpty() && r.mobs(SpawnCategory.CREATURE).isEmpty();
        });
        // 7 — tier entry plus biome entry
        SelfTestHarness.check(lines, tally, "7  tier entry plus biome entry -> both present", () -> {
            Tier tier = tierWithMobs("TEMPERATE", 2, SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:husk", 50)));
            BiomeConfig config = biomeWithMobs(PLAINS, "TEMPERATE", SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:stray", 20)));
            Resolution r = resolver(facts, tiers(tier), biomes(config)).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).size() == 4;
        });

        // 8 — weight 0 removes
        SelfTestHarness.check(lines, tally, "8  weight 0 removes it from the resolved list", () -> {
            Tier tier = tierWithMobs("TEMPERATE", 2, SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:husk", 50)));
            BiomeConfig config = biomeWithMobs(PLAINS, "TEMPERATE", SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:husk", 0)));
            Resolution r = resolver(facts, tiers(tier), biomes(config)).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).size() == 2;
        });

        // 9 — replacing hostiles leaves passives alone
        SelfTestHarness.check(lines, tally, "9  biome spawn entry -> passives untouched", () -> {
            BiomeConfig config = biomeWithMobs(PLAINS, null, SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:husk", 50)));
            config = withLevel(config, 2);
            Resolution r = resolver(facts, tiers(), biomes(config)).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).size() == 3
                    && r.mobs(SpawnCategory.CREATURE).size() == 2;
        });

        // 10 — same entity in two categories survives
        SelfTestHarness.check(lines, tally, "10 same entity, two categories -> both survive", () -> {
            Facts two = new Facts();
            two.spawns.put(PLAINS + "/monster",
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:drowned", 100)));
            two.spawns.put(PLAINS + "/water_creature",
                    List.of(spawn(SpawnCategory.WATER_CREATURE, "minecraft:drowned", 5)));
            Resolution r = resolver(two, tiers(), biomes(biome(PLAINS, null, 2))).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).size() == 1
                    && r.mobs(SpawnCategory.WATER_CREATURE).size() == 1;
        });

        // 11 — a later layer updates rather than duplicates
        SelfTestHarness.check(lines, tally, "11 later layer, same entity -> weight replaced", () -> {
            BiomeConfig config = biomeWithMobs(PLAINS, null, SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:zombie", 5)));
            config = withLevel(config, 2);
            Resolution r = resolver(facts, tiers(), biomes(config)).resolve(PLAINS);
            List<Spawn> monsters = r.mobs(SpawnCategory.MONSTER);
            return monsters.size() == 2 && monsters.stream()
                    .anyMatch(s -> s.entity().equals("minecraft:zombie") && s.weight() == 5);
        });

        // 15 / 16 — scaling curve formula (pinned values: 5->0.5, 1->0.5, 3->0.0, 7->1.5, 2.0/2.0 at level 4
        // from start 1 -> 32.0)
        SelfTestHarness.check(lines, tally, "15 start_level 5 at level 5 -> level-1 bonus",
                () -> AttributeCurve.bonus(0.5, 1.0, 5, 5) == 0.5
                        && AttributeCurve.bonus(0.5, 1.0, 1, 1) == 0.5);
        SelfTestHarness.check(lines, tally, "16 before start -> zero; exponent curve concrete",
                () -> AttributeCurve.bonus(0.5, 1.0, 3, 5) == 0.0
                        && AttributeCurve.bonus(2.0, 2.0, 4, 1) == 32.0);

        // 17 — ore base is the game's own ores
        SelfTestHarness.check(lines, tally, "17 no tier, biome level -> ore base is the game's", () -> {
            Resolution r = resolver(facts, tiers(), biomes(biome(PLAINS, null, 2))).resolve(PLAINS);
            return r.ores().size() == 3 && r.ores().contains("iron");
        });

        // 18 — ore deny removes from the game's list
        SelfTestHarness.check(lines, tally, "18 biome denies an ore -> removed from the game's list", () -> {
            BiomeConfig config = new BiomeConfig(PLAINS, null, 2, null, null, null,
                    new EnumMap<>(SpawnCategory.class), List.of(OreRule.deny("copper")),
                    List.of(), List.of(), null);
            Resolution r = resolver(facts, tiers(), biomes(config)).resolve(PLAINS);
            return r.ores().size() == 2 && !r.ores().contains("copper");
        });

        // 29 — frequency_pct from a tier ore rule lands in the resolution map
        SelfTestHarness.check(lines, tally, "29 tier frequency_pct -> oreFrequencies", () -> {
            Tier tier = new Tier("ORE", 2, OverrideMode.ADD, 1.0,
                    new EnumMap<>(SpawnCategory.class),
                    List.of(OreRule.add("iron", 50)));
            Resolution r = resolver(facts, tiers(tier),
                    biomes(biome(PLAINS, "ORE", null))).resolve(PLAINS);
            return r.ores().contains("iron")
                    && r.oreFrequencies().getOrDefault("iron", -1.0) == 50.0;
        });

        // 30 — biome frequency beats the tier's on the same ore (later layer wins)
        SelfTestHarness.check(lines, tally, "30 biome frequency beats tier -> most specific wins", () -> {
            Tier tier = new Tier("ORE", 2, OverrideMode.ADD, 1.0,
                    new EnumMap<>(SpawnCategory.class),
                    List.of(OreRule.add("iron", 50)));
            BiomeConfig config = new BiomeConfig(PLAINS, "ORE", null, null, null, null,
                    new EnumMap<>(SpawnCategory.class),
                    List.of(OreRule.add("iron", 25)),
                    List.of(), List.of(), null);
            Resolution r = resolver(facts, tiers(tier), biomes(config)).resolve(PLAINS);
            return r.oreFrequencies().getOrDefault("iron", -1.0) == 25.0;
        });

        // 31 — a deny strips the ore from the frequency map as well as the list
        SelfTestHarness.check(lines, tally, "31 deny drops ore from frequency map", () -> {
            BiomeConfig config = new BiomeConfig(PLAINS, null, 2, null, null, null,
                    new EnumMap<>(SpawnCategory.class), List.of(OreRule.deny("copper")),
                    List.of(), List.of(), null);
            Resolution r = resolver(facts, tiers(), biomes(config)).resolve(PLAINS);
            return !r.oreFrequencies().containsKey("copper")
                    && !r.ores().contains("copper");
        });

        // 32 — unmentioned ores stay absent from the map (normal density at worldgen)
        SelfTestHarness.check(lines, tally, "32 unmentioned ores absent from frequency map", () -> {
            Resolution r = resolver(facts, tiers(),
                    biomes(biome(PLAINS, null, 2))).resolve(PLAINS);
            return r.oreFrequencies().isEmpty() && r.ores().size() == 3;
        });

        // ---- variants as spawn entries ----

        // 19 — variant spawn entry appears in roster
        SelfTestHarness.check(lines, tally, "19 variant in tier spawn list -> appears in resolution", () -> {
            Facts local = new Facts();
            local.spawns.put(FOREST + "/monster",
                    List.of(spawn(SpawnCategory.MONSTER, CREEPER, 100)));
            Tier tier = tierWithMobs("FOREST", 3, SpawnCategory.MONSTER, List.of(
                    spawn(SpawnCategory.MONSTER, CREEPER, 100),
                    variantSpawn(SpawnCategory.MONSTER, CREEPER, "forest_creeper", 80)));
            Map<String, Variant> vars = variants(Variant.of("forest_creeper", CREEPER));
            Resolution r = resolver(local, tiers(tier),
                    biomes(biome(FOREST, "FOREST", null)), vars).resolve(FOREST);
            return r.mobs(SpawnCategory.MONSTER).size() == 2
                    && r.mobs(SpawnCategory.MONSTER).stream().anyMatch(Spawn::isVariant);
        });

        // 20 — disabled spawn entry is excluded from resolved list
        SelfTestHarness.check(lines, tally, "20 disabled spawn entry -> excluded from resolution", () -> {
            Facts local = new Facts();
            local.spawns.put(FOREST + "/monster",
                    List.of(spawn(SpawnCategory.MONSTER, CREEPER, 100)));
            Tier tier = tierWithMobs("FOREST", 3, SpawnCategory.MONSTER, List.of(
                    disabledSpawn(SpawnCategory.MONSTER, CREEPER, 100),
                    variantSpawn(SpawnCategory.MONSTER, CREEPER, "forest_creeper", 80)));
            Map<String, Variant> vars = variants(Variant.of("forest_creeper", CREEPER));
            Resolution r = resolver(local, tiers(tier),
                    biomes(biome(FOREST, "FOREST", null)), vars).resolve(FOREST);
            return r.mobs(SpawnCategory.MONSTER).size() == 1
                    && r.mobs(SpawnCategory.MONSTER).get(0).isVariant();
        });

        // ---- coefficient layering ----

        // 21 — global coefficient
        SelfTestHarness.check(lines, tally, "21 global coefficient -> used when no tier/biome override", () -> {
            Resolution r = resolver(facts, tiers(), biomes(biome(PLAINS, null, 2)),
                    Map.of(), List.of(), 1.5).resolve(PLAINS);
            return r.coefficient() == 1.5;
        });

        // 22 — tier coefficient beats global
        SelfTestHarness.check(lines, tally, "22 tier coefficient -> beats global", () -> {
            Tier tier = new Tier("TEMPERATE", 2, OverrideMode.ADD, 1.3,
                    new EnumMap<>(SpawnCategory.class), List.of());
            Resolution r = resolver(facts, tiers(tier),
                    biomes(biome(PLAINS, "TEMPERATE", null)),
                    Map.of(), List.of(), 1.5).resolve(PLAINS);
            return r.coefficient() == 1.3;
        });

        // 23 — biome coefficient beats tier
        SelfTestHarness.check(lines, tally, "23 biome coefficient -> beats tier", () -> {
            Tier tier = new Tier("TEMPERATE", 2, OverrideMode.ADD, 1.3,
                    new EnumMap<>(SpawnCategory.class), List.of());
            BiomeConfig config = new BiomeConfig(PLAINS, "TEMPERATE", null,
                    null, null, null, new EnumMap<>(SpawnCategory.class), List.of(),
                    List.of(), List.of(), 2.0);
            Resolution r = resolver(facts, tiers(tier), biomes(config),
                    Map.of(), List.of(), 1.5).resolve(PLAINS);
            return r.coefficient() == 2.0;
        });

        // ---- global spawn layer ----

        // 26 — global spawns appear above the game roster
        SelfTestHarness.check(lines, tally, "26 global spawns appear above the game roster", () -> {
            List<Spawn> globals = List.of(
                    spawn(SpawnCategory.MONSTER, "minecraft:stray", 10),
                    spawn(SpawnCategory.MONSTER, "minecraft:zombie", 5));
            Resolution r = resolver(facts, tiers(), biomes(biome(PLAINS, null, 2)),
                    Map.of(), List.of(), 1.0, globalSpawns(globals)).resolve(PLAINS);
            List<Spawn> monsters = r.mobs(SpawnCategory.MONSTER);
            return monsters.size() == 3  // zombie+skeleton+stray
                    && monsters.stream().anyMatch(s -> s.entity().equals("minecraft:stray"));
        });

        // 27 — a REPLACE tier drops global spawns too
        SelfTestHarness.check(lines, tally, "27 a REPLACE tier keeps only its own and its biomes' spawns", () -> {
            Tier tier = new Tier("HARSH", 2, OverrideMode.REPLACE, 1.0,
                    new EnumMap<>(SpawnCategory.class) {{
                        put(SpawnCategory.MONSTER, List.of(
                                spawn(SpawnCategory.MONSTER, "minecraft:husk", 50)));
                    }}, List.of());
            List<Spawn> globals = List.of(
                    spawn(SpawnCategory.MONSTER, "minecraft:stray", 10));
            Resolution r = resolver(facts, tiers(tier),
                    biomes(biome(PLAINS, "HARSH", null)),
                    Map.of(), List.of(), 1.0, globalSpawns(globals)).resolve(PLAINS);
            List<Spawn> monsters = r.mobs(SpawnCategory.MONSTER);
            return monsters.size() == 1
                    && monsters.getFirst().entity().equals("minecraft:husk");
        });

        // 28 — tier beats global on the same entity
        SelfTestHarness.check(lines, tally, "28 tier entry beats global on same entity", () -> {
            Tier tier = tierWithMobs("TEMPERATE", 2, SpawnCategory.MONSTER,
                    List.of(spawn(SpawnCategory.MONSTER, "minecraft:zombie", 50)));
            List<Spawn> globals = List.of(
                    spawn(SpawnCategory.MONSTER, "minecraft:zombie", 5));
            Resolution r = resolver(facts, tiers(tier),
                    biomes(biome(PLAINS, "TEMPERATE", null)),
                    Map.of(), List.of(), 1.0, globalSpawns(globals)).resolve(PLAINS);
            return r.mobs(SpawnCategory.MONSTER).stream()
                    .filter(s -> s.entity().equals("minecraft:zombie"))
                    .findFirst().map(s -> s.weight() == 50
                            && s.source().layer() == Source.Layer.TIER)
                    .orElse(false);
        });

        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }

    // ---- fixture helpers ----

    private static Resolver resolver(Resolver.GameFacts facts) {
        return new Resolver(facts, Map.of(), Map.of(), Map.of(), List.of(), Map.of(), 1.0);
    }

    private static Resolver resolver(Resolver.GameFacts facts, Map<String, Tier> tiers,
                                     Map<String, BiomeConfig> biomes) {
        return new Resolver(facts, tiers, biomes, Map.of(), List.of(), Map.of(), 1.0);
    }

    private static Resolver resolver(Resolver.GameFacts facts, Map<String, Tier> tiers,
                                     Map<String, BiomeConfig> biomes,
                                     Map<String, Variant> variants) {
        return new Resolver(facts, tiers, biomes, variants, List.of(), Map.of(), 1.0);
    }

    private static Resolver resolver(Resolver.GameFacts facts, Map<String, Tier> tiers,
                                     Map<String, BiomeConfig> biomes,
                                     Map<String, Variant> variants,
                                     List<MobLoot> globalLoot,
                                     double globalCoefficient) {
        return new Resolver(facts, tiers, biomes, variants, globalLoot, Map.of(), globalCoefficient);
    }

    private static Resolver resolver(Resolver.GameFacts facts, Map<String, Tier> tiers,
                                     Map<String, BiomeConfig> biomes,
                                     Map<String, Variant> variants,
                                     List<MobLoot> globalLoot,
                                     double globalCoefficient,
                                     Map<SpawnCategory, List<Spawn>> globalSpawns) {
        return new Resolver(facts, tiers, biomes, variants, globalLoot, Map.of(),
                globalCoefficient, globalSpawns);
    }

    private static Map<SpawnCategory, List<Spawn>> globalSpawns(List<Spawn> entries) {
        Map<SpawnCategory, List<Spawn>> out = new EnumMap<>(SpawnCategory.class);
        for (Spawn spawn : entries) {
            out.computeIfAbsent(spawn.category(), k -> new ArrayList<>()).add(spawn);
        }
        return out;
    }

    private static Map<String, Tier> tiers(Tier... entries) {
        Map<String, Tier> out = new LinkedHashMap<>();
        for (Tier tier : entries) {
            out.put(tier.name(), tier);
        }
        return out;
    }

    private static Map<String, BiomeConfig> biomes(BiomeConfig... entries) {
        Map<String, BiomeConfig> out = new LinkedHashMap<>();
        for (BiomeConfig config : entries) {
            out.put(config.biome(), config);
        }
        return out;
    }

    private static Map<String, Variant> variants(Variant... entries) {
        Map<String, Variant> out = new LinkedHashMap<>();
        for (Variant v : entries) {
            out.put(v.id(), v);
        }
        return out;
    }

    private static BiomeConfig biome(String id, String tier, Integer level) {
        return new BiomeConfig(id, tier, level, null, null, null,
                new EnumMap<>(SpawnCategory.class), List.of(),
                List.of(), List.of(), null);
    }

    private static BiomeConfig withLevel(BiomeConfig c, int level) {
        return new BiomeConfig(c.biome(), c.tier(), level, c.scaleMobs(),
                c.restrictOres(), c.substituteSpawns(), c.mobs(), c.ores(),
                c.loot(), c.spawnRules(), c.coefficient());
    }

    private static Tier tierWithMobs(String name, int level, SpawnCategory category,
                                     List<Spawn> rules) {
        Map<SpawnCategory, List<Spawn>> mobs = new EnumMap<>(SpawnCategory.class);
        mobs.put(category, rules);
        return new Tier(name, level, OverrideMode.ADD, 1.0, mobs, List.of());
    }

    private static BiomeConfig biomeWithMobs(String id, String tier, SpawnCategory category,
                                             List<Spawn> rules) {
        Map<SpawnCategory, List<Spawn>> mobs = new EnumMap<>(SpawnCategory.class);
        mobs.put(category, rules);
        return new BiomeConfig(id, tier, null, null, null, null, mobs, List.of(),
                List.of(), List.of(), null);
    }

}
