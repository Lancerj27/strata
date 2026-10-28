package com.strata.gametest;

import com.strata.domain.GoalsOps;
import com.strata.model.GoalRow;
import com.strata.model.GoalCondition;
import com.strata.model.GoalType;
import com.strata.runtime.GoalsRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** THE AI, IN A REAL WORLD. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class GoalGameTests {

    private GoalGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void gameSkeletonFightsBack(GameTestHelper helper) {
        fightsBack(helper, null);
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void familySkeletonFightsBack(GameTestHelper helper) {
        GoalsOps.restoreGoalSet("skeleton");
        List<GoalRow> family = GoalsOps.goalSet("skeleton");
        if (family.isEmpty()) {
            helper.fail("No skeleton family behavior was seeded");
            return;
        }
        fightsBack(helper, family);
    }

    /**
     * THE WHOLE PATH: the owner's list saved as the skeleton's behavior, the way the editor's Save does
     * (GoalsOps#saveMobGoals saves into the family), then a skeleton spawned as the game spawns one: the
     * spawn event, the equipment at its join, and its behavior.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "pipeline")
    public static void naturalSkeletonWithSavedGoalSetFightsBack(GameTestHelper helper) {
        var saved = GoalsOps.saveGoalSet("skeleton", "skeleton", ownerList());
        if (!saved.ok()) {
            helper.fail("Could not save the skeleton behavior: " + saved.first());
            return;
        }
        helper.getLevel().setDayTime(18000);
        boolean substitution = com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get();
        com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(false);
        Skeleton skeleton;
        try {
            skeleton = EntityType.SKELETON.spawn(helper.getLevel(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)),
                    net.minecraft.world.entity.MobSpawnType.NATURAL);
        } finally {
            com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(substitution);
        }
        if (skeleton == null) {
            helper.fail("The skeleton did not spawn");
            return;
        }
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        watch(helper, skeleton);
    }

    /**
     * A SUBSTITUTED SKELETON: made the way SpawnHandler makes one, in place of the monster the game meant to
     * spawn (create, the spawn event, then added).
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "substitute")
    public static void substitutedSkeletonWithSavedGoalSetFightsBack(GameTestHelper helper) {
        var saved = GoalsOps.saveGoalSet("skeleton", "skeleton", ownerList());
        if (!saved.ok()) {
            helper.fail("Could not save the skeleton behavior: " + saved.first());
            return;
        }
        helper.getLevel().setDayTime(18000);
        Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        skeleton.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        boolean substitution = com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get();
        com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(false);
        try {
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(skeleton, helper.getLevel(),
                    helper.getLevel().getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.NATURAL,
                    null);
        } finally {
            com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(substitution);
        }
        helper.getLevel().addFreshEntity(skeleton);
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        watch(helper, skeleton);
    }

    /**
     * AN EDIT REACHES A SKELETON ALREADY IN THE WORLD: it spawns on the game's AI, then the owner's list is
     * saved (a reload applies it).
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "reload")
    public static void livingSkeletonTakesTheSavedGoalSet(GameTestHelper helper) {
        GoalsOps.restoreGoalSet("skeleton");
        helper.getLevel().setDayTime(18000);
        boolean substitution = com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get();
        com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(false);
        Skeleton skeleton;
        try {
            skeleton = EntityType.SKELETON.spawn(helper.getLevel(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)),
                    net.minecraft.world.entity.MobSpawnType.NATURAL);
        } finally {
            com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(substitution);
        }
        if (skeleton == null) {
            helper.fail("The skeleton did not spawn");
            return;
        }
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        helper.runAfterDelay(5, () -> {
            var saved = GoalsOps.saveGoalSet("skeleton", "skeleton", ownerList());
            if (!saved.ok()) {
                helper.fail("Could not save the skeleton behavior: " + saved.first());
            }
        });
        watch(helper, skeleton);
    }

    /**
     * A MOB'S OWN GOAL SET CHOICE REACHES THE WORLD: the editor's Save as goal set names a list for plain
     * zombies, and the choice is a row of mob_goal_sets.csv now, not a column of equipment.csv.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "choice")
    public static void chosenGoalSetReachesASpawnedZombie(GameTestHelper helper) {
        var made = com.strata.domain.MobOps.ensureDefaultVariant("minecraft:zombie");
        List<GoalRow> calm = List.of(row(false, 1, "FloatGoal"), row(false, 2, "RandomLookAroundGoal"));
        var saved = GoalsOps.nameOwnList(made.name(), "global", "", "g51_calm", calm);
        if (!made.report().ok() || !saved.ok()) {
            helper.fail("Could not save the goal set choice: " + saved.first());
            return;
        }
        boolean substitution = com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get();
        com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(false);
        Zombie zombie;
        try {
            zombie = EntityType.ZOMBIE.spawn(helper.getLevel(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)),
                    net.minecraft.world.entity.MobSpawnType.NATURAL);
        } finally {
            com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(substitution);
        }
        if (zombie == null) {
            GoalsOps.deleteGoalSets(List.of("g51_calm"));
            helper.fail("The zombie did not spawn");
            return;
        }
        helper.runAfterDelay(10, () -> {
            List<String> goals = new ArrayList<>();
            for (var selector : List.of(zombie.goalSelector, zombie.targetSelector)) {
                selector.getAvailableGoals().forEach(w -> goals.add((w.getGoal()
                        instanceof com.strata.runtime.ConditionalGoal c ? c.inner() : w.getGoal())
                        .getClass().getSimpleName()));
            }
            String stored = com.strata.domain.VariantOps.storedGoalSet(made.name(), "global", "");
            GoalsOps.deleteGoalSets(List.of("g51_calm"));
            helper.assertTrue("g51_calm".equals(stored), "The choice is not in mob_goal_sets.csv: '" + stored + "'");
            helper.assertTrue(goals.contains("RandomLookAroundGoal") && !goals.contains("NearestAttackableTargetGoal"),
                    "The zombie does not run its chosen goal set: " + goals);
            helper.succeed();
        });
    }

    /**
     * THE GOALS EDITOR'S SAVE REACHES THE MOB ALREADY IN THE WORLD: a zombie is in the world, then its goals
     * are saved the way GoalsScreen saves a mob's list (GoalsOps#saveMobGoals, no goal set picked), and the
     * zombie runs the saved list.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "editsave")
    public static void editorSavedGoalsReachALivingZombie(GameTestHelper helper) {
        GoalsOps.restoreGoalSet("zombie");
        boolean substitution = com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get();
        com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(false);
        Zombie zombie;
        try {
            zombie = EntityType.ZOMBIE.spawn(helper.getLevel(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)),
                    net.minecraft.world.entity.MobSpawnType.NATURAL);
        } finally {
            com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(substitution);
        }
        if (zombie == null) {
            helper.fail("The zombie did not spawn");
            return;
        }
        String owner = com.strata.model.Variant.defaultNameFor("minecraft:zombie");
        List<GoalRow> calm = List.of(row(false, 1, "FloatGoal"), row(false, 2, "RandomLookAroundGoal"));
        helper.runAfterDelay(5, () -> {
            var saved = GoalsOps.saveMobGoals(owner, "minecraft:zombie", "global", "", "", calm);
            if (!saved.report().ok()) {
                helper.fail("Could not save the zombie's goals: " + saved.report().first());
            }
        });
        helper.runAfterDelay(15, () -> {
            List<String> goals = new ArrayList<>();
            for (var selector : List.of(zombie.goalSelector, zombie.targetSelector)) {
                selector.getAvailableGoals().forEach(w -> goals.add((w.getGoal()
                        instanceof com.strata.runtime.ConditionalGoal c ? c.inner() : w.getGoal())
                        .getClass().getSimpleName()));
            }
            String where = GoalsOps.effective(owner, "minecraft:zombie", "global", "", "").source();
            GoalsOps.restoreGoalSet("zombie");
            helper.assertTrue(goals.contains("RandomLookAroundGoal") && !goals.contains("NearestAttackableTargetGoal"),
                    "The living zombie does not run its saved goals (editor shows " + where + "): " + goals);
            helper.succeed();
        });
    }

    /**
     * A SET PICKED IN THE MOB EDITOR, THEN EDITED AND SAVED IN THE GOALS EDITOR, IS THE MOB'S: the pick was
     * not saved in the mob editor, and before, the goals editor's Save left the mob on its old set.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "editsave2")
    public static void pickedThenEditedGoalSetReachesALivingZombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        var made = com.strata.domain.MobOps.ensureDefaultVariant("minecraft:zombie");
        String owner = made.name();
        List<GoalRow> calm = List.of(row(false, 1, "FloatGoal"), row(false, 2, "RandomLookAroundGoal"));
        var library = GoalsOps.saveGoalSet("", "g_picked", calm);
        if (!made.report().ok() || !library.ok()) {
            helper.fail("Could not set up the goal set: " + library.first());
            return;
        }
        com.strata.domain.VariantOps.saveMobGoalSet("global", "", owner, "");
        List<GoalRow> edited = List.of(row(false, 1, "FloatGoal"), row(false, 3, "LookAtPlayerGoal"));
        helper.runAfterDelay(5, () -> {
            var saved = GoalsOps.saveMobGoals(owner, "minecraft:zombie", "global", "", "g_picked", edited);
            if (!saved.report().ok()) {
                helper.fail("Could not save the goals: " + saved.report().first());
            }
        });
        helper.runAfterDelay(15, () -> {
            List<String> goals = new ArrayList<>();
            for (var selector : List.of(zombie.goalSelector, zombie.targetSelector)) {
                selector.getAvailableGoals().forEach(w -> goals.add((w.getGoal()
                        instanceof com.strata.runtime.ConditionalGoal c ? c.inner() : w.getGoal())
                        .getClass().getSimpleName()));
            }
            String stored = com.strata.domain.VariantOps.storedGoalSet(owner, "global", "");
            com.strata.domain.VariantOps.saveMobGoalSet("global", "", owner, "");
            GoalsOps.deleteGoalSets(List.of("g_picked"));
            helper.assertTrue("g_picked".equals(stored), "The mob's choice was not stored: '" + stored + "'");
            helper.assertTrue(goals.contains("LookAtPlayerGoal") && !goals.contains("NearestAttackableTargetGoal"),
                    "The living zombie does not run the edited set: " + goals);
            helper.succeed();
        });
    }

    /** GOALS BESIDE A BRAIN. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "brain")
    public static void villagerWithMeleeGoalsFightsAZombie(GameTestHelper helper) {
        helper.getLevel().setDayTime(18000);
        net.minecraft.world.entity.npc.Villager villager = helper.spawn(EntityType.VILLAGER, 1, 2, 1);
        var damage = villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damage == null) {
            helper.fail("The villager has no attack damage: Strata adds it at startup");
            return;
        }
        damage.setBaseValue(6.0);
        villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0);
        villager.setHealth(200.0f);
        List<GoalRow> rows = new ArrayList<>();
        rows.add(row(true, 1, "HurtByTargetGoal"));
        rows.add(row(false, 1, "MeleeAttackGoal", "speed", "1.0", "keep_chasing", "true"));
        GoalsRuntime.apply(villager, rows);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 2, 4);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float full = zombie.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() < full || !zombie.isAlive(),
                "The villager never hit the zombie (its target: " + villager.getTarget() + ")"));
    }

    /** A VILLAGER IS A MOB: its Equipment rows arm it as they arm any mob. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "villager")
    public static void villagerWearsItsEquipment(GameTestHelper helper) {
        var made = com.strata.domain.MobOps.ensureDefaultVariant("minecraft:villager");
        String owner = made.name();
        var rows = com.strata.domain.EquipmentOps.equipmentFor(owner, "minecraft:villager", "global", "", List.of());
        var head = com.strata.domain.EquipmentOps.saveEquipment(owner, "global", "", null, "head",
                "minecraft:carved_pumpkin", "100", "", "0", "0", false, "", rows);
        rows = com.strata.domain.EquipmentOps.equipmentFor(owner, "minecraft:villager", "global", "", List.of());
        var hand = com.strata.domain.EquipmentOps.saveEquipment(owner, "global", "", null, "mainhand",
                "minecraft:iron_sword", "100", "", "0", "0", false, "", rows);
        if (!made.report().ok() || !head.ok() || !hand.ok()) {
            com.strata.domain.EquipmentOps.restoreEquipment(owner, "global", "");
            helper.fail("Could not save the villager's equipment: " + head.first() + " / " + hand.first());
            return;
        }
        net.minecraft.world.entity.npc.Villager villager = EntityType.VILLAGER.spawn(helper.getLevel(),
                helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)), net.minecraft.world.entity.MobSpawnType.NATURAL);
        helper.runAfterDelay(10, () -> {
            String wornHead = villager == null ? "none" : villager.getItemBySlot(EquipmentSlot.HEAD).getItem().toString();
            String held = villager == null ? "none" : villager.getItemBySlot(EquipmentSlot.MAINHAND).getItem().toString();
            com.strata.domain.EquipmentOps.restoreEquipment(owner, "global", "");
            helper.assertTrue(villager != null && villager.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN),
                    "The villager's head: " + wornHead);
            helper.assertTrue(villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD),
                    "The villager's hand: " + held);
            helper.succeed();
        });
    }

    /** THE BRAIN WAITS WHILE A GOAL MOVES THE MOB. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "brain")
    public static void villagerBrainWaitsWhileAGoalMovesIt(GameTestHelper helper) {
        net.minecraft.world.entity.npc.Villager villager = helper.spawn(EntityType.VILLAGER, 1, 2, 1);
        villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(EntityType.PIG, 5, 2, 5);
        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0f);
        pig.setNoAi(true);
        List<GoalRow> rows = new ArrayList<>();
        rows.add(row(true, 1, "NearestAttackableTargetGoal", "target", "minecraft:pig", "interval", "1"));
        rows.add(row(false, 1, "MeleeAttackGoal", "speed", "1.0", "keep_chasing", "true"));
        GoalsRuntime.apply(villager, rows);
        helper.runAfterDelay(40, () -> {
            boolean fighting = villager.getTarget() == pig && com.strata.runtime.BrainPause.paused(villager);
            net.minecraft.world.entity.animal.Cow cow = helper.spawn(EntityType.COW, 2, 2, 2);
            cow.setNoAi(true);
            helper.runAfterDelay(60, () -> {
                boolean sensedCow = villager.getBrain().getMemory(
                                net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_LIVING_ENTITIES)
                        .map(list -> list.contains(cow)).orElse(false);
                helper.assertTrue(fighting, "The villager was not fighting the pig with its Brain paused (target "
                        + villager.getTarget() + ")");
                helper.assertTrue(!sensedCow, "The villager's Brain kept running while its melee goal moved it");
                helper.succeed();
            });
        });
    }

    /** THE BRAIN STILL RUNS: goals that only look never pause it. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "brain")
    public static void villagerBrainRunsBesideLookGoals(GameTestHelper helper) {
        net.minecraft.world.entity.npc.Villager villager = helper.spawn(EntityType.VILLAGER, 1, 2, 1);
        List<GoalRow> rows = new ArrayList<>();
        rows.add(row(false, 1, "RandomLookAroundGoal"));
        GoalsRuntime.apply(villager, rows);
        helper.spawn(EntityType.PIG, 3, 2, 3);
        helper.runAfterDelay(60, () -> {
            boolean sensed = villager.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_LIVING_ENTITIES).isPresent();
            boolean looks = villager.goalSelector.getAvailableGoals().stream()
                    .anyMatch(w -> w.getGoal() instanceof net.minecraft.world.entity.ai.goal.RandomLookAroundGoal);
            helper.assertTrue(looks, "The look goal is not on the villager");
            helper.assertTrue(sensed, "The villager's Brain did not run beside the look goal (no sensed mobs)");
            helper.succeed();
        });
    }

    /** A player in Survival who hits the skeleton becomes its target. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void skeletonFightsBackASurvivalPlayer(GameTestHelper helper) {
        playerHits(helper, net.minecraft.world.level.GameType.SURVIVAL, true);
    }

    private static void playerHits(GameTestHelper helper, net.minecraft.world.level.GameType mode, boolean fights) {
        helper.getLevel().setDayTime(18000);
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 1, 2, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        GoalsRuntime.apply(skeleton, ownerList());
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(mode);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5, 2, 5)));
        helper.runAfterDelay(10, () -> skeleton.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0f));
        if (fights) {
            helper.succeedWhen(() -> helper.assertTrue(skeleton.getTarget() == player,
                    "The skeleton did not turn on the Survival player who hit it (target " + skeleton.getTarget() + ")"));
        } else {
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(skeleton.getTarget() == null,
                        "The skeleton turned on a Creative player: " + skeleton.getTarget());
                helper.succeed();
            });
        }
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void ownerListSkeletonFightsBack(GameTestHelper helper) {
        fightsBack(helper, ownerList());
    }

    /** The owner's list: "fights back, hunts player, hunts iron golem; shoot a bow, wanders, watches, looks around". */
    private static List<GoalRow> ownerList() {
        List<GoalRow> rows = new ArrayList<>();
        rows.add(row(true, 1, "HurtByTargetGoal"));
        rows.add(row(true, 2, "NearestAttackableTargetGoal", "target", "minecraft:player", "interval", "5"));
        rows.add(row(true, 3, "NearestAttackableTargetGoal", "target", "minecraft:iron_golem", "interval", "5"));
        rows.add(row(false, 4, "RangedBowAttackGoal", "interval", "40"));
        rows.add(row(false, 5, "WaterAvoidingRandomStrollGoal"));
        rows.add(row(false, 6, "LookAtPlayerGoal"));
        rows.add(row(false, 6, "RandomLookAroundGoal"));
        return rows;
    }

    /** A SKELETON WITH A SWORD FIGHTS. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void familySkeletonWithASwordFightsBack(GameTestHelper helper) {
        GoalsOps.restoreGoalSet("skeleton");
        List<GoalRow> family = GoalsOps.goalSet("skeleton");
        if (family.stream().noneMatch(r -> r.goal().equals("MeleeAttackGoal"))) {
            helper.fail("The skeleton family has no melee goal: " + family.stream().map(GoalRow::goal).toList());
            return;
        }
        helper.getLevel().setDayTime(18000);
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 1, 2, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        GoalsRuntime.apply(skeleton, family);
        watch(helper, skeleton, Hit.MELEE);
    }

    /**
     * A SKELETON WITH A BOW SHOOTS, even with its melee goal first in the list: the melee goal stands down
     * while it holds a bow.
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void bowSkeletonWithMeleeFirstStillShoots(GameTestHelper helper) {
        List<GoalRow> rows = new ArrayList<>(ownerList());
        rows.add(row(false, 3, "MeleeAttackGoal"));
        fightsBack(helper, rows);
    }

    /** A SUN IMMUNE SKELETON DOES NOT FEAR THE SUN. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "sun")
    public static void sunImmuneSkeletonDoesNotFleeTheSun(GameTestHelper helper) {
        var made = com.strata.domain.MobOps.ensureDefaultVariant("minecraft:skeleton");
        String owner = made.name();
        var added = com.strata.domain.AbilitiesOps.addAbility(owner, "global", "", "sun_immune", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                com.strata.domain.AbilitiesOps.abilitiesShown(owner, "minecraft:skeleton", "global", ""));
        if (!made.report().ok() || !added.ok()) {
            helper.fail("Could not give skeletons sun_immune: "
                    + (added.refusal() != null ? added.refusal() : added.first()));
            return;
        }
        helper.getLevel().setDayTime(6000);
        boolean substitution = com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get();
        com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(false);
        Skeleton skeleton;
        try {
            skeleton = EntityType.SKELETON.spawn(helper.getLevel(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)),
                    net.minecraft.world.entity.MobSpawnType.NATURAL);
        } finally {
            com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(substitution);
        }
        if (skeleton == null) {
            helper.fail("The skeleton did not spawn");
            return;
        }
        skeleton.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        Skeleton old = helper.spawn(EntityType.SKELETON, 3, 2, 3);
        old.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.runAfterDelay(40, () -> {
            List<String> sunGoals = new ArrayList<>();
            for (Skeleton s : List.of(skeleton, old)) {
                s.goalSelector.getAvailableGoals().stream()
                        .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                        .map(g -> g instanceof com.strata.runtime.ConditionalGoal c ? c.inner() : g)
                        .filter(g -> g instanceof net.minecraft.world.entity.ai.goal.FleeSunGoal
                                || g instanceof net.minecraft.world.entity.ai.goal.RestrictSunGoal)
                        .forEach(g -> sunGoals.add((s == old ? "joined " : "spawned ") + g.getClass().getSimpleName()
                                + " (variant " + s.getData(com.strata.runtime.MobLevel.VARIANT_ID) + ")"));
            }
            float health = Math.min(skeleton.getHealth(), old.getHealth());
            float max = skeleton.getMaxHealth();
            com.strata.domain.AbilitiesOps.abilitiesShown(owner, "minecraft:skeleton", "global", "").stream()
                    .filter(r -> r.ability().equals("sun_immune") && !r.inherited())
                    .findFirst().ifPresent(r -> com.strata.domain.AbilitiesOps.removeAbility(owner, "global", "", r,
                            com.strata.domain.AbilitiesOps.abilitiesShown(owner, "minecraft:skeleton", "global", "")));
            helper.assertTrue(sunGoals.isEmpty(), "A sun immune skeleton still has " + sunGoals);
            helper.assertTrue(health == max, "A sun immune skeleton burned: health " + health + "/" + max);
            helper.succeed();
        });
    }

    /**
     * A MOB'S OWN GOAL WITH A CONDITION SURVIVES A SECOND APPLY: a reload applies every
     * loaded mob's list again, and the wrapped goal was filed under its wrapper, so it was not found and was
     * lost.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void conditionalOwnGoalSurvivesAReload(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        List<GoalRow> rows = new ArrayList<>();
        for (GoalRow r : GoalsOps.copyOf("minecraft:zombie")) {
            rows.add(r.game() ? r.withCondition(new GoalCondition(GoalCondition.Type.NIGHT, 0)) : r);
        }
        List<String> own = rows.stream().filter(GoalRow::game).map(GoalRow::goal).toList();
        if (own.isEmpty()) {
            helper.fail("The zombie's list has no goal of its own to test with");
            return;
        }
        GoalsRuntime.apply(zombie, rows);
        GoalsRuntime.apply(zombie, rows);
        List<String> kept = new ArrayList<>();
        for (var selector : List.of(zombie.goalSelector, zombie.targetSelector)) {
            selector.getAvailableGoals().forEach(w -> {
                var g = w.getGoal() instanceof com.strata.runtime.ConditionalGoal c ? c.inner() : w.getGoal();
                kept.add(g.getClass().getName());
            });
        }
        List<String> lost = own.stream().filter(name -> !kept.contains(name)).toList();
        helper.assertTrue(lost.isEmpty(), "Lost at the second apply: " + lost + " (kept " + kept + ")");
        helper.succeed();
    }

    /**
     * A SPAWN EGG MAKES WHAT IT SAYS: with substitution On, a zombie from an egg stays a zombie.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void spawnEggZombiesStayZombies(GameTestHelper helper) {
        boolean substitution = com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get();
        com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(true);
        List<String> wrong = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                Zombie z = EntityType.ZOMBIE.spawn(helper.getLevel(),
                        helper.absolutePos(new net.minecraft.core.BlockPos(1 + i % 5, 2, 1 + i / 5)),
                        net.minecraft.world.entity.MobSpawnType.SPAWN_EGG);
                if (z == null || !z.isAddedToLevel()) {
                    wrong.add("egg " + i + (z == null ? " made nothing" : " was not added (cancelled)"));
                }
                if (z != null) {
                    z.setNoAi(true);
                }
            }
        } finally {
            com.strata.data.StrataConfig.ENABLE_SPAWN_SUBSTITUTION.set(substitution);
        }
        helper.assertTrue(wrong.isEmpty(), "Spawn eggs were swapped: " + wrong + " in "
                + helper.getLevel().getBiome(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)))
                .unwrapKey().map(k -> k.location().toString()).orElse("?"));
        helper.succeed();
    }

    /** AN OWN-ENTITY VARIANT SPAWNS BY ITS BASE MOB'S RULES. */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void ownEntityVariantHasItsBaseMobsSpawnRules(GameTestHelper helper) {
        EntityType<?> own = com.strata.runtime.VariantEntityRegistry.getRegistered("gt_own_zombie").orElse(null);
        if (own == null) {
            helper.fail("The fixture's own-entity variant gt_own_zombie is not registered");
            return;
        }
        List<String> wrong = new ArrayList<>();
        if (!net.minecraft.world.entity.SpawnPlacements.hasPlacement(own)) {
            wrong.add("no spawn placement");
        }
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(own)
                != net.minecraft.world.entity.SpawnPlacements.getPlacementType(EntityType.ZOMBIE)) {
            wrong.add("placement differs");
        }
        if (net.minecraft.world.entity.SpawnPlacements.getHeightmapType(own)
                != net.minecraft.world.entity.SpawnPlacements.getHeightmapType(EntityType.ZOMBIE)) {
            wrong.add("heightmap differs");
        }
        if (own.clientTrackingRange() != EntityType.ZOMBIE.clientTrackingRange()
                || own.updateInterval() != EntityType.ZOMBIE.updateInterval()) {
            wrong.add("tracking " + own.clientTrackingRange() + "/" + own.updateInterval() + " vs "
                    + EntityType.ZOMBIE.clientTrackingRange() + "/" + EntityType.ZOMBIE.updateInterval());
        }
        helper.getLevel().setDayTime(6000);
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        boolean base = net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityType.ZOMBIE, helper.getLevel(),
                net.minecraft.world.entity.MobSpawnType.NATURAL, at, helper.getLevel().getRandom());
        @SuppressWarnings({"unchecked", "rawtypes"})
        boolean variant = net.minecraft.world.entity.SpawnPlacements.checkSpawnRules((EntityType) own,
                helper.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, at, helper.getLevel().getRandom());
        if (base != variant) {
            wrong.add("spawn check in daylight: zombie " + base + ", variant " + variant);
        }
        helper.assertTrue(wrong.isEmpty(), "gt_own_zombie: " + wrong);
        helper.succeed();
    }

    /** Hurts an armed skeleton with a still zombie; passes when the skeleton targets it and shoots it. */
    private static void fightsBack(GameTestHelper helper, List<GoalRow> rows) {
        helper.getLevel().setDayTime(18000);
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 1, 2, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        if (rows != null) {
            GoalsRuntime.apply(skeleton, rows);
        }
        watch(helper, skeleton);
    }

    /** How the skeleton must hurt the zombie. */
    private enum Hit { ARROW, MELEE }

    /** A still zombie hurts the skeleton; passes when the skeleton targets it and shoots it. */
    private static void watch(GameTestHelper helper, Skeleton skeleton) {
        watch(helper, skeleton, Hit.ARROW);
    }

    /** A still zombie hurts the skeleton; passes when the skeleton targets it and hurts it the way given. */
    private static void watch(GameTestHelper helper, Skeleton skeleton, Hit hit) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 5, 2, 5);
        zombie.setNoAi(true);
        float start = zombie.getHealth();
        helper.runAfterDelay(10, () -> skeleton.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0f));
        StringBuilder story = new StringBuilder();
        float[] lastHealth = {skeleton.getHealth()};
        helper.onEachTick(() -> {
            if (skeleton.getHealth() != lastHealth[0]) {
                story.append(" [tick ").append(helper.getTick()).append(": health ").append(skeleton.getHealth())
                        .append(" by ").append(skeleton.getLastDamageSource() == null ? "?"
                                : skeleton.getLastDamageSource().getMsgId()).append(" at ")
                        .append(skeleton.blockPosition().getY()).append(" on fire ").append(skeleton.isOnFire())
                        .append("]");
                lastHealth[0] = skeleton.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            java.util.function.Supplier<String> detail = () -> "(target: " + skeleton.getTarget()
                            + ", skeleton alive " + skeleton.isAlive() + ", removed " + skeleton.getRemovalReason()
                            + ", last damage " + (skeleton.getLastDamageSource() == null ? "none"
                            : skeleton.getLastDamageSource().getMsgId()) + ", health " + skeleton.getHealth()
                            + "/" + skeleton.getMaxHealth() + ", at " + skeleton.blockPosition()
                            + ", last hurt by " + skeleton.getLastHurtByMob() + ", zombie alive " + zombie.isAlive()
                            + ", difficulty " + helper.getLevel().getDifficulty() + ", noAi " + skeleton.isNoAi()
                            + ", goals " + skeleton.targetSelector.getAvailableGoals().size() + "/"
                            + skeleton.goalSelector.getAvailableGoals().size() + ", holding "
                            + skeleton.getMainHandItem() + ", goals " + skeleton.goalSelector.getAvailableGoals()
                            .stream().map(g -> g.getPriority() + ":" + g.getGoal().getClass().getSimpleName())
                            .toList() + ", targets " + skeleton.targetSelector.getAvailableGoals().stream()
                            .map(g -> g.getPriority() + ":" + g.getGoal().getClass().getSimpleName()).toList() + ")"
                            + ", age " + skeleton.tickCount + ", family list "
                            + (GoalsRuntime.familyList("minecraft:skeleton") == null ? "none"
                            : GoalsRuntime.familyList("minecraft:skeleton").size() + " rows")
                            + ", running " + skeleton.goalSelector.getAvailableGoals().stream()
                            .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                            .map(g -> g.getGoal().getClass().getSimpleName()).toList()
                            + ", zombie health " + zombie.getHealth() + "/" + start + ", zombie last damage "
                            + (zombie.getLastDamageSource() == null ? "none" : zombie.getLastDamageSource().getMsgId())
                            + story;
            helper.assertTrue(skeleton.getTarget() == zombie,
                    "The skeleton did not turn on the zombie that hurt it " + detail.get());
            helper.assertTrue(zombie.getHealth() < start,
                    "The skeleton targets the zombie but did not hurt it " + detail.get());
            boolean arrow = zombie.getLastDamageSource() != null && zombie.getLastDamageSource()
                    .is(net.minecraft.world.damagesource.DamageTypes.ARROW);
            helper.assertTrue(hit == Hit.ARROW ? arrow : !arrow,
                    "The skeleton hurt the zombie, but not with " + (hit == Hit.ARROW ? "an arrow" : "its sword")
                            + " " + detail.get());
        });
    }

    private static GoalRow row(boolean target, int priority, String goal, String... settings) {
        Map<String, String> values = GoalType.of(goal).defaults();
        for (int i = 0; i + 1 < settings.length; i += 2) {
            values.put(settings[i], settings[i + 1]);
        }
        return new GoalRow(target, priority, goal, false, Map.copyOf(values), GoalCondition.ALWAYS, true);
    }
}
