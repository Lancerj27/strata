package com.strata.runtime;

import com.strata.data.Config;
import com.strata.data.StrataConfig;
import com.strata.model.Resolution;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Map;

/** Which ores a biome is allowed to generate, and at what density. */
public final class OreConfig {

    private OreConfig() {}

    /** The worldgen gate's answer for one biome: which ores, at what pct. */
    public record AllowedOres(List<String> names, Map<String, Double> frequencies) {}

    /**
     * The ore names + their frequency_pct for a biome, in one resolver call (the modifier needs both; two
     * calls would resolve twice).
     */
    public static AllowedOres allowedInWithFrequencies(Holder<Biome> biomeHolder) {
        // this runs from the worldgen biome modifier, which fires before the mod config file has been parsed
        // on an integrated-server start.
        if (!StrataConfig.oreRestrictionEnabled()) {
            return null;
        }
        ResourceKey<Biome> key = biomeHolder.unwrapKey().orElse(null);
        if (key == null) {
            return null;
        }
        // Ore generation is composition, not difficulty.
        Resolution resolution = Config.resolve(key.location());
        if (!resolution.allows(resolution.restrictOres(),
                StrataConfig.oreRestrictionEnabled())) {
            return null;
        }
        return resolution.configured()
                ? new AllowedOres(generating(resolution.ores(), resolution.oreFrequencies()),
                        resolution.oreFrequencies())
                : null;
    }

    /**
     * The allowed ores that generate at all: an ore at 0% is off (Defaults: "0 = disabled"), so the add,
     * remove, and regenerate passes treat it as not allowed.
     */
    public static List<String> generating(List<String> names, Map<String, Double> frequencies) {
        return names.stream()
                .filter(n -> frequencies.getOrDefault(n, com.strata.model.OreFrequency.FULL) > 0)
                .toList();
    }
}
