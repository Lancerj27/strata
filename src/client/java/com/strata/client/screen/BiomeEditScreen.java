package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.BiomeOps;
import com.strata.domain.TierOps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Edit one biome: tier assignment plus per-biome overrides (level, switches, coefficient). */
public class BiomeEditScreen extends StrataScreen {

    private record Snapshot(String tier, String level, String scaleMobs,
                            String restrictOres, String substituteSpawns,
                            String coefficient) {}

    private static final String NO_TIER = "(none)";
    private static final String INHERIT = "inherit";
    private static final String ON = "on";
    private static final String OFF = "off";
    private static final List<String> TRI = List.of(INHERIT, ON, OFF);

    private final String biomeId;
    private final UndoManager<Snapshot> undo = new UndoManager<>();
    private Snapshot current;
    /** The fields as opened, for the unsaved-edits check. */
    private Snapshot baseline;
    /** What the saved config says, read once per init for the labels. */
    private BiomeOps.BiomeForm saved;
    private Integer savedTierLevel;
    private int overrideCount;

    /** The tier, chosen in a picker. */
    private Button tierButton;
    private String selectedTier = NO_TIER;
    private EditField levelField;
    private DropdownSelect scaleMobs;
    private DropdownSelect restrictOres;
    private DropdownSelect substituteSpawns;
    /** Whether this Replace biome blocks other spawns. */
    private EditField coefficientField;
    private Button saveButton;
    private Button undoButton;
    private boolean syncing;

    /** The screen's layout, built in init. */
    private com.strata.ui.Layout layout;

    /**
     * The layout for the tier and the labels as they stand.
     *
     * @param levelNow the level override as typed now
     */
    private com.strata.ui.Layout layout(String levelNow) {
        String levelLabel = "Level override (blank = inherit)";
        if (savedTierLevel != null && (saved.level().isEmpty() || levelNow.strip().isEmpty())) {
            levelLabel = "Level override (blank = inherit: " + savedTierLevel + " from " + saved.tier() + ")";
        }
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.biomeEdit(frame(), this.font::width,
                title(), tierLabel(), levelLabel, TRI,
                overrideCount > 0 ? com.strata.ui.Words.count(overrideCount, "override", "overrides") + " diverge from tier " + saved.tier() : "");
        useLayout(built);
        return built;
    }

