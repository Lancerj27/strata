package com.strata.runtime;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** GOALS BESIDE A BRAIN. */
public final class BrainPause {

    private BrainPause() {
    }

    /** The Brain mobs Strata gave goals: only their Brains may pause. */
    private static final Set<Mob> WITH_GOALS =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    /** True for a mob a Brain runs: its Brain holds memories. */
    public static boolean hasBrain(Mob mob) {
        return !mob.getBrain().getMemories().isEmpty();
    }

    /** Called as Strata applies a mob's goal list (GoalsRuntime#apply). */
    public static void track(Mob mob, boolean hasGoals) {
        if (hasGoals && hasBrain(mob)) {
            WITH_GOALS.add(mob);
        } else {
            WITH_GOALS.remove(mob);
        }
    }

    /** True when one of the running goals moves the mob. */
    public static boolean pauses(Collection<EnumSet<Goal.Flag>> running) {
        return running.stream().anyMatch(flags -> flags.contains(Goal.Flag.MOVE));
    }

    /** True while this entity's Brain waits for a Strata goal (BrainMixin asks, each Brain tick). */
    public static boolean paused(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !WITH_GOALS.contains(mob)) {
            return false;
        }
        List<EnumSet<Goal.Flag>> running = mob.goalSelector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning).map(WrappedGoal::getFlags).toList();
        return pauses(running);
    }

    /** Adds attack damage to each mob type that has none (mod bus, at startup). */
    public static void onAttributes(EntityAttributeModificationEvent event) {
        // Vanilla-neutral: the game reads neither attribute on a mob that never had it; only Strata's goals and Flying do.
        // Off: every mob keeps only the game's own attributes (Options > Extra mob attributes).
        if (!com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.ENABLE_EXTRA_MOB_ATTRIBUTES)) {
            com.strata.Strata.LOGGER.info("Extra mob attributes: Off, every mob has the game's own");
            return;
        }
        int added = 0;
        for (var type : event.getTypes()) {
            // A mob: every mob type has follow range (Mob#createMobAttributes); a player or an armor stand
            // has none.
            if (event.has(type, Attributes.FOLLOW_RANGE) && !event.has(type, Attributes.ATTACK_DAMAGE)) {
                event.add(type, Attributes.ATTACK_DAMAGE);
                added++;
            }
            // Flying speed too: a mob given Flying (mob_flags.csv) flies by it, and the game's flying move
            // control asks it of every mob it drives.
            if (event.has(type, Attributes.FOLLOW_RANGE) && !event.has(type, Attributes.FLYING_SPEED)) {
                event.add(type, Attributes.FLYING_SPEED, 0.4);
            }
        }
        com.strata.Strata.LOGGER.info("Attack damage added to {} mob type(s) without it (melee goals)", added);
    }
}
