package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for one variant instance's abilities: sun_immune, heal, effect, aura_players, aura_mobs,
 * on_hit_effect, on_hurt_effect, damage_immunity.
 */
public class AbilitiesScreen extends StrataScreen implements GroupBar.Host {

    /** The kind of group this screen fills. */
    private static final String GROUP_KIND = com.strata.domain.GroupOps.KIND_ABILITIES;

    /** Where the rows are kept: a mob variant instance, or an item variant. */
    private final com.strata.domain.AbilitiesOps.Store store;

    /** The group row above the list: picker, Apply, Edit, name field, Make group. */
    private final GroupBar groups;

    private BrowseList list;
    private List<com.strata.domain.AbilitiesOps.AbilityRow> allRows = List.of();
    /** Where an inherited set comes from: "base mob", "from global", or "". */
    private String inheritedFrom = "";
    /** "Remove" on an own row, "Turn off" on an inherited one. */
    private Button removeButton;
    /** The ability the tooltips were made for, so they are made once per choice. */
    private String tipsFor = "";

    // Inline add fields
    private DropdownSelect abilityType;
    /**
     * The parameter: an effect or a damage type, PICKED, never typed.
     */
    private String parameter = "";
    private Button parameterButton;
    private EditField amountField;
    private EditField intervalField;
    private EditField radiusField;
    private DropdownSelect triggerSelect;
    private EditField durationField;
    /** The percent chance a class row lands when its trigger fires; blank is 100. */
    private EditField chanceField;
    /**
     * The entity the class acts on (spawn's creature): PICKED from the game's entity registry, so a mod's
     * entities are offered with the game's own.
     */
    private String entity = "";
    private Button entityButton;
    /** The cells only some classes read: shown only for them. */
    private EditField damageField;
    private String damageType = "";
    /** "true" or "false", as stored; kept apart from its dropdown so a layout can read it before init. */
    private String canKill = "false";
    private String cure = "";
    /** The row's own particle and HUD icon: blank is the game's own. */
    private String particle = "";
    private String hudIcon = "";

    /**
     * @param baseEntity the creature these abilities hang off. It names the mob in the Recent group, keyed on
     *     (entity, variant), and it finds the base mob's abilities a variant inherits. It is a CONSTRUCTOR
     *     PARAMETER rather than something the opener sets afterwards so that a future caller cannot forget it.
     */
    public AbilitiesScreen(String variantName, String baseEntity,
                                  String scope, String scopeId) {
        super("Strata — Abilities: " + Labels.ownerTitle(baseEntity, scopeId, variantName));
        this.store = com.strata.domain.AbilitiesOps.mobStore(variantName, baseEntity, scope, scopeId);
        this.groups = new GroupBar(this, this, GROUP_KIND, "abilities");
        editingMob(baseEntity, variantName);
    }

    /**
     * The same editor over another list of ability rows: an item variant's effects
     * (ItemVariantOps#effectsStore).
     */
    public AbilitiesScreen(String title, com.strata.domain.AbilitiesOps.Store store) {
        this(null, title, store);
    }

    /** The same, under a sub-root (Items for an item variant's effects, Blocks for a block's). */
    public AbilitiesScreen(String root, String title, com.strata.domain.AbilitiesOps.Store store) {
        super(root == null ? com.strata.ui.TitlePath.rootOfClass("AbilitiesScreen") : root, title);
        this.store = store;
        this.groups = new GroupBar(this, this, GROUP_KIND, "abilities");
        // A group's own editor, or an item variant's list: either way this is not a mob being worked on, so
        // Recent records nothing.
        editingGroup(title);
    }