    public BiomeEditScreen(String biomeId) {
        super("Strata — Biome: " + com.strata.util.Labels.biome(biomeId));
        this.biomeId = biomeId;
        editingBiome(biomeId);
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    @Override
    protected void init() {
        super.init();

        saved = BiomeOps.formFor(biomeId);
        savedTierLevel = BiomeOps.tierLevel(saved.tier());
        overrideCount = BiomeOps.overrideCount(biomeId);

        // The layout is the UI layer's: the actions are pinned.
        selectedTier = saved.tier() == null ? NO_TIER : saved.tier();
        layout = layout(saved.level());

        tierButton = addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("tier")),
                b -> openTierPicker()).build(), layout.get("tier")));
        levelField = field(0, 0, 0, 0, "Level override", saved.level(), EditField.nonNegativeIntegerValidator(),
                this::edited);
        place(levelField.getWidget(), layout.get("level"));
        scaleMobs = triState("Scale mobs", saved.scaleMobs());
        restrictOres = triState("Restrict ores", saved.restrictOres());
        substituteSpawns = triState("Substitute spawns", saved.substituteSpawns());
        addRenderableWidget(place(scaleMobs.getWidget(), layout.get("scaleMobs")));
        addRenderableWidget(place(restrictOres.getWidget(), layout.get("restrictOres")));
        addRenderableWidget(place(substituteSpawns.getWidget(), layout.get("substitute")));
        coefficientField = field(0, 0, 0, 0, "Coefficient override", saved.coefficient(),
                EditField.coefficientValidator(), this::edited);
        place(coefficientField.getWidget(), layout.get("coefficient"));

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        undoButton.active = false;

        // Navigation buttons live under the last field row, never on the action row, where they overlapped
        // Undo and Save.
        placeAll(layout, new String[]{"mobs", "blocks", "structures", "biomeData", "climate", "rules"},
                Button.builder(Component.literal("Mobs"), b -> ScreenNavigator.get().navigateTo(
                        new MobListScreen("biome", biomeId))).build(),
                // This biome's Ores, Vegetation and Block replacements.
                Button.builder(Component.literal("Blocks"), b -> ScreenNavigator.get().navigateTo(
                        new OreListScreen("biome", biomeId))).build(),
                // The structures this biome takes; the registry is World > Structures.
                Button.builder(Component.literal("Structures"), b -> ScreenNavigator.get().navigateTo(
                        OverridesScreen.structuresIn(biomeId))).build(),
                Button.builder(Component.literal("Biome data"), b -> ScreenNavigator.get().navigateTo(
                        new BiomeDataScreen(biomeId))).build(),
                // The biome's JSON - climate, looks, features - here rather than a second biome list under
                // World.
                Button.builder(Component.literal("Climate & looks"), b -> ScreenNavigator.get().navigateTo(
                        OverridesScreen.entry(com.strata.data.JsonOverrides.BIOME, biomeId))).build(),
                // The spawn rules and the Special mob spawns switch.
                Button.builder(Component.literal("Rules"), b -> ScreenNavigator.get().navigateTo(
                        new RulesScreen("biome", biomeId))).build());
        // Minecraft runs init again on a return from Mobs or Ores and on a resize.
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

    private String tierLabel() {
        return "Tier: " + selectedTier;
    }

    /** Picks the tier. */
    private void openTierPicker() {
        List<String> choices = new ArrayList<>();
        choices.add(NO_TIER);
        choices.addAll(TierOps.names());
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick a tier",
                RegistryPickerScreen.plainOptions("tier", choices),
                id -> {
                    undo.pushState(current);
                    current = new Snapshot(id, current.level(), current.scaleMobs(),
                            current.restrictOres(), current.substituteSpawns(),
                            current.coefficient());
                }));
    }

    private DropdownSelect triState(String label, Boolean value) {
        DropdownSelect d = new DropdownSelect(0, 0, 0, 0, label, TRI, word(value));
        d.setOnChange(s -> edited());
        return d;
    }

    /** A switch as the dropdown word. */
    private static String word(Boolean value) {
        return value == null ? INHERIT : value ? ON : OFF;
    }

    /** The dropdown word as a switch: null for inherit. */
    private static Boolean value(DropdownSelect d) {
        return switch (d.getSelected()) {
            case ON -> Boolean.TRUE;
            case OFF -> Boolean.FALSE;
            default -> null;
        };
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private Snapshot readFields() {
        return new Snapshot(selectedTier, levelField.getValue(),
                scaleMobs.getSelected(), restrictOres.getSelected(),
                substituteSpawns.getSelected(), coefficientField.getValue());
    }

    private void applySnapshot(Snapshot s) {
        syncing = true;
        selectedTier = s.tier();
        tierButton.setMessage(Component.literal(layout(s.level()).textOf("tier")));
        levelField.setValue(s.level());
        scaleMobs.setSelected(s.scaleMobs());
        restrictOres.setSelected(s.restrictOres());
        substituteSpawns.setSelected(s.substituteSpawns());
        coefficientField.setValue(s.coefficient());
        syncing = false;
    }

    private void undo() {
        Snapshot prev = undo.undo();
        if (prev != null) { applySnapshot(prev); current = prev; refreshButtons(); }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = levelField.isValid() && coefficientField.isValid();
    }

    private void save() {
        if (!saveButton.active) return;
        BiomeOps.BiomeForm form = new BiomeOps.BiomeForm(
                NO_TIER.equals(selectedTier) ? null : selectedTier,
                levelField.getValue(), value(scaleMobs), value(restrictOres),
                value(substituteSpawns), coefficientField.getValue(), null);
        if (!report(BiomeOps.save(biomeId, form))) {
            return;
        }
        // Save stays: the saved values are the new start, and the labels (the tier's level, the overrides)
        // are read again.
        current = readFields();
        baseline = current;
        savedHere();
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        layout = layout(levelField.getValue());
        titles(graphics, layout);
        // Conflict warning: the biome has its own rules that may clash with its tier.
        text(graphics, layout.get("note0"), Palette.GROUP);

        tooltip(graphics, mouseX, mouseY, layout.get("level"),
                "Blank = inherit level from the assigned tier.",
                "Set a number to override the tier's level for this biome.");
        tooltip(graphics, mouseX, mouseY, layout.get("scaleMobs"),
                "Scale mobs: whether mob stats scale in this biome.",
                "  Inherit = use the tier's setting",
                "  On/Off = override the tier for this biome");
        tooltip(graphics, mouseX, mouseY, layout.get("restrictOres"),
                "Restrict ores: whether ore restrictions apply here.",
                "  Inherit = use the tier's setting",
                "  On/Off = override the tier for this biome");
        tooltip(graphics, mouseX, mouseY, layout.get("substitute"),
                "Substitute spawns: whether tier spawn rules apply.",
                "  Inherit = use the tier's setting",
                "  On/Off = override the tier for this biome");
        tooltip(graphics, mouseX, mouseY, layout.get("coefficient"),
                com.strata.domain.ScalingOps.COEFFICIENT_BIOME.toArray(String[]::new));
    }
}