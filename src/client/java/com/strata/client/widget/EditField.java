package com.strata.client.widget;

import com.strata.ui.Palette;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Labeled text input field. */
public class EditField {

    /** A field's check that says why: the reason a value is refused, or null when it is accepted. */
    @FunctionalInterface
    public interface Check extends Predicate<String> {
        String problem(String value);

        @Override
        default boolean test(String value) {
            return problem(value) == null;
        }
    }

    private final EditBox editBox;
    private Predicate<String> validator;
    private boolean valid = true;
    /** What the field is for, shown as its tooltip while its value is accepted. */
    private String help;

    public EditField(Font font, int x, int y, int width, int height, String label, String initial) {
        this.editBox = new EditBox(font, x, y, width, height, Component.literal(label));
        this.editBox.setMaxLength(256);
        if (initial != null) {
            this.editBox.setValue(initial);
        }
    }

    public EditBox getWidget() {
        return editBox;
    }

    public String getValue() {
        return editBox.getValue();
    }

    public void setValue(String value) {
        editBox.setValue(value);
    }

    public void setValidator(Predicate<String> validator) {
        this.validator = validator;
        validate();
    }

    public void setResponder(Consumer<String> responder) {
        editBox.setResponder(s -> {
            validate();
            responder.accept(s);
        });
    }

    public boolean isValid() {
        return valid;
    }

    /** Checks the value again, for a check that reads another field (Min and Max). */
    public void recheck() {
        validate();
    }

    private void validate() {
        if (validator != null) {
            String reason = validator instanceof Check c ? c.problem(editBox.getValue())
                    : validator.test(editBox.getValue()) ? null : "This value is not accepted here";
            valid = reason == null;
            editBox.setTextColor(valid ? Palette.FIELD : Palette.ERROR);
            showTip(valid ? help : reason);
        }
    }

    /** Sets what the field is for: its tooltip while its value is accepted. */
    public void setHelp(String help) {
        this.help = help;
        showTip(valid ? help : problem());
    }

    private void showTip(String text) {
        editBox.setTooltip(text == null ? null : net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(text)));
    }

    /** Why the value is refused, or null when it is accepted (a plain validator gives no reason). */
    public String problem() {
        return validator instanceof Check c ? c.problem(editBox.getValue()) : null;
    }

    /** A real, finite number. */
    public static Check numericValidator() {
        return s -> {
            if (s.isEmpty()) return null;
            try { return Double.isFinite(Double.parseDouble(s)) ? null : "A number, like 1.5"; }
            catch (NumberFormatException e) { return "A number, like 1.5"; }
        };
    }

    public static Check integerValidator() {
        return s -> {
            if (s.isEmpty()) return null;
            try { Integer.parseInt(s); return null; }
            catch (NumberFormatException e) { return "A whole number, like 3"; }
        };
    }

    /** A whole number that cannot be negative. */
    public static Check nonNegativeIntegerValidator() {
        return s -> {
            if (s.isEmpty()) return null;
            try { return Integer.parseInt(s) >= 0 ? null : "A whole number, 0 or more"; }
            catch (NumberFormatException e) { return "A whole number, 0 or more"; }
        };
    }

    /** A bounded decimal: digits with at most one dot, digits each side. */
    public static Check coefficientValidator() {
        return s -> {
            if (s.isEmpty()) return null;
            return s.matches("\\d+\\.\\d+") || s.matches("\\d+") ? null : "A number like 1 or 1.5";
        };
    }

    /** Free text that will be written into a CSV column. */
    public static Check csvTextValidator() {
        return s -> s.indexOf('\n') < 0 && s.indexOf('\r') < 0 ? null : "No line breaks";
    }
}
