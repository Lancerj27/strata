package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.EditField;
import com.strata.client.widget.ToggleSwitch;
import com.strata.client.widget.UndoManager;
import com.strata.domain.ScalingOps;
import com.strata.domain.ScalingOps.CurveForm;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Edit one scaling curve, mob or player. */
public class ScalingEditScreen extends StrataScreen {

    private final boolean mobs;
    private final String originalAttribute;
    /**
     * The mob XP curve, not an attribute's: its per level and exponent, saved as the two mob XP settings.
     */
    private final boolean mobXp;

    private final UndoManager<CurveForm> undo = new UndoManager<>();
    private CurveForm current;
    /** The fields as opened, for the unsaved-edits check. */
    private CurveForm baseline;
    private CurveForm pendingSnapshot;
    private boolean syncing;

    private String selectedAttribute;
    private Button attributeButton;
    /** The mob the curve is previewed on, in real values. */
    private String previewMob = ScalingOps.PREVIEW_MOB;
    private EditField perLevelField;
    private EditField exponentField;
    private EditField startLevelField;
    private ToggleSwitch enabledToggle;
    private Button saveButton;
    private Button undoButton;

    /**
     * @param mobs true for a mob curve, false for a player curve
     * @param row the curve to edit, or null for a new one
     */
    public ScalingEditScreen(boolean mobs, ScalingOps.CurveRow row) {
        this(mobs, row, false);
    }

    /** The mob XP curve's editor: Scaling > Mob > "XP a mob drops". */
    public static ScalingEditScreen forMobXp() {
        return new ScalingEditScreen(true, null, true);
    }

