package com.strata.data;

import com.strata.Strata;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ALL OR NOTHING. */
public final class Rollback {

    private Rollback() {
    }

    /** Each table the window wrote, as it was before: its bytes, or null when it did not exist. */
    private static final Map<Path, byte[]> BEFORE = new LinkedHashMap<>();
    private static Thread owner;

    /** Opens the window, unless one is open: the first report of an operation opens it. */
    public static synchronized void open() {
        if (owner == null) {
            owner = Thread.currentThread();
            BEFORE.clear();
        }
    }

    /** Keeps a table as it was, at the window's first write of it. */
    public static synchronized void beforeWrite(Path path) {
        if (owner != Thread.currentThread() || path == null) {
            return;
        }
        Path p = path.toAbsolutePath().normalize();
        if (!BEFORE.containsKey(p)) {
            try {
                BEFORE.put(p, Files.isRegularFile(p) ? Files.readAllBytes(p) : null);
            } catch (IOException e) {
                Strata.LOGGER.warn("Rollback: could not keep {} before writing it: {}", p, e.toString());
            }
        }
    }

    /** Closes the window. */
    public static synchronized List<Path> close(boolean ok) {
        List<Path> stuck = new ArrayList<>();
        if (owner != null && owner != Thread.currentThread()) {
            // Another thread's window: leave it open for its owner.
            return stuck;
        }
        if (!ok && owner == Thread.currentThread()) {
            for (Map.Entry<Path, byte[]> e : BEFORE.entrySet()) {
                try {
                    restore(e.getKey(), e.getValue());
                } catch (IOException ex) {
                    Strata.LOGGER.error("Rollback: could not put {} back: {}", e.getKey(), ex.toString());
                    stuck.add(e.getKey());
                }
            }
            if (!BEFORE.isEmpty()) {
                Strata.LOGGER.warn("Rollback: a save failed partway; {} table(s) put back as they were",
                        BEFORE.size() - stuck.size());
            }
        }
        BEFORE.clear();
        owner = null;
        return stuck;
    }

    private static void restore(Path path, byte[] bytes) throws IOException {
        try {
            if (bytes == null) {
                Files.deleteIfExists(path);
                return;
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(temp, bytes);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            CsvTable.forget(path);
        }
    }
}
