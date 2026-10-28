package com.strata.network;

import com.strata.Strata;
import com.strata.data.ProfileManager;
import com.strata.game.GameData;
import com.strata.network.StrataPayloads.GoalsReply;
import com.strata.network.StrataPayloads.GoalsRequest;
import com.strata.network.StrataPayloads.OwnEntities;
import com.strata.network.StrataPayloads.OwnEntity;
import com.strata.network.StrataPayloads.ProfileUpload;
import com.strata.network.StrataPayloads.RestartRequest;
import com.strata.network.StrataPayloads.ServerSync;
import com.strata.network.StrataPayloads.UploadResult;
import com.strata.runtime.Regeneration;
import com.strata.runtime.VariantEntityRegistry;
import com.strata.runtime.VariantHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/** Strata's link between a player's game and a server (SOLO TO SERVER). */
public final class StrataNetwork {

    private StrataNetwork() {
    }

    /** Operator level for loading a profile and reading a mob's default AI. */
    public static final int OPERATOR = 2;

    /** Operator level for stopping the server, the same as /stop. */
    public static final int STOP_LEVEL = 4;

    /** What the player's game does with each message from the server. */
    public interface ClientHooks {
        void ownEntities(List<OwnEntity> entities);

        void serverSync(ServerSync sync);

        void uploadResult(UploadResult result);

        void goals(GoalsReply reply);

        /** The server offers its custom textures and sounds (either phase; on the network thread). */
        default void assetManifest(StrataPayloads.AssetManifest manifest, IPayloadContext context) {
        }

        default void assetChunk(StrataPayloads.AssetChunk chunk, IPayloadContext context) {
        }

        default void assetUploadAck(StrataPayloads.AssetUploadAck ack, IPayloadContext context) {
        }

        /** An effect's HUD icon swapped for another's. */
        default void hudIcon(StrataPayloads.HudIcon icon) {
        }

        /** A mob's property values, from the server (MobProperties). */
        default void mobProps(StrataPayloads.MobProps props) {
        }

        default void projectileLook(StrataPayloads.ProjectileLook look) {
        }
    }

    private static volatile ClientHooks client;

    /** Set once at client setup. */
    public static void setClientHooks(ClientHooks hooks) {
        client = hooks;
    }

    // ---- registration (mod bus) ----

    public static void register(RegisterPayloadHandlersEvent event) {
        // OPTIONAL: a player whose game has no Strata negotiates none of these channels and is let in; each
        // message is sent only to a game that has its channel (#sendIfHeard).
        PayloadRegistrar registrar = event.registrar("1").optional();
        // On the network thread: the list must be stored before the registry check that follows can turn the
        // player away.
        registrar.executesOn(HandlerThread.NETWORK).configurationToClient(
                OwnEntities.TYPE, OwnEntities.CODEC,
                (payload, context) -> onClient(hooks -> hooks.ownEntities(payload.entities())));
        registrar = event.registrar("1").optional();
        registrar.playToClient(ServerSync.TYPE, ServerSync.CODEC,
                (payload, context) -> onClient(hooks -> hooks.serverSync(payload)));
        registrar.playToClient(UploadResult.TYPE, UploadResult.CODEC,
                (payload, context) -> onClient(hooks -> hooks.uploadResult(payload)));
        registrar.playToClient(GoalsReply.TYPE, GoalsReply.CODEC,
                (payload, context) -> onClient(hooks -> hooks.goals(payload)));
        registrar.playToClient(StrataPayloads.HudIcon.TYPE, StrataPayloads.HudIcon.CODEC,
                (payload, context) -> onClient(hooks -> hooks.hudIcon(payload)));
        registrar.playToClient(StrataPayloads.MobProps.TYPE, StrataPayloads.MobProps.CODEC,
                (payload, context) -> onClient(hooks -> hooks.mobProps(payload)));
        registrar.playToClient(StrataPayloads.ProjectileLook.TYPE, StrataPayloads.ProjectileLook.CODEC,
                (payload, context) -> onClient(hooks -> hooks.projectileLook(payload)));
        registrar.playToServer(ProfileUpload.TYPE, ProfileUpload.CODEC, StrataNetwork::onUpload);
        registerAssets(event);
        registrar.playToServer(RestartRequest.TYPE, RestartRequest.CODEC, StrataNetwork::onRestart);
        registrar.playToServer(GoalsRequest.TYPE, GoalsRequest.CODEC, StrataNetwork::onGoalsRequest);
    }

