package com.strata.runtime;

import com.strata.game.OreDetector;
import com.strata.model.OreFrequency;
import com.strata.Strata;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** BiomeModifier that adds, removes, and thins ore features based on config. */
public record OreRestrictionModifier() implements BiomeModifier {

    public static final OreRestrictionModifier INSTANCE = new OreRestrictionModifier();

    public static final MapCodec<OreRestrictionModifier> CODEC = MapCodec.unit(INSTANCE);

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Strata.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<OreRestrictionModifier>>
            ORE_RESTRICTION = BIOME_MODIFIERS.register("ore_restriction", () -> CODEC);

    /** The managed ores each biome has in the GAME's own features, before Strata adds or removes any. */
    private static final Map<ResourceLocation, Set<String>> ORIGINAL_ORES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** The managed ores a biome had before Strata changed it. */
    public static Set<String> originalOres(ResourceLocation biome) {
        return ORIGINAL_ORES.getOrDefault(biome, Set.of());
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        // The data pack's ore definitions of THIS server, before the first biome is changed.
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            OreData.ensureDefinitions(server.registryAccess());
        }
        if (phase == Phase.ADD) {
            addAllowedOres(biome, builder);
            return;
        }
        // AFTER_EVERYTHING: every other mod's biome modifiers have run, so their spawns, values and features
        // are here to be read and changed, and Strata's change is the last word.
        if (phase != Phase.AFTER_EVERYTHING) {
            return;
        }

        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) {
            return;
        }

        OreConfig.AllowedOres allowedOres = OreConfig.allowedInWithFrequencies(biome);
        if (allowedOres == null) {
            return;
        }

        Map<ResourceLocation, Double> frequencyByFeature = new HashMap<>();
        for (String oreName : allowedOres.names()) {
            double pct = allowedOres.frequencies().getOrDefault(oreName, OreFrequency.FULL);
            OreDetector.placedFeaturesFor(oreName).ifPresent(ids -> {
                for (ResourceLocation id : ids) {
                    frequencyByFeature.put(id, pct);
                }
            });
        }

        Set<ResourceLocation> managed = OreDetector.allManagedFeatures();

        int removed = 0;
        int throttled = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            List<Holder<PlacedFeature>> features = builder.getGenerationSettings().getFeatures(step);
            for (int i = 0; i < features.size(); i++) {
                Holder<PlacedFeature> holder = features.get(i);
                ResourceLocation id = holder.unwrapKey()
                        .map(ResourceKey::location)
                        .orElse(null);
                if (id == null || !managed.contains(id)) {
                    continue;
                }
                Double pct = frequencyByFeature.get(id);
                if (pct == null) {
                    features.remove(i);
                    i--;
                    removed++;
                    continue;
                }
                if (pct < OreFrequency.FULL) {
                    features.set(i, throttledHolder(holder, OreFrequency.rarityFor(pct)));
                    throttled++;
                }
            }
        }

        if (removed > 0 || throttled > 0) {
            Strata.LOGGER.debug("Ore restriction: {} removed, {} throttled in {} (allows {})",
                    removed, throttled, key.location(), allowedOres.names());
        }
    }

    /**
     * ADD phase: records the biome's own managed ores, then adds the features of every allowed ore the biome
     * does not have.
     */
    private static void addAllowedOres(Holder<Biome> biome, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) {
            return;
        }
        Set<ResourceLocation> present = new java.util.HashSet<>();
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> holder : builder.getGenerationSettings().getFeatures(step)) {
                holder.unwrapKey().ifPresent(k -> present.add(k.location()));
            }
        }
        Set<String> original = new java.util.HashSet<>();
        for (String ore : OreDetector.knownOreNames()) {
            OreDetector.placedFeaturesFor(ore).ifPresent(ids -> {
                if (ids.stream().anyMatch(present::contains)) {
                    original.add(ore);
                }
            });
        }
        ORIGINAL_ORES.put(key.location(), Set.copyOf(original));

        OreConfig.AllowedOres allowedOres = OreConfig.allowedInWithFrequencies(biome);
        if (allowedOres == null) {
            return;
        }
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        var placed = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        for (String ore : allowedOres.names()) {
            if (original.contains(ore)) {
                continue;
            }
            OreDetector.placedFeaturesFor(ore).ifPresent(ids -> {
                for (ResourceLocation id : ids) {
                    placed.getHolder(ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, id))
                            .ifPresent(holder -> builder.getGenerationSettings()
                                    .addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, holder));
                }
            });
        }
    }

    /**
     * Re-wraps a kept feature with an extra RarityFilter appended after its existing placement modifiers.
     */
    private static Holder<PlacedFeature> throttledHolder(
            Holder<PlacedFeature> original, int rarity) {
        List<PlacementModifier> mods = new ArrayList<>(original.value().placement());
        mods.add(RarityFilter.onAverageOnceEvery(rarity));
        return Holder.direct(new PlacedFeature(original.value().feature(), mods));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ORE_RESTRICTION.get();
    }

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        BIOME_MODIFIERS.register(modEventBus);
    }
}
