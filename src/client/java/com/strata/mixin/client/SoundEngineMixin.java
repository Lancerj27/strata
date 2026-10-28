package com.strata.mixin.client;

import com.strata.client.ReplacedSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A REPLACED SOUND PLAYS AT ITS OWN SPEED, not at the pitch the game's code gives the event. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Inject(method = "calculatePitch", at = @At("HEAD"), cancellable = true)
    private void strata$replacedAtOwnSpeed(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (ReplacedSounds.has(sound.getLocation())) {
            cir.setReturnValue(1.0F);
        }
    }
}
