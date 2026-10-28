package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Flow;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.Comparator;
import java.util.List;

/** THE LIST SCREEN, LAID OUT: the shape most Strata lists share. */
public final class ListLayout {

    private ListLayout() {
    }

    /** The bottom bar's last line: this far above the screen's bottom. */
    public static final int BAR_FROM_BOTTOM = 50;
    /** The last hint line sits this far above the bar's top. */
    public static final int HINT_ABOVE_BAR = 14;
    /** The line step of hint lines: the font's line height. */
    public static final int LINE_STEP = 9;
    /** The gap between the list and the rows around it. */
    public static final int LIST_GAP = 6;
    /** With no bar and no footer, the list stops this far above the screen's bottom. */
    public static final int BOTTOM_MARGIN = 4;
    /** The room a choice keeps around its text: its text is cut to its width less this. */
    public static final int CHOICE_MARGIN = Layout.LABEL_MARGIN;
    /** A list row is this much narrower than the list (the scroll bar's room). */
    public static final int ROW_INSET = 12;

    /**
     * A control of a row or the bar: a button, or a text field (a search, a value).
     *
     * @param fill it takes the rest of its line, at least width wide
     * @param active usable in the state laid out
     * @param main a main action: shown and usable (on a list, usually Add)
     */
    public record Action(String id, Layout.Kind kind, String label, int width, boolean fill, boolean active,
                         boolean main) {

        /** A button of a set width: its label changes (a count, a state), or it is an icon. */
        public static Action of(String id, String label, int width) {
            return new Action(id, Layout.Kind.BUTTON, label, width, false, true, false);
        }

        /** A button of fixed words, as wide as they need ({@link Grid#buttonW}): one label, one width. */
        public static Action of(String id, String label) {
            return of(id, label, Grid.buttonW(label));
        }

        /** A text field with the title its id names ("search" is "Search"): see {@link #field(String, int, String)}. */
        public static Action field(String id, int width) {
            return field(id, width, titleOf(id));
        }

        /** A text field with its title drawn BESIDE it, to its left. */
        public static Action field(String id, int width, String title) {
            return new Action(id, Layout.Kind.FIELD, title, width, false, true, false);
        }

        /** The default title of a field by its id: "search" is Search, "value" Value, "name" Name. */
        static String titleOf(String id) {
            return id.isEmpty() ? "" : Character.toUpperCase(id.charAt(0)) + id.substring(1);
        }

        /** Whether this is a field with a title to draw beside it. */
        boolean titled() {
            return kind == Layout.Kind.FIELD && !label.isEmpty();
        }

        /** A dropdown: the widest of its texts must fit. */
        public static Action choice(String id, String name, List<String> options, int width, TextFit.Measure m) {
            String widest = options.stream().map(o -> Layout.choiceText(name, o))
                    .max(Comparator.comparingInt(m::width)).orElse("");
            return new Action(id, Layout.Kind.BUTTON, widest, width, false, true, false);
        }

        /**
         * A choice whose options are data (a mod's id): it widens to its widest text, from width up to max,
         * and a text wider than that is cut.
         */
        public static Action dataChoice(String id, String name, List<String> options, int width, int max,
                                        TextFit.Measure m) {
            String widest = options.stream().map(o -> Layout.choiceText(name, o))
                    .max(Comparator.comparingInt(m::width)).orElse("");
            int w = Math.max(width, Math.min(max, m.width(widest) + CHOICE_MARGIN));
            return new Action(id, Layout.Kind.BUTTON, TextFit.clip(widest, w - CHOICE_MARGIN, m), w, false, true, false);
        }

        /** This control takes the rest of its line. */
        public Action filling() {
            return new Action(id, kind, label, width, true, active, main);
        }

        public Action asMain() {
            return new Action(id, kind, label, width, fill, active, true);
        }

        public Action withActive(boolean usable) {
            return new Action(id, kind, label, width, fill, usable, main);
        }
    }

