package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** THE REPLACEMENTS LIST, LAID OUT. */
public final class ReplacementsLayout {

    private ReplacementsLayout() {
    }

    /** The two pickers' width. */
    public static final int MOB_W = 120;

    /** Every add-row cell; each has a "label." + id title over it. */
    public static final List<String> FIELDS = List.of("from", "to", "share", "sources");

    /**
     * @param fromText the From picker's text
     * @param toText the To picker's text
     * @param sources what Sources offers
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, String fromText, String toText,
                               List<String> sources, String footer, boolean removeActive) {
        int mw = Grid.wide(MOB_W, frame.contentWidth(), 18, 200);
        return ListLayout.buildWithUndo(frame, measure, new ListLayout.Input(title, List.of(),
                List.of(FormLayout.Cell.button("from", mw, "Replace",
                                TextFit.clip(fromText, mw - Layout.LABEL_MARGIN, measure)),
                        FormLayout.Cell.button("to", mw, "With",
                                TextFit.clip(toText, mw - Layout.LABEL_MARGIN, measure)),
                        FormLayout.Cell.field("share", 60, "Share %"),
                        FormLayout.Cell.choice("sources", 110, "Sources", "Sources", sources)),
                List.of(ListLayout.Action.of("add", "+ Add replacement").asMain(),
                        ListLayout.Action.of("remove", "Remove").withActive(removeActive)),
                List.of(), footer, "No replacements here. Pick a mob to replace and the mob to spawn instead."));
    }
}
