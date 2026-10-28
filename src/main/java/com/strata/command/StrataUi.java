package com.strata.command;

/** Server-safe bridge to the client UI. */
public final class StrataUi {

    private static volatile Runnable openRootAction;

    private StrataUi() {}

    public static void setOpenRoot(Runnable action) {
        openRootAction = action;
    }

    public static void openRoot() {
        Runnable action = openRootAction;
        if (action != null) {
            action.run();
        }
    }

    public static boolean isAvailable() {
        return openRootAction != null;
    }

    /**
     * Whether the editor key opens Strata with no screen open: outside a world, in the player's own world
     * (single player, or the host of an open world), or for an operator of a server.
     *
     * @param noWorld no world is open
     * @param host this game runs the world (single player)
     * @param operator the player has operator level 1 or more
     */
    public static boolean keyAllowed(boolean noWorld, boolean host, boolean operator) {
        return noWorld || host || operator;
    }
}
