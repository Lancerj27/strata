package com.strata.client;

import com.strata.data.Config;
import com.strata.data.ProfileManager;
import com.strata.data.ServerEntityCache;
import com.strata.network.StrataNetwork;
import com.strata.network.StrataPayloads.GoalsReply;
import com.strata.network.StrataPayloads.GoalsRequest;
import com.strata.network.StrataPayloads.OwnEntity;
import com.strata.network.StrataPayloads.ProfileUpload;
import com.strata.network.StrataPayloads.RestartRequest;
import com.strata.network.StrataPayloads.ServerSync;
import com.strata.network.StrataPayloads.UploadResult;
import com.strata.runtime.VariantEntityRegistry;
import com.strata.runtime.VariantHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The player's side of Strata's server link. */
public final class ClientNetwork implements StrataNetwork.ClientHooks {

    private ClientNetwork() {
    }

    /** The last sync from the connected server, or null when there is none. */
    private static volatile ServerSync server;

    /** Default AI goals the server sent, per entity id. */
    private static final Map<String, List<String>> GOALS = new ConcurrentHashMap<>();

    /** Entities already asked about, so the editor asks once per mob. */
    private static final Map<String, Boolean> ASKED = new ConcurrentHashMap<>();

    /** A server's own-entity types this game lacks: a restart registers them. */
    private static volatile Map<String, String> restartNeeded = Map.of();

    /** Wires the hooks and the listeners. */
    public static void install() {
        StrataNetwork.setClientHooks(new ClientNetwork());
        NeoForge.EVENT_BUS.addListener(ClientNetwork::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientNetwork::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientAssets::onLoggingIn);
    }

    // ---- what the editor reads ----

    /** The connected server's sync, or null. */
    public static ServerSync serverSync() {
        return server;
    }

    /** The server's default goals for a mob, or null while the answer is on its way. */
    public static List<String> goalsFor(String entity) {
        List<String> known = GOALS.get(entity);
        if (known == null && ASKED.putIfAbsent(entity, Boolean.TRUE) == null) {
            PacketDistributor.sendToServer(new GoalsRequest(entity));
        }
        return known;
    }

    /** Sends a local profile to the server. */
    public static String upload(String profile) {
        Map<String, String> files = ProfileManager.profileFiles(profile);
        String problem = ProfileManager.uploadProblem(profile, files);
        if (problem != null) {
            return problem;
        }
        PacketDistributor.sendToServer(new ProfileUpload(profile, files));
        // The custom files its sound and texture replacements use follow, one at a time.
        ClientAssets.uploadForProfile(profile);
        return null;
    }

    /** Asks the server to stop, so its host restarts it with the new config. */
    public static void requestRestart() {
        PacketDistributor.sendToServer(new RestartRequest());
    }

    // ---- what the server sends ----

    @Override
    public void ownEntities(List<OwnEntity> entities) {
        Map<String, String> fromServer = new LinkedHashMap<>();
        entities.forEach(e -> fromServer.put(e.id(), e.base()));
        Map<String, String> missing = ServerEntityCache.missing(fromServer,
                VariantEntityRegistry.registeredBases().keySet());
        // NO RESTART: the server's variants are registered now, before the server checks this game's mob
        // types.
        VariantEntityRegistry.registerNow(missing);
        missing = ServerEntityCache.missing(fromServer, VariantEntityRegistry.registeredBases().keySet());
        if (!missing.isEmpty()) {
            ServerEntityCache.store(fromServer);
            restartNeeded = Map.copyOf(missing);
        }
    }

    @Override
    public void serverSync(ServerSync sync) {
        server = sync;
        Minecraft.getInstance().execute(() -> {
            VariantHandler.useTextureIndex(sync.textures());
            com.strata.runtime.MobModels.useServerIndex(sync.models());
        });
    }

    @Override
    public void uploadResult(UploadResult result) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof com.strata.client.screen.StrataScreen screen) {
                screen.onUploadResult(result);
            } else if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Strata: " + result.message()), false);
            }
        });
    }

    @Override
    public void assetManifest(com.strata.network.StrataPayloads.AssetManifest manifest,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        ClientAssets.onManifest(manifest, context);
    }

    @Override
    public void assetChunk(com.strata.network.StrataPayloads.AssetChunk chunk,
                           net.neoforged.neoforge.network.handling.IPayloadContext context) {
        ClientAssets.onChunk(chunk, context);
    }

    @Override
    public void assetUploadAck(com.strata.network.StrataPayloads.AssetUploadAck ack,
                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        ClientAssets.onUploadAck(ack);
    }

    @Override
    public void goals(GoalsReply reply) {
        GOALS.put(reply.entity(), List.copyOf(reply.goals()));
    }

    @Override
    public void hudIcon(com.strata.network.StrataPayloads.HudIcon icon) {
        // On the network thread: the store is concurrent, and the HUD reads it on the render thread.
        ClientHudIcons.put(icon.effect(), icon.icon(), icon.ticks());
    }

    @Override
    public void mobProps(com.strata.network.StrataPayloads.MobProps props) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            com.strata.runtime.MobProperties.received(level.getEntity(props.entity()), props.values());
        }
    }

    @Override
    public void projectileLook(com.strata.network.StrataPayloads.ProjectileLook look) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            ProjectileLooks.received(level.getEntity(look.entity()), look.texture());
        }
    }

    // ---- listeners ----

    /** Leaving a server: forget it, and draw this machine's textures again. */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        server = null;
        GOALS.clear();
        ProjectileLooks.clear();
        ASKED.clear();
        VariantHandler.indexTextures(List.copyOf(Config.tables().variants().values()));
        com.strata.runtime.MobModels.useLocalIndex();
        ClientAssets.onLeave();
    }

    /** A server turned this game away because it lacks the server's own-entity types. */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof DisconnectedScreen) {
            // A join that failed: this machine's custom textures and sounds again.
            ClientAssets.onLeave();
        }
        if (!(event.getNewScreen() instanceof DisconnectedScreen) || restartNeeded.isEmpty()) {
            return;
        }
        event.setNewScreen(new DisconnectedScreen(new JoinMultiplayerScreen(new TitleScreen()),
                Component.literal("Strata: restart your game to join"),
                Component.literal("This server uses Strata mob types your game does not have yet: "
                        + String.join(", ", restartNeeded.keySet())
                        + ".\nStrata has saved them. Restart your game, then join again.")));
    }
}
