package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.client.widget.ToggleSwitch;
import com.strata.client.widget.UndoManager;
import com.strata.domain.LootOps;
import com.strata.domain.MobOps;
import com.strata.domain.VariantOps;
import com.strata.domain.VariantOps.VariantForm;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

/** VARIANT EDITOR. */
public class MobEditScreen extends StrataScreen {

    /** No preset: the mob keeps the game's own AI. */

    /** How the editor was opened. */
    private final VariantOps.EditorOpen at;
    // INSTANCING: the scope this editor was OPENED in, captured once.
    private String openedScope;
    private String openedScopeId;
    private final UndoManager<VariantForm> undo = new UndoManager<>();
    private VariantForm current;
    /** The fields as opened, for the unsaved-edits check. */
    private VariantForm baseline;
    private VariantForm pendingSnapshot;
    private boolean syncing;

    private EditField nameField;
    private String scopeValue = "tier";
    private Button scopeButton;
    /** A new row's target: a tier or a biome, PICKED, never typed. */
    private String selectedTarget;
    /** What Restore all removes, read at init. */
    private List<MobOps.ResetPart> resetParts = List.of();
    private Button targetButton;
    private Button baseEntityButton;
    private String selectedBaseEntity;
    private EditField weightField;
    private EditField minCountField;
    private EditField maxCountField;
    /** The spawn chance %. */
    private EditField chanceField;
    private ToggleSwitch enabledToggle;
    /** The mob's own scaling switch, back beside Coefficient. */
    private com.strata.client.widget.DropdownSelect scalingChoice;
    private EditField coefficientField;
    /** The mob's own level; blank is 1. */
    private EditField startLevelField;
    private EditField displayNameField;
    /** The row as stored: its scaling, mob griefing, and texture, which Save keeps. */
    private VariantForm stored;
    private Button goalSetButton;
    /** The chosen behavior, as a choice: one of VariantOps#defaultChoices, or a behavior's name. */
    private String selectedGoalSet = VariantOps.GAME_DEFAULT;
    /**
     * The behavior last picked in the picker, as stored, or null: a pick replaces this
     * scope's own AI list on Save, while the form still holds it.
     */
    private String pickedGoalSet;
    private Button saveButton;
    private Button undoButton;
    /** The mob's own scaling switch. */

    /** The footer's two lines, built in init rather than per frame. */
    private String[] cachedDataLines;

    /** Create-from-mob constructor: a variant row, or a new variant. */
    public MobEditScreen(String name, String baseEntity,
                             String sourceScope, String sourceScopeId) {
        this(name, baseEntity, sourceScope, sourceScopeId, false);
    }

    /**
     * ONE EDITOR FOR MOBS AND VARIANTS.
     *
     * @param baseMobRow true when this opens the BASE row for an entity - the one whose variant_name is
     *     blank. It stays blank on save unless the designer types a name, which promotes the row to a variant.
     *     With a null baseEntity it is a NEW plain mob: the "+ Add mob" button.
     */
    public MobEditScreen(String name, String baseEntity,
                             String sourceScope, String sourceScopeId,
                             boolean baseMobRow) {
        super(name == null && !baseMobRow ? "Strata — New variant"
                : baseMobRow && baseEntity == null ? "Strata — New mob"
                : baseMobRow ? "Strata — Mob: " + Labels.creature(baseEntity)
                : "Strata — Variant: " + name);
        this.at = VariantOps.EditorOpen.of(name, baseEntity, sourceScope, sourceScopeId, baseMobRow);
    }

