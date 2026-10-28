package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** ONE EQUIPMENT ROW, LAID OUT: EquipmentItemScreen. */
public final class EquipmentItemLayout {

    private EquipmentItemLayout() {
    }

    /** The Item button: this wide at most. */
    public static final int ITEM_W = Grid.FIELD_W + 100;
    /** A number field. */
    public static final int NUM_W = 100;
    /** The Local difficulty choice: its label and "Yes" fit. */
    public static final int SCALED_W = 140;

    /**
     * @param slots every slot the Slot dropdown offers
     * @param itemName the chosen item, in words
     * @param footer1 the scope line
     * @param footer2 the refusal or the inherited note, or null
     * @param scaledChoices the Local difficulty dropdown's words
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, List<String> slots,
                               String itemName, String footer1, String footer2, List<String> scaledChoices) {
        int itemW = Math.min(ITEM_W, frame.contentWidth());
        List<FormLayout.Row> rows = new ArrayList<>();
        rows.add(FormLayout.Row.of(
                FormLayout.Cell.choice("slot", 160, "Slot", "Slot", slots),
                FormLayout.Cell.button("item", itemW, "Item",
                        TextFit.clip(itemName, itemW - Layout.LABEL_MARGIN, measure))));
        rows.add(FormLayout.Row.of(FormLayout.Cell.field("chance", NUM_W, "Normal %"),
                FormLayout.Cell.field("chanceHard", NUM_W, "Hard %"),
                FormLayout.Cell.choice("scaled", SCALED_W, "Local difficulty", "Local difficulty", scaledChoices)));
        rows.add(FormLayout.Row.of(FormLayout.Cell.field("drop", NUM_W, "Drop %"),
                FormLayout.Cell.field("enchant", NUM_W, "Enchant %"),
                FormLayout.Cell.field("playerLevel", NUM_W, "Player XP level")));
        return FormLayout.build(frame, measure, new FormLayout.Input(title, false, true, List.copyOf(rows),
                footer1, footer2));
    }
}