    /** The custom textures and sounds exchange, in both phases. */
    private static void registerAssets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar assets = event.registrar("1").optional().executesOn(HandlerThread.NETWORK);
        assets.commonToClient(StrataPayloads.AssetManifest.TYPE, StrataPayloads.AssetManifest.CODEC,
                (payload, context) -> onClient(hooks -> hooks.assetManifest(payload, context)));
        assets.commonToClient(StrataPayloads.AssetChunk.TYPE, StrataPayloads.AssetChunk.CODEC,
                (payload, context) -> onClient(hooks -> hooks.assetChunk(payload, context)));
        assets.commonToServer(StrataPayloads.AssetNeed.TYPE, StrataPayloads.AssetNeed.CODEC, AssetServer::onNeed);
        assets.commonToServer(StrataPayloads.AssetAck.TYPE, StrataPayloads.AssetAck.CODEC, AssetServer::onAck);
        assets.playToClient(StrataPayloads.AssetUploadAck.TYPE, StrataPayloads.AssetUploadAck.CODEC,
                (payload, context) -> onClient(hooks -> hooks.assetUploadAck(payload, context)));
        assets.playToServer(StrataPayloads.AssetUploadBegin.TYPE, StrataPayloads.AssetUploadBegin.CODEC,
                AssetServer::onUploadBegin);
        assets.playToServer(StrataPayloads.AssetUploadChunk.TYPE, StrataPayloads.AssetUploadChunk.CODEC,
                AssetServer::onUploadChunk);
    }

    /** A player left: an upload of theirs in flight goes. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        AssetServer.onLoggedOut(event.getEntity().getUUID());
    }

    private static void onClient(java.util.function.Consumer<ClientHooks> action) {
        ClientHooks hooks = client;
        if (hooks != null) {
            action.accept(hooks);
        }
    }

    /**
     * Sends the server's own-entity types the moment configuration starts, BEFORE the registry sync, which
     * runs as a task after this event.
     */
    public static void onConfigurationTasks(RegisterConfigurationTasksEvent event) {
        var listener = event.getListener();
        // The server's custom textures and sounds: a task that completes when the player has them.
        AssetServer.onConfigurationTasks(event);
        if (!listener.hasChannel(OwnEntities.TYPE)) {
            return;
        }
        List<OwnEntity> entities = new ArrayList<>();
        VariantEntityRegistry.registeredBases().forEach((id, base) -> entities.add(new OwnEntity(id, base)));
        listener.send(new OwnEntities(entities));
    }

    // ---- sync (game bus) ----

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfHeard(player, sync(player.server));
        }
    }

    /** Sends to the player only when their game has Strata's channel: a vanilla game gets nothing. */
    public static void sendIfHeard(ServerPlayer player, ServerSync payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** What every player's game shows about this server now. */
    public static ServerSync sync(MinecraftServer server) {
        return new ServerSync(ProfileManager.activeProfile(), Regeneration.pending(server),
                VariantHandler.textureIndex(), com.strata.runtime.MobModels.clientIndex());
    }

    // ---- requests from a player's game ----

    private static void onUpload(ProfileUpload upload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.hasPermissions(OPERATOR)) {
            context.reply(new UploadResult(false, "Only a server operator can load a profile onto the server"));
            return;
        }
        String problem = ProfileManager.uploadProblem(upload.name(), upload.files());
        if (problem != null) {
            context.reply(new UploadResult(false, problem));
            return;
        }
        ProfileManager.Result stored = ProfileManager.storeUpload(upload.name(), upload.files());
        ProfileManager.Result loaded = stored == ProfileManager.Result.OK
                ? Regeneration.loadProfile(player.server, upload.name()) : stored;
        if (loaded != ProfileManager.Result.OK) {
            context.reply(new UploadResult(false, loaded.failureText()));
            return;
        }
        Strata.LOGGER.info("{} loaded profile '{}' onto the server", player.getGameProfile().getName(),
                upload.name());
        context.reply(new UploadResult(true, "Profile '" + upload.name()
                + "' is now the server's active profile. " + Regeneration.RESTART_NOTICE));
        ServerSync now = sync(player.server);
        player.server.getPlayerList().getPlayers().forEach(p -> sendIfHeard(p, now));
    }

    private static void onRestart(RestartRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (!server.isDedicatedServer()) {
            context.reply(new UploadResult(false, "A singleplayer world restarts by reopening it"));
            return;
        }
        if (!player.hasPermissions(STOP_LEVEL)) {
            context.reply(new UploadResult(false, "Only an operator of level " + STOP_LEVEL
                    + " can stop the server"));
            return;
        }
        Strata.LOGGER.info("{} stops the server to apply a new Strata config", player.getGameProfile().getName());
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "The server stops to apply a new Strata config. It is back after the host restarts it."), false);
        server.halt(false);
    }

    private static void onGoalsRequest(GoalsRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(OPERATOR)) {
            return;
        }
        context.reply(new GoalsReply(request.entity(),
                GameData.defaultGoals(request.entity(), player.server)));
    }
}
