package com.strata.runtime;

import com.strata.network.StrataPayloads;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** AN EFFECT ROW'S HUD ICON. */
public final class HudIcons {

    private HudIcons() {
    }

    /** The cell's word for no icon. */
    public static final String NONE = "none";

    /** True unless the row's HUD icon is "none". */
    public static boolean shows(String hudIcon) {
        return hudIcon == null || !NONE.equalsIgnoreCase(hudIcon.strip());
    }

    /**
     * The effect id a row's HUD icon names, or null when it names none (blank, "none", or an id the game has
     * no effect for).
     */
    public static ResourceLocation iconOf(String hudIcon) {
        String id = hudIcon == null ? "" : hudIcon.strip();
        if (id.isEmpty() || NONE.equalsIgnoreCase(id)) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.MOB_EFFECT.containsKey(rl) ? rl : null;
    }

    /** Tells a player's game to draw this effect with the row's icon, for its duration. */
    public static void given(LivingEntity target, Holder<MobEffect> effect, String hudIcon, int ticks) {
        ResourceLocation icon = iconOf(hudIcon);
        if (icon == null || effect == null || !(target instanceof ServerPlayer player)) {
            return;
        }
        var key = effect.unwrapKey();
        if (key.isEmpty()) {
            return;
        }
        var payload = new StrataPayloads.HudIcon(key.get().location().toString(), icon.toString(), ticks);
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
