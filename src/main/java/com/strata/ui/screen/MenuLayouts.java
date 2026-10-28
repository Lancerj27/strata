package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Flow;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/**
 * THE MENUS AND TOOLS, LAID OUT: ClientScreen (ServerScreen went into Profiles), TestModeScreen.
 */
public final class MenuLayouts {

    private MenuLayouts() {
    }

    /** The Client menu's entries, in order. */
    // Settings joined Options.
    public static final List<String> CLIENT_ENTRIES = List.of("World", "Items", "Blocks");
    public static final int MENU_W = 180;
    public static final int MENU_H = 24;
    // ONE COLUMN, NARROW BUTTONS: every menu's buttons are MENU_W wide, one under the next, centred; a menu
    // taller than the window scrolls.
    public static final int MENU_STEP = 32;
    /** The gap between two columns of a grid of buttons (the Help index still lays out in columns). */
    public static final int MENU_GAP = 8;
    /** The room the Client menu keeps under its buttons for its two lines. */
    public static final int CLIENT_FOOTER_RESERVE = 44;

    /**
     * ClientScreen: its entries, centred, "entry" plus the number from 0; the biome count and the profile
     * line stay put at the bottom ("status", "profile").
     */
    public static Layout client(Frame frame, TextFit.Measure m, String title, String status, String profile) {
        Layout layout = Layout.framed(frame, m, title);
        entries(layout, frame, CLIENT_ENTRIES, true, CLIENT_FOOTER_RESERVE);
        // Both lines at the bottom: the biome count sat under the title, where the failed-write banner is
        // drawn.
        layout.add(layout.text("profile", frame.contentLeft(), frame.footerY2(), profile, frame.contentWidth())
                .asPinned());
        layout.add(layout.text("status", frame.contentLeft(), frame.footerY(), status, frame.contentWidth())
                .asPinned());
        return layout.scrolls(true).footerReserve(CLIENT_FOOTER_RESERVE);
    }


    /** A menu's entries IN ONE COLUMN, top to bottom, centred. */
    private static void entries(Layout layout, Frame frame, List<String> entries) {
        entries(layout, frame, entries, true, 8);
    }

    private static void entries(Layout layout, Frame frame, List<String> entries, boolean main,
                                int reserve) {
        int w = Math.min(MENU_W, frame.contentWidth());
        int left = frame.contentLeft() + (frame.contentWidth() - w) / 2;
        // Centred vertically too: in the room between the header and the footer lines; taller than it, they
        // start at the top and scroll.
        int top = Grid.actionRowY() + 4;
        int gridH = (entries.size() - 1) * MENU_STEP + MENU_H;
        int y = top + Math.max(0, (frame.height() - reserve - top - gridH) / 2);
        for (int i = 0; i < entries.size(); i++) {
            Layout.Element e = Layout.control("entry" + i, Layout.Kind.BUTTON,
                    new Box(left, y + MENU_STEP * i, w, MENU_H), entries.get(i));
            layout.add(main ? e.asMain() : e);
        }
    }

    public static final int PREVIEW_X = 200;
    public static final int PREVIEW_STEP = 12;
    public static final int TEST_FOOTER_RESERVE = 44;

