package com.strata.gametest;

import com.strata.data.GameLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** BLOCK STATS, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class BlockStatsGameTests {

    private BlockStatsGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "blocks")
    public static void blockStatsServedOverTheGame(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(stone.getDestroySpeed(level, pos) == 50, "Stone hardness " + stone.getDestroySpeed(level, pos) + ", not 50");
        helper.assertTrue(Blocks.STONE.getExplosionResistance() == 1200, "Stone blast resistance " + Blocks.STONE.getExplosionResistance());
        helper.assertTrue(Blocks.PACKED_ICE.getFriction() == 0.6f, "Packed ice friction " + Blocks.PACKED_ICE.getFriction());
        helper.assertTrue(Blocks.DIRT.getSpeedFactor() == 0.4f && Blocks.DIRT.getJumpFactor() == 0.5f, "Dirt speed or jump factor");
        helper.assertTrue(Blocks.GLASS.defaultBlockState().getLightEmission() == 12, "Glass light " + Blocks.GLASS.defaultBlockState().getLightEmission());
        BlockState lamp = Blocks.REDSTONE_LAMP.defaultBlockState();
        helper.assertTrue(lamp.setValue(RedstoneLampBlock.LIT, true).getLightEmission() == 7
                        && lamp.setValue(RedstoneLampBlock.LIT, false).getLightEmission() == 0,
                "The redstone lamp's light is not 7 lit and 0 unlit");
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        helper.assertTrue(planks.getFlammability(level, pos, Direction.UP) == 0
                        && planks.getFireSpreadSpeed(level, pos, Direction.UP) == 0,
                "Oak planks still burn");
        helper.assertTrue(Blocks.GRAVEL.defaultBlockState().requiresCorrectToolForDrops(), "Gravel does not need the correct tool");
        BlockState basalt = Blocks.SMOOTH_BASALT.defaultBlockState();
        helper.assertTrue(basalt.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS, "Basalt's sound");
        helper.assertTrue(basalt.getMapColor(level, pos) == net.minecraft.world.level.material.MapColor.COLOR_RED,
                "Basalt's map colour");
        helper.assertTrue(basalt.isRandomlyTicking(), "Basalt does not tick at random");
        helper.assertTrue(!basalt.isSolid(), "Basalt is still solid");
        helper.assertTrue(basalt.getCollisionShape(level, pos).isEmpty()
                        && basalt.getCollisionShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty(),
                "Basalt still has collision");
        helper.assertTrue(!basalt.canOcclude() && !basalt.isSolidRender(level, pos), "Basalt still occludes");
        helper.assertTrue(!basalt.isRedstoneConductor(level, pos), "Basalt still conducts redstone");
        helper.assertTrue(!basalt.shouldSpawnTerrainParticles(), "Basalt still throws breaking particles");
        helper.assertTrue(basalt.emissiveRendering(level, pos), "Basalt is not drawn fully lit");
        helper.assertTrue(!basalt.isValidSpawn(level, pos, net.minecraft.world.entity.EntityType.ZOMBIE),
                "Mobs can still spawn on basalt");
        helper.assertTrue(!basalt.isSuffocating(level, pos) && !basalt.isViewBlocking(level, pos),
                "Basalt still suffocates or blocks the view");
        BlockState bush = Blocks.DEAD_BUSH.defaultBlockState();
        helper.assertTrue(!bush.getCollisionShape(level, pos).isEmpty(), "A dead bush with collision on lets things through");
        helper.assertTrue(bush.hasOffsetFunction() && java.util.stream.IntStream.range(0, 16).anyMatch(n ->
                        !bush.getOffset(level, new BlockPos(n, 0, n * 3)).equals(net.minecraft.world.phys.Vec3.ZERO)),
                "A dead bush given an offset does not sit off the grid");
        BlockState grass = Blocks.SHORT_GRASS.defaultBlockState();
        helper.assertTrue(!grass.hasOffsetFunction() && grass.getOffset(level, pos).equals(net.minecraft.world.phys.Vec3.ZERO),
                "Grass with no offset still sits off the grid");
        helper.assertTrue(!Blocks.STONE_BRICKS.defaultBlockState().hasOffsetFunction(), "Solid stone bricks took an offset");
        helper.assertTrue(com.strata.model.BlockStats.soundNames.contains("glass")
                        && com.strata.model.BlockStats.mapColorNames.contains("color_red"),
                "The sound or map colour names were not read from the game");

        helper.assertTrue(Blocks.GRANITE.defaultBlockState().getDestroySpeed(level, pos) == 1.5f, "Granite changed");

        List<GameLayer.Row> sheet = GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCKS));
        helper.assertTrue(has(sheet, "minecraft:stone", GameLayer.COMPONENT, "hardness", "1.5"),
                "game/blocks.csv lacks stone's own hardness 1.5");
        helper.assertTrue(has(sheet, "minecraft:glass", GameLayer.COMPONENT, "light_level", "0"),
                "game/blocks.csv lacks glass's own light 0");
        helper.assertTrue(has(sheet, "minecraft:redstone_lamp", GameLayer.NOTE, "light_level", "LIT"),
                "game/blocks.csv does not mark the lamp's light as lit only");
        helper.assertTrue(has(sheet, "minecraft:iron_ore", GameLayer.KIND, GameLayer.KIND, "Ores")
                        && has(sheet, "minecraft:stone", GameLayer.KIND, GameLayer.KIND, "Stone and metal")
                        && has(sheet, "minecraft:oak_planks", GameLayer.KIND, GameLayer.KIND, "Wood")
                        && has(sheet, "minecraft:water", GameLayer.KIND, GameLayer.KIND, "Liquids")
                        && has(sheet, "minecraft:oak_leaves", GameLayer.KIND, GameLayer.KIND, "Plants"),
                "A block's kind is wrong in game/blocks.csv");
        helper.succeed();
    }

    private static boolean has(List<GameLayer.Row> sheet, String entity, String group, String key, String value) {
        return sheet.stream().anyMatch(r -> r.entity().equals(entity) && r.group().equals(group)
                && r.key().equals(key) && r.value().equals(value));
    }
}
