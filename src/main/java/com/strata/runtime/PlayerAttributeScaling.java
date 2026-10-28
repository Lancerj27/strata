package com.strata.runtime;


import com.strata.data.StrataConfig;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player-side counterpart to MobAttributeScaling, driven entirely by config/strata/scaling_player.csv (see
 * Scaling, AttributeCurve).
 */
public final class PlayerAttributeScaling {

    private PlayerAttributeScaling() {}

    /**
     * Refreshes every player in the world, on the server thread: the player scaling switch
     * was changed in the editor, so it takes effect now rather than at each player's next XP change.
     */
    public static void refreshOnline() {
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> server.getPlayerList().getPlayers().forEach(PlayerAttributeScaling::refresh));
    }
    /** Recomputes and re-applies every enabled curve row from the player's current power level. */
    public static void refresh(ServerPlayer player) {
        if (!StrataConfig.ENABLE_PLAYER_SCALING.get()) {
            // Toggle is off: sweep any previously-applied curve modifier so disabling scaling actually
            // returns the player to vanilla.
            for (AttributeCurve curve : Scaling.players()) {
                curve.apply(player, 0);
            }
            return;
        }

        int level = PowerLevel.of(player);

        for (AttributeCurve curve : Scaling.players()) {
            curve.apply(player, level);
        }

        // Clamp current health down if max_health just decreased (e.g. cap config lowered and server
        // restarted) — mirrors the doc's own explicit health-clamp requirement.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }

        syncToClient(player);
    }

    /** Pushes the updated attributes to the player's own client. */
    private static void syncToClient(ServerPlayer player) {
        if (player.connection == null) {
            return;  // Mid-login: vanilla will send the full set shortly anyway.
        }

        player.connection.send(new ClientboundUpdateAttributesPacket(
                player.getId(), player.getAttributes().getSyncableAttributes()));

        player.resetSentInfo();
    }

}
