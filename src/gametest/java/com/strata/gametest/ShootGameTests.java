package com.strata.gametest;

import com.strata.model.GoalRow;
import com.strata.model.GoalType;
import com.strata.runtime.ShootProjectileGoal;
import java.util.HashMap;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ANY MOB FIRES ANY PROJECTILE. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ShootGameTests {

    private ShootGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "shoot")
    public static void zombieFiresAGhastFireball(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        zombie.setNoAi(true);
        Zombie target = helper.spawn(EntityType.ZOMBIE, 4, 2, 4);
        target.setNoAi(true);

        var settings = new HashMap<>(GoalType.of("ShootProjectileGoal").defaults());
        settings.put("projectile", "minecraft:fireball");
        GoalRow row = new GoalRow(false, 2, "ShootProjectileGoal", false, settings,
                com.strata.model.GoalCondition.ALWAYS, true);
        helper.assertTrue(row.problem() == null, "The goal row is refused: " + row.problem());

        var fireball = new ShootProjectileGoal(zombie, EntityType.FIREBALL, 40, 16, 1.0, 2);
        helper.assertTrue(fireball.fire(target), "The fireball was not fired");
        AABB around = new AABB(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))).inflate(3);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(LargeFireball.class, around).isEmpty(),
                "No ghast fireball near the zombie");

        var arrow = new ShootProjectileGoal(zombie, EntityType.ARROW, 40, 16, 1.6, 5);
        helper.assertTrue(arrow.fire(target), "The arrow was not fired");
        List<Arrow> arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, around);
        helper.assertTrue(!arrows.isEmpty() && arrows.getFirst().getBaseDamage() == 5,
                "No arrow, or not the set damage");

        var wrong = new ShootProjectileGoal(zombie, EntityType.PIG, 40, 16, 1.0, 2);
        helper.assertTrue(!wrong.fire(target), "A pig was fired as a projectile");
        helper.getLevel().getEntitiesOfClass(LargeFireball.class, around.inflate(20)).forEach(e -> e.discard());
        helper.succeed();
    }

    /** An attribute an item does not have, added: armour on a diamond sword. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "item_attribute")
    public static void swordGainsArmour(GameTestHelper helper) {
        var item = net.minecraft.world.item.Items.DIAMOND_SWORD;
        var b = net.minecraft.core.component.DataComponentPatch.builder();
        com.strata.runtime.ItemStatsRuntime.patch(b, item, java.util.Map.of(
                com.strata.model.ItemStats.attributeKey("minecraft:generic.armor", "mainhand"), "4"));
        var stack = new net.minecraft.world.item.ItemStack(item);
        stack.applyComponents(b.build());
        var mods = stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        helper.assertTrue(mods != null && mods.modifiers().stream().anyMatch(e -> e.attribute().is(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) && e.modifier().amount() == 4),
                "The sword has no armour");
        helper.assertTrue(mods.modifiers().stream().anyMatch(e -> e.attribute().is(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)), "The sword lost its attack damage");
        helper.succeed();
    }
}
