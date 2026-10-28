package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.BlockVariantOps;
import com.strata.model.BlockVariant;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** One block variant: its name and base block (fixed once saved) and the name its item shows. */
public class BlockVariantScreen extends StrataScreen {

    private record Snapshot(String name, String baseBlock, String displayName) {
    }

    private final String originalName;
    private final UndoManager<Snapshot> undo = new UndoManager<>();
    private Snapshot current;
    private Snapshot baseline;
    /** The base block picked for a new variant (the picker sets it, then init runs again). */
    private String baseBlock = "";
    private boolean syncing;

    private EditField nameField;
    private EditField displayNameField;
    private Button saveButton;
    private Button undoButton;
    private com.strata.ui.Layout layout;

    /** @param name the variant, or null for a new one */
    public BlockVariantScreen(String name) {
        this(name, null);
    }

    /**
     * @param name the variant, or null for a new one
     * @param base a new variant's base block, picked in the Blocks list, or null
     */
    public BlockVariantScreen(String name, String base) {
        super(name == null ? "Strata — New block variant" : "Strata — Block variant: " + name);
        this.originalName = name == null ? "" : name;
        if (name == null && base != null) {
            this.baseBlock = base;
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
        String baseText = baseBlock.isEmpty() ? "Pick..." : Labels.blockName(baseBlock);
        String footer;
        if (!exists()) {
            footer = "Pick the base block and type a name, then Save. " + BlockVariantOps.APPLIES;
        } else {
            BlockVariantOps.Variant v = BlockVariantOps.find(originalName);
            footer = (v != null && v.registered() ? "strata:" + originalName + " is in this game. "
                    : "strata:" + originalName + " is added at the next game start. ") + BlockVariantOps.APPLIES;
        }
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.blockVariantEdit(frame(), this.font::width,
                title(), exists(), baseText, footer);
        useLayout(built);
        return built;
    }

    @Override
    protected void init() {
        super.init();
        BlockVariantOps.Form form = BlockVariantOps.formFor(originalName);
        if (baseBlock.isEmpty()) {
            baseBlock = form.baseBlock();
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

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        if (exists()) {
            placeAll(layout, new String[]{"delete", "stats", "effects", "give"},
                    Button.builder(Component.literal("Remove"), b -> confirmDelete()).build(),
                    Button.builder(Component.literal("Stats"), b -> ScreenNavigator.get().navigateTo(
                            BlockStatScreen.forVariant(originalName))).build(),
                    // Its abilities, on whoever stands on or in it.
                    Button.builder(Component.literal("Abilities"), b -> ScreenNavigator.get().navigateTo(
                            new AbilitiesScreen(com.strata.ui.TitlePath.BLOCKS, "Strata — Abilities: " + originalName,
                                    BlockVariantOps.effectsStore(originalName)))).build(),
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
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick the base block",
                com.strata.game.RegistryQuery.blocks(), id -> {
                    undo.pushState(current);
                    baseBlock = id;
                    current = new Snapshot(current.name(), id, current.displayName());
                }));
    }

    private Snapshot readFields() {
        return new Snapshot(nameField.getValue(), baseBlock, displayNameField.getValue());
    }

    private BlockVariantOps.Form form() {
        return new BlockVariantOps.Form(nameField.getValue(), baseBlock, displayNameField.getValue());
    }

    private void applySnapshot(Snapshot s) {
        syncing = true;
        nameField.setValue(s.name());
        baseBlock = s.baseBlock();
        displayNameField.setValue(s.displayName());
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
        saveButton.active = BlockVariantOps.formProblem(form(), !exists()) == null;
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(readFields());
    }

    /** Saves and stays; a new variant reopens as its own editor. */
    private void save() {
        String problem = BlockVariantOps.formProblem(form(), !exists());
        if (problem != null) {
            refuse(problem);
            return;
        }
        if (!report(BlockVariantOps.saveForm(originalName, form()))) {
            return;
        }
        if (!exists()) {
            reopen(new BlockVariantScreen(nameField.getValue().strip()));
            return;
        }
        current = readFields();
        baseline = current;
        savedHere();
        rebuildWidgets();
    }

    private void confirmDelete() {
        minecraft.setScreen(new ConfirmDialog(this, "Remove the block variant '" + originalName + "'?\n"
                + "Its values and abilities go too. At the next game start its block and item\n"
                + "leave the game: placed ones become air.", () -> {
            if (report(BlockVariantOps.remove(List.of(originalName)))) {
                deferBack();
            }
        }));
    }

    /** Gives one to the player at the editor, to test it: it is a real item, so the game's own /give. */
    private void give() {
        if (this.minecraft == null || this.minecraft.player == null) {
            refuse("No world loaded: open a world first");
            return;
        }
        BlockVariantOps.Variant v = BlockVariantOps.find(originalName);
        if (v == null || !v.registered()) {
            refuse("It is added at the next game start: restart the game to test it");
            return;
        }
        this.minecraft.player.connection.sendCommand(BlockVariantOps.giveCommand(originalName));
        clearRefusal();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        tooltip(graphics, mouseX, mouseY, layout.get("displayName"), BlockVariant.what(BlockVariant.DISPLAY_NAME));
        if (layout.get("footer1") != null) {
            text(graphics, layout.get("footer1"), Palette.MUTED);
        }
    }
}