    /**
     * The screen's layout: every widget's box and every line of text, from
     * com.strata.ui.screen.MobEditLayout, which the fit gate checks at every size.
     */
    private com.strata.ui.Layout layout;
    /** The target the layout and the footer were read for. */
    private String layoutTarget = "";

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    /** The layout for the form as it stands. */
    private com.strata.ui.Layout buildLayout(VariantForm form) {
        boolean plainMob = plain(form.name());
        // The behavior in effect, by name, and its AI in the game's two lists.
        String owner = at.originalName().isEmpty() ? com.strata.model.Variant.defaultNameFor(selectedBaseEntity)
                : at.originalName();
        com.strata.domain.GoalsOps.Effective effective = com.strata.domain.GoalsOps.effective(owner, selectedBaseEntity,
                scopeValue, layoutTarget, VariantOps.goalSetOfChoice(selectedGoalSet, plainMob),
                goalSetPicked(form));
        return com.strata.ui.screen.MobEditLayout.build(frame(), this.font::width,
                new com.strata.ui.screen.MobEditLayout.Input(title(), at.isNew(),
                        !at.isNew() && (!at.baseMobRow() || MobOps.removableFromScope(openedScope, openedScopeId)),
                        !at.isNew(), !resetParts.isEmpty(), true,
                        "Scope: " + Labels.pretty(scopeValue), targetText(),
                        Labels.scope(scopeValue, openedScopeId),
                        Labels.creature(selectedBaseEntity),
                        com.strata.domain.GoalsOps.goalSetLabel(effective, VariantOps.goalSetOfChoice(selectedGoalSet, plainMob)),
                        com.strata.domain.GoalsOps.goalsText(effective.rows()),
                        at.baseMobRow() && !at.isNew()
                                ? "Variant name (a name turns this mob into a variant)"
                                : "Variant name (blank = plain mob)",
                        VariantOps.isVariantName(form.name()), form.enabled(),
                        cachedDataLines[1], cachedDataLines[0],
                        com.strata.domain.TradesOps.trades(selectedBaseEntity),
                        com.strata.ui.Layout.choiceText("Scaling", VariantOps.switchWord(form.scaling()))));
    }

    private com.strata.ui.Layout.Element el(String id) {
        return layout.get(id);
    }

    @Override
    protected void init() {
        super.init();
        VariantForm start = loadForm();
        placeActionsAndTools();
        placeScopeAndBase(start);
        placeFields(start);
        restoreEdits();
    }

    /** Opens the row, picks the form the controls are built from, and builds the layout. */
    private VariantForm loadForm() {
        VariantOps.Opened opened = VariantOps.open(at);
        VariantForm start = opened.form();
        // The values the Spawning and Model tabs of Mob data set: read again at every init, so Save keeps
        // what those tabs saved.
        stored = start;
        // Captured on the FIRST init only.
        if (openedScope == null) {
            openedScope = opened.openedScope();
            openedScopeId = opened.openedScopeId();
        }
        scopeValue = start.scope();
        selectedBaseEntity = start.baseEntity();
        // A picker return or an undo rebuild carries its own base and scope, and the dependent controls must
        // be built from those.
        VariantForm now = start;
        if (pendingSnapshot != null) {
            now = pendingSnapshot;
        } else if (current != null) {
            now = current;
        }
        selectedBaseEntity = now.baseEntity();
        scopeValue = now.scope();

        // The target the row is filed under: a new row's from its form, an existing row's the one it was
        // opened in.
        layoutTarget = "global".equals(scopeValue) ? ""
                : at.isNew() ? (now.scopeId() == null ? "" : now.scopeId())
                : (openedScopeId == null ? "" : openedScopeId);

        // The footer lines and the inherited behavior: after selectedBaseEntity is final, or they count
        // against the wrong entity after a picker return or an undo.
        cachedDataLines = vanillaDataLines(layoutTarget);
        selectedGoalSet = VariantOps.choiceOfGoalSet(now.goalSet(), plain(now.name()));
        // What Reset to game would remove: a saved plain mob's edits.
        resetParts = !at.isNew() && at.baseMobRow() ? MobOps.resetParts(now.baseEntity()) : List.of();
        layout = buildLayout(now);
        useLayout(layout);
        return start;
    }

