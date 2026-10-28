package com.strata.runtime;

import com.strata.data.Config;
import com.strata.data.StrataConfig;
import com.strata.model.Resolution;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;
import com.strata.model.SpawnPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** A REPLACE BIOME'S HOSTILE MOBS SPAWN IN ANY LIGHT. */
public class ReplaceSpawnRules {

    /** The spawns the game makes on its own: a Replace biome allows these only for mobs on its list. */
    public static final java.util.Set<MobSpawnType> WORLD_SPAWNS = java.util.EnumSet.of(
            MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL, MobSpawnType.STRUCTURE);

    /**
     * True when a Replace biome refuses this world spawn: a mob with no row on the biome's list, while the
     * biome, its tier, or the global option blocks other spawns (OtherSpawns).
     */
    public static boolean blocked(ServerLevelAccessor level, BlockPos pos, EntityType<?> type, MobSpawnType spawnType) {
        // A SPECIAL MOB WHERE SPECIAL MOB SPAWNS ARE OFF: the same one rule.
        if (SpecialSpawnsRuntime.refuses(level, pos, type, spawnType)) {
            return true;
        }
        if (!WORLD_SPAWNS.contains(spawnType)) {
            return false;
        }
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        if (biome.isEmpty() || !Config.replacesGameSpawns(biome)) {
            return false;
        }
        Resolution resolution = Config.resolve(biome);
        boolean blocks = OtherSpawns.blocks(StrataConfig.read(StrataConfig.BLOCK_OTHER_SPAWNS), resolution.tier(),
                biome);
        return !SpawnPlan.allowedInReplace(blocks, true, rowsFor(resolution, type));
    }

    /** The list's rows that spawn as this type: its own and its variants without an entity (SpawnsModifier#keyOf). */
    public static java.util.List<Spawn> rowsFor(Resolution resolution, EntityType<?> type) {
        String key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).map(k -> k.location().toString()).orElse("");
        java.util.List<Spawn> out = new java.util.ArrayList<>();
        for (SpawnCategory category : SpawnCategory.values()) {
            for (Spawn s : resolution.mobs(category)) {
                if (SpawnsModifier.keyOf(s).equals(key)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /**
     * The type's spawn rule: a Replace biome refuses a mob not on its list (#blocked), and a hostile mob on
     * it skips the light.
     */
    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (blocked(event.getLevel(), event.getPos(), event.getEntityType(), event.getSpawnType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            return;
        }
        if (event.getResult() != MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT || event.getDefaultResult()) {
            return;
        }
        EntityType<?> type = event.getEntityType();
        if (!applies(event.getLevel(), event.getPos(), type, event.getSpawnType())) {
            return;
        }
        BlockPos below = event.getPos().below();
        if (event.getLevel().getDifficulty() != Difficulty.PEACEFUL
                && event.getLevel().getBlockState(below).isValidSpawn(event.getLevel(), below, type)) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
        }
    }

    /**
     * The mob's own spawn check: refused in a Replace biome for a mob not on its list (#blocked, the
     * new-chunk path's only check); without the light for a hostile mob on it (only nothing in the way).
     */
    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        if (blocked(event.getLevel(), mob.blockPosition(), mob.getType(), event.getSpawnType())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }
        if (event.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT) {
            return;
        }
        if (applies(event.getLevel(), mob.blockPosition(), mob.getType(), event.getSpawnType())
                && mob.checkSpawnObstruction(event.getLevel())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
        }
    }

    /** True when a spawn of this type here skips the need for darkness. */
    private static boolean applies(ServerLevelAccessor level, BlockPos pos, EntityType<?> type, MobSpawnType spawnType) {
        if (type.getCategory() != MobCategory.MONSTER
                || SpawnPlacements.getPlacementType(type) != SpawnPlacementTypes.ON_GROUND) {
            return false;
        }
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        boolean replaces = !biome.isEmpty() && Config.replacesGameSpawns(biome);
        // The biome's or its tier's rule, else the old global switch.
        boolean anyLight = !biome.isEmpty() && AreaRules.on(AreaRules.MONSTERS_OUTSIDE_DARKNESS,
                StrataConfig.read(StrataConfig.MONSTERS_OUTSIDE_DARKNESS), Config.resolve(biome).tier(), biome);
        return SpawnPlan.anyLight(anyLight, replaces, true,
                spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION,
                replaces && !rowsFor(Config.resolve(biome), type).isEmpty());
    }
}
