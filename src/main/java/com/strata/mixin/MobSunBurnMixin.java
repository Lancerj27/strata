package com.strata.mixin;

import com.strata.runtime.SunBurn;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game's own sun question for the mobs it burns (zombies, skeletons, phantoms): a sun-immune mob is never
 * sun-lit, and a sun-lit mob with no helmet is marked, so its death by on_fire reads as sunlight (SunBurn).
 */
@Mixin(Mob.class)
public abstract class MobSunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void strata$immune(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.level().isClientSide() && Boolean.FALSE.equals(SunBurn.setting(self))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isSunBurnTick", at = @At("RETURN"))
    private void strata$mark(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (cir.getReturnValueZ() && !self.level().isClientSide()
                && self.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            SunBurn.mark(self);
        }
    }
}
