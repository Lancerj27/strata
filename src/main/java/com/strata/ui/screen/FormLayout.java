package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Flow;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.Comparator;
import java.util.List;

/** THE EDIT FORM, LAID OUT: the shape Strata's one-thing editors share. */
public final class FormLayout {

    private FormLayout() {
    }

    /** The gap between two cells of a row. */
    public static final int CELL_GAP = 10;

    /**
     * One field of a row.
     *
     * @param width the widest it is; less when the content column is narrower
     * @param label the text above it, or null
     * @param texts every text the control can show (a toggle's ON and OFF, a choice's options): the widest
     *     must fit. Empty for a text field.
     */
    public record Cell(String id, Layout.Kind kind, int width, String label, List<String> texts) {

        /** A line of text on the row (a status beside a field), cut to its width. */
        public static Cell text(String id, int width, String text) {
            return new Cell(id, Layout.Kind.TEXT, width, null, List.of(text == null ? "" : text));
        }

        public static Cell field(String id, int width, String label) {
            return new Cell(id, Layout.Kind.FIELD, width, label, List.of());
        }

        public static Cell button(String id, int width, String label, String text) {
            return new Cell(id, Layout.Kind.BUTTON, width, label, List.of(text));
        }

        public static Cell toggle(String id, int width, String label, String name) {
            return new Cell(id, Layout.Kind.TOGGLE, width, label,
                    List.of(Layout.toggleText(name, true), Layout.toggleText(name, false)));
        }

        /** A dropdown: it shows "name: option". */
        public static Cell choice(String id, int width, String label, String name, List<String> options) {
            return new Cell(id, Layout.Kind.DROPDOWN, width, label,
                    options.stream().map(o -> Layout.choiceText(label == null || label.isEmpty() ? name : "", o)).toList());
        }
    }

    /**
     * One row of the form.
     *
     * @param label a label for the whole row, over its first cell, or null
     */
    public record Row(String label, List<Cell> cells) {

        public static Row of(Cell... cells) {
            return new Row(null, List.of(cells));
        }

        public static Row labelled(String label, Cell... cells) {
            return new Row(label, List.of(cells));
        }
    }

    /**
     * What a form shows.
     *
     * @param notes lines under the fields (a preview, a warning), each cut to the width; "" keeps a line
     * @param nav buttons under the fields and notes that open other screens, or none
     * @param footer1 the last line, or null
     * @param footer2 the line above it, or null
     */
    public record Input(String title, boolean showDelete, boolean saveActive, List<Row> rows,
                        List<String> notes, List<ListLayout.Action> nav, String footer1, String footer2) {

        /** A form with no notes and no nav row. */
        public Input(String title, boolean showDelete, boolean saveActive, List<Row> rows, String footer1,
                     String footer2) {
            this(title, showDelete, saveActive, rows, List.of(), List.of(), footer1, footer2);
        }
    }

    /** The step from one note line to the next: the font's line height. */
    public static final int NOTE_STEP = 9;

    public static Layout build(Frame frame, TextFit.Measure measure, Input in) {
        Layout layout = Layout.framed(frame, measure, in.title());
        int x = frame.contentLeft();
        int top = Grid.actionRowY();
        layout.add(Layout.control("undo", Layout.Kind.BUTTON, new Box(x, top, Grid.BTN_W, Grid.CTRL_H), "Undo")
                .asPinned());
        layout.add(Layout.control("save", Layout.Kind.BUTTON,
                new Box(x + Grid.BTN_2, top, Grid.BTN_W_WIDE, Grid.CTRL_H), "Save").asPinned().asMain()
                .withActive(in.saveActive()));
        if (in.showDelete()) {
            layout.add(Layout.control("delete", Layout.Kind.BUTTON,
                    new Box(x + Grid.BTN_3, top, Grid.BTN_W_WIDE, Grid.CTRL_H), "Remove").asPinned());
        }
        int viewTop = top + Grid.CTRL_H + Grid.GAP;
        layout.viewTop(viewTop);

        int rh = frame.rowH();
        int y = viewTop - Grid.LABEL_OFFSET + 2;
        for (int r = 0; r < in.rows().size(); r++) {
            Row row = in.rows().get(r);
            int[] widths = row.cells().stream().mapToInt(c -> Math.min(c.width(), frame.contentWidth())).toArray();
            int[] line = Flow.lines(x, frame.contentRight(), CELL_GAP, widths);
            if (row.label() != null) {
                // A row's label spans the row and can name data (a tier): cut to the width.
                layout.add(layout.text("rowLabel" + r, x, y + Grid.LABEL_OFFSET, row.label(), frame.contentWidth()));
            }
            int cx = x;
            for (int i = 0; i < widths.length; i++) {
                if (i > 0 && line[i] != line[i - 1]) {
                    cx = x;
                }
                Cell cell = row.cells().get(i);
                int cy = y + line[i] * rh;
                String text = cell.texts().stream().max(Comparator.comparingInt(measure::width)).orElse("");
                layout.add(Layout.control(cell.id(), cell.kind(), new Box(cx, cy, widths[i], Grid.CTRL_H), text));
                if (cell.label() != null) {
                    layout.add(layout.text("label." + cell.id(), cx, cy + Grid.LABEL_OFFSET, cell.label(),
                            Integer.MAX_VALUE));
                }
                cx += widths[i] + CELL_GAP;
            }
            int lines = widths.length == 0 ? 1 : line[line.length - 1] + 1;
            y += rh * lines;
        }

        // Under the last row: the notes, then the nav row.
        int below = y - rh + Grid.CTRL_H + Grid.GAP + 2;
        for (int i = 0; i < in.notes().size(); i++) {
            layout.add(layout.text("note" + i, x, below, in.notes().get(i), frame.contentWidth()));
            below += NOTE_STEP;
        }
        if (!in.nav().isEmpty()) {
            int navY = below + (in.notes().isEmpty() ? Grid.NAV_GAP - Grid.GAP - 2 : Grid.GAP);
            Flow.Placed nav = Flow.row(x, frame.contentRight(), navY, Grid.CTRL_H, Grid.GAP,
                    in.nav().stream().mapToInt(ListLayout.Action::width).toArray());
            for (int i = 0; i < in.nav().size(); i++) {
                ListLayout.Action a = in.nav().get(i);
                Layout.Element e = Layout.control(a.id(), a.kind(), nav.boxes().get(i), a.label())
                        .withActive(a.active());
                layout.add(a.main() ? e.asMain() : e);
            }
        }

        int reserve = 8;
        if (in.footer2() != null) {
            layout.add(layout.text("footer2", x, frame.footerY2(), in.footer2(), frame.contentWidth()).asPinned());
            reserve = frame.height() - frame.footerY2() + 2;
        }
        if (in.footer1() != null) {
            layout.add(layout.text("footer1", x, frame.footerY(), in.footer1(), frame.contentWidth()).asPinned());
            reserve = Math.max(reserve, frame.height() - frame.footerY() + 2);
        }
        return layout.scrolls(true).footerReserve(reserve);
    }
}
