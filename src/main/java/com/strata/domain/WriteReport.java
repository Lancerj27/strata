package com.strata.domain;

import java.util.ArrayList;
import java.util.List;

/** What a domain operation wrote, and what it could not. */
public final class WriteReport {

    private final List<String> failures = new ArrayList<>();
    private final List<String> emptyCascades = new ArrayList<>();
    private String refusal;

    /** An operation's first report opens its rollback window: made before its first write. */
    public WriteReport() {
        com.strata.data.Rollback.open();
    }

    /** An operation that refused its input and wrote nothing. */
    public static WriteReport refused(String reason) {
        WriteReport report = new WriteReport();
        report.refusal = reason;
        return report;
    }

    private String reloadNote;

    /**
     * CONFIGURE, THEN LOAD: a change that takes effect only when the world is next loaded says so, and that
     * is all.
     */
    public WriteReport needsReload(String note) {
        this.reloadNote = note;
        return this;
    }

    /** What the player must reload for, or null when the change applies at once. */
    public String reloadNote() {
        return reloadNote;
    }

    /** Why the operation refused its input, or null when it did not. */
    public String refusal() {
        return refusal;
    }

    /** Records one write. */
    public WriteReport record(String what, boolean wrote) {
        if (!wrote) {
            failures.add(what);
        }
        return this;
    }

    /** Records a write whose FALSE IS NORMAL, so it never reaches the banner. */
    public WriteReport cascaded(String what, boolean wrote) {
        if (!wrote) {
            emptyCascades.add(what);
        }
        return this;
    }

    /** Cascade writes that matched no rows - worth a debug line, nothing more. */
    public List<String> emptyCascades() {
        return List.copyOf(emptyCascades);
    }

    /** True when the op did not refuse and every write it attempted landed. */
    public boolean ok() {
        return refusal == null && failures.isEmpty();
    }

    /** What failed, in the designer's words, in the order it was attempted. */
    public List<String> failures() {
        return List.copyOf(failures);
    }

    /** The first failure, or null when everything landed. */
    public String first() {
        return failures.isEmpty() ? null : failures.get(0);
    }
}
