package com.strata.runtime;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * A MOB'S GLOWING TEXTURE (mob_visuals.csv glow_texture): a texture drawn over its model at full
 * brightness, the way a spider's and an enderman's eyes glow in the dark.
 */
public class GlowTextureLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    public GlowTextureLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        String id = MobProperties.forMob(entity).glowTexture;
        ResourceLocation texture = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id.strip());
        if (texture == null) {
            return;
        }
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(texture)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
    }
}
