package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Flow;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** THE MOB EDITOR, LAID OUT. */
public final class MobEditLayout {

    private MobEditLayout() {
    }

    /** The most lines the AI box shows (fewer when the view is short); a longer AI ends with "... */
    public static final int GOALS_BOX_LINES = 14;
    /** The AI box's inner margin. */
    public static final int GOALS_BOX_PAD = 3;
    /** The room under the view for the two footer lines. */
    public static final int FOOTER_RESERVE = 44;
    /** The line step of wrapped text: the font's line height. */
    public static final int LINE_STEP = 9;

    /**
     * What the editor shows.
     *
     * @param isNew a row not saved yet: it picks its scope and target
     * @param showDelete the row can be deleted here
     * @param toolsActive the tools are usable (the row exists)
     * @param canReset Reset to game is usable: a saved plain mob with edits
     * @param saveActive Save is usable (every field is valid)
     * @param scopeButton the Scope button's text, for a new row
     * @param targetText the Target picker's text, for a new row ("Target: t1")
     * @param scopeText the locked scope, in words, for an existing row
     * @param baseEntity the base entity, in words
     * @param goalSetLabel the Goal set button's text ("Goal set: skeleton")
     * @param goalLines the AI in effect, as GoalsOps#goalsText lays it out
     * @param nameLabel the label over the name field
     * @param variantFields the variant-only section is shown (the row has a variant name)
     * @param enabledOn the Enabled toggle is on
     * @param footer1 the last footer line (drops)
     * @param footer2 the footer line above it (equipment)
     * @param scalingText the Scaling dropdown's text ("Scaling: Default"),
     * @param trades the mob trades (a villager, the wandering trader): the Trades tool shows,
     */
    public record Input(String title, boolean isNew, boolean showDelete, boolean toolsActive, boolean canReset,
                        boolean saveActive,
                        String scopeButton, String targetText, String scopeText, String baseEntity,
                        String goalSetLabel,
                        List<String> goalLines, String nameLabel, boolean variantFields, boolean enabledOn,
                        String footer1, String footer2, boolean trades, String scalingText) {
    }

