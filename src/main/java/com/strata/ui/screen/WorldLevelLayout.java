package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** THE WORLD LEVEL SCREEN, LAID OUT: WorldLevelScreen. The stage tabs, the stage form, the rule row, the rules. */
public final class WorldLevelLayout {

    private WorldLevelLayout() {
    }

    /** The picker cells' width on a small column. */
    public static final int PICK_W = 90;

    /** The picker cells' width on a wide column; PICK_W on a small one. */
    public static int pickW(Frame frame) {
        return Grid.wide(PICK_W, frame.contentWidth(), 12, 130);
    }

    /** What the rule row's value cell is. */
    public enum ValueCell { FIELD, CHOICE, BUTTON }

    /**
     * The stage form's and the rule row's texts, as they stand.
     *
     * @param start true on the Start tab: no name, players or conditions, and no rule row
     * @param kinds every rule kind the Kind dropdown offers
     * @param kind the kind chosen now
     * @param valueCell what the value cell is for that kind
     * @param valueText the value button's text (a BUTTON cell), or the chosen tier (a CHOICE cell)
     * @param tiers the tier names (a CHOICE cell)
     * @param hasEffect true when the kind is a locked area: an effect picker and an amplifier field
     * @param effectText the effect picker's text
     * @param addLabel "+ Add rule", or "Save rule" while a rule is edited
     */
    public record Form(boolean start, List<String> kinds, String kind, ValueCell valueCell, String valueText,
                       List<String> tiers, boolean hasEffect, String effectText, String addLabel) {
    }

    public static final List<String> PLAYER_CHOICES = List.of("any", "all");
    public static final List<String> CONDITION_CHOICES = List.of("all", "any");
    public static final List<String> MODES = List.of("add", "subtract", "multiply", "scale_level");
    public static final List<String> HOLDS = List.of("keep", "while_met");
    public static final List<String> LEVEL_SOURCES = List.of("highest_ever", "highest_online", "average_online", "total_online");

    /**
     * @param tabs the page of stage tabs (ListScreenLayouts#stageTabs)
     * @param help the hint under the list
     * @param footer the line under the bar
     * @param empty the note in an empty list, or null
     */
    public static Layout build(Frame frame, TextFit.Measure m, String title, ListScreenLayouts.StageTabs tabs,
                               Form form, String help, String footer, String empty) {
        // THE TABS: a page of them, arrows when paged, + Stage last.
        List<ListLayout.Action> top = new ArrayList<>();
        boolean paged = tabs.count() < tabs.labels().size();
        if (paged && tabs.first() > 0) {
            top.add(ListLayout.Action.of("tab_back", "<"));
        }
        for (int i = tabs.first(); i < tabs.first() + tabs.count() && i < tabs.labels().size(); i++) {
            top.add(ListLayout.Action.of("tab" + i, tabs.labels().get(i)));
        }
        if (paged && tabs.first() + tabs.count() < tabs.labels().size()) {
            top.add(ListLayout.Action.of("tab_more", ">"));
        }
        top.add(ListLayout.Action.of("add_stage", "+ Stage"));

        // THE STAGE FORM, under the tabs.
        List<FormLayout.Cell> stage = new ArrayList<>();
        if (!form.start()) {
            stage.add(FormLayout.Cell.field("name", 110, "Name"));
            stage.add(FormLayout.Cell.choice("players", 70, "Players", "Players", PLAYER_CHOICES));
            stage.add(FormLayout.Cell.choice("conditions", 76, "Conditions", "Conditions", CONDITION_CHOICES));
        }
        stage.add(FormLayout.Cell.field("offset", 54, "Offset"));
        stage.add(FormLayout.Cell.field("coefficient", 66, "Coef."));
        stage.add(FormLayout.Cell.choice("mode", 90, "Mode", "Mode", MODES));
        if (form.start()) {
            stage.add(FormLayout.Cell.choice("level_source", 110, "Level source", "Level source", LEVEL_SOURCES));
        } else {
            stage.add(FormLayout.Cell.choice("hold", 84, "Hold", "Hold", HOLDS));
        }
        stage.add(FormLayout.Cell.button("save_stage", Grid.buttonW("Save stage"), null, "Save stage"));

        // THE RULE ROW, over the list: the kind, its value, and a locked area's effect and amplifier.
        List<FormLayout.Cell> rule = new ArrayList<>();
        List<ListLayout.Action> bar = new ArrayList<>();
        if (!form.start()) {
            int pw = pickW(frame);
            rule.add(FormLayout.Cell.choice("kind", 120, "Rule", "Kind", form.kinds()));
            switch (form.valueCell()) {
                case FIELD -> rule.add(FormLayout.Cell.field("value", 60, "Value"));
                case CHOICE -> rule.add(FormLayout.Cell.choice("value", pw, "Tier", "Tier",
                        form.tiers().isEmpty() ? List.of("(no tiers)") : form.tiers()));
                case BUTTON -> rule.add(FormLayout.Cell.button("value", pw, "Value",
                        TextFit.clip(form.valueText(), pw - Layout.LABEL_MARGIN, m)));
            }
            if (form.hasEffect()) {
                rule.add(FormLayout.Cell.button("effect", pw, "Effect",
                        TextFit.clip(form.effectText(), pw - Layout.LABEL_MARGIN, m)));
                rule.add(FormLayout.Cell.field("amplifier", 54, "Amplifier"));
            }
            bar.add(ListLayout.Action.of("add", form.addLabel(), Grid.buttonW("+ Add rule")).asMain());
            bar.add(ListLayout.Action.of("edit", "Edit", 34));
            bar.add(ListLayout.Action.of("remove", "Remove"));
        }
        bar.add(ListLayout.Action.of("remove_stage", "Remove stage"));

        List<String> hints = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), ListScreenLayouts.HELP_LINES, m));
        while (hints.size() < ListScreenLayouts.HELP_LINES) {
            hints.add("");
        }
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, top, rule, bar, hints, footer, empty)
                .withUnderTop(stage));
    }
}
