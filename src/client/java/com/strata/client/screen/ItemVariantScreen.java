package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.ItemVariantOps;
import com.strata.model.ItemVariant;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One item variant: its name and base item (fixed once saved), and its look: display name, lore, rarity,
 * glint, unbreakable, custom model data.
 */
public class ItemVariantScreen extends StrataScreen {

    private record Snapshot(String name, String baseItem, String displayName, String lore, String rarity,
                            String glint, String unbreakable, String model) {
    }

    private final String originalName;
    private final UndoManager<Snapshot> undo = new UndoManager<>();
    private Snapshot current;
    private Snapshot baseline;
    /** The base item picked for a new variant (the picker sets it, then init runs again). */
    private String baseItem = "";
    private boolean syncing;

    private EditField nameField;
    private EditField displayNameField;
    private EditField loreField;
    private EditField modelField;
    private DropdownSelect rarity;
    private DropdownSelect glint;
    private DropdownSelect unbreakable;
    private Button saveButton;
    private Button undoButton;
    private com.strata.ui.Layout layout;

    /** @param name the variant, or null for a new one */
    public ItemVariantScreen(String name) {
        this(name, null);
    }

    /**
     * @param name the variant, or null for a new one
     * @param base a new variant's base item, picked in the Items list, or null
     */
    public ItemVariantScreen(String name, String base) {
        super(name == null ? "Strata — New item variant" : "Strata — Item variant: " + name);
        this.originalName = name == null ? "" : name;
        if (name == null && base != null) {
            this.baseItem = base;
        }
    }

    private boolean exists() {
        return !originalName.isEmpty();
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    private com.strata.ui.Layout layout() {
        String baseText = baseItem.isEmpty() ? "Pick..." : Labels.itemName(baseItem);
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.itemVariantEdit(frame(), this.font::width,
                title(), exists(), baseText, ItemVariantOps.RARITY_CHOICES, ItemVariant.GLINTS, ItemVariantOps.YES_NO,
                exists() && ItemVariantOps.find(originalName) != null ? "Blank values are the base item's. "
                        + com.strata.ui.Words.count(ItemVariantOps.find(originalName).effects(), "effect", "effects") + "." : "Pick the base item and type a name, then Save.");
        useLayout(built);
        return built;
    }

    @Override
    protected void init() {
        super.init();
        ItemVariantOps.Form form = ItemVariantOps.formFor(originalName);
        if (baseItem.isEmpty()) {
            baseItem = form.baseItem();
        }
        layout = layout();

        nameField = field(0, 0, 0, 0, "Name", form.name(), null, this::edited);
        nameField.getWidget().setEditable(!exists());
        place(nameField.getWidget(), layout.get("name"));
        Button base = Button.builder(Component.literal(layout.textOf("base")), b -> pickBase()).build();
        placeAll(layout, new String[]{"base"}, base);
        base.active = !exists();
        displayNameField = field(0, 0, 0, 0, "Display name", form.displayName(), null, this::edited);
        place(displayNameField.getWidget(), layout.get("displayName"));
        loreField = field(0, 0, 0, 0, "Lore", form.lore(), null, this::edited);
        place(loreField.getWidget(), layout.get("lore"));
        rarity = new DropdownSelect(0, 0, 0, 0, "Rarity", ItemVariantOps.RARITY_CHOICES, form.rarity());
        rarity.setOnChange(s -> edited());
        addRenderableWidget(place(rarity.getWidget(), layout.get("rarity")));
        glint = new DropdownSelect(0, 0, 0, 0, "Glint", ItemVariant.GLINTS, form.glint());
        glint.setOnChange(s -> edited());
        addRenderableWidget(place(glint.getWidget(), layout.get("glint")));
        unbreakable = new DropdownSelect(0, 0, 0, 0, "Unbreakable", ItemVariantOps.YES_NO, form.unbreakable());
        unbreakable.setOnChange(s -> edited());
        addRenderableWidget(place(unbreakable.getWidget(), layout.get("unbreakable")));
        modelField = field(0, 0, 0, 0, "Custom model data", form.customModelData(), null, this::edited);
        place(modelField.getWidget(), layout.get("model"));

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        if (exists()) {
            placeAll(layout, new String[]{"delete", "stats", "effects", "give"},
                    Button.builder(Component.literal("Remove"), b -> confirmDelete()).build(),
                    Button.builder(Component.literal("Stats"), b -> ScreenNavigator.get().navigateTo(
                            new ItemVariantStatsScreen(originalName))).build(),
                    Button.builder(Component.literal("Effects"), b -> ScreenNavigator.get().navigateTo(
                            new AbilitiesScreen(com.strata.ui.TitlePath.ITEMS, "Strata — Effects: " + originalName,
                                    ItemVariantOps.effectsStore(originalName)))).build(),
                    Button.builder(Component.literal("Give me one"), b -> give()).build());
        }
        // Minecraft runs init again after the picker and a dialog: put the edits back.
        if (current != null) {
            applySnapshot(current);
        }
        current = readFields();
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    private void pickBase() {
        // Recorded first: the picker's return runs init again, which keeps the fields.
        current = readFields();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick the base item",
                com.strata.game.RegistryQuery.items(), id -> {
                    undo.pushState(current);
                    baseItem = id;
                    current = new Snapshot(current.name(), id, current.displayName(), current.lore(),
                            current.rarity(), current.glint(), current.unbreakable(), current.model());
                }));
    }

