package com.strata.domain;

import com.strata.data.EditHistory;

/** The editor's Undo. */
public final class HistoryOps {

    private HistoryOps() {
    }

    /** True when there is a change to undo. */
    public static boolean canUndo() {
        return EditHistory.canUndo();
    }

    /** The Undo button's words for the newest change: where it was made. */
    public static String undoText() {
        String where = EditHistory.lastWhere();
        return where.isEmpty() ? "Nothing to undo" : "Undo your last change, made in " + where;
    }

    /** Puts the newest change back, then reloads. */
    public static WriteReport undo() {
        String[] problem = new String[1];
        WriteReport[] report = new WriteReport[1];
        EditHistory.whileRestoring(() -> {
            problem[0] = EditHistory.undo();
            if (problem[0] == null) {
                report[0] = Commit.reloadIfOk(new WriteReport().record("the undo", true));
            }
        });
        return problem[0] != null ? WriteReport.refused(problem[0]) : report[0];
    }
}
