package com.strata.runtime;

import com.strata.model.SpawnRule;
import com.strata.model.SpecialSpawners;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;

/** The game's PhantomSpawner, run with Strata's values ( model.SpecialSpawners "phantoms"). */
public class StrataPhantomSpawner extends PhantomSpawner {

    private static final String P = SpecialSpawners.PHANTOMS;
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
        if (!SpecialSpawnsRuntime.hasRows()) {
            return super.tick(level, spawnEnemies, spawnFriendlies);
        }
        if (!spawnEnemies || !level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            return 0;
        }
        RandomSource random = level.random;
        if (--this.nextTick > 0) {
            return 0;
        }
        this.nextTick += (SpecialSpawnsRuntime.globalWhole(P, "check_seconds")
                + random.nextInt(SpecialSpawnsRuntime.globalWhole(P, "check_extra_seconds") + 1)) * 20;
        int spawned = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            if (!SpecialSpawnsRuntime.runs(P, level, pos, EntityType.PHANTOM)) {
                continue;
            }
            if (SpecialSpawnsRuntime.on(P, "night_only", level, pos) && level.getSkyDarken() < 5
                    && level.dimensionType().hasSkyLight()) {
                continue;
            }
            DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
            int fewest = SpecialSpawnsRuntime.whole(P, "count_min", level, pos);
            int most = fewest + SpecialSpawnsRuntime.whole(P, "count_per_difficulty", level, pos)
                    * difficulty.getDifficulty().getId();
            var event = new PlayerSpawnPhantomsEvent(player, fewest + random.nextInt(most - fewest + 1));
            NeoForge.EVENT_BUS.post(event);
            if (event.getResult() == PlayerSpawnPhantomsEvent.Result.DENY) {
                continue;
            }
            boolean allow = event.getResult() == PlayerSpawnPhantomsEvent.Result.ALLOW;
            if (!allow) {
                boolean sky = !SpecialSpawnsRuntime.on(P, "open_sky", level, pos) || !level.dimensionType().hasSkyLight()
                        || pos.getY() >= level.getSeaLevel() && level.canSeeSky(pos);
                if (!sky || SpecialSpawnsRuntime.on(P, "difficulty_roll", level, pos)
                        && !difficulty.isHarderThan(random.nextFloat() * 3.0F)) {
                    continue;
                }
                // THE PHANTOM'S SPAWN RULES, AT THE PLAYER: its Time since rest rule sets the days awake (the
                // game's, read from its code, when it has none), and every other rule must hold too.
                List<SpawnRule> rules = SpecialSpawnsRuntime.rulesAt(level, pos, SpecialSpawners.PHANTOM);
                if (!SpawnRuleEvaluator.allMatch(rules, level, pos, player)) {
                    continue;
                }
                int days = rules.stream().filter(r -> r.type() == SpawnRule.Type.TIME_SINCE_REST)
                        .mapToInt(r -> SpecialSpawners.restDays(r.value())).filter(d -> d >= 0).findFirst()
                        .orElse(SpecialSpawnsRuntime.gameRestDays());
                int awake = Mth.clamp(player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1,
                        Integer.MAX_VALUE);
                if (!SpecialSpawners.restRoll(awake, days, random.nextInt(awake))) {
                    continue;
                }
            }
            BlockPos at = pos.above(20 + random.nextInt(15)).east(-10 + random.nextInt(21)).south(-10 + random.nextInt(21));
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, at, level.getBlockState(at), level.getFluidState(at),
                    EntityType.PHANTOM)) {
                continue;
            }
            SpawnGroupData group = null;
            for (int n = 0; n < event.getPhantomsToSpawn(); n++) {
                Phantom phantom = EntityType.PHANTOM.create(level);
                if (phantom != null) {
                    phantom.moveTo(at, 0.0F, 0.0F);
                    // Through FinalizeSpawnEvent, as the game's own spawner is (its coremod).
                    group = net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(phantom, level, difficulty,
                            MobSpawnType.NATURAL, group);
                    level.addFreshEntityWithPassengers(phantom);
                    spawned++;
                }
            }
        }
        return spawned;
    }
}
