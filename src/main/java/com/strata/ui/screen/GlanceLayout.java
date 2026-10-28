package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.TextFit;

import java.util.List;

/** THE AT-A-GLANCE COLUMNS' ROOM: how many columns a list can take, and how wide each is. */
public final class GlanceLayout {

    private GlanceLayout() {
    }

    /** The most columns any list shows. */
    public static final int MAX = 6;
    /** The room a column should have for a new one to be added. */
    public static final int ADD_MIN_W = 70;
    /** The narrowest a column may be drawn: under it the extra columns are not shown. */
    public static final int SHOW_MIN_W = 40;
    /** The room at a row's left that an item or block row's name keeps clear of the columns. */
    public static final int NAME_ROOM = 220;

    /** The width the columns have on a list frame, to the rows' right edge, after nameRoom. */
    public static int room(Frame frame, int nameRoom) {
        Box list = new Box(frame.contentLeft(), 0, frame.contentWidth(), 1);
        int rowRight = ListLayout.rowLeft(list) + ListLayout.rowWidth(list);
        return Math.max(0, rowRight - frame.contentLeft() - nameRoom);
    }

    /** How many columns the user may have in room: one more is added only while this exceeds the count. */
    public static int maxAdd(int room) {
        return Math.max(0, Math.min(MAX, room / ADD_MIN_W));
    }

    /** How many of count chosen columns are shown in room. */
    public static int visible(int room, int count) {
        return Math.max(0, Math.min(Math.min(count, MAX), room / SHOW_MIN_W));
    }

    /**
     * One column's width: as wide as the widest heading and 10, within an equal share of room; 0 when that is
     * under SHOW_MIN_W (the columns are hidden).
     */
    public static int columnWidth(int room, TextFit.Measure measure, List<String> headings) {
        if (headings.isEmpty()) {
            return 0;
        }
        int widest = 0;
        for (String h : headings) {
            widest = Math.max(widest, measure.width(h));
        }
        int w = Math.min(widest + 10, room / headings.size());
        return w >= SHOW_MIN_W ? w : 0;
    }

    /** The room an item or block list's columns have. */
    public static int listRoom(Frame frame) {
        return room(frame, NAME_ROOM);
    }
}
