package com.strata.client.widget;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.screen.RegistryPickerScreen;
import com.strata.game.RegistryQuery;
import com.strata.model.ValueType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** THE VALUE CONTROL OF A VALUE SHEET. */
public final class ValueInput {

    private final Font font;
    private final UnaryOperator<AbstractWidget> put;
    private final Consumer<AbstractWidget> drop;
    private final Supplier<RegistryAccess> registries;
    private final Runnable applyPick;

    private ValueType type = ValueType.TEXT;
    private AbstractWidget shown;
    private EditField field;
    private DropdownSelect choice;
    private String picked = "";
    private boolean active = true;

    /**
     * @param font the screen's font
     * @param put places a widget on the layout's value slot and adds it to the screen
     * @param drop takes a widget off the screen
     * @param registries the world's registries, or null when there is no world
     * @param applyPick what a pick from the picker does next: the sheet's Set value, or null
     */
    public ValueInput(Font font, UnaryOperator<AbstractWidget> put, Consumer<AbstractWidget> drop,
                      Supplier<RegistryAccess> registries, Runnable applyPick) {
        this.font = font;
        this.put = put;
        this.drop = drop;
        this.registries = registries;
        this.applyPick = applyPick;
    }

    /** The type shown now. */
    public ValueType type() {
        return type;
    }

    /** Rebuilds the control for a row of this type, showing the row's current value. */
    public void show(ValueType next, String current) {
        if (shown != null) {
            drop.accept(shown);
        }
        type = next == null ? ValueType.TEXT : next;
        String text = current == null ? "" : current;
        field = null;
        choice = null;
        picked = text;
        switch (type.shape()) {
            case BOOL, ENUM -> {
                List<String> options = type.choices();
                // The stored word may differ in case from the option ("On", "on").
                String match = options.stream().filter(o -> o.equalsIgnoreCase(text.strip())).findFirst()
                        .orElse(options.isEmpty() ? "" : options.get(0));
                choice = new DropdownSelect(0, 0, 0, 0, "", options, match);
                // The words stay as stored: "true", "pickaxe".
                choice.setWords(s -> s);
                shown = put.apply(choice.getWidget());
                choice.fitTo(font::width, shown.getWidth() - com.strata.ui.screen.ListLayout.CHOICE_MARGIN);
            }
            case REGISTRY -> {
                Button button = Button.builder(Component.literal(""), b -> openPicker()).build();
                shown = put.apply(button);
                label(button);
            }
            default -> {
                field = new EditField(font, 0, 0, 0, 0, "Value", text);
                if (type.shape() == ValueType.Shape.WHOLE || type.shape() == ValueType.Shape.NUMBER) {
                    ValueType t = type;
                    field.setValidator((EditField.Check) s -> s.isEmpty() ? null : t.problem(s));
                }
                shown = put.apply(field.getWidget());
            }
        }
        shown.active = active;
    }

    /** What the control holds: the picked word, the picked id, or the typed text. */
    public String value() {
        if (choice != null) {
            return choice.getSelected();
        }
        if (field != null) {
            return field.getValue();
        }
        return picked;
    }

    /** True while the control's value is accepted (a field's check). */
    public boolean valid() {
        return field == null || field.isValid();
    }

    /** Sets the shown value (a row was selected, the text a screen keeps over a rebuild). */
    public void setValue(String text) {
        show(type, text);
    }

    /** Usable or greyed. */
    public void setActive(boolean usable) {
        active = usable;
        if (shown != null) {
            shown.active = usable;
        }
    }

    private void label(Button button) {
        int room = Math.max(8, button.getWidth() - com.strata.ui.screen.ListLayout.CHOICE_MARGIN);
        String words = picked.isBlank() ? "Pick..." : picked;
        button.setMessage(Component.literal(com.strata.ui.TextFit.clip(words, room, font::width)));
        button.setTooltip(Tooltip.create(Component.literal(picked.isBlank()
                ? "Open the list to pick one" : picked + ". Open the list to pick another.")));
    }

    private void openPicker() {
        ValueType.Registry kind = type.kind();
        RegistryAccess regs = registries == null ? null : registries.get();
        List<RegistryQuery.Option> options = RegistryQuery.forKind(kind, regs);
        if (kind == ValueType.Registry.LIVING_MOB || kind == ValueType.Registry.ENTITY) {
            options = RegistryPickerScreen.withRecent(options, com.strata.client.util.RecentEdits.recentEntities());
        }
        Button button = (Button) shown;
        // A sound or texture can be imported from here: "+ Add custom..." first in the list.
        boolean custom = kind == ValueType.Registry.SOUND || kind == ValueType.Registry.TEXTURE;
        if (custom) {
            List<RegistryQuery.Option> withAdd = new java.util.ArrayList<>();
            withAdd.add(RegistryQuery.addCustomOption());
            withAdd.addAll(options);
            options = withAdd;
        }
        RegistryPickerScreen picker = new RegistryPickerScreen(RegistryQuery.question(kind), options, id -> {
            picked = id;
            label(button);
            if (applyPick != null) {
                applyPick.run();
            }
        });
        if (kind == ValueType.Registry.SOUND) {
            picker.withPlay();
        }
        if (custom) {
            picker.withCustom(kind == ValueType.Registry.SOUND ? com.strata.domain.CustomAssetsOps.SOUND
                    : com.strata.domain.CustomAssetsOps.TEXTURE, () -> {
                List<RegistryQuery.Option> now = new java.util.ArrayList<>();
                now.add(RegistryQuery.addCustomOption());
                now.addAll(RegistryQuery.forKind(kind, regs));
                return now;
            });
        }
        ScreenNavigator.get().navigateTo(picker);
    }
}
