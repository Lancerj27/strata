package com.strata.runtime;

import com.strata.model.SpecialSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.Optional;

/**
 * The game's WanderingTraderSpawner, run with Strata's values (model.SpecialSpawners "wandering_trader"): a
 * try every Tries every seconds, passing at the chance (Chance % at first, raised by Chance % added after
 * each miss, up to Chance % at most, kept in the world's data as the game keeps it); then, where it runs,
 * Chance % at the player, within Distance blocks of the player's village bell, with its Trader llamas,
 * staying Stays seconds.
 */
public class StrataWanderingTraderSpawner extends WanderingTraderSpawner {

    private static final String P = SpecialSpawners.TRADER;
    /** The game checks its delay every 1200 ticks (a minute). */
    private static final int STEP = 1200;

    private final RandomSource random = RandomSource.create();
    private final ServerLevelData data;
    private int tickDelay = STEP;

    public StrataWanderingTraderSpawner(ServerLevelData data) {
        super(data);
        this.data = data;
    }

    @Override
    public int tick(ServerLevel level, boolean spawnHostiles, boolean spawnPassives) {
        if (!SpecialSpawnsRuntime.hasRows()) {
            return super.tick(level, spawnHostiles, spawnPassives);
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING) || --this.tickDelay > 0) {
            return 0;
        }
        this.tickDelay = STEP;
        int delay = this.data.getWanderingTraderSpawnDelay() - STEP;
        this.data.setWanderingTraderSpawnDelay(delay);
        if (delay > 0) {
            return 0;
        }
        this.data.setWanderingTraderSpawnDelay(Math.max(STEP, SpecialSpawnsRuntime.globalWhole(P, "check_seconds") * 20));
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return 0;
        }
        int start = SpecialSpawnsRuntime.globalWhole(P, "chance_start");
        int most = Math.max(start, SpecialSpawnsRuntime.globalWhole(P, "chance_max"));
        int chance = this.data.getWanderingTraderSpawnChance();
        this.data.setWanderingTraderSpawnChance(Mth.clamp(chance + SpecialSpawnsRuntime.globalWhole(P, "chance_step"),
                start, most));
        if (this.random.nextInt(100) > chance) {
            return 0;
        }
        if (spawn(level)) {
            this.data.setWanderingTraderSpawnChance(start);
            return 1;
        }
        return 0;
    }

    private boolean spawn(ServerLevel level) {
        Player player = level.getRandomPlayer();
        if (player == null) {
            return true;
        }
        BlockPos near = player.blockPosition();
        if (this.random.nextInt(100) >= SpecialSpawnsRuntime.whole(P, "spawn_chance", level, near)) {
            return false;
        }
        int distance = Math.max(1, SpecialSpawnsRuntime.whole(P, "distance", level, near));
        Optional<BlockPos> bell = level.getPoiManager().find(p -> p.is(PoiTypes.MEETING), p -> true, near, distance,
                PoiManager.Occupancy.ANY);
        BlockPos center = bell.orElse(near);
        BlockPos at = spotNear(level, center, distance);
        if (at == null || !hasRoom(level, at) || level.getBiome(at).is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)
                || !SpecialSpawnsRuntime.runs(P, level, at, EntityType.WANDERING_TRADER)) {
            return false;
        }
        WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(level, at, MobSpawnType.EVENT);
        if (trader == null) {
            return false;
        }
        for (int i = 0; i < SpecialSpawnsRuntime.whole(P, "llamas", level, at); i++) {
            BlockPos llamaAt = spotNear(level, trader.blockPosition(), 4);
            if (llamaAt != null) {
                TraderLlama llama = EntityType.TRADER_LLAMA.spawn(level, llamaAt, MobSpawnType.EVENT);
                if (llama != null) {
                    llama.setLeashedTo(trader, true);
                }
            }
        }
        this.data.setWanderingTraderId(trader.getUUID());
        trader.setDespawnDelay(SpecialSpawnsRuntime.whole(P, "stay_seconds", level, at) * 20);
        trader.setWanderTarget(center);
        trader.restrictTo(center, 16);
        return true;
    }

    private BlockPos spotNear(ServerLevel level, BlockPos pos, int distance) {
        var placement = SpawnPlacements.getPlacementType(EntityType.WANDERING_TRADER);
        for (int i = 0; i < 10; i++) {
            int x = pos.getX() + this.random.nextInt(distance * 2) - distance;
            int z = pos.getZ() + this.random.nextInt(distance * 2) - distance;
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            if (placement.isSpawnPositionOk(level, at, EntityType.WANDERING_TRADER)) {
                return at;
            }
        }
        return null;
    }

    private static boolean hasRoom(BlockGetter level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos, pos.offset(1, 2, 1))) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
