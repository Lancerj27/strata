package com.strata.gametest;

import com.strata.data.Config;
import com.strata.data.Defaults;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.runtime.MobProperties;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A MOB'S PROPERTY VALUES DO WHAT THEY SAY: the property tables used to be read and never applied. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class PropertyGameTests {

    private PropertyGameTests() {
    }

    private static final Map<String, String> VINDICATOR_ROW = Map.of(Schema.MobLootXp.SCOPE, "",
            Schema.MobLootXp.SCOPE_ID, "", Schema.MobLootXp.ENTITY, "minecraft:vindicator",
            Schema.MobLootXp.VARIANT_NAME, "", Schema.MobLootXp.IS_VARIANT, "false");

    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties")
    public static void aMobsLootAndXpRowApplies(GameTestHelper helper) {
        Defaults.writeIfMissing();
        boolean written = TableWriter.upsert(Schema.MobLootXp.FILE, VINDICATOR_ROW, Map.of(
                Schema.MobLootXp.DEATH_LOOT_TABLE, "minecraft:entities/cow", Schema.MobLootXp.XP_MIN, "50",
                Schema.MobLootXp.XP_MAX, "50", Schema.MobLootXp.ALWAYS_DROP_EQUIPMENT, "true",
                Schema.MobLootXp.ENABLED, "true"));
        if (!written) {
            helper.fail("Could not write the loot and XP row");
            return;
        }
        Config.reload();
        try {
            Vindicator mob = helper.spawn(EntityType.VINDICATOR, 2, 2, 2);
            mob.setNoAi(true);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            helper.assertTrue(Integer.valueOf(50).equals(MobProperties.forMob(mob).xpMin), "The row was not read for the mob");
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            mob.setLastHurtByPlayer(player);
            int[] dropped = {-1};
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent> watch =
                    e -> { if (e.getEntity() == mob) dropped[0] = e.getDroppedExperience(); };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, watch);
            mob.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
            helper.runAfterDelay(5, () -> {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(watch);
                AABB around = new AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(12);
                int xp = dropped[0];
                var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
                helper.assertTrue(xp == 50, "It dropped " + xp + " experience, not its 50");
                helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.BEEF)),
                        "Its own loot table (the cow's) dropped no beef");
                helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.IRON_AXE)),
                        "Always drop equipment: its axe did not drop");
                helper.succeed();
            });
        } finally {
            TableWriter.delete(Schema.MobLootXp.FILE, VINDICATOR_ROW);
        }
    }

    private static final Map<String, String> STRAY_ROW = Map.of(Schema.MobCombat.SCOPE, "",
            Schema.MobCombat.SCOPE_ID, "", Schema.MobCombat.ENTITY, "minecraft:stray",
            Schema.MobCombat.VARIANT_NAME, "", Schema.MobCombat.IS_VARIANT, "false");

    private static final Map<String, String> HUSK_ROW = Map.of(Schema.MobCombat.SCOPE, "",
            Schema.MobCombat.SCOPE_ID, "", Schema.MobCombat.ENTITY, "minecraft:husk",
            Schema.MobCombat.VARIANT_NAME, "", Schema.MobCombat.IS_VARIANT, "false");

    /**
     * A ranged mob's combat row: what it fires becomes its projectile (one of the designer's), its bow goal
     * takes its interval, it ignores the damage it is immune to, and it reaches.
     */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_combat")
    public static void aMobsCombatRowApplies(GameTestHelper helper) {
        Defaults.writeIfMissing();
        boolean written = TableWriter.upsert(Schema.Projectiles.FILE, Map.of(Schema.Projectiles.NAME, "gt_ice"),
                Map.of(Schema.Projectiles.BASE, "minecraft:snowball", Schema.Projectiles.ENABLED, "true"))
                && TableWriter.upsert(Schema.MobCombat.FILE, STRAY_ROW, Map.of(
                Schema.MobCombat.PROJECTILE_TYPE, "strata:gt_ice", Schema.MobCombat.RANGED_ATTACK_INTERVAL, "7",
                Schema.MobCombat.DAMAGE_IMMUNITIES, "arrow, #minecraft:is_fire", Schema.MobCombat.REACH, "5",
                Schema.MobCombat.ENABLED, "true"));
        if (!written) {
            helper.fail("Could not write the combat rows");
            return;
        }
        Config.reload();
        try {
            var stray = helper.spawn(EntityType.STRAY, 1, 2, 1);
            stray.setNoAi(true);
            stray.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));

            var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, helper.getLevel());
            arrow.setOwner(stray);
            var at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 3.5, 1.5));
            arrow.moveTo(at.x, at.y, at.z);
            arrow.setDeltaMovement(1, 0, 0);
            helper.getLevel().addFreshEntity(arrow);
            AABB around = new AABB(helper.absolutePos(new BlockPos(1, 3, 1))).inflate(3);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Snowball.class,
                    around, s -> s.getPersistentData().getString(com.strata.runtime.Projectiles.TAG).equals("gt_ice")).size() == 1,
                    "Its arrow was not fired as its projectile, strata:gt_ice");
            helper.assertTrue(arrow.isRemoved() || !arrow.isAddedToLevel(), "The arrow it was replacing is still in the world");

            float before = stray.getHealth();
            stray.hurt(helper.getLevel().damageSources().inFire(), 4.0F);
            stray.hurt(helper.getLevel().damageSources().arrow(arrow, null), 4.0F);
            helper.assertTrue(stray.getHealth() == before, "It took damage it is immune to: " + (before - stray.getHealth()));

            var far = helper.spawn(EntityType.PIG, 5, 2, 1);
            far.setNoAi(true);
            helper.assertTrue(stray.isWithinMeleeAttackRange(far), "A reach of 5 did not reach a pig 4 blocks off");

            helper.runAfterDelay(3, () -> {
                boolean seven = stray.goalSelector.getAvailableGoals().stream().anyMatch(w ->
                        w.getGoal() instanceof net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<?> bow
                                && intervalOf(bow) == 7);
                helper.assertTrue(seven, "Its bow goal does not wait its 7 ticks between shots");
                helper.succeed();
            });
        } finally {
            TableWriter.delete(Schema.MobCombat.FILE, STRAY_ROW);
            TableWriter.delete(Schema.Projectiles.FILE, Map.of(Schema.Projectiles.NAME, "gt_ice"));
        }
    }

    /** A melee mob's area hit: the hit on its target also lands on the creature beside it. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "properties_melee")
    public static void aMobsAreaHitLandsAround(GameTestHelper helper) {
        Defaults.writeIfMissing();
        if (!TableWriter.upsert(Schema.MobCombat.FILE, HUSK_ROW, Map.of(Schema.MobCombat.AOE_RADIUS, "3",
                Schema.MobCombat.ENABLED, "true"))) {
            helper.fail("Could not write the combat row");
            return;
        }
        Config.reload();
        try {
            var husk = helper.spawn(EntityType.HUSK, 1, 2, 2);
            husk.setNoAi(true);
            var target = helper.spawn(EntityType.PIG, 2, 2, 2);
            var beside = helper.spawn(EntityType.PIG, 3, 2, 2);
            target.setNoAi(true);
            beside.setNoAi(true);
            float before = beside.getHealth();
            husk.doHurtTarget(target);
            helper.assertTrue(beside.getHealth() < before, "The hit did not land on the pig beside its target");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobCombat.FILE, HUSK_ROW);
        }
    }

    private static int intervalOf(net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<?> goal) {
        try {
            var f = net.minecraft.world.entity.ai.goal.RangedBowAttackGoal.class.getDeclaredField("attackIntervalMin");
            f.setAccessible(true);
            return f.getInt(goal);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** A mob's flags row: its immunities, item pickup, persistence, climbing and walking on water. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "properties_flags")
    public static void aMobsFlagsRowApplies(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> row = Map.of(Schema.MobFlags.SCOPE, "", Schema.MobFlags.SCOPE_ID, "",
                Schema.MobFlags.ENTITY, "minecraft:evoker", Schema.MobFlags.VARIANT_NAME, "",
                Schema.MobFlags.IS_VARIANT, "false");
        if (!TableWriter.upsert(Schema.MobFlags.FILE, row, Map.of(Schema.MobFlags.FIRE_IMMUNE, "true",
                Schema.MobFlags.FALL_DAMAGE_IMMUNE, "true", Schema.MobFlags.CAN_PICK_UP_ITEMS, "true",
                Schema.MobFlags.PERSISTENT, "true", Schema.MobFlags.CAN_CLIMB, "true",
                Schema.MobFlags.CAN_WALK_ON_WATER, "true", Schema.MobFlags.ENABLED, "true"))) {
            helper.fail("Could not write the flags row");
            return;
        }
        Config.reload();
        try {
            var evoker = helper.spawn(EntityType.EVOKER, 2, 2, 2);
            evoker.setNoAi(true);
            float before = evoker.getHealth();
            evoker.hurt(helper.getLevel().damageSources().inFire(), 5.0F);
            evoker.hurt(helper.getLevel().damageSources().fall(), 5.0F);
            helper.assertTrue(evoker.getHealth() == before, "Fire or a fall hurt a mob immune to both");
            helper.assertTrue(evoker.canPickUpLoot(), "It cannot pick up items");
            helper.assertTrue(evoker.isPersistenceRequired(), "It is not persistent");
            helper.assertTrue(evoker.canStandOnFluid(net.minecraft.world.level.material.Fluids.WATER.defaultFluidState()),
                    "It cannot stand on water");
            evoker.horizontalCollision = true;
            helper.assertTrue(evoker.onClimbable(), "Against a wall it cannot climb");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobFlags.FILE, row);
        }
    }

    /** The last mob sound the level was asked to play: what reached the game after Strata. */
    private static volatile float[] heard;

    static {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition e) ->
                        heard = new float[]{e.getNewVolume(), e.getNewPitch()});
    }

    /** A mob's sounds row: its hurt sound plays at its own volume and pitch. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "properties_sounds")
    public static void aMobsSoundVolumeAndPitchApply(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> row = Map.of(Schema.MobSounds.SCOPE, "", Schema.MobSounds.SCOPE_ID, "",
                Schema.MobSounds.ENTITY, "minecraft:witch", Schema.MobSounds.VARIANT_NAME, "",
                Schema.MobSounds.IS_VARIANT, "false");
        if (!TableWriter.upsert(Schema.MobSounds.FILE, row, Map.of(Schema.MobSounds.HURT_VOLUME, "0.25",
                Schema.MobSounds.HURT_PITCH, "1.75", Schema.MobSounds.ENABLED, "true"))) {
            helper.fail("Could not write the sounds row");
            return;
        }
        Config.reload();
        try {
            var witch = helper.spawn(EntityType.WITCH, 2, 2, 2);
            witch.setNoAi(true);
            heard = null;
            witch.playSound(net.minecraft.sounds.SoundEvents.WITCH_HURT, 1.0F, 1.0F);
            float[] got = heard;
            helper.assertTrue(got != null && got[0] == 0.25F && got[1] == 1.75F,
                    "Its hurt sound played at " + (got == null ? "nothing" : got[0] + " / " + got[1]) + ", not 0.25 / 1.75");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobSounds.FILE, row);
        }
    }

    /** Flying and Swimming swap how a mob moves, and taking them off puts its own back. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "properties_movement")
    public static void flyingAndSwimmingSwapHowAMobMoves(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> row = Map.of(Schema.MobFlags.SCOPE, "", Schema.MobFlags.SCOPE_ID, "",
                Schema.MobFlags.ENTITY, "minecraft:mooshroom", Schema.MobFlags.VARIANT_NAME, "",
                Schema.MobFlags.IS_VARIANT, "false");
        try {
            TableWriter.upsert(Schema.MobFlags.FILE, row, Map.of(Schema.MobFlags.FLYING, "true", Schema.MobFlags.ENABLED, "true"));
            Config.reload();
            var cow = helper.spawn(EntityType.MOOSHROOM, 2, 2, 2);
            var ownControl = cow.getMoveControl();
            helper.assertTrue(cow.getMoveControl() instanceof net.minecraft.world.entity.ai.control.FlyingMoveControl
                            && cow.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
                            && cow.isNoGravity(), "Flying did not give it the flying move control and path finder");

            TableWriter.upsert(Schema.MobFlags.FILE, row, Map.of(Schema.MobFlags.FLYING, "", Schema.MobFlags.SWIMMING, ""));
            Config.reload();
            helper.assertTrue(!(cow.getMoveControl() instanceof net.minecraft.world.entity.ai.control.FlyingMoveControl)
                    && !cow.isNoGravity(), "Taking Flying off did not put its own movement back");

            TableWriter.upsert(Schema.MobFlags.FILE, row, Map.of(Schema.MobFlags.SWIMMING, "true"));
            Config.reload();
            helper.assertTrue(cow.getMoveControl() instanceof net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl
                            && cow.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation,
                    "Swimming did not give it the swimming move control and path finder");
            helper.assertTrue(ownControl != null, "No move control of its own");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobFlags.FILE, row);
        }
    }

    /** On flee: a mob that starts to panic runs its On flee abilities. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "properties_flee")
    public static void onFleeRunsAsAMobPanics(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> keys = Map.of(Schema.Abilities.VARIANT_NAME, "gt_flee", Schema.Abilities.SCOPE, "global",
                Schema.Abilities.SCOPE_ID, "");
        Map<String, String> values = new java.util.HashMap<>(keys);
        values.put(Schema.Abilities.ENABLED, "true");
        values.put(Schema.Abilities.EFFECT_CLASS, "apply_effect");
        values.put(Schema.Abilities.TRIGGER, "on_flee");
        values.put(Schema.Abilities.PARAMETER, "minecraft:speed");
        values.put(Schema.Abilities.DURATION, "100");
        values.put(Schema.Abilities.CHANCE, "100");
        if (!TableWriter.upsert(Schema.Abilities.FILE, keys, values)) {
            helper.fail("Could not write the On flee row");
            return;
        }
        Config.reload();
        try {
            var rabbit = helper.spawn(EntityType.RABBIT, 2, 2, 2);
            rabbit.setData(com.strata.runtime.MobLevel.VARIANT_ID, "gt_flee");
            var panic = rabbit.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal())
                    .filter(g -> g instanceof net.minecraft.world.entity.ai.goal.PanicGoal).findFirst();
            helper.assertTrue(panic.isPresent(), "A rabbit has no panic goal");
            panic.get().start();
            helper.assertTrue(rabbit.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
                    "Starting to panic did not run its On flee ability");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.Abilities.FILE, keys);
        }
    }

    /**
     * A boss gets the game's boss bar: the colour and music of its row, the players within its range on the
     * list, and a progress that follows its health.
     */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_boss")
    public static void aBossShowsItsBarToPlayersNearIt(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> flagsRow = Map.of(Schema.MobFlags.SCOPE, "", Schema.MobFlags.SCOPE_ID, "",
                Schema.MobFlags.ENTITY, "minecraft:ravager", Schema.MobFlags.VARIANT_NAME, "",
                Schema.MobFlags.IS_VARIANT, "false");
        Map<String, String> bossRow = Map.of(Schema.MobBoss.SCOPE, "", Schema.MobBoss.SCOPE_ID, "",
                Schema.MobBoss.ENTITY, "minecraft:ravager", Schema.MobBoss.VARIANT_NAME, "",
                Schema.MobBoss.IS_VARIANT, "false");
        boolean written = TableWriter.upsert(Schema.MobFlags.FILE, flagsRow, Map.of(Schema.MobFlags.BOSS, "true",
                Schema.MobFlags.ENABLED, "true"))
                && TableWriter.upsert(Schema.MobBoss.FILE, bossRow, Map.of(Schema.MobBoss.BAR_COLOR, "red",
                Schema.MobBoss.BAR_RANGE, "20", Schema.MobBoss.BOSS_MUSIC, "true", Schema.MobBoss.ENABLED, "true"));
        if (!written) {
            TableWriter.delete(Schema.MobFlags.FILE, flagsRow);
            TableWriter.delete(Schema.MobBoss.FILE, bossRow);
            helper.fail("Could not write the boss rows");
            return;
        }
        Config.reload();
        try {
            var ravager = helper.spawn(EntityType.RAVAGER, 2, 2, 2);
            ravager.setNoAi(true);
            var player = helper.makeMockServerPlayerInLevel();
            player.moveTo(ravager.getX() + 3, ravager.getY(), ravager.getZ(), 0, 0);
            helper.runAfterDelay(5, () -> {
                try {
                    var bar = com.strata.runtime.MobPropertyEffects.bossEventOf(ravager);
                    helper.assertTrue(bar != null, "A boss has no boss bar");
                    helper.assertTrue(bar.getColor() == net.minecraft.world.BossEvent.BossBarColor.RED,
                            "Its bar is " + bar.getColor() + ", not red");
                    helper.assertTrue(bar.shouldPlayBossMusic(), "Its bar plays no boss music");
                    helper.assertTrue(bar.getPlayers().contains(player), "The player beside it is not on its bar");
                    ravager.hurt(helper.getLevel().damageSources().generic(), 20.0F);
                    helper.runAfterDelay(3, () -> {
                        try {
                            helper.assertTrue(bar.getProgress() < 1.0F, "Its bar did not follow its health");
                            helper.succeed();
                        } finally {
                            TableWriter.delete(Schema.MobFlags.FILE, flagsRow);
                            TableWriter.delete(Schema.MobBoss.FILE, bossRow);
                            Config.reload();
                        }
                    });
                } catch (RuntimeException e) {
                    TableWriter.delete(Schema.MobFlags.FILE, flagsRow);
                    TableWriter.delete(Schema.MobBoss.FILE, bossRow);
                    Config.reload();
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            TableWriter.delete(Schema.MobFlags.FILE, flagsRow);
            TableWriter.delete(Schema.MobBoss.FILE, bossRow);
            Config.reload();
            throw e;
        }
    }

    /**
     * Drop chance per slot: a mainhand chance of 1 drops the item, an offhand chance of 0 keeps it - the
     * game's own 8.5 percent would make either a coin flip.
     */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_drop_chance")
    public static void aSlotsDropChanceApplies(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> row = Map.of(Schema.MobLootXp.SCOPE, "", Schema.MobLootXp.SCOPE_ID, "",
                Schema.MobLootXp.ENTITY, "minecraft:piglin_brute", Schema.MobLootXp.VARIANT_NAME, "",
                Schema.MobLootXp.IS_VARIANT, "false");
        if (!TableWriter.upsert(Schema.MobLootXp.FILE, row, Map.of(Schema.MobLootXp.DROP_CHANCE_MAINHAND, "1.0",
                Schema.MobLootXp.DROP_CHANCE_OFFHAND, "0", Schema.MobLootXp.ENABLED, "true"))) {
            helper.fail("Could not write the drop chance row");
            return;
        }
        Config.reload();
        try {
            var mob = helper.spawn(EntityType.PIGLIN_BRUTE, 2, 2, 2);
            mob.setNoAi(true);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
            mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLDEN_SWORD));
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            mob.setLastHurtByPlayer(player);
            mob.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
            AABB around = new AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(4);
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
            helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.GOLDEN_AXE)),
                    "A mainhand drop chance of 1 did not drop its axe");
            helper.assertTrue(items.stream().noneMatch(i -> i.getItem().is(Items.GOLDEN_SWORD)),
                    "An offhand drop chance of 0 dropped its sword");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobLootXp.FILE, row);
            Config.reload();
        }
    }

    /** A Loot &amp; XP slot chance wins over the piece's own Equipment Drop %. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_drop_chance")
    public static void aSlotChanceWinsOverEquippedDropPct(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> loot = Map.of(Schema.MobLootXp.SCOPE, "", Schema.MobLootXp.SCOPE_ID, "",
                Schema.MobLootXp.ENTITY, "minecraft:skeleton", Schema.MobLootXp.VARIANT_NAME, "",
                Schema.MobLootXp.IS_VARIANT, "false");
        Map<String, String> gear = Map.of(
                Schema.Equipment.VARIANT_NAME, com.strata.model.Variant.defaultNameFor("minecraft:skeleton"),
                Schema.Equipment.SCOPE, "", Schema.Equipment.SCOPE_ID, "",
                Schema.Equipment.SLOT, "offhand", Schema.Equipment.ITEM, "minecraft:shield");
        boolean written = TableWriter.upsert(Schema.MobLootXp.FILE, loot, Map.of(
                        Schema.MobLootXp.DROP_CHANCE_OFFHAND, "2.0", Schema.MobLootXp.ENABLED, "true"))
                && TableWriter.upsert(Schema.Equipment.FILE, gear, Map.of(
                        Schema.Equipment.CHANCE_PCT, "100", Schema.Equipment.DROP_PCT, "0"));
        if (!written) {
            helper.fail("Could not write the loot and equipment rows");
            return;
        }
        Config.reload();
        try {
            var mob = helper.spawn(EntityType.SKELETON, 2, 2, 2);
            mob.setNoAi(true);
            mob.setData(com.strata.runtime.MobLevel.EQUIPMENT_PENDING, true);
            mob.setData(com.strata.runtime.MobLevel.VARIANT_SCOPE, "");
            com.strata.runtime.MobEquipment.onJoin(mob);
            helper.assertTrue(mob.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                    "The equipment did not arm the offhand shield, so the test proves nothing");
            mob.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
            AABB around = new AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(4);
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
            helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.SHIELD)),
                    "A Loot & XP offhand chance of 2 did not beat the equipped Drop % of 0");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobLootXp.FILE, loot);
            TableWriter.delete(Schema.Equipment.FILE, gear);
            Config.reload();
        }
    }

    /** The owner's report, as the Equipment screen sets it: a piece at Drop % 100, killed by a player. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_drop_chance")
    public static void anEquippedPiecesDropPctAppliesOnAPlayerKill(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> gear = Map.of(
                Schema.Equipment.VARIANT_NAME, com.strata.model.Variant.defaultNameFor("minecraft:skeleton"),
                Schema.Equipment.SCOPE, "", Schema.Equipment.SCOPE_ID, "",
                Schema.Equipment.SLOT, "offhand", Schema.Equipment.ITEM, "minecraft:shield");
        if (!TableWriter.upsert(Schema.Equipment.FILE, gear, Map.of(
                Schema.Equipment.CHANCE_PCT, "100", Schema.Equipment.DROP_PCT, "100"))) {
            helper.fail("Could not write the equipment row");
            return;
        }
        Config.reload();
        try {
            var mob = helper.spawn(EntityType.SKELETON, 2, 2, 2);
            mob.setNoAi(true);
            mob.setData(com.strata.runtime.MobLevel.EQUIPMENT_PENDING, true);
            mob.setData(com.strata.runtime.MobLevel.VARIANT_SCOPE, "");
            com.strata.runtime.MobEquipment.onJoin(mob);
            helper.assertTrue(mob.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                    "The equipment did not arm the offhand shield, so the test proves nothing");
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            mob.setLastHurtByPlayer(player);
            mob.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
            AABB around = new AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(4);
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
            helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.SHIELD)),
                    "An equipped Drop % of 100 did not drop its shield on a player kill");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.Equipment.FILE, gear);
            Config.reload();
        }
    }

    /** Pathfinding cost: set, it is the mob's malus; cleared, the game's own is back. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_pathfinding")
    public static void aTerrainCostApplies(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> row = Map.of(Schema.MobPathfinding.SCOPE, "", Schema.MobPathfinding.SCOPE_ID, "",
                Schema.MobPathfinding.ENTITY, "minecraft:zoglin", Schema.MobPathfinding.VARIANT_NAME, "",
                Schema.MobPathfinding.IS_VARIANT, "false");
        var water = net.minecraft.world.level.pathfinder.PathType.WATER;
        var mob = helper.spawn(EntityType.ZOGLIN, 2, 2, 2);
        mob.setNoAi(true);
        float own = mob.getPathfindingMalus(water);
        helper.assertTrue(own != -1.0F, "The zoglin's own water cost is already -1, so the test proves nothing");
        if (!TableWriter.upsert(Schema.MobPathfinding.FILE, row, Map.of(Schema.MobPathfinding.WATER, "-1",
                Schema.MobPathfinding.ENABLED, "true"))) {
            helper.fail("Could not write the pathfinding row");
            return;
        }
        try {
            Config.reload();
            helper.assertTrue(mob.getPathfindingMalus(water) == -1.0F,
                    "Its water cost is " + mob.getPathfindingMalus(water) + ", not -1");
            TableWriter.delete(Schema.MobPathfinding.FILE, row);
            Config.reload();
            helper.assertTrue(mob.getPathfindingMalus(water) == own,
                    "Its water cost did not go back to the game's " + own);
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobPathfinding.FILE, row);
            Config.reload();
        }
    }

    /** Scoreboard tags: added on reload; tags taken out of the row come off, the mob's own stay. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_tags")
    public static void scoreboardTagsFollowTheRow(GameTestHelper helper) {
        Defaults.writeIfMissing();
        Map<String, String> row = Map.of(Schema.MobFlags.SCOPE, "", Schema.MobFlags.SCOPE_ID, "",
                Schema.MobFlags.ENTITY, "minecraft:hoglin", Schema.MobFlags.VARIANT_NAME, "",
                Schema.MobFlags.IS_VARIANT, "false");
        var mob = helper.spawn(EntityType.HOGLIN, 2, 2, 2);
        mob.setNoAi(true);
        mob.addTag("gt_own");
        if (!TableWriter.upsert(Schema.MobFlags.FILE, row, Map.of(Schema.MobFlags.SCOREBOARD_TAGS,
                "gt_own, gt_one,gt_two", Schema.MobFlags.ENABLED, "true"))) {
            helper.fail("Could not write the flags row");
            return;
        }
        try {
            Config.reload();
            helper.assertTrue(mob.getTags().containsAll(java.util.List.of("gt_own", "gt_one", "gt_two")),
                    "Its tags are " + mob.getTags());
            TableWriter.upsert(Schema.MobFlags.FILE, row, Map.of(Schema.MobFlags.SCOREBOARD_TAGS, "gt_two"));
            Config.reload();
            helper.assertTrue(mob.getTags().contains("gt_two") && !mob.getTags().contains("gt_one"),
                    "A tag taken out of the row stayed on: " + mob.getTags());
            TableWriter.delete(Schema.MobFlags.FILE, row);
            Config.reload();
            helper.assertTrue(!mob.getTags().contains("gt_two") && mob.getTags().contains("gt_own"),
                    "Clearing the row left " + mob.getTags() + " (its own tag stays, Strata's go)");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobFlags.FILE, row);
            Config.reload();
        }
    }

    /** Type values: the blocks a type ignores, how far it is tracked, how often it is sent. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "properties_type")
    public static void typeValuesApplyAndComeBack(GameTestHelper helper) {
        Defaults.writeIfMissing();
        EntityType<?> type = EntityType.TURTLE;
        String ownImmune = com.strata.runtime.MobDataRuntime.immuneToOf(type);
        int ownInterval = type.updateInterval();
        int ownRange = type.clientTrackingRange();
        Map<String, String> immune = typeRow("minecraft:turtle", "immune_to");
        Map<String, String> interval = typeRow("minecraft:turtle", "update_interval");
        Map<String, String> range = typeRow("minecraft:turtle", "tracking_range");
        try {
            boolean written = TableWriter.upsert(Schema.MobData.FILE, immune, Map.of(Schema.MobData.VALUE,
                    "minecraft:wither_rose,minecraft:sweet_berry_bush"))
                    && TableWriter.upsert(Schema.MobData.FILE, interval, Map.of(Schema.MobData.VALUE, "7"))
                    && TableWriter.upsert(Schema.MobData.FILE, range, Map.of(Schema.MobData.VALUE, "3"));
            helper.assertTrue(written, "Could not write the type rows");
            Config.reload();
            helper.assertTrue(com.strata.runtime.MobDataRuntime.immuneToOf(type)
                            .equals("minecraft:sweet_berry_bush,minecraft:wither_rose"),
                    "Its ignored blocks are " + com.strata.runtime.MobDataRuntime.immuneToOf(type));
            helper.assertTrue(type.updateInterval() == 7, "Its update interval is " + type.updateInterval());
            helper.assertTrue(type.clientTrackingRange() == 3, "Its tracking range is " + type.clientTrackingRange());
            TableWriter.delete(Schema.MobData.FILE, immune);
            TableWriter.delete(Schema.MobData.FILE, interval);
            TableWriter.delete(Schema.MobData.FILE, range);
            Config.reload();
            helper.assertTrue(com.strata.runtime.MobDataRuntime.immuneToOf(type).equals(ownImmune),
                    "Its ignored blocks did not come back: " + com.strata.runtime.MobDataRuntime.immuneToOf(type));
            helper.assertTrue(type.updateInterval() == ownInterval && type.clientTrackingRange() == ownRange,
                    "Its interval and range did not come back");
            helper.succeed();
        } finally {
            TableWriter.delete(Schema.MobData.FILE, immune);
            TableWriter.delete(Schema.MobData.FILE, interval);
            TableWriter.delete(Schema.MobData.FILE, range);
            Config.reload();
        }
    }

    private static Map<String, String> typeRow(String entity, String key) {
        return Map.of(Schema.MobData.ENTITY, entity, Schema.MobData.VARIANT_NAME, "", Schema.MobData.SCOPE, "",
                Schema.MobData.SCOPE_ID, "", Schema.MobData.GROUP, "type", Schema.MobData.KEY, key);
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "properties")
    public static void aHealthCurveKeepsTheShareOfHearts(GameTestHelper helper) {
        var mob = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        mob.setNoAi(true);
        mob.setHealth(mob.getMaxHealth() / 2);
        var curve = new com.strata.runtime.AttributeCurve(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                net.minecraft.resources.ResourceLocation.parse("minecraft:generic.max_health"), 1.0, 1.0, 1,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("strata", "test_share"),
                com.strata.model.ScalingFormulas.Kind.MOB_ATTRIBUTE);
        curve.apply(mob, 1);
        float share = mob.getHealth() / mob.getMaxHealth();
        helper.assertTrue(mob.getMaxHealth() > 20F && Math.abs(share - 0.5F) < 0.01F,
                "A max health curve set health to " + mob.getHealth() + " of " + mob.getMaxHealth() + ", not half");
        helper.succeed();
    }
}
