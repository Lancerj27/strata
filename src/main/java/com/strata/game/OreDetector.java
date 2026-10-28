package com.strata.game;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.strata.Strata;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Maps an ore NAME (the short string used in tier/biome ore tables: "iron", "nether_quartz") to the placed
 * features that generate it, READ FROM THE GAME.
 */
public final class OreDetector {

    private OreDetector() {}

    /** An ore block's ore, and what it replaced: "block:<id>" or "tag:<id>". */
    public record OreBlock(String ore, String host) {
    }

    private static volatile Map<String, List<ResourceLocation>> detected = Map.of();
    private static volatile Map<ResourceLocation, OreBlock> blocks = Map.of();
    /** Runtime-registered definitions (strata:ore_definition), over the detected ones. */
    private static volatile Map<String, List<ResourceLocation>> definitions = Map.of();

    /** Reads every ore from the game's placed features. */
    public static void detect(RegistryAccess registries, Predicate<Block> isOre) {
        Map<String, List<ResourceLocation>> byName = new LinkedHashMap<>();
        Map<ResourceLocation, OreBlock> byBlock = new LinkedHashMap<>();
        try {
            registries.registryOrThrow(Registries.PLACED_FEATURE).holders().forEach(placed -> {
                if (!(placed.value().feature().value().config() instanceof OreConfiguration ore)) {
                    return;
                }
                for (OreConfiguration.TargetBlockState target : ore.targetStates) {
                    Block block = target.state.getBlock();
                    ResourceLocation id = BuiltInRegistries.BLOCK.getResourceKey(block)
                            .map(k -> k.location()).orElse(null);
                    if (id == null || !isOre.test(block)) {
                        continue;
                    }
                    String name = oreName(id);
                    List<ResourceLocation> features = byName.computeIfAbsent(name, k -> new ArrayList<>());
                    if (!features.contains(placed.key().location())) {
                        features.add(placed.key().location());
                    }
                    byBlock.putIfAbsent(id, new OreBlock(name, host(target.target, registries)));
                }
            });
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Ore detection failed ({}): no ores are managed", e.toString());
        }
        Map<String, List<ResourceLocation>> frozen = new LinkedHashMap<>();
        byName.forEach((name, features) -> frozen.put(name, List.copyOf(features)));
        detected = Map.copyOf(frozen);
        blocks = Map.copyOf(byBlock);
        Strata.LOGGER.info("Ores read from the game: {}", new TreeSet<>(detected.keySet()));
    }

    /** An ore block's ore name: iron_ore and deepslate_iron_ore are "iron". */
    public static String oreName(ResourceLocation block) {
        String path = block.getPath();
        if (path.startsWith("deepslate_")) {
            path = path.substring("deepslate_".length());
        }
        if (path.endsWith("_ore")) {
            path = path.substring(0, path.length() - "_ore".length());
        }
        return path;
    }

    /** What an ore's target rule replaces, as "block:<id>" or "tag:<id>", or "" when it is neither. */
    private static String host(RuleTest rule, RegistryAccess registries) {
        return RuleTest.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), rule)
                .result().filter(e -> e.isJsonObject()).map(e -> hostOf(e.getAsJsonObject())).orElse("");
    }

    /** "block:<id>" for a block-match rule, "tag:<id>" for a tag-match rule, else "". */
    public static String hostOf(JsonObject rule) {
        if (rule.has("block")) {
            return "block:" + rule.get("block").getAsString();
        }
        if (rule.has("tag")) {
            return "tag:" + rule.get("tag").getAsString();
        }
        return "";
    }

    /** Registers or replaces a defined ore's placed-feature list (strata:ore_definition). */
    public static void register(String oreName, List<ResourceLocation> placedFeatures) {
        Map<String, List<ResourceLocation>> updated = new HashMap<>(definitions);
        updated.put(oreName, List.copyOf(placedFeatures));
        definitions = Map.copyOf(updated);
    }

    /** Clears the definitions. */
    public static void clearModdedOres() {
        definitions = Map.of();
    }

    /** The placed-feature IDs for an ore name: a definition first, then the detected. */
    public static Optional<List<ResourceLocation>> placedFeaturesFor(String oreName) {
        List<ResourceLocation> defined = definitions.get(oreName);
        return defined != null ? Optional.of(defined) : Optional.ofNullable(detected.get(oreName));
    }

    /** Every detected ore block, to its ore and what it replaced. */
    public static Map<ResourceLocation, OreBlock> oreBlocks() {
        return blocks;
    }

    /** Every placed feature Strata is allowed to remove. */
    public static Set<ResourceLocation> allManagedFeatures() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (String ore : knownOreNames()) {
            placedFeaturesFor(ore).ifPresent(out::addAll);
        }
        return out;
    }

    /** Every known ore name: detected and defined. */
    public static Set<String> knownOreNames() {
        Set<String> all = new TreeSet<>(detected.keySet());
        all.addAll(definitions.keySet());
        return Set.copyOf(all);
    }
}
