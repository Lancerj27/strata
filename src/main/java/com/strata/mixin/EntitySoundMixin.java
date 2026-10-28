package com.strata.mixin;

import com.strata.runtime.MobModels;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob's edited sounds (MobModels#sound): its idle, hurt, death, step, attack, swim and fall sounds swapped
 * as they play, and each one's volume and pitch (MobModels#volume, #pitch).
 */
@Mixin(Entity.class)
public abstract class EntitySoundMixin {

    private static final ThreadLocal<Boolean> STRATA$REPLAYING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void strata$sound(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        if (STRATA$REPLAYING.get() || sound == null) {
            return;
        }
        Entity self = (Entity) (Object) this;
        SoundEvent swapped = MobModels.sound(self, sound);
        float v = MobModels.volume(self, sound, volume);
        float p = MobModels.pitch(self, sound, pitch);
        if (swapped == sound && v == volume && p == pitch) {
            return;
        }
        ci.cancel();
        if (swapped == null) {
            return;
        }
        STRATA$REPLAYING.set(true);
        try {
            self.playSound(swapped, v, p);
        } finally {
            STRATA$REPLAYING.set(false);
        }
    }
}
