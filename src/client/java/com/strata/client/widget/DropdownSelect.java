package com.strata.client.widget;

import com.strata.Strata;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * A dropdown: a button that shows its option, for a fixed list of options (tier assignment, override mode,
 * spawn category, and more).
 */
public class DropdownSelect {

    private static boolean warnedUnknown = false;

    /** Shows a dropdown's options: set by the screen that is open (StrataScreen#init). */
    private static Consumer<DropdownSelect> opener;
    /** Every dropdown by its button, for the right-click. */
    private static final java.util.Map<Button, DropdownSelect> BY_BUTTON = new java.util.WeakHashMap<>();

    /** Sets what shows a dropdown's options; null to cycle. */
    public static void setOpener(Consumer<DropdownSelect> show) {
        opener = show;
    }

    /** The dropdown this button belongs to, or null. */
    public static DropdownSelect of(Button button) {
        return BY_BUTTON.get(button);
    }

    private final String label;
    private final List<String> options;
    private int selectedIndex;
    private final Button button;
    private Consumer<String> onChange;
    /** The text is cut to this room, when set: for options that are data (a mod's id). */
    private com.strata.ui.TextFit.Measure fitMeasure;
    private int fitRoom;

    public DropdownSelect(int x, int y, int width, int height, String label,
                          List<String> options, String initial) {
        this.label = label;
        this.options = List.copyOf(options);
        int idx = this.options.indexOf(initial);
        if (idx < 0 && !this.options.isEmpty()) {
            warnOnce("Strata: dropdown '" + label + "' got unknown value '"
                    + initial + "', showing its first option");
            idx = 0;
        }
        this.selectedIndex = Math.max(0, idx);
        this.button = Button.builder(displayText(), b -> open())
                .pos(x, y).size(width, height).build();
        BY_BUTTON.put(button, this);
    }

    /** A MENU DROPDOWN: it keeps no choice. */
    private Consumer<String> menu;

    /** Makes this a menu: a pick calls this with the option, and nothing is selected. */
    public void setMenu(Consumer<String> onPick) {
        this.menu = onPick;
        this.selectedIndex = -1;
        button.setMessage(displayText());
    }

    private void open() {
        if (opener != null && (options.size() > 1 || (menu != null && !options.isEmpty()))) {
            opener.accept(this);
        } else {
            cycle();
        }
    }

    /** The options, as stored. */
    public List<String> options() {
        return options;
    }

    /** The label shown before the option ("" for none). */
    public String label() {
        return label;
    }

    /** The index of the option shown. */
    public int selectedIndex() {
        return selectedIndex;
    }

    /** Picks an option by its index, as a click on it in the list does. */
    public void pick(int index) {
        if (index < 0 || index >= options.size()) return;
        if (menu != null) {
            menu.accept(options.get(index));
            return;
        }
        boolean changed = index != selectedIndex;
        selectedIndex = index;
        button.setMessage(displayText());
        if (changed && onChange != null) {
            onChange.accept(getSelected());
        }
    }

    /** Steps back one option: the right-click. */
    public void stepBack() {
        if (options.isEmpty() || menu != null) return;
        pick((selectedIndex - 1 + options.size()) % options.size());
    }

    private static void warnOnce(String message) {
        if (!warnedUnknown) {
            warnedUnknown = true;
            Strata.LOGGER.warn(message);
        }
    }

    public Button getWidget() {
        return button;
    }

    public String getSelected() {
        return options.isEmpty() ? "" : options.get(selectedIndex);
    }

    /**
     * Cuts the text with "..." to room, as the UI layer's layout of this choice does
     * (ListLayout.Action#dataChoice).
     */
    public void fitTo(com.strata.ui.TextFit.Measure measure, int room) {
        this.fitMeasure = measure;
        this.fitRoom = room;
        button.setMessage(displayText());
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    /** How an option shows: a table code as its word (ChoiceWords), unless a screen sets another. */
    private java.util.function.Function<String, String> words = com.strata.ui.ChoiceWords::word;

    /**
     * Sets how the options show: the picker's group filter shows a group as its heading does ("Minecraft",
     * not "minecraft").
     */
    public void setWords(java.util.function.Function<String, String> words) {
        this.words = words;
        button.setMessage(displayText());
    }

    /** An option as it shows, on the button and in the open list. */
    public String word(String option) {
        return words.apply(option);
    }

    public void setSelected(String value) {
        int idx = options.indexOf(value);
        if (idx >= 0) {
            selectedIndex = idx;
            button.setMessage(displayText());
        }
    }

    private void cycle() {
        if (options.isEmpty()) return;
        if (menu != null) {
            // No screen to show the list: a click takes the first option, as a menu's default.
            menu.accept(options.get(0));
            return;
        }
        selectedIndex = (selectedIndex + 1) % options.size();
        button.setMessage(displayText());
        if (onChange != null) {
            onChange.accept(getSelected());
        }
    }

    private Component displayText() {
        if (menu != null) {
            return Component.literal(label);
        }
        String value = options.isEmpty() ? "—" : options.get(selectedIndex);
        // One format, from the UI layer, so the fit gate measures what is drawn.
        String text = com.strata.ui.Layout.choiceText(label, word(value));
        return Component.literal(fitMeasure == null ? text : com.strata.ui.TextFit.clip(text, fitRoom, fitMeasure));
    }
}
