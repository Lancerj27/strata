package com.strata.game;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.data.GameSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every biome the editor can list, with its temperature band and dimension. */
public final class BiomeCatalog {

    private BiomeCatalog() {
    }

    /** Temperature bands, hottest first. */
    public static final List<String> BANDS = List.of("Hot", "Warm", "Temperate", "Cold", "Frozen");

    /** The band for a biome with no known temperature. */
    public static final String DEFAULT_BAND = "Temperate";

    /** One listable biome. */
    public record Entry(String id, String band, String dimension) {
    }

    /**
     * Every listable biome, ordered by band and then by id.
     *
     * @param worldRegistries the open world's registry access, or null at the title screen
     */
    public static List<Entry> all(RegistryAccess worldRegistries) {
        // Dimension membership is derived from world presets, which live in the WORLDGEN registries — the
        // startup pack-built access at the title screen, the server's once a world is bound.
        Map<String, String> dimensions = DimensionBiomes.byBiome(Config.boundRegistries());
        Map<String, String> bands = new LinkedHashMap<>();
        List<String> ids;
        if (worldRegistries != null) {
            ids = fromRegistry(worldRegistries, false, bands);
        } else if (Config.boundRegistries() != null) {
            ids = fromRegistry(Config.boundRegistries(), true, bands);
        } else {
            Set<String> all = new LinkedHashSet<>();
            all.addAll(GameSnapshot.get().keySet());
            all.addAll(Config.tables().biomes().keySet());
            ids = all.stream().sorted().toList();
        }
        RegistryAccess tagged = worldRegistries != null ? worldRegistries : Config.boundRegistries();
        List<Entry> out = new ArrayList<>();
        for (String id : ids) {
            String dimension = dimensions.get(id);
            if (dimension == null) {
                dimension = byTag(tagged, id);
            }
            out.add(new Entry(id, bands.getOrDefault(id, DEFAULT_BAND), dimension));
        }
        return out;
    }

    /**
     * A biome no world preset names - a mod's, added by its own generator - by the dimension tags the game
     * and mods give it (#minecraft:is_overworld, #is_nether, #is_end), else Other.
     */
    private static String byTag(RegistryAccess registries, String id) {
        if (registries == null) {
            return DimensionBiomes.UNCLAIMED;
        }
        try {
            Holder<Biome> holder = registries.lookupOrThrow(Registries.BIOME)
                    .get(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id))).orElse(null);
            if (holder == null) {
                return DimensionBiomes.UNCLAIMED;
            }
            if (holder.is(net.minecraft.tags.BiomeTags.IS_NETHER)) {
                return DimensionBiomes.labelFor(ResourceLocation.withDefaultNamespace("the_nether"));
            }
            if (holder.is(net.minecraft.tags.BiomeTags.IS_END)) {
                return DimensionBiomes.labelFor(ResourceLocation.withDefaultNamespace("the_end"));
            }
            if (holder.is(net.minecraft.tags.BiomeTags.IS_OVERWORLD)) {
                return DimensionBiomes.labelFor(ResourceLocation.withDefaultNamespace("overworld"));
            }
        } catch (RuntimeException e) {
            Strata.LOGGER.debug("No dimension tag for biome {}: {}", id, e.toString());
        }
        return DimensionBiomes.UNCLAIMED;
    }

    /** Biome ids from a registry, banded by temperature. */
    private static List<String> fromRegistry(RegistryAccess registries, boolean withSnapshot,
                                             Map<String, String> bands) {
        Map<String, List<String>> byBand = new LinkedHashMap<>();
        for (String band : BANDS) byBand.put(band, new ArrayList<>());

        var biomeReg = registries.registry(Registries.BIOME);
        if (biomeReg.isPresent()) {
            for (ResourceLocation key : biomeReg.get().keySet()) {
                String id = key.toString();
                String band = temperatureBand(rawTemperature(registries, key));
                bands.put(id, band);
                byBand.get(band).add(id);
            }
        }
        List<String> extra = new ArrayList<>(Config.tables().biomes().keySet());
        if (withSnapshot) {
            extra.addAll(GameSnapshot.get().keySet());
        }
        for (String id : extra) {
            if (!bands.containsKey(id)) {
                byBand.get(DEFAULT_BAND).add(id);
                bands.put(id, DEFAULT_BAND);
            }
        }
        List<String> ids = new ArrayList<>();
        for (String band : BANDS) {
            List<String> biomes = byBand.get(band);
            biomes.sort(Comparator.naturalOrder());
            ids.addAll(biomes);
        }
        return ids;
    }

    private static String temperatureBand(float temp) {
        if (temp >= 1.5f) return "Hot";
        if (temp >= 0.9f) return "Warm";
        if (temp >= 0.5f) return "Temperate";
        if (temp >= 0.2f) return "Cold";
        return "Frozen";
    }

    private static float rawTemperature(RegistryAccess registries, ResourceLocation biomeId) {
        try {
            HolderLookup.RegistryLookup<Biome> lookup = registries.lookupOrThrow(Registries.BIOME);
            Holder<Biome> holder = lookup.get(ResourceKey.create(Registries.BIOME, biomeId)).orElse(null);
            if (holder != null) return holder.value().getBaseTemperature();
        } catch (RuntimeException e) {
            // Narrowed from Exception: a missing biome registry is the case this guards, and a broad catch
            // would swallow an NPE or an IndexOutOfBounds from the lookup identically.
            Strata.LOGGER.debug("No temperature for biome {} - using the 0.5 default: {}",
                    biomeId, e.toString());
        }
        return 0.5f;
    }
}
