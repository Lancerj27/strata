package com.strata.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

/**
 * THE HUD ICONS THE SERVER SWAPPED (runtime.HudIcons): this effect is drawn with that one's icon until the
 * game time it ends.
 */
public final class ClientHudIcons {

    private ClientHudIcons() {
    }

    private record Swap(ResourceLocation icon, long until) {
    }

    private static final Map<ResourceLocation, Swap> SWAPS = new ConcurrentHashMap<>();

    /** From the server: draw effect with icon for ticks, -1 for good. */
    public static void put(String effect, String icon, int ticks) {
        ResourceLocation e = ResourceLocation.tryParse(effect);
        ResourceLocation i = ResourceLocation.tryParse(icon);
        if (e == null || i == null) {
            return;
        }
        long now = now();
        SWAPS.put(e, new Swap(i, ticks < 0 ? Long.MAX_VALUE : now + ticks));
    }

    /** The effect whose icon this one is drawn with now, or null for its own. */
    public static Holder<MobEffect> iconFor(Holder<MobEffect> effect) {
        var key = effect.unwrapKey();
        if (key.isEmpty() || SWAPS.isEmpty()) {
            return null;
        }
        Swap swap = SWAPS.get(key.get().location());
        if (swap == null) {
            return null;
        }
        if (now() >= swap.until()) {
            SWAPS.remove(key.get().location());
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(swap.icon()).map(h -> (Holder<MobEffect>) h).orElse(null);
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }
}
