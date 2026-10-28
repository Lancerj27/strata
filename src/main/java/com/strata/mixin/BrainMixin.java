package com.strata.mixin;

import com.strata.runtime.BrainPause;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Strata's first mixin: a Brain waits while a Strata goal that moves its mob is running (BrainPause). */
@Mixin(Brain.class)
public abstract class BrainMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void strata$pauseForGoals(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (BrainPause.paused(entity)) {
            ci.cancel();
        }
    }
}
