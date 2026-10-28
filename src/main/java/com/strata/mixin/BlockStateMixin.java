package com.strata.mixin;

import com.strata.runtime.BlockStatsRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block state's hardness, light, and correct-tool rule, the user's first (runtime.BlockStatsRuntime). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMixin {

    @Shadow
    @Final
    private int lightEmission;

    @Shadow
    public abstract Block getBlock();

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void strata$hardness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        if (o != null && !Float.isNaN(o.hardness)) {
            cir.setReturnValue(o.hardness);
        }
    }

    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void strata$light(CallbackInfoReturnable<Integer> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        // A lamp's or a furnace's unlit states stay dark.
        if (o != null && o.light >= 0 && (!o.lightWhenLit || lightEmission > 0)) {
            cir.setReturnValue(o.light);
        }
    }

    @Inject(method = "requiresCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
    private void strata$requiresTool(CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.requiresTool, cir);
    }

    // require = 0: a game whose method differs keeps the game's value and still starts.
    @Inject(method = "canBeReplaced()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$replaceable(CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.replaceable, cir);
    }

    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$pushReaction(CallbackInfoReturnable<net.minecraft.world.level.material.PushReaction> cir) {
        strata$own(o -> o.pushReaction, cir);
    }

    @Inject(method = "instrument", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$instrument(
            CallbackInfoReturnable<net.minecraft.world.level.block.state.properties.NoteBlockInstrument> cir) {
        strata$own(o -> o.instrument, cir);
    }

    @Inject(method = "ignitedByLava", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$ignitedByLava(CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.ignitedByLava, cir);
    }

    // PLAN C STEP 5: the rest of what the game builds a block from.

    @Shadow
    protected abstract BlockState asState();

    @Inject(method = "getSoundType()Lnet/minecraft/world/level/block/SoundType;", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$sound(CallbackInfoReturnable<net.minecraft.world.level.block.SoundType> cir) {
        strata$own(o -> o.sound, cir);
    }

    @Inject(method = "getMapColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$mapColor(BlockGetter level, BlockPos pos,
                                 CallbackInfoReturnable<net.minecraft.world.level.material.MapColor> cir) {
        strata$own(o -> o.mapColor, cir);
    }

    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$randomTicks(CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.randomTicks, cir);
    }

    @Inject(method = "isSolid", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$solid(CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.solid, cir);
    }

    /** Collision off: nothing to bump into. */
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$collision(BlockGetter level, BlockPos pos,
                                  CallbackInfoReturnable<net.minecraft.world.phys.shapes.VoxelShape> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        if (o != null && o.collision != null) {
            cir.setReturnValue(o.collision ? asState().getShape(level, pos) : net.minecraft.world.phys.shapes.Shapes.empty());
        }
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$collisionInContext(BlockGetter level, BlockPos pos,
                                           net.minecraft.world.phys.shapes.CollisionContext context,
                                           CallbackInfoReturnable<net.minecraft.world.phys.shapes.VoxelShape> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        if (o != null && o.collision != null) {
            cir.setReturnValue(o.collision ? asState().getShape(level, pos, context)
                    : net.minecraft.world.phys.shapes.Shapes.empty());
        }
    }

    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$occludes(CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.occludes, cir);
    }

    /** The cached "solid render" follows Occludes: off draws what is behind it. */
    @Inject(method = "isSolidRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$solidRender(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        if (o != null && o.occludes != null && !o.occludes) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isRedstoneConductor", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$conductor(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.redstoneConductor, cir);
    }

    @Inject(method = "shouldSpawnTerrainParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$terrainParticles(CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.terrainParticles, cir);
    }

    @Inject(method = "emissiveRendering", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$emissive(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.emissive, cir);
    }

    @Inject(method = "isValidSpawn", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$mobsSpawnOn(BlockGetter level, BlockPos pos, net.minecraft.world.entity.EntityType<?> type,
                                    CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.mobsSpawnOn, cir);
    }

    @Inject(method = "isSuffocating", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$suffocating(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.suffocating, cir);
    }

    @Inject(method = "isViewBlocking", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$viewBlocking(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        strata$own(o -> o.viewBlocking, cir);
    }

    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$offset(BlockGetter level, BlockPos pos, CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        if (o != null && o.offset != null) {
            cir.setReturnValue(BlockStatsRuntime.offsetOf(asState(), level, pos, o.offset));
        }
    }

    @Inject(method = "hasOffsetFunction", at = @At("HEAD"), cancellable = true, require = 0)
    private void strata$hasOffset(CallbackInfoReturnable<Boolean> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        if (o != null && o.offset != null) {
            cir.setReturnValue(!"none".equals(o.offset));
        }
    }

    /** Answers with the block's own value of a stat, when the designer set one. */
    private <T> void strata$own(java.util.function.Function<BlockStatsRuntime.Own, T> value,
                                CallbackInfoReturnable<T> cir) {
        BlockStatsRuntime.Own o = BlockStatsRuntime.of(getBlock());
        T v = o == null ? null : value.apply(o);
        if (v != null) {
            cir.setReturnValue(v);
        }
    }
}
