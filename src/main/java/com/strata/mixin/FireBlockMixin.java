package com.strata.mixin;

import com.strata.runtime.BlockStatsRuntime;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block's fire odds, the user's first ( runtime.BlockStatsRuntime). */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "getBurnOdds", at = @At("HEAD"), cancellable = true)
    private void strata$flammability(BlockState state, CallbackInfoReturnable<Integer> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(state.getBlock());
        if (o != null && o.flammability >= 0) {
            cir.setReturnValue(strata$wet(state) ? 0 : o.flammability);
        }
    }

    @Inject(method = "getIgniteOdds", at = @At("HEAD"), cancellable = true)
    private void strata$fireSpread(BlockState state, CallbackInfoReturnable<Integer> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(state.getBlock());
        if (o != null && o.fireSpread >= 0) {
            cir.setReturnValue(strata$wet(state) ? 0 : o.fireSpread);
        }
    }

    private static boolean strata$wet(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }
}
