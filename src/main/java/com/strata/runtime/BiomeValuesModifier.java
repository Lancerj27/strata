package com.strata.runtime;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Map;

/**
 * Puts the user's biome values over the game's: a biome's creature probability, and the charge and energy
 * budget of each spawn cost.
 */
public record BiomeValuesModifier() implements BiomeModifier {

    public static final BiomeValuesModifier INSTANCE = new BiomeValuesModifier();

    public static final MapCodec<BiomeValuesModifier> CODEC = MapCodec.unit(INSTANCE);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<BiomeValuesModifier>>
            BIOME_VALUES = OreRestrictionModifier.BIOME_MODIFIERS.register("biome_values", () -> CODEC);

    /** Loads the class during mod construction, so the serializer registers in time. */
    public static void init() {
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        // AFTER_EVERYTHING: every other mod's biome modifiers have run, so their spawns, values and features
        // are here to be read and changed, and Strata's change is the last word.
        if (phase != Phase.AFTER_EVERYTHING) {
            return;
        }
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse("");
        Map<String, String> own = BiomeValues.edits().get(id);
        if (own == null) {
            return;
        }
        var spawns = builder.getMobSpawnSettings();
        Double probability = BiomeValues.number(own.get(BiomeValues.BIOME + "|" + BiomeValues.PROBABILITY), null);
        if (probability != null && probability < 1) {
            spawns.creatureGenerationProbability(probability.floatValue());
        }
        for (String key : own.keySet()) {
            String[] parts = key.split("\\|");
            if (parts.length != 3 || !BiomeValues.COST.equals(parts[0])) {
                continue;
            }
            ResourceLocation typeId = ResourceLocation.tryParse(parts[1]);
            if (typeId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)) {
                continue;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
            MobSpawnSettings.MobSpawnCost game = spawns.getCost(type);
            double[] cost = BiomeValues.cost(own, parts[1], game == null ? null : game.charge(),
                    game == null ? null : game.energyBudget());
            if (cost != null) {
                spawns.addMobCharge(type, cost[0], cost[1]);
            }
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return BIOME_VALUES.get();
    }
}
