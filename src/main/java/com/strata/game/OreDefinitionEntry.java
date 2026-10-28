package com.strata.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** One datapack entry defining a modded ore, at data/<pack>/strata/ore_definition/<name>.json: */
public record OreDefinitionEntry(String oreName, List<ResourceLocation> placedFeatures) {

    public static final Codec<OreDefinitionEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ore_name").forGetter(OreDefinitionEntry::oreName),
            ResourceLocation.CODEC.listOf().fieldOf("placed_features").forGetter(OreDefinitionEntry::placedFeatures)
    ).apply(instance, OreDefinitionEntry::new));
}
