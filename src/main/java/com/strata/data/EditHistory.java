package com.strata.data;

import com.strata.Strata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** THE EDITOR'S UNDO. */
public final class EditHistory {

    private EditHistory() {
    }

    /** One thing a change can put back. */
    public interface Step {
        /** True when the thing still holds what the change made it. */
        boolean unchangedSince();

        /** True when the change left it as it was. */
        boolean noChange();

        /** Puts it back as it was before the change. */
        void restore() throws IOException;

        /** Makes it hold what the change made it again, after a failed undo. */
        default void reapply() throws IOException {
        }
    }

    /** One editor action's steps, and where it was made (a screen's title). */
    public record Change(String where, List<Step> steps) {
    }

    /** How many changes the session keeps. */
    public static final int DEPTH = 50;

    private static volatile Supplier<String> actor = () -> null;
    private static final Deque<Change> done = new ArrayDeque<>();
    /** The open action's file steps by path, and its other steps, in order. */
    private static final Map<Path, FileStep> openFiles = new LinkedHashMap<>();
    private static final List<Step> openOther = new ArrayList<>();
    private static String openWhere;
    /** How deep the unrecorded work is: an undo's writes and reload, and a reload's own table migration. */
    private static int restoring;

    /** The editor that is acting now, or null: installed by the client. */
    public static void setActor(Supplier<String> who) {
        actor = who == null ? () -> null : who;
    }

    private static synchronized boolean quiet() {
        return restoring > 0;
    }

    private static String who() {
        if (quiet()) {
            return null;
        }
        try {
            return actor.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Keeps a file as it was before the open action's first write of it. */
    public static synchronized void beforeWrite(Path path) {
        // The operation's rollback keeps it too (Rollback), editor or not.
        Rollback.beforeWrite(path);
        String where = who();
        if (where == null || path == null) {
            return;
        }
        Path p = path.toAbsolutePath().normalize();
        if (!openFiles.containsKey(p)) {
            openWhere = where;
            openFiles.put(p, new FileStep(p, read(p)));
        }
    }

    /** Records a step of the open action that is not a file (a setting). */
    public static synchronized void record(Step step) {
        String where = who();
        if (where == null || step == null) {
            return;
        }
        openWhere = where;
        openOther.add(step);
    }

    /** Ends the open action: it becomes the newest change, unless it changed nothing. */
    public static synchronized void close() {
        if (openFiles.isEmpty() && openOther.isEmpty()) {
            return;
        }
        List<Step> steps = new ArrayList<>();
        for (FileStep f : openFiles.values()) {
            f.after = read(f.path);
            steps.add(f);
        }
        steps.addAll(openOther);
        String where = openWhere == null ? "" : openWhere;
        openFiles.clear();
        openOther.clear();
        openWhere = null;
        if (steps.stream().allMatch(Step::noChange)) {
            return;
        }
        done.push(new Change(where, List.copyOf(steps)));
        while (done.size() > DEPTH) {
            done.removeLast();
        }
    }

    /** True when there is a change to undo. */
    public static synchronized boolean canUndo() {
        return !done.isEmpty();
    }

    /** Where the newest change was made, or "" when there is none. */
    public static synchronized String lastWhere() {
        return done.isEmpty() ? "" : done.peek().where();
    }

    /** Puts the newest change back. */
    public static synchronized String undo() {
        close();
        Change last = done.peek();
        if (last == null) {
            return "There is nothing to undo";
        }
        for (Step s : last.steps()) {
            if (!s.unchangedSince()) {
                return "It changed again since, outside this undo: " + describe(s);
            }
        }
        List<Step> reversed = new ArrayList<>(last.steps());
        java.util.Collections.reverse(reversed);
        List<Step> putBack = new ArrayList<>();
        for (Step s : reversed) {
            try {
                s.restore();
                putBack.add(s);
            } catch (IOException e) {
                Strata.LOGGER.error("Undo: could not put back {}: {}", describe(s), e.toString(), e);
                // THE CHANGE STAYS WHOLE: the steps already put back take the change's values
                // again, so the files and the history agree and a later Undo can try again.
                for (Step back : putBack) {
                    try {
                        back.reapply();
                    } catch (IOException again) {
                        Strata.LOGGER.error("Undo: could not return {} to the change: {}", describe(back),
                                again.toString(), again);
                    }
                }
                return "Could not put back " + describe(s);
            }
        }
        done.pop();
        return null;
    }

    /**
     * Runs the work with recording off: an undo's own writes and reload, and a reload's table migration
     * (Config#reload).
     */
    public static void whileRestoring(Runnable work) {
        synchronized (EditHistory.class) {
            restoring++;
        }
        try {
            work.run();
        } finally {
            synchronized (EditHistory.class) {
                restoring--;
            }
        }
    }

    /** Forgets every change (a gate's reset). */
    public static synchronized void clear() {
        done.clear();
        openFiles.clear();
        openOther.clear();
        openWhere = null;
    }

    private static String describe(Step s) {
        return s instanceof FileStep f ? String.valueOf(f.path.getFileName()) : s.toString();
    }

    /** A file's bytes, or null when it does not exist. */
    private static byte[] read(Path p) {
        try {
            return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** A file as it was before the action, and as the action left it. */
    private static final class FileStep implements Step {
        private final Path path;
        private final byte[] before;
        private byte[] after;

        FileStep(Path path, byte[] before) {
            this.path = path;
            this.before = before;
        }

        @Override
        public boolean unchangedSince() {
            return Arrays.equals(read(path), after);
        }

        @Override
        public boolean noChange() {
            return Arrays.equals(before, after);
        }

        @Override
        public void restore() throws IOException {
            write(before);
        }

        @Override
        public void reapply() throws IOException {
            write(after);
        }

        private void write(byte[] bytes) throws IOException {
            try {
                if (bytes == null) {
                    Files.deleteIfExists(path);
                } else {
                    // Through a temp file, as every save writes: a failed undo
                    // cannot leave half a table.
                    Files.createDirectories(path.getParent());
                    Path temp = path.resolveSibling(path.getFileName() + ".tmp");
                    Files.write(temp, bytes);
                    try {
                        Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } finally {
                CsvTable.forget(path);
            }
        }
    }
}
