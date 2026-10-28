package com.strata.ui;

/** EVERY TEXT COLOUR OF THE UI, BY WHAT IT MEANS. */
public final class Palette {

    private Palette() {
    }

    /** A row, a value. */
    public static final int TEXT = 0xFFFFFF;
    /** Help and notes text, a list row's subtitle, a stat column. */
    public static final int BODY = 0xDDDDDD;
    /** A field's label. */
    public static final int LABEL = 0xAAAAAA;
    /** The hint lines over a bar. */
    public static final int HINT = 0xBBBBBB;
    /** The footer, a count, a row that is off. */
    public static final int MUTED = 0x777777;
    /** A row this list does not own: the game's, a tier's, a base mob's. */
    public static final int INHERITED = 0x999999;
    /** A value that cannot be changed. */
    public static final int LOCKED = 0x888888;
    /** The user's own value. */
    public static final int EDITED = 0xFFFF55;
    /** A group header, at every depth. */
    public static final int GROUP = 0xFFAA00;
    /** A problem, a refusal. */
    public static final int ERROR = 0xFF5555;
    /** Saved, active. */
    public static final int DONE = 0x55FF55;
    /** The game's own colour for the text in a field. */
    public static final int FIELD = 0xE0E0E0;

    // ---- fills, with alpha (ARGB) ----

    /** The inside of a box: a dropdown, a cell, the AI box. */
    public static final int PANEL = 0xFF101010;
    /** The inside of a cell that cannot be edited. */
    public static final int PANEL_LOCKED = 0xFF202020;
    /** A box's border. */
    public static final int BORDER = 0xFF6A6A6A;
    /** A box's border under the mouse. */
    public static final int BORDER_HOVER = 0xFF909090;
    /** A box's border while it is edited. */
    public static final int BORDER_ACTIVE = 0xFFFFFFFF;
    /** An open dropdown's border. */
    public static final int BORDER_MENU = 0xFFA0A0A0;
    /** A dropdown row under the mouse. */
    public static final int ROW_HOVER = 0xFF3A3A5A;
    /** A picked list row. */
    public static final int ROW_PICKED = 0x40FFFFFF;
    /** A list row being dragged. */
    public static final int ROW_DRAGGED = 0x30FFAA00;
    /** Where a dragged row lands. */
    public static final int DROP_LINE = 0xFFFFAA00;
    /** A scroll bar's track. */
    public static final int SCROLL_TRACK = 0x40FFFFFF;
    /** A scroll bar's thumb. */
    public static final int SCROLL_THUMB = 0xC0FFFFFF;
    /** Greys out a part of a screen that does not apply. */
    public static final int DIM = 0x88000000;
    /** The debug HUD's panel. */
    public static final int HUD_PANEL = 0x90000000;
}