    private Snapshot readFields() {
        return new Snapshot(nameField.getValue(), baseItem, displayNameField.getValue(), loreField.getValue(),
                rarity.getSelected(), glint.getSelected(), unbreakable.getSelected(), modelField.getValue());
    }

    private ItemVariantOps.Form form() {
        return new ItemVariantOps.Form(nameField.getValue(), baseItem, displayNameField.getValue(), loreField.getValue(),
                rarity.getSelected(), glint.getSelected(), unbreakable.getSelected(), modelField.getValue());
    }

    private void applySnapshot(Snapshot s) {
        syncing = true;
        nameField.setValue(s.name());
        baseItem = s.baseItem();
        displayNameField.setValue(s.displayName());
        loreField.setValue(s.lore());
        rarity.setSelected(s.rarity());
        glint.setSelected(s.glint());
        unbreakable.setSelected(s.unbreakable());
        modelField.setValue(s.model());
        syncing = false;
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private void undo() {
        Snapshot prev = undo.undo();
        if (prev != null) {
            applySnapshot(prev);
            current = prev;
            rebuildWidgets();
        }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = ItemVariantOps.formProblem(form(), !exists()) == null;
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(readFields());
    }

    /** Saves and stays; a new variant reopens as its own editor. */
    private void save() {
        String problem = ItemVariantOps.formProblem(form(), !exists());
        if (problem != null) {
            refuse(problem);
            return;
        }
        if (!report(ItemVariantOps.saveForm(originalName, form()))) {
            return;
        }
        if (!exists()) {
            reopen(new ItemVariantScreen(nameField.getValue().strip()));
            return;
        }
        current = readFields();
        baseline = current;
        savedHere();
        rebuildWidgets();
    }

    private void confirmDelete() {
        minecraft.setScreen(new ConfirmDialog(this, "Remove the item variant '" + originalName + "'?\n"
                + "Its values and effects go too. Stacks already in the world stay as they are,\n"
                + "and their effects stop.", () -> {
            if (report(ItemVariantOps.remove(List.of(originalName)))) {
                deferBack();
            }
        }));
    }

    /** Gives one to the player at the editor, to test it (the server's reply is in chat). */
    private void give() {
        if (this.minecraft == null || this.minecraft.player == null) {
            refuse("No world loaded: open a world first");
            return;
        }
        this.minecraft.player.connection.sendCommand(
                com.strata.command.StrataCommands.giveVariantCommand(originalName));
        clearRefusal();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        tooltip(graphics, mouseX, mouseY, layout.get("lore"), ItemVariant.what(ItemVariant.LORE));
        tooltip(graphics, mouseX, mouseY, layout.get("glint"), ItemVariant.what(ItemVariant.GLINT));
        tooltip(graphics, mouseX, mouseY, layout.get("unbreakable"), ItemVariant.what(ItemVariant.UNBREAKABLE));
        tooltip(graphics, mouseX, mouseY, layout.get("model"), ItemVariant.what(ItemVariant.CUSTOM_MODEL_DATA));
        if (layout.get("footer1") != null) {
            text(graphics, layout.get("footer1"), Palette.MUTED);
        }
    }
}

