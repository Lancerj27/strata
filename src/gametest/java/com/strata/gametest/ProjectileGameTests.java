package com.strata.gametest;

import com.strata.data.Config;
import com.strata.data.Defaults;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.runtime.Projectiles;
import com.strata.runtime.ShootProjectileGoal;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ABILITIES FIRED THROUGH PROJECTILES. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ProjectileGameTests {

    private ProjectileGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "projectile")
    public static void aLeechSnowballHealsItsShooter(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!projectile("gt_leech", "4") || !ability("gt_leech", "life_steal", Schema.Abilities.AMOUNT, "3")) {
            helper.fail("Could not write the projectile rows");
            return;
        }
        Config.reload();
        helper.assertTrue(Projectiles.preset("gt_leech") != null, "gt_leech was not loaded");

        Zombie shooter = zombie(helper, 1, 2);
        shooter.setHealth(8.0F);
        Zombie target = zombie(helper, 4, 2);
        float targetBefore = target.getHealth();
        var goal = new ShootProjectileGoal(shooter, EntityType.SNOWBALL, "gt_leech", 40, 16, 1.6, 0);
        helper.assertTrue(goal.fire(target), "The leech snowball was not fired");

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(targetBefore - target.getHealth() > 5.0F,
                    "The target lost " + (targetBefore - target.getHealth()) + ", not its damage and the 3 stolen");
            helper.assertTrue(shooter.getHealth() > 8.0F,
                    "Life steal on impact gave the shooter nothing: health " + shooter.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "projectile")
    public static void aBroodSnowballSpawnsWhereItLands(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!projectile("gt_brood", "")
                || !ability("gt_brood", "spawn", Schema.Abilities.ENTITY, "minecraft:cow", Schema.Abilities.AMOUNT, "2")) {
            helper.fail("Could not write the projectile rows");
            return;
        }
        Config.reload();

        Zombie shooter = zombie(helper, 1, 1);
        Snowball ball = new Snowball(helper.getLevel(), shooter);
        Vec3 above = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 4, 3)));
        ball.moveTo(above.x, above.y, above.z);
        ball.shoot(0, -1, 0, 1.0F, 0.0F);
        Projectiles.prepare(ball, "gt_brood");
        helper.getLevel().addFreshEntity(ball);

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getEntities(EntityType.COW, new BlockPos(3, 2, 3), 3).size() >= 2,
                    "The snowball spawned no cows where it landed on the floor");
            helper.assertTrue(ball.isRemoved(), "The snowball is still flying");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "projectile")
    public static void aSnowballLooksLikeItsItem(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!projectile("gt_look", "") || !TableWriter.upsert(Schema.Projectiles.FILE,
                Map.of(Schema.Projectiles.NAME, "gt_look"), Map.of(Schema.Projectiles.LOOK, "minecraft:fire_charge"))) {
            helper.fail("Could not write the projectile rows");
            return;
        }
        Config.reload();
        Snowball ball = new Snowball(helper.getLevel(), zombie(helper, 1, 1));
        Projectiles.prepare(ball, "gt_look");
        helper.assertTrue(ball.getItem().is(Items.FIRE_CHARGE), "The snowball is drawn as " + ball.getItem());
        ball.discard();
        helper.succeed();
    }

    /** A projectile fired as a snowball that does only what it is given (keep_base false). */
    private static boolean projectile(String name, String damage) {
        return TableWriter.upsert(Schema.Projectiles.FILE, Map.of(Schema.Projectiles.NAME, name),
                Map.of(Schema.Projectiles.BASE, "minecraft:snowball", Schema.Projectiles.DAMAGE, damage,
                        Schema.Projectiles.KEEP_BASE, "false", Schema.Projectiles.ENABLED, "true"));
    }

    /** One On impact ability of a projectile, written the way the Abilities editor writes one. */
    private static boolean ability(String projectile, String effectClass, String... pairs) {
        Map<String, String> keys = Map.of(Schema.ProjectileEffects.PROJECTILE, projectile,
                Schema.Abilities.EFFECT_CLASS, effectClass, Schema.Abilities.TRIGGER, "on_impact");
        Map<String, String> values = new HashMap<>();
        values.put(Schema.Abilities.ENABLED, "true");
        values.put(Schema.Abilities.CHANCE, "100");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return TableWriter.upsert(Schema.ProjectileEffects.FILE, keys, values);
    }

    /** A zombie with its AI off and a hat on, so neither its walking nor the sun moves the reading. */
    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, x, 2, z);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }
}
