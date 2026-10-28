package com.strata.client;

import com.strata.Strata;
import com.strata.data.CustomAssetsPack;
import com.strata.data.Paths;
import com.strata.data.StrataConfig;
import com.strata.domain.AssetRules;
import com.strata.domain.AssetSync;
import com.strata.domain.CustomAssetsOps;
import com.strata.network.StrataPayloads.AssetAck;
import com.strata.network.StrataPayloads.AssetChunk;
import com.strata.network.StrataPayloads.AssetManifest;
import com.strata.network.StrataPayloads.AssetNeed;
import com.strata.network.StrataPayloads.AssetUploadAck;
import com.strata.network.StrataPayloads.AssetUploadBegin;
import com.strata.network.StrataPayloads.AssetUploadChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** THE PLAYER'S SIDE OF A SERVER'S CUSTOM TEXTURES AND SOUNDS. */
public final class ClientAssets {

    private ClientAssets() {
    }

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Strata client asset timer");
        t.setDaemon(true);
        return t;
    });

    /** One exchange with a server: a manifest and the files fetched for it. */
    private static final class Exchange {
        final ICommonPacketListener listener;
        final boolean configuration;
        final Path cache;
        final String serverName;
        final List<AssetSync.Entry> manifest;
        final List<AssetSync.Entry> needed;
        /** The game sounds and textures the server's files replace, one CSV line each. */
        List<String> replacements = List.of();
        final ArrayDeque<String> pending = new ArrayDeque<>();
        AssetSync.Receiver receiver;
        String receiving;
        boolean stored;
        boolean over;
        boolean applied;
        int strays;
        long activity = System.currentTimeMillis();

        Exchange(ICommonPacketListener listener, boolean configuration, Path cache, String serverName,
                 List<AssetSync.Entry> manifest, List<AssetSync.Entry> needed) {
            this.listener = listener;
            this.configuration = configuration;
            this.cache = cache;
            this.serverName = serverName;
            this.manifest = manifest;
            this.needed = needed;
        }
    }

    private static volatile Exchange exchange;
    /** The player took the files of this connection: a live change is then taken without asking again. */
    private static volatile boolean acceptedThisConnection;
    /** What the pack was last loaded with, so a reload is made only when it differs. */
    private static volatile String loadedSignature = "local";
    private static volatile boolean reloadAfterJoin;

    // ------------------------------------------------------------ the manifest

    /** The server offers its library. */
    public static void onManifest(AssetManifest manifest, IPayloadContext context) {
        if (context.connection().isMemoryConnection()) {
            return;
        }
        boolean configuration = context.protocol() == ConnectionProtocol.CONFIGURATION;
        if (configuration) {
            acceptedThisConnection = false;
        }
        ICommonPacketListener listener = context.listener();
        AssetSync.Accepted accepted = AssetSync.accept(manifest.entries());
        accepted.notices().forEach(n -> Strata.LOGGER.warn("{}", n));
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getCurrentServer();
        String address = server != null && server.ip != null ? server.ip
                : String.valueOf(context.connection().getRemoteAddress());
        String name = server != null && server.name != null && !server.name.isBlank() ? server.name : address;
        Path cache = AssetSync.cacheDir(Paths.ROOT, address);
        List<AssetSync.Entry> needed;
        try {
            needed = AssetSync.needed(cache, accepted.entries());
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Could not read the server asset cache: {}", e.toString());
            needed = accepted.entries();
        }
        Exchange ex = new Exchange(listener, configuration, cache, name, accepted.entries(), needed);
        ex.replacements = manifest.replacements();
        exchange = ex;
        if (!accepted.notices().isEmpty()) {
            minecraft.execute(() -> notice(accepted.notices().size() + " file(s) the server offered were refused"
                    + " because they did not pass Strata's checks"));
        }
        StrataConfig.ServerAssets mode = StrataConfig.isLoaded() ? StrataConfig.SERVER_ASSETS.get()
                : StrataConfig.SERVER_ASSETS.getDefault();
        if (mode == StrataConfig.ServerAssets.NEVER) {
            listener.send(new AssetNeed(false, List.of()));
            ex.over = true;
            return;
        }
        if (ex.needed.isEmpty()) {
            listener.send(new AssetNeed(true, List.of()));
            finish(ex);
            return;
        }
        if (mode == StrataConfig.ServerAssets.ALWAYS || acceptedThisConnection) {
            accept(ex);
            return;
        }
        // ASK: the question, on the game's thread.
        minecraft.execute(() -> ask(ex));
    }

    private static void ask(Exchange ex) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen previous = minecraft.screen;
        long bytes = AssetSync.totalBytes(ex.needed);
        Component message = Component.literal(ex.serverName + " wants to send you " + ex.needed.size()
                + " custom texture(s) and sound(s), " + size(bytes) + " in all. They are checked by your game,"
                + " kept in config/strata/server_assets, and used only while you are on this server."
                + " You can change this in Strata's Options (Server textures & sounds).");
        ConfirmScreen[] screen = new ConfirmScreen[1];
        screen[0] = new ConfirmScreen(yes -> {
            // Only put the earlier screen back when this one is still showing (a dropped connection may have
            // replaced it).
            if (minecraft.screen == screen[0]) {
                minecraft.setScreen(previous);
            }
            if (exchange != ex || ex.over) {
                return;
            }
            if (yes) {
                accept(ex);
            } else {
                ex.listener.send(new AssetNeed(false, List.of()));
                ex.over = true;
            }
        }, Component.literal("Server textures & sounds"), message, Component.literal("Accept"),
                Component.literal("Decline")) {
            @Override
            public void tick() {
                super.tick();
                // The connection screen behind keeps ticking, so a dropped connection still shows.
                if (previous != null && minecraft.screen == this) {
                    previous.tick();
                }
            }
        };
        minecraft.setScreen(screen[0]);
    }

    private static String size(long bytes) {
        return bytes >= 1 << 20 ? String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0)
                : Math.max(1, bytes >> 10) + " KB";
    }

    /** The player took them: ask the server for the hashes this cache lacks. */
    private static void accept(Exchange ex) {
        acceptedThisConnection = true;
        List<String> hashes = AssetSync.neededHashes(ex.needed);
        ex.pending.addAll(hashes);
        ex.activity = System.currentTimeMillis();
        ex.listener.send(new AssetNeed(true, hashes));
        watch(ex);
    }

    /** A transfer that goes quiet is given up on: what has arrived is used, the rest is not. */
    private static void watch(Exchange ex) {
        TIMER.schedule(() -> {
            if (exchange != ex || ex.over) {
                return;
            }
            if (System.currentTimeMillis() - ex.activity >= AssetRules.FILE_TIMEOUT_MS) {
                Strata.LOGGER.warn("The server stopped sending custom assets; using what arrived");
                finish(ex);
            } else {
                watch(ex);
            }
        }, AssetRules.FILE_TIMEOUT_MS / 4, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------ chunks

    /** A chunk of a file. */
    public static void onChunk(AssetChunk chunk, IPayloadContext context) {
        Exchange ex = exchange;
        if (ex == null || ex.over || ex.listener != context.listener()) {
            return;
        }
        ex.activity = System.currentTimeMillis();
        if (ex.receiver == null) {
            // The first chunk of a file: one that was asked for.
            if (chunk.index() != 0 || !ex.pending.contains(chunk.sha256())) {
                stray(ex);
                return;
            }
            while (!ex.pending.peek().equals(chunk.sha256())) {
                ex.pending.poll();
            }
            AssetSync.Entry first = null;
            for (AssetSync.Entry e : ex.needed) {
                if (e.sha256().equals(chunk.sha256())) {
                    first = e;
                    break;
                }
            }
            if (first == null) {
                stray(ex);
                return;
            }
            ex.receiver = new AssetSync.Receiver(first.kind(), first.sha256(), first.size(), AssetRules.CHUNK_BYTES);
            ex.receiving = chunk.sha256();
        } else if (!chunk.sha256().equals(ex.receiving)) {
            stray(ex);
            return;
        }
        String problem = ex.receiver.accept(chunk.index(), chunk.data());
        if (problem != null) {
            drop(ex, "a chunk was refused: " + problem, chunk.index());
            return;
        }
        if (!ex.receiver.complete()) {
            ex.listener.send(new AssetAck(chunk.sha256(), chunk.index(), AssetAck.CHUNK));
            return;
        }
        AssetRules.Checked checked = ex.receiver.finish();
        if (!checked.ok()) {
            drop(ex, checked.problem(), chunk.index());
            return;
        }
        try {
            for (AssetSync.Entry e : ex.needed) {
                if (e.sha256().equals(ex.receiving)) {
                    // A texture is stored as the clean copy Strata wrote; a sound as it came (checked).
                    AssetSync.store(ex.cache, e, checked.clean());
                    ex.stored = true;
                }
            }
        } catch (IOException | RuntimeException e) {
            drop(ex, "could not be written: " + e, chunk.index());
            return;
        }
        ex.pending.remove(ex.receiving);
        ex.receiver = null;
        ex.receiving = null;
        ex.listener.send(new AssetAck(chunk.sha256(), chunk.index(), AssetAck.STORED));
        if (ex.pending.isEmpty()) {
            finish(ex);
        }
    }

    private static void drop(Exchange ex, String why, int index) {
        String hash = ex.receiving;
        Strata.LOGGER.warn("A server file was dropped: {}", why);
        Minecraft.getInstance().execute(() -> notice("A file from the server was refused: " + why));
        ex.pending.remove(hash);
        ex.receiver = null;
        ex.receiving = null;
        ex.listener.send(new AssetAck(hash, index, AssetAck.DROPPED));
        if (ex.pending.isEmpty()) {
            finish(ex);
        }
    }

    private static void stray(Exchange ex) {
        if (++ex.strays > AssetRules.MAX_STRAY_CHUNKS) {
            Strata.LOGGER.warn("The server sent files nobody asked for; ignoring the rest");
            ex.over = true;
            finish(ex);
        }
    }

    // ------------------------------------------------------------ the set is in

    /** Makes the cache exactly what the manifest and the transfer gave, serves it, and schedules the reload. */
    private static void finish(Exchange ex) {
        synchronized (ClientAssets.class) {
            if (ex.applied || exchange != ex) {
                return;
            }
            ex.applied = true;
            ex.over = true;
        }
        try {
            // The replacements first: finishSet builds the pack from them.
            CustomAssetsOps.writeReplacementLines(ex.cache, ex.replacements);
            List<AssetSync.Entry> have = AssetSync.finishSet(ex.cache, ex.manifest);
            CustomAssetsOps.ensurePack(ex.cache);
            CustomAssetsPack.use(ex.cache);
            StringBuilder sig = new StringBuilder(ex.cache.toString());
            ex.replacements.forEach(r -> sig.append('|').append(r));
            for (AssetSync.Entry e : have) {
                sig.append('|').append(e.key()).append(':').append(e.sha256()).append(':')
                        .append(e.category()).append(':').append(e.subtitle());
            }
            String signature = sig.toString();
            if (signature.equals(loadedSignature)) {
                return;
            }
            if (ex.configuration) {
                reloadAfterJoin = true;
                pendingSignature = signature;
            } else {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> reload(signature, "Strata: the server's textures and sounds were updated"));
            }
        } catch (IOException | RuntimeException e) {
            Strata.LOGGER.warn("Could not apply the server's custom assets: {}", e.toString());
        }
    }

    private static volatile String pendingSignature;

    private static void reload(String signature, String message) {
        loadedSignature = signature;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.reloadResourcePacks();
        if (message != null) {
            notice(message);
        }
    }

    private static void notice(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("Strata: " + text), false);
        } else {
            Strata.LOGGER.info("Strata: {}", text);
        }
    }

    // ------------------------------------------------------------ joining and leaving

    /** In the level: the ONE reload of a join (see the class doc). */
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (reloadAfterJoin) {
            reloadAfterJoin = false;
            String signature = pendingSignature;
            Minecraft.getInstance().execute(() -> reload(signature, null));
        }
    }

    /** Left a server: this machine's library again, and a reload only if what was served changed. */
    public static void onLeave() {
        exchange = null;
        acceptedThisConnection = false;
        reloadAfterJoin = false;
        if (CustomAssetsPack.use(null) || !"local".equals(loadedSignature)) {
            Minecraft.getInstance().execute(() -> reload("local", null));
        }
    }

    // ------------------------------------------------------------ an operator's upload

    private static volatile byte[] uploading;
    private static volatile int uploadChunks;

    /**
     * Whether this game is an operator in a server world, so that an import can be sent to the server: on a
     * remote server (not this game's own world) and permission level 2 or more.
     */
    public static boolean canUpload() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.getConnection() != null
                && !minecraft.hasSingleplayerServer() && ClientNetwork.serverSync() != null
                && minecraft.player.hasPermissions(2);
    }

    /** Sends a file to the server. */
    public static String upload(String kind, String category, String name, String subtitle, byte[] raw,
                                boolean replace) {
        if (!canUpload()) {
            return "You need to be an operator on a server to upload";
        }
        AssetRules.Checked checked = AssetRules.check(kind, raw, false);
        if (!checked.ok()) {
            return checked.problem();
        }
        byte[] clean = checked.clean();
        String problem = new AssetSync.Entry(kind, category == null ? "" : category, name, AssetSync.sha256(clean),
                clean.length, "").problem();
        if (problem != null) {
            return "Not a valid asset: " + problem;
        }
        uploading = clean;
        uploadChunks = AssetSync.chunkCount(clean.length, AssetRules.UPLOAD_CHUNK_BYTES);
        PacketDistributor.sendToServer(new AssetUploadBegin(kind, category == null ? "" : category, name,
                AssetRules.cleanSubtitle(subtitle), clean.length, AssetSync.sha256(clean), replace));
        return null;
    }

    // ------------------------------------------------------------ a profile's files to the server

    /** The custom files a profile's replacements use, waiting to go to the server one at a time. */
    private static final ArrayDeque<CustomAssetsOps.Asset> QUEUE = new ArrayDeque<>();
    private static volatile CustomAssetsOps.Asset queued;

    /**
     * Sends the server each custom file the profile's replacements use that the server does not hold (as the
     * last manifest says), one after another. Returns how many go.
     */
    public static int uploadForProfile(String profile) {
        java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
        for (CustomAssetsOps.Replacement r : CustomAssetsOps.replacements(Paths.profile(profile))) {
            wanted.add(r.kind() + "|" + r.customId());
        }
        Exchange last = exchange;
        List<CustomAssetsOps.Asset> send = new java.util.ArrayList<>();
        for (CustomAssetsOps.Asset a : CustomAssetsOps.all(Paths.ROOT)) {
            if (wanted.contains(a.kind() + "|" + a.id()) && !serverHolds(last, a)) {
                send.add(a);
            }
        }
        synchronized (QUEUE) {
            QUEUE.addAll(send);
        }
        if (queued == null) {
            next(0);
        }
        return send.size();
    }

    private static boolean serverHolds(Exchange last, CustomAssetsOps.Asset a) {
        if (last == null) {
            return false;
        }
        try {
            byte[] clean = AssetRules.check(a.kind(), java.nio.file.Files.readAllBytes(
                    a.packFile(CustomAssetsOps.packDir(Paths.ROOT))), false).clean();
            String sha = AssetSync.sha256(clean);
            return last.manifest.stream().anyMatch(e -> e.kind().equals(a.kind()) && e.name().equals(a.id())
                    && e.sha256().equals(sha));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Starts the next queued file after a wait, the server's gap between uploads. */
    private static void next(long waitMs) {
        TIMER.schedule(() -> Minecraft.getInstance().execute(() -> {
            CustomAssetsOps.Asset a;
            synchronized (QUEUE) {
                a = QUEUE.poll();
            }
            queued = a;
            if (a == null) {
                return;
            }
            String problem;
            try {
                problem = upload(a.kind(), a.category(), a.id(), a.subtitle(), java.nio.file.Files.readAllBytes(
                        a.packFile(CustomAssetsOps.packDir(Paths.ROOT))), true);
            } catch (IOException e) {
                problem = e.getMessage();
            }
            if (problem != null) {
                notice("Could not send the custom " + a.kind().toLowerCase(java.util.Locale.ROOT) + " " + a.id()
                        + ": " + problem);
                next(AssetRules.UPLOAD_GAP_MS + 500);
            }
        }), waitMs, TimeUnit.MILLISECONDS);
    }

    /** The server's answer to the start or a chunk of an upload. */
    public static void onUploadAck(AssetUploadAck ack) {
        byte[] data = uploading;
        if (data == null) {
            return;
        }
        if (!ack.ok() || ack.next() >= uploadChunks) {
            uploading = null;
            CustomAssetsOps.Asset a = queued;
            if (a != null) {
                // The server's limit of a minute: the same file again after it; any other refusal skips it.
                if (!ack.ok() && ack.message().startsWith("Too much")) {
                    synchronized (QUEUE) {
                        QUEUE.addFirst(a);
                    }
                    next(60_000);
                } else {
                    next(AssetRules.UPLOAD_GAP_MS + 500);
                }
            }
            return;
        }
        PacketDistributor.sendToServer(new AssetUploadChunk(ack.next(),
                AssetSync.chunk(data, ack.next(), AssetRules.UPLOAD_CHUNK_BYTES)));
    }
}
