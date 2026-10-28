package com.strata.client.screen;

import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.WorldSettingsOps;
import com.strata.ui.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The world's own settings: the border, difficulty, time of day, day length, weather and spawn angle, set
 * live on the running world.
 */
public class WorldSettingsScreen extends StrataScreen {

    private final UndoManager<WorldSettingsOps.Form> undo = new UndoManager<>();
    private WorldSettingsOps.Snapshot snapshot;
    private WorldSettingsOps.Form current;
    private WorldSettingsOps.Form baseline;

    private EditField centerX;
    private EditField centerZ;
    private EditField size;
    private EditField damage;
    private EditField safeZone;
    private EditField warnBlocks;
    private EditField warnSeconds;
    private EditField time;
    private EditField dayMinutes;
    private EditField weatherTicks;
    private EditField spawnAngle;
    private DropdownSelect difficulty;
    private DropdownSelect locked;
    private DropdownSelect weather;
    private Button saveButton;
    private Button undoButton;
    private com.strata.ui.Layout layout;

    public WorldSettingsScreen() {
        super("Strata — World settings");
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    private boolean hasWorld() {
        return snapshot != null;
    }

    @Override
    protected void init() {
        super.init();
        if (snapshot == null) {
            snapshot = WorldSettingsOps.read();
        }
        WorldSettingsOps.Form form = current != null ? current
                : hasWorld() ? snapshot.form() : WorldSettingsOps.EMPTY;
        String footer = hasWorld() ? "Applied to the running world, which saves them. Save sets what you changed."
                : WorldSettingsOps.NO_WORLD;
        layout = com.strata.ui.screen.FormScreenLayouts.worldSettings(frame(), this.font::width, title(), true,
                footer);
        useLayout(layout);

        centerX = number("centerX", "Centre X", form.centerX());
        centerZ = number("centerZ", "Centre Z", form.centerZ());
        size = number("size", "Size", form.size());
        damage = number("damage", "Damage per block", form.damagePerBlock());
        safeZone = number("safeZone", "Safe zone", form.safeZone());
        warnBlocks = number("warnBlocks", "Warning distance", form.warningBlocks());
        warnSeconds = number("warnSeconds", "Warning time", form.warningSeconds());
        difficulty = choice("difficulty", "Difficulty", WorldSettingsOps.DIFFICULTIES, form.difficulty());
        locked = choice("locked", "Difficulty locked", WorldSettingsOps.YES_NO, form.locked());
        shown("hardcore", "Hardcore", hasWorld() ? (snapshot.hardcore() ? "Yes" : "No") : "");
        time = number("time", "Time of day", form.time());
        dayMinutes = number("dayMinutes", "Day length", form.dayMinutes());
        weather = choice("weather", "Weather", WorldSettingsOps.WEATHERS, form.weather());
        weatherTicks = number("weatherTicks", "Weather lasts", form.weatherTicks());
        spawnAngle = number("spawnAngle", "Spawn angle", form.spawnAngle());
        shown("datapacks", "Enabled datapacks", hasWorld() ? String.join(", ", snapshot.datapacks()) : "");

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        current = readFields();
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    private EditField number(String id, String label, String value) {
        EditField f = field(0, 0, 0, 0, label, value, null, this::edited);
        f.getWidget().setEditable(hasWorld());
        place(f.getWidget(), layout.get(id));
        return f;
    }

    /** A locked row: shown, never edited. */
    private void shown(String id, String label, String value) {
        EditField f = field(0, 0, 0, 0, label, value, null, null);
        f.getWidget().setEditable(false);
        place(f.getWidget(), layout.get(id));
    }

    private DropdownSelect choice(String id, String name, java.util.List<String> options, String value) {
        DropdownSelect d = new DropdownSelect(0, 0, 0, 0, name, options, value);
        d.setOnChange(s -> edited());
        addRenderableWidget(place(d.getWidget(), layout.get(id)));
        return d;
    }

    private WorldSettingsOps.Form readFields() {
        return new WorldSettingsOps.Form(centerX.getValue(), centerZ.getValue(), size.getValue(), damage.getValue(),
                safeZone.getValue(), warnBlocks.getValue(), warnSeconds.getValue(), difficulty.getSelected(),
                locked.getSelected(), time.getValue(), dayMinutes.getValue(), weather.getSelected(),
                weatherTicks.getValue(), spawnAngle.getValue());
    }

    private void edited() {
        if (spawnAngle == null || weather == null) {
            return;
        }
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private void undo() {
        WorldSettingsOps.Form prev = undo.undo();
        if (prev != null) {
            current = prev;
            rebuildWidgets();
        }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        // Active with no world too (a greyed Save is a fault, fit.10): Save then says why it cannot.
        saveButton.active = !hasWorld() || WorldSettingsOps.formProblem(readFields()) == null;
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(readFields());
    }

    /** Sets what changed on the world, reads it back, and stays. */
    private void save() {
        if (!hasWorld()) {
            refuse(WorldSettingsOps.NO_WORLD);
            return;
        }
        WorldSettingsOps.Form form = readFields();
        String problem = WorldSettingsOps.formProblem(form);
        if (problem != null) {
            refuse(problem);
            return;
        }
        if (!report(WorldSettingsOps.save(form))) {
            return;
        }
        snapshot = WorldSettingsOps.read();
        current = snapshot == null ? form : snapshot.form();
        baseline = current;
        savedHere();
        rebuildWidgets();
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
