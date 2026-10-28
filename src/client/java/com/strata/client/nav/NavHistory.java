package com.strata.client.nav;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * The back-stack semantics, with no Minecraft in them.
 *
 * @param <T> the screen type, so the gate can drive this with plain strings
 */
public final class NavHistory<T> {

    private final Deque<T> history = new ArrayDeque<>();

    /** Records the screen being left, before showing a new one. */
    public void push(T current) {
        if (current != null) {
            history.push(current);
        }
    }

    /** The screen to return to, or empty when there is nothing to go back to. */
    public Optional<T> pop() {
        return history.isEmpty() ? Optional.empty() : Optional.of(history.pop());
    }

    /** The screens under the current one, newest first. */
    public java.util.List<T> entries() {
        return java.util.List.copyOf(history);
    }

    public boolean canGoBack() {
        return !history.isEmpty();
    }

    public int depth() {
        return history.size();
    }

    /** Empties the history and returns its oldest screen, or empty when there was none. */
    public Optional<T> clearToOldest() {
        Optional<T> oldest = history.isEmpty() ? Optional.empty() : Optional.of(history.peekLast());
        history.clear();
        return oldest;
    }

    /** Starts a new editor session. */
    public void restart(T caller) {
        history.clear();
        push(caller);
    }
}
