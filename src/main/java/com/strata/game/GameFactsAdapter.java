package com.strata.game;

import com.strata.model.Resolver;
import com.strata.model.Source;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;

/** The seam between the game and the model. */
public final class GameFactsAdapter implements Resolver.GameFacts {

    private final RegistryAccess registries;

    public GameFactsAdapter(RegistryAccess registries) {
        this.registries = registries;
    }

    @Override
    public List<Spawn> spawns(String biome, SpawnCategory category) {
        MobCategory vanilla = toVanilla(category);
        if (vanilla == null) {
            return List.of();
        }
        ResourceLocation id = ResourceLocation.tryParse(biome);
        if (id == null) {
            return List.of();
        }

        List<Spawn> out = new ArrayList<>();
        for (GameData.SpawnEntry entry : GameData.mobsIn(registries, id)) {
            if (entry.category() != vanilla) {
                continue;
            }
            // THE GAME'S GROUP SIZE.
            out.add(Spawn.of(category, entry.entity().toString(), entry.weight(),
                    entry.minCount(), entry.maxCount(), Source.GAME));
        }
        return out;
    }

    @Override
    public List<String> ores(String biome) {
        ResourceLocation id = ResourceLocation.tryParse(biome);
        return id == null ? List.of() : GameData.oresIn(registries, id);
    }

    /** Null when the model has no equivalent, which is only MISC. */
    public static MobCategory toVanilla(SpawnCategory category) {
        return switch (category) {
            case MONSTER -> MobCategory.MONSTER;
            case CREATURE -> MobCategory.CREATURE;
            case AMBIENT -> MobCategory.AMBIENT;
            case AXOLOTLS -> MobCategory.AXOLOTLS;
            case UNDERGROUND_WATER_CREATURE -> MobCategory.UNDERGROUND_WATER_CREATURE;
            case WATER_CREATURE -> MobCategory.WATER_CREATURE;
            case WATER_AMBIENT -> MobCategory.WATER_AMBIENT;
        };
    }
}
