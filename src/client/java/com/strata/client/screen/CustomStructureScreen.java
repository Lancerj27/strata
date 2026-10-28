package com.strata.client.screen;

import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.CustomStructuresOps;
import com.strata.model.CustomStructure;
import com.strata.ui.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One of the designer's structures: its name (fixed once saved), its structure file, and where and how often
 * the world builds it.
 */
public class CustomStructureScreen extends StrataScreen {

    private final String originalName;
    private final UndoManager<CustomStructuresOps.Form> undo = new UndoManager<>();
    private CustomStructuresOps.Form current;
    private CustomStructuresOps.Form baseline;
    private boolean syncing;

    private EditField nameField;
    private EditField fileField;
    private EditField biomesField;
    private EditField spacingField;
    private EditField separationField;
    private EditField heightField;
    private DropdownSelect step;
    private DropdownSelect terrain;
    private Button saveButton;
    private Button undoButton;
    private com.strata.ui.Layout layout;

    /** @param name the structure, or null for a new one */
    public CustomStructureScreen(String name) {
        super(com.strata.ui.TitlePath.WORLD, name == null ? "Strata — New structure" : "Strata — Structure: " + name);
        this.originalName = name == null ? "" : name;
    }

    private boolean exists() {
        return !originalName.isEmpty();
    }

    @Override
    protected boolean scrolls() {
        return true;
    }

    /** What the typed file holds, or why not; nothing typed on a saved structure keeps its file. */
    private String fileNote(String typed) {
        if (typed == null || typed.isBlank()) {
            return exists() ? "Its file stays as it is." : "Type the path of a structure file (.nbt).";
        }
        var probe = CustomStructuresOps.probe(typed);
        return probe.problem() != null ? probe.problem() : "The file: " + probe.summary() + ".";
    }

    @Override
    protected void init() {
        super.init();
        CustomStructuresOps.Form form = current != null ? current : CustomStructuresOps.formFor(originalName);
        String footer = (exists() ? "strata_generated:custom/" + originalName + ". " : "") + CustomStructuresOps.APPLIES;
        layout = com.strata.ui.screen.FormScreenLayouts.customStructureEdit(frame(), this.font::width, title(), exists(),
                fileNote(form.file()), footer);
        useLayout(layout);

        nameField = text("name", "Name", form.name());
        nameField.getWidget().setEditable(!exists());
        fileField = text("file", "Structure file", form.file());
        biomesField = text("biomes", "Biomes", form.biomes());
        addRenderableWidget(place(Button.builder(Component.literal("Browse..."), b -> browse()).build(), layout.get("browse")));
        addRenderableWidget(place(Button.builder(Component.literal("+ Add biome"), b -> addBiome()).build(),
                layout.get("addBiome")));
        spacingField = text("spacing", "Spacing", form.spacing());
        separationField = text("separation", "Separation", form.separation());
        heightField = text("height", "Height", form.height());
        step = new DropdownSelect(0, 0, 0, 0, "Step", CustomStructure.STEPS, form.step());
        step.setOnChange(s -> edited());
        addRenderableWidget(place(step.getWidget(), layout.get("step")));
        terrain = new DropdownSelect(0, 0, 0, 0, "Land around it", CustomStructure.TERRAIN_ADAPTATIONS, form.terrain());
        terrain.setOnChange(s -> edited());
        addRenderableWidget(place(terrain.getWidget(), layout.get("terrain")));

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        if (exists()) {
            placeAll(layout, new String[]{"delete"},
                    Button.builder(Component.literal("Remove"), b -> confirmDelete()).build());
        }
        current = readFields();
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    /** The operating system's file dialog, as on Custom textures & sounds. */
    private void browse() {
        com.strata.client.util.FileDialog.pick("Pick a structure file", "*.nbt", "Structure files", picked -> {
            if (fileField != null) {
                fileField.setValue(picked);
            }
        });
    }

    /** Picks a biome and adds it to the Biomes field. */
    private void addBiome() {
        current = readFields();
        java.util.List<com.strata.game.RegistryQuery.Option> options = new java.util.ArrayList<>();
        com.strata.game.BiomeCatalog.all(this.minecraft != null && this.minecraft.level != null ? worldRegistries() : null)
                .forEach(b -> options.add(new com.strata.game.RegistryQuery.Option(b.id(),
                        com.strata.util.Labels.biome(b.id()), b.dimension())));
        com.strata.client.nav.ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add a biome", options, id -> {
            String now = current.biomes() == null ? "" : current.biomes().strip();
            boolean has = java.util.Arrays.stream(now.split(",")).map(String::strip).anyMatch(id::equals);
            if (!has) {
                undo.pushState(current);
                current = new CustomStructuresOps.Form(current.name(), current.file(),
                        now.isEmpty() ? id : now + ", " + id, current.spacing(), current.separation(), current.step(),
                        current.terrain(), current.height());
            }
        }));
    }

    private EditField text(String id, String label, String value) {
        EditField f = field(0, 0, 0, 0, label, value, null, this::edited);
        place(f.getWidget(), layout.get(id));
        return f;
    }

    private CustomStructuresOps.Form readFields() {
        return new CustomStructuresOps.Form(nameField.getValue(), fileField.getValue(), biomesField.getValue(),
                spacingField.getValue(), separationField.getValue(), step.getSelected(), terrain.getSelected(),
                heightField.getValue());
    }

    private void edited() {
        if (syncing || terrain == null || step == null || heightField == null) {
            return;
        }
        undo.pushState(current);
        String oldFile = current == null ? "" : current.file();
        current = readFields();
        refreshButtons();
        // A new path: say what the file holds, before Save.
        if (!current.file().equals(oldFile)) {
            rebuildWidgets();
        }
    }

    private void undo() {
        CustomStructuresOps.Form prev = undo.undo();
        if (prev != null) {
            current = prev;
            rebuildWidgets();
        }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = CustomStructuresOps.formProblem(readFields(), !exists()) == null;
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(readFields());
    }

    private void save() {
        CustomStructuresOps.Form form = readFields();
        String problem = CustomStructuresOps.formProblem(form, !exists());
        if (problem != null) {
            refuse(problem);
            return;
        }
        if (!report(CustomStructuresOps.save(originalName, form))) {
            return;
        }
        if (!exists()) {
            reopen(new CustomStructureScreen(form.name().strip()));
            return;
        }
        current = CustomStructuresOps.formFor(originalName);
        baseline = current;
        savedHere();
        rebuildWidgets();
    }

    private void confirmDelete() {
        minecraft.setScreen(new ConfirmDialog(this, "Remove the structure '" + originalName + "' and its file?\n"
                + "Land already made keeps what was built.", () -> {
            if (report(CustomStructuresOps.remove(List.of(originalName)))) {
                deferBack();
            }
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        if (layout.get("note0") != null) {
            text(graphics, layout.get("note0"), Palette.MUTED);
        }
        if (layout.get("footer1") != null) {
            text(graphics, layout.get("footer1"), Palette.MUTED);
        }
    }
}
