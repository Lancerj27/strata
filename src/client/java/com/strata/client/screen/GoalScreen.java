package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.ToggleSwitch;
import com.strata.domain.GoalsOps;
import com.strata.game.RegistryQuery;
import com.strata.model.GoalRow;
import com.strata.model.GoalCondition;
import com.strata.model.GoalType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One goal of an AI list: its priority, every setting of how the mob carries it out, its condition, and its
 * switch.
 */
public class GoalScreen extends StrataScreen {

    private static final String ALWAYS = GoalCondition.Type.ALWAYS.word();

    private final GoalRow original;
    private final Consumer<GoalRow> onDone;
    private final GoalType.Type type;
    /** The values as edited, kept across init (a picker return). */
    private final Map<String, String> values = new LinkedHashMap<>();
    private String priority;
    private String condition;
    private String conditionValue;
    private boolean enabled;
    private final List<EditField> checked = new ArrayList<>();

    public GoalScreen(GoalRow row, Consumer<GoalRow> onDone) {
        super("Strata — Goal: " + row.name());
        this.original = row;
        this.onDone = onDone;
        this.type = row.game() ? null : GoalType.of(row.goal());
        if (type != null) {
            values.putAll(GoalType.withDefaults(type, row.settings()));
        }
        this.priority = String.valueOf(row.priority());
        this.condition = row.condition().type() == GoalCondition.Type.ALWAYS ? ALWAYS : row.condition().type().word();
        this.conditionValue = row.condition().type().takesValue() ? String.valueOf(row.condition().value()) : "";
        this.enabled = row.enabled();
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    /** The condition chosen takes a value. */
    private boolean conditionTakesValue() {
        GoalCondition.Type ct = GoalCondition.Type.parse(ALWAYS.equals(condition) ? "" : condition);
        return ct != null && ct.takesValue();
    }

    /**
     * The layout: the rows from the goal's type, the label beside or above each control as the width allows.
     */
    private com.strata.ui.Layout layout() {
        com.strata.ui.Layout layout = com.strata.ui.screen.MenuLayouts.goal(frame(), this.font::width, title(),
                com.strata.ui.screen.MenuLayouts.goalRows(type, key -> GoalsOps.targetLabel(values.getOrDefault(key, "")),
                        conditionTakesValue()),
                original.game()
                        ? "The mob's own goal: its priority, condition, and switch can change."
                        : (original.target() ? "Target goal: it picks a target." : "Goal: it acts."));
        useLayout(layout);
        return layout;
    }

    @Override
    protected void init() {
        super.init();
        checked.clear();
        com.strata.ui.Layout layout = layout();
        // "Done", not "Save": the goal goes back to the list, and the list's Save writes it.
        placeAll(layout, new String[]{"save"}, Button.builder(Component.literal(layout.textOf("save")),
                b -> done()).build());

        // The reason in the red tooltip.
        EditField prio = field(0, 0, 0, 0, "", priority,
                (EditField.Check) s -> wholeIn(s, 0, 99) ? null : "A whole number from 0 to 99", null);
        place(prio.getWidget(), layout.get("priority"));
        prio.setResponder(s -> priority = s.strip());
        checked.add(prio);

        if (type != null) {
            for (GoalType.Param param : type.params()) {
                control(param, layout);
            }
        }

        DropdownSelect when = new DropdownSelect(0, 0, 0, 0, "", GoalCondition.CHOICES, condition);
        when.setOnChange(c -> {
            condition = c;
            clearWidgets();
            init();
        });
        addRenderableWidget(place(when.getWidget(), layout.get("when")));
        when.fitTo(this.font::width, layout.boxOf("when").w() - com.strata.ui.Layout.LABEL_MARGIN);
        if (conditionTakesValue()) {
            EditField v = field(0, 0, 0, 0, "", conditionValue, (EditField.Check) s -> GoalCondition.problem(condition, s), null);
            place(v.getWidget(), layout.get("whenValue"));
            v.setResponder(s -> conditionValue = s.strip());
            checked.add(v);
        }

        ToggleSwitch on = new ToggleSwitch(0, 0, 0, 0, "On", enabled);
        on.setOnChange(b -> enabled = b);
        addRenderableWidget(place(on.getWidget(), layout.get("on")));
    }

    /** One setting's control, by its kind, on the layout's box for it. */
    private void control(GoalType.Param param, com.strata.ui.Layout layout) {
        String id = "p." + param.key();
        com.strata.ui.Layout.Element e = layout.get(id);
        String value = values.get(param.key());
        switch (param.kind()) {
            case FLAG -> {
                ToggleSwitch t = new ToggleSwitch(0, 0, 0, 0, "", "true".equals(value));
                t.setOnChange(b -> values.put(param.key(), String.valueOf(b)));
                addRenderableWidget(place(t.getWidget(), e));
            }
            case TARGET -> {
                // Every living entity and every group the game's goals use, from the game layer: a picker,
                // not a cycle.
                addRenderableWidget(place(Button.builder(Component.literal(e.text()), b ->
                        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Target which?",
                                RegistryPickerScreen.plainOptions("target", GoalsOps.targetChoices()),
                                picked -> values.put(param.key(), picked)))).build(), e));
            }
            case PROJECTILE -> {
                // Every projectile the game and its mods register: a picker.
                addRenderableWidget(place(Button.builder(Component.literal(e.text()), b ->
                        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Fire which?",
                                com.strata.game.RegistryQuery.projectiles(this.minecraft.level),
                                picked -> values.put(param.key(), picked)))).build(), e));
            }
            case CHOICE -> {
                List<String> choices = new ArrayList<>(param.choices());
                if (!choices.contains(value)) {
                    choices.add(value);
                }
                DropdownSelect d = new DropdownSelect(0, 0, 0, 0, "", choices, value);
                d.setOnChange(v -> values.put(param.key(), v));
                addRenderableWidget(place(d.getWidget(), e));
                d.fitTo(this.font::width, e.box().w() - com.strata.ui.Layout.LABEL_MARGIN);
            }
            case ITEMS -> {
                // THE LIST IS NOT TYPED: the field shows the joined items, + Add item picks one onto the end,
                // and Remove takes the last off.
                EditField f = field(0, 0, 0, 0, "", value, (EditField.Check) s -> GoalType.problem(param, s), null);
                place(f.getWidget(), e);
                f.getWidget().setEditable(false);
                checked.add(f);
                placeAll(layout, new String[]{id + ".add", id + ".remove"},
                        Button.builder(Component.literal("+ Add item"), b -> pickItem(param.key())).build(),
                        Button.builder(Component.literal("Remove"), b -> {
                            values.put(param.key(), GoalType.withoutLastItem(values.getOrDefault(param.key(), "")));
                            clearWidgets();
                            init();
                        }).build());
            }
            default -> {
                EditField f = field(0, 0, 0, 0, "", value, (EditField.Check) s -> GoalType.problem(param, s), null);
                place(f.getWidget(), e);
                f.setResponder(s -> values.put(param.key(), s.strip()));
                checked.add(f);
            }
        }
    }
    private void pickItem(String key) {
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add which item?", RegistryQuery.items(), id -> {
            String now = values.getOrDefault(key, "");
            values.put(key, now.isBlank() ? id : now + "+" + id);
        }));
    }

    private static boolean wholeIn(String s, int min, int max) {
        try {
            int v = Integer.parseInt(s.strip());
            return v >= min && v <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private GoalRow edited() {
        String c = ALWAYS.equals(condition) ? "" : condition;
        return new GoalRow(original.target(), Integer.parseInt(priority), original.goal(), original.game(),
                Map.copyOf(values), GoalCondition.of(c, conditionValue), enabled);
    }

    private void done() {
        if (!checked.stream().allMatch(EditField::isValid)) {
            refuse("Fix the fields in red first");
            return;
        }
        GoalRow row = edited();
        String problem = row.problem();
        if (problem != null) {
            refuse(problem);
            return;
        }
        onDone.accept(row);
        ScreenNavigator.get().back();
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return checked.stream().allMatch(EditField::isValid) && wholeIn(priority, 0, 99) && !edited().equals(original);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.id().startsWith("label.")) {
                text(graphics, e, Palette.LABEL);
            }
        }
        text(graphics, layout.get("footer"), Palette.MUTED);
    }
}