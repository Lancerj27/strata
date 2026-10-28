package com.strata.client.widget;

import java.util.ArrayDeque;
import java.util.Deque;

/** Generic undo stack for screen-level state snapshots. */
public class UndoManager<T> {

    private final Deque<T> undoStack = new ArrayDeque<>();
    private final int maxDepth;

    public UndoManager(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public UndoManager() {
        this(50);
    }

    public void pushState(T snapshot) {
        undoStack.push(snapshot);
        while (undoStack.size() > maxDepth) {
            undoStack.removeLast();
        }
    }

    /** The state before the last change, or null when there is none. */
    public T undo() {
        return undoStack.isEmpty() ? null : undoStack.pop();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
    }
}
