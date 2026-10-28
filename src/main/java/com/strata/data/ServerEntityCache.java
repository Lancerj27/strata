package com.strata.data;

import com.strata.Strata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The own-entity variants of the servers this game has joined: variant id to base mob, in
 * config/strata/cache/server-entities.csv.
 */
public final class ServerEntityCache {

    private ServerEntityCache() {
    }

    /** The most types a server may send and the cache keeps: a hostile server cannot grow it without end. */
    public static final int MAX = 512;

    private static final String HEADER = "variant,base";

    /** A variant id as Strata registers it, and a mob id. */
    private static final Pattern VARIANT = Pattern.compile("[A-Za-z0-9_ \\-]{1,64}");

    private static Path file(Path root) {
        return root.resolve("cache").resolve("server-entities.csv");
    }

    /** The stored types: variant id to base mob id. */
    public static Map<String, String> read() {
        return read(Paths.ROOT);
    }

    static Map<String, String> read(Path root) {
        Map<String, String> out = new LinkedHashMap<>();
        Path file = file(root);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String[] cells = line.split(",", 2);
                if (cells.length == 2 && !line.equals(HEADER) && valid(cells[0], cells[1])) {
                    out.put(cells[0].strip(), cells[1].strip());
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read {}: {}", file, e.toString());
        }
        return out;
    }

    /** The server's types this game has not registered: they need a game restart. */
    public static Map<String, String> missing(Map<String, String> fromServer, Set<String> registered) {
        Map<String, String> out = new LinkedHashMap<>();
        fromServer.forEach((id, base) -> {
            if (valid(id, base) && !registered.contains(id)) {
                out.put(id, base);
            }
        });
        return out;
    }

    /** Stores the server's types with the ones already stored. */
    public static void store(Map<String, String> fromServer) {
        store(Paths.ROOT, fromServer);
    }

    static void store(Path root, Map<String, String> fromServer) {
        Map<String, String> all = read(root);
        fromServer.forEach((id, base) -> {
            if (valid(id, base)) {
                all.remove(id.strip());
                all.put(id.strip(), base.strip());
            }
        });
        // Newest last: drop the oldest past MAX.
        for (var it = all.keySet().iterator(); all.size() > MAX && it.hasNext(); ) {
            it.next();
            it.remove();
        }
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        all.forEach((id, base) -> text.append(id).append(',').append(base).append('\n'));
        Path file = file(root);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text.toString());
        } catch (IOException e) {
            Strata.LOGGER.error("Could not store the server's variant types in {}: {}", file, e.toString());
        }
    }

    /** A server's entry is used only when both ids have a safe shape. */
    private static boolean valid(String id, String base) {
        return id != null && base != null && VARIANT.matcher(id.strip()).matches()
                && net.minecraft.resources.ResourceLocation.tryParse(base.strip()) != null;
    }
}
