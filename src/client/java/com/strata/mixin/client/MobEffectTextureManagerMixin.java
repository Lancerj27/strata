package com.strata.mixin.client;

import com.strata.client.ClientHudIcons;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AN EFFECT DRAWN WITH ANOTHER'S ICON. */
@Mixin(MobEffectTextureManager.class)
public abstract class MobEffectTextureManagerMixin {

    @Shadow
    public abstract TextureAtlasSprite get(Holder<MobEffect> effect);

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void strata$swappedIcon(Holder<MobEffect> effect, CallbackInfoReturnable<TextureAtlasSprite> cir) {
        Holder<MobEffect> icon = ClientHudIcons.iconFor(effect);
        if (icon != null && !icon.equals(effect)) {
            cir.setReturnValue(get(icon));
        }
    }
}
