package com.strata.ui;

/** The editor's keys. */
public final class Keys {

    private Keys() {
    }

    public static final int ENTER = 257;
    public static final int KEYPAD_ENTER = 335;
    public static final int Z = 90;
    public static final int ESCAPE = 256;
    public static final int DELETE = 261;
    public static final int BACKSPACE = 259;

    /**
     * The button ids an action presses, first found first: Enter saves a form, and on a value
     * sheet, which has no Save, it sets the typed value.
     */
    public static java.util.List<String> buttonsFor(String action) {
        if (action == null) {
            return java.util.List.of();
        }
        return "save".equals(action) ? java.util.List.of("save", "set") : java.util.List.of(action);
    }

    /**
     * DOUBLE-CLICK A ROW TO EDIT IT: the second click presses the screen's button with this id, as a key
     * does.
     */
    public static final String DOUBLE_CLICK = "edit";
    /** Two clicks on one row within this many milliseconds are a double-click. */
    public static final long DOUBLE_CLICK_MS = 400;

    /**
     * True when a click on row {@code id} at {@code now} is the second of a double-click: the last click was
     * on the same row, at most {@link #DOUBLE_CLICK_MS} before.
     */
    public static boolean isDoubleClick(String lastId, long lastAt, String id, long now) {
        return lastId != null && lastId.equals(id) && now - lastAt >= 0 && now - lastAt <= DOUBLE_CLICK_MS;
    }

    /** The id of the button a key presses: "save", "undo", "remove", or null. */
    public static String action(int key, boolean ctrl, boolean shift) {
        if ((key == ENTER || key == KEYPAD_ENTER) && !ctrl) {
            return "save";
        }
        if (key == DELETE && !ctrl) {
            return "remove";
        }
        // Ctrl+Z presses Undo on every screen that has one.
        if (ctrl && key == Z) {
            return "undo";
        }
        return null;
    }
}
