package com.strata.client.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Boolean toggle button. */
public class ToggleSwitch {

    private boolean value;
    private final String label;
    private final Button button;
    private Consumer<Boolean> onChange;

    public ToggleSwitch(int x, int y, int width, int height, String label, boolean initial) {
        this.label = label;
        this.value = initial;
        this.button = Button.builder(displayText(), b -> toggle())
                .pos(x, y).size(width, height).build();
    }

    public Button getWidget() {
        return button;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
        button.setMessage(displayText());
    }

    public void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    private void toggle() {
        value = !value;
        button.setMessage(displayText());
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    private Component displayText() {
        // One format, from the UI layer, so the fit gate measures what is drawn.
        return Component.literal(com.strata.ui.Layout.toggleText(label, value));
    }
}
