package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** THE CONFIRM DIALOG, LAID OUT: ConfirmDialog. */
public final class ConfirmLayout {

    private ConfirmLayout() {
    }

    public static final int LINE_STEP = 11;
    public static final int TOP_MARGIN = 8;
    public static final int BUTTON_W = 100;
    public static final int BUTTON_H = 20;
    /** The gap between the two buttons, and from the text to them. */
    public static final int GAP = 10;
    public static final int SIDE_ROOM = 40;

    /** The verbs a confirm button may take from its message's first word. */
    private static final java.util.Set<String> VERBS = java.util.Set.of("Delete", "Remove", "Restore", "Reset",
            "Replace", "Deny", "Activate", "Import", "Assign", "Clear", "Save", "Rename", "Close", "Load", "Restart");

    /**
     * The confirm button's word: the message's first word when it is an action verb ("Delete the goal
     * set...?" gives "Delete"), else "Confirm".
     */
    public static String actionWord(String message) {
        String first = message == null ? "" : message.strip().split("[\\s,.?!']", 2)[0];
        return VERBS.contains(first) ? first : "Confirm";
    }

    public static Layout build(Frame frame, TextFit.Measure m, String message) {
        return build(frame, m, message, actionWord(message));
    }

    /** @param confirmWord the confirm button's word */
    public static Layout build(Frame frame, TextFit.Measure m, String message, String confirmWord) {
        Layout layout = new Layout(frame, m);
        int width = Math.max(100, frame.width() - SIDE_ROOM);
        List<String> lines = new ArrayList<>();
        for (String part : message.split("\n", -1)) {
            List<String> wrapped = TextFit.wrap(part, width, Integer.MAX_VALUE, m);
            lines.addAll(wrapped.isEmpty() ? List.of("") : wrapped);
        }
        int centerX = frame.width() / 2;
        int centerY = frame.height() / 2;
        // The most lines that fit above the buttons, with the buttons on screen.
        int maxLines = Math.max(1, (frame.height() - TOP_MARGIN - GAP - BUTTON_H - 4) / LINE_STEP);
        if (lines.size() > maxLines) {
            List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
            // The last line kept says there is more: "..." after it, or cut to make room.
            kept.set(maxLines - 1, TextFit.clip(kept.get(maxLines - 1) + " " + TextFit.ELLIPSIS, width, m));
            lines = kept;
        }
        int top = Math.max(TOP_MARGIN, centerY - 4 - lines.size() * LINE_STEP);
        for (int i = 0; i < lines.size(); i++) {
            layout.add(layout.centred("line" + i, centerX, top + i * LINE_STEP, lines.get(i), width));
        }
        int by = Math.max(centerY + GAP, top + lines.size() * LINE_STEP + GAP);
        layout.add(Layout.control("confirm", Layout.Kind.BUTTON,
                new Box(centerX - BUTTON_W - GAP / 2, by, BUTTON_W, BUTTON_H), confirmWord).asMain());
        layout.add(Layout.control("cancel", Layout.Kind.BUTTON,
                new Box(centerX + GAP / 2, by, BUTTON_W, BUTTON_H), "Cancel").asMain());
        return layout;
    }
}
