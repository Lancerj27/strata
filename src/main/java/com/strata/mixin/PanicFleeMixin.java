package com.strata.mixin;

import com.strata.runtime.VariantHandler;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** On flee: a mob starts to panic, and its On flee abilities run (VariantHandler#onFlee). */
@Mixin(PanicGoal.class)
public abstract class PanicFleeMixin {

    @Shadow @Final protected PathfinderMob mob;

    @Inject(method = "start", at = @At("HEAD"))
    private void strata$flee(CallbackInfo ci) {
        VariantHandler.onFlee(mob);
    }
}
