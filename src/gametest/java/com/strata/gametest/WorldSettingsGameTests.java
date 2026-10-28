package com.strata.gametest;

import com.strata.domain.WorldSettingsOps;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * THE WORLD SETTINGS EDITOR CHANGES THE REAL WORLD: each value is set through WorldSettingsOps on the test
 * server, read back from the world itself, and the original values put back so the other tests see the world
 * they expect.
 */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class WorldSettingsGameTests {

    private WorldSettingsGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "worldsettings")
    public static void everyWorldSettingIsAppliedAndReadBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        WorldSettingsOps.Snapshot before = WorldSettingsOps.read(server);
        WorldSettingsOps.Form orig = before.form();
        long dayBase = level.getDayTime() - Math.floorMod(level.getDayTime(), 24000L);
        try {
            WorldSettingsOps.Form set = new WorldSettingsOps.Form("100", "-200", "5000", "1.5", "7", "9", "11",
                    "Hard", "Yes", "6000", "10", "Thunder", "1234", "90");
            helper.assertTrue(WorldSettingsOps.save(set).ok(), "The settings were not saved");
            var border = level.getWorldBorder();
            helper.assertTrue(border.getCenterX() == 100 && border.getCenterZ() == -200, "Centre " + border.getCenterX());
            helper.assertTrue(border.getSize() == 5000, "Size " + border.getSize());
            helper.assertTrue(border.getDamagePerBlock() == 1.5 && border.getDamageSafeZone() == 7, "Damage or safe zone");
            helper.assertTrue(border.getWarningBlocks() == 9 && border.getWarningTime() == 11, "Warning");
            helper.assertTrue(server.getWorldData().getDifficulty() == Difficulty.HARD, "Difficulty");
            helper.assertTrue(server.getWorldData().isDifficultyLocked(), "Difficulty lock");
            helper.assertTrue(Math.floorMod(level.getDayTime(), 24000L) == 6000, "Time " + level.getDayTime());
            helper.assertTrue(level.getDayTimePerTick() == 2.0f, "Day length " + level.getDayTimePerTick());
            ServerLevelData data = (ServerLevelData) level.getLevelData();
            helper.assertTrue(data.isRaining() && data.isThundering() && data.getThunderTime() == 1234, "Weather");
            helper.assertTrue(level.getSharedSpawnAngle() == 90f, "Spawn angle " + level.getSharedSpawnAngle());
            WorldSettingsOps.Form read = WorldSettingsOps.read(server).form();
            helper.assertTrue(read.weather().equals("Thunder") && read.dayMinutes().equals("10")
                    && read.difficulty().equals("Hard") && read.size().equals("5000"), "Read back: " + read);
            helper.assertTrue(WorldSettingsOps.save(new WorldSettingsOps.Form("x", "0", "1", "0", "0", "0", "0",
                    "Hard", "No", "0", "20", "Clear", "0", "0")).refusal() != null, "A bad value was not refused");
        } finally {
            WorldSettingsOps.save(orig);
            level.setDayTime(dayBase + Long.parseLong(orig.time()));
        }
        helper.assertTrue(level.getWorldBorder().getSize() == Double.parseDouble(orig.size()), "Size not restored");
        helper.succeed();
    }
}
