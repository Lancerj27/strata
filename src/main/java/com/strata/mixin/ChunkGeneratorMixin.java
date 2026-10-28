package com.strata.mixin;

import com.strata.runtime.BlockSwapRuntime;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block replacement and disabled-block stripping in newly generated chunks: at the end of a chunk's
 * decoration the terrain, the surface, the features and the structures are all in it, so one pass replaces a
 * block - or takes a disabled one out - however it was placed.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void strata$replaceBlocks(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                      CallbackInfo ci) {
        if (BlockSwapRuntime.active()) {
            BlockSwapRuntime.apply(level, chunk);
        }
    }
}