    private ScalingEditScreen(boolean mobs, ScalingOps.CurveRow row, boolean mobXp) {
        super(mobXp ? "Strata — Mob XP" : row == null
                ? (mobs ? "Strata — Mob scaling" : "Strata — Player scaling")
                : "Strata — " + (mobs ? "Mob" : "Player") + " scaling: "
                        + Labels.attribute(row.attribute()));
        this.mobs = mobs;
        this.mobXp = mobXp;
        this.originalAttribute = row == null ? null : row.attribute();
        this.current = mobXp ? new CurveForm("",
                com.strata.domain.OptionsOps.find(com.strata.domain.OptionsOps.MOB_XP_PER_LEVEL).value(),
                com.strata.domain.OptionsOps.find(com.strata.domain.OptionsOps.MOB_XP_EXPONENT).value(), "1", true)
                : ScalingOps.formFor(row);
        this.selectedAttribute = current.attribute();
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
        com.strata.ui.Layout layout = layout();

        attributeButton = addRenderableWidget(place(Button.builder(
                Component.literal(layout.textOf("attribute")), b -> openAttributePicker()).build(),
                layout.get("attribute")));
        if (mobs) {
            addRenderableWidget(place(Button.builder(
                    Component.literal(layout.textOf("mob")), b -> openMobPicker()).build(),
                    layout.get("mob")));
        }
        perLevelField = field(0, 0, 0, 0, "Per level", current.perLevel(), EditField.numericValidator(), this::edited);
        place(perLevelField.getWidget(), layout.get("perLevel"));
        exponentField = field(0, 0, 0, 0, "Exponent", current.exponent(), EditField.numericValidator(), this::edited);
        place(exponentField.getWidget(), layout.get("exponent"));
        startLevelField = field(0, 0, 0, 0, "Start level", current.startLevel(),
                EditField.nonNegativeIntegerValidator(), this::edited);
        place(startLevelField.getWidget(), layout.get("start"));
        enabledToggle = new ToggleSwitch(0, 0, 0, 0, "Enabled", current.enabled());
        enabledToggle.setOnChange(b -> edited());
        addRenderableWidget(place(enabledToggle.getWidget(), layout.get("enabled")));

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        undoButton.active = false;
        if (mobXp) {
            // The XP curve has no attribute, no start level, and no switch of its own: mob scaling's switch
            // turns it on and off.
            attributeButton.active = false;
            setShown(startLevelField.getWidget(), false);
            setShown(enabledToggle.getWidget(), false);
        }
        if (pendingSnapshot != null) {
            applySnapshot(pendingSnapshot);
            current = pendingSnapshot;
            pendingSnapshot = null;
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

    /** The layout for the form as it stands: the attribute and the preview. */
    private com.strata.ui.Layout layout() {
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.scalingEdit(frame(), this.font::width,
                title(), attributeLabel(), mobs ? "Preview on: " + Labels.creature(previewMob) : null,
                preview(), mobs ? mobPreview() : null, mobs ? "Mob scaling" : "Player scaling");
        useLayout(built);
        return built;
    }

    /** The curve at five levels, in one run of text the layout wraps: "Mob Lv 1: +0% · Lv 5: +10%...". */
    private String preview() {
        if (perLevelField == null) {
            return "";
        }
        if (mobXp) {
            // The XP a mob drops, as a multiple of the game's, at the preview levels.
            try {
                com.strata.runtime.MobXpCurve curve = new com.strata.runtime.MobXpCurve(
                        Double.parseDouble(perLevelField.getValue().strip()),
                        Double.parseDouble(exponentField.getValue().strip()));
                StringBuilder s = new StringBuilder("XP");
                for (int i = 0; i < ScalingOps.PREVIEW_LEVELS.size(); i++) {
                    int level = ScalingOps.PREVIEW_LEVELS.get(i);
                    s.append(i == 0 ? " Lv " : "  ·  Lv ").append(level).append(": x")
                            .append(Labels.number(curve.multiplierAt(level)));
                }
                return s.toString();
            } catch (NumberFormatException e) {
                return "";
            }
        }
        java.util.List<Double> bonuses = ScalingOps.bonuses(readFields(), mobs
                ? com.strata.model.ScalingFormulas.Kind.MOB_ATTRIBUTE : com.strata.model.ScalingFormulas.Kind.PLAYER_ATTRIBUTE, 1.0);
        if (bonuses == null) {
            return "";
        }
        StringBuilder s = new StringBuilder(mobs ? "Mob" : "XP");
        for (int i = 0; i < bonuses.size(); i++) {
            s.append(i == 0 ? " Lv " : "  ·  Lv ").append(ScalingOps.PREVIEW_LEVELS.get(i)).append(": +")
                    .append(Labels.number(bonuses.get(i) * 100)).append('%');
        }
        return s.toString();
    }

    /**
     * The curve on the preview mob, in real values: "Zombie, Max Health 20: Lv 1: 22 · Lv 5:
     * 30...".
     */
    private String mobPreview() {
        if (perLevelField == null) {
            return "";
        }
        ScalingOps.MobPreview p = ScalingOps.mobPreview(previewMob, readFields());
        if (p == null) {
            return "";
        }
        String mob = Labels.creature(p.entity());
        String attribute = Labels.attribute(p.attribute());
        if (p.base() == null) {
            return mob + " has no " + attribute + ", so this curve does nothing to it.";
        }
        StringBuilder s = new StringBuilder(mob + ", " + attribute + " " + Labels.number(p.base()) + ":");
        for (int i = 0; i < p.values().size(); i++) {
            s.append(i == 0 ? " Lv " : "  ·  Lv ").append(ScalingOps.PREVIEW_LEVELS.get(i)).append(": ")
                    .append(Labels.number(p.values().get(i)));
        }
        return s.toString();
    }

    /** Picks the mob the curve is previewed on. */
    private void openMobPicker() {
        pendingSnapshot = readFields();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Preview on mob",
                RegistryPickerScreen.entitiesWithRecent(), id -> previewMob = id));
    }

    private String attributeLabel() {
        if (mobXp) {
            return "XP a mob drops";
        }
        return selectedAttribute.isEmpty() ? "Pick an attribute..." : Labels.attribute(selectedAttribute);
    }

