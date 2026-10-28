package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Layout;

import java.util.List;

/** ONE LIST ROW, LAID OUT: what BrowseList draws in a row. */
public final class ListRowLayout {

    private ListRowLayout() {
    }

    /** Width of the inline chance cell, and its margin from the row's right edge. */
    public static final int CHANCE_W = 56;
    public static final int CHANCE_GAP = 4;
    /** Each depth of a row moves it this far right. */
    public static final int INDENT = 10;
    /** A row this short shows no subtitle. */
    public static final int SUBTITLE_MIN_H = 16;
    /** A subtitle starts this far under the name: a 9 px line and a 1 px gap. */
    public static final int LINE_STEP = 10;

    /**
     * What one row shows.
     *
     * @param columns the right-side column values, or none
     * @param columnW each column's width; 0 hides the columns
     * @param chance the chance cell's text ("100%"), or null for a row with none
     */
    public record Row(int indent, String label, String subtitle, List<String> columns, int columnW, String chance) {
    }

    /** The name's top in a row of this height. */
    public static int labelY(int top, int height) {
        return top + (height >= 16 ? 2 : 1);
    }

    /** The name's top in a row that also shows a subtitle. */
    public static int labelYWithSubtitle(int top, int height) {
        return top + (height - (LINE_STEP + 9)) / 2;
    }

    /** The subtitle's top in a row of this height: one line under the name. */
    public static int subtitleY(int top, int height) {
        return labelYWithSubtitle(top, height) + LINE_STEP;
    }

    /** The name's left edge. */
    public static int textX(int left, int indent) {
        return left + 2 + indent * INDENT;
    }

    /** The chance cell's box in a row. */
    public static Box chanceBox(int top, int left, int width, int height) {
        return new Box(left + width - CHANCE_W - CHANCE_GAP, top + Math.max(0, (height - 16) / 2), CHANCE_W,
                Math.max(10, Math.min(16, height - 6)));
    }

    /** The room the name and the subtitle have: to the columns, and to the chance cell. */
    public static int textRoom(int top, int left, int width, int height, Row row) {
        int right = left + width - (row.columnW() > 0 ? row.columnW() * row.columns().size() : 0) - 4;
        if (row.chance() != null) {
            right = Math.min(right, chanceBox(top, left, width, height).x() - 4);
        }
        return Math.max(0, right - textX(left, row.indent()));
    }

    /** The row's elements, in the given layout (which supplies the font). */
    public static void add(Layout layout, int top, int left, int width, int height, Row row) {
        int x = textX(left, row.indent());
        int room = textRoom(top, left, width, height, row);
        boolean twoLines = row.subtitle() != null && !row.subtitle().isEmpty() && height >= SUBTITLE_MIN_H;
        layout.add(layout.text("label", x, twoLines ? labelYWithSubtitle(top, height) : labelY(top, height),
                row.label(), room));
        if (twoLines) {
            layout.add(layout.text("subtitle", x, subtitleY(top, height), row.subtitle(), room));
        }
        if (row.chance() != null) {
            layout.add(Layout.control("chance", Layout.Kind.FIELD, chanceBox(top, left, width, height), ""));
            layout.add(layout.centred("chanceText", left + width - CHANCE_W / 2 - CHANCE_GAP, chanceBox(top, left,
                    width, height).y() + 4, row.chance(), CHANCE_W - 4));
        }
        for (int i = 0; i < row.columns().size() && row.columnW() > 0; i++) {
            int cx = left + width - (row.columns().size() - i) * row.columnW() + row.columnW() / 2;
            layout.add(layout.fitted("column" + i, cx, labelY(top, height), row.columns().get(i), row.columnW() - 4));
        }
    }
}