    /** The layout for the list and the add row as they stand. */
    private com.strata.ui.Layout layout() {
        List<com.strata.domain.AbilitiesOps.AbilityRow> picked = pickedRows();
        int turnOffs = (int) picked.stream().filter(com.strata.domain.AbilitiesOps::removeTurnsOff).count();
        String problem = abilityType == null ? null : addProblem();
        boolean inherits = allRows.stream().anyMatch(com.strata.domain.AbilitiesOps.AbilityRow::inherited);
        return com.strata.ui.screen.AbilitiesLayout.build(frame(), this.font::width, title(),
                com.strata.domain.AbilitiesOps.abilityChoices(),
                com.strata.ui.screen.ListLayout.manyLabel(com.strata.ui.screen.AbilitiesLayout.REMOVE,
                        com.strata.ui.screen.AbilitiesLayout.TURN_OFF, picked.size() - turnOffs, turnOffs),
                parameterText(), triggerText(), entityText(),
                footer(problem, inherits),
                groups.text(),
                groups.picked(),
                this::shows,
                new com.strata.ui.screen.AbilitiesLayout.Extras(damageTypeText(), cureText(), canKill, particle, hudIcon,
                        canKillLabel()));
    }

    /** On an explode row the Can kill cell is the switch that lets the blast hurt its own source. */
    private String canKillLabel() {
        return abilityType != null && abilityType.getSelected().startsWith("explode") ? "Hurts itself" : "Can kill";
    }

    /** The field each add-row cell edits. */
    private static com.strata.model.AbilityField fieldOf(String id) {
        return switch (id) {
            case "entity" -> com.strata.model.AbilityField.ENTITY;
            case "parameter" -> com.strata.model.AbilityField.PARAMETER;
            case "trigger" -> com.strata.model.AbilityField.TRIGGER;
            case "amount" -> com.strata.model.AbilityField.AMOUNT;
            case "ticks" -> com.strata.model.AbilityField.TICKS;
            case "duration" -> com.strata.model.AbilityField.DURATION;
            case "radius" -> com.strata.model.AbilityField.RADIUS;
            case "chance" -> com.strata.model.AbilityField.CHANCE;
            case "damage" -> com.strata.model.AbilityField.DAMAGE;
            case "damage_type" -> com.strata.model.AbilityField.DAMAGE_TYPE;
            case "can_kill" -> com.strata.model.AbilityField.CAN_KILL;
            case "cure" -> com.strata.model.AbilityField.CURE;
            case "look" -> com.strata.model.AbilityField.PARTICLE;
            default -> null;
        };
    }

    /** True when the chosen ability or class reads this cell, so it is on the row. */
    private boolean shows(String id) {
        String a = abilityType == null ? keepAbilityKind : abilityType.getSelected();
        com.strata.model.AbilityField field = fieldOf(id);
        if (field == com.strata.model.AbilityField.PARAMETER) {
            return com.strata.domain.AbilitiesOps.parameterKind(a) != com.strata.domain.AbilitiesOps.ParameterKind.NONE;
        }
        return field != null && com.strata.domain.AbilitiesOps.usesField(a, field);
    }

    /** Adds the widget at its cell when the row has that cell; a cell the row does not read is not built. */
    private <T extends net.minecraft.client.gui.components.AbstractWidget> T placeIf(T widget,
                                                                                      com.strata.ui.Layout layout, String id) {
        if (layout.get(id) != null) {
            addRenderableWidget(place(widget, layout.get(id)));
        }
        return widget;
    }

    private String damageTypeText() {
        return damageType.isEmpty() ? "Pick a type..."
                : damageType.startsWith("#") ? damageType
                : Labels.pretty(net.minecraft.resources.ResourceLocation.tryParse(damageType));
    }

    private String cureText() {
        return cure.isEmpty() ? "None" : Labels.itemName(cure);
    }

    /** The line under the bar. */
    private String footer(String problem, boolean inherits) {
        String groupLine = groups.note();
        if (!groupLine.isEmpty()) {
            return groupLine;
        }
        List<String> elsewhere = store.elsewhere();
        String also = elsewhere.isEmpty() ? "" : " Also set in: " + String.join(", ", elsewhere)
                + " (open the mob from there to see them).";
        return problem != null ? "To add: " + problem
                : (inherits ? "Inherited (" + inheritedFrom + "). A change makes the set this scope's own."
                : com.strata.ui.Words.count(allRows.size(), "ability", "abilities")) + also;
    }

    // ---- what the group row asks of this editor ----

    @Override
    public boolean canTakeGroups() {
        return store.canTakeGroups();
    }