    /**
     * What a list screen shows.
     *
     * @param top the controls above the list, or none
     * @param underTop a row of labelled fields beneath the top row, or none
     * @param addRow the fields of an add row above the bar, or none
     * @param bar the bottom bar, or none: the list then runs to the footer
     * @param hints the lines just above the bar, top first; "" keeps a line's room
     * @param footer the line under the bar, or null
     * @param empty the text shown in the list when it has no rows, or null
     * @param headings the headings over the rows' right-side columns, or none
     * @param columnW each column's width; 0 hides the columns and their headings
     * @param undo the bar starts with Undo: a screen that writes at once
     */
    public record Input(String title, List<Action> top, List<FormLayout.Cell> underTop,
                        List<FormLayout.Cell> addRow, List<Action> bar,
                        List<String> hints, String footer, String empty, List<String> headings, int columnW,
                        boolean undo) {

        /** The shape every list screen used before underTop existed. */
        public Input(String title, List<Action> top, List<FormLayout.Cell> addRow, List<Action> bar,
                     List<String> hints, String footer, String empty, List<String> headings, int columnW,
                     boolean undo) {
            this(title, top, List.of(), addRow, bar, hints, footer, empty, headings, columnW, undo);
        }

        public Input(String title, List<Action> top, List<FormLayout.Cell> addRow, List<Action> bar,
                     List<String> hints, String footer, String empty, List<String> headings, int columnW) {
            this(title, top, List.of(), addRow, bar, hints, footer, empty, headings, columnW, false);
        }

        /** A list with no columns. */
        public Input(String title, List<Action> top, List<FormLayout.Cell> addRow, List<Action> bar,
                     List<String> hints, String footer, String empty) {
            this(title, top, List.of(), addRow, bar, hints, footer, empty, List.of(), 0, false);
        }

        /** The same list with Undo first in its bar. */
        public Input withUndo() {
            return new Input(title, top, underTop, addRow, bar, hints, footer, empty, headings, columnW, true);
        }

        /** The same list with a row of labelled fields beneath the top row. */
        public Input withUnderTop(List<FormLayout.Cell> cells) {
            return new Input(title, top, cells, addRow, bar, hints, footer, empty, headings, columnW, undo);
        }

        /**
         * The same list with the title of its rows' inline chance cell over it: "Chance %" over the cell at
         * the rows' right edge.
         */
        public Input withChanceHeading() {
            return new Input(title, top, underTop, addRow, bar, hints, footer, empty, List.of("Chance %"),
                    ListRowLayout.CHANCE_W + 2 * ListRowLayout.CHANCE_GAP, undo);
        }

        /** The bar as built: Undo first when asked for. */
        List<Action> barActions() {
            if (!undo) {
                return bar;
            }
            List<Action> out = new java.util.ArrayList<>();
            out.add(UNDO);
            out.addAll(bar);
            return out;
        }
    }

    /** A list screen's Undo: it puts the session's newest change back. */
    public static final Action UNDO = Action.of("undo", "Undo");

    /** The room a line of column headings takes above the list. */
    public static final int HEADING_ROOM = 12;

    /** A list screen that writes at once: Undo first in its bar. */
    public static Layout buildWithUndo(Frame frame, TextFit.Measure measure, Input in) {
        return build(frame, measure, in.withUndo());
    }

    /**
     * THE TOOLS AT THE TOP ROW'S RIGHT END: the top-row buttons named, which come last in the row, move to
     * its right edge when they sit on the same line as the row's first control.
     */
    public static Layout rightAlign(Layout layout, Frame frame, List<String> ids) {
        if (ids.isEmpty()) {
            return layout;
        }
        Layout.Element first = layout.get("search");
        Layout.Element last = layout.get(ids.get(ids.size() - 1));
        if (last == null) {
            return layout;
        }
        int y = first != null ? first.box().y() : last.box().y();
        for (String id : ids) {
            Layout.Element e = layout.get(id);
            if (e == null || e.box().y() != y) {
                return layout;
            }
        }
        int shift = frame.contentRight() - last.box().right();
        if (shift <= 0) {
            return layout;
        }
        for (String id : ids) {
            Layout.Element e = layout.get(id);
            Box b = e.box();
            layout.replace(new Layout.Element(e.id(), e.kind(), new Box(b.x() + shift, b.y(), b.w(), b.h()), e.text(),
                    e.pinned(), e.active(), e.main(), e.hidden()));
        }
        return layout;
    }

    /** A SECOND BAR above the main one. */
    public static int placeBar(Layout layout, Frame frame, TextFit.Measure measure, List<Action> actions,
                               int bottomY) {
        if (actions.isEmpty()) {
            return bottomY;
        }
        Flow.Placed placed = add(layout, actions, Flow.bar(frame.contentLeft(), frame.contentRight(), bottomY,
                Grid.CTRL_H, Grid.GAP, widths(actions, frame.contentWidth(), measure)));
        return placed.top();
    }

