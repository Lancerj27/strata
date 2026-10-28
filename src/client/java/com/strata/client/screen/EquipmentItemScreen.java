package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.EquipmentOps;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Edit ONE equipment row of a variant's loadout. */
public class EquipmentItemScreen extends StrataScreen {

    /** The fields, so Undo has one shape to restore. */
    private record Snapshot(String slot, String item, String chance, String hard, String drop, String enchant,
                            String scaled, String playerLevel) {
    }

    private final String variantName;
    /** The creature this equipment is worn by, for the Recent group and its default equipment. */
    private final String baseEntity;
    private final String scope;
    private final String scopeId;
    /** The row the save moves; null when it writes a new one. */
    private final EquipmentOps.EquipmentRow editing;
    /** Where an opened inherited row came from ("vanilla", "base mob"). */
    private final String from;

    /** The rows as the list showed them: the save needs them to keep game equipment. */
    private final List<EquipmentOps.EquipmentRow> allRows;

    private final UndoManager<Snapshot> undo = new UndoManager<>();
    private Snapshot current;
    /** The fields as opened, for the unsaved-edits check. */
    private Snapshot baseline;
    private Snapshot pendingSnapshot;
    private boolean syncing;

    private String selectedItem;
    private Button itemButton;
    private DropdownSelect slotDropdown;
    private EditField chanceField;
    private EditField hardField;
    private EditField dropField;
    private EditField enchantField;
    private EditField playerLevelField;
    private DropdownSelect scaledDropdown;
    private Button saveButton;
    private Button undoButton;