    private void openAttributePicker() {
        pendingSnapshot = readFields();
        ScreenNavigator.get().navigateTo(
                new RegistryPickerScreen("Pick an attribute",
                        RegistryQuery.attributes(),
                        id -> {
                            undo.pushState(pendingSnapshot);
                            pendingSnapshot = new CurveForm(id,
                                    pendingSnapshot.perLevel(),
                                    pendingSnapshot.exponent(),
                                    pendingSnapshot.startLevel(),
                                    pendingSnapshot.enabled());
                        }));
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = readFields();
        refreshButtons();
    }

    private CurveForm readFields() {
        return new CurveForm(selectedAttribute, perLevelField.getValue(),
                exponentField.getValue(), startLevelField.getValue(), enabledToggle.getValue());
    }

    private void applySnapshot(CurveForm s) {
        syncing = true;
        selectedAttribute = s.attribute();
        attributeButton.setMessage(Component.literal(layout().textOf("attribute")));
        perLevelField.setValue(s.perLevel());
        exponentField.setValue(s.exponent());
        startLevelField.setValue(s.startLevel());
        enabledToggle.setValue(s.enabled());
        syncing = false;
    }

    private void undo() {
        CurveForm prev = undo.undo();
        if (prev != null) { applySnapshot(prev); current = prev; refreshButtons(); }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = (mobXp || !selectedAttribute.isEmpty())
                && perLevelField.isValid() && exponentField.isValid() && startLevelField.isValid();
    }


    private void save() {
        if (!saveButton.active) return;
        if (mobXp) {
            // The two mob XP settings, each checked and written by OptionsOps.
            if (!report(com.strata.domain.OptionsOps.set(com.strata.domain.OptionsOps.MOB_XP_PER_LEVEL,
                    perLevelField.getValue()))
                    || !report(com.strata.domain.OptionsOps.set(com.strata.domain.OptionsOps.MOB_XP_EXPONENT,
                    exponentField.getValue()))) {
                return;
            }
            // A small form goes back to its list.
            backSaved();
            return;
        }
        CurveForm saved = readFields();
        if (!report(ScalingOps.save(mobs, originalAttribute, saved))) {
            return;
        }
        // A small form goes back to its list.
        backSaved();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
                // The previews: the percent lines, then the real values on the mob.
        int notes = mobs ? 2 * com.strata.ui.screen.FormScreenLayouts.PREVIEW_LINES
                : com.strata.ui.screen.FormScreenLayouts.PREVIEW_LINES;
        for (int i = 0; i < notes; i++) {
            text(graphics, layout.get("note" + i),
                    i < com.strata.ui.screen.FormScreenLayouts.PREVIEW_LINES ? Palette.LABEL : Palette.TEXT);
        }
        text(graphics, layout.get("footer1"), Palette.MUTED);

        tooltip(graphics, mouseX, mouseY, layout.get("perLevel"),
                mobXp ? "XP per level: the XP a mob drops is 1 + this x level ^ exponent" : "Bonus per level: how much this attribute",
                mobXp ? "times the game's XP. 0 gives the game's XP at every level."
                        : mobs ? "increases for each mob level" : "increases for each player level");
        // What the attribute does.
        if (!mobXp && !selectedAttribute.isEmpty()) {
            tooltip(graphics, mouseX, mouseY, layout.get("attribute"),
                    wrapTip(com.strata.model.AttributeGuide.scaled(selectedAttribute, !mobs)));
        }
        tooltip(graphics, mouseX, mouseY, layout.get("exponent"),
                "Exponent for the scaling curve.",
                "1.0 = linear, < 1.0 = diminishing returns");
        tooltip(graphics, mouseX, mouseY, layout.get("start"),
                "The level where the bonus begins.",
                "Start level + 0 gets the first increment,",
                "earlier levels get none. Default 1.");
        if (mobs) {
            tooltip(graphics, mouseX, mouseY, layout.get("mob"),
                    "Click to pick the mob the preview uses.",
                    "Its base value is the game's, or your edit.",
                    "A tier, biome or variant coefficient multiplies the bonus.");
        }
    }
}
