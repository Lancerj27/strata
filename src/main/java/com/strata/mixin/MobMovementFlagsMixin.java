package com.strata.mixin;

import com.strata.runtime.MobProperties;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A MOB'S CAN CLIMB AND WALKS ON WATER (mob_flags.csv), through the game's own two questions: is
 * it on something it can climb (a spider is, against any wall it pushes into), and can it stand on this fluid
 * (a strider can, on lava).
 */
@Mixin(LivingEntity.class)
public abstract class MobMovementFlagsMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void strata$climb(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Mob mob && !mob.level().isClientSide() && mob.horizontalCollision
                && Boolean.TRUE.equals(MobProperties.forMob(mob).canClimb)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void strata$walkOnWater(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Mob mob && !mob.level().isClientSide() && fluid.is(FluidTags.WATER)
                && Boolean.TRUE.equals(MobProperties.forMob(mob).canWalkOnWater)) {
            cir.setReturnValue(true);
        }
    }
}
