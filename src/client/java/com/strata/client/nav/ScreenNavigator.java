package com.strata.client.nav;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Navigation stack for Strata config screens. */
public class ScreenNavigator {

    private static final ScreenNavigator INSTANCE = new ScreenNavigator();

    private final NavHistory<Screen> history = new NavHistory<>();

    private ScreenNavigator() {}

    public static ScreenNavigator get() {
        return INSTANCE;
    }

    public void navigateTo(Screen screen) {
        Screen current = Minecraft.getInstance().screen;
        // Its title says where it was opened from (Crumbed).
        if (screen instanceof Crumbed opened && current instanceof Crumbed opener && opened != opener) {
            opened.openedFrom(opener.crumbPath());
        }
        history.push(current);
        Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Opens a screen from the top bar (Go to, Options, Profiles, Scaling, Help): Back still returns here, but
     * its title starts a path of its own, not one under the screen it was opened over.
     */
    public void jumpTo(Screen screen) {
        history.push(Minecraft.getInstance().screen);
        Minecraft.getInstance().setScreen(screen);
    }

    /** Closes the editor: back to the screen that opened it (the Mods list), or to the game. */
    public void exit() {
        Screen oldest = history.clearToOldest().orElse(null);
        Minecraft.getInstance().setScreen(oldest instanceof com.strata.client.screen.StrataScreen ? null : oldest);
    }

    /** The screens Back returns through, newest first. */
    public java.util.List<Screen> history() {
        return history.entries().stream().filter(java.util.Objects::nonNull).toList();
    }

    public void back() {
        Minecraft.getInstance().setScreen(history.pop().orElse(null));
    }

    /** Opens the editor over the current screen, as a new session. */
    public void openRoot(Screen rootScreen) {
        Minecraft.getInstance().setScreen(startAt(Minecraft.getInstance().screen, rootScreen));
    }

    /**
     * Starts a new session with {@code caller} as the first Back target, and returns {@code rootScreen} for
     * the caller to show.
     */
    public Screen startAt(Screen caller, Screen rootScreen) {
        history.restart(caller);
        return rootScreen;
    }

    /** Shows another view in the SAME place as the current screen, such as a Mobs tab. */
    public void swapTo(Screen view) {
        Minecraft.getInstance().setScreen(view);
    }
}
