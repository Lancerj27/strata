package com.strata.gametest;

import com.google.gson.JsonParser;
import com.strata.data.JsonOverrides;
import com.strata.domain.OverrideOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * THE OVERRIDE SHEET OVER THE REAL GAME: every recipe is read from the live resources, a furnace
 * recipe among them, and an override of it is written into Strata's pack with its field set and a false
 * condition when off.
 */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class OverrideGameTests {

    private OverrideGameTests() {
    }

    private static final String RECIPE = "minecraft:iron_ingot_from_smelting_raw_iron";

    @GameTest(template = "arena", timeoutTicks = 40, batch = "overrides")
    public static void recipesAreReadAndOverridden(GameTestHelper helper) throws Exception {
        JsonOverrides.extract(helper.getLevel().getServer().getResourceManager());
        var game = JsonOverrides.game(JsonOverrides.RECIPE);
        helper.assertTrue(game.size() > 500, "Only " + game.size() + " recipes were read");
        helper.assertTrue(game.containsKey(RECIPE), "The furnace's raw iron recipe was not read");
        // The Type filter went into the search: "smelting" finds the furnace's recipes by their type.
        helper.assertTrue(OverrideOps.entries(JsonOverrides.RECIPE, "smelting (furnace)").stream()
                        .anyMatch(e -> e.id().equals(RECIPE)), "A search for the smelting type misses the furnace's recipe");

        Path tmp = Files.createTempDirectory("strata-overrides");
        try {
            JsonOverrides.store(JsonOverrides.RECIPE, RECIPE, new JsonOverrides.Override(
                    java.util.Map.of("cookingtime", "40"), true));
            helper.assertTrue(JsonOverrides.writePack(tmp, java.util.Set.of()) >= 1, "No override was written");
            Path file = tmp.resolve("minecraft").resolve("recipe").resolve("iron_ingot_from_smelting_raw_iron.json");
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            helper.assertTrue(json.get("cookingtime").getAsInt() == 40, "The cook time was not set");
            helper.assertTrue(json.has("neoforge:conditions"), "An off recipe has no false condition");
        } finally {
            JsonOverrides.store(JsonOverrides.RECIPE, RECIPE, JsonOverrides.Override.NONE);
        }
        helper.succeed();
    }

    /**
     * WORLD GENERATION OVERRIDES LOAD: each written structure, set and feature is decoded by the game's own
     * codec against the live registries - what the world does as it loads them.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "overrides_worldgen")
    public static void worldgenOverridesDecode(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        JsonOverrides.extract(server.getResourceManager());
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        record Case(JsonOverrides.Kind kind, String id, JsonOverrides.Override o,
                    com.mojang.serialization.Codec<?> codec) {
        }
        var cases = java.util.List.of(
                new Case(JsonOverrides.STRUCTURE, "minecraft:village_plains", new JsonOverrides.Override(java.util.Map.of(), true),
                        net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC),
                new Case(JsonOverrides.STRUCTURE_SET, "minecraft:villages", new JsonOverrides.Override(
                        java.util.Map.of("placement.spacing", "40", "placement.separation", "12"), false),
                        net.minecraft.world.level.levelgen.structure.StructureSet.DIRECT_CODEC),
                new Case(JsonOverrides.PLACED_FEATURE, "minecraft:ore_gold", new JsonOverrides.Override(java.util.Map.of(), true),
                        net.minecraft.world.level.levelgen.placement.PlacedFeature.DIRECT_CODEC),
                new Case(JsonOverrides.CONFIGURED_FEATURE, "minecraft:ore_iron", new JsonOverrides.Override(
                        java.util.Map.of("config.size", "20"), false),
                        net.minecraft.world.level.levelgen.feature.ConfiguredFeature.DIRECT_CODEC),
                new Case(JsonOverrides.LOOT_TABLE, "minecraft:chests/simple_dungeon", new JsonOverrides.Override(java.util.Map.of(), true),
                        net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC),
                new Case(JsonOverrides.ADVANCEMENT, "minecraft:story/mine_diamond", new JsonOverrides.Override(java.util.Map.of(), true),
                        net.minecraft.advancements.Advancement.CODEC),
                new Case(JsonOverrides.ITEM_TAG, "minecraft:logs", new JsonOverrides.Override(java.util.Map.of(), true),
                        net.minecraft.tags.TagFile.CODEC),
                new Case(JsonOverrides.ENCHANTMENT, "minecraft:sharpness", new JsonOverrides.Override(
                        java.util.Map.of("max_level", "10"), false),
                        net.minecraft.world.item.enchantment.Enchantment.DIRECT_CODEC),
                new Case(JsonOverrides.BIOME, "minecraft:plains", new JsonOverrides.Override(java.util.Map.of("temperature", "1.5"), false,
                        com.strata.model.JsonPaths.remove(JsonOverrides.game(JsonOverrides.BIOME).get("minecraft:plains"), "carvers[0]")),
                        net.minecraft.world.level.biome.Biome.DIRECT_CODEC),
                new Case(JsonOverrides.STRUCTURE, "minecraft:swamp_hut", new JsonOverrides.Override(java.util.Map.of(), false,
                        com.strata.model.JsonPaths.duplicate(JsonOverrides.game(JsonOverrides.STRUCTURE).get("minecraft:swamp_hut"),
                                "spawn_overrides.monster.spawns[0]", "")),
                        net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC));
        for (Case c : cases) {
            var game = JsonOverrides.game(c.kind()).get(c.id());
            helper.assertTrue(JsonOverrides.game(c.kind()).size() > 0, c.kind().id() + ": nothing was read");
            helper.assertTrue(game != null, c.id() + " was not read");
            var json = JsonOverrides.applied(c.kind(), game, c.o());
            var result = c.codec().parse(ops, json);
            helper.assertTrue(result.error().isEmpty(), c.id() + " does not load: "
                    + result.error().map(e -> e.message()).orElse(""));
        }
        helper.succeed();
    }

    /**
     * EVERY OTHER KIND: each new tag, the other game data and every world
     * generation kind is read from the live game, and each entry it read, overridden as the sheet writes it,
     * is read back by the game's own codec for its registry (a tag by the tag codec).
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "overrides_more")
    public static void everyOtherKindIsReadAndLoads(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        JsonOverrides.extract(server.getResourceManager());
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        java.util.Map<String, com.mojang.serialization.Codec<?>> codecs = new java.util.HashMap<>();
        net.neoforged.neoforge.registries.DataPackRegistriesHooks.getDataPackRegistriesWithDimensions()
                .forEach(d -> codecs.put(d.key().location().getPath(), d.elementCodec()));
        java.util.List<JsonOverrides.Kind> kinds = new java.util.ArrayList<>(JsonOverrides.MORE_TAGS);
        kinds.addAll(JsonOverrides.OTHER_DATA);
        kinds.addAll(JsonOverrides.WORLD_GENERATION);
        java.util.Map<String, java.util.Map<String, String>> changes = java.util.Map.of(
                "minecraft:overworld", java.util.Map.of("ambient_light", "0.5"),
                "minecraft:kebab", java.util.Map.of("width", "3"));
        int checked = 0;
        for (JsonOverrides.Kind kind : kinds) {
            var game = JsonOverrides.game(kind);
            helper.assertTrue(!game.isEmpty() || kind == JsonOverrides.STRUCTURE_MODIFIER || kind == JsonOverrides.DIMENSION,
                    kind.id() + ": nothing was read");
            boolean tag = kind.merge() == JsonOverrides.Merge.TAG;
            String registry = kind.folder().startsWith("neoforge/") ? kind.folder().substring("neoforge/".length())
                    : kind.folder();
            com.mojang.serialization.Codec<?> codec = tag ? net.minecraft.tags.TagFile.CODEC : codecs.get(registry);
            helper.assertTrue(codec != null, kind.id() + ": no codec for " + registry);
            for (var entry : game.entrySet()) {
                var o = new JsonOverrides.Override(changes.getOrDefault(entry.getKey(), java.util.Map.of()), false);
                var json = JsonOverrides.applied(kind, entry.getValue(), o);
                var result = codec.parse(ops, json);
                helper.assertTrue(result.error().isEmpty(), kind.id() + " " + entry.getKey() + " does not load: "
                        + result.error().map(e -> e.message()).orElse(""));
                checked++;
            }
        }
        var settings = JsonOverrides.applied(JsonOverrides.NOISE_SETTINGS,
                JsonOverrides.game(JsonOverrides.NOISE_SETTINGS).get("minecraft:overworld"),
                new JsonOverrides.Override(java.util.Map.of("sea_level", "40"), false));
        var read = net.minecraft.world.level.levelgen.NoiseGeneratorSettings.DIRECT_CODEC.parse(ops, settings);
        helper.assertTrue(read.result().map(s -> s.seaLevel() == 40).orElse(false),
                "The overworld's sea level did not read back as 40");
        helper.assertTrue(checked > 300, "Only " + checked + " entries were checked");
        helper.succeed();
    }
}