    public static Layout build(Frame frame, TextFit.Measure measure, Input in) {
        Layout layout = Layout.framed(frame, measure, in.title());
        int x = frame.contentLeft();
        int listTop = Grid.CONTENT_Y + 4;
        if (!in.top().isEmpty()) {
            Flow.Placed row = add(layout, in.top(), Flow.row(x, frame.contentRight(), Grid.actionRowY(),
                    Grid.CTRL_H, Grid.GAP, widths(in.top(), frame.contentWidth(), measure)));
            listTop = row.bottom() + LIST_GAP;
        }
        // THE ROW BENEATH THE TOP ROW.
        if (!in.underTop().isEmpty()) {
            // ONE LINE WHERE IT CAN.
            int count = in.underTop().size();
            int share = (frame.contentWidth() - Grid.GAP * (count - 1)) / Math.max(1, count);
            int[] cellW = in.underTop().stream().mapToInt(c -> Math.min(c.width(), share)).toArray();
            int[] cellLine = Flow.lines(x, frame.contentRight(), Grid.GAP, cellW);
            int lines = cellLine.length == 0 ? 0 : cellLine[cellLine.length - 1] + 1;
            // The block's height COUNTS THE LINES.
            int blockH = (lines - 1) * ADD_ROW_STEP + Grid.CTRL_H - Grid.LABEL_OFFSET;
            int labelTop = listTop;
            addRow(layout, frame, in.underTop(), labelTop + blockH, cellW);
            listTop = labelTop + blockH + LIST_GAP;
        }
        int above;
        List<Action> barActions = in.barActions();
        if (!barActions.isEmpty()) {
            Flow.Placed bar = add(layout, barActions, Flow.bar(x, frame.contentRight(),
                    frame.height() - BAR_FROM_BOTTOM, Grid.CTRL_H, Grid.GAP, widths(barActions, frame.contentWidth(), measure)));
            above = bar.top();
        } else {
            above = in.footer() != null ? frame.footerY() - BOTTOM_MARGIN : frame.height() - BOTTOM_MARGIN;
        }
        if (!in.addRow().isEmpty()) {
            above = addRow(layout, frame, in.addRow(), above - LIST_GAP);
        }
        boolean headed = !in.headings().isEmpty() && in.columnW() > 0;
        if (headed) {
            listTop += HEADING_ROOM;
        }
        int listBottom = above - LIST_GAP;
        // A hint line left empty takes no room: an empty second line left a blank row between the hint and
        // the bar.
        List<String> hints = new java.util.ArrayList<>(in.hints());
        while (!hints.isEmpty() && (hints.getLast() == null || hints.getLast().isEmpty())) {
            hints.removeLast();
        }
        if (!hints.isEmpty()) {
            int firstY = above - HINT_ABOVE_BAR - (hints.size() - 1) * LINE_STEP;
            for (int i = 0; i < hints.size(); i++) {
                layout.add(layout.text("hint" + i, x, firstY + i * LINE_STEP, hints.get(i),
                        frame.contentWidth()));
            }
            listBottom = firstY - LIST_GAP;
        }
        Box list = new Box(x, listTop, frame.contentWidth(), Math.max(22, listBottom - listTop));
        layout.add(Layout.control("list", Layout.Kind.LIST, list, ""));
        if (headed) {
            // Over the rows' columns, drawn smaller when wider than a column.
            for (int i = 0; i < in.headings().size(); i++) {
                int left = columnLeft(list, i, in.headings().size(), in.columnW());
                layout.add(layout.fitted("heading" + i, left + in.columnW() / 2, listTop - HEADING_ROOM + 2,
                        in.headings().get(i), in.columnW() - 4));
            }
        }
        if (in.empty() != null) {
            layout.add(layout.note("empty", x + 4, listTop + LIST_GAP, in.empty(), frame.contentWidth() - 8));
        }
        if (in.footer() != null) {
            layout.add(layout.text("footer", x, frame.footerY(), in.footer(), frame.contentWidth()));
        }
        return layout;
    }

    // ---------------------------------------------------------- list rows THE ROW GEOMETRY OF A LIST
    // (BrowseList, a vanilla selection list): a row is ROW_INSET narrower than the list, centred, 2 in from
    // its left.

    /** A row's width in a list of this box. */
    public static int rowWidth(Box list) {
        return list.w() - ROW_INSET;
    }

    /** A row's left edge in a list of this box. */
    public static int rowLeft(Box list) {
        return list.x() + list.w() / 2 - rowWidth(list) / 2 + 2;
    }

    /** The left edge of right-side column i of count, each columnW wide. */
    public static int columnLeft(Box list, int i, int count, int columnW) {
        return rowLeft(list) + rowWidth(list) - (count - i) * columnW;
    }

