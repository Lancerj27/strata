package com.strata.client;

import com.strata.data.CustomAssetsPack;
import com.strata.domain.CustomAssetsOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.stream.Collectors;

/** The sound events a custom sound replaces in the active pack: they play at their own speed. */
public final class ReplacedSounds {

    private ReplacedSounds() {}

    private static volatile Set<ResourceLocation> replaced = Set.of();
    private static volatile long readAt = Long.MIN_VALUE / 2;

    /** True when a custom sound replaces this event. The table is read again at most once a second. */
    public static boolean has(ResourceLocation event) {
        long now = System.currentTimeMillis();
        if (now - readAt > 1000) {
            readAt = now;
            replaced = CustomAssetsOps.replacements(CustomAssetsPack.activeRoot()).stream()
                    .filter(r -> CustomAssetsOps.SOUND.equals(r.kind()))
                    .map(r -> ResourceLocation.tryParse(r.target()))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return replaced.contains(event);
    }
}
