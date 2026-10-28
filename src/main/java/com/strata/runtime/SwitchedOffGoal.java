package com.strata.runtime;

import net.minecraft.world.entity.ai.goal.Goal;

/** A goal kept on the mob but never run: the Passive switch's hunting goals. */
public final class SwitchedOffGoal extends Goal {

    private final Goal inner;

    public SwitchedOffGoal(Goal inner) {
        this.inner = inner;
        setFlags(inner.getFlags());
    }

    /** The goal switched off. */
    public Goal inner() {
        return inner;
    }

    @Override
    public boolean canUse() {
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
