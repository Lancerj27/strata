package com.strata.game;

import com.strata.Strata;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Which dimension a biome belongs to. */
public final class DimensionBiomes {

    private DimensionBiomes() {}

    /** Bucket for biomes no dimension claims. */
    public static final String UNCLAIMED = "Other";

    /** Vanilla first, in world order, then modded dimensions, then Other. */
    private static final List<String> VANILLA_ORDER =
            List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

    /** biome id → dimension label, for every biome any known dimension can generate. */
    public static Map<String, String> byBiome(RegistryAccess registries) {
        Map<String, String> out = new LinkedHashMap<>();
        if (registries == null) {
            return out;
        }
        Optional<net.minecraft.core.Registry<WorldPreset>> presets =
                registries.registry(Registries.WORLD_PRESET);
        if (presets.isEmpty()) {
            Strata.LOGGER.debug("No world presets bound - biome dimensions unavailable");
            return out;
        }

        for (var preset : presets.get().entrySet()) {
            try {
                Map<ResourceKey<LevelStem>, LevelStem> dims =
                        preset.getValue().createWorldDimensions().dimensions();
                for (var dim : dims.entrySet()) {
                    String label = labelFor(dim.getKey().location());
                    for (Holder<Biome> biome : dim.getValue().generator()
                            .getBiomeSource().possibleBiomes()) {
                        biome.unwrapKey().ifPresent(key -> {
                            // FIRST dimension to claim a biome wins, and presets are visited in registry
                            // order with vanilla first, so a biome shared by a modded preset keeps its
                            // vanilla home.
                            out.putIfAbsent(key.location().toString(), label);
                        });
                    }
                }
            } catch (Exception e) {
                Strata.LOGGER.warn("Could not read dimensions from world preset {}: {}",
                        preset.getKey().location(), e.toString());
            }
        }
        return out;
    }

    /**
     * A dimension id as a heading: "Overworld", "The Nether", "The End", and "Namespace: Name" for a modded
     * dimension, since two mods can easily both ship a "twilight".
     */
    public static String labelFor(ResourceLocation dimensionId) {
        String pretty = titleCase(dimensionId.getPath().replace('_', ' '));
        if ("minecraft".equals(dimensionId.getNamespace())) {
            return pretty;
        }
        return titleCase(dimensionId.getNamespace().replace('_', ' ')) + ": " + pretty;
    }

    /**
     * Heading order: the three vanilla dimensions in world order, then every other dimension alphabetically,
     * then the unclaimed bucket last.
     */
    public static Comparator<String> headingOrder() {
        List<String> vanilla = VANILLA_ORDER.stream()
                .map(id -> labelFor(ResourceLocation.parse(id)))
                .toList();
        return (a, b) -> {
            int ia = rank(a, vanilla);
            int ib = rank(b, vanilla);
            if (ia != ib) {
                return Integer.compare(ia, ib);
            }
            return a.compareToIgnoreCase(b);
        };
    }

    private static int rank(String label, List<String> vanilla) {
        if (UNCLAIMED.equals(label)) {
            return 2;
        }
        int idx = vanilla.indexOf(label);
        return idx >= 0 ? -vanilla.size() + idx : 1;
    }

    private static String titleCase(String words) {
        StringBuilder sb = new StringBuilder();
        for (String word : words.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(sb.isEmpty() ? "" : " ")
                    .append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return sb.toString();
    }
}
