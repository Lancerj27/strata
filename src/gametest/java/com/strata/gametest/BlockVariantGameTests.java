package com.strata.gametest;

import com.strata.runtime.BlockVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** BLOCK VARIANTS, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class BlockVariantGameTests {

    private BlockVariantGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "blocks")
    public static void blockVariantIsItsOwnDoor(GameTestHelper helper) {
        BlockVariants.Def def = BlockVariants.named("reinforced_oak_door");
        helper.assertTrue(def != null, "strata:reinforced_oak_door was not registered");
        helper.assertTrue(def.block() instanceof DoorBlock, "The variant is not a door: " + def.block().getClass());
        helper.assertTrue(def.base() == Blocks.OAK_DOOR, "The variant's base is not the oak door");
        helper.assertTrue(def.item() instanceof DoubleHighBlockItem, "The variant's item does not place a door");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        float hardness = def.block().defaultBlockState().getDestroySpeed(level, pos);
        helper.assertTrue(hardness == 10, "The variant's hardness " + hardness + ", not its own 10");
        helper.assertTrue(def.block().getExplosionResistance() == Blocks.OAK_DOOR.getExplosionResistance(),
                "The variant's blast resistance is not its base block's");
        helper.assertTrue(Blocks.OAK_DOOR.defaultBlockState().getDestroySpeed(level, pos) == 3,
                "The oak door itself changed");
        helper.setBlock(new BlockPos(1, 2, 1), def.block().defaultBlockState());
        helper.assertBlockPresent(def.block(), new BlockPos(1, 2, 1));
        helper.succeed();
    }
}
