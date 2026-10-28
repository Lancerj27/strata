package com.strata.gametest;

import com.strata.data.Config;
import com.strata.data.Defaults;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.domain.EffectOps.EffectRow;
import com.strata.model.EffectClass;
import com.strata.runtime.EffectRuntime;
import com.strata.runtime.EffectTable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** THE SIX BEHAVIOUR CLASSES IN A REAL WORLD. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class EffectGameTests {

    private EffectGameTests() {
    }

    /** ONE OF EACH CLASS RUNS ON A MOB AND THE RESULT IS READ OFF THE MOB (acceptance 2). */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "effect_class")
    public static void eachEffectClassRunsOnAMob(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!sixRows()) {
            helper.fail("Could not write the six effect rows to abilities.csv");
            return;
        }
        Config.reload();

        EffectTable.EffectEntry boom = entry(helper, "gt_boom", EffectClass.EXPLODE);
        EffectTable.EffectEntry gift = entry(helper, "gt_gift", EffectClass.APPLY_EFFECT);
        EffectTable.EffectEntry drain = entry(helper, "gt_drain", EffectClass.LIFE_STEAL);
        EffectTable.EffectEntry share = entry(helper, "gt_share", EffectClass.ATTRIBUTE_CHANGE);
        EffectTable.EffectEntry born = entry(helper, "gt_born", EffectClass.SPAWN);
        EffectTable.EffectEntry beat = entry(helper, "gt_beat", EffectClass.DAMAGE_OVER_TIME);

        Zombie bomber = zombie(helper, 1, 1);
        Zombie blastVictim = zombie(helper, 2, 1);
        float beforeBlast = blastVictim.getHealth();
        boolean griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(false, helper.getLevel().getServer());
            EffectRuntime.apply(boom, bomber, bomber);
        } finally {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(griefing, helper.getLevel().getServer());
        }
        helper.assertTrue(blastVictim.getHealth() < beforeBlast,
                "explode dealt no damage inside its radius: health is "
                        + blastVictim.getHealth() + " of " + beforeBlast);
        helper.assertTrue(bomber.getHealth() == beforeBlast,
                "explode damaged the mob it came from, which a creeper does not");

        Zombie gifted = zombie(helper, 5, 1);
        EffectRuntime.apply(gift, gifted, gifted);
        helper.assertTrue(gifted.hasEffect(MobEffects.MOVEMENT_SPEED),
                "apply_effect put no effect on the mob");

        Zombie donor = zombie(helper, 1, 5);
        Zombie patient = zombie(helper, 3, 5);
        donor.setHealth(10.0F);
        float donorBefore = donor.getHealth();
        float patientBefore = patient.getHealth();
        EffectRuntime.apply(drain, donor, patient);
        float given = donor.getHealth() - donorBefore;
        float taken = patientBefore - patient.getHealth();
        helper.assertTrue(taken > 0.0F, "life_steal took nothing from the target");
        helper.assertTrue(given > 0.0F, "life_steal gave nothing to the holder");
        helper.assertTrue(Math.abs(given - taken) < 0.05F,
                "life_steal did not move what the target lost: holder gained "
                        + given + ", target lost " + taken);

        Zombie shared = zombie(helper, 3, 3);
        double before = attribute(shared);
        EffectRuntime.apply(share, shared, shared);
        double after = attribute(shared);
        helper.assertTrue(after > before,
                "attribute_change did not move the attribute: " + before + " to " + after);

        Zombie bornFrom = zombie(helper, 4, 2);
        EffectRuntime.apply(born, bornFrom, bornFrom);
        helper.assertTrue(!helper.getEntities(EntityType.COW, new BlockPos(4, 2, 2), 3).isEmpty(),
                "spawn put no creature near the mob");

        Zombie beaten = zombie(helper, 5, 5);
        float beatenBefore = beaten.getHealth();
        EffectRuntime.apply(beat, beaten, beaten);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(beaten.getHealth() < beatenBefore,
                    "damage_over_time never damaged the mob: health is "
                            + beaten.getHealth() + " of " + beatenBefore);
            helper.succeed();
        });
    }

    /** ONE CLASS, TWO ROWS. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "effect_rows")
    public static void poisonAndBleedDifferOnlyInCanKillAndDamageType(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!poisonAndBleedRows()) {
            helper.fail("Could not write the poison and bleed rows to abilities.csv");
            return;
        }
        Config.reload();

        EffectTable.EffectEntry poison = entry(helper, "gt_poison", EffectClass.DAMAGE_OVER_TIME);
        EffectTable.EffectEntry bleed = entry(helper, "gt_bleed", EffectClass.DAMAGE_OVER_TIME);
        String odd = unexpectedDifference(poison.row(), bleed.row());
        helper.assertTrue(odd.isEmpty(),
                "The poison and bleed rows differ in " + odd + ", which is not can kill or damage type");

        Zombie poisoned = zombie(helper, 2, 2);
        Zombie bled = zombie(helper, 4, 4);
        poisoned.setHealth(3.0F);
        bled.setHealth(3.0F);
        EffectRuntime.apply(poison, poisoned, poisoned);
        EffectRuntime.apply(bleed, bled, bled);

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(poisoned.getHealth() < 3.0F,
                    "the poison row dealt no damage: health is " + poisoned.getHealth());
            helper.assertTrue(bled.getHealth() <= 0.0F,
                    "the bleed row dealt no damage: health is " + bled.getHealth());
            helper.assertTrue(poisoned.getHealth() > 0.0F,
                    "poison with can_kill no took the last heart: health is "
                            + poisoned.getHealth());
            helper.assertTrue("minecraft:magic".equals(lastSource(poisoned)),
                    "poison did not deal its own damage type: " + lastSource(poisoned));
            helper.assertTrue("minecraft:wither".equals(lastSource(bled)),
                    "bleed did not deal its own damage type: " + lastSource(bled));
            helper.succeed();
        });
    }

    /** CLASS ROWS FIRE FROM THEIR TRIGGERS, WITH NO LEGACY ABILITY IN THE PROFILE. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "effect_triggers")
    public static void classRowsFireFromTheirTriggers(GameTestHelper helper) {
        Defaults.writeIfMissing();
        boolean written = row("gt_dawn", map(
                        Schema.Abilities.EFFECT_CLASS, "apply_effect",
                        Schema.Abilities.TRIGGER, "on_spawn",
                        Schema.Abilities.PARAMETER, "minecraft:speed",
                        Schema.Abilities.DURATION, "400"))
                && row("gt_sting", map(
                        Schema.Abilities.EFFECT_CLASS, "apply_effect",
                        Schema.Abilities.TRIGGER, "on_hit",
                        Schema.Abilities.PARAMETER, "minecraft:slowness",
                        Schema.Abilities.DURATION, "400"))
                && row("gt_sunny", map(
                        Schema.Abilities.EFFECT_CLASS, "apply_effect",
                        Schema.Abilities.TRIGGER, "in_sunlight",
                        Schema.Abilities.PARAMETER, "minecraft:strength",
                        Schema.Abilities.DURATION, "400"));
        if (!written) {
            helper.fail("Could not write the trigger rows to abilities.csv");
            return;
        }
        Config.reload();
        for (String trigger : List.of("on_spawn", "on_hit", "in_sunlight")) {
            helper.assertTrue(EffectTable.hasTrigger(trigger), "No loaded row names " + trigger);
        }

        Zombie dawn = zombie(helper, 1, 1);
        dawn.setData(com.strata.runtime.MobLevel.VARIANT_ID, "gt_dawn");
        dawn.setData(com.strata.runtime.MobLevel.SPAWN_TRIGGER_PENDING, true);
        com.strata.runtime.VariantHandler.fireSpawnTriggers(dawn);
        helper.assertTrue(dawn.hasEffect(MobEffects.MOVEMENT_SPEED), "on_spawn put no speed on the mob");
        dawn.removeAllEffects();
        com.strata.runtime.VariantHandler.fireSpawnTriggers(dawn);
        helper.assertFalse(dawn.hasEffect(MobEffects.MOVEMENT_SPEED), "on_spawn fired a second time");

        Zombie sting = zombie(helper, 3, 1);
        sting.setData(com.strata.runtime.MobLevel.VARIANT_ID, "gt_sting");
        Zombie victim = zombie(helper, 5, 1);
        victim.hurt(helper.getLevel().damageSources().mobAttack(sting), 1.0F);
        helper.assertTrue(victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "on_hit put no slowness on the victim");

        helper.getLevel().setDayTime(6000);
        helper.getLevel().setWeatherParameters(6000, 0, false, false);
        Zombie sunny = zombie(helper, 3, 4);
        sunny.setData(com.strata.runtime.MobLevel.VARIANT_ID, "gt_sunny");
        helper.succeedWhen(() -> helper.assertTrue(sunny.hasEffect(MobEffects.DAMAGE_BOOST),
                "in_sunlight put no strength on the mob"));
    }

    /** HEAL, AURA AND KILL, AND AN OLD AURA ROW READ AS A CLASS ROW. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "effect_new_classes")
    public static void healAuraKillRun(GameTestHelper helper) {
        Defaults.writeIfMissing();
        boolean written = row("gt_mender", map(
                        Schema.Abilities.EFFECT_CLASS, "heal",
                        Schema.Abilities.TRIGGER, "permanent",
                        Schema.Abilities.AMOUNT, "4"))
                && row("gt_queen", map(
                        Schema.Abilities.EFFECT_CLASS, "aura",
                        Schema.Abilities.TRIGGER, "permanent",
                        Schema.Abilities.ENTITY, "minecraft:spider",
                        Schema.Abilities.PARAMETER, "minecraft:haste",
                        Schema.Abilities.AMOUNT, "2",
                        Schema.Abilities.RADIUS, "5"))
                && row("gt_doomed", map(
                        Schema.Abilities.EFFECT_CLASS, "kill",
                        Schema.Abilities.TRIGGER, "on_spawn"))
                && row("gt_old_aura", map(
                        Schema.Abilities.ABILITY, "aura_mobs",
                        Schema.Abilities.PARAMETER, "minecraft:speed",
                        Schema.Abilities.AMOUNT, "0",
                        Schema.Abilities.INTERVAL_TICKS, "20",
                        Schema.Abilities.RADIUS, "5"));
        if (!written) {
            helper.fail("Could not write the rows to abilities.csv");
            return;
        }
        Config.reload();

        Zombie mender = zombie(helper, 1, 1);
        mender.setHealth(4.0F);
        EffectRuntime.apply(entry(helper, "gt_mender", EffectClass.HEAL), mender, mender);
        helper.assertTrue(mender.getHealth() == 8.0F, "heal gave back " + (mender.getHealth() - 4.0F) + ", expected 4");

        Zombie queen = zombie(helper, 2, 2);
        net.minecraft.world.entity.monster.Spider spider = helper.spawn(EntityType.SPIDER, 3, 2, 2);
        spider.setNoAi(true);
        Zombie bystander = zombie(helper, 2, 3);
        EffectRuntime.apply(entry(helper, "gt_queen", EffectClass.AURA), queen, queen);
        helper.assertTrue(spider.hasEffect(MobEffects.DIG_SPEED), "aura gave the spider no haste");
        helper.assertFalse(bystander.hasEffect(MobEffects.DIG_SPEED), "aura reached a creature it does not name");
        helper.assertFalse(queen.hasEffect(MobEffects.DIG_SPEED), "aura gave itself its effect");

        EffectTable.EffectEntry old = entry(helper, "gt_old_aura", EffectClass.AURA);
        Zombie oldQueen = zombie(helper, 4, 4);
        Zombie near = zombie(helper, 4, 3);
        EffectRuntime.apply(old, oldQueen, oldQueen);
        helper.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SPEED), "the old aura_mobs row gave no speed");

        Zombie doomed = zombie(helper, 1, 4);
        EffectRuntime.apply(entry(helper, "gt_doomed", EffectClass.KILL), doomed, doomed);
        helper.assertFalse(doomed.isAlive(), "kill left the mob alive");
        helper.succeed();
    }

    /**
     * A ROW'S LOOK: its own particle hides the game's swirl, "none" hides the icon, and blank keeps both.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "effect_look")
    public static void rowLookIsApplied(GameTestHelper helper) {
        Defaults.writeIfMissing();
        boolean written = row("gt_plain", map(
                        Schema.Abilities.EFFECT_CLASS, "apply_effect",
                        Schema.Abilities.TRIGGER, "on_spawn",
                        Schema.Abilities.PARAMETER, "minecraft:speed"))
                && row("gt_styled", map(
                        Schema.Abilities.EFFECT_CLASS, "apply_effect",
                        Schema.Abilities.TRIGGER, "on_spawn",
                        Schema.Abilities.PARAMETER, "minecraft:speed",
                        Schema.Abilities.PARTICLE, "minecraft:flame",
                        Schema.Abilities.HUD_ICON, "none"));
        if (!written) {
            helper.fail("Could not write the rows to abilities.csv");
            return;
        }
        Config.reload();
        Zombie plain = zombie(helper, 1, 1);
        EffectRuntime.apply(entry(helper, "gt_plain", EffectClass.APPLY_EFFECT), plain, plain);
        var p = plain.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(p != null && p.isVisible() && p.showIcon(), "a blank look lost the game's swirl or icon");
        Zombie styled = zombie(helper, 3, 1);
        EffectRuntime.apply(entry(helper, "gt_styled", EffectClass.APPLY_EFFECT), styled, styled);
        var s = styled.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(s != null && !s.isVisible(), "an own particle kept the game's swirl");
        helper.assertTrue(s != null && !s.showIcon(), "HUD icon none still shows an icon");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the rows

    /** The six rows, one per variant, so each is fetchable by its own name. */
    private static boolean sixRows() {
        return row("gt_boom", map(
                        Schema.Abilities.EFFECT_CLASS, "explode",
                        Schema.Abilities.DAMAGE, "3",
                        Schema.Abilities.RADIUS, "2",
                        Schema.Abilities.DAMAGE_TYPE, "minecraft:generic"))
                && row("gt_gift", map(
                        Schema.Abilities.EFFECT_CLASS, "apply_effect",
                        Schema.Abilities.PARAMETER, "minecraft:speed",
                        Schema.Abilities.AMOUNT, "1",
                        Schema.Abilities.DURATION, "400"))
                && row("gt_drain", map(
                        Schema.Abilities.EFFECT_CLASS, "life_steal",
                        Schema.Abilities.AMOUNT, "4"))
                && row("gt_share", map(
                        Schema.Abilities.EFFECT_CLASS, "attribute_change",
                        Schema.Abilities.PARAMETER, "minecraft:generic.movement_speed",
                        Schema.Abilities.AMOUNT, "0.5",
                        Schema.Abilities.DURATION, "400",
                        Schema.Abilities.CURE, "minecraft:milk_bucket"))
                && row("gt_born", map(
                        Schema.Abilities.EFFECT_CLASS, "spawn",
                        Schema.Abilities.PARAMETER, "minecraft:cow",
                        Schema.Abilities.AMOUNT, "1"))
                && row("gt_beat", map(
                        Schema.Abilities.EFFECT_CLASS, "damage_over_time",
                        Schema.Abilities.DAMAGE, "2",
                        Schema.Abilities.INTERVAL_TICKS, "10",
                        Schema.Abilities.DURATION, "100",
                        Schema.Abilities.CAN_KILL, "false",
                        Schema.Abilities.DAMAGE_TYPE, "minecraft:magic"));
    }

    /**
     * The poison and the bleed, built from ONE map so the two can only differ where the two puts below say
     * they do.
     */
    private static boolean poisonAndBleedRows() {
        Map<String, String> poison = map(
                Schema.Abilities.EFFECT_CLASS, "damage_over_time",
                Schema.Abilities.DAMAGE, "5",
                Schema.Abilities.INTERVAL_TICKS, "10",
                Schema.Abilities.DURATION, "60",
                Schema.Abilities.CAN_KILL, "false",
                Schema.Abilities.DAMAGE_TYPE, "minecraft:magic");

        Map<String, String> bleed = new HashMap<>(poison);
        bleed.put(Schema.Abilities.CAN_KILL, "true");
        bleed.put(Schema.Abilities.DAMAGE_TYPE, "minecraft:wither");
        return row("gt_poison", poison) && row("gt_bleed", bleed);
    }

    /** One row written the way the editor writes one: identified by its own variant. */
    private static boolean row(String variant, Map<String, String> values) {
        Map<String, String> keys = map(
                Schema.Abilities.VARIANT_NAME, variant,
                Schema.Abilities.SCOPE, "global",
                Schema.Abilities.SCOPE_ID, "");
        Map<String, String> whole = new HashMap<>(keys);
        whole.put(Schema.Abilities.ENABLED, "true");
        whole.putAll(values);
        return TableWriter.upsert(Schema.Abilities.FILE, keys, whole);
    }

    /** Alternating key, value pairs, so a row reads like the column it names. */
    private static Map<String, String> map(String... pairs) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    // ---------------------------------------------------------------- reading

    /**
     * The one row of this variant and class, or the test fails here with a name: a loader that skipped the
     * row, or indexed it under another scope, says so as itself rather than as a null further down.
     */
    private static EffectTable.EffectEntry entry(GameTestHelper helper, String variant, EffectClass want) {
        List<EffectTable.EffectEntry> rows = EffectTable.forVariant(variant, "", "");
        if (rows.size() != 1) {
            helper.fail(variant + " gave " + rows.size() + " effect rows, expected 1");
            return null;
        }
        EffectTable.EffectEntry entry = rows.get(0);
        if (entry.row().effectClass() != want) {
            helper.fail(variant + " gave the class " + entry.row().effectClass() + ", expected " + want);
            return null;
        }
        return entry;
    }

    /** One zombie, with its AI off so it cannot walk out of the reading, and WITH A HAT ON. */
    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, x, 2, z);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    private static double attribute(Zombie zombie) {
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            throw new IllegalStateException("A zombie has no movement speed to move");
        }
        return speed.getValue();
    }

    /** The id of the damage type the mob was last hurt with, or "none". */
    private static String lastSource(Zombie zombie) {
        DamageSource source = zombie.getLastDamageSource();
        if (source == null) {
            return "none";
        }
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unkeyed");
    }

    /**
     * What the two rows differ in, or "" when the only differences are the two the acceptance allows AND BOTH
     * OF THEM ACTUALLY DIFFER.
     */
    private static String unexpectedDifference(EffectRow poison, EffectRow bleed) {
        if (poison.effectClass() != bleed.effectClass()) {
            return "effect class";
        }
        if (!poison.trigger().equals(bleed.trigger())) {
            return "trigger";
        }
        if (poison.chance() != bleed.chance()) {
            return "chance";
        }
        if (!poison.parameter().equals(bleed.parameter())) {
            return "parameter";
        }
        if (poison.amount() != bleed.amount()) {
            return "amount";
        }
        if (poison.interval() != bleed.interval()) {
            return "interval";
        }
        if (poison.radius() != bleed.radius()) {
            return "radius";
        }
        if (poison.damage() != bleed.damage()) {
            return "damage";
        }
        if (poison.duration() != bleed.duration()) {
            return "duration";
        }
        if (!poison.particle().equals(bleed.particle())) {
            return "particle";
        }
        if (!poison.hudIcon().equals(bleed.hudIcon())) {
            return "hud icon";
        }
        if (!poison.cure().equals(bleed.cure())) {
            return "cure";
        }
        if (poison.canKill() == bleed.canKill()) {
            return "can kill, which is the same in both";
        }
        if (poison.damageType().equals(bleed.damageType())) {
            return "damage type, which is the same in both";
        }
        return "";
    }

    /** Explode and die: the blast hurts what is near, and the mob is gone as an exploding creeper is. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "effects")
    public static void explodeAndDieRemovesTheMob(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!row("gt_creeper", map(Schema.Abilities.EFFECT_CLASS, "explode_and_die", Schema.Abilities.DAMAGE, "3",
                Schema.Abilities.RADIUS, "2", Schema.Abilities.DAMAGE_TYPE, "minecraft:generic"))) {
            helper.fail("Could not write the explode_and_die row");
            return;
        }
        Config.reload();
        EffectTable.EffectEntry boom = entry(helper, "gt_creeper", EffectClass.EXPLODE_AND_DIE);
        Zombie bomber = zombie(helper, 1, 1);
        Zombie victim = zombie(helper, 2, 1);
        float before = victim.getHealth();
        boolean griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
            EffectRuntime.apply(boom, bomber, bomber);
        } finally {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
        }
        helper.assertTrue(victim.getHealth() < before, "explode_and_die dealt no damage inside its radius");
        helper.assertTrue(bomber.isRemoved(), "explode_and_die left the mob it came from");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "effects")
    public static void explodeWithCanKillHurtsItself(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!row("gt_selfboom", map(Schema.Abilities.EFFECT_CLASS, "explode_and_die", Schema.Abilities.DAMAGE, "1000",
                Schema.Abilities.RADIUS, "2", Schema.Abilities.DAMAGE_TYPE, "minecraft:generic",
                Schema.Abilities.CAN_KILL, "true"))) {
            helper.fail("Could not write the self-hurting row");
            return;
        }
        Config.reload();
        EffectTable.EffectEntry boom = entry(helper, "gt_selfboom", EffectClass.EXPLODE_AND_DIE);
        Zombie bomber = zombie(helper, 1, 1);
        boolean griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
            EffectRuntime.apply(boom, bomber, bomber);
        } finally {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
        }
        helper.assertTrue(bomber.isDeadOrDying(), "The blast did not kill its own source");
        helper.assertTrue(bomber.getRemovalReason() != net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,
                "A mob its own blast killed was discarded, not killed");
        helper.succeed();
    }
}
