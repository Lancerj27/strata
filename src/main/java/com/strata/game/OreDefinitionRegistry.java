package com.strata.game;

import com.strata.Strata;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Registers the strata:ore_definition datapack registry — the modded-ore override mechanism (see
 * OreDefinitionEntry).
 */
public final class OreDefinitionRegistry {

    private OreDefinitionRegistry() {}

    public static final ResourceKey<Registry<OreDefinitionEntry>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID, "ore_definition"));

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY_KEY, OreDefinitionEntry.CODEC);
    }
}