    /**
     * TestModeScreen: the mob ("entity"); the area level ("levelLabel", "level"); Spawn test mob ("spawn");
     * the scaling preview beside them ("previewHead", "preview" plus the number), as many lines as the view
     * holds; the feedback and the note at the bottom ("feedback", "note").
     */
    public static Layout testMode(Frame frame, TextFit.Measure m, String title, String entityText,
                                  String previewHead, List<String> preview, String feedback, String note) {
        Layout layout = Layout.framed(frame, m, title);
        int x = frame.contentLeft();
        int y = Grid.actionRowY();
        int rh = frame.rowH();
        int entityW = Math.min(Grid.fieldW(frame.contentWidth()) + 100, frame.contentWidth());
        layout.add(Layout.control("entity", Layout.Kind.BUTTON, new Box(x, y, entityW, Grid.CTRL_H),
                TextFit.clip(entityText, entityW - Layout.LABEL_MARGIN, m)).asMain());
        layout.add(layout.text("levelLabel", x, y + rh - 12, "Area level", frame.contentWidth()));
        layout.add(Layout.control("level", Layout.Kind.FIELD, new Box(x, y + rh, 80, Grid.CTRL_H), ""));
        // Not an edit form (no Undo/Save), so the Spawn action stays on the canonical row under the fields.
        layout.add(Layout.control("spawn", Layout.Kind.BUTTON, new Box(x, Grid.navRowY(2, rh), 140, 20),
                "Spawn test mob").asMain());

        // The preview: beside the fields when there is room, else under Spawn.
        int px = x + PREVIEW_X;
        int py = y + rh * 2 - 8;
        int room = frame.contentRight() - px;
        if (room < 120) {
            px = x;
            py = Grid.navRowY(2, rh) + 24;
            room = frame.contentWidth();
        }
        int viewBottom = frame.height() - TEST_FOOTER_RESERVE;
        if (previewHead != null && py + Layout.LINE_H <= viewBottom) {
            layout.add(layout.text("previewHead", px, py, previewHead, room));
            int ly = py + 14;
            for (int i = 0; i < preview.size() && ly + Layout.LINE_H <= viewBottom; i++) {
                layout.add(layout.text("preview" + i, px, ly, preview.get(i), room));
                ly += PREVIEW_STEP;
            }
        }
        layout.add(layout.text("note", x, frame.footerY2(), note, frame.contentWidth()).asPinned());
        layout.add(layout.text("feedback", x, frame.footerY(), feedback, frame.contentWidth()).asPinned());
        return layout.scrolls(true).footerReserve(TEST_FOOTER_RESERVE);
    }

    /** The goal form's label column: its controls start this far right, when there is room. */
    public static final int GOAL_CONTROL_X = 190;
    /** In a stacked row the control sits this far under its label. */
    public static final int GOAL_STACK = 11;
    public static final int GOAL_FOOTER_RESERVE = 34;

    /**
     * One row of a goal: its label and its controls.
     *
     * @param cells each control's id, kind, width, and every text it can show
     */
    public record GoalFormRow(String label, List<FormLayout.Cell> cells) {
    }

    /** The room a goal row's field keeps for its own title to its left: 0 for a cell with none. */
    private static int cellTitleRoom(FormLayout.Cell c, TextFit.Measure m) {
        return c.kind() == Layout.Kind.FIELD && c.label() != null ? m.width(c.label()) + 4 : 0;
    }

    public static final int GOAL_WIDE = 200;
    public static final int GOAL_NUMBER = 80;
    public static final int GOAL_ITEM_BUTTON = 56;

    /**
     * A goal's rows, from its type: the priority; each setting by its kind (a number a field, on/off a
     * switch, a who a picker button, a choice a cycle, an item list a field and + Item); the condition, and
     * its value when it takes one; the switch.
     *
     * @param type the goal's type, or null for the mob's own goal (no settings)
     * @param targetText what a who setting's button shows, by its key
     * @param withValue the condition chosen takes a value
     */
    public static List<GoalFormRow> goalRows(com.strata.model.GoalType.Type type,
                                         java.util.function.Function<String, String> targetText,
                                         boolean withValue) {
        List<GoalFormRow> rows = new ArrayList<>();
        rows.add(new GoalFormRow("Priority (lower runs first)", List.of(FormLayout.Cell.field("priority", 60, null))));
        if (type != null) {
            for (com.strata.model.GoalType.Param p : type.params()) {
                String id = "p." + p.key();
                List<FormLayout.Cell> cells = switch (p.kind()) {
                    case FLAG -> List.of(FormLayout.Cell.toggle(id, GOAL_NUMBER, null, ""));
                    case TARGET, PROJECTILE -> List.of(FormLayout.Cell.button(id, GOAL_WIDE, null, targetText.apply(p.key())));
                    case CHOICE -> List.of(FormLayout.Cell.choice(id, GOAL_WIDE, null, "", p.choices()));
                    case ITEMS -> List.of(FormLayout.Cell.field(id, GOAL_WIDE, null),
                            FormLayout.Cell.button(id + ".add", GOAL_ITEM_BUTTON, null, "+ Add item"),
                            // The list is not typed: items go on by the picker, and off here.
                            FormLayout.Cell.button(id + ".remove", GOAL_ITEM_BUTTON, null, "Remove"));
                    default -> List.of(FormLayout.Cell.field(id, GOAL_NUMBER, null));
                };
                rows.add(new GoalFormRow(p.label(), cells));
            }
        }
        List<FormLayout.Cell> when = new ArrayList<>();
        when.add(FormLayout.Cell.choice("when", 130, null, "", com.strata.model.GoalCondition.CHOICES));
        if (withValue) {
            // Its own title, beside it.
            when.add(FormLayout.Cell.field("whenValue", 60, "Value"));
        }
        rows.add(new GoalFormRow("Only when", when));
        rows.add(new GoalFormRow("Switched on", List.of(FormLayout.Cell.toggle("on", GOAL_NUMBER, null, "On"))));
        return rows;
    }

