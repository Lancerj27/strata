package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.LootOps;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Edit one loot entry. */
public class LootEntryScreen extends StrataScreen {

    private record Snapshot(String item, String countMin, String countMax,
                            String chancePct, String mode,
                            String conditionType, String conditionValue) {}

    private final String entity;
    /** WHICH VARIANT's drop this is, blank for the base mob's. */
    private final String variantName;
    private final LootOps.EntryTarget target;

    private final UndoManager<Snapshot> undo = new UndoManager<>();
    private Snapshot current;
    /** The fields as opened, for the unsaved-edits check. */
    private Snapshot baseline;
    private Snapshot pendingSnapshot;
    private boolean syncing;

    private String selectedItem;
    private Button itemButton;
    private EditField countMinField;
    private EditField countMaxField;
    private EditField chancePctField;
    private DropdownSelect modeDropdown;
    private DropdownSelect conditionTypeDropdown;
    private EditField conditionValueField;
    private DropdownSelect conditionValueDropdown;
    /** The death causes the dropdown offers: "any" then the game's damage types. */
    private List<String> valueChoices = List.of("any");
    private Button saveButton;
    private Button undoButton;

    /** The layout for the form as it stands: the item, and what the save would refuse (null for nothing). */
    private com.strata.ui.Layout layout(String problem) {
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.lootEntry(frame(), this.font::width,
                title(), selectedItem == null || selectedItem.isEmpty() ? "Pick an item..." : Labels.itemName(selectedItem),
                LootOps.MODE_CHOICES, LootOps.CONDITION_CHOICES,
                "Loot pool " + target.poolIndex() + " — " + Labels.scope(target.scope(), target.scopeId()), problem);
        useLayout(built);
        return built;
    }