    /**
     * @param baseEntity the creature this equipment is worn by. A constructor parameter, so a new caller
     *     cannot omit it unnoticed.
     * @param form the row as it opens, from {@link EquipmentOps#equipmentForm}
     */
    public EquipmentItemScreen(String variantName, String baseEntity,
                                       String scope, String scopeId,
                                       List<EquipmentOps.EquipmentRow> allRows,
                                       EquipmentOps.EquipmentForm form) {
        super(EquipmentOps.NEW_GEAR_FORM.equals(form)
                ? "Strata — New equipment item"
                : "Strata — Equipment item: " + Labels.itemName(form.item()));
        this.variantName = variantName;
        this.baseEntity = baseEntity == null ? "" : baseEntity;
        this.scope = scope;
        this.scopeId = scopeId;
        this.allRows = allRows;
        this.editing = form.editing();
        this.from = form.from();

        // One equipment row is still this mob's data: the Recent group has to catch a save made from here,
        // not only from the list behind it.
        editingMob(this.baseEntity, variantName);
        this.current = new Snapshot(form.slot(), form.item(), form.chance(), form.hard(), form.drop(), form.enchant(),
                EquipmentOps.scaledWord(form.scaled()), form.playerLevel());
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    /** The layout for the form as it stands: the footer lines follow the fields. */
    private com.strata.ui.Layout layout(String item) {
        String problem = slotDropdown == null ? null : problem();
        String footer2 = problem != null ? problem
                : editing == null && !from.isEmpty()
                ? "That piece is " + from + " equipment — saving writes it as this variant's own row"
                : null;
        com.strata.ui.Layout layout = com.strata.ui.screen.EquipmentItemLayout.build(frame(),
                this.font::width, title(), EquipmentOps.SLOT_CHOICES, Labels.itemName(item),
                "Equipment " + Labels.scope(scope, scopeId) + " — one row is one draw in that slot",
                footer2, EquipmentOps.SCALED_CHOICES);
        useLayout(layout);
        return layout;
    }

    @Override
    protected void init() {
        super.init();
        com.strata.ui.Layout layout = layout(current.item());

        slotDropdown = new DropdownSelect(0, 0, 0, 0, "", EquipmentOps.SLOT_CHOICES, current.slot());
        slotDropdown.setOnChange(s -> edited());
        addRenderableWidget(place(slotDropdown.getWidget(), layout.get("slot")));

        selectedItem = current.item();
        itemButton = addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("item")),
                b -> openItemPicker()).build(), layout.get("item")));

        chanceField = field(0, 0, 0, 0, "Normal %", current.chance(), EditField.numericValidator(), this::edited);
        place(chanceField.getWidget(), layout.get("chance"));
        hardField = field(0, 0, 0, 0, "Hard %", current.hard(), null, this::edited);
        place(hardField.getWidget(), layout.get("chanceHard"));
        scaledDropdown = new DropdownSelect(0, 0, 0, 0, "", EquipmentOps.SCALED_CHOICES,
                current.scaled());
        scaledDropdown.setOnChange(s -> edited());
        addRenderableWidget(place(scaledDropdown.getWidget(), layout.get("scaled")));
        dropField = field(0, 0, 0, 0, "Drop %", current.drop(),
                (EditField.Check) s -> EquipmentOps.optionalChanceProblem("Drop %", s), this::edited);
        place(dropField.getWidget(), layout.get("drop"));
        enchantField = field(0, 0, 0, 0, "Enchant %", current.enchant(),
                (EditField.Check) s -> EquipmentOps.optionalChanceProblem("Enchant %", s), this::edited);
        place(enchantField.getWidget(), layout.get("enchant"));
        playerLevelField = field(0, 0, 0, 0, "Player XP level", current.playerLevel(),
                (EditField.Check) s -> com.strata.model.LevelRange.problem(s, true), this::edited);
        place(playerLevelField.getWidget(), layout.get("playerLevel"));

        // What each field means, on hover.
        tip(slotDropdown.getWidget(), EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.SLOT));
        tip(itemButton, EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.ITEM));
        tip(chanceField.getWidget(), EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.CHANCE));
        tip(hardField.getWidget(), EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.HARD));
        tip(scaledDropdown.getWidget(), EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.SCALED));
        tip(dropField.getWidget(), EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.DROP));
        tip(enchantField.getWidget(), EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.ENCHANT));
        tip(playerLevelField.getWidget(), EquipmentOps.equipmentTip(EquipmentOps.EquipmentField.PLAYER_LEVEL));

        // The actions are pinned: they never scroll.
        undoButton = addRenderableWidget(place(Button.builder(Component.literal("Undo"), b -> undo()).build(),
                layout.get("undo")));
        undoButton.active = false;
        saveButton = addRenderableWidget(place(Button.builder(Component.literal("Save"), b -> save()).build(),
                layout.get("save")));

        // A picker return hands back the form it left with; otherwise read the freshly-built widgets.
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
        // Capture the form before navigating: init rebuilds from current, which still holds the OLD item, so
        // the pick would be lost.
        pendingSnapshot = readFields();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "Pick an item",
                RegistryQuery.itemsAndVariants(),
                id -> {
                    undo.pushState(current);
                    Snapshot base = pendingSnapshot;
                    pendingSnapshot = new Snapshot(base.slot(), id, base.chance(), base.hard(), base.drop(),
                            base.enchant(), base.scaled(), base.playerLevel());
                }));
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private Snapshot readFields() {
        return new Snapshot(slotDropdown.getSelected(), selectedItem, chanceField.getValue(), hardField.getValue(),
                dropField.getValue(), enchantField.getValue(), scaledDropdown.getSelected(),
                playerLevelField.getValue());
    }

    private void applySnapshot(Snapshot s) {
        syncing = true;
        selectedItem = s.item();
        itemButton.setMessage(Component.literal(layout(s.item()).textOf("item")));
        chanceField.setValue(s.chance());
        hardField.setValue(s.hard());
        scaledDropdown.setSelected(s.scaled());
        dropField.setValue(s.drop());
        enchantField.setValue(s.enchant());
        playerLevelField.setValue(s.playerLevel());
        slotDropdown.setSelected(s.slot());
        syncing = false;
    }

    private void undo() {
        Snapshot prev = undo.undo();
        if (prev != null) {
            applySnapshot(prev);
            current = prev;
            refreshButtons();
        }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        // The same refusal the save gives, so Save greys exactly when the red note is showing.
        saveButton.active = chanceField.isValid() && playerLevelField.isValid() && problem() == null;
    }

    /** What the save would refuse right now, or null. */
    private String problem() {
        String p = EquipmentOps.equipmentChanceProblem(editing,
                slotDropdown.getSelected(), selectedItem, chanceField.getValue());
        if (p == null) {
            p = EquipmentOps.pieceProblem(hardField.getValue(), dropField.getValue(), enchantField.getValue());
        }
        return p != null ? p
                : com.strata.model.LevelRange.problem(playerLevelField.getValue(), true);
    }

    private void save() {
        if (!saveButton.active) return;
        Snapshot s = readFields();
        boolean saved = report(EquipmentOps.saveEquipment(variantName, scope, scopeId, editing, s.slot(),
                s.item(), s.chance(), s.hard(), s.drop(), s.enchant(), EquipmentOps.scaledOf(s.scaled()),
                s.playerLevel(), allRows));
        if (saved) {
            // A SMALL FORM GOES BACK TO ITS LIST.
            backSaved();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout(selectedItem);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (String id : new String[]{"label.slot", "label.item", "label.chance", "label.chanceHard",
                "label.scaled", "label.drop", "label.enchant", "label.playerLevel"}) {
            if (layout.get(id) != null) {
                text(graphics, layout.get(id), Palette.LABEL);
            }
        }
        text(graphics, layout.get("footer1"), Palette.MUTED);
        // The save's own refusal, in red as the designer types.
        if (layout.get("footer2") != null) {
            text(graphics, layout.get("footer2"), problem() != null ? Palette.ERROR : Palette.MUTED);
        }
    }

    /** Attaches a hover tooltip to a widget, as the abilities editor does. */
    private static void tip(net.minecraft.client.gui.components.AbstractWidget widget, String text) {
        widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.literal(text)));
    }
}

