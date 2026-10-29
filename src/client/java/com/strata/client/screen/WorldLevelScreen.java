package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.domain.WorldLevelOps;
import com.strata.domain.WriteReport;
import com.strata.game.RegistryQuery;
import com.strata.model.ValueType;
import com.strata.runtime.WorldLevel;
import com.strata.ui.Palette;
import com.strata.ui.screen.ListScreenLayouts;
import com.strata.ui.screen.WorldLevelLayout;
import com.strata.util.Labels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's level: a tab per stage (Start first, + Stage adds one to the right), the stage's form under the
 * tabs with Save stage, a rule row over the list (kind, value, and a locked area's effect and amplifier) with
 * + Add rule, and the stage's rules as the list, with Edit and Remove. As the Abilities editor works.
 */
public class WorldLevelScreen extends StrataScreen {

    private List<Integer> stages = List.of(0);
    /** The stage whose tab is open. */
    private int shown;
    /** The first tab drawn, when there are more tabs than fit. */
    private int firstTab;
    /** The stage the form fields were filled for, so another tab reloads them. */
    private int formStage = -1;
    private List<WorldLevel.Rule> rules = List.of();
    /** The tabs' words and the open stage's heading, read once per init: the render must not read the tables. */
    private List<String> tabLabels = List.of("Start");
    private String headingText = "";
    private BrowseList list;

    // The stage form.
    private EditField nameField;
    private DropdownSelect playersSelect;
    private DropdownSelect conditionsSelect;
    private EditField offsetField;
    private EditField coefficientField;
    private DropdownSelect modeSelect;
    private DropdownSelect holdSelect;
    private DropdownSelect sourceSelect;

    // The rule row, kept across init.
    private String kind = WorldLevel.KINDS.getFirst();
    private String value = "";
    private String effect = "";
    private String amplifier = "0";
    private EditField valueField;
    private DropdownSelect valueSelect;
    private EditField amplifierField;
    /** The rule Edit loaded into the row, or null while adding. */
    private WorldLevel.Rule editing;

    public WorldLevelScreen() {
        super("Strata — World level");
    }

    @Override
    protected void init() {
        super.init();
        stages = WorldLevelOps.stages();
        if (!stages.contains(shown)) {
            shown = 0;
        }
        rules = WorldLevelOps.rulesOf(shown);
        tabLabels = stages.stream().map(WorldLevelOps::tab).toList();
        headingText = WorldLevelOps.heading(shown);
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        value = valueField == null ? value : valueField.getValue();
        amplifier = amplifierField == null ? amplifier : amplifierField.getValue();

        // The stage form's values: as typed on this tab, or the stored ones on another.
        String keepName = nameField == null ? "" : nameField.getValue();
        String keepPlayers = playersSelect == null ? "any" : playersSelect.getSelected();
        String keepConditions = conditionsSelect == null ? "all" : conditionsSelect.getSelected();
        String keepOffset = offsetField == null ? "0" : offsetField.getValue();
        String keepCoefficient = coefficientField == null ? "0" : coefficientField.getValue();
        String keepMode = modeSelect == null ? WorldLevel.ADD : modeSelect.getSelected();
        String keepHold = holdSelect == null ? WorldLevel.KEEP : holdSelect.getSelected();
        String keepSource = sourceSelect == null ? WorldLevel.HIGHEST_EVER : sourceSelect.getSelected();
        WorldLevelOps.StageForm form = formStage == shown && offsetField != null
                ? new WorldLevelOps.StageForm(keepName, keepPlayers, keepConditions, keepOffset, keepCoefficient, keepMode,
                        keepHold, keepSource)
                : WorldLevelOps.formFor(shown);
        formStage = shown;

        ListScreenLayouts.StageTabs tabs = tabs();
        firstTab = tabs.first();
        com.strata.ui.Layout layout = layout(tabs);
        placeUndo(layout);

        // THE TABS.
        List<String> ids = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        if (tabs.first() > 0) {
            ids.add("tab_back");
            buttons.add(Button.builder(Component.literal("<"), b -> {
                firstTab = Math.max(0, firstTab - tabs.count());
                rebuildWidgets();
            }).build());
        }
        for (int i = tabs.first(); i < tabs.first() + tabs.count(); i++) {
            int stage = stages.get(i);
            ids.add("tab" + i);
            buttons.add(Button.builder(Component.literal(tabLabels.get(i)), b -> open(stage)).build());
        }
        if (tabs.first() + tabs.count() < stages.size()) {
            ids.add("tab_more");
            buttons.add(Button.builder(Component.literal(">"), b -> {
                firstTab = Math.min(stages.size() - 1, firstTab + tabs.count());
                rebuildWidgets();
            }).build());
        }
        ids.add("add_stage");
        buttons.add(Button.builder(Component.literal("+ Stage"), b -> {
            int next = WorldLevelOps.nextStage();
            if (report(WorldLevelOps.addStage())) {
                firstTab = Integer.MAX_VALUE;
                open(next);
            }
        }).build());
        placeAll(layout, ids.toArray(new String[0]), buttons.toArray(new Button[0]));
        int shownIndex = stages.indexOf(shown);
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals("tab" + shownIndex)) {
                buttons.get(i).active = false;
            }
        }

        // THE STAGE FORM.
        if (shown != 0) {
            nameField = new EditField(this.font, 0, 0, 0, 0, "Name", form.name());
            placeIf(nameField.getWidget(), layout, "name");
            playersSelect = new DropdownSelect(0, 0, 0, 0, "", WorldLevelOps.PLAYER_CHOICES, form.players());
            placeIf(playersSelect.getWidget(), layout, "players");
            conditionsSelect = new DropdownSelect(0, 0, 0, 0, "", WorldLevelOps.CONDITION_CHOICES, form.conditions());
            placeIf(conditionsSelect.getWidget(), layout, "conditions");
        } else {
            nameField = new EditField(this.font, 0, 0, 0, 0, "Name", "");
            playersSelect = null;
            conditionsSelect = null;
        }
        offsetField = new EditField(this.font, 0, 0, 0, 0, "Offset", form.offset());
        offsetField.setValidator(EditField.numericValidator());
        placeIf(offsetField.getWidget(), layout, "offset");
        coefficientField = new EditField(this.font, 0, 0, 0, 0, "Coefficient", form.coefficient());
        coefficientField.setValidator(EditField.numericValidator());
        placeIf(coefficientField.getWidget(), layout, "coefficient");
        modeSelect = new DropdownSelect(0, 0, 0, 0, "", WorldLevel.MODES, form.mode());
        placeIf(modeSelect.getWidget(), layout, "mode");
        holdSelect = null;
        sourceSelect = null;
        if (shown == 0) {
            sourceSelect = new DropdownSelect(0, 0, 0, 0, "", WorldLevel.LEVEL_SOURCES, form.levelSource());
            placeIf(sourceSelect.getWidget(), layout, "level_source");
        } else {
            holdSelect = new DropdownSelect(0, 0, 0, 0, "", WorldLevel.HOLDS, form.hold());
            placeIf(holdSelect.getWidget(), layout, "hold");
        }
        placeIf(Button.builder(Component.literal("Save stage"), b -> saveStage()).build(), layout, "save_stage");

        // THE RULE ROW.
        valueField = null;
        valueSelect = null;
        amplifierField = null;
        if (shown != 0) {
            DropdownSelect kindSelect = new DropdownSelect(0, 0, 0, 0, "", WorldLevel.KINDS, kind);
            kindSelect.setWords(k -> k);
            kindSelect.setOnChange(k -> {
                // Another kind takes another kind of value: the old one goes.
                if (WorldLevelOps.valueType(k).shape() != WorldLevelOps.valueType(kind).shape()
                        || WorldLevelOps.valueType(k).kind() != WorldLevelOps.valueType(kind).kind()) {
                    value = "";
                }
                kind = k;
                // The old boxes must not write their text back over the new value on the rebuild.
                dropRow();
                rebuildWidgets();
            });
            placeIf(kindSelect.getWidget(), layout, "kind");

            ValueType type = WorldLevelOps.valueType(kind);
            switch (type.shape()) {
                case ENUM -> {
                    List<String> tiers = type.choices().isEmpty() ? List.of("(no tiers)") : type.choices();
                    valueSelect = new DropdownSelect(0, 0, 0, 0, "", tiers, tiers.contains(value) ? value : tiers.getFirst());
                    valueSelect.setOnChange(v -> value = v);
                    value = valueSelect.getSelected();
                    placeIf(valueSelect.getWidget(), layout, "value");
                }
                case REGISTRY -> placeIf(Button.builder(Component.literal(layout.textOf("value")),
                        b -> pick(type.kind(), picked -> value = picked)).build(), layout, "value");
                default -> {
                    valueField = new EditField(this.font, 0, 0, 0, 0, "Value", value);
                    valueField.setValidator(EditField.nonNegativeIntegerValidator());
                    placeIf(valueField.getWidget(), layout, "value");
                }
            }
            if (WorldLevelOps.hasEffect(kind)) {
                placeIf(Button.builder(Component.literal(layout.textOf("effect")),
                        b -> pick(ValueType.Registry.EFFECT, picked -> effect = picked)).build(), layout, "effect");
                amplifierField = new EditField(this.font, 0, 0, 0, 0, "Amplifier", amplifier);
                amplifierField.setValidator(EditField.nonNegativeIntegerValidator());
                placeIf(amplifierField.getWidget(), layout, "amplifier");
            }
        }

        // THE LIST AND ITS BAR.
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("WorldLevelScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        Button removeStage = Button.builder(Component.literal("Remove stage"), b -> confirmRemoveStage()).build();
        if (shown != 0) {
            Button add = Button.builder(Component.literal(editing == null ? "+ Add rule" : "Save rule"), b -> add()).build();
            Button edit = Button.builder(Component.literal("Edit"), b -> edit()).build();
            Button remove = Button.builder(Component.literal("Remove"), b -> confirmRemove()).build();
            placeAll(layout, new String[]{"add", "edit", "remove", "remove_stage"}, add, edit, remove, removeStage);
            needsSelection(() -> selectedRule() != null && !list.manyPicked(), edit);
            needsSelection(() -> !picked().isEmpty(), remove);
        } else {
            placeAll(layout, new String[]{"remove_stage"}, removeStage);
            removeStage.active = false;
        }
        rebuild();
        list.restore(keepList);
    }

    /** Keeps what the rule row's fields hold, before init builds them again. */
    private void keepRow() {
        if (valueField != null) {
            value = valueField.getValue();
        }
        if (amplifierField != null) {
            amplifier = amplifierField.getValue();
        }
    }

    /** Opens another stage's tab. */
    private void open(int stage) {
        shown = stage;
        editing = null;
        rebuildWidgets();
    }

    private ListScreenLayouts.StageTabs tabs() {
        return ListScreenLayouts.stageTabs(frame(), this.font::width, tabLabels, stages.indexOf(shown), firstTab);
    }

    /** The layout for the tab, the form and the rule row as they stand. */
    private com.strata.ui.Layout layout(ListScreenLayouts.StageTabs tabs) {
        ValueType type = WorldLevelOps.valueType(kind);
        WorldLevelLayout.ValueCell cell = switch (type.shape()) {
            case ENUM -> WorldLevelLayout.ValueCell.CHOICE;
            case REGISTRY -> WorldLevelLayout.ValueCell.BUTTON;
            default -> WorldLevelLayout.ValueCell.FIELD;
        };
        String valueText = pretty(cell == WorldLevelLayout.ValueCell.BUTTON ? value : "");
        String effectText = pretty(effect);
        WorldLevelLayout.Form form = new WorldLevelLayout.Form(shown == 0, WorldLevel.KINDS, kind, cell, valueText,
                type.shape() == ValueType.Shape.ENUM ? type.choices() : List.of(), WorldLevelOps.hasEffect(kind),
                effectText, editing == null ? "+ Add rule" : "Save rule");
        String footer = headingText + ". The world is at stage " + WorldLevel.stage() + "; /strata stage <n> sets it.";
        String empty = shown == 0 ? "The start has no rules: it is where every world begins." : "No rules yet. Fill the row above, then + Add rule.";
        return WorldLevelLayout.build(frame(), this.font::width, title(), tabs, form, WorldLevelOps.HINT, footer, empty);
    }

    /** An id's words for a picker button, "Pick..." for none. */
    private static String pretty(String id) {
        net.minecraft.resources.ResourceLocation rl = id.isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        return rl == null ? (id.isEmpty() ? "Pick..." : id) : Labels.pretty(rl);
    }

    /** Adds the widget at its cell when the layout has that cell. */
    private <T extends net.minecraft.client.gui.components.AbstractWidget> T placeIf(T widget, com.strata.ui.Layout layout, String id) {
        if (layout.get(id) != null) {
            addRenderableWidget(place(widget, layout.get(id)));
        }
        return widget;
    }

    /** Opens the list of a registry; the pick lands in the row and the row is laid out again. */
    private void pick(ValueType.Registry registry, java.util.function.Consumer<String> onPick) {
        List<RegistryQuery.Option> options = RegistryQuery.forKind(registry, worldRegistries());
        if (options.isEmpty()) {
            refuse(RegistryQuery.question(registry) + ": the list is empty here; open a world first");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(RegistryQuery.question(registry), options, picked -> {
            onPick.accept(picked);
            rebuildWidgets();
        }));
    }

    // ---- the stage form ----

    private WorldLevelOps.StageForm typedForm() {
        return new WorldLevelOps.StageForm(nameField == null ? "" : nameField.getValue(),
                playersSelect == null ? "any" : playersSelect.getSelected(),
                conditionsSelect == null ? "all" : conditionsSelect.getSelected(),
                offsetField.getValue(), coefficientField.getValue(), modeSelect.getSelected(),
                holdSelect == null ? WorldLevel.KEEP : holdSelect.getSelected(),
                sourceSelect == null ? WorldLevel.HIGHEST_EVER : sourceSelect.getSelected());
    }

    private void saveStage() {
        if (report(WorldLevelOps.saveStage(shown, typedForm()))) {
            // The tab may carry a new name.
            rebuildWidgets();
        }
    }

    // ---- the rule row ----

    /** The rule the row describes. */
    private WorldLevel.Rule typedRule() {
        keepRow();
        return WorldLevelOps.rule(shown, kind, value, effect, amplifier);
    }

    private void add() {
        WorldLevel.Rule rule = typedRule();
        if (editing != null) {
            WorldLevel.Rule old = editing;
            editing = null;
            act(WorldLevelOps.replaceRule(old, rule));
            return;
        }
        act(WorldLevelOps.addRule(rule));
    }

    /** Loads the selected rule into the row; + Add rule becomes Save rule. */
    private void edit() {
        WorldLevel.Rule rule = selectedRule();
        if (rule == null) {
            return;
        }
        editing = rule;
        kind = rule.kind();
        value = rule.value();
        effect = WorldLevelOps.effectOf(rule);
        amplifier = WorldLevelOps.amplifierOf(rule);
        dropRow();
        rebuildWidgets();
    }

    /** Forgets the row's boxes, so the next init takes the row's values, not the boxes' old text. */
    private void dropRow() {
        valueField = null;
        valueSelect = null;
        amplifierField = null;
    }

    /** The rule a row id names (its words), or null: a group header has no rule. */
    private WorldLevel.Rule ruleOf(String id) {
        for (WorldLevel.Rule r : rules) {
            if (WorldLevelOps.label(r).equals(id)) {
                return r;
            }
        }
        return null;
    }

    private WorldLevel.Rule selectedRule() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.groupId != null ? null : ruleOf(sel.id);
    }

    /** Every picked rule, top to bottom, each once. */
    private List<WorldLevel.Rule> picked() {
        List<WorldLevel.Rule> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (String id : list.pickedIds()) {
            WorldLevel.Rule r = ruleOf(id);
            if (r != null && !out.contains(r)) {
                out.add(r);
            }
        }
        return out;
    }

    private void confirmRemove() {
        List<WorldLevel.Rule> rows = picked();
        if (rows.isEmpty()) {
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, "Remove " + com.strata.ui.Words.count(rows.size(), "rule", "rules") + "?",
                () -> act(WorldLevelOps.remove(rows))));
    }

    private void confirmRemoveStage() {
        if (shown == 0) {
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, "Remove " + WorldLevelOps.heading(shown) + " and its rules?", () -> {
            if (report(WorldLevelOps.removeStage(shown))) {
                open(0);
            }
        }));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            rules = WorldLevelOps.rulesOf(shown);
            rebuild();
            // The add button's words and the row follow the edit state.
            rebuildWidgets();
        }
    }

    /** The shown stage's rules. */
    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (WorldLevel.Rule r : rules) {
            String what = WorldLevelOps.KIND_WORDS.getOrDefault(r.kind(), "");
            // The row's id is its words, stable across adds and removes (the Recent group keeps ids).
            entries.add(new BrowseList.Entry(WorldLevelOps.label(r), WorldLevelOps.label(r),
                    (r.isLock() ? "Lock: " : "Condition: ") + what, r.isLock() ? Palette.EDITED : Palette.TEXT));
        }
        list.refresh(entries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout(tabs());
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        if (layout.get("footer") != null) {
            text(graphics, layout.get("footer"), Palette.MUTED);
        }
        emptyNote(graphics, layout, list);
    }
}
