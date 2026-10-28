package com.strata.runtime;

import com.strata.data.StrataConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Login and respawn also set the player's base stats first (PlayerBaseStats); an edit sets them on every
 * online player at once (Config#reload).
 */
public class PlayerScalingEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The base first, then the curves over it.
            PlayerBaseStats.apply(player);
            PlayerAttributeScaling.refresh(player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerBaseStats.apply(player);
            PlayerAttributeScaling.refresh(player);
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerAttributeScaling.refresh(player);
        }
    }

    @SubscribeEvent
    public void onXpChange(PlayerXpEvent.XpChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshAfterEvent(player);
        }
    }

    @SubscribeEvent
    public void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshAfterEvent(player);
        }
    }

    /** Refreshes AFTER the current event finishes, not during it. */
    private static void refreshAfterEvent(ServerPlayer player) {
        if (!StrataConfig.ENABLE_PLAYER_SCALING.get()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            PlayerAttributeScaling.refresh(player);
            return;
        }

        server.execute(() -> {
            if (!player.hasDisconnected()) {
                PlayerAttributeScaling.refresh(player);
            }
        });
    }
}