    /** The pinned part: Undo, Save, Remove, the tools and Restore all. */
    private void placeActionsAndTools() {
        // ---------- PINNED: the actions, the tools, the behavior ---------- THE ACTIONS NEVER SCROLL.
        undoButton = addRenderableWidget(place(Button.builder(Component.literal("Undo"), b -> undo()).build(),
                el("undo")));
        undoButton.active = false;
        saveButton = addRenderableWidget(place(Button.builder(Component.literal("Save"), b -> save()).build(),
                el("save")));
        // A plain mob has Delete only where it can be removed: a named tier or biome.
        if (el("delete") != null) {
            addRenderableWidget(place(Button.builder(Component.literal("Remove"), b -> confirmDelete()).build(),
                    el("delete")));
        }

        // The tools: equipment and drops must be reachable for ANY mob.
        Button spawnRulesButton = Button.builder(Component.literal(el("spawnRules").text()), b -> openSpawnRules()).build();
        // THE VARIANT IS PASSED THROUGH.
        Button lootButton = Button.builder(Component.literal(el("loot").text()), b -> openLoot()).build();
        // Same for equipment: the scope decides WHICH instance's loadout is edited.
        Button equipmentButton = Button.builder(Component.literal(el("equipment").text()), b -> openExtras(true)).build();
        // Every value extracted from the game for this mob, editable.
        Button attributesButton = Button.builder(Component.literal("Mob data"), b -> openMobData()).build();
        Button abilitiesButton = Button.builder(Component.literal("Abilities"), b -> openExtras(false)).build();
        // Every biome where the mob spawns naturally, each On or Off (design 8.1).
        Button biomesButton = Button.builder(Component.literal("Biomes"),
                b -> ScreenNavigator.get().navigateTo(new MobBiomesScreen(selectedBaseEntity))).build();
        String[] toolIds = {"spawnRules", "loot", "equipment", "mobdata", "abilities", "biomes"};
        Button[] tools = {spawnRulesButton, lootButton, equipmentButton, attributesButton, abilitiesButton, biomesButton};
        for (int i = 0; i < tools.length; i++) {
            addRenderableWidget(place(tools[i], el(toolIds[i])));
            tools[i].active = el(toolIds[i]).active();
        }
        // Trades: a villager's or the wandering trader's lists, at this scope.
        if (el("trades") != null) {
            Button tradesButton = addRenderableWidget(place(Button.builder(Component.literal("Trades"),
                    b -> ScreenNavigator.get().navigateTo(new TradesScreen(selectedBaseEntity, scopeValue, targetValue())))
                    .build(), el("trades")));
            tradesButton.active = el("trades").active();
            saveFirst(tradesButton);
        }
        saveFirst(spawnRulesButton, lootButton, equipmentButton, attributesButton, abilitiesButton, biomesButton);
        Button resetButton = addRenderableWidget(place(Button.builder(Component.literal("Restore all"),
                b -> confirmReset()).build(), el("reset")));
        resetButton.active = el("reset").active();
    }

    /** The scope and target (a new row only) and the base entity. */
    private void placeScopeAndBase(VariantForm start) {
        // ---------- THE FIELDS: they scroll under the pinned part ---------- THE SCOPE IS LOCKED TO THE ONE
        // YOU CAME IN THROUGH.
        if (at.isNew()) {
            // A DROPDOWN: it cycled, the one choice left that did not show its options after.
            com.strata.client.widget.DropdownSelect scopeChoice = new com.strata.client.widget.DropdownSelect(
                    0, 0, 0, 0, "Scope", VariantOps.SCOPE_CHOICES, scopeValue);
            scopeChoice.setOnChange(this::changeScope);
            scopeButton = addRenderableWidget(place(scopeChoice.getWidget(), el("scope")));
            selectedTarget = start.scopeId() == null ? "" : start.scopeId();
            targetButton = addRenderableWidget(place(Button.builder(Component.literal(el("target").text()),
                    b -> openTargetPicker()).build(), el("target")));
            targetButton.active = !"global".equals(scopeValue);
        } else {
            scopeButton = null;
            selectedTarget = null;
            targetButton = null;
        }

        baseEntityButton = addRenderableWidget(place(Button.builder(Component.literal(el("base").text()),
                b -> openBaseEntityPicker()).build(), el("base")));
    }

