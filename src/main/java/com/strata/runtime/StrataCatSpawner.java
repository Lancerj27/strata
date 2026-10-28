package com.strata.runtime;

import com.strata.model.SpecialSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * The game's CatSpawner, run with Strata's values (model.SpecialSpawners "cats"): a try every Tries every
 * seconds, 8 to 31 blocks from a player; in a village with Village beds claimed beds and fewer than Cats per
 * village cats, or at a witch hut with fewer than Cats per witch hut, where cats run, one cat.
 */
public class StrataCatSpawner extends CatSpawner {

    private static final String P = SpecialSpawners.CATS;
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnHostiles, boolean spawnPassives) {
        if (!SpecialSpawnsRuntime.hasRows()) {
            return super.tick(level, spawnHostiles, spawnPassives);
        }
        if (!spawnPassives || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || --this.nextTick > 0) {
            return 0;
        }
        this.nextTick = Math.max(1, SpecialSpawnsRuntime.globalWhole(P, "check_seconds") * 20);
        Player player = level.getRandomPlayer();
        if (player == null) {
            return 0;
        }
        RandomSource random = level.random;
        int dx = (8 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        int dz = (8 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        BlockPos pos = player.blockPosition().offset(dx, 0, dz);
        if (!level.hasChunksAt(pos.getX() - 10, pos.getZ() - 10, pos.getX() + 10, pos.getZ() + 10)
                || !SpawnPlacements.isSpawnPositionOk(EntityType.CAT, level, pos)
                || !SpecialSpawnsRuntime.runs(P, level, pos, EntityType.CAT)) {
            return 0;
        }
        if (level.isCloseToVillage(pos, 2)) {
            long beds = level.getPoiManager().getCountInRange(p -> p.is(PoiTypes.HOME), pos, 48,
                    PoiManager.Occupancy.IS_OCCUPIED);
            return beds >= SpecialSpawnsRuntime.whole(P, "village_beds", level, pos)
                    && cats(level, pos, 48.0) < SpecialSpawnsRuntime.whole(P, "village_max", level, pos)
                    ? spawnCat(level, pos) : 0;
        }
        if (level.structureManager().getStructureWithPieceAt(pos, StructureTags.CATS_SPAWN_IN).isValid()) {
            return cats(level, pos, 16.0) < SpecialSpawnsRuntime.whole(P, "hut_max", level, pos) ? spawnCat(level, pos) : 0;
        }
        return 0;
    }

    private static int cats(ServerLevel level, BlockPos pos, double range) {
        return level.getEntitiesOfClass(Cat.class, new AABB(pos).inflate(range, 8.0, range)).size();
    }

    private static int spawnCat(ServerLevel level, BlockPos pos) {
        Cat cat = EntityType.CAT.create(level);
        if (cat == null) {
            return 0;
        }
        cat.moveTo(pos, 0.0F, 0.0F);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(cat, level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.NATURAL, null);
        level.addFreshEntityWithPassengers(cat);
        return 1;
    }
}
