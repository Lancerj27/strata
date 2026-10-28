package com.strata.domain;

import com.strata.data.Config;

import java.util.function.Supplier;

/**
 * The last step of every editor operation: reload the config when every write landed, so the game and the
 * editor read the new rows.
 */
final class Commit {

    private Commit() {
    }

    /** How deep {@link #once} calls are, and whether a reload waits. */
    private static int deferring;
    private static boolean waiting;

    static WriteReport reloadIfOk(WriteReport report) {
        if (deferring > 0) {
            // The outermost once() closes the window, saves and reloads, so a batch stays all or nothing.
            waiting |= report.ok();
            return report;
        }
        // ALL OR NOTHING: a failed operation's tables go back as they were, so no
        // half-saved state is left.
        for (java.nio.file.Path stuck : com.strata.data.Rollback.close(report.ok())) {
            report.record("putting " + stuck.getFileName() + " back", false);
        }
        if (report.ok()) {
            // Kept in the active profile, never in Default.
            com.strata.data.ProfileManager.autoSave();
            Config.reloadAfterEdit();
        }
        return report;
    }

    /** Runs the work with every reload in it held back, then reloads once if any was asked for. */
    static WriteReport once(Supplier<WriteReport> work) {
        deferring++;
        WriteReport report = null;
        try {
            report = work.get();
        } finally {
            deferring--;
            if (deferring == 0) {
                boolean ok = report != null && report.ok();
                for (java.nio.file.Path stuck : com.strata.data.Rollback.close(ok)) {
                    if (report != null) {
                        report.record("putting " + stuck.getFileName() + " back", false);
                    }
                }
                if (ok && waiting) {
                    com.strata.data.ProfileManager.autoSave();
                    Config.reloadAfterEdit();
                }
                waiting = false;
            }
        }
        return report;
    }
}
