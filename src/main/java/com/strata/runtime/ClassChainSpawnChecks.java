package com.strata.runtime;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A SPAWN CHECK FOR A MOB THE GAME GAVE NONE. */
public class ClassChainSpawnChecks {

    /** A type's kind for its check, by its class chain, cached. */
    private static final Map<EntityType<?>, Kind> KINDS = new ConcurrentHashMap<>();

    /** True when a Strata row, not the game's list, spawns this type here (R9: the game's spawns stand). */
    private static boolean strataSpawns(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.core.BlockPos pos,
                                        EntityType<?> type) {
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        return !biome.isEmpty() && ReplaceSpawnRules.rowsFor(com.strata.data.Config.resolve(biome), type).stream()
                .anyMatch(s -> s.source().layer() != com.strata.model.Source.Layer.GAME);
    }

    /** The check a class chain gives. */
    public enum Kind { WATER, MONSTER, ANIMAL, MOB, NONE }

    /** The kind, from what the class chain holds. */
    public static Kind kind(boolean mob, boolean water, boolean monster, boolean animal) {
        if (!mob) return Kind.NONE;
        if (water) return Kind.WATER;
        if (monster) return Kind.MONSTER;
        if (animal) return Kind.ANIMAL;
        return Kind.MOB;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        EntityType<?> type = event.getEntityType();
        if ((spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION)
                || SpawnPlacements.hasPlacement(type) || event.getResult() != MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT
                || !strataSpawns(event.getLevel(), event.getPos(), type)) {
            return;
        }
        Kind kind = KINDS.computeIfAbsent(type, t -> {
            Entity sample = t.create(event.getLevel().getLevel());
            Kind k = kind(sample instanceof Mob, sample instanceof WaterAnimal,
                    sample instanceof Monster || sample instanceof Enemy, sample instanceof Animal);
            if (sample != null) {
                sample.discard();
            }
            return k;
        });
        var level = event.getLevel();
        var pos = event.getPos();
        boolean ok = switch (kind) {
            case WATER -> {
                FluidState fluid = level.getFluidState(pos);
                yield fluid.is(FluidTags.WATER) && level.getFluidState(pos.below()).is(FluidTags.WATER);
            }
            case MONSTER -> Monster.checkMonsterSpawnRules((EntityType) type, level, spawnType, pos, event.getRandom());
            case ANIMAL -> Animal.checkAnimalSpawnRules((EntityType) type, level, spawnType, pos, event.getRandom());
            case MOB -> Mob.checkMobSpawnRules((EntityType) type, level, spawnType, pos, event.getRandom());
            case NONE -> true;
        };
        event.setResult(ok ? MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED : MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }
}