    // ---------------------------------------------------------- the rows

    /** The step of one add-row line: its control and the label above it. */
    public static final int ADD_ROW_STEP = Grid.CTRL_H - Grid.LABEL_OFFSET;

    /** Places the add row's cells, their last line ending at bottom, each with its label above it. */
    private static int addRow(Layout layout, Frame frame, List<FormLayout.Cell> cells, int bottom) {
        return addRow(layout, frame, cells, bottom,
                cells.stream().mapToInt(c -> Math.min(c.width(), frame.contentWidth())).toArray());
    }

    /** The add row with the widths already decided. */
    private static int addRow(Layout layout, Frame frame, List<FormLayout.Cell> cells, int bottom, int[] widths) {
        int x = frame.contentLeft();
        int[] line = Flow.lines(x, frame.contentRight(), Grid.GAP, widths);
        int lines = line.length == 0 ? 0 : line[line.length - 1] + 1;
        int firstY = bottom - Grid.CTRL_H - (lines - 1) * ADD_ROW_STEP;
        int cx = x;
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0 && line[i] != line[i - 1]) {
                cx = x;
            }
            FormLayout.Cell cell = cells.get(i);
            int cy = firstY + line[i] * ADD_ROW_STEP;
            String text = cell.texts().stream().max(Comparator.comparingInt(layout.measure()::width)).orElse("");
            if (cell.kind() == Layout.Kind.TEXT) {
                layout.add(layout.text(cell.id(), cx, cy + 4, text, widths[i]));
            } else {
                // CUT TO THE CELL, like every other control.
                layout.add(Layout.control(cell.id(), cell.kind(), new Box(cx, cy, widths[i], Grid.CTRL_H),
                        TextFit.clip(text, widths[i] - Layout.LABEL_MARGIN, layout.measure())));
            }
            if (cell.label() != null) {
                layout.add(layout.text("label." + cell.id(), cx, cy + Grid.LABEL_OFFSET, cell.label(),
                        Integer.MAX_VALUE));
            }
            cx += widths[i] + Grid.GAP;
        }
        return firstY + Grid.LABEL_OFFSET;
    }

    /** The room between a field's title and the field. */
    public static final int TITLE_GAP = 4;

    /** The room a titled field's title takes to its left: the words and a gap; 0 for any other action. */
    static int titleRoom(Action a, TextFit.Measure m) {
        return a.titled() ? m.width(a.label()) + TITLE_GAP : 0;
    }

    /** The actions' widths; a filling one takes what the others leave on the line. */
    private static int[] widths(List<Action> actions, int contentWidth, TextFit.Measure m) {
        int fixed = 0;
        for (Action a : actions) {
            fixed += a.fill() ? 0 : a.width() + titleRoom(a, m);
        }
        int rest = contentWidth - fixed - Grid.GAP * (actions.size() - 1);
        return actions.stream().mapToInt(a -> (a.fill() ? Math.max(a.width(), Math.min(rest, contentWidth)) : a.width())
                + titleRoom(a, m)).toArray();
    }

    private static Flow.Placed add(Layout layout, List<Action> actions, Flow.Placed placed) {
        TextFit.Measure m = layout.measure();
        for (int i = 0; i < actions.size(); i++) {
            Action a = actions.get(i);
            Box box = placed.boxes().get(i);
            if (a.titled()) {
                // The title beside the field, on its line; the field takes the rest of the box.
                int room = Math.min(titleRoom(a, m), box.w() - 1);
                layout.add(layout.text("label." + a.id(), box.x(), box.y() + (box.h() - Layout.LINE_H) / 2,
                        a.label(), Integer.MAX_VALUE));
                box = new Box(box.x() + room, box.y(), box.w() - room, box.h());
            }
            Layout.Element e = Layout.control(a.id(), a.kind(), box, a.titled() ? "" : a.label())
                    .withActive(a.active());
            layout.add(a.main() ? e.asMain() : e);
        }
        return placed;
    }

    /**
     * A remove button's text for the picked rows: the one word for one row, both words for a mix, and the
     * count for several: "Remove", "Deny", "Remove / Deny (3)".
     *
     * @param one the word for the first kind (Remove)
     * @param other the word for the second kind (Deny, Turn off)
     */
    public static String manyLabel(String one, String other, int ones, int others) {
        int n = ones + others;
        String word = ones > 0 && others > 0 ? one + " / " + other : others > 0 ? other : one;
        return n > 1 ? word + " (" + n + ")" : word;
    }
}