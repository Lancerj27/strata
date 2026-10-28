package com.strata.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.strata.client.ModelSwap;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A mob's edited texture (ModelSwap#texture): drawn instead of its own, on whatever model it is drawn as.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @ModifyExpressionValue(method = "getRenderType", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation strata$texture(ResourceLocation own, @Local(argsOnly = true) LivingEntity entity) {
        ResourceLocation edited = ModelSwap.texture(entity);
        return edited == null ? own : edited;
    }

    /**
     * Emissive (mob_visuals.csv): the model drawn at full brightness, glowing in the dark,
     * whatever light it stands in.
     */
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 2)
    private int strata$emissive(int light, @Local(argsOnly = true) LivingEntity entity) {
        return Boolean.TRUE.equals(com.strata.runtime.MobProperties.forMob(entity).emissive)
                ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
    }

    /** Tint (mob_visuals.csv): the model's colour multiplied by the mob's own. */
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int strata$tint(int color, @Local(argsOnly = true) LivingEntity entity) {
        Integer tint = com.strata.runtime.MobProperties.forMob(entity).tint;
        if (tint == null) {
            return color;
        }
        int r = ((color >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((color >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (color & 0xFF) * (tint & 0xFF) / 255;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Visual scale (mob_visuals.csv): drawn bigger or smaller, its hitbox as it is. */
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    shift = At.Shift.AFTER))
    private void strata$visualScale(LivingEntity entity, float yaw, float partialTicks,
                                    com.mojang.blaze3d.vertex.PoseStack poseStack,
                                    net.minecraft.client.renderer.MultiBufferSource buffer, int light,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Float scale = com.strata.runtime.MobProperties.forMob(entity).scaleVisual;
        if (scale != null && scale > 0) {
            poseStack.scale(scale, scale, scale);
        }
    }

    /** Shadow radius (mob_visuals.csv): its own shadow's size, scaled with it as the game's is. */
    @Inject(method = "getShadowRadius(Lnet/minecraft/world/entity/LivingEntity;)F", at = @At("RETURN"), cancellable = true)
    private void strata$shadow(LivingEntity entity, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        Float radius = com.strata.runtime.MobProperties.forMob(entity).shadowRadius;
        if (radius != null && radius >= 0) {
            cir.setReturnValue(radius * entity.getScale());
        }
    }
}
