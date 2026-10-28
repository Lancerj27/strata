package com.strata.runtime;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Draws a variant's alternate texture over the creature's normal one. */
public class VariantTextureLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {

    /** Same margin vanilla uses for outer layers. */
    private static final float OUTSET = 1.01F;

    public VariantTextureLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        // Invisible mobs: vanilla skips the body, and drawing the variant skin over a mob that is meant to be
        // unseen would make potions of invisibility useless against exactly the mobs worth hiding from.
        if (entity.isInvisible()) {
            return;
        }
        // A STAND-IN for a mob drawn as another model (ModelSwap): the variant texture was painted for the
        // mob's own model, and on another model's shape it would be noise.
        if (com.strata.client.ModelSwap.isPuppet(entity)) {
            return;
        }

        ResourceLocation texture = VariantHandler.textureFor(entity);
        if (texture == null) {
            return;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();
        poseStack.scale(OUTSET, OUTSET, OUTSET);
        // The parent model is already posed for this frame by the renderer, so this reuses that pose rather
        // than re-running the animation - which is both cheaper and guarantees the overlay moves exactly with
        // the body rather than drifting a frame behind it.
        this.getParentModel().renderToBuffer(poseStack, consumer, packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F), -1);
        poseStack.popPose();
    }
}
