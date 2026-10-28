package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.GoalTable;
import com.strata.data.CsvTable;
import com.strata.data.Schema;
import com.strata.model.GoalRow;
import com.strata.model.GoalCondition;
import com.strata.model.GoalType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds a mob's AI from its list. */
public final class GoalsRuntime {

    private GoalsRuntime() {
    }

    private static volatile Map<String, List<GoalRow>> ownLists = Map.of();
    private static volatile Map<String, List<GoalRow>> goalSets = Map.of();

    /** Reads mob_goals.csv: each instance's own list. */
    public static void load(Path csvPath) {
        Map<String, List<Map<String, String>>> grouped = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(csvPath)) {
            String variant = row.getOrDefault(Schema.MobGoals.VARIANT_NAME, "").strip();
            if (variant.isEmpty()) {
                continue;
            }
            grouped.computeIfAbsent(ScopeIndex.key(row.getOrDefault(Schema.MobGoals.SCOPE, ""),
                    row.getOrDefault(Schema.MobGoals.SCOPE_ID, ""), variant), k -> new ArrayList<>()).add(row);
        }
        Map<String, List<GoalRow>> out = new HashMap<>();
        grouped.forEach((key, rows) -> out.put(key, List.copyOf(GoalTable.rows(rows))));
        ownLists = Map.copyOf(out);
        Strata.LOGGER.info("Loaded goal lists for {} mob instance(s)", out.size());
    }

    /** Uses the archetype library (goal_sets.csv). */
    public static void useGoalSets(Map<String, List<GoalRow>> library) {
        goalSets = Map.copyOf(library);
    }

    /** The archetype library, name to its rows. */
    public static Map<String, List<GoalRow>> goalSets() {
        return goalSets;
    }

    private static volatile Map<String, String> chosen = Map.of();

    /** Reads mob_goal_sets.csv: the goal set each instance runs, by ScopeIndex key. */
    public static void loadChoices(Path csvPath) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(csvPath)) {
            String variant = row.getOrDefault(Schema.MobGoalSets.VARIANT_NAME, "").strip();
            String goalSet = row.getOrDefault(Schema.MobGoalSets.GOAL_SET, "").strip();
            if (variant.isEmpty() || goalSet.isEmpty()) {
                continue;
            }
            out.put(ScopeIndex.key(row.getOrDefault(Schema.MobGoalSets.SCOPE, ""),
                    row.getOrDefault(Schema.MobGoalSets.SCOPE_ID, ""), variant), goalSet);
            if (!goalSets.containsKey(goalSet) && !com.strata.model.GoalSets.NONE.equals(goalSet)) {
                Strata.LOGGER.warn("mob_goal_sets.csv: variant '{}' names unknown goal set '{}' - ignored",
                        variant, goalSet);
            }
        }
        chosen = Map.copyOf(out);
        Strata.LOGGER.info("Loaded goal set choices for {} mob instance(s)", out.size());
    }

    /** Every instance's goal set choice, by ScopeIndex key, for {@link #pick}. */
    public static Map<String, String> chosenGoalSets() {
        return chosen;
    }

    /** The goal set a variant runs as seen from one biome (biome, tier, global), else its base mob's, else "". */
    public static String goalSetFor(String variantId, String base, String tier, String biome) {
        String hit = ScopeIndex.resolveOrBase(chosen, variantId, base, tier, biome);
        return hit == null ? "" : hit;
    }

    private static volatile Map<String, String> familyOfMob = Map.of();
    private static volatile Map<String, List<GoalRow>> familyAsGame = Map.of();

    /**
     * Uses the mob families from the game layer: each mob type's family, and each family's goals as the game
     * runs them.
     */
    public static void useFamilies(List<com.strata.domain.GoalFamilies.Family> families) {
        Map<String, List<GoalRow>> asGame = new HashMap<>();
        families.forEach(f -> asGame.put(f.name(), List.copyOf(com.strata.domain.GoalFamilies.rows(f))));
        familyAsGame = Map.copyOf(asGame);
        familyOfMob = Map.copyOf(com.strata.domain.GoalFamilies.familyByMob(families));
        Strata.LOGGER.info("Loaded {} mob famil(ies)", families.size());
    }

    /**
     * The family archetype a mob of this type runs, or null for the game's own AI: it has no family (a brain
     * AI), the archetype is gone, or the archetype is still as the game runs it.
     */
    public static List<GoalRow> familyList(String entity) {
        String family = familyOfMob.get(entity);
        List<GoalRow> rows = family == null ? null : goalSets.get(family);
        return rows == null || rows.equals(familyAsGame.get(family)) ? null : rows;
    }

    /**
     * What decided a mob's AI.
     *
     * @param rows the list it runs, or null for the game's own AI ("none")
     * @param base true when the base mob's choice decided it
     * @param layer the scope layer that decided it, 0 the mob's own scope
     * @param ownList true for an own list, false for a named behavior
     * @param behavior the behavior's name, or "" for an own list
     */
    public record Pick(List<GoalRow> rows, boolean base, int layer, boolean ownList, String goalSet) {
    }

    /**
     * THE ONE WALK.
     *
     * @param own own lists by ScopeIndex key
     * @param presets behavior choices by ScopeIndex key ("none" = the game's AI)
     * @param layers the scopes, nearest first, each {scope, scope id}
     */
    public static Pick pick(Map<String, List<GoalRow>> own, Map<String, String> chosenGoalSets,
                            Map<String, List<GoalRow>> library, String variant, String base, List<String[]> layers) {
        String[] names = {variant, base};
        for (int w = 0; w < names.length; w++) {
            String name = names[w];
            if (name == null || name.isEmpty()) {
                continue;
            }
            for (int i = 0; i < layers.size(); i++) {
                String key = ScopeIndex.key(layers.get(i)[0], layers.get(i)[1], name);
                List<GoalRow> list = own.get(key);
                if (list != null && !list.isEmpty()) {
                    return new Pick(list, w == 1, i, true, "");
                }
                String p = chosenGoalSets.getOrDefault(key, "").strip();
                if (com.strata.model.GoalSets.NONE.equals(p)) {
                    return new Pick(null, w == 1, i, false, p);
                }
                if (!p.isEmpty() && library.containsKey(p)) {
                    return new Pick(library.get(p), w == 1, i, false, p);
                }
            }
        }
        return null;
    }

    /** The runtime's layers for a mob that spawned in this tier and biome: biome, tier, global. */
    public static List<String[]> layers(String tier, String biome) {
        List<String[]> out = new ArrayList<>();
        if (biome != null && !biome.isEmpty()) {
            out.add(new String[]{"biome", biome});
        }
        if (tier != null && !tier.isEmpty()) {
            out.add(new String[]{"tier", tier});
        }
        out.add(new String[]{"", ""});
        return out;
    }

    /**
     * The list a mob runs ({@link #pick} over the loaded indexes), else its family's.
     *
     * @param entity the mob's type id, for its family
     */
    public static List<GoalRow> listFor(String variant, String base, String tier, String biome, String entity) {
        Pick p = pick(ownLists, chosen, goalSets, variant, base, layers(tier, biome));
        return p != null ? p.rows() : familyList(entity);
    }

    /** Where a mob's list comes from, in words (/strata mob ai): the same walk as {@link #listFor}. */
    public static String sourceFor(String variant, String base, String tier, String biome, String entity) {
        Pick p = pick(ownLists, chosen, goalSets, variant, base, layers(tier, biome));
        if (p != null) {
            String who = p.base() ? "its base mob's " : "its ";
            return p.rows() == null ? "the game's own goals (goal set \"none\")"
                    : p.ownList() ? who + "own list" : who + "goal set '" + p.goalSet() + "'";
        }
        return familyList(entity) != null ? "its family's goal set, edited" : "the game's own goals";
    }

    /** WHAT A MOB IS DOING. */
    public static List<String> report(Mob mob) {
        List<String> out = new ArrayList<>();
        String entity = BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType())
                .map(k -> k.location().toString()).orElse("?");
        String variant = mob.getData(MobLevel.VARIANT_ID);
        String packed = mob.getData(MobLevel.VARIANT_SCOPE);
        String owner = variant == null || variant.isEmpty() ? com.strata.model.Variant.defaultNameFor(entity) : variant;
        out.add(entity + (variant == null || variant.isEmpty() ? "" : " as " + variant) + ": goals from "
                + sourceFor(owner, variant == null || variant.isEmpty() ? "" : VariantHandler.baseOf(variant),
                ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed), entity));
        var follow = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        out.add("Holding " + mob.getMainHandItem().getHoverName().getString() + ", target "
                + (mob.getTarget() == null ? "none" : mob.getTarget().getName().getString())
                + ", follow range " + (follow == null ? "none" : follow.getValue())
                + (mob.isNoAi() ? ", NO AI" : ""));
        out.add("Targets: " + goals(mob.targetSelector));
        out.add("Goals: " + goals(mob.goalSelector));
        return out;
    }

    /** "1 HurtByTarget*, 2 NearestAttackableTarget": * marks a goal running now. */
    private static String goals(net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        List<String> parts = new ArrayList<>();
        selector.getAvailableGoals().stream()
                .sorted(java.util.Comparator.comparingInt(WrappedGoal::getPriority))
                .forEach(g -> parts.add(g.getPriority() + " " + g.getGoal().getClass().getSimpleName()
                        .replace("Goal", "") + (g.isRunning() ? "*" : "")));
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    /** Which of the goal needs this mob meets (GoalType.Needs), as their names. */
    public static Set<String> needsMet(net.minecraft.world.entity.Entity e) {
        Set<String> out = new java.util.TreeSet<>();
        if (!(e instanceof Mob)) {
            return out;
        }
        out.add(GoalType.Needs.ANY.name());
        if (e instanceof PathfinderMob) out.add(GoalType.Needs.PATHFINDER.name());
        if (e instanceof Animal) out.add(GoalType.Needs.ANIMAL.name());
        if (e instanceof TamableAnimal) out.add(GoalType.Needs.TAMABLE.name());
        if (e instanceof RangedAttackMob) out.add(GoalType.Needs.RANGED.name());
        if (e instanceof Monster && e instanceof RangedAttackMob) out.add(GoalType.Needs.BOW.name());
        if (e instanceof Monster && e instanceof RangedAttackMob && e instanceof CrossbowAttackMob) {
            out.add(GoalType.Needs.CROSSBOW.name());
        }
        if (e instanceof Creeper) out.add(GoalType.Needs.CREEPER.name());
        return out;
    }

    /** Replaces the mob's goals with the list's. */
    public static void apply(Mob mob, List<GoalRow> rows) {
        // A goal a condition wraps is filed under its own class: filed under the wrapper, a GAME row with a
        // condition was not found again when a reload applied the list a second time, and was lost.
        Map<String, Deque<Goal>> own = new HashMap<>();
        for (WrappedGoal wrapped : new ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            Goal g = unwrap(wrapped.getGoal());
            own.computeIfAbsent(g.getClass().getName(), k -> new ArrayDeque<>()).add(g);
        }
        for (WrappedGoal wrapped : new ArrayList<>(mob.targetSelector.getAvailableGoals())) {
            Goal g = unwrap(wrapped.getGoal());
            own.computeIfAbsent(g.getClass().getName(), k -> new ArrayDeque<>()).add(g);
        }
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        Set<String> warned = new HashSet<>();
        for (GoalRow row : rows) {
            if (!row.enabled()) {
                continue;
            }
            Goal goal = row.game() ? poll(own, row.goal()) : build(mob, row);
            if (goal == null) {
                if (warned.add(row.goal())) {
                    String why = com.strata.domain.GoalsOps.whyNot(row, needsMet(mob),
                            new HashSet<>(own.keySet()));
                    Strata.LOGGER.debug("Goals: {} cannot run {} - skipped{}", mob.getType(), row.goal(),
                            why == null ? "" : ": " + why);
                }
                continue;
            }
            if (row.condition().type() != GoalCondition.Type.ALWAYS) {
                goal = new ConditionalGoal(mob, goal, row.condition());
            }
            (row.target() ? mob.targetSelector : mob.goalSelector).addGoal(row.priority(), goal);
        }
        // A Brain mob's Brain waits while one of these goals moves it.
        BrainPause.track(mob, !rows.isEmpty());
    }

    /**
     * Switches off every targeting goal that would pick a player.
     *
     * @return how many goals it switched off
     */
    public static int dropPlayerHunting(Mob mob) {
        List<net.minecraft.world.entity.ai.goal.WrappedGoal> hunting = mob.targetSelector.getAvailableGoals().stream()
                .filter(w -> !(w.getGoal() instanceof SwitchedOffGoal) && huntsPlayers(unwrap(w.getGoal()))).toList();
        for (net.minecraft.world.entity.ai.goal.WrappedGoal w : hunting) {
            mob.targetSelector.removeGoal(w.getGoal());
            mob.targetSelector.addGoal(w.getPriority(), new SwitchedOffGoal(w.getGoal()));
        }
        return hunting.size();
    }

    /** True when the goal picks its target from a type a player is. */
    static boolean huntsPlayers(Goal goal) {
        if (!(goal instanceof NearestAttackableTargetGoal<?>)) {
            return false;
        }
        for (Class<?> c = goal.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("targetType");
                f.setAccessible(true);
                return f.get(goal) instanceof Class<?> type
                        && type.isAssignableFrom(net.minecraft.world.entity.player.Player.class);
            } catch (NoSuchFieldException e) {
                // Declared higher up: keep walking.
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Takes off the goals that keep a mob out of the sun (RestrictSunGoal, FleeSunGoal), wrapped or not, and
     * lets its paths cross daylight (for the sun_immune ability).
     */
    public static void dropSunGoals(Mob mob) {
        mob.goalSelector.removeAllGoals(goal -> {
            Goal g = unwrap(goal);
            return g instanceof RestrictSunGoal || g instanceof FleeSunGoal;
        });
        if (mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setAvoidSun(false);
        }
    }

    /**
     * Gives a mob that walks paths the skeleton's sun goals, when its list has none: keep out of daylight,
     * and run to shade when burning (the sun_weakness ability).
     */
    public static void addSunGoals(Mob mob) {
        if (!(mob instanceof PathfinderMob path)) {
            return;
        }
        boolean has = mob.goalSelector.getAvailableGoals().stream().map(w -> unwrap(w.getGoal()))
                .anyMatch(g -> g instanceof RestrictSunGoal || g instanceof FleeSunGoal);
        if (!has) {
            mob.goalSelector.addGoal(2, new RestrictSunGoal(path));
            mob.goalSelector.addGoal(3, new FleeSunGoal(path, 1.0));
        }
        if (mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setAvoidSun(true);
        }
    }

    /** The goal a condition wraps, or the goal itself. */
    private static Goal unwrap(Goal goal) {
        if (goal instanceof SwitchedOffGoal off) {
            goal = off.inner();
        }
        return goal instanceof ConditionalGoal c ? c.inner() : goal;
    }

    private static Goal poll(Map<String, Deque<Goal>> own, String className) {
        Deque<Goal> goals = own.get(className);
        return goals == null ? null : goals.poll();
    }

    /** Builds a row's goal for this mob, or null when the mob cannot run it. */
    static Goal build(Mob mob, GoalRow row) {
        GoalType.Type type = GoalType.of(row.goal());
        if (type == null) {
            return null;
        }
        Map<String, String> s = GoalType.withDefaults(type, row.settings());
        PathfinderMob path = mob instanceof PathfinderMob p ? p : null;
        try {
            return switch (type.goal()) {
                case "NearestAttackableTargetGoal" -> hunt(mob, s);
                case "HurtByTargetGoal" -> path == null ? null
                        : alert(new HurtByTargetGoal(path), bool(s, "alert_others"));
                case "MeleeAttackGoal" -> path == null || mob.getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == null ? null
                        : melee(path, num(s, "speed"), bool(s, "keep_chasing"));
                case "LeapAtTargetGoal" -> new LeapAtTargetGoal(mob, (float) num(s, "jump"));
                case "RangedBowAttackGoal" -> bow(mob, s);
                case "RangedCrossbowAttackGoal" -> crossbow(mob, s);
                case "RangedAttackGoal" -> mob instanceof RangedAttackMob ranged
                        ? new RangedAttackGoal(ranged, num(s, "speed"), whole(s, "interval_min"),
                                whole(s, "interval_max"), (float) num(s, "range")) : null;
                case "ShootProjectileGoal" -> shoot(mob, s);
                case "SwellGoal" -> mob instanceof Creeper creeper ? new SwellGoal(creeper) : null;
                case "OcelotAttackGoal" -> new OcelotAttackGoal(mob);
                case "PanicGoal" -> path == null ? null : new PanicGoal(path, num(s, "speed"));
                case "AvoidEntityGoal" -> path == null ? null : avoid(path, s);
                case "RestrictSunGoal" -> path == null ? null : new RestrictSunGoal(path);
                case "FleeSunGoal" -> path == null ? null : new FleeSunGoal(path, num(s, "speed"));
                case "FloatGoal" -> new FloatGoal(mob);
                case "RandomStrollGoal" -> path == null ? null
                        : new RandomStrollGoal(path, num(s, "speed"), whole(type, s, "interval"));
                case "WaterAvoidingRandomStrollGoal" -> path == null ? null
                        : new WaterAvoidingRandomStrollGoal(path, num(s, "speed"), (float) num(s, "chance"));
                case "RandomSwimmingGoal" -> path == null ? null
                        : new RandomSwimmingGoal(path, num(s, "speed"), whole(type, s, "interval"));
                case "WaterAvoidingRandomFlyingGoal" -> path == null ? null
                        : new WaterAvoidingRandomFlyingGoal(path, num(s, "speed"));
                case "MoveTowardsRestrictionGoal" -> path == null ? null
                        : new MoveTowardsRestrictionGoal(path, num(s, "speed"));
                case "MoveThroughVillageGoal" -> path == null ? null : villageWalk(path, s);
                case "BreakDoorGoal" -> new BreakDoorGoal(mob, doorDifficulty(s.get("difficulty")));
                case "OpenDoorGoal" -> new OpenDoorGoal(mob, bool(s, "close"));
                case "LookAtPlayerGoal" -> new LookAtPlayerGoal(mob, GoalTargets.type(s.get("target")),
                        (float) num(s, "range"), (float) num(s, "chance"));
                case "RandomLookAroundGoal" -> new RandomLookAroundGoal(mob);
                case "TemptGoal" -> path == null ? null
                        : new TemptGoal(path, num(s, "speed"), items(s.get("items")), bool(s, "scared"));
                case "BreedGoal" -> mob instanceof Animal animal ? new BreedGoal(animal, num(s, "speed")) : null;
                case "FollowParentGoal" -> mob instanceof Animal animal
                        ? new FollowParentGoal(animal, num(s, "speed")) : null;
                case "EatBlockGoal" -> new EatBlockGoal(mob);
                case "FollowOwnerGoal" -> mob instanceof TamableAnimal tamable
                        ? new FollowOwnerGoal(tamable, num(s, "speed"), (float) num(s, "start"),
                                (float) num(s, "stop")) : null;
                case "FollowMobGoal" -> new FollowMobGoal(mob, num(s, "speed"), (float) num(s, "stop"),
                        (float) num(s, "area"));
                default -> null;
            };
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Goals: {} could not build {}: {}", mob.getType(), type.goal(), e.toString());
            return null;
        }
    }

    /** Shoots a projectile: any projectile the registry has, or none when the id names nothing. */
    private static Goal shoot(Mob mob, Map<String, String> s) {
        // One of the designer's projectiles, or any the registry has.
        String preset = com.strata.model.ProjectilePreset.nameOf(s.get("projectile"));
        var type = Projectiles.typeFor(s.get("projectile"));
        if (type == null) {
            return null;
        }
        return new ShootProjectileGoal(mob, type, preset,
                whole(s, "interval"), num(s, "range"), num(s, "speed"), num(s, "damage"));
    }

    private static <T extends net.minecraft.world.entity.LivingEntity> Goal hunt(Mob mob, Map<String, String> s) {
        @SuppressWarnings("unchecked")
        Class<T> target = (Class<T>) GoalTargets.type(s.get("target"));
        NearestAttackableTargetGoal<T> goal = new NearestAttackableTargetGoal<>(mob, target,
                whole(s, "interval"), bool(s, "must_see"), bool(s, "must_reach"), null);
        return goal;
    }

    /** A MOB THAT CAN SHOOT FIGHTS HAND TO HAND ONLY WITH NO BOW OR CROSSBOW IN HAND. */
    private static Goal melee(PathfinderMob mob, double speed, boolean keepChasing) {
        if (!(mob instanceof RangedAttackMob)) {
            return new MeleeAttackGoal(mob, speed, keepChasing);
        }
        return new MeleeAttackGoal(mob, speed, keepChasing) {
            @Override
            public boolean canUse() {
                return !holdsRangedWeapon(mob) && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !holdsRangedWeapon(mob) && super.canContinueToUse();
            }
        };
    }

    /** True when the mob holds a bow, a crossbow, or any other ranged weapon. */
    public static boolean holdsRangedWeapon(Mob mob) {
        return mob.isHolding(stack -> stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem);
    }

    private static Goal alert(HurtByTargetGoal goal, boolean alert) {
        return alert ? goal.setAlertOthers() : goal;
    }

    private static <T extends net.minecraft.world.entity.LivingEntity> Goal avoid(PathfinderMob mob,
                                                                                  Map<String, String> s) {
        @SuppressWarnings("unchecked")
        Class<T> target = (Class<T>) GoalTargets.type(s.get("target"));
        return new AvoidEntityGoal<>(mob, target, (float) num(s, "range"), num(s, "walk_speed"),
                num(s, "sprint_speed"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Goal bow(Mob mob, Map<String, String> s) {
        if (!(mob instanceof Monster) || !(mob instanceof RangedAttackMob)) {
            return null;
        }
        return new RangedBowAttackGoal((Monster & RangedAttackMob) mob, num(s, "speed"),
                whole(s, "interval"), (float) num(s, "range"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Goal crossbow(Mob mob, Map<String, String> s) {
        if (!(mob instanceof Monster) || !(mob instanceof RangedAttackMob) || !(mob instanceof CrossbowAttackMob)) {
            return null;
        }
        return new RangedCrossbowAttackGoal((Monster & RangedAttackMob & CrossbowAttackMob) mob,
                num(s, "speed"), (float) num(s, "range"));
    }

    private static Goal villageWalk(PathfinderMob mob, Map<String, String> s) {
        boolean doors = bool(s, "doors");
        return new MoveThroughVillageGoal(mob, num(s, "speed"), bool(s, "only_at_night"),
                whole(s, "distance"), () -> doors);
    }

    private static java.util.function.Predicate<Difficulty> doorDifficulty(String choice) {
        return switch (choice == null ? "" : choice) {
            case "any" -> d -> d != Difficulty.PEACEFUL;
            case "normal_and_hard" -> d -> d == Difficulty.NORMAL || d == Difficulty.HARD;
            default -> d -> d == Difficulty.HARD;
        };
    }

    private static java.util.function.Predicate<ItemStack> items(String list) {
        Set<Item> items = new HashSet<>();
        for (String id : (list == null ? "" : list).split("\\+")) {
            ResourceLocation key = ResourceLocation.tryParse(id.strip());
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                items.add(BuiltInRegistries.ITEM.get(key));
            }
        }
        return stack -> items.contains(stack.getItem());
    }

    private static double num(Map<String, String> s, String key) {
        return Double.parseDouble(s.get(key));
    }

    private static int whole(Map<String, String> s, String key) {
        return Integer.parseInt(s.get(key));
    }

    /** A whole setting raised to its least (GoalType.Param#min). */
    private static int whole(GoalType.Type type, Map<String, String> s, String key) {
        return Math.max(type.param(key).min(), whole(s, key));
    }

    private static boolean bool(Map<String, String> s, String key) {
        return Boolean.parseBoolean(s.get(key));
    }
}
