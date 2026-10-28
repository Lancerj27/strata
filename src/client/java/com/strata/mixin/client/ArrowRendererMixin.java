package com.strata.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.strata.client.ProjectileLooks;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Draws one of the designer's arrows with its own texture (ProjectileLooks). */
@Mixin(ArrowRenderer.class)
public abstract class ArrowRendererMixin {

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/projectile/AbstractArrow;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private ResourceLocation strata$texture(ResourceLocation own, @Local(argsOnly = true) AbstractArrow arrow) {
        ResourceLocation edited = ProjectileLooks.texture(arrow);
        return edited == null ? own : edited;
    }
}
