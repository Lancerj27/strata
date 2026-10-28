package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.model.Resolution;
import com.strata.model.Spawn;
import com.strata.model.SpawnPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.List;

/** WEIGHT, THEN SPAWN CHANCE, THEN GROUP SIZE. */
public class SpawnGroups {

    /** True while this places a group's other mobs: their position checks are not rolled again. */
    private static final ThreadLocal<Boolean> PLACING = ThreadLocal.withInitial(() -> false);

    /** How many spots each more mob of a group tries. */
    private static final int TRIES = 6;
    /** The game keeps a natural spawn this far from a player (24 blocks, squared). */
    private static final double NEAR_PLAYER_SQR = 24 * 24;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (PLACING.get() || (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION)
                || event.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL) {
            return;
        }
        Mob mob = event.getEntity();
        ServerLevelAccessor level = event.getLevel();
        String biome = level.getBiome(mob.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("");
        if (biome.isEmpty()) {
            return;
        }
        Resolution resolution = Config.resolve(biome);
        List<Spawn> rows = ReplaceSpawnRules.rowsFor(resolution, mob.getType());
        if (!SpawnPlan.groupsByStrata(rows)) {
            return;
        }
        // The game's own check, when no rule decided: only a spawn that would go ahead is rolled. After the
        // rows, so a mob Strata does not group takes no extra random draws (Default stays the game's).
        if (event.getResult() == MobSpawnEvent.PositionCheck.Result.DEFAULT
                && !(mob.checkSpawnRules(level, type) && mob.checkSpawnObstruction(level))) {
            return;
        }
        RandomSource random = level.getRandom();
        Spawn row = SpawnPlan.pickRow(rows, random.nextDouble());
        if (row == null || !SpawnPlan.passesChance(row.chance(), random.nextDouble())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }
        int size = SpawnPlan.groupSize(row.minCount(), row.maxCount(), random.nextDouble());
        if (size <= 0) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }
        event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
        placeOthers(level, mob, size - 1, type, random);
    }

    /**
     * Places a group's other mobs around its first, each through the game's spawn checks and the events
     * Strata's own rules answer.
     */
    static void placeOthers(ServerLevelAccessor level, Mob first, int count, MobSpawnType type,
                            RandomSource random) {
        if (count <= 0) {
            return;
        }
        EntityType<?> entityType = first.getType();
        int cx = first.getBlockX();
        int cz = first.getBlockZ();
        int chunkX = cx >> 4 << 4;
        int chunkZ = cz >> 4 << 4;
        PLACING.set(true);
        try {
            for (int n = 0; n < count; n++) {
                for (int tries = 0; tries < TRIES; tries++) {
                    int x = cx + random.nextInt(6) - random.nextInt(6);
                    int z = cz + random.nextInt(6) - random.nextInt(6);
                    if (type == MobSpawnType.CHUNK_GENERATION) {
                        x = Math.max(chunkX, Math.min(chunkX + 15, x));
                        z = Math.max(chunkZ, Math.min(chunkZ + 15, z));
                    }
                    if (x == cx && z == cz) {
                        continue;
                    }
                    int y = type == MobSpawnType.CHUNK_GENERATION
                            ? level.getHeight(SpawnPlacements.getHeightmapType(entityType), x, z)
                            : first.getBlockY();
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!SpawnPlacements.isSpawnPositionOk(entityType, level, pos)
                            || !level.noCollision(entityType.getSpawnAABB(x + 0.5, y, z + 0.5))
                            || !SpawnPlacements.checkSpawnRules(entityType, level, type, pos, random)) {
                        continue;
                    }
                    if (type == MobSpawnType.NATURAL) {
                        Player player = level.getNearestPlayer(x + 0.5, y, z + 0.5, -1, false);
                        if (player != null && player.distanceToSqr(x + 0.5, y, z + 0.5) < NEAR_PLAYER_SQR) {
                            continue;
                        }
                    }
                    if (place(level, entityType, pos, type, random)) {
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            // A world must not stop over one group: the first mob spawns as it was.
            Strata.LOGGER.warn("Spawn group of {} not placed: {}", entityType.getDescription().getString(),
                    e.toString(), e);
        } finally {
            PLACING.set(false);
        }
    }

    /** One more mob at the spot, through its position check and spawn events. */
    private static boolean place(ServerLevelAccessor level, EntityType<?> entityType, BlockPos pos,
                                 MobSpawnType type, RandomSource random) {
        Entity created = entityType.create(level.getLevel());
        if (!(created instanceof Mob mob)) {
            if (created != null) {
                created.discard();
            }
            return false;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        if (!EventHooks.checkSpawnPosition(mob, level, type)) {
            mob.discard();
            return false;
        }
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), type, null);
        if (mob.isSpawnCancelled()) {
            mob.discard();
            return false;
        }
        level.addFreshEntityWithPassengers(mob);
        return true;
    }
}
