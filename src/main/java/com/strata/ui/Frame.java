package com.strata.ui;

/**
 * The frame every Strata screen shares, for one screen size: the content column, the header (Back, the title,
 * the failure banner), the view a form scrolls in, and the footer lines.
 *
 * @param width the screen width in GUI pixels
 * @param height the screen height in GUI pixels
 */
public record Frame(int width, int height) {

    public int contentWidth() {
        return Grid.contentWidthFor(width, Grid.GUTTER, Grid.MAX_CONTENT_W);
    }

    public int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    public int contentRight() {
        return contentLeft() + contentWidth();
    }

    /** The field row height at this screen height. */
    public int rowH() {
        return Grid.rowH(height);
    }

    /** The Back button. */
    public Box back() {
        return new Box(contentLeft(), 4, 60, Grid.CTRL_H);
    }

    /** The Help button's width: one "?". */
    public static final int HELP_W = 20;

    /** The Exit button's width: its word. */
    public static final int EXIT_W = 30;

    /** The Help button, at the top right of every screen. */
    public Box help() {
        return new Box(contentRight() - HELP_W, 4, HELP_W, Grid.CTRL_H);
    }

    /** The Exit button, left of Help. */
    public Box exit() {
        return new Box(help().x() - Grid.GAP - EXIT_W, 4, EXIT_W, Grid.CTRL_H);
    }

    /** The Options button's width: its word. */
    public static final int OPTIONS_W = 52;

    /** The Options button, left of Exit at the top right of every screen. */
    public Box options() {
        return new Box(exit().x() - Grid.GAP - OPTIONS_W, 4, OPTIONS_W, Grid.CTRL_H);
    }

    /** The Scaling button's width: its word. */
    public static final int SCALING_W = 52;

    /** The Scaling button, left of Options at the top right of every screen. */
    public Box scaling() {
        return new Box(options().x() - Grid.GAP - SCALING_W, 4, SCALING_W, Grid.CTRL_H);
    }

    /** The Profiles button's width: its word. */
    public static final int PROFILES_W = 52;

    /** The Profiles button, left of Scaling at the top right of every screen. */
    public Box profiles() {
        return new Box(scaling().x() - Grid.GAP - PROFILES_W, 4, PROFILES_W, Grid.CTRL_H);
    }

    /** The Go to menu's width. */
    public static final int GOTO_W = 44;

    /** Go to, left of Profiles: one menu of the main lists, on every screen at every width. */
    public Box goTo() {
        return new Box(profiles().x() - Grid.GAP - GOTO_W, 4, GOTO_W, Grid.CTRL_H);
    }

    /** The room the title has: from its start to just left of Go to. */
    public int titleRoom() {
        return goTo().x() - Grid.GAP - titleX();
    }

    /** Where the title line starts: right of the Back button. */
    public int titleX() {
        return contentLeft() + 66;
    }

    public int titleY() {
        return Grid.HEADER_Y + 5;
    }

    /** The failed-write banner, under the title. */
    public int failureY() {
        return Grid.HEADER_Y + 17;
    }

    /** The top of the view a form scrolls in: under the header. */
    public int viewTop() {
        return Grid.CONTENT_Y;
    }

    /** The canonical bottom status line. */
    public int footerY() {
        return height - 30;
    }

    /** The second bottom line, above {@link #footerY}. */
    public int footerY2() {
        return height - 42;
    }

    /** The whole screen, as a box. */
    public Box screen() {
        return new Box(0, 0, width, height);
    }
}
