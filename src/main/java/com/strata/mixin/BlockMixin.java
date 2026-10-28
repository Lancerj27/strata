package com.strata.mixin;

import com.strata.runtime.BlockStatsRuntime;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block's blast resistance, friction, speed factor, and jump factor, the user's first
 * (runtime.BlockStatsRuntime).
 */
@Mixin(Block.class)
public abstract class BlockMixin {

    private Block strata$self() {
        return (Block) (Object) this;
    }

    @Inject(method = "getExplosionResistance()F", at = @At("HEAD"), cancellable = true)
    private void strata$blastResistance(CallbackInfoReturnable<Float> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(strata$self());
        if (o != null && !Float.isNaN(o.blastResistance)) {
            cir.setReturnValue(o.blastResistance);
        }
    }

    @Inject(method = "getFriction()F", at = @At("HEAD"), cancellable = true)
    private void strata$friction(CallbackInfoReturnable<Float> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(strata$self());
        if (o != null && !Float.isNaN(o.friction)) {
            cir.setReturnValue(o.friction);
        }
    }

    @Inject(method = "getSpeedFactor()F", at = @At("HEAD"), cancellable = true)
    private void strata$speedFactor(CallbackInfoReturnable<Float> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(strata$self());
        if (o != null && !Float.isNaN(o.speedFactor)) {
            cir.setReturnValue(o.speedFactor);
        }
    }

    @Inject(method = "getJumpFactor()F", at = @At("HEAD"), cancellable = true)
    private void strata$jumpFactor(CallbackInfoReturnable<Float> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(strata$self());
        if (o != null && !Float.isNaN(o.jumpFactor)) {
            cir.setReturnValue(o.jumpFactor);
        }
    }
}
