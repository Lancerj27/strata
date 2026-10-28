package com.strata.runtime;

import com.strata.game.OreDetector;
import com.strata.game.OreDefinitionRegistry;
import com.strata.game.OreDefinitionEntry;
import com.strata.Strata;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;


/**
 * Reads the strata:ore_definition datapack registry into memory on world load, so a datapack can teach Strata
 * about a modded ore by name.
 */
public final class OreData {

    private OreData() {}

    /** The registries the data pack ore definitions were last read from. */
    private static volatile net.minecraft.core.RegistryAccess definitionsFrom;

    /**
     * Reads the data pack ore definitions (strata:ore_definition) from these registries, once per registries.
     *
     * @return how many definitions were read, or -1 when the registry is absent
     */
    public static int registerDefinitions(net.minecraft.core.RegistryAccess access) {
        if (access == null) {
            return -1;
        }
        synchronized (OreData.class) {
            // Clear first: an ore definition removed from the pack must go, not linger from the previous
            // load.
            OreDetector.clearModdedOres();
            definitionsFrom = access;
            var definitionsOpt = access.lookup(OreDefinitionRegistry.REGISTRY_KEY);
            if (definitionsOpt.isEmpty()) {
                Strata.LOGGER.warn("OreDefinitionRegistry not present in world registry access — skipping ore detection");
                return -1;
            }
            HolderLookup.RegistryLookup<OreDefinitionEntry> definitions = definitionsOpt.get();
            int[] definitionCount = {0};
            definitions.listElements().forEach(holder -> {
                OreDefinitionEntry entry = holder.value();
                if (entry.placedFeatures().isEmpty()) {
                    Strata.LOGGER.warn("strata:ore_definition {} lists no placed_features — ignoring",
                            holder.key().location());
                    return;
                }
                OreDetector.register(entry.oreName(), entry.placedFeatures());
                definitionCount[0]++;
            });
            return definitionCount[0];
        }
    }

    /** {@link #registerDefinitions} unless these registries were read already. */
    public static void ensureDefinitions(net.minecraft.core.RegistryAccess access) {
        if (access != null && access != definitionsFrom) {
            registerDefinitions(access);
        }
    }

    /** Rebuilds ore state from the live registry. */
    public static void buildIndex(ServerLevel level) {
        int count = registerDefinitions(level.registryAccess());
        if (count < 0) {
            return;
        }

        // Every ore from the world's own features and bound c:ores tag: the world's data packs may
        // add ores.
        OreDetector.detect(level.registryAccess(), block -> block.builtInRegistryHolder()
                .is(net.neoforged.neoforge.common.Tags.Blocks.ORES));
        Regeneration.resetOreBlocks();

        validateOreNames();

        Strata.LOGGER.info("Loaded {} strata:ore_definition entries and the game's ores", count);
    }

    /** Names ore rules that point at ores nothing defines. */
    private static void validateOreNames() {
        com.strata.data.Config.tables().tiers().forEach((name, tier) ->
                tier.ores().forEach(rule -> warnIfUnknown("tier " + name, rule.oreId())));
        com.strata.data.Config.tables().biomes().forEach((biome, config) ->
                config.ores().forEach(rule -> warnIfUnknown(biome, rule.oreId())));
    }

    private static void warnIfUnknown(String owner, String oreName) {
        if (com.strata.game.OreDetector.placedFeaturesFor(oreName).isEmpty()) {
            Strata.LOGGER.warn("{} names unknown ore '{}' - known ores: {}",
                    owner, oreName, com.strata.game.OreDetector.knownOreNames());
        }
    }

}
