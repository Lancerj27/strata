package com.strata.runtime;

import com.strata.model.GoalCondition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** A goal that may run only while its condition holds: "attack players, only at night". */
public final class ConditionalGoal extends Goal {

    private final Mob mob;
    private final Goal inner;
    private final GoalCondition condition;

    public ConditionalGoal(Mob mob, Goal inner, GoalCondition condition) {
        this.mob = mob;
        this.inner = inner;
        this.condition = condition;
        setFlags(inner.getFlags());
    }

    /** The wrapped goal, so a list applied again finds the mob's own goal inside. */
    public Goal inner() {
        return inner;
    }

    /** What the condition reads off the mob now. */
    private GoalCondition.State state() {
        LivingEntity target = mob.getTarget();
        float max = mob.getMaxHealth();
        return new GoalCondition.State(
                isDay(mob),
                max <= 0 ? 0 : mob.getHealth() / max * 100.0,
                mob.level().isRainingAt(mob.blockPosition()),
                mob.isInWater(),
                target == null ? -1 : mob.distanceTo(target));
    }

    /** Day or night where the mob stands. */
    private static boolean isDay(Mob mob) {
        if (!mob.level().dimensionType().hasFixedTime()) {
            return mob.level().isDay();
        }
        return GoalCondition.isDayTime(mob.level().getDayTime());
    }

    @Override
    public boolean canUse() {
        return condition.test(state()) && inner.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return condition.test(state()) && inner.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return inner.isInterruptable();
    }

    @Override
    public void start() {
        inner.start();
    }

    @Override
    public void stop() {
        inner.stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return inner.requiresUpdateEveryTick();
    }

    @Override
    public void tick() {
        inner.tick();
    }
}
