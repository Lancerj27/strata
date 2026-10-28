package com.strata.ui;

import java.util.Map;

/** WHAT A STANDARD BUTTON DOES, IN ONE PLACE. */
public final class ButtonTips {

    private ButtonTips() {
    }

    private static final Map<String, String> TIPS = Map.ofEntries(
            Map.entry("Edit", "Open the selected row."),
            Map.entry("On / Off", "Switch the picked rows off, or on again. A row that is off keeps its values."),
            Map.entry("Restore", "Put the picked rows back to the game's values."),
            Map.entry("Restore all", "Put every change on this screen back to the game's values."),
            Map.entry("Duplicate", "Copy the selected row as a new one."),
            Map.entry("Remove", "Take the picked rows out of this list."),
            Map.entry("Remove element", "Take the selected element out of its list."),
            Map.entry("Set value", "Write the typed value into the selected row."),
            Map.entry("Clear", "Clear the search."),
            Map.entry("Save", "Save this form."),
            Map.entry("Edit picked", "Change one value on every picked mob at once."),
            Map.entry("Special spawns", "The game's own spawners: phantoms, patrols, cats, traders, and the rest."),
            Map.entry("Goal sets", "Named sets of goals that mobs can share."),
            Map.entry("Mob caps", "How many mobs of each category the world keeps at once."));

    /** The tooltip of a button with these words, or null for none. */
    public static String tip(String label) {
        if (label == null) {
            return null;
        }
        int bracket = label.indexOf(" (");
        return TIPS.get(bracket > 0 ? label.substring(0, bracket) : label);
    }
}
