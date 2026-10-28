package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** THE SPAWN CONDITIONS, LAID OUT: SpawnRulesScreen. */
public final class SpawnRulesLayout {

    private SpawnRulesLayout() {
    }

    /** The bar's first line: this far above the screen's bottom. */
    public static final int BAR_FROM_BOTTOM = 56;
    /** The step from the bar's first line to its second. */
    public static final int BAR_STEP = 26;
    /** Room above the bar for the context line. */
    public static final int CONTEXT_ROOM = 22;
    public static final int TYPE_W = 140;
    public static final int VALUE_X = 150;
    public static final int VALUE_W = Grid.FIELD_W - 30;
    public static final int REMOVE_X = Grid.FIELD_W + 126;
    public static final int ARROW_X = Grid.FIELD_W + 150;
    public static final int ARROW_W = 18;

    /**
     * The editor's columns for a content width: the constants above on a column no wider than they need
     * (ARROW_X + ARROW_W), and a wider column's extra shared a third to the type and two thirds to the value,
     * so the editor ends at the column's right edge.
     */
    public record Geometry(int typeW, int valueX, int valueW, int removeX, int arrowX) {
    }

    public static Geometry geometry(int contentWidth) {
        int extra = Math.max(0, contentWidth - (ARROW_X + ARROW_W));
        int typeW = TYPE_W + extra / 3;
        int valueX = VALUE_X + extra / 3;
        int valueW = VALUE_W + extra - extra / 3;
        int shift = extra;
        return new Geometry(typeW, valueX, valueW, REMOVE_X + shift, ARROW_X + shift);
    }

    /**
     * One condition row's value control.
     *
     * @param kind FIELD for a typed value; BUTTON for a choice or a picker
     * @param texts every text it can show (a choice's options, a picker's label); empty for a field
     */
    public record Value(Layout.Kind kind, List<String> texts) {
    }

    /**
     * @param types every text the type choice can show
     * @param shown the value controls of the rows shown, in order
     * @param overflow more rows than fit: the arrows show
     * @param context the line above the bar
     * @param empty the text when there are no rows, or null
     */
    public record Input(String title, List<String> types, List<Value> shown, boolean overflow, String context,
                        String empty) {
    }

    /** How many condition rows fit on a screen of this size. */
    public static int visibleRows(Frame frame) {
        int usable = barY(frame) - rowsTop() - CONTEXT_ROOM;
        return Math.max(1, usable / frame.rowH());
    }

    /** The column headings' line above the first row. */
    private static final int HEADING_ROOM = 12;

    private static int rowsTop() {
        return Grid.CONTENT_Y + 4 + HEADING_ROOM;
    }

    private static int barY(Frame frame) {
        return frame.height() - BAR_FROM_BOTTOM;
    }

    public static Layout build(Frame frame, TextFit.Measure measure, Input in) {
        Layout layout = Layout.framed(frame, measure, in.title());
        int x = frame.contentLeft();
        int rh = frame.rowH();
        Geometry g = geometry(frame.contentWidth());
        String widestType = in.types().stream().max(java.util.Comparator.comparingInt(measure::width)).orElse("");
        if (!in.shown().isEmpty()) {
            // THE VALUE COLUMN'S TITLE: its text boxes have no room for one beside them.
            layout.add(layout.text("column.type", x, Grid.CONTENT_Y + 4, "Rule", g.typeW()));
            layout.add(layout.text("column.value", x + g.valueX(), Grid.CONTENT_Y + 4, "Value", g.valueW()));
        }
        for (int i = 0; i < in.shown().size(); i++) {
            int y = rowsTop() + i * rh;
            Value v = in.shown().get(i);
            layout.add(Layout.control("type" + i, Layout.Kind.BUTTON, new Box(x, y, g.typeW(), Grid.CTRL_H), widestType));
            String text = v.texts().stream().max(java.util.Comparator.comparingInt(measure::width))
                    .map(t -> TextFit.clip(t, g.valueW() - Layout.LABEL_MARGIN, measure)).orElse("");
            layout.add(Layout.control("value" + i, v.kind(), new Box(x + g.valueX(), y, g.valueW(), Grid.CTRL_H), text));
            layout.add(Layout.control("remove" + i, Layout.Kind.BUTTON,
                    new Box(x + g.removeX(), y, ARROW_W, Grid.CTRL_H), "×"));
        }
        int bar = barY(frame);
        layout.add(Layout.control("add", Layout.Kind.BUTTON, new Box(x, bar, 130, Grid.CTRL_H), "+ Add rule")
                .asMain());
        int by = bar + BAR_STEP;
        layout.add(Layout.control("undo", Layout.Kind.BUTTON, new Box(x, by, Grid.BTN_W, Grid.CTRL_H), "Undo"));
        layout.add(Layout.control("save", Layout.Kind.BUTTON,
                new Box(x + Grid.BTN_2, by, Grid.BTN_W_WIDE, Grid.CTRL_H), "Save").asMain());
        if (in.overflow()) {
            layout.add(Layout.control("up", Layout.Kind.BUTTON, new Box(x + g.arrowX(), bar, ARROW_W, Grid.CTRL_H),
                    "▲"));
            layout.add(Layout.control("down", Layout.Kind.BUTTON, new Box(x + g.arrowX(), by, ARROW_W, Grid.CTRL_H),
                    "▼"));
        }
        // ABOVE THE BAR: at footerY it lay on the Undo row.
        layout.add(layout.text("context", x, bar - 12, in.context(), frame.contentWidth()));
        if (in.empty() != null) {
            layout.add(layout.text("empty", x, Grid.CONTENT_Y + 20, in.empty(), frame.contentWidth()));
        }
        return layout;
    }
}
