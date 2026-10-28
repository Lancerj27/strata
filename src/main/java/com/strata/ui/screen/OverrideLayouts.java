package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/**
 * THE OVERRIDE SHEET, LAID OUT: one kind's entries WITH one entry's fields beside them, and the
 * game-rules list.
 */
public final class OverrideLayouts {

    private OverrideLayouts() {
    }

    /** The share of the content width the editor panel takes; the entries list takes the rest. */
    public static final int EDITOR_SHARE = 52;

    /**
     * A kind's entries, WITH THE SELECTED ENTRY'S FIELDS BESIDE THEM: the entries list at the left (a Type
     * search over it, which matches the type too, and a bar of On / Off, Restore and the side button), the editor panel at
     * the right (the entry's fields, and its own bar of Value, Set value, Restore, Duplicate element, Remove
     * element and Restore all).
     *
     * @param sideLabel the side button's words ("Spacing", "Settings"), or null for none
     * @param tools top-row buttons at the right end, ids "tool0", "tool1"...
     * @param hints the editor's hint lines, top first (each a line even when empty)
     * @param editorTitle the editor panel's own title, over its fields list
     */
    public static Layout listWithEditor(Frame frame, TextFit.Measure measure, String title,
            String footer, boolean picked, String sideLabel, boolean sideActive, List<String> tools,
            List<String> hints, String editorTitle) {
        List<ListLayout.Action> top = new ArrayList<>();
        top.add(ListLayout.Action.field("search", 160));
        List<String> toolIds = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            toolIds.add("tool" + i);
            top.add(ListLayout.Action.of("tool" + i, tools.get(i)));
        }
        List<ListLayout.Action> entriesBar = new ArrayList<>(List.of(
                ListLayout.Action.of("toggle", "On / Off").withActive(picked),
                ListLayout.Action.of("reset", "Restore").withActive(picked)));
        if (sideLabel != null) {
            entriesBar.add(ListLayout.Action.of("side", sideLabel).withActive(sideActive));
        }
        Layout layout = ListLayout.rightAlign(ListLayout.buildWithUndo(frame, measure, new ListLayout.Input(title, top,
                List.of(), entriesBar, List.of(), footer, "Nothing read yet: open a world once, and every entry of"
                + " the game and its mods is listed here.")), frame, toolIds);

        // THE EDITOR'S BAR, above the list's: the list's bar keeps the bottom line, where every other list
        // screen puts Undo and its actions.
        int entriesBarTop = barTop(layout, "undo", "toggle", "reset", "side");
        List<ListLayout.Action> editorBar = List.of(
                ListLayout.Action.field("value", 120, "Value"),
                ListLayout.Action.of("pset", "Set value").asMain(),
                ListLayout.Action.of("prestore", "Restore"),
                ListLayout.Action.of("pdup", "Duplicate element"),
                ListLayout.Action.of("pdrop", "Remove element"),
                ListLayout.Action.of("presetall", "Restore all"));
        int editorBarTop = ListLayout.placeBar(layout, frame, measure, editorBar,
                entriesBarTop - Grid.GAP - Grid.CTRL_H);

        // The editor's hint lines, over its bar and under both lists.
        int listBottom = editorBarTop - ListLayout.LIST_GAP;
        if (!hints.isEmpty()) {
            int firstY = editorBarTop - ListLayout.HINT_ABOVE_BAR - (hints.size() - 1) * ListLayout.LINE_STEP;
            for (int i = 0; i < hints.size(); i++) {
                layout.add(layout.text("hint" + i, frame.contentLeft(), firstY + i * ListLayout.LINE_STEP,
                        hints.get(i), frame.contentWidth()));
            }
            listBottom = firstY - ListLayout.LIST_GAP;
        }

        // The two columns: the entries at the left, the editor at the right.
        Layout.Element listEl = layout.get("list");
        Box all = listEl.box();
        int gap = Grid.GAP * 2;
        int editorW = (all.w() - gap) * EDITOR_SHARE / 100;
        int entriesW = all.w() - gap - editorW;
        int editorX = all.x() + entriesW + gap;
        layout.replace(new Layout.Element(listEl.id(), listEl.kind(),
                new Box(all.x(), all.y(), entriesW, Math.max(22, listBottom - all.y())), listEl.text(),
                listEl.pinned(), listEl.active(), listEl.main(), listEl.hidden()));
        // The list's empty note is cut to the narrow list: over its full width it would run into the editor.
        Layout.Element empty = layout.get("empty");
        if (empty != null) {
            layout.replace(layout.note("empty", all.x() + 4, empty.box().y(), empty.text(), entriesW - 8));
        }
        layout.add(layout.text("panelTitle", editorX, all.y(), editorTitle, editorW));
        int fieldTop = all.y() + Layout.LINE_H + 4;
        Box fieldBox = new Box(editorX, fieldTop, editorW, Math.max(22, listBottom - fieldTop));
        layout.add(Layout.control("fieldList", Layout.Kind.LIST, fieldBox, ""));
        layout.add(layout.note("panelEmpty", fieldBox.x() + 4, fieldBox.y() + ListLayout.LIST_GAP,
                "Select an entry at the left to edit its fields.", fieldBox.w() - 8));
        return layout;
    }

    /** The y of the top line of a bar, from the elements the layout placed for its ids. */
    private static int barTop(Layout layout, String... ids) {
        int top = Integer.MAX_VALUE;
        for (String id : ids) {
            Layout.Element e = layout.get(id);
            if (e != null) {
                top = Math.min(top, e.box().y());
            }
        }
        return top == Integer.MAX_VALUE ? 0 : top;
    }

    /**
     * The game rules, as one list, edited in place: its value field, Set value, Restore (the rule), and
     * Restore all (every rule).
     */
    public static Layout rules(Frame frame, TextFit.Measure measure, String title, List<String> hints, String footer) {
        List<ListLayout.Action> bar = new ArrayList<>(List.of(ListLayout.Action.field("value", 160),
                ListLayout.Action.of("set", "Set value").asMain(),
                ListLayout.Action.of("restore", "Restore"),
                ListLayout.Action.of("reset", "Restore all")));
        return ListLayout.buildWithUndo(frame, measure, new ListLayout.Input(title, List.of(), List.of(), bar,
                hints, footer, "This entry is not in the game now: a mod that added it may be gone."));
    }
}
