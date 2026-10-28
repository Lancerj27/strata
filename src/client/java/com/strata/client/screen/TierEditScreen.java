package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.TierOps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Edit one tier. */
public class TierEditScreen extends StrataScreen {

    private record Snapshot(String name, String level, String spawnMode, String coefficient) {}

    private final String originalName;
    private final UndoManager<Snapshot> undo = new UndoManager<>();
    private Snapshot current;
    /** The fields as opened, for the unsaved-edits check. */
    private Snapshot baseline;
    /** The footer counts, read once per init rather than per frame. */
    private TierOps.TierSummary summary;

    private EditField nameField;
    private EditField levelField;
    private EditField coefficientField;
    private DropdownSelect modeDropdown;
    /** Whether this Replace tier blocks other spawns. */
    private Button saveButton;
    private Button undoButton;
    private Button deleteButton;
    private boolean syncing;

    /** The tier's biomes in words, read once per init. */
    private List<String> tierBiomes = List.of();

    /** The screen's layout, built in init. */
    private com.strata.ui.Layout layout;

    /** The layout for the counts as they stand. */
    private com.strata.ui.Layout layout() {
        long biomes = summary == null ? 0 : summary.biomes();
        int spawns = summary == null ? 0 : summary.spawnRules();
        int ores = summary == null ? 0 : summary.oreRules();
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.tierEdit(frame(), this.font::width, title(),
                !originalName.isEmpty(), TierOps.SPAWN_MODES, TierOps.OTHER_SPAWN_CHOICES,
                tierBiomes, com.strata.ui.Words.count(spawns, "spawn rule", "spawn rules") + ", " + com.strata.ui.Words.count(ores, "ore rule", "ore rules"), com.strata.ui.Words.count(biomes, "biome", "biomes") + " use this tier");
        useLayout(built);
        return built;
    }

