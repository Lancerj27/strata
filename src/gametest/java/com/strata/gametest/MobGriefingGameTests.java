package com.strata.gametest;

import com.strata.domain.MobOps;
import com.strata.model.SpawnCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** A MOB'S OWN MOB GRIEFING SWITCH, IN A REAL WORLD. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class MobGriefingGameTests {

    private MobGriefingGameTests() {
    }

    private static final List<BlockPos> RING = List.of(new BlockPos(2, 2, 3), new BlockPos(4, 2, 3),
            new BlockPos(3, 2, 2), new BlockPos(3, 2, 4));

    /** Off: a lit creeper's blast leaves the dirt around it. */
    @GameTest(template = "arena", timeoutTicks = 240, batch = "griefing")
    public static void creeperWithMobGriefingOffBreaksNoBlocks(GameTestHelper helper) {
        if (!set("minecraft:creeper", MobOps.BatchAction.MOB_GRIEFING_OFF)) {
            helper.fail("Could not set the creeper's Mob griefing to Off");
            return;
        }
        RING.forEach(p -> helper.setBlock(p, Blocks.DIRT));
        Creeper off = helper.spawn(EntityType.CREEPER, 3, 2, 3);
        off.ignite();
        helper.runAfterDelay(60, () -> {
            long kept = RING.stream().filter(p -> helper.getBlockState(p).is(Blocks.DIRT)).count();
            set("minecraft:creeper", MobOps.BatchAction.MOB_GRIEFING_DEFAULT);
            if (kept != RING.size()) {
                helper.fail("With Mob griefing Off the blast broke " + (RING.size() - kept) + " block(s)");
                return;
            }
            Creeper game = helper.spawn(EntityType.CREEPER, 3, 2, 3);
            game.ignite();
            helper.runAfterDelay(60, () -> {
                long left = RING.stream().filter(p -> helper.getBlockState(p).is(Blocks.DIRT)).count();
                helper.assertTrue(left < RING.size(),
                        "With Mob griefing on Default the blast broke nothing: the test cannot see a break");
                helper.succeed();
            });
        });
    }

    /** Off: the game's own check for an enderman taking a block says no; Default: the game's rule. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "griefing")
    public static void endermanWithMobGriefingOffTakesNoBlocks(GameTestHelper helper) {
        if (!set("minecraft:enderman", MobOps.BatchAction.MOB_GRIEFING_OFF)) {
            helper.fail("Could not set the enderman's Mob griefing to Off");
            return;
        }
        EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 3, 2, 3);
        boolean whenOff = EventHooks.canEntityGrief(helper.getLevel(), enderman);
        set("minecraft:enderman", MobOps.BatchAction.MOB_GRIEFING_DEFAULT);
        boolean whenDefault = EventHooks.canEntityGrief(helper.getLevel(), enderman);
        boolean rule = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        helper.assertTrue(!whenOff, "With Mob griefing Off the enderman may still take blocks");
        helper.assertTrue(whenDefault == rule, "On Default the enderman does not follow the game's rule");
        helper.succeed();
    }

    /** The switch for a plain mob on Global, as the Batch screen sets it; reloads. */
    private static boolean set(String entity, MobOps.BatchAction action) {
        return MobOps.batch(List.of(new MobOps.BatchMob("global", "", entity, "", SpawnCategory.MONSTER)), action)
                .ok();
    }
}
