package com.strata.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Plays a sound to the player at the screen, for an editor to hear it. */
public final class SoundPreview {

    private SoundPreview() {}

    /** Plays the sound; false when the game has no sound by that id. */
    public static boolean play(String id, float volume, float pitch) {
        ResourceLocation sound = ResourceLocation.tryParse(id == null ? "" : id.strip());
        var manager = Minecraft.getInstance().getSoundManager();
        if (sound == null || manager.getSoundEvent(sound) == null) {
            return false;
        }
        manager.play(new SimpleSoundInstance(sound, SoundSource.MASTER, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        return true;
    }
}
