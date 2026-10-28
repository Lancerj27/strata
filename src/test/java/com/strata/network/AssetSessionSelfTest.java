package com.strata.network;

import com.strata.SelfTestHarness;
import com.strata.domain.AssetRules;
import com.strata.domain.AssetSync;
import com.strata.network.StrataPayloads.AssetAck;
import com.strata.network.StrataPayloads.AssetNeed;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Headless tests of the server's custom assets exchange with one player (AssetServer.Session). */
public final class AssetSessionSelfTest {

    private AssetSessionSelfTest() {}

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];
        try {
            Path dir = Files.createTempDirectory("strata-session");
            try {
                cases(dir, lines, tally);
            } finally {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (Exception e) {
            lines.add("ERROR setup " + e);
            tally[1]++;
        }
        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }

    private static void cases(Path dir, List<String> lines, int[] tally) throws Exception {
        // File A takes two chunks, file B one.
        byte[] a = new byte[AssetRules.CHUNK_BYTES + 10];
        byte[] b = new byte[100];
        a[0] = 1;
        b[0] = 2;
        Path pa = Files.write(dir.resolve("a.bin"), a);
        Path pb = Files.write(dir.resolve("b.bin"), b);
        String ha = AssetSync.sha256(a);
        String hb = AssetSync.sha256(b);
        Map<String, Path> files = new LinkedHashMap<>();
        files.put(ha, pa);
        files.put(hb, pb);
        AssetSync.Built built = new AssetSync.Built(List.of(), files, List.of());

        SelfTestHarness.check(lines, tally, "session.1 a second offer mid-file, with a late ack, completes", () -> {
            AtomicBoolean kicked = new AtomicBoolean();
            ICommonPacketListener player = fakePlayer(kicked);
            AssetServer.Session s = new AssetServer.Session(player, false, dir);
            s.offer(built);
            s.onNeed(new AssetNeed(true, List.of(ha)));
            s.onAck(new AssetAck(ha, 0, AssetAck.CHUNK));
            // The library changes while chunk 1 of A is on its way.
            s.offer(built);
            s.onAck(new AssetAck(ha, 1, AssetAck.STORED));
            s.onNeed(new AssetNeed(true, List.of(ha, hb)));
            s.onAck(new AssetAck(ha, 0, AssetAck.CHUNK));
            s.onAck(new AssetAck(ha, 1, AssetAck.STORED));
            s.onAck(new AssetAck(hb, 0, AssetAck.STORED));
            return !kicked.get() && finished(s);
        });

        SelfTestHarness.check(lines, tally, "session.2 a drop mid-file moves to the next file", () -> {
            AtomicBoolean kicked = new AtomicBoolean();
            ICommonPacketListener player = fakePlayer(kicked);
            AssetServer.Session s = new AssetServer.Session(player, false, dir);
            s.offer(built);
            s.onNeed(new AssetNeed(true, List.of(ha, hb)));
            s.onAck(new AssetAck(ha, 0, AssetAck.DROPPED));
            s.onAck(new AssetAck(hb, 0, AssetAck.STORED));
            return !kicked.get() && finished(s);
        });

        SelfTestHarness.check(lines, tally, "session.3 a store before the last chunk is a stray", () -> {
            AtomicBoolean kicked = new AtomicBoolean();
            ICommonPacketListener player = fakePlayer(kicked);
            AssetServer.Session s = new AssetServer.Session(player, false, dir);
            s.offer(built);
            s.onNeed(new AssetNeed(true, List.of(ha)));
            s.onAck(new AssetAck(ha, 0, AssetAck.STORED));
            return !kicked.get() && !finished(s);
        });

        SelfTestHarness.check(lines, tally, "session.4 too many wrong answers disconnect the player", () -> {
            AtomicBoolean kicked = new AtomicBoolean();
            ICommonPacketListener player = fakePlayer(kicked);
            AssetServer.Session s = new AssetServer.Session(player, false, dir);
            s.offer(built);
            for (int i = 0; i <= AssetRules.MAX_STRAY_CHUNKS; i++) {
                s.onAck(new AssetAck("nope", i, AssetAck.CHUNK));
            }
            return kicked.get();
        });
    }

    /** A player connection that ignores what it is sent and records a disconnect. */
    private static ICommonPacketListener fakePlayer(AtomicBoolean kicked) {
        return (ICommonPacketListener) Proxy.newProxyInstance(AssetSessionSelfTest.class.getClassLoader(),
                new Class<?>[] {ICommonPacketListener.class}, (proxy, method, args) -> {
                    if (method.getName().equals("disconnect")) {
                        kicked.set(true);
                    }
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) {
                        return false;
                    }
                    if (r.isPrimitive() && r != void.class) {
                        return 0;
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }

    private static boolean finished(AssetServer.Session s) throws ReflectiveOperationException {
        var f = AssetServer.Session.class.getDeclaredField("finished");
        f.setAccessible(true);
        synchronized (s) {
            return f.getBoolean(s);
        }
    }
}
