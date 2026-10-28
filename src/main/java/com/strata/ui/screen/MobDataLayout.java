package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** THE MOB DATA SHEET, LAID OUT: MobDataScreen. */
public final class MobDataLayout {

    private MobDataLayout() {
    }

    /** The help, wrapped to HELP_LINES; a short help keeps the lines' room. */
    private static List<String> helpLines(String help, Frame frame, TextFit.Measure measure) {
        List<String> lines = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), HELP_LINES, measure));
        while (lines.size() < HELP_LINES) {
            lines.add("");
        }
        return lines;
    }

    /** The help text may take this many lines. */
    public static final int HELP_LINES = 2;

    /**
     * @param tabs the group tabs' labels, in order
     * @param active the open tab, which is greyed out
     * @param help the help for the selected value
     * @param empty the text in the list when nothing was read, or null
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, List<String> tabs, int active,
                               String help, String footer, String empty) {
        return build(frame, measure, title, tabs, active, help, footer, empty, List.of(), false);
    }

    /**
     * @param switches the Attributes tab's Category, Mob griefing, and Spawn as, each on its own labelled row
     *     beneath the tabs and the search. They are absent on the other tabs.
     * @param play true on the Sounds tab: a Play button hears the selected sound
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, List<String> tabs, int active,
                               String help, String footer, String empty, List<FormLayout.Cell> switches,
                               boolean play) {
        List<ListLayout.Action> top = new ArrayList<>();
        for (int i = 0; i < tabs.size(); i++) {
            top.add(ListLayout.Action.of("tab" + i, tabs.get(i)).withActive(i != active));
        }
        top.add(ListLayout.Action.field("search", 160));
        return ListLayout.buildWithUndo(frame, measure, new ListLayout.Input(title, top, List.of(),
                play ? List.of(ListLayout.Action.field("value", 160),
                        ListLayout.Action.of("set", "Set value"),
                        ListLayout.Action.of("restore", "Restore"),
                        ListLayout.Action.of("play", "Play"))
                        : List.of(ListLayout.Action.field("value", 160),
                        ListLayout.Action.of("set", "Set value"),
                        ListLayout.Action.of("restore", "Restore")),
                helpLines(help, frame, measure), footer, empty).withUnderTop(switches));
    }
}
