package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** THE EQUIPMENT LIST, LAID OUT: EquipmentScreen. */
public final class EquipmentLayout {

    private EquipmentLayout() {
    }

    /** The Remove button's two labels: which one depends on the selected row. */
    public static final String REMOVE = "Remove";
    public static final String DENY = "Deny";

    /**
     * The Remove button's text for the picked rows: Remove or Deny for one, with the count for several,
     * "Remove / Deny (5)" for a mix.
     */
    public static String removeLabel(int denies, int deletes) {
        return ListLayout.manyLabel(REMOVE, DENY, deletes, denies);
    }

    /**
     * @param removeLabel REMOVE or DENY
     * @param groupText the group picker's text, {@link GroupLayout#text} of it
     * @param canGroup a group is picked, so Apply and Edit are live
     * @param empty the text in an empty list, or null
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, String removeLabel,
                               String hint, String footer, String empty, String groupText, boolean canGroup) {
        return ListLayout.buildWithUndo(frame, measure, new ListLayout.Input(title,
                GroupLayout.top(measure, groupText, canGroup), List.of(),
                List.of(ListLayout.Action.of("add", "+ Add equipment").asMain(),
                        ListLayout.Action.of("edit", "Edit"),
                        ListLayout.Action.of("remove", removeLabel, 120),
                        ListLayout.Action.of("restore", "Restore all")),
                hint == null ? List.of() : List.of(hint), footer, empty)
                .withUnderTop(GroupLayout.underTop()).withChanceHeading());
    }
}
