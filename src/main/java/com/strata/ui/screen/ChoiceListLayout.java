package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** A DROPDOWN'S OPTIONS, LAID OUT. */
public final class ChoiceListLayout {

    private ChoiceListLayout() {
    }

    /** One option's row height. */
    public static final int ROW_H = 13;
    /** The room round the rows inside the panel. */
    public static final int PAD = 2;
    /** The room kept between the panel and the screen's edge. */
    public static final int EDGE = 4;

    /** How many rows the panel shows on this screen for this many options. */
    public static int rows(Frame frame, int options) {
        int room = Math.max(1, (frame.height() - 2 * EDGE - 2 * PAD) / ROW_H);
        return Math.min(options, room);
    }

    /** The first option shown, kept in range. */
    public static int clampFirst(Frame frame, int options, int first) {
        return Math.max(0, Math.min(first, options - rows(frame, options)));
    }

    /**
     * @param anchor the dropdown button's box, where it is drawn now
     * @param words the options, as they are shown
     * @param first the first option shown (a long list scrolls)
     */
    public static Layout build(Frame frame, TextFit.Measure m, Box anchor, List<String> words, int first) {
        Layout layout = new Layout(frame, m);
        int widest = 0;
        for (String w : words) {
            widest = Math.max(widest, m.width(w));
        }
        int maxW = Math.max(40, frame.width() - 2 * EDGE);
        int w = Math.min(maxW, Math.max(anchor.w(), widest + 2 * PAD + 8));
        int rows = rows(frame, words.size());
        int top = clampFirst(frame, words.size(), first);
        int h = rows * ROW_H + 2 * PAD;
        int x = Math.max(EDGE, Math.min(anchor.x(), frame.width() - EDGE - w));
        int below = anchor.bottom() + 1;
        int above = anchor.y() - 1 - h;
        int y = below + h <= frame.height() - EDGE ? below
                : above >= EDGE ? above
                : Math.max(EDGE, frame.height() - EDGE - h);
        layout.add(Layout.control("panel", Layout.Kind.LIST, new Box(x, y, w, h), ""));
        for (int i = 0; i < rows; i++) {
            int index = top + i;
            layout.add(layout.note("option" + index, x + PAD + 3, y + PAD + i * ROW_H + 3, words.get(index),
                    w - 2 * PAD - 6));
        }
        return layout;
    }

    /**
     * Where the screen under the list sees the mouse: off the screen while the options are
     * open, so nothing under them lights up or shows its tooltip over them.
     *
     * @return {x, y}
     */
    public static int[] mouseForScreen(boolean open, int x, int y) {
        return open ? new int[]{-1, -1} : new int[]{x, y};
    }

    /** The option under a point, or -1. */
    public static int optionAt(Layout layout, int x, int y) {
        Box panel = layout.boxOf("panel");
        if (x < panel.x() || x >= panel.right()) {
            return -1;
        }
        for (Layout.Element e : layout.elements()) {
            if (e.id().startsWith("option")) {
                int top = e.box().y() - 3;
                if (y >= top && y < top + ROW_H) {
                    return Integer.parseInt(e.id().substring("option".length()));
                }
            }
        }
        return -1;
    }

    /** The box of an option's row, for the highlight. */
    public static Box rowBox(Layout layout, int option) {
        Layout.Element e = layout.get("option" + option);
        if (e == null) {
            return null;
        }
        Box panel = layout.boxOf("panel");
        return new Box(panel.x() + PAD, e.box().y() - 3, panel.w() - 2 * PAD, ROW_H);
    }
}
