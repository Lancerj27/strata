package com.strata.runtime;

import com.strata.model.SpecialSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;

/**
 * The game's PatrolSpawner, run with Strata's values (model.SpecialSpawners "patrols"): a try every Tries
 * every + up to Plus up to seconds; then, where patrols run and its Leader's spawn rules hold (the
 * pillager's: World day 5+, daylight, from the game's code), at the Chance, away from a village, Nearest to
 * Farthest blocks on each side of a player, Members + More per difficulty x the local difficulty (rounded up)
 * mobs: the Leader first, then Members.
 */
public class StrataPatrolSpawner extends PatrolSpawner {

    private static final String P = SpecialSpawners.PATROLS;
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
        if (!SpecialSpawnsRuntime.hasRows()) {
            return super.tick(level, spawnEnemies, spawnFriendlies);
        }
        if (!spawnEnemies || !level.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)) {
            return 0;
        }
        RandomSource random = level.random;
        if (--this.nextTick > 0) {
            return 0;
        }
        this.nextTick += SpecialSpawnsRuntime.globalWhole(P, "check_seconds") * 20
                + random.nextInt(Math.max(1, SpecialSpawnsRuntime.globalWhole(P, "check_extra_seconds") * 20));

        int players = level.players().size();
        if (players < 1) {
            return 0;
        }
        Player player = level.players().get(random.nextInt(players));
        if (player.isSpectator()) {
            return 0;
        }
        BlockPos near = player.blockPosition();
        if (random.nextInt(100) >= SpecialSpawnsRuntime.whole(P, "chance", level, near)
                || SpecialSpawnsRuntime.on(P, "not_near_village", level, near) && level.isCloseToVillage(near, 2)) {
            return 0;
        }
        int lo = SpecialSpawnsRuntime.whole(P, "distance_min", level, near);
        int hi = Math.max(lo, SpecialSpawnsRuntime.whole(P, "distance_max", level, near));
        int dx = (lo + random.nextInt(hi - lo + 1)) * (random.nextBoolean() ? -1 : 1);
        int dz = (lo + random.nextInt(hi - lo + 1)) * (random.nextBoolean() ? -1 : 1);
        BlockPos.MutableBlockPos pos = near.mutable().move(dx, 0, dz);
        if (!level.hasChunksAt(pos.getX() - 10, pos.getZ() - 10, pos.getX() + 10, pos.getZ() + 10)
                || level.getBiome(pos).is(BiomeTags.WITHOUT_PATROL_SPAWNS)) {
            return 0;
        }
        EntityType<?> leader = SpecialSpawnsRuntime.mob(SpecialSpawnsRuntime.value(P, "leader", level, pos),
                EntityType.PILLAGER);
        EntityType<?> member = SpecialSpawnsRuntime.mob(SpecialSpawnsRuntime.value(P, "member", level, pos),
                EntityType.PILLAGER);
        // THE LEADER'S SPAWN RULES: the pillager's are the game's World day 5+ and daylight, seeded from its
        // code.
        String leaderId = BuiltInRegistries.ENTITY_TYPE.getResourceKey(leader).map(k -> k.location().toString())
                .orElse("");
        if (!SpecialSpawnsRuntime.runs(P, level, pos, leader)
                || !SpawnRuleEvaluator.allMatch(SpecialSpawnsRuntime.rulesAt(level, pos, leaderId), level, pos)) {
            return 0;
        }
        int members = SpecialSpawnsRuntime.whole(P, "members_base", level, pos)
                + SpecialSpawnsRuntime.whole(P, "members_per_difficulty", level, pos)
                * (int) Math.ceil(level.getCurrentDifficultyAt(pos).getEffectiveDifficulty());
        int made = 0;
        for (int n = 0; n < members; n++) {
            made++;
            pos.setY(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY());
            if (n == 0) {
                if (!spawnMember(level, pos, leader, true)) {
                    break;
                }
            } else {
                spawnMember(level, pos, member, false);
            }
            pos.setX(pos.getX() + random.nextInt(5) - random.nextInt(5));
            pos.setZ(pos.getZ() + random.nextInt(5) - random.nextInt(5));
        }
        return made;
    }

    /** One member, as PatrolSpawner#spawnPatrolMember: a pillager's spawn checks, a patrolling mob's leader and target. */
    @SuppressWarnings("unchecked")
    private static boolean spawnMember(ServerLevel level, BlockPos pos, EntityType<?> type, boolean leader) {
        BlockState state = level.getBlockState(pos);
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, state.getFluidState(), type)
                || !PatrollingMonster.checkPatrollingMonsterSpawnRules((EntityType<? extends PatrollingMonster>) type,
                level, MobSpawnType.PATROL, pos, level.random)) {
            return false;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return false;
        }
        if (leader && mob instanceof PatrollingMonster patrolling) {
            patrolling.setPatrolLeader(true);
            patrolling.findPatrolTarget();
        }
        mob.setPos(pos.getX(), pos.getY(), pos.getZ());
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.PATROL, null);
        level.addFreshEntityWithPassengers(mob);
        return true;
    }
}
