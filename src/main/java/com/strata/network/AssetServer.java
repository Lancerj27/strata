package com.strata.network;

import com.strata.Strata;
import com.strata.data.Paths;
import com.strata.domain.AssetRules;
import com.strata.domain.AssetSync;
import com.strata.domain.CustomAssetsOps;
import com.strata.domain.WriteReport;
import com.strata.network.StrataPayloads.AssetAck;
import com.strata.network.StrataPayloads.AssetChunk;
import com.strata.network.StrataPayloads.AssetManifest;
import com.strata.network.StrataPayloads.AssetNeed;
import com.strata.network.StrataPayloads.AssetUploadAck;
import com.strata.network.StrataPayloads.AssetUploadBegin;
import com.strata.network.StrataPayloads.AssetUploadChunk;
import com.strata.network.StrataPayloads.UploadResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** THE SERVER'S SIDE OF SERVING CUSTOM TEXTURES AND SOUNDS. */
public final class AssetServer {

    private AssetServer() {
    }

    public static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("strata:assets");

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Strata asset timer");
        t.setDaemon(true);
        return t;
    });

    private static final Map<Object, Session> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());

    // ------------------------------------------------------------ the configuration task

    /** Adds the task to a connection that is being configured. */
    public static void onConfigurationTasks(RegisterConfigurationTasksEvent event) {
        var listener = event.getListener();
        if (!listener.hasChannel(AssetManifest.TYPE) || listener.getConnection().isMemoryConnection()) {
            return;
        }
        AssetSync.Built built = AssetSync.buildManifest(Paths.ROOT);
        if (built.entries().isEmpty()) {
            // Nothing to serve: no task, no exchange.
            return;
        }
        event.register(new Task(listener, built));
    }

    private static final class Task implements ICustomConfigurationTask {
        private final ServerConfigurationPacketListener listener;
        private final AssetSync.Built built;

        Task(ServerConfigurationPacketListener listener, AssetSync.Built built) {
            this.listener = listener;
            this.built = built;
        }

        @Override
        public ConfigurationTask.Type type() {
            return TASK;
        }

        @Override
        public void run(java.util.function.Consumer<CustomPacketPayload> sender) {
            Session session = new Session(listener, true, Paths.ROOT);
            SESSIONS.put(listener, session);
            session.offer(built);
        }
    }

    // ------------------------------------------------------------ the live push

    /**
     * The library changed (an import, a removal, /strata assets reload): every player in the game gets the
     * new manifest, and fetches what it lacks.
     */
    public static void libraryChanged(MinecraftServer server) {
        AssetSync.Built built = AssetSync.buildManifest(Paths.ROOT);
        built.notices().forEach(n -> Strata.LOGGER.warn("{}", n));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var connection = player.connection;
            if (connection.getConnection().isMemoryConnection() || !connection.hasChannel(AssetManifest.TYPE)) {
                continue;
            }
            Session session = new Session(connection, false, Paths.ROOT);
            SESSIONS.put(connection, session);
            session.offer(built);
        }
    }

    /** Tells the operators about files left out of the library (a one-line notice each). */
    public static void noticeOps(MinecraftServer server, List<String> notices) {
        for (String n : notices) {
            Strata.LOGGER.warn("{}", n);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.hasPermissions(StrataNetwork.OPERATOR)) {
                    p.sendSystemMessage(Component.literal("Strata: " + n));
                }
            }
        }
    }

    // ------------------------------------------------------------ messages from a client

    public static void onNeed(AssetNeed need, IPayloadContext context) {
        Session session = SESSIONS.get(context.listener());
        if (session == null) {
            return;
        }
        session.onNeed(need);
    }

    public static void onAck(AssetAck ack, IPayloadContext context) {
        Session session = SESSIONS.get(context.listener());
        if (session == null) {
            return;
        }
        session.onAck(ack);
    }

    // ------------------------------------------------------------ one player's exchange

    /** Package-private for AssetSessionSelfTest. */
    static final class Session {
        private final WeakReference<net.neoforged.neoforge.common.extensions.ICommonPacketListener> listener;
        private final boolean configuration;
        private final AssetSync.Throttle throttle = new AssetSync.Throttle();
        private AssetSync.Built built;
        private final ArrayDeque<String> queue = new ArrayDeque<>();
        private String current;
        private byte[] currentBytes;
        private int sentIndex;
        private boolean needSeen;
        private boolean finished;
        private int strays;
        private long generation;
        private final java.nio.file.Path root;

        Session(net.neoforged.neoforge.common.extensions.ICommonPacketListener listener, boolean configuration,
                java.nio.file.Path root) {
            this.root = root;
            this.listener = new WeakReference<>(listener);
            this.configuration = configuration;
        }

        private void send(CustomPacketPayload payload) {
            var l = listener.get();
            if (l != null) {
                l.send(payload);
            }
        }

        synchronized void offer(AssetSync.Built built) {
            this.built = built;
            this.queue.clear();
            this.current = null;
            this.currentBytes = null;
            this.needSeen = false;
            this.finished = false;
            this.strays = 0;
            long gen = ++generation;
            send(new AssetManifest(built.entries(), AssetSync.replacementLines(root, built.entries())));
            // A client that never answers (a screen left open) is let through, never held forever.
            TIMER.schedule(() -> expire(gen), AssetRules.CONSENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        private void expire(long gen) {
            synchronized (this) {
                if (finished || gen != generation || current != null) {
                    return;
                }
                if (needSeen) {
                    return;
                }
                Strata.LOGGER.info("A player did not answer the custom assets question in time");
                finish();
            }
        }

        synchronized void onNeed(AssetNeed need) {
            if (finished || needSeen || built == null) {
                return;
            }
            needSeen = true;
            if (!need.accept()) {
                finish();
                return;
            }
            for (String hash : need.hashes()) {
                if (built.files().containsKey(hash) && !queue.contains(hash)) {
                    queue.add(hash);
                }
            }
            next();
        }

        /** Starts the next file of the queue, or finishes. */
        private void next() {
            current = null;
            currentBytes = null;
            while (!queue.isEmpty()) {
                String hash = queue.poll();
                try {
                    byte[] data = java.nio.file.Files.readAllBytes(built.files().get(hash));
                    if (data.length > AssetRules.MAX_SOUND_FILE || !AssetSync.sha256(data).equals(hash)) {
                        Strata.LOGGER.warn("A custom asset changed on disk while it was offered; skipped");
                        continue;
                    }
                    current = hash;
                    currentBytes = data;
                    sentIndex = 0;
                    sendChunk(0);
                    return;
                } catch (java.io.IOException e) {
                    Strata.LOGGER.warn("Could not read a custom asset to send: {}", e.toString());
                }
            }
            finish();
        }

        private void sendChunk(int index) {
            byte[] chunk = AssetSync.chunk(currentBytes, index, AssetRules.CHUNK_BYTES);
            long delay = throttle.delayMs(System.currentTimeMillis(), chunk.length);
            String hash = current;
            long gen = generation;
            sentIndex = index;
            Runnable go = () -> send(new AssetChunk(hash, index, chunk));
            if (delay <= 0) {
                go.run();
            } else {
                TIMER.schedule(go, delay, TimeUnit.MILLISECONDS);
            }
            // A client that stops answering mid-file is let through when the file times out.
            TIMER.schedule(() -> stalled(gen, hash, index), AssetRules.FILE_TIMEOUT_MS + delay, TimeUnit.MILLISECONDS);
        }

        private void stalled(long gen, String hash, int index) {
            synchronized (this) {
                if (finished || gen != generation || !hash.equals(current) || index != sentIndex) {
                    return;
                }
                Strata.LOGGER.info("A player stopped acknowledging a custom asset; the rest is dropped");
                queue.clear();
                finish();
            }
        }

        synchronized void onAck(AssetAck ack) {
            if (finished || current == null || !current.equals(ack.sha256())) {
                stray();
                return;
            }
            if (ack.state() == AssetAck.CHUNK) {
                if (ack.index() != sentIndex
                        || ack.index() + 1 >= AssetSync.chunkCount(currentBytes.length, AssetRules.CHUNK_BYTES)) {
                    stray();
                    return;
                }
                sendChunk(ack.index() + 1);
            } else if (ack.state() == AssetAck.STORED || ack.state() == AssetAck.DROPPED) {
                if (ack.index() != sentIndex
                        || ack.index() + 1 != AssetSync.chunkCount(currentBytes.length, AssetRules.CHUNK_BYTES)
                        && ack.state() == AssetAck.STORED) {
                    // A drop may come at any chunk (the client refused it); a store only at the last.
                    stray();
                    return;
                }
                if (ack.state() == AssetAck.DROPPED) {
                    Strata.LOGGER.info("A player dropped a custom asset it was sent (it did not pass its checks)");
                }
                next();
            } else {
                stray();
            }
        }

        private void stray() {
            if (++strays > AssetRules.MAX_STRAY_CHUNKS) {
                finished = true;
                var l = listener.get();
                if (l != null) {
                    Strata.LOGGER.warn("Disconnecting a player for answering the custom assets exchange wrongly");
                    l.disconnect(Component.literal("Strata: bad custom assets answers"));
                }
            }
        }

        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            current = null;
            currentBytes = null;
            if (configuration) {
                var l = listener.get();
                // On the server thread, as the game finishes its own tasks: this runs on the network
                // thread or the asset timer, and the task queue is not thread safe.
                if (l instanceof ServerConfigurationPacketListener cfg) {
                    MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        server.execute(() -> finishOn(cfg));
                    } else {
                        finishOn(cfg);
                    }
                }
            }
        }
    }

    /** Finishes the task; a player who left meanwhile has no task to finish, and that is fine. */
    private static void finishOn(ServerConfigurationPacketListener cfg) {
        try {
            cfg.finishCurrentTask(TASK);
        } catch (IllegalStateException e) {
            Strata.LOGGER.debug("The custom assets task was already over: {}", e.toString());
        }
    }

    // ------------------------------------------------------------ operators' uploads

    private static final Map<UUID, AssetSync.UploadLimiter> LIMITERS = new HashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    private record Upload(AssetUploadBegin begin, AssetSync.Receiver receiver) {
    }

    private static void refuse(IPayloadContext context, String message) {
        context.reply(new AssetUploadAck(0, false, message));
        context.reply(new UploadResult(false, message));
    }

    public static void onUploadBegin(AssetUploadBegin begin, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        synchronized (UPLOADS) {
            UPLOADS.remove(id);
            if (!player.hasPermissions(StrataNetwork.OPERATOR)) {
                refuse(context, "Only a server operator can upload custom textures and sounds");
                return;
            }
            String problem = new AssetSync.Entry(begin.kind(), begin.category(), begin.name(), begin.sha256(),
                    begin.size(), "").problem();
            if (problem == null) {
                String sub = AssetRules.subtitleProblem(AssetRules.cleanSubtitle(begin.subtitle()));
                problem = sub == null ? null : "a bad subtitle";
            }
            if (problem != null) {
                refuse(context, "The server refused the upload: " + problem);
                return;
            }
            String wait = LIMITERS.computeIfAbsent(id, k -> new AssetSync.UploadLimiter())
                    .allow(System.currentTimeMillis(), begin.size());
            if (wait != null) {
                refuse(context, wait);
                return;
            }
            UPLOADS.put(id, new Upload(begin,
                    new AssetSync.Receiver(begin.kind(), begin.sha256(), begin.size(), AssetRules.UPLOAD_CHUNK_BYTES)));
        }
        context.reply(new AssetUploadAck(0, true, ""));
    }

    public static void onUploadChunk(AssetUploadChunk chunk, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Upload upload;
        synchronized (UPLOADS) {
            upload = UPLOADS.get(player.getUUID());
            if (upload == null || !player.hasPermissions(StrataNetwork.OPERATOR)) {
                UPLOADS.remove(player.getUUID());
                return;
            }
            String problem = upload.receiver().accept(chunk.index(), chunk.data());
            if (problem != null) {
                UPLOADS.remove(player.getUUID());
                refuse(context, "The server refused the upload: " + problem);
                return;
            }
            if (!upload.receiver().complete()) {
                context.reply(new AssetUploadAck(upload.receiver().nextIndex(), true, ""));
                return;
            }
            UPLOADS.remove(player.getUUID());
        }
        // Whole and in: checked as hash, then as format, then imported like any import, on the server thread.
        AssetSync.Receiver receiver = upload.receiver();
        AssetUploadBegin begin = upload.begin();
        MinecraftServer server = player.server;
        server.execute(() -> {
            AssetRules.Checked checked = receiver.finish();
            if (!checked.ok()) {
                refuse(context, "The server refused the upload: " + checked.problem());
                return;
            }
            WriteReport report = CustomAssetsOps.addBytes(Paths.ROOT, begin.kind(), begin.name(), begin.category(),
                    begin.subtitle(), receiver.bytes(), "upload", begin.replace());
            // The report opened the rollback window on this thread; no Commit closes it here.
            com.strata.data.Rollback.close(report.ok());
            if (!report.ok()) {
                refuse(context, report.refusal() != null ? report.refusal() : "The server could not store the file");
                return;
            }
            Strata.LOGGER.info("{} uploaded the custom {} {}", player.getGameProfile().getName(),
                    begin.kind().toLowerCase(java.util.Locale.ROOT), begin.name());
            context.reply(new AssetUploadAck(receiver.nextIndex(), true, "done"));
            context.reply(new UploadResult(true, "Uploaded the custom " + begin.kind().toLowerCase(java.util.Locale.ROOT)
                    + " " + begin.name() + ". Players get it at once."));
            libraryChanged(server);
        });
    }

    /** A player left: their upload and limiter go. */
    public static void onLoggedOut(UUID id) {
        synchronized (UPLOADS) {
            UPLOADS.remove(id);
            LIMITERS.remove(id);
        }
    }
}