    @Override
    public com.strata.domain.GroupOps.Added adds(String groupId) {
        return store.groupAdds(groupId);
    }

    @Override
    public boolean stamp(String groupId) {
        if (!report(store.applyGroup(groupId))) {
            return false;
        }
        loadRows();
        rebuild();
        return true;
    }

    @Override
    public void openGroup(com.strata.domain.GroupOps.Group group) {
        ScreenNavigator.get().navigateTo(new AbilitiesScreen(
                "Strata — Group: " + group.name(),
                com.strata.domain.AbilitiesOps.mobStore(group.id(), "", "global", "")));
    }

    @Override
    public void redraw() {
        rebuild();
    }

    @Override
    protected void init() {
        super.init();
        loadRows();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // Minecraft runs init again when a dialog closes.
        List<String> choices = com.strata.domain.AbilitiesOps.abilityChoices();
        String keepAbility = abilityType == null ? choices.getFirst() : abilityType.getSelected();
        keepAbilityKind = keepAbility;

        String keepAmount = amountField == null ? "1" : amountField.getValue();
        String keepInterval = intervalField == null ? "" : intervalField.getValue();
        String keepRadius = radiusField == null ? "" : radiusField.getValue();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("AbilitiesScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);

        abilityType = new DropdownSelect(0, 0, 0, 0, "", choices, keepAbility);
        // Another kind of ability takes another kind of parameter: the old one goes.
        abilityType.setOnChange(a -> {
            if (com.strata.domain.AbilitiesOps.parameterKind(a) != com.strata.domain.AbilitiesOps.parameterKind(keepAbilityKind)) {
                parameter = "";
            }
            keepAbilityKind = a;
            if (!com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.ENTITY)) {
                entity = "";
            }
            // The row shows the cells this choice reads: lay it out again.
            rebuildWidgets();
        });
        addRenderableWidget(place(abilityType.getWidget(), layout.get("ability")));

        // An effect or a damage type is a dropdown; an entity or an attribute, from a long list, is picked.
        var kind = com.strata.domain.AbilitiesOps.parameterKind(abilityType.getSelected());
        List<com.strata.game.RegistryQuery.Option> paramChoices = kind == com.strata.domain.AbilitiesOps.ParameterKind.EFFECT
                || kind == com.strata.domain.AbilitiesOps.ParameterKind.DAMAGE ? parameterOptions(kind) : List.of();
        parameterButton = placeIf(paramChoices.isEmpty()
                ? Button.builder(Component.literal(layout.textOf("parameter")), b -> pickParameter()).build()
                : dropdown(paramChoices, parameter, v -> parameter = v), layout, "parameter");
        refreshParameter();
        // The fields are new: their tooltips are set again on the next frame.
        tipsFor = "";

        amountField = new EditField(this.font, 0, 0, 0, 0, "amt", keepAmount);
        amountField.setValidator(EditField.numericValidator());
        placeIf(amountField.getWidget(), layout, "amount");

        // HEAL and the auras need an interval, and the auras use a radius.
        intervalField = new EditField(this.font, 0, 0, 0, 0, "ticks", keepInterval);
        intervalField.setValidator(EditField.nonNegativeIntegerValidator());
        placeIf(intervalField.getWidget(), layout, "ticks");

        radiusField = new EditField(this.font, 0, 0, 0, 0, "radius", keepRadius);
        radiusField.setValidator(EditField.numericValidator());
        placeIf(radiusField.getWidget(), layout, "radius");

        // The trigger picker and the duration/entity fields were declared in the layout but their widgets
        // were never built: the cells rendered empty.
        String keepTrigger = triggerSelect == null || triggerSelect.getSelected() == null || triggerSelect.getSelected().isEmpty()
                ? "" : triggerSelect.getSelected();
        List<String> triggerIds = new java.util.ArrayList<>();
        // The triggers THIS list can fire (a projectile has only On impact), by their names.
        triggerIds.addAll(store.triggers());
        triggerSelect = new DropdownSelect(0, 0, 0, 0, "", triggerIds, keepTrigger);
        triggerSelect.setWords(com.strata.domain.TriggerOps::displayName);
        placeIf(triggerSelect.getWidget(), layout, "trigger");
        keepTrigger = triggerSelect.getSelected();

        durationField = new EditField(this.font, 0, 0, 0, 0, "Duration",
                durationField == null ? "" : durationField.getValue());
        durationField.setValidator(EditField.numericValidator());
        placeIf(durationField.getWidget(), layout, "duration");

        chanceField = new EditField(this.font, 0, 0, 0, 0, "Chance %",
                chanceField == null ? "" : chanceField.getValue());
        chanceField.setValidator(EditField.numericValidator());
        placeIf(chanceField.getWidget(), layout, "chance");

        entityButton = placeIf(Button.builder(Component.literal(layout.textOf("entity")),
                b -> pickEntity()).build(), layout, "entity");

        damageField = new EditField(this.font, 0, 0, 0, 0, "Damage",
                damageField == null ? "" : damageField.getValue());
        damageField.setValidator(EditField.numericValidator());
        placeIf(damageField.getWidget(), layout, "damage");
        List<com.strata.game.RegistryQuery.Option> damageTypes = parameterOptions(
                com.strata.domain.AbilitiesOps.ParameterKind.DAMAGE);
        placeIf(damageTypes.isEmpty()
                ? Button.builder(Component.literal(layout.textOf("damage_type")), b -> pickDamageType()).build()
                : dropdown(damageTypes, damageType, v -> damageType = v), layout, "damage_type");
        DropdownSelect canKillSelect = new DropdownSelect(0, 0, 0, 0, "",
                com.strata.ui.screen.AbilitiesLayout.CAN_KILL_CHOICES, canKill);
        canKillSelect.setOnChange(v -> canKill = v);
        placeIf(canKillSelect.getWidget(), layout, "can_kill");
        placeIf(Button.builder(Component.literal(layout.textOf("cure")),
                b -> ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Which item ends it?",
                        com.strata.game.RegistryQuery.items(), picked -> cure = picked))).build(), layout, "cure");
        Button look = placeIf(Button.builder(Component.literal(layout.textOf("look")), b -> pickLook()).build(),
                layout, "look");
        look.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Particle: " + (particle.isEmpty() ? "the game's own" : particle)
                        + "\nHUD icon: " + (hudIcon.isEmpty() ? "its own" : hudIcon))));
        refreshFields();
        refreshTrigger();

        Button addButton = Button.builder(Component.literal(editing == null ? "+ Add ability" : "Save ability"),
                b -> add()).build();
        Button editButton = Button.builder(Component.literal("Edit"), b -> edit()).build();
        Button toggleButton = Button.builder(Component.literal("On / Off"),
                        b -> { var row = selectedRow(); if (row != null) act(
                                store.toggle(row, allRows)); })
                .build();
        // Several picked rows (Ctrl, Shift) go together, confirmed first.
        removeButton = Button.builder(Component.literal(com.strata.ui.screen.AbilitiesLayout.REMOVE),
                b -> removePicked()).build();
        placeAll(layout, new String[]{"add", "edit", "toggle", "remove"}, addButton, editButton, toggleButton, removeButton);
        // An inherited row is changed by adding this instance's own; only an own row is edited.
        needsSelection(() -> selectedRow() != null && !selectedRow().inherited() && !list.manyPicked(), editButton);
        needsSelection(() -> selectedRow() != null && !list.manyPicked(), toggleButton);
        needsSelection(() -> !list.pickedIds().isEmpty(), removeButton);

        // The group's own controls: made and placed by GroupBar in one call, so nothing here is a field init
        // would have to assign in order (drift.17) and three screens cannot disagree about the row.
        groups.init(this.font, layout, this::addRenderableWidget);

        rebuild();
        list.restore(keepList);
    }

    /** The ability the parameter was picked for, to see when the kind changes. */
    private String keepAbilityKind = "";

    /** The Parameter picker's text: the pick, "Pick..." before one, or that none is needed. */
    private String parameterText() {
        var kind = com.strata.domain.AbilitiesOps.parameterKind(abilityType == null ? keepAbilityKind : abilityType.getSelected());
        if (kind == com.strata.domain.AbilitiesOps.ParameterKind.NONE) {
            return "none needed";
        }
        if (parameter.isEmpty()) {
            return "Pick " + (kind == com.strata.domain.AbilitiesOps.ParameterKind.ENTITY ? "an entity"
                    : kind == com.strata.domain.AbilitiesOps.ParameterKind.ATTRIBUTE ? "an attribute"
                            : kind == com.strata.domain.AbilitiesOps.ParameterKind.EFFECT ? "an effect"
                                    : "a damage type") + "...";
        }
        return parameter.startsWith("#") ? parameter
                : Labels.pretty(net.minecraft.resources.ResourceLocation.tryParse(parameter));
    }

    /** The Parameter picker's text and switch, for the ability chosen now. */
    private void refreshParameter() {
        parameterButton.setMessage(Component.literal(com.strata.ui.TextFit.clip(parameterText(),
                parameterButton.getWidth() - com.strata.ui.Layout.LABEL_MARGIN,
                this.font::width)));
        parameterButton.active = com.strata.domain.AbilitiesOps.parameterKind(abilityType.getSelected())
                != com.strata.domain.AbilitiesOps.ParameterKind.NONE;
        refreshTrigger();
    }

    /** The Trigger cell's current label: the chosen trigger, or the first offered. */
    private String triggerText() {
        if (triggerSelect != null && triggerSelect.getSelected() != null) {
            return com.strata.domain.TriggerOps.displayName(triggerSelect.getSelected());
        }
        List<String> triggers = store.triggers();
        return triggers.isEmpty() ? "" : com.strata.domain.TriggerOps.displayName(triggers.get(0));
    }

    private void refreshTrigger() {
        if (triggerSelect != null) {
            triggerSelect.getWidget().setMessage(Component.literal(com.strata.ui.TextFit.clip(triggerText(),
                    triggerSelect.getWidget().getWidth() - com.strata.ui.Layout.LABEL_MARGIN,
                    this.font::width)));
        }
    }

    /** The Entity picker's text: the pick, "Pick an entity..." before one, or that none is needed. */
    private String entityText() {
        String a = abilityType == null ? keepAbilityKind : abilityType.getSelected();
        if (!com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.ENTITY)) {
            return "none needed";
        }
        return entity.isEmpty() ? "Pick an entity..."
                : Labels.pretty(net.minecraft.resources.ResourceLocation.tryParse(entity));
    }

    /** The row's look: which to set, then that one's picker. */
    private void pickLook() {
        List<com.strata.game.RegistryQuery.Option> which = List.of(
                new com.strata.game.RegistryQuery.Option("particle", "Particle: " + (particle.isEmpty() ? "the game's" : particle), "strata"),
                new com.strata.game.RegistryQuery.Option("hud_icon", "HUD icon: " + (hudIcon.isEmpty() ? "its own" : hudIcon), "strata"));
        // The second picker opens AFTER the first has gone back (RegistryPickerScreen goes back once its
        // callback returns), so it is queued rather than opened inside it.
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Set which?", which, kind -> minecraft.tell(() -> {
            List<com.strata.game.RegistryQuery.Option> options = new ArrayList<>();
            if (kind.equals("particle")) {
                options.add(new com.strata.game.RegistryQuery.Option("", "The game's own", "strata"));
                options.addAll(com.strata.game.RegistryQuery.particles());
                ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Show which particle?", options,
                        picked -> particle = picked));
            } else {
                options.add(new com.strata.game.RegistryQuery.Option("", "Its own icon", "strata"));
                options.add(new com.strata.game.RegistryQuery.Option(com.strata.runtime.HudIcons.NONE, "No icon", "strata"));
                options.addAll(com.strata.game.RegistryQuery.effects());
                ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Show which icon?", options,
                        picked -> hudIcon = picked));
            }
        })));
    }

    /** Picks the damage type from the world's damage type registry, mods' included. */
    private void pickDamageType() {
        var options = com.strata.game.RegistryQuery.damageTypesFor(this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.registryAccess() : null);
        if (options.isEmpty()) {
            refuse("damage type: open a world to pick one (the damage types are the world's)");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Which damage type?", options,
                picked -> damageType = picked));
    }

    /** Picks the entity from the game's entity registry, mods' entities included. */
    private void pickEntity() {
        // The variants first, as "variant:" and the name: a variant spawns as itself.
        List<com.strata.game.RegistryQuery.Option> options = new java.util.ArrayList<>();
        for (String name : com.strata.data.Config.tables().variants().keySet().stream().sorted().toList()) {
            options.add(new com.strata.game.RegistryQuery.Option(com.strata.runtime.EffectTable.VARIANT_PREFIX + name,
                    "Variant: " + name, "variants"));
        }
        options.addAll(com.strata.game.RegistryQuery.entities());
        if (options.isEmpty()) {
            refuse("entity: the game data could not be read at game start (see the log)");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Which entity?", options, picked -> entity = picked));
    }

    /** Greys the add fields the chosen ability does not use. */
    private void refreshFields() {
        String a = abilityType.getSelected();
        usable(amountField, com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.AMOUNT));
        usable(intervalField, com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.TICKS));
        usable(radiusField, com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.RADIUS));
        if (durationField != null) {
            usable(durationField, com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.DURATION));
        }
        if (triggerSelect != null) {
            triggerSelect.getWidget().active =
                    com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.TRIGGER);
        }
        if (chanceField != null) {
            usable(chanceField, com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.CHANCE));
        }
        if (entityButton != null) {
            entityButton.active = com.strata.domain.AbilitiesOps.usesField(a, com.strata.model.AbilityField.ENTITY);
            entityButton.setMessage(Component.literal(com.strata.ui.TextFit.clip(entityText(),
                    entityButton.getWidth() - com.strata.ui.Layout.LABEL_MARGIN,
                    this.font::width)));
        }
    }

    private static void usable(EditField field, boolean on) {
        field.getWidget().setEditable(on);
        field.getWidget().active = on;
    }

    /** Picks the parameter from the game's effects or damage types. */
    /** The choices for a parameter kind; damage types only with a world open (they are the world's). */
    private List<com.strata.game.RegistryQuery.Option> parameterOptions(com.strata.domain.AbilitiesOps.ParameterKind kind) {
        return switch (kind) {
            case EFFECT -> com.strata.game.RegistryQuery.effects();
            case ATTRIBUTE -> com.strata.game.RegistryQuery.attributes();
            case ENTITY -> com.strata.game.RegistryQuery.entities();
            default -> com.strata.game.RegistryQuery.damageTypesFor(this.minecraft != null && this.minecraft.level != null
                    ? this.minecraft.level.registryAccess() : null).stream().filter(o -> !o.id().startsWith("#")).toList();
        };
    }

    /** A dropdown of options by their names, holding the id. */
    private Button dropdown(List<com.strata.game.RegistryQuery.Option> options, String value,
                            java.util.function.Consumer<String> onPick) {
        // "Pick..." first, an empty value: nothing is set until a choice is made.
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        names.put("", "Pick...");
        options.forEach(o -> names.put(o.id(), o.displayName()));
        DropdownSelect d = new DropdownSelect(0, 0, 0, 0, "", List.copyOf(names.keySet()), value == null ? "" : value);
        d.setWords(id -> names.getOrDefault(id, id));
        d.setOnChange(onPick::accept);
        return d.getWidget();
    }

    private void pickParameter() {
        var kind = com.strata.domain.AbilitiesOps.parameterKind(abilityType.getSelected());
        var options = parameterOptions(kind);
        if (options.isEmpty()) {
            refuse(labelFor(kind) + ": the game data could not be read at game start (see the log)");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "Which " + labelFor(kind).toLowerCase() + "?",
                options, picked -> parameter = picked));
    }

    private String labelFor(com.strata.domain.AbilitiesOps.ParameterKind kind) {
        return switch (kind) {
            case EFFECT -> "effect";
            case ATTRIBUTE -> "attribute";
            case ENTITY -> "entity";
            default -> "damage type";
        };
    }

    /** What the add row still needs, from the back end's own check, or null. */
    private String addProblem() {
        String ability = abilityType.getSelected();
        if (com.strata.model.EffectClass.of(ability) != null) {
            // Class row: the back end's own check, on the row an add would write.
            return com.strata.domain.AbilitiesOps.classProblem(typedRow());
        }
        return com.strata.domain.AbilitiesOps.abilityProblem(ability, parameter,
                amountField.getValue(), intervalField.getValue(), radiusField.getValue());
    }

    /** Adds the typed ability. */
    /** The row the add fields describe. */
    private com.strata.domain.AbilitiesOps.AbilityRow typedRow() {
        String ability = abilityType.getSelected();
        if (com.strata.model.EffectClass.of(ability) != null) {
            String trigger = triggerSelect == null || triggerSelect.getSelected() == null ? "" : triggerSelect.getSelected();
            return new com.strata.domain.AbilitiesOps.AbilityRow("", parameter, amountField.getValue(),
                    intervalField.getValue(), radiusField.getValue(), true, false,
                    ability, trigger, chanceField == null ? "" : chanceField.getValue(),
                    damageField == null ? "" : damageField.getValue(),
                    durationField == null ? "" : durationField.getValue(),
                    canKill, damageType, particle, hudIcon, cure, entity);
        }
        return new com.strata.domain.AbilitiesOps.AbilityRow(ability, parameter, amountField.getValue(),
                intervalField.getValue(), radiusField.getValue(), true);
    }

    /** The row Edit put into the fields, or null while adding. */
    private com.strata.domain.AbilitiesOps.AbilityRow editing;

    /** Puts the selected row into the fields; Save ability then writes it in place of the old one. */
    private void edit() {
        var row = selectedRow();
        if (row == null || row.inherited()) {
            return;
        }
        editing = row;
        abilityType.setSelected(row.effectClass().isEmpty() ? row.ability() : row.effectClass());
        parameter = row.parameter();
        entity = row.entity();
        damageType = row.damageType();
        canKill = row.canKill();
        particle = row.particle();
        hudIcon = row.hudIcon();
        cure = row.cure();
        amountField.setValue(row.amount());
        intervalField.setValue(row.intervalTicks());
        radiusField.setValue(row.radius());
        if (chanceField != null) chanceField.setValue(row.chance());
        if (damageField != null) damageField.setValue(row.damage());
        if (durationField != null) durationField.setValue(row.duration());
        if (triggerSelect != null) triggerSelect.setSelected(row.trigger());
        rebuildWidgets();
    }

    private void add() {
        String ability = abilityType.getSelected();
        com.strata.domain.AbilitiesOps.AbilityRow row = typedRow();
        if (editing != null) {
            var old = editing;
            editing = null;
            act(store.replace(old, row, allRows));
            return;
        }
        Runnable write = () -> act(store.add(row, allRows));
        if (!store.has(row)) {
            write.run();
            return;
        }
        String what = Labels.pretty(ability) + (parameter.isBlank() ? "" : " " + parameter.strip());
        minecraft.setScreen(new ConfirmDialog(this,
                "Replace " + what + "?\nThis variant already has it. The new amount,"
                        + " interval and radius replace the old ones.",
                write));
    }

    private com.strata.domain.AbilitiesOps.AbilityRow selectedRow() {
        BrowseList.Entry sel = list.getSelected();
        return sel == null ? null : allRows.get(Integer.parseInt(sel.id));
    }

    /** The rows picked with Ctrl and Shift, or the selected row alone. */
    private List<com.strata.domain.AbilitiesOps.AbilityRow> pickedRows() {
        return list == null ? List.of()
                : list.pickedIds().stream().map(id -> allRows.get(Integer.parseInt(id))).toList();
    }

    /** Removes or turns off the picked abilities: one at once, several after a confirmation that names them. */
    private void removePicked() {
        List<com.strata.domain.AbilitiesOps.AbilityRow> rows = pickedRows();
        if (rows.isEmpty()) {
            return;
        }
        // ONE ROW GOES AT ONCE; SEVERAL ASK.
        if (rows.size() == 1) {
            act(store.remove(rows.getFirst(), allRows));
            return;
        }
        com.strata.domain.AbilitiesOps.AbilityRemovalPlan plan = com.strata.domain.AbilitiesOps.abilityRemovalPlan(rows, allRows);
        StringBuilder text = new StringBuilder("Remove " + rows.size() + " abilities?");
        if (!plan.delete().isEmpty()) {
            text.append("\nDelete your own: ").append(names(plan.delete()));
        }
        if (!plan.turnOff().isEmpty()) {
            text.append("\nTurn off inherited (" + inheritedFrom + "): ").append(names(plan.turnOff()));
            text.append("\nThe set becomes this scope's own.");
        }
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, text.toString(),
                () -> act(store.removeAll(rows, allRows))));
    }

    private static String names(List<com.strata.domain.AbilitiesOps.AbilityRow> rows) {
        return com.strata.domain.Picks.listed(rows.stream().map(r -> Labels.pretty(com.strata.domain.AbilitiesOps.rowName(r))
                + (r.parameter().isEmpty() ? "" : " " + r.parameter())).toList());
    }

    /** Shows what an ability action reported, and redraws the rows after it. */
    private void act(WriteReport result) {
        if (report(result)) {
            loadRows();
            rebuild();
        }
    }

    /** The rows, and where an inherited set comes from. */
    private void loadRows() {
        allRows = store.shown();
        String from = store.from();
        inheritedFrom = switch (from) {
            case com.strata.domain.ScopeLayers.FROM_TIER -> "from the tier";
            case com.strata.domain.ScopeLayers.FROM_GLOBAL -> "from global";
            default -> from;
        };
    }

    private String subtitle(com.strata.domain.AbilitiesOps.AbilityRow row) {
        StringBuilder sb = new StringBuilder();
        // What the row does, in a sentence.
        sb.append(com.strata.domain.AbilitiesOps.abilitySentence(row));
        if (!row.enabled()) sb.append("  [disabled]");
        if (row.inherited()) sb.append("  (").append(inheritedFrom).append(")");
        return sb.toString();
    }

    /**
     * Each add field's tooltip says what the field means for the chosen ability: Amount is health for heal
     * but a strength level for an effect, and Ticks is a timer or a duration.
     */
    private void showTips(String ability) {
        if (ability.equals(tipsFor)) {
            return;
        }
        tipsFor = ability;
        tip(abilityType.getWidget(), com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.ABILITY));
        tip(parameterButton, com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.PARAMETER));
        tip(amountField.getWidget(), com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.AMOUNT));
        tip(intervalField.getWidget(), com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.TICKS));
        tip(radiusField.getWidget(), com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.RADIUS));
        tip(triggerSelect.getWidget(), com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.TRIGGER));
        tip(durationField.getWidget(), com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.DURATION));
        tip(entityButton, com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.ENTITY));
        tip(chanceField.getWidget(), com.strata.domain.AbilitiesOps.abilityTip(ability, com.strata.model.AbilityField.CHANCE));
    }

    private static void tip(net.minecraft.client.gui.components.AbstractWidget widget, String text) {
        widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(text)));
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < allRows.size(); i++) {
            com.strata.domain.AbilitiesOps.AbilityRow row = allRows.get(i);
            entries.add(new BrowseList.Entry(String.valueOf(i),
                    Labels.pretty(com.strata.domain.AbilitiesOps.rowName(row)), subtitle(row),
                    !row.enabled() ? Palette.MUTED : row.inherited() ? Palette.INHERITED : Palette.TEXT));
        }
        list.refresh(entries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        removeButton.setMessage(Component.literal(layout.textOf("remove")));
        showTips(abilityType.getSelected());
        super.render(graphics, mouseX, mouseY, partialTick);
        // One label over each add field.
        for (String id : com.strata.ui.screen.AbilitiesLayout.FIELDS) {
            if (layout.get("label." + id) != null) {
                text(graphics, layout.get("label." + id), Palette.LABEL);
            }
        }
        text(graphics, layout.get("label.groupName"), Palette.LABEL);
        // The footer says what the add row still needs, in red, while it needs something.
        text(graphics, layout.get("footer"), addProblem() != null ? Palette.ERROR : Palette.MUTED);
    }
}