    /**
     * GoalScreen: Save, pinned; one row per setting, the label on the left and the controls beside it at
     * GOAL_CONTROL_X, or STACKED (the label above, the controls under it) on a window with no room beside it:
     * an item list and its + Item button ran off a narrow one.
     */
    public static Layout goal(Frame frame, TextFit.Measure m, String title, List<GoalFormRow> rows, String footer) {
        Layout layout = Layout.framed(frame, m, title);
        int x = frame.contentLeft();
        int top = Grid.actionRowY();
        layout.add(Layout.control("save", Layout.Kind.BUTTON, new Box(x, top, Grid.BTN_W, Grid.CTRL_H), "Done")
                .asPinned().asMain());
        int viewTop = top + Grid.CTRL_H + Grid.GAP;
        layout.viewTop(viewTop);

        int widest = 0;
        for (GoalFormRow row : rows) {
            int w = -Grid.GAP;
            for (FormLayout.Cell c : row.cells()) {
                w += c.width() + cellTitleRoom(c, m) + Grid.GAP;
            }
            widest = Math.max(widest, w);
        }
        boolean beside = GOAL_CONTROL_X + widest <= frame.contentWidth();
        // A wide column widens the wide controls into the slack beside the labels.
        int wideW = beside ? Math.min(Grid.fieldW(frame.contentWidth()), GOAL_WIDE + frame.contentWidth() - GOAL_CONTROL_X - widest)
                : GOAL_WIDE;
        int rh = frame.rowH();
        int y = viewTop + 2;
        for (GoalFormRow row : rows) {
            int cx = beside ? x + GOAL_CONTROL_X : x;
            int cy = beside ? y : y + GOAL_STACK;
            String first = row.cells().isEmpty() ? "row" + y : row.cells().getFirst().id();
            layout.add(layout.text("label." + first, x, beside ? y + 5 : y, row.label(),
                    beside ? GOAL_CONTROL_X - 4 : frame.contentWidth()));
            List<Integer> widths = new ArrayList<>();
            row.cells().forEach(c -> widths.add(Math.min((c.width() == GOAL_WIDE ? wideW : c.width())
                    + cellTitleRoom(c, m), frame.contentRight() - cx)));
            Flow.Placed placed = Flow.row(cx, frame.contentRight(), cy, Grid.CTRL_H, Grid.GAP,
                    widths.stream().mapToInt(Integer::intValue).toArray());
            for (int i = 0; i < row.cells().size(); i++) {
                FormLayout.Cell c = row.cells().get(i);
                String text = c.texts().stream().max(java.util.Comparator.comparingInt(m::width)).orElse("");
                Box at = placed.boxes().get(i);
                int titleRoom = Math.min(cellTitleRoom(c, m), at.w() - 1);
                if (titleRoom > 0) {
                    // A field's own title, beside it, kept when the row wraps.
                    layout.add(layout.text("label." + c.id(), at.x(), at.y() + (at.h() - Layout.LINE_H) / 2,
                            c.label(), Integer.MAX_VALUE));
                    at = new Box(at.x() + titleRoom, at.y(), at.w() - titleRoom, at.h());
                }
                layout.add(Layout.control(c.id(), c.kind(), at,
                        c.kind() == Layout.Kind.FIELD ? "" : TextFit.clip(text, widths.get(i) - Layout.LABEL_MARGIN, m)));
            }
            y += Math.max(rh, placed.bottom() - y + Grid.GAP);
        }
        layout.add(layout.text("footer", x, frame.footerY(), footer, frame.contentWidth()).asPinned());
        return layout.scrolls(true).footerReserve(GOAL_FOOTER_RESERVE);
    }
}