    public TierEditScreen(String name) {
        super(name == null ? "Strata — New tier" : "Strata — Tier: " + name);
        this.originalName = name == null ? "" : name;
        editingTier(this.originalName);
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    @Override
    protected void init() {
        super.init();

        TierOps.TierForm form = TierOps.formFor(originalName);
        List<String> existingNames = TierOps.names();
        summary = TierOps.summary(originalName);
        // The tier's biomes, in words, for the section under the fields.
        tierBiomes = TierOps.biomesIn(originalName).stream().map(Labels::biome).sorted().toList();

        // The layout is the UI layer's: the actions are pinned.
        layout = layout();
        List<String> modes = TierOps.SPAWN_MODES;

        nameField = field(0, 0, 0, 0, "Name", form.name(),
                (EditField.Check) s -> TierOps.nameProblem(s, originalName, existingNames), this::edited);
        place(nameField.getWidget(), layout.get("name"));
        levelField = field(0, 0, 0, 0, "Area level", form.level(), EditField.integerValidator(), this::edited);
        place(levelField.getWidget(), layout.get("level"));
        modeDropdown = new DropdownSelect(0, 0, 0, 0, "Spawn mode", modes, form.spawnMode());
        modeDropdown.setOnChange(s -> edited());
        addRenderableWidget(place(modeDropdown.getWidget(), layout.get("mode")));
        coefficientField = field(0, 0, 0, 0, "Coefficient", form.coefficient(), EditField.numericValidator(),
                this::edited);
        place(coefficientField.getWidget(), layout.get("coefficient"));

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        undoButton.active = false;

        if (!originalName.isEmpty()) {
            // "Delete", the word every other editor uses for the record it edits.
            deleteButton = Button.builder(Component.literal("Remove"), b -> confirmDelete()).build();
            placeAll(layout, new String[]{"delete", "mobs", "blocks", "biomes", "rules"},
                    deleteButton,
                    Button.builder(Component.literal("Mobs"), b -> ScreenNavigator.get().navigateTo(
                            new MobListScreen("tier", originalName))).build(),
                    // This tier's Ores, Vegetation and Block replacements.
                    Button.builder(Component.literal("Blocks"), b -> ScreenNavigator.get().navigateTo(
                            new OreListScreen("tier", originalName))).build(),
                    // The biome list, adding to this tier.
                    Button.builder(Component.literal("+ Add biomes"), b -> ScreenNavigator.get().navigateTo(
                            new BiomeListScreen(originalName))).build(),
                    // The spawn rules and the Special mob spawns switch.
                    Button.builder(Component.literal("Rules"), b -> ScreenNavigator.get().navigateTo(
                            new RulesScreen("tier", originalName))).build());
        }
        // Minecraft runs init again after a dialog closes and on a resize.
        if (current != null) {
            applySnapshot(current);
        }
        current = readFields();
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(readFields());
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private Snapshot readFields() {
        return new Snapshot(nameField.getValue(), levelField.getValue(),
                modeDropdown.getSelected(), coefficientField.getValue());
    }

    private TierOps.TierForm formFromFields() {
        return new TierOps.TierForm(nameField.getValue(), levelField.getValue(),
                modeDropdown.getSelected(), coefficientField.getValue(), "");
    }

    private void applySnapshot(Snapshot s) {
        syncing = true;
        nameField.setValue(s.name());
        levelField.setValue(s.level());
        modeDropdown.setSelected(s.spawnMode());
        coefficientField.setValue(s.coefficient());
        syncing = false;
    }

    private void undo() {
        Snapshot prev = undo.undo();
        if (prev != null) { applySnapshot(prev); current = prev; refreshButtons(); }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = nameField.isValid() && levelField.isValid() && coefficientField.isValid();
    }

    private void save() {
        if (!saveButton.active) return;
        TierOps.TierForm form = formFromFields();
        // Recent records the name the save writes: a new tier or a rename has no name until now.
        editingTier(form.name().strip());
        if (TierOps.isRename(originalName, form.name())) {
            confirmRename(form);
            return;
        }
        if (!report(TierOps.save(originalName, form))) {
            return;
        }
        afterSave(form.name().strip());
    }

    /**
     * Stays after a save: an existing tier keeps its editor with the saved values as the new start; a new
     * tier opens as its own editor, so a second Save edits it.
     */
    private void afterSave(String name) {
        if (originalName.isEmpty() || !originalName.equals(name)) {
            reopen(new TierEditScreen(name));
            return;
        }
        current = readFields();
        baseline = current;
        savedHere();
        rebuildWidgets();
    }

    /** Shows how many rows a rename rewrites before it runs. */
    private void confirmRename(TierOps.TierForm form) {
        TierOps.TierImpact impact = TierOps.impact(originalName);
        String msg = "Rename '" + originalName + "' to '" + form.name().strip() + "'?\n"
                + com.strata.ui.Words.count(impact.total(), "row", "rows") + " will be updated:\n"
                + "  " + com.strata.ui.Words.count(impact.biomes(), "biome link", "biome links") + ", " + com.strata.ui.Words.count(impact.mobs(), "mob spawn", "mob spawns") + ",\n"
                + "  " + com.strata.ui.Words.count(impact.ores(), "ore and vegetation rule", "ore and vegetation rules") + ", " + com.strata.ui.Words.count(impact.spawnRules(), "spawn rule", "spawn rules") + ",\n"
                + "  " + impact.loot() + " loot entries, " + com.strata.ui.Words.count(impact.edits(), "mob edit", "mob edits") + ".";
        minecraft.setScreen(new ConfirmDialog(this, msg, () -> {
            // Deferred, because ConfirmDialog sets its parent screen after this.
            if (report(TierOps.save(originalName, form))) {
                afterSave(form.name().strip());
            }
        }));
    }

    /** Shows everything a delete removes before it runs. */
    private void confirmDelete() {
        TierOps.TierImpact impact = TierOps.impact(originalName);
        String msg = "Remove tier '" + originalName + "'?\n"
                + com.strata.ui.Words.count(impact.biomes(), "biome", "biomes") + " will be left with no tier.\n"
                + "Removed with it: " + com.strata.ui.Words.count(impact.mobs(), "mob spawn", "mob spawns") + ", "
                + com.strata.ui.Words.count(impact.ores(), "ore and vegetation rule", "ore and vegetation rules") + ",\n"
                + com.strata.ui.Words.count(impact.spawnRules(), "spawn rule", "spawn rules") + ", " + impact.loot() + " loot entries,\n"
                + com.strata.ui.Words.count(impact.edits(), "mob edit", "mob edits") + " (equipment, goals, stats, and the like).";
        minecraft.setScreen(new ConfirmDialog(this, msg, () -> {
            // A deleted tier is not recent work to come back to.
            editingTier(null);
            if (report(TierOps.delete(originalName))) {
                deferBack();
            }
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        tooltip(graphics, mouseX, mouseY, layout.get("level"),
                "Area level — mobs in this tier's biomes scale to this level");
        tooltip(graphics, mouseX, mouseY, layout.get("mode"),
                "How this tier's mob list interacts with vanilla spawns:",
                "  Add — tier mobs join the vanilla list",
                "  Replace — tier mobs replace vanilla spawns",
                "    (a Replace tier with no mobs: nothing spawns)");
        tooltip(graphics, mouseX, mouseY, layout.get("coefficient"),
                com.strata.domain.ScalingOps.COEFFICIENT_TIER.toArray(String[]::new));
        // The tier's biomes: the heading, then the names.
        for (int i = 0; layout.get("note" + i) != null; i++) {
            text(graphics, layout.get("note" + i), i == 0 ? Palette.GROUP : Palette.BODY);
        }
        text(graphics, layout.get("footer2"), Palette.LABEL);
        text(graphics, layout.get("footer1"), Palette.MUTED);
    }
}
