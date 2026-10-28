package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.domain.BatchOps;
import com.strata.domain.MobOps;
import com.strata.domain.EquipmentOps;
import com.strata.game.RegistryQuery;
import com.strata.ui.screen.FormScreenLayouts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** THE BATCH SCREEN. */
public class MobBatchScreen extends StrataScreen {

    private final List<MobOps.BatchMob> mobs;
    private final String where;

    private int group;
    private BatchOps.Field field = BatchOps.Field.SPAWNING;
    private String action = "On";
    private String item = "";
    private String slot = EquipmentOps.SLOT_CHOICES.getFirst();
    private String ruleType = com.strata.domain.SpawnRulesOps.TYPE_CHOICES.getFirst();
    private String ruleValue = "";
    private String ability = "";
    private String parameter = "";
    /** The attribute an Attributes batch sets; carried in the request's ability slot. */
    private String attributeId = "";
    private String countMin = "1";
    private String countMax = "1";
    private String chance = "100";
    private String amount = "1";
    private String ticks = "";
    private String radius = "";
    /** An effect class row's trigger, entity and duration. */
    private String trigger = "";
    private String entity = "";
    private String duration = "";

    private Button applyButton;

    /**
     * @param mobs the mob list's rows, in the scope it shows
     * @param where the list's scope, in words ("Global", "Tier: t1")
     */
    public MobBatchScreen(List<MobOps.BatchMob> mobs, String where) {
        super("Strata — Batch: " + where);
        this.mobs = List.copyOf(mobs);
        this.where = where;
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    private List<MobOps.BatchGroup> groups() {
        return MobOps.batchGroups();
    }

    /** "Monster (12)": a group and how many mobs of this list it holds. */
    private List<String> groupWords() {
        List<String> out = new ArrayList<>();
        for (MobOps.BatchGroup g : groups()) {
            out.add(g.label() + " (" + MobOps.inGroup(mobs, g).size() + ")");
        }
        return out;
    }

    private List<MobOps.BatchMob> chosen() {
        return MobOps.inGroup(mobs, groups().get(group));
    }

    private BatchOps.Request request() {
        return new BatchOps.Request(field, action, item, countMin, countMax, chance, slot, ruleType,
                ruleValue, field == BatchOps.Field.ATTRIBUTES ? attributeId : ability, parameter, amount, ticks,
                radius, trigger, entity, duration);
    }

    private FormScreenLayouts.BatchSection section() {
        return switch (field) {
            case LOOT -> FormScreenLayouts.BatchSection.LOOT;
            case SPAWN_RULES -> FormScreenLayouts.BatchSection.SPAWN_RULE;
            case ABILITIES -> FormScreenLayouts.BatchSection.ABILITY;
            case EQUIPMENT -> FormScreenLayouts.BatchSection.EQUIPMENT;
            case ATTRIBUTES -> FormScreenLayouts.BatchSection.ATTRIBUTE;
            case COEFFICIENT -> FormScreenLayouts.BatchSection.COEFFICIENT;
            case START_LEVEL -> FormScreenLayouts.BatchSection.START_LEVEL;
            case BOSS -> FormScreenLayouts.BatchSection.BOSS;
            default -> FormScreenLayouts.BatchSection.NONE;
        };
    }

    /** The attribute ids the Attributes batch offers. */
    private static List<String> attributeChoices() {
        return RegistryQuery.attributes().stream().map(RegistryQuery.Option::id).toList();
    }

    private com.strata.ui.Layout layout() {
        String problem = chosen().isEmpty() ? "There are no mobs of that group in this list"
                : BatchOps.problem(request());
        List<String> notes = List.of(
                com.strata.ui.Words.count(chosen().size(), "mob", "mobs") + " of this list: " + groups().get(group).label() + " in " + where + ".",
                problem == null ? "Apply asks first. Undo puts the whole batch back." : "Needs: " + problem);
        com.strata.ui.Layout built = FormScreenLayouts.batch(frame(), this.font::width, title(), groupWords(),
                BatchOps.FIELDS, BatchOps.actionsFor(field), section(), BatchOps.adds(action),
                item.isEmpty() ? "Pick an item..." : Labels.itemName(item), EquipmentOps.SLOT_CHOICES,
                com.strata.domain.SpawnRulesOps.TYPE_CHOICES,
                com.strata.domain.SpawnRulesOps.valueChoices(ruleType), structureValue() ? (ruleValue.isEmpty()
                        ? "Pick a structure..." : Labels.pretty(ruleValue)) : null,
                field == BatchOps.Field.ATTRIBUTES ? attributeChoices() : com.strata.domain.AbilitiesOps.abilityChoices(),
                parameterText(),
                com.strata.model.EffectClass.of(ability) != null ? triggerIds() : List.of(),
                entity.isEmpty() ? "Pick an entity..." : entity, notes, problem == null);
        useLayout(built);
        shown = built;
        return built;
    }

    /** The layout as last built: at init, and at each typed change (#refreshApply). */
    private com.strata.ui.Layout shown;

    private static List<String> triggerIds() {
        return com.strata.domain.TriggerOps.all().stream().map(com.strata.domain.TriggerOps.Trigger::id).toList();
    }

    private String parameterText() {
        var kind = com.strata.domain.AbilitiesOps.parameterKind(ability);
        if (kind == com.strata.domain.AbilitiesOps.ParameterKind.NONE) {
            return "Parameter: none needed";
        }
        return parameter.isEmpty() ? ("Pick a " + labelFor(kind) + "...") : parameter;
    }

    private String labelFor(com.strata.domain.AbilitiesOps.ParameterKind kind) {
        return switch (kind) {
            case EFFECT -> "effect";
            case ATTRIBUTE -> "attribute";
            case ENTITY -> "entity";
            default -> "damage type";
        };
    }

    @Override
    protected void init() {
        super.init();
        if (field == BatchOps.Field.ATTRIBUTES) {
            List<String> attrs = attributeChoices();
            if (attributeId.isEmpty() && !attrs.isEmpty()) {
                attributeId = attrs.getFirst();
            }
        } else if (ability.isEmpty() && !com.strata.domain.AbilitiesOps.abilityChoices().isEmpty()) {
            ability = com.strata.domain.AbilitiesOps.abilityChoices().getFirst();
        }
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        applyButton = Button.builder(Component.literal("Apply"), b -> confirm()).build();
        placeAll(layout, new String[]{"save"}, applyButton);

        List<String> words = groupWords();
        choice("Mobs", words, words.get(group), layout, "group", s -> group = Math.max(0, words.indexOf(s)));
        choice("Change", BatchOps.FIELDS, field.word, layout, "field", s -> {
            field = BatchOps.Field.of(s);
            action = BatchOps.actionsFor(field).getFirst();
        });
        choice("Action", BatchOps.actionsFor(field), action, layout, "action", s -> action = s);

        // The change's own controls (FormScreenLayouts#batch places only its section's).
        button(layout, "item", () -> ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick an item",
                RegistryQuery.itemsAndVariants(), id -> item = id)));
        button(layout, "parameter", this::pickParameter);
        text(layout, "min", "Min", countMin, s -> countMin = s);
        text(layout, "max", "Max", countMax, s -> countMax = s);
        text(layout, "chance", "Chance %", chance, s -> chance = s);
        text(layout, "amount", "Amount", amount, s -> amount = s);
        text(layout, "ticks", "Ticks", ticks, s -> ticks = s);
        text(layout, "radius", "Radius", radius, s -> radius = s);
        text(layout, "duration", "Duration", duration, s -> duration = s);
        button(layout, "entity", () -> ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Which entity?",
                RegistryQuery.entities(), id -> entity = id)));
        if (com.strata.model.EffectClass.of(ability) != null) {
            if (trigger.isEmpty()) {
                trigger = triggerIds().getFirst();
            }
            choice("Trigger", triggerIds(), trigger, layout, "trigger", s -> trigger = s);
        }
        text(layout, "condValue", "Value", ruleValue, s -> ruleValue = s);
        // A structure is picked, as the spawn rules screen does, not typed.
        if (structureValue()) {
            button(layout, "condValue", this::pickStructure);
        }
        choice("Slot", EquipmentOps.SLOT_CHOICES, slot, layout, "slot", s -> slot = s);
        choice("Rule", com.strata.domain.SpawnRulesOps.TYPE_CHOICES, ruleType, layout, "condType", s -> {
            ruleType = s;
            List<String> w = com.strata.domain.SpawnRulesOps.valueChoices(s);
            ruleValue = w == null ? "" : w.getFirst();
        });
        List<String> values = com.strata.domain.SpawnRulesOps.valueChoices(ruleType);
        if (values != null) {
            if (!values.contains(ruleValue)) {
                ruleValue = values.getFirst();
            }
            choice("Value", values, ruleValue, layout, "condValue", s -> ruleValue = s);
        }
        if (field == BatchOps.Field.ATTRIBUTES) {
            choice("Attribute", attributeChoices(), attributeId, layout, "ability", s -> attributeId = s);
        } else {
            choice("Ability", com.strata.domain.AbilitiesOps.abilityChoices(), ability, layout, "ability", s -> {
                if (com.strata.domain.AbilitiesOps.parameterKind(s) != com.strata.domain.AbilitiesOps.parameterKind(ability)) {
                    parameter = "";
                }
                ability = s;
            });
        }
        applyButton.active = layout.get("save") != null && layout.activeOf("save");
    }

    /** A dropdown on its box, if the layout has it; a change lays the screen out again. */
    private DropdownSelect choice(String name, List<String> options, String value, com.strata.ui.Layout layout,
                                  String id, java.util.function.Consumer<String> onChange) {
        if (layout.get(id) == null || options.isEmpty()) {
            return null;
        }
        DropdownSelect d = new DropdownSelect(0, 0, 0, 0, name, options,
                options.contains(value) ? value : options.getFirst());
        d.setOnChange(s -> {
            onChange.accept(s);
            deferTo(this::rebuildWidgets);
        });
        addRenderableWidget(place(d.getWidget(), layout.get(id)));
        return d;
    }

    private void button(com.strata.ui.Layout layout, String id, Runnable onPress) {
        if (layout.get(id) != null) {
            addRenderableWidget(place(Button.builder(Component.literal(layout.textOf(id)), b -> onPress.run())
                    .build(), layout.get(id)));
        }
    }

    private void text(com.strata.ui.Layout layout, String id, String label, String value,
                      java.util.function.Consumer<String> onEdit) {
        if (layout.get(id) == null || layout.get(id).kind() != com.strata.ui.Layout.Kind.FIELD) {
            return;
        }
        EditField f = new EditField(this.font, 0, 0, 0, 0, label, value);
        f.setResponder(s -> {
            onEdit.accept(s);
            refreshApply();
        });
        addRenderableWidget(place(f.getWidget(), layout.get(id)));
    }

    /** Apply is usable while the change is whole; the note under the fields says what it needs. */
    private void refreshApply() {
        if (applyButton != null) {
            applyButton.active = !chosen().isEmpty() && BatchOps.problem(request()) == null;
        }
        // The notes say what the change needs now.
        layout();
    }

    /** The rule chosen is "structure": its value is picked from the structures, not typed. */
    private boolean structureValue() {
        return "structure".equals(ruleType) && BatchOps.adds(action)
                && com.strata.domain.SpawnRulesOps.valueChoices(ruleType) == null;
    }

    private void pickStructure() {
        // RegistryQuery#structuresFor picks the registries: the open world's, else the startup ones.
        var structures = RegistryQuery.structuresFor(this.minecraft != null && this.minecraft.level != null
                ? worldRegistries() : null);
        if (structures.isEmpty()) {
            refuse("No structures: the game data could not be read at game start (see the log)");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick a structure", structures,
                id -> ruleValue = id));
    }

    private void pickParameter() {
        var kind = com.strata.domain.AbilitiesOps.parameterKind(ability);
        if (kind == com.strata.domain.AbilitiesOps.ParameterKind.NONE) {
            return;
        }
        var options = switch (kind) {
            case EFFECT -> RegistryQuery.effects();
            case ATTRIBUTE -> RegistryQuery.attributes();
            case ENTITY -> RegistryQuery.entities();
            default -> RegistryQuery.damageTypesFor(this.minecraft != null && this.minecraft.level != null
                    ? worldRegistries() : null);
        };
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "Which " + labelFor(kind) + "?",
                options, picked -> parameter = picked));
    }

    /** Asks, naming the change and how many mobs, then applies it. */
    private void confirm() {
        List<MobOps.BatchMob> targets = chosen();
        BatchOps.Request r = request();
        String problem = targets.isEmpty() ? "There are no mobs of that group in this list" : BatchOps.problem(r);
        if (problem != null) {
            refuse(problem);
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this,
                BatchOps.question(r, targets.size(), groups().get(group).label(), where), "Apply",
                () -> {
                    if (report(BatchOps.apply(targets, r))) {
                        // Every mob it changed goes to the top of its list's Recent group.
                        targets.forEach(m -> com.strata.client.util.RecentEdits.touch(m.entity(), m.variantName()));
                        savedHere();
                    }
                }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = shown != null ? shown : layout();
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.kind() == com.strata.ui.Layout.Kind.TEXT && !e.id().equals("title")) {
                text(graphics, e, e.id().equals("note1") && e.text().startsWith("Needs") ? Palette.ERROR : Palette.LABEL);
            }
        }
    }
}