    /** The fields that scroll: spawn numbers, switches, level, name, behavior and display name. */
    private void placeFields(VariantForm start) {
        // Values the game accepts: Min and Max are checked together, so each reads the other.
        weightField = field(0, 0, 0, 0, "Spawn weight", start.weight(), (EditField.Check) VariantOps::weightProblem,
                this::edited);
        place(weightField.getWidget(), el("weight"));

        minCountField = field(0, 0, 0, 0, "Min", start.minCount(), null, this::edited);
        place(minCountField.getWidget(), el("min"));
        maxCountField = field(0, 0, 0, 0, "Max", start.maxCount(), null, this::edited);
        place(maxCountField.getWidget(), el("max"));
        minCountField.setValidator((EditField.Check) s -> VariantOps.groupProblem(s, maxCountField.getValue()));
        maxCountField.setValidator((EditField.Check) s -> VariantOps.groupProblem(minCountField.getValue(), s));
        chanceField = field(0, 0, 0, 0, "Chance %", start.spawnChance(), (EditField.Check) VariantOps::chanceProblem,
                this::edited);
        place(chanceField.getWidget(), el("chance"));

        enabledToggle = new ToggleSwitch(0, 0, 0, 0, "Enabled", start.enabled());
        enabledToggle.setOnChange(b -> edited());
        addRenderableWidget(place(enabledToggle.getWidget(), el("enabled")));
        scalingChoice = new com.strata.client.widget.DropdownSelect(0, 0, 0, 0, "Scaling",
                VariantOps.SWITCH_CHOICES, VariantOps.switchWord(start.scaling()));
        scalingChoice.setOnChange(v -> edited());
        Button scalingButton = addRenderableWidget(place(scalingChoice.getWidget(), el("scaling")));
        scalingButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(VariantOps.SCALING_HELP)));

        coefficientField = field(0, 0, 0, 0, "Coefficient", start.coefficient(), EditField.numericValidator(),
                this::edited);
        place(coefficientField.getWidget(), el("coefficient"));

        startLevelField = field(0, 0, 0, 0, "Level", start.startLevel(),
                (EditField.Check) VariantOps::startLevelProblem, this::edited);
        place(startLevelField.getWidget(), el("startLevel"));
        startLevelField.getWidget().setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "This mob's own level. Blank is 1. With scaling on, an area of a higher level makes it"
                        + " stronger and a lower one weaker, relative to this level. With scaling off,"
                        + " it is always this level.")));

        // A BASE ROW OPENS WITH AN EMPTY NAME, and that is the whole of what keeps it a base row
        // (VariantOps#initialName).
        nameField = new EditField(this.font, 0, 0, 0, 0, "Name", start.name());
        nameField.setValidator((EditField.Check) s -> VariantOps.nameProblem(s, at));
        nameField.setResponder(s -> edited());
        addRenderableWidget(place(nameField.getWidget(), el("name")));

        // ---------- THE AI: its own section, under the name ---------- The Behavior button opens the
        // Behaviors screen for this mob: it picks a behavior, or opens the AI editor.
        goalSetButton = addRenderableWidget(place(Button.builder(Component.literal(el("goalSet").text()),
                b -> openGoalSetPicker()).build(), el("goalSet")));

        // ---------- THE VARIANT SECTION ----------
        displayNameField = field(0, 0, 0, 0, "Display name", start.displayName(), EditField.csvTextValidator(),
                this::edited);
        place(displayNameField.getWidget(), el("display"));
    }

    /** Puts back the edits a re-init would lose, then sets the baseline and the buttons. */
    private void restoreEdits() {
        // Returning from the base-entity picker or the Scope button: apply the snapshot captured before, or
        // the picker selection and every unsaved edit would be lost to the rebuild.
        if (pendingSnapshot != null) {
            VariantForm snap = pendingSnapshot;
            pendingSnapshot = null;
            applySnapshot(snap);
            current = snap;
        } else if (current != null) {
            applySnapshot(current);
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

    /** Says why a nav button is grey on a new row. */
    private static void saveFirst(Button... buttons) {
        for (Button button : buttons) {
            if (!button.active) {
                button.setTooltip(Tooltip.create(Component.literal(
                        "Save this mob first - it does not exist yet")));
            }
        }
    }

    /** A blank variant name is a plain mob. */
    private static boolean plain(String name) {
        return name == null || name.isBlank();
    }

    /** Opens Equipment or Abilities. */
    private void openExtras(boolean equipment) {
        String name = at.originalName();
        if (name.isEmpty()) {
            MobOps.DefaultVariant made = MobOps.ensureDefaultVariant(selectedBaseEntity);
            // A failure shows; a success is not an Undo step of its own: the made
            // variant joins the next change.
            if (!made.report().ok()) {
                report(made.report());
                return;
            }
            name = made.name();
        }
        ScreenNavigator.get().navigateTo(equipment
                ? new EquipmentScreen(name, selectedBaseEntity, scopeValue, targetValue())
                : new AbilitiesScreen(name, selectedBaseEntity, scopeValue, targetValue()));
    }

    /** The target this row is filed under. */
    private void openSpawnRules() {
        ScreenNavigator.get().navigateTo(new SpawnRulesScreen(scopeValue, targetValue(),
                selectedBaseEntity, at.originalName()));
    }

    private void openLoot() {
        ScreenNavigator.get().navigateTo(new LootScreen(scopeValue, targetValue(),
                selectedBaseEntity, at.originalName()));
    }

    private void openMobData() {
        ScreenNavigator.get().navigateTo(new MobDataScreen(selectedBaseEntity, at.originalName(),
                scopeValue, targetValue()));
    }

    private String targetValue() {
        if (selectedTarget != null) {
            return selectedTarget.strip();
        }
        return openedScopeId == null ? "" : openedScopeId;
    }

    /** The target button's text: the picked tier or biome in words. */
    private String targetText() {
        if ("global".equals(scopeValue)) {
            return "Target: none (global)";
        }
        String target = targetValue();
        if (target.isEmpty()) {
            return "Target: pick one";
        }
        return "Target: " + ("biome".equals(scopeValue) ? Labels.biome(target) : target);
    }

    /**
     * Picks a new row's target from the tiers or from every game biome, not a typed id.
     */
    private void openTargetPicker() {
        VariantForm base = readFields();
        pendingSnapshot = base;
        List<RegistryQuery.Option> options = new java.util.ArrayList<>();
        if ("biome".equals(scopeValue)) {
            com.strata.game.BiomeCatalog.all(minecraft != null && minecraft.level != null
                    ? worldRegistries() : null)
                    .forEach(b -> options.add(new RegistryQuery.Option(b.id(), Labels.biome(b.id()), b.dimension())));
        } else {
            options.addAll(RegistryPickerScreen.plainOptions("tier", VariantOps.targetChoices(scopeValue)));
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "biome".equals(scopeValue) ? "Pick a biome" : "Pick a tier",
                options,
                id -> {
                    undo.pushState(current);
                    pendingSnapshot = new VariantForm(
                            base.name(), base.displayName(), base.baseEntity(),
                            base.coefficient(), base.texture(),
                            base.ownEntity(), base.goalSet(),
                            base.scope(), id,
                            base.weight(), base.minCount(),
                            base.maxCount(), base.enabled(), base.scaling(), base.spawnChance(), base.mobGriefing(), base.category(),
                            base.startLevel());
                }));
    }

    /** Sets the scope to one of VariantOps.SCOPE_CHOICES, from the Scope dropdown. */
    private void changeScope(String next) {
        VariantForm base = readFields();
        scopeValue = next;
        pendingSnapshot = new VariantForm(base.name(), base.displayName(),
                base.baseEntity(), base.coefficient(), base.texture(),
                base.ownEntity(), base.goalSet(),
                scopeValue, "", base.weight(), base.minCount(),
                base.maxCount(), base.enabled(), base.scaling(), base.spawnChance(), base.mobGriefing(), base.category(),
                            base.startLevel());
        // An undoable edit like any other.
        undo.pushState(current);
        clearWidgets();
        init();
    }

    private void openBaseEntityPicker() {
        // Carry the FULL form across the picker round-trip.
        VariantForm base = readFields();
        pendingSnapshot = base;
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "Pick the base mob",
                RegistryPickerScreen.entitiesWithRecent(),
                id -> {
                    undo.pushState(current);
                    pendingSnapshot = VariantOps.withStartOf(at.isNew(), base, id);
                }));
    }


    /** Opens the mob's AI list: every goal and its settings, editable. */
    private void openGoalsEditor() {
        String owner = at.originalName();
        if (owner.isEmpty()) {
            MobOps.DefaultVariant made = MobOps.ensureDefaultVariant(selectedBaseEntity);
            // A failure shows; a success is not an Undo step of its own: the made
            // variant joins the next change.
            if (!made.report().ok()) {
                report(made.report());
                return;
            }
            owner = made.name();
        }
        // A list saved as a named behavior is this mob's behavior now: the table has it, so the form and its
        // baseline take it too.
        ScreenNavigator.get().navigateTo(GoalsScreen.forMob(owner, selectedBaseEntity, scopeValue, targetValue(),
                VariantOps.goalSetOfChoice(selectedGoalSet, plain(nameField.getValue())), named -> {
                    // The AI editor saved the mob's AI here: no pick is pending.
                    pickedGoalSet = null;
                    VariantForm form = pendingSnapshot != null ? pendingSnapshot : current;
                    if (form != null) {
                        pendingSnapshot = form.withGoalSet(named);
                    }
                    if (baseline != null) {
                        baseline = baseline.withGoalSet(named);
                    }
                }));
    }
    /**
     * THE BEHAVIOR MENU: the table's library, the designer's own included, less the ones this mob cannot use,
     * and first of all "Edit this mob's goals".
     */
    private void openGoalSetPicker() {
        VariantForm base = readFields();
        pendingSnapshot = base;
        // THE BEHAVIORS SCREEN, NOT A PICKER: Client > Behaviors, with Use for this mob and Edit this mob's
        // AI.
        ScreenNavigator.get().navigateTo(GoalSetsScreen.forMob(new GoalSetsScreen.MobPick(
                selectedBaseEntity, plain(base.name()), selectedGoalSet,
                id -> {
                    undo.pushState(current);
                    pickedGoalSet = VariantOps.goalSetOfChoice(id, plain(base.name()));
                    pendingSnapshot = new VariantForm(
                            base.name(), base.displayName(), base.baseEntity(),
                            base.coefficient(), base.texture(),
                            base.ownEntity(), VariantOps.goalSetOfChoice(id, plain(base.name())),
                            base.scope(), base.scopeId(),
                            base.weight(), base.minCount(),
                            base.maxCount(), base.enabled(), base.scaling(), base.spawnChance(), base.mobGriefing(), base.category(),
                            base.startLevel());
                },
                // After the screen goes back: this screen is back, its form put back from the snapshot, and
                // then the AI editor opens.
                () -> deferTo(this::openGoalsEditor))));
    }

    private void edited() {
        // Min and Max read each other: a change to one can clear the other's red.
        minCountField.recheck();
        maxCountField.recheck();
        if (syncing) return;
        undo.pushState(current);
        current = readFields();
        // A name turns a plain mob into a variant, which changes what the behavior field means (default from
        // the base mob) and shows the variant section.
        relayout();
        refreshButtons();
    }

    /**
     * The layout again, for the text as it stands now: the behavior, the base entity, the scope, a name, a
     * toggle.
     */
    private void relayout() {
        layoutTarget = "global".equals(scopeValue) ? "" : targetValue();
        int goalsHeight = el("goalsBox").box().h();
        layout = buildLayout(readFields());
        useLayout(layout);
        if (el("goalsBox").box().h() != goalsHeight) {
            // The AI now has more or fewer lines (a name makes a plain mob a variant, which inherits another
            // AI): the fields under the box move, so the form is laid out again, its edits kept.
            current = readFields();
            rebuildWidgets();
            return;
        }
        goalSetButton.setMessage(Component.literal(el("goalSet").text()));
        baseEntityButton.setMessage(Component.literal(el("base").text()));
        if (scopeButton != null) {
            scopeButton.setMessage(Component.literal(el("scope").text()));
        }
        if (targetButton != null) {
            targetButton.setMessage(Component.literal(el("target").text()));
            targetButton.active = !"global".equals(scopeValue);
        }
    }

    private VariantForm readFields() {
        String goalSet = selectedGoalSet;
        String target = "global".equals(scopeValue) ? "" : targetValue();
        return new VariantForm(nameField.getValue(), displayNameField.getValue(),
                selectedBaseEntity, coefficientField.getValue(),
                // Every variant is its own mob.
                stored.texture(), true,
                VariantOps.goalSetOfChoice(goalSet, plain(nameField.getValue())), scopeValue, target,
                weightField.getValue(), minCountField.getValue(),
                maxCountField.getValue(), enabledToggle.getValue(),
                VariantOps.switchStored(scalingChoice.getSelected()), chanceField.getValue(), stored.mobGriefing(), "",
                startLevelField.getValue());
    }

    private void applySnapshot(VariantForm s) {
        syncing = true;
        String scope = s.scope() == null || s.scope().isEmpty() ? "tier" : s.scope();
        scopeValue = scope;
        if (selectedTarget != null) {
            String target = s.scopeId() == null ? "" : s.scopeId();
            if (target.isEmpty() && !"global".equals(scope)) {
                target = VariantOps.targetChoices(scope).getFirst();
            }
            selectedTarget = target;
        }
        nameField.setValue(s.name());
        displayNameField.setValue(s.displayName());
        selectedBaseEntity = s.baseEntity();
        weightField.setValue(s.weight());
        minCountField.setValue(s.minCount());
        maxCountField.setValue(s.maxCount());
        chanceField.setValue(s.spawnChance());
        enabledToggle.setValue(s.enabled());
        scalingChoice.setSelected(VariantOps.switchWord(s.scaling()));
        coefficientField.setValue(s.coefficient());
        startLevelField.setValue(s.startLevel());
        selectedGoalSet = VariantOps.choiceOfGoalSet(s.goalSet(), plain(s.name()));
        syncing = false;
        relayout();
    }

    private void undo() {
        VariantForm prev = undo.undo();
        if (prev != null) { current = prev; applySnapshot(prev); refreshButtons(); }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        // A locked row has no Target field, and a missing control is not an invalid one - guarding this keeps
        // Save reachable on every existing row.
        boolean targetOk = selectedTarget == null
                || VariantOps.targetProblem(scopeValue, selectedTarget) == null;
        saveButton.active = nameField.isValid() && !selectedBaseEntity.isEmpty()
                && coefficientField.isValid() && targetOk
                && weightField.isValid() && minCountField.isValid()
                && maxCountField.isValid() && chanceField.isValid() && startLevelField.isValid();
        refreshVariantSection();
    }

    /** Shows the variant-only fields only for a variant name. */
    private void refreshVariantSection() {
        setShown(displayNameField.getWidget(), !el("display").hidden());
    }

    /** Whether the form still holds the behavior last picked (see {@link #pickedGoalSet}). */
    private boolean goalSetPicked(VariantForm form) {
        return pickedGoalSet != null && pickedGoalSet.equals(form.goalSet());
    }

    private void save() {
        if (!saveButton.active) return;
        VariantForm form = readFields();
        VariantOps.VariantSave saved = VariantOps.save(at, openedScope, openedScopeId, form, goalSetPicked(form));
        if (!report(saved.report())) {
            return;
        }
        // Remembered so the mob list can float it to the top.
        com.strata.client.util.RecentEdits.touch(selectedBaseEntity.strip(), saved.name());
        // SAVE STAYS: the editor opens again on the row just saved, as a new row, a rename, or a scope change
        // leaves it, so a second Save edits that row and never makes a copy.
        String entity = selectedBaseEntity.strip();
        String scope = "global".equals(scopeValue) ? "global" : scopeValue;
        String target = "global".equals(scopeValue) ? "" : targetValue();
        reopen(plain(saved.name())
                ? new MobEditScreen(null, entity, scope, target, true)
                : new MobEditScreen(saved.name(), null, scope, target));
    }

    /** Resets the plain mob to the game after a confirmation that lists what goes. */
    private void confirmReset() {
        if (resetParts.isEmpty()) {
            return;
        }
        String entity = selectedBaseEntity;
        minecraft.setScreen(new ConfirmDialog(this,
                "Reset " + Labels.creature(entity) + " to the game?\nThis removes " + MobOps.resetSummary(resetParts)
                        + ", in every scope.\nIts tier and biome places, and its family's goal set, are kept.",
                // The dialog returns here, and init reads the tables again.
                () -> {
                    if (report(MobOps.resetToGame(entity))) {
                        current = null;
                        baseline = null;
                        pendingSnapshot = null;
                        undo.clear();
                    }
                }));
    }

    private void confirmDelete() {
        if (at.baseMobRow()) {
            minecraft.setScreen(new ConfirmDialog(this,
                    "Remove " + Labels.creature(at.prefilledBase()) + " from "
                            + Labels.scope(openedScope, openedScopeId)
                            + "?\nIts loot and spawn rules there go too.",
                    () -> afterDelete(MobOps.removeFromScope(
                            openedScope, openedScopeId, at.prefilledBase()))));
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this,
                "Remove variant '" + at.originalName()
                        + "'?\nThis removes stats, abilities, equipment, and spawn references.",
                () -> afterDelete(VariantOps.deleteFromEditor(at))));
    }

    /** Back to the list the editor came from, when the delete landed. */
    private void afterDelete(com.strata.domain.WriteReport result) {
        if (report(result)) {
            deferBack();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // THE AI BOX: a sunken box, its lines inside it.
        com.strata.ui.Layout.Element box = el("goalsBox");
        int boxY = box.box().y() - scrollOffset();
        // Drawn whenever any of it is in view, clipped to the view: a box taller than the view (a mob with
        // many or long goals, the zombie's) was never drawn when it had to fit whole.
        if (boxY < viewBottom() && boxY + box.box().h() > viewTop()) {
            graphics.enableScissor(0, viewTop(), this.width, viewBottom());
            graphics.fill(box.box().x(), boxY, box.box().right(), boxY + box.box().h(), Palette.BORDER);
            graphics.fill(box.box().x() + 1, boxY + 1, box.box().right() - 1, boxY + box.box().h() - 1, Palette.PANEL);
            graphics.disableScissor();
            for (com.strata.ui.Layout.Element e : layout.elements()) {
                if (e.kind() == com.strata.ui.Layout.Kind.NOTE) {
                    // A list's heading ("Goals (what it does):") in orange, its goals in white.
                    text(graphics, e, e.text().startsWith(" ") ? Palette.BODY : Palette.GROUP);
                }
            }
        }
        // EVERY LINE OF TEXT FROM THE LAYOUT, as the fit gate checked it: cut or wrapped to its room, at its
        // place.
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.kind() != com.strata.ui.Layout.Kind.TEXT || e.id().equals("title")) {
                continue;
            }
            int color = e.id().startsWith("note") ? Palette.GROUP
                    : e.id().startsWith("footer") ? Palette.MUTED
                    : e.id().equals("scopeText") ? Palette.TEXT
                    : Palette.LABEL;
            text(graphics, e, color);
        }

        tooltip(graphics, mouseX, mouseY, el("scope"),
                "Scope controls where this spawn rule applies.",
                "  Click to pick: Global, Tier, or Biome",
                "  Global — all biomes; Tier — a tier's biomes;",
                "  Biome — one specific biome");
        tooltip(graphics, mouseX, mouseY, el("target"),
                "Click to pick the tier or biome this variant spawns in.",
                "Global needs no target.");
        tooltip(graphics, mouseX, mouseY, el("base"),
                "Click to select the base entity from the registry");
        // WHAT EACH NUMBER DOES.
        tooltip(graphics, mouseX, mouseY, el("weight"),
                "Spawn weight: this mob's share when the game picks among its category's mobs",
                "(its weight / all their weights). Not a chance: a mob alone on its list",
                "is picked every time. The mob caps decide how many spawn.");
        tooltip(graphics, mouseX, mouseY, el("chance"),
                "Spawn chance %: once the weight picks this mob, how often it then spawns.",
                "100 or blank: always. 25: one pick in four. 0: never.");
        tooltip(graphics, mouseX, mouseY, el("min"),
                "Smallest group, 0 or more. Strata rolls the group from Min to Max;",
                "a group of 0 spawns nothing.");
        tooltip(graphics, mouseX, mouseY, el("max"),
                "Largest group. The rest of the group spawns around the first mob.");
        tooltip(graphics, mouseX, mouseY, el("enabled"),
                "Enabled — whether this variant SPAWNS at all.");
        tooltip(graphics, mouseX, mouseY, el("coefficient"),
                com.strata.domain.ScalingOps.COEFFICIENT_MOB.toArray(String[]::new));
        tooltip(graphics, mouseX, mouseY, el("name"),
                "Blank = a plain mob, with only its spawn settings.",
                "Type a unique name (for example fire_zombie) to make a variant.");
        tooltip(graphics, mouseX, mouseY, el("display"),
                "What the player sees in-world (blank = use the id)");
        tooltip(graphics, mouseX, mouseY, el("texture"),
                "Custom texture path (blank = use base mob texture)");
        tooltip(graphics, mouseX, mouseY, el("own"),
                "On = register a real EntityType so this variant can be summoned "
                        + "(/summon) and has its own spawn egg",
                "Applies at mod construction — a full game restart is required "
                        + "(turning it off also needs a restart).");
        tooltip(graphics, mouseX, mouseY, el("goalSet"),
                "Goal set: changes the mob's goals, with the game's own goals",
                "A variant takes its base mob's unless you pick another here.",
                "Mobs get it when they spawn, or when they next load.");
    }

    /**
     * The two footer lines: this mob's equipment and drops, overrides or vanilla.
     *
     * @param target the tier or biome the row is filed under
     */
    private String[] vanillaDataLines(String target) {
        String entity = selectedBaseEntity == null ? "" : selectedBaseEntity.strip();
        if (entity.isEmpty()) {
            return new String[]{"Equipment: none", "Loot: none"};
        }
        // WHAT THIS MOB GETS, INHERITED INCLUDED: the rows the Equipment and Loot lists show, from this
        // scope, a wider scope, the base mob, and the game.
        String equipmentOwner = at.originalName().isEmpty()
                ? com.strata.model.Variant.defaultNameFor(entity) : at.originalName();
        List<com.strata.domain.EquipmentOps.GameEquipment> gameEquipment = com.strata.domain.DefaultEquipmentOps.gameEquipment(entity);
        List<LootOps.GameLootItem> gameLoot = com.strata.runtime.VanillaData.lootItems(entity, this.minecraft).stream()
                .map(d -> new LootOps.GameLootItem(d.item(), d.countMin(), d.countMax())).toList();
        String equipment = com.strata.domain.EquipmentOps.equipmentSummary(
                com.strata.domain.EquipmentOps.equipmentFor(equipmentOwner, entity, scopeValue, target, gameEquipment),
                com.strata.ui.ChoiceWords::word, Labels::itemName);
        String loot = LootOps.lootSummary(
                LootOps.rowsFor(scopeValue, target, entity, at.originalName(), gameLoot), Labels::itemName);
        return new String[]{"Equipment: " + equipment, "Loot: " + loot};
    }
}

