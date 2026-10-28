package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.ProjectileOps;
import com.strata.ui.Palette;
import com.strata.util.Labels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One of the designer's projectiles: its name (fixed once saved), the projectile it is fired as, how it flies
 * and hits.
 */
public class ProjectileEditScreen extends StrataScreen {

    private final String originalName;
    private final UndoManager<ProjectileOps.Form> undo = new UndoManager<>();
    private ProjectileOps.Form current;
    private ProjectileOps.Form baseline;
    /** The base projectile (the picker sets it, then init runs again). */
    private String base = "";
    private boolean syncing;

    private EditField nameField;
    private EditField speedField;
    private EditField gravityField;
    private EditField damageField;
    private EditField explosionField;
    private EditField fireField;
    private EditField lookField;
    private EditField textureField;
    private DropdownSelect keepBase;
    private Button saveButton;
    private Button undoButton;
    private com.strata.ui.Layout layout;

    /** @param name the projectile, or null for a new one */
    public ProjectileEditScreen(String name) {
        super(name == null ? "Strata — New projectile" : "Strata — Projectile: " + name);
        this.originalName = name == null ? "" : name;
    }

    /** A projectile's abilities: the Abilities editor over its own list, On impact only. */
    public static AbilitiesScreen abilities(String name) {
        return new AbilitiesScreen(com.strata.ui.TitlePath.WORLD, "Strata — Abilities: " + name,
                ProjectileOps.effectsStore(name));
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
        String baseText = base.isEmpty() ? "Pick..." : Labels.pretty(ResourceLocation.tryParse(base));
        String footer = exists() ? "strata:" + originalName + " in a goal's Fires. " + ProjectileOps.APPLIES
                : "Type a name and pick what it is fired as, then Save.";
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.projectileEdit(frame(), this.font::width,
                title(), exists(), baseText, footer);
        useLayout(built);
        return built;
    }

    @Override
    protected void init() {
        super.init();
        ProjectileOps.Form form = current != null ? current : ProjectileOps.formFor(originalName);
        if (base.isEmpty()) {
            base = form.base();
        }
        layout = layout();

        nameField = field(0, 0, 0, 0, "Name", form.name(), null, this::edited);
        nameField.getWidget().setEditable(!exists());
        place(nameField.getWidget(), layout.get("name"));
        Button baseButton = Button.builder(Component.literal(layout.textOf("base")), b -> pickBase()).build();
        placeAll(layout, new String[]{"base"}, baseButton);
        speedField = number("speed", "Speed", form.speed());
        gravityField = number("gravity", "Gravity", form.gravity());
        damageField = number("damage", "Damage", form.damage());
        explosionField = number("explosion", "Explosion power", form.explosion());
        fireField = number("fire", "Sets on fire (seconds)", form.fireSeconds());
        lookField = text("look", "Looks like item", form.look());
        textureField = text("texture", "Arrow texture", form.texture());
        keepBase = new DropdownSelect(0, 0, 0, 0, "Keep its own impact",
                com.strata.ui.screen.FormScreenLayouts.KEEP_BASE, form.keepBase() ? "Yes" : "No");
        keepBase.setOnChange(s -> edited());
        addRenderableWidget(place(keepBase.getWidget(), layout.get("keepBase")));

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        if (exists()) {
            placeAll(layout, new String[]{"delete", "effects"},
                    Button.builder(Component.literal("Remove"), b -> confirmDelete()).build(),
                    Button.builder(Component.literal("Abilities"), b -> ScreenNavigator.get().navigateTo(
                            abilities(originalName))).build());
        }
        current = readFields();
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    private EditField text(String id, String label, String value) {
        EditField f = field(0, 0, 0, 0, label, value, null, this::edited);
        place(f.getWidget(), layout.get(id));
        return f;
    }

    private EditField number(String id, String label, String value) {
        EditField f = field(0, 0, 0, 0, label, value, EditField.numericValidator(), this::edited);
        place(f.getWidget(), layout.get(id));
        return f;
    }

    private void pickBase() {
        current = readFields();
        // The game's and mods' projectiles: a projectile is fired as one of those, never as another of yours.
        List<com.strata.game.RegistryQuery.Option> options = com.strata.game.RegistryQuery.projectiles(this.minecraft.level)
                .stream().filter(o -> !com.strata.model.ProjectilePreset.NAMESPACE.equals(o.namespace())).toList();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Fired as which?", options, id -> {
            undo.pushState(current);
            base = id;
            current = readFieldsWith(id);
        }));
    }

    private ProjectileOps.Form readFields() {
        return readFieldsWith(base);
    }

    private ProjectileOps.Form readFieldsWith(String baseId) {
        return new ProjectileOps.Form(nameField.getValue(), baseId, speedField.getValue(), gravityField.getValue(),
                damageField.getValue(), explosionField.getValue(), fireField.getValue(),
                !"No".equals(keepBase.getSelected()), lookField.getValue(), textureField.getValue());
    }

    private void applyForm(ProjectileOps.Form f) {
        syncing = true;
        nameField.setValue(f.name());
        base = f.base();
        speedField.setValue(f.speed());
        gravityField.setValue(f.gravity());
        damageField.setValue(f.damage());
        explosionField.setValue(f.explosion());
        fireField.setValue(f.fireSeconds());
        lookField.setValue(f.look());
        textureField.setValue(f.texture());
        syncing = false;
    }

    private void edited() {
        if (syncing || nameField == null || keepBase == null) {
            return;
        }
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private void undo() {
        ProjectileOps.Form prev = undo.undo();
        if (prev != null) {
            applyForm(prev);
            current = prev;
            rebuildWidgets();
        }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = ProjectileOps.formProblem(readFields(), !exists(), ProjectileOps.names()) == null;
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(readFields());
    }

    /** Saves and stays; a new projectile reopens as its own editor. */
    private void save() {
        ProjectileOps.Form form = readFields();
        String problem = ProjectileOps.formProblem(form, !exists(), ProjectileOps.names());
        if (problem != null) {
            refuse(problem);
            return;
        }
        if (!report(ProjectileOps.saveForm(originalName, form))) {
            return;
        }
        if (!exists()) {
            reopen(new ProjectileEditScreen(form.name().strip()));
            return;
        }
        current = readFields();
        baseline = current;
        savedHere();
        rebuildWidgets();
    }

    private void confirmDelete() {
        minecraft.setScreen(new ConfirmDialog(this, "Remove the projectile '" + originalName + "'?\n"
                + "Its abilities go too. A goal that fires it fires nothing.", () -> {
            if (report(ProjectileOps.remove(List.of(originalName)))) {
                deferBack();
            }
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        if (layout.get("footer1") != null) {
            text(graphics, layout.get("footer1"), Palette.MUTED);
        }
    }
}
