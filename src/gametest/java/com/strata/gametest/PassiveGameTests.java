package com.strata.gametest;

import com.strata.runtime.GoalsRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** THE PASSIVE SWITCH ON REAL MOBS. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class PassiveGameTests {

    private PassiveGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "passive")
    public static void passiveDropsOnlyPlayerHunting(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        int huntersBefore = hunters(zombie);
        int dropped = GoalsRuntime.dropPlayerHunting(zombie);
        helper.assertTrue(dropped == 1, "The zombie lost " + dropped + " goals, expected its one player goal");
        helper.assertTrue(hunters(zombie) == huntersBefore - 1,
                "The zombie's villager and iron golem goals did not stay");

        Spider spider = helper.spawn(EntityType.SPIDER, 3, 2, 1);
        int spiderDropped = GoalsRuntime.dropPlayerHunting(spider);
        helper.assertTrue(spiderDropped >= 1, "The spider's own player goal (a subclass) was not dropped");
        helper.succeed();
    }

    /** A mob set to not convert does not: its conversion is cancelled, last. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "converts")
    public static void convertsOffStopsConversion(GameTestHelper helper) {
        com.strata.data.Defaults.writeIfMissing();
        var saved = com.strata.domain.VariantOps.saveSwitch("minecraft:zombie", "", "global", "",
                com.strata.data.Schema.Variants.CONVERTS, "Off");
        helper.assertTrue(saved.ok(), "Converts could not be set: " + saved.refusal());
        com.strata.data.Config.reload();
        try {
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
            var event = new net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Pre(zombie,
                    EntityType.DROWNED, t -> { });
            com.strata.runtime.MobScalingSwitch.onConvert(event);
            helper.assertTrue(event.isCanceled(), "A zombie set to not convert was let convert");
            Spider spider = helper.spawn(EntityType.SPIDER, 4, 2, 2);
            var other = new net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Pre(spider,
                    EntityType.CAVE_SPIDER, t -> { });
            com.strata.runtime.MobScalingSwitch.onConvert(other);
            helper.assertTrue(!other.isCanceled(), "A mob with no switch was stopped");
        } finally {
            com.strata.domain.VariantOps.saveSwitch("minecraft:zombie", "", "global", "",
                    com.strata.data.Schema.Variants.CONVERTS, "Default");
            com.strata.data.Config.reload();
        }
        helper.succeed();
    }

    private static int hunters(net.minecraft.world.entity.Mob mob) {
        return (int) mob.targetSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof NearestAttackableTargetGoal<?>).count();
    }
}