    public static Layout build(Frame frame, TextFit.Measure measure, Input in) {
        Layout layout = Layout.framed(frame, measure, in.title());
        int x = frame.contentLeft();
        int rh = frame.rowH();

        // ---------- PINNED: the actions, the tools, the behavior ----------
        int top = Grid.actionRowY();
        layout.add(Layout.control("undo", Layout.Kind.BUTTON, new Box(x, top, Grid.BTN_W, Grid.CTRL_H), "Undo")
                .asPinned());
        layout.add(Layout.control("save", Layout.Kind.BUTTON,
                new Box(x + Grid.BTN_2, top, Grid.BTN_W_WIDE, Grid.CTRL_H), "Save").asPinned().asMain()
                .withActive(in.saveActive()));
        // RESET TO GAME BESIDE DELETE, ending the row.
        int resetX = x + (in.showDelete() ? Grid.BTN_3 + Grid.BTN_W_WIDE + Grid.GAP : Grid.BTN_3);
        if (in.showDelete()) {
            layout.add(Layout.control("delete", Layout.Kind.BUTTON,
                    new Box(x + Grid.BTN_3, top, Grid.BTN_W_WIDE, Grid.CTRL_H), "Remove").asPinned());
        }
        layout.add(Layout.control("reset", Layout.Kind.BUTTON, new Box(resetX, top, 100, Grid.CTRL_H), "Restore all")
                .asPinned().withActive(in.canReset()));

        // The AI is not here any more: it is the editor's bottom section.
        List<String> ids = new java.util.ArrayList<>(List.of("mobdata", "biomes", "spawnRules", "loot", "equipment",
                "abilities"));
        List<String> labels = new java.util.ArrayList<>(List.of("Mob data", "Biomes", "Spawn rules", "Loot", "Equipment",
                "Abilities"));
        List<Integer> widths = new java.util.ArrayList<>(List.of(90, Grid.BTN_W, 100, Grid.BTN_W, 90, Grid.BTN_W_WIDE));
        if (in.trades()) {
            ids.add("trades");
            labels.add("Trades");
            widths.add(Grid.BTN_W);
        }
        ids.add("enabled");
        labels.add(Layout.toggleText("Enabled", in.enabledOn()));
        widths.add(120);
        Flow.Placed tools = Flow.row(x, frame.contentRight(), top + Grid.CTRL_H + 6, Grid.CTRL_H, Grid.GAP,
                widths.stream().mapToInt(Integer::intValue).toArray());
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals("enabled")) {
                layout.add(Layout.control("enabled", Layout.Kind.TOGGLE, tools.boxes().get(i), labels.get(i))
                        .asPinned());
                continue;
            }
            // The tools act on a saved row: on a new row they wait for the first save, so they are main
            // actions only on a saved row.
            Layout.Element tool = Layout.control(ids.get(i), Layout.Kind.BUTTON, tools.boxes().get(i), labels.get(i))
                    .asPinned().withActive(in.toolsActive());
            layout.add(!in.isNew() ? tool.asMain() : tool);
        }
        // THE MOB'S OTHER SWITCHES ARE IN MOB DATA: Category, Mob griefing, and Spawn as head its Attributes
        // tab.
        int pinnedBottom = tools.bottom() + 4;
        layout.viewTop(pinnedBottom);

        // ---------- THE FIELDS: they scroll under the pinned part ----------
        int y = pinnedBottom - Grid.LABEL_OFFSET + 2;
        int room = frame.contentWidth();
        if (in.isNew()) {
            layout.add(layout.text("scopeLabel", x, y + Grid.LABEL_OFFSET, "Scope / Target (pick a tier or biome)",
                    room));
            layout.add(Layout.control("scope", Layout.Kind.BUTTON, new Box(x, y, 140, Grid.CTRL_H),
                    TextFit.clip(in.scopeButton(), 140 - Layout.LABEL_MARGIN, measure)));
            // A PICKER, never typed.
            layout.add(Layout.control("target", Layout.Kind.BUTTON, new Box(x + 150, y, fieldW(frame, 150),
                    Grid.CTRL_H), TextFit.clip(in.targetText(), fieldW(frame, 150) - Layout.LABEL_MARGIN, measure)));
        } else {
            // LOCKED, so it is stated rather than offered.
            layout.add(layout.text("scopeLabel", x, y + Grid.LABEL_OFFSET, "Scope (locked to where you opened it)",
                    room));
            layout.add(layout.text("scopeText", x, y + 4, in.scopeText(), room));
        }
        y += rh;
        layout.add(layout.text("baseLabel", x, y + Grid.LABEL_OFFSET, "Base entity", room));
        layout.add(Layout.control("base", Layout.Kind.BUTTON, new Box(x, y, fieldW(frame, 0), Grid.CTRL_H),
                TextFit.clip(in.baseEntity(), fieldW(frame, 0) - Layout.LABEL_MARGIN, measure)));
        y += rh;
        // A TITLE OVER EACH FIELD: one label over three fields named only the first.
        layout.add(layout.text("groupLabel", x, y + Grid.LABEL_OFFSET, "Spawn weight", 90));
        layout.add(layout.text("minLabel", x + 90, y + Grid.LABEL_OFFSET, "Min count", 90));
        layout.add(layout.text("maxLabel", x + 180, y + Grid.LABEL_OFFSET, "Max count", 90));
        layout.add(Layout.control("weight", Layout.Kind.FIELD, new Box(x, y, 80, Grid.CTRL_H), ""));
        layout.add(Layout.control("min", Layout.Kind.FIELD, new Box(x + 90, y, 80, Grid.CTRL_H), ""));
        layout.add(Layout.control("max", Layout.Kind.FIELD, new Box(x + 180, y, 80, Grid.CTRL_H), ""));
        y += rh;
        // THE SPAWN CHANCE: after the game picks the mob by weight.
        layout.add(layout.text("chanceLabel", x, y + Grid.LABEL_OFFSET,
                "Spawn chance % (after the weight picks it; blank = 100)", room));
        layout.add(Layout.control("chance", Layout.Kind.FIELD, new Box(x, y, 80, Grid.CTRL_H), ""));
        y += rh;
        layout.add(layout.text("enabledLabel", x, y + Grid.LABEL_OFFSET, "Scaling", 130));
        layout.add(layout.text("coefficientLabel", x + 130, y + Grid.LABEL_OFFSET, "Coefficient", 110));
        layout.add(layout.text("startLevelLabel", x + 240, y + Grid.LABEL_OFFSET, "Start level", 100));
        layout.add(Layout.control("scaling", Layout.Kind.BUTTON, new Box(x, y, 120, Grid.CTRL_H),
                TextFit.clip(in.scalingText(), 120 - Layout.LABEL_MARGIN, measure)));
        layout.add(Layout.control("coefficient", Layout.Kind.FIELD, new Box(x + 130, y, 100, Grid.CTRL_H), ""));
        // THE MOB'S OWN LEVEL: scaling moves its stats relative to it.
        layout.add(Layout.control("startLevel", Layout.Kind.FIELD, new Box(x + 240, y, 60, Grid.CTRL_H), ""));
        y += rh;
        layout.add(layout.text("nameLabel", x, y + Grid.LABEL_OFFSET, in.nameLabel(), room));
        layout.add(Layout.control("name", Layout.Kind.FIELD, new Box(x, y, fieldW(frame, 0), Grid.CTRL_H), ""));
        y += rh;
        // THE DISPLAY NAME, UNDER THE VARIANT NAME.
        boolean off = !in.variantFields();
        layout.add(layout.text("displayLabel", x, y + Grid.LABEL_OFFSET, "Display name (blank = use id)", room)
                .withHidden(off));
        layout.add(Layout.control("display", Layout.Kind.FIELD, new Box(x, y, fieldW(frame, 0), Grid.CTRL_H), "")
                .withHidden(off));
        if (!off) {
            y += rh;
        }

        // ---------- THE AI: its own section, under the name ----------
        layout.add(layout.text("goalSetLabel", x, y + Grid.LABEL_OFFSET,
                "Goal set and goals (the button picks a goal set, or edits this mob's goals)", room));
        layout.add(Layout.control("goalSet", Layout.Kind.BUTTON, new Box(x, y, fieldW(frame, 0), Grid.CTRL_H),
                TextFit.clip(in.goalSetLabel(), fieldW(frame, 0) - Layout.LABEL_MARGIN, measure)));
        // THE BOX FITS THE VIEW: the scroll hides a control it cannot show whole, so a box taller than the
        // view would never show.
        int reserve = FOOTER_RESERVE;
        int view = frame.height() - reserve - pinnedBottom - 2;
        int maxLines = Math.max(2, Math.min(GOALS_BOX_LINES, (view - GOALS_BOX_PAD * 2) / LINE_STEP));
        List<String> ai = new java.util.ArrayList<>(in.goalLines());
        if (ai.size() > maxLines) {
            int more = ai.size() - (maxLines - 1);
            ai = new java.util.ArrayList<>(ai.subList(0, maxLines - 1));
            ai.add("  ... and " + more + " more line(s): the button's Edit goals shows them all");
        }
        int boxTop = y + Grid.CTRL_H + Grid.GAP;
        Box box = new Box(x, boxTop, frame.contentWidth(), ai.size() * LINE_STEP + GOALS_BOX_PAD * 2);
        layout.add(Layout.control("goalsBox", Layout.Kind.LIST, box, ""));
        for (int i = 0; i < ai.size(); i++) {
            layout.add(layout.note("ai" + i, x + GOALS_BOX_PAD, boxTop + GOALS_BOX_PAD + i * LINE_STEP, ai.get(i),
                    box.w() - GOALS_BOX_PAD * 2));
        }

        // ---------- THE FOOTER: it stays put, under the view ---------- Texture path and Own entity are
        // gone: the Model tab sets a mob's texture, and every variant is its own mob.
        layout.add(layout.text("footer2", x, frame.footerY2(), in.footer2(), room).asPinned());
        layout.add(layout.text("footer1", x, frame.footerY(), in.footer1(), room).asPinned());
        return layout.scrolls(true).footerReserve(reserve);
    }

    /** A field's width from dx right of the content's left edge: FIELD_W, or what room is left. */
    private static int fieldW(Frame frame, int dx) {
        return Math.min(Grid.FIELD_W, frame.contentWidth() - dx);
    }
}
