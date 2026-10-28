package com.strata.gametest;

import com.google.gson.JsonParser;
import com.strata.data.Config;
import com.strata.data.Defaults;
import com.strata.data.JsonOverrides;
import com.strata.domain.DisabledOps;
import com.strata.runtime.BlockSwapRuntime;
import com.strata.runtime.DisabledContent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** DISABLED BLOCKS AND ITEMS OVER THE REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class DisabledGameTests {

    private DisabledGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "disabled")
    public static void disabledContentIsGone(GameTestHelper helper) throws Exception {
        Defaults.writeIfMissing();
        JsonOverrides.extract(helper.getLevel().getServer().getResourceManager());
        helper.assertTrue(DisabledOps.toggle(DisabledOps.ITEM, List.of("minecraft:iron_ingot")).ok(),
                "Iron ingots could not be disabled");
        helper.assertTrue(DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:gold_ore")).ok(),
                "Gold ore could not be disabled");
        helper.assertTrue(DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:diamond_block")).ok(),
                "Diamond blocks could not be disabled");
        Config.reload();
        try {
            helper.assertTrue(DisabledContent.disabled(Items.IRON_INGOT), "Iron ingots read as on");
            helper.assertTrue(DisabledContent.disabled(Blocks.DIAMOND_BLOCK), "The diamond block reads as on");
            helper.assertTrue(DisabledContent.disabled(Items.DIAMOND_BLOCK), "The diamond block's item reads as on");
            helper.assertTrue(!DisabledContent.disabled(Items.GOLD_INGOT), "Gold ingots read as off");

            ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(List.of(
                    new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)));
            loot.removeIf(stack -> DisabledContent.disabled(stack.getItem()));
            helper.assertTrue(loot.size() == 1 && loot.getFirst().is(Items.GOLD_INGOT), "Loot kept the iron ingot");

            Path tmp = Files.createTempDirectory("strata-disabled");
            JsonOverrides.writePack(tmp, DisabledOps.allIds());
            Path smelt = tmp.resolve("minecraft").resolve("recipe").resolve("iron_ingot_from_smelting_raw_iron.json");
            helper.assertTrue(Files.isRegularFile(smelt), "The furnace's iron ingot recipe was not switched off");
            helper.assertTrue(JsonParser.parseString(Files.readString(smelt)).getAsJsonObject().has("neoforge:conditions"),
                    "The iron ingot recipe has no false condition");
            helper.assertTrue(JsonOverrides.game(JsonOverrides.STRUCTURE).containsKey("minecraft:village_plains"),
                    "The plains village was not read");
            helper.assertTrue(JsonOverrides.game(JsonOverrides.STRUCTURE_SET).containsKey("minecraft:villages"),
                    "The villages structure set was not read");
            helper.assertTrue(JsonOverrides.game(JsonOverrides.CONFIGURED_FEATURE).containsKey("minecraft:ore_diamond_small"),
                    "The diamond ore feature was not read");
            Path diamonds = tmp.resolve("minecraft").resolve("worldgen").resolve("placed_feature").resolve("ore_diamond.json");
            helper.assertTrue(!Files.isRegularFile(diamonds), "Diamond ore went off for a disabled diamond BLOCK");
            Path gold = tmp.resolve("minecraft").resolve("worldgen").resolve("placed_feature").resolve("ore_gold.json");
            helper.assertTrue(Files.isRegularFile(gold), "Gold ore still generates with gold ore disabled");
            helper.assertTrue(JsonParser.parseString(Files.readString(gold)).getAsJsonObject().getAsJsonArray("placement")
                    .get(0).getAsJsonObject().get("count").getAsInt() == 0, "Gold ore is not placed zero times");
        } finally {
            DisabledOps.toggle(DisabledOps.ITEM, List.of("minecraft:iron_ingot"));
            DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:diamond_block"));
            DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:gold_ore"));
            Config.reload();
        }
        helper.succeed();
    }

    /** The block-rules sweep: a disabled block in land already made comes out - air where it was. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "disabled2")
    public static void disabledBlockLeavesAChunk(GameTestHelper helper) {
        Path table = com.strata.data.Paths.table(com.strata.data.Schema.BlockReplacements.FILE);
        BlockPos pos = new BlockPos(5, 3, 5);
        helper.setBlock(pos, Blocks.OBSIDIAN);
        helper.assertTrue(helper.getBlockState(pos).is(Blocks.OBSIDIAN), "the arena block did not place");
        DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:obsidian"));
        Config.reload();
        try {
            helper.assertTrue(DisabledContent.disabled(Blocks.OBSIDIAN), "Obsidian reads as on");
            BlockPos abs = helper.absolutePos(pos);
            LevelChunk chunk = helper.getLevel().getChunk(abs.getX() >> 4, abs.getZ() >> 4);
            BlockSwapRuntime.stripExisting(helper.getLevel(), chunk);
            helper.assertTrue(helper.getBlockState(pos).is(Blocks.AIR), "The disabled block stayed");
            helper.setBlock(pos, Blocks.GRASS_BLOCK);
            helper.assertTrue(com.strata.domain.BlockReplacementOps.save(table,
                    "global", "", "minecraft:grass_block", "minecraft:dirt").ok(),
                    "The grass-to-dirt rule could not be saved");
            Config.reload();
            BlockSwapRuntime.stripExisting(helper.getLevel(), chunk);
            helper.assertTrue(helper.getBlockState(pos).is(Blocks.DIRT), "The replacement did not reach existing land");
        } finally {
            com.strata.domain.BlockReplacementOps.remove(table,
                    new com.strata.domain.BlockReplacementOps.Row("global", "", "minecraft:grass_block", "minecraft:dirt"));
            DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:obsidian"));
            Config.reload();
        }
        helper.succeed();
    }

    /**
     * The mid-session re-arm: a toggle with the world open makes the chunks already in memory stale, so the
     * in-tick sweep brings them in line.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "disabled2")
    public static void toggleRearmsTheSweep(GameTestHelper helper) {
        BlockPos pos = new BlockPos(5, 3, 5);
        helper.setBlock(pos, Blocks.OBSIDIAN);
        BlockPos abs = helper.absolutePos(pos);
        LevelChunk chunk = helper.getLevel().getChunk(abs.getX() >> 4, abs.getZ() >> 4);
        helper.assertTrue(!com.strata.runtime.Regeneration.needsUpdate(
                chunk.getData(com.strata.runtime.MobLevel.BLOCK_GENERATION),
                com.strata.runtime.Regeneration.blockCurrent()),
                "A fresh chunk should already be in line with the sweep");
        DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:obsidian"));
        Config.reload();
        try {
            helper.assertTrue(DisabledContent.disabled(Blocks.OBSIDIAN), "Obsidian reads as on");
            helper.assertTrue(com.strata.runtime.Regeneration.needsUpdate(
                    chunk.getData(com.strata.runtime.MobLevel.BLOCK_GENERATION),
                    com.strata.runtime.Regeneration.blockCurrent()),
                    "The loaded chunk was not marked for the sweep after a toggle");
        } finally {
            DisabledOps.toggle(DisabledOps.BLOCK, List.of("minecraft:obsidian"));
            Config.reload();
        }
        helper.succeed();
    }
}
