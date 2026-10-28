package com.strata.mixin;

import com.strata.runtime.MobProperties;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A MOB'S REACH (mob_combat.csv reach): how far past its own body a melee hit lands. */
@Mixin(Mob.class)
public abstract class MobAttackBoxMixin {

    private static final double GAME_REACH = Math.sqrt(2.04F) - 0.6F;

    @Inject(method = "getAttackBoundingBox", at = @At("RETURN"), cancellable = true)
    private void strata$reach(CallbackInfoReturnable<AABB> cir) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        Double reach = MobProperties.forMob(self).reach;
        if (reach != null && reach >= 0) {
            double more = reach - GAME_REACH;
            cir.setReturnValue(cir.getReturnValue().inflate(more, 0.0, more));
        }
    }
}
