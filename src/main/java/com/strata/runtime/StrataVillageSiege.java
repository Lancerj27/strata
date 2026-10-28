package com.strata.runtime;

import com.strata.model.SpecialSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillageSiegeEvent;

/**
 * The game's VillageSiege, run with Strata's values (model.SpecialSpawners "sieges"): at midnight, the
 * Chance of a siege that night; then, at a player in a village where sieges run, Mobs of the Mob, Distance
 * blocks away, one each few ticks.
 */
public class StrataVillageSiege extends VillageSiege {

    private static final String P = SpecialSpawners.SIEGES;

    private boolean tonight;
    private boolean setUp;
    private int toSpawn;
    private int nextSpawnTime;
    private BlockPos center = BlockPos.ZERO;
    private EntityType<?> mob = EntityType.ZOMBIE;

    @Override
    public int tick(ServerLevel level, boolean spawnHostiles, boolean spawnPassives) {
        if (!SpecialSpawnsRuntime.hasRows()) {
            return super.tick(level, spawnHostiles, spawnPassives);
        }
        if (level.isDay() || !spawnHostiles) {
            this.tonight = false;
            this.setUp = false;
            return 0;
        }
        if ((double) level.getTimeOfDay(0.0F) == 0.5) {
            this.tonight = level.random.nextInt(100) < SpecialSpawnsRuntime.globalWhole(P, "chance");
        }
        if (!this.tonight) {
            return 0;
        }
        if (!this.setUp) {
            if (!setUp(level)) {
                return 0;
            }
            this.setUp = true;
        }
        if (this.nextSpawnTime > 0) {
            this.nextSpawnTime--;
            return 0;
        }
        this.nextSpawnTime = 2;
        if (this.toSpawn > 0) {
            spawnOne(level);
            this.toSpawn--;
        } else {
            this.tonight = false;
        }
        return 1;
    }

    private boolean setUp(ServerLevel level) {
        for (Player player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            if (!level.isVillage(pos) || level.getBiome(pos).is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
                continue;
            }
            EntityType<?> type = SpecialSpawnsRuntime.mob(SpecialSpawnsRuntime.value(P, "mob", level, pos),
                    EntityType.ZOMBIE);
            if (!SpecialSpawnsRuntime.runs(P, level, pos, type)) {
                continue;
            }
            int distance = SpecialSpawnsRuntime.whole(P, "distance", level, pos);
            for (int i = 0; i < 10; i++) {
                float angle = level.random.nextFloat() * (float) (Math.PI * 2);
                BlockPos at = new BlockPos(pos.getX() + Mth.floor(Mth.cos(angle) * distance), pos.getY(),
                        pos.getZ() + Mth.floor(Mth.sin(angle) * distance));
                Vec3 where = findSpot(level, at, type);
                if (where != null) {
                    if (NeoForge.EVENT_BUS.post(new VillageSiegeEvent(this, level, player, where)).isCanceled()) {
                        return false;
                    }
                    this.center = at;
                    this.mob = type;
                    this.nextSpawnTime = 0;
                    this.toSpawn = SpecialSpawnsRuntime.whole(P, "zombies", level, pos);
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private void spawnOne(ServerLevel level) {
        Vec3 where = findSpot(level, this.center, this.mob);
        if (where == null) {
            return;
        }
        Entity entity = this.mob.create(level);
        if (!(entity instanceof Mob m)) {
            if (entity != null) {
                entity.discard();
            }
            return;
        }
        m.moveTo(where.x, where.y, where.z, level.random.nextFloat() * 360.0F, 0.0F);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(m, level, level.getCurrentDifficultyAt(m.blockPosition()),
                MobSpawnType.EVENT, null);
        level.addFreshEntityWithPassengers(m);
    }

    /** A spot in the village near a place, as VillageSiege#findRandomSpawnPos: a monster's spawn rules, or the type's. */
    @SuppressWarnings("unchecked")
    private static Vec3 findSpot(ServerLevel level, BlockPos near, EntityType<?> type) {
        for (int i = 0; i < 10; i++) {
            int x = near.getX() + level.random.nextInt(16) - 8;
            int z = near.getZ() + level.random.nextInt(16) - 8;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            boolean ok = type.getCategory() == MobCategory.MONSTER
                    ? Monster.checkMonsterSpawnRules((EntityType<? extends Monster>) type, level, MobSpawnType.EVENT, pos,
                    level.random)
                    : SpawnPlacements.isSpawnPositionOk(type, level, pos);
            if (level.isVillage(pos) && ok) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return null;
    }
}
