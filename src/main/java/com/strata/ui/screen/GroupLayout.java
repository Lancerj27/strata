package com.strata.ui.screen;

import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** THE GROUP CONTROLS, LAID OUT. */
public final class GroupLayout {

    private GroupLayout() {
    }

    /** The group picker's width: "Group: " plus the widest name the gate saw. */
    public static final int GROUP_W = 130;
    public static final int APPLY_GROUP_W = 95;
    public static final int EDIT_GROUP_W = 85;
    public static final int GROUP_NAME_W = 140;
    public static final int MAKE_GROUP_W = 85;

    public static final String APPLY_GROUP = "Apply group";
    public static final String EDIT_GROUP = "Edit group";
    public static final String MAKE_GROUP = "Make group";
    /** What the picker says when there is nothing to pick yet. */
    public static final String NO_GROUPS = "none yet";

    /**
     * The picker's text, from what the screen knows.
     *
     * @param pickedName the chosen group's NAME, or "" for none
     * @param anyMade whether this kind has any group at all, so "none yet" is not shown beside a picker that
     *     has something in it
     */
    public static String text(String pickedName, boolean anyMade) {
        if (pickedName != null && !pickedName.isBlank()) {
            return pickedName;
        }
        return anyMade ? "" : NO_GROUPS;
    }

    /**
     * The row above the list: the picker, Apply group, Edit group.
     *
     * @param groupText the picker's text, {@link #text} of it
     * @param canGroup false when no group is picked, which greys Apply and Edit rather than leaving them to
     *     refuse after the click
     */
    public static List<ListLayout.Action> top(TextFit.Measure measure, String groupText, boolean canGroup) {
        // A top-row Action has no separate label field, so the button's text carries "Group: " itself and has
        // to be cut to the button's width here.
        return List.of(ListLayout.Action.of("group",
                        TextFit.clip(Layout.choiceText("Group", groupText == null ? NO_GROUPS : groupText),
                                GROUP_W - Layout.LABEL_MARGIN, measure), GROUP_W),
                ListLayout.Action.of("applyGroup", APPLY_GROUP, APPLY_GROUP_W).withActive(canGroup),
                ListLayout.Action.of("editGroup", EDIT_GROUP, EDIT_GROUP_W).withActive(canGroup));
    }

    /** The make-group row beneath the top row. */
    public static List<FormLayout.Cell> underTop() {
        return List.of(FormLayout.Cell.field("groupName", GROUP_NAME_W, "New group"),
                FormLayout.Cell.button("groupMake", MAKE_GROUP_W, "", MAKE_GROUP));
    }
}
