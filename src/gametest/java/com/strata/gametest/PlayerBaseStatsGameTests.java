package com.strata.gametest;

import com.strata.domain.MobStatsOps;
import com.strata.runtime.PlayerBaseStats;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** THE PLAYER'S BASE STATS, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class PlayerBaseStatsGameTests {

    private PlayerBaseStatsGameTests() {
    }

    private static final String HEALTH = "minecraft:generic.max_health";

    @GameTest(template = "arena", timeoutTicks = 40, batch = "playerbase")
    public static void playerBaseStatsSetAndRestored(GameTestHelper helper) {
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5f1a0e5c-0000-4000-8000-000000000064"),
                        "strata_base_stats"));
        var up = MobStatsOps.saveOverride("minecraft:player", HEALTH, "30");
        helper.assertTrue(up.ok(), "Could not save the player's max health: " + up.first());
        player.setHealth(10);
        PlayerBaseStats.apply(player);
        double raised = player.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        float stillHurt = player.getHealth();

        var down = MobStatsOps.saveOverride("minecraft:player", HEALTH, "6");
        player.setHealth(10);
        PlayerBaseStats.apply(player);
        double lowered = player.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        float clamped = player.getHealth();

        var back = MobStatsOps.resetOverride("minecraft:player", HEALTH);
        PlayerBaseStats.apply(player);
        double restored = player.getAttribute(Attributes.MAX_HEALTH).getBaseValue();

        helper.assertTrue(raised == 30, "Base max health " + raised + ", not 30");
        helper.assertTrue(stillHurt == 10, "A raised max healed the player to " + stillHurt);
        helper.assertTrue(down.ok() && lowered == 6 && clamped == 6,
                "Max health 6: base " + lowered + ", health " + clamped);
        helper.assertTrue(back.ok() && restored == 20, "Restore left base max health " + restored + ", not the game's 20");
        helper.succeed();
    }
}