    public LootEntryScreen(String entity, String variantName,
                               LootOps.EntryTarget target, LootOps.LootForm form) {
        super(form.item().isEmpty() ? "Strata — New loot entry" : "Strata — Loot entry: " + Labels.itemName(form.item()));
        this.entity = entity;
        this.variantName = variantName == null ? "" : variantName.strip();
        this.target = target;
        // One drop entry is still this mob's data - the Recent group has to catch a save made from here, not
        // only from the list behind it.
        editingMob(this.entity, this.variantName);
        this.current = new Snapshot(form.item(), form.countMin(), form.countMax(),
                form.chancePct(), form.mode(), form.conditionType(), form.conditionValue());
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    @Override
    protected void init() {
        super.init();
        // The layout is the UI layer's: the actions are pinned.
        selectedItem = current.item();
        com.strata.ui.Layout layout = layout(null);

        itemButton = addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("item")),
                b -> openItemPicker()).build(), layout.get("item")));
        countMinField = field(0, 0, 0, 0, "Min", current.countMin(), EditField.nonNegativeIntegerValidator(),
                this::edited);
        place(countMinField.getWidget(), layout.get("min"));
        countMaxField = field(0, 0, 0, 0, "Max", current.countMax(), EditField.nonNegativeIntegerValidator(),
                this::edited);
        place(countMaxField.getWidget(), layout.get("max"));
        chancePctField = field(0, 0, 0, 0, "Chance %", current.chancePct(), EditField.nonNegativeIntegerValidator(),
                this::edited);
        place(chancePctField.getWidget(), layout.get("chance"));
        modeDropdown = new DropdownSelect(0, 0, 0, 0, "Mode", LootOps.MODE_CHOICES, current.mode());
        modeDropdown.setOnChange(s -> edited());
        addRenderableWidget(place(modeDropdown.getWidget(), layout.get("mode")));
        conditionTypeDropdown = new DropdownSelect(0, 0, 0, 0, "Condition", LootOps.CONDITION_CHOICES,
                current.conditionType());
        conditionTypeDropdown.setOnChange(s -> {
            edited();
            refreshValue();
        });
        addRenderableWidget(place(conditionTypeDropdown.getWidget(), layout.get("condition")));
        valueChoices = deathCauseChoices();
        conditionValueField = field(0, 0, 0, 0, "Value", current.conditionValue(), EditField.csvTextValidator(),
                this::edited);
        place(conditionValueField.getWidget(), layout.get("value"));
        conditionValueDropdown = new DropdownSelect(0, 0, 0, 0, "Value", valueChoices,
                fullId(valueChoices, current.conditionValue()));
        conditionValueDropdown.setOnChange(s -> edited());
        conditionValueDropdown.setWords(com.strata.domain.LootOps::deathCauseLabel);
        conditionValueDropdown.fitTo(this.font::width, layout.boxOf("value").w() - com.strata.ui.Layout.LABEL_MARGIN);
        addRenderableWidget(place(conditionValueDropdown.getWidget(), layout.get("value")));
        refreshValue();

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        undoButton.active = false;
        // Apply pending snapshot from the picker round-trip if present, otherwise read from the freshly-built
        // widgets.
        if (pendingSnapshot != null) {
            Snapshot snap = pendingSnapshot;
            pendingSnapshot = null;
            applySnapshot(snap);
            current = snap;
        } else {
            current = readFields();
        }
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(readFields());
    }

    private void openItemPicker() {
        // Capture full form state before navigating — init rebuilds from current (which holds the OLD item)
        // on the round-trip, so the picker selection is lost without this.
        pendingSnapshot = readFields();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "Pick an item",
                RegistryQuery.itemsAndVariants(),
                id -> {
                    undo.pushState(current);
                    Snapshot base = pendingSnapshot;
                    pendingSnapshot = new Snapshot(
                            id, base.countMin(), base.countMax(),
                            base.chancePct(), base.mode(),
                            base.conditionType(), base.conditionValue());
                }));
    }

    /**
     * The death causes the dropdown offers: "any" first, then every damage type id the game's registry holds,
     * a mod's included.
     */
    private List<String> deathCauseChoices() {
        List<RegistryQuery.Option> options = RegistryQuery.damageTypesFor(
                this.minecraft != null && this.minecraft.level != null
                        ? this.minecraft.level.registryAccess() : null);
        List<String> out = new ArrayList<>(com.strata.model.LootCondition.CAUSES);
        for (RegistryQuery.Option o : options) {
            if (!o.id().startsWith("#")) {
                out.add(o.id());
            }
        }
        return out;
    }

    /**
     * The dropdown choice a stored value shows: the id itself, or the id whose bare path it is (a hand-typed
     * "drown" selects the drown row).
     */
    private static String fullId(List<String> choices, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String v = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (choices.contains(v)) {
            return v;
        }
        int slash = v.lastIndexOf(':');
        if (slash >= 0) {
            v = v.substring(slash + 1);
        }
        for (String id : choices) {
            if (id.endsWith(":" + v)) {
                return id;
            }
        }
        return "";
    }

    /**
     * The Value box when the condition takes a number or text, and the dropdown of the game's damage types
     * for a death cause.
     */
    private String value() {
        return "death_cause".equals(conditionTypeDropdown.getSelected()) && valueChoices.size() > 1
                ? conditionValueDropdown.getSelected() : conditionValueField.getValue();
    }

    /**
     * Greys the Value control when the condition takes none; a death cause without its game
     * list open falls back to the Value box, so a cause already there is never lost.
     */
    private void refreshValue() {
        String type = conditionTypeDropdown.getSelected();
        boolean on = LootOps.conditionTakesValue(type);
        boolean pick = "death_cause".equals(type) && valueChoices.size() > 1;
        conditionValueField.getWidget().setEditable(on && !pick);
        conditionValueField.getWidget().active = on && !pick;
        conditionValueField.getWidget().visible = !pick;
        conditionValueDropdown.getWidget().visible = on && pick;
        conditionValueDropdown.getWidget().active = on && pick;
        if (on && pick) {
            conditionValueDropdown.setSelected(fullId(valueChoices, conditionValueField.getValue()));
        }
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private Snapshot readFields() {
        return new Snapshot(
                selectedItem,
                countMinField.getValue(),
                countMaxField.getValue(),
                chancePctField.getValue(),
                modeDropdown.getSelected(),
                conditionTypeDropdown.getSelected(),
                value());
    }

    private void applySnapshot(Snapshot s) {
        syncing = true;
        selectedItem = s.item();
        itemButton.setMessage(Component.literal(layout(null).textOf("item")));

        countMinField.setValue(s.countMin());
        countMaxField.setValue(s.countMax());
        chancePctField.setValue(s.chancePct());
        modeDropdown.setSelected(s.mode());
        conditionTypeDropdown.setSelected(s.conditionType());
        conditionValueField.setValue(s.conditionValue());
        refreshValue();
        syncing = false;
    }

    private void undo() {
        Snapshot prev = undo.undo();
        if (prev != null) { applySnapshot(prev); current = prev; refreshButtons(); }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = !selectedItem.isEmpty() && countMinField.isValid()
                && countMaxField.isValid() && chancePctField.isValid();
    }

    private void save() {
        if (!saveButton.active) return;
        Snapshot s = readFields();
        LootOps.LootForm form = new LootOps.LootForm(s.item(), s.countMin(), s.countMax(), s.chancePct(), s.mode(),
                s.conditionType(), s.conditionValue());
        if (!report(LootOps.save(entity, variantName, target, form))) {
            return;
        }
        // A SMALL FORM GOES BACK TO ITS LIST.
        backSaved();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String problem = LootOps.entryProblem(modeDropdown.getSelected(),
                countMinField.getValue(), countMaxField.getValue(), chancePctField.getValue());
        if (problem == null) {
            problem = LootOps.conditionValueProblem(conditionTypeDropdown.getSelected(),
                    value());
        }
        com.strata.ui.Layout layout = layout(problem);
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("footer1"), Palette.MUTED);
        // What the save would refuse, in red as the designer types, from the same check the save runs.
        if (layout.get("footer2") != null) {
            text(graphics, layout.get("footer2"), Palette.ERROR);
        }

        tooltip(graphics, mouseX, mouseY, layout.get("item"),
                "Click to select an item from the registry");
        tooltip(graphics, mouseX, mouseY, layout.get("min"),
                "Minimum number of items dropped");
        tooltip(graphics, mouseX, mouseY, layout.get("max"),
                "Maximum number of items dropped");
        tooltip(graphics, mouseX, mouseY, layout.get("chance"),
                "Percent chance this entry is dropped (0-100)");
        tooltip(graphics, mouseX, mouseY, layout.get("mode"),
                "Add = add this item to the loot table",
                "Replace = clear all of the game's loot, then drop this");
        tooltip(graphics, mouseX, mouseY, layout.get("condition"),
                "Extra condition, with its value:",
                "  " + com.strata.ui.ChoiceWords.word("random_chance") + " — a number from 0 to 1, like 0.25",
                "  " + com.strata.ui.ChoiceWords.word("looting") + " — the lowest Looting level, like >=2",
                "  " + com.strata.ui.ChoiceWords.word("death_cause") + " — pick a cause: Player (any kill), Player melee, Mob,",
                "    Fire (not sunlight), Lava, Sunlight, Drowning, Fall, Explosion, Other, or Any death.",
                "    Every game damage type follows them (a world must be open to list those).");
    }
}
