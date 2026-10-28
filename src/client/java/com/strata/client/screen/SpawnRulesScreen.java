package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.SpawnRulesOps;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpawnRulesScreen extends StrataScreen {

    private final String scope;
    private final String scopeId;
    private final String entity;
    /** WHICH VARIANT's conditions these are, blank for the base mob's. */
    private final String variantName;

    /** The rows shown are the global mob's, inherited: Save makes them this scope's own. */
    private boolean inherits;

    // Scrollable: widgets are materialised only for the visible slice.
    private final List<String[]> data = new ArrayList<>();
    private int scrollOffset = 0;

    // Rows whose TYPE was just changed by the user: the value resync below may only fire for these, never on
    // load/scroll/ undo/add/remove rebuilds, or stored legacy data would be silently overwritten.
    private final Set<String[]> typeChangedRows = new HashSet<>();

    private record Snapshot(List<String[]> rules) {
        static Snapshot of(List<String[]> rows) {
            List<String[]> copy = new ArrayList<>();
            for (String[] r : rows) copy.add(new String[]{r[0], r[1]});
            return new Snapshot(copy);
        }

        /** Row-by-row value equality. */
        boolean sameRows(List<String[]> rows) {
            if (rows.size() != rules.size()) return false;
            for (int i = 0; i < rows.size(); i++) {
                if (!java.util.Arrays.equals(rows.get(i), rules.get(i))) return false;
            }
            return true;
        }
    }

    private final UndoManager<Snapshot> undoMgr = new UndoManager<>();
    /** The rows as opened, for the unsaved-edits check. */
    private Snapshot baseline;
    private Snapshot pendingSnapshot;
    private Button undoButton;

    public SpawnRulesScreen(String scope, String scopeId, String entity, String variantName) {
        super("Strata — Spawn rules: " + Labels.ownerTitle(entity, scopeId, variantName));
        this.scope = scope == null ? "" : scope;
        this.scopeId = scopeId == null ? "" : scopeId;
        this.entity = entity == null ? "" : entity;
        this.variantName = variantName == null ? "" : variantName.strip();
        // THE VARIANT IS PASSED THROUGH, like the loot editor.
        editingMob(this.entity, this.variantName);
    }

    @Override
    protected void init() {
        super.init();
        typeChangedRows.clear();

        if (pendingSnapshot != null) {
            // Returning from structure picker — restore the full snapshot.
            Snapshot snap = pendingSnapshot;
            pendingSnapshot = null;
            data.clear();
            data.addAll(snap.rules());
            scrollOffset = 0;
            rebuild();
            return;
        }

        // Read the saved rows on the FIRST init only.
        if (baseline == null) {
            data.clear();
            scrollOffset = 0;
            data.addAll(SpawnRulesOps.spawnRulesFor(scope, scopeId, entity, variantName));
            // A TIER OR BIOME ROW IS THE GLOBAL MOB until it sets its own: with none of its own, it
            // shows the global ones it runs.
            if (data.isEmpty()) {
                List<String[]> inherited = SpawnRulesOps.inheritedFor(scope, scopeId, entity, variantName);
                inherits = !inherited.isEmpty();
                inherited.forEach(r -> data.add(new String[]{r[0], r[1]}));
            }
            baseline = Snapshot.of(data);
        }

        rebuild();
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.sameRows(data);
    }

    /** The screen's layout, built by rebuild for the rows shown. */
    private com.strata.ui.Layout layout;

    private int maxVisible() {
        return com.strata.ui.screen.SpawnRulesLayout.visibleRows(frame());
    }

    private boolean overflow() {
        return data.size() > maxVisible();
    }

    /** What a row's value control can show: its choices, its picker label, or nothing (typed). */
    private static com.strata.ui.screen.SpawnRulesLayout.Value valueOf(String[] row) {
        List<String> choices = SpawnRulesOps.valueChoices(row[0]);
        if (choices != null) {
            return new com.strata.ui.screen.SpawnRulesLayout.Value(com.strata.ui.Layout.Kind.BUTTON, choices);
        }
        if ("structure".equals(row[0])) {
            return new com.strata.ui.screen.SpawnRulesLayout.Value(com.strata.ui.Layout.Kind.BUTTON,
                    List.of(row[1].isEmpty() ? "Pick a structure" : Labels.pretty(row[1])));
        }
        return new com.strata.ui.screen.SpawnRulesLayout.Value(com.strata.ui.Layout.Kind.FIELD, List.of());
    }

    private void rebuild() {
        clearWidgets();
        super.init();

        int max = maxVisible();
        if (scrollOffset > data.size() - max) {
            scrollOffset = Math.max(0, data.size() - max);
        }
        List<String[]> shown = data.subList(scrollOffset, Math.min(data.size(), scrollOffset + max));
        layout = com.strata.ui.screen.SpawnRulesLayout.build(frame(), this.font::width,
                new com.strata.ui.screen.SpawnRulesLayout.Input(title(), SpawnRulesOps.TYPE_CHOICES,
                        shown.stream().map(SpawnRulesScreen::valueOf).toList(), overflow(),
                        inherits && !hasUnsavedEdits()
                                ? "From the global mob: Save makes them " + Labels.scope(scope, scopeId) + "'s own"
                                : "Spawn rules for " + Labels.creature(entity) + " — " + Labels.scope(scope, scopeId),
                        data.isEmpty() ? "No rules — the mob spawns wherever the game allows" : null));
        for (int i = 0; i < shown.size(); i++) {
            addRuleRow(i, shown.get(i));
        }
        addBottomBar();
    }

    private void addBottomBar() {
        addRenderableWidget(place(Button.builder(Component.literal("+ Add rule"), b -> addRule()).build(),
                layout.get("add")));

        undoButton = Button.builder(Component.literal("Undo"), b -> undoAction()).build();
        undoButton.active = undoMgr.canUndo();
        addRenderableWidget(place(undoButton, layout.get("undo")));


        // No second Back button here: the header's "← Back" does the same.
        addRenderableWidget(place(Button.builder(Component.literal("Save"), b -> save()).build(),
                layout.get("save")));
        if (overflow()) {
            addRenderableWidget(place(Button.builder(Component.literal("▲"),
                    b -> { scrollOffset = Math.max(0, scrollOffset - 1); rebuild(); }).build(), layout.get("up")));
            addRenderableWidget(place(Button.builder(Component.literal("▼"),
                    b -> { scrollOffset = Math.min(data.size() - maxVisible(), scrollOffset + 1); rebuild(); })
                    .build(), layout.get("down")));
        }
    }
    /** Adds a row of the first type this mob does not use yet. */
    private void addRule() {
        String[] next = SpawnRulesOps.newCondition(data);
        if (next == null) {
            refuse("Every rule type is already used - change a row instead");
            return;
        }
        pushUndo();
        data.add(next);
        scrollOffset = Math.max(0, data.size() - maxVisible());
        rebuild();
    }

    /** The shown row i: its type, its value, and x, on the layout's boxes. */
    private void addRuleRow(int i, String[] row) {
        DropdownSelect typeDd = new DropdownSelect(0, 0, 0, 0, "", SpawnRulesOps.TYPE_CHOICES, row[0]);
        addRenderableWidget(place(typeDd.getWidget(), layout.get("type" + i)));
        // What the condition tests and its value's format.
        String help = com.strata.model.SpawnRule.help(com.strata.model.SpawnRule.Type.parse(row[0]));
        typeDd.getWidget().setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(help)));
        typeDd.setOnChange(s -> {
            pushUndo();
            typeChangedRows.add(row);
            row[0] = s;
            rebuild();
        });

        boolean typeJustChanged = typeChangedRows.remove(row);
        List<String> catValues = SpawnRulesOps.valueChoices(row[0]);
        if (catValues != null) {
            String initial;
            if (catValues.contains(row[1])) {
                initial = row[1];
            } else if (typeJustChanged) {
                // Real user type-switch: sync the stored value to the first valid token - pushUndo already
                // ran.
                initial = catValues.getFirst();
                row[1] = initial;
            } else {
                // Load/scroll/undo/add/remove: PRESERVE stored value.
                initial = row[1];
            }
            DropdownSelect valueDd = new DropdownSelect(0, 0, 0, 0, "", catValues, initial);
            addRenderableWidget(place(valueDd.getWidget(), layout.get("value" + i)));
            valueDd.getWidget().setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(help)));
            valueDd.setOnChange(s -> {
                pushUndo();
                row[1] = s;
            });
        } else if ("structure".equals(row[0])) {
            String[] captured = row;
            addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("value" + i)),
                    b -> openStructurePicker(captured)).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal(help))).build(), layout.get("value" + i)));
        } else {
            EditField valueField = new EditField(this.font, 0, 0, 0, 0, "value", row[1]);
            place(valueField.getWidget(), layout.get("value" + i));
            // Red as typed, with the reason and the format in the tooltip, from the same check the save runs
            //.
            valueField.setValidator((EditField.Check) s -> SpawnRulesOps.valueProblem(row[0], s));
            valueField.setResponder(s -> {
                pushUndo();
                row[1] = s;
            });
            // The format while the value is fine; the reason while it is not.
            valueField.setHelp(help);
            addRenderableWidget(valueField.getWidget());
        }

        String[] captured = row;
        addRenderableWidget(place(Button.builder(Component.literal("×"), b -> removeRow(captured)).build(),
                layout.get("remove" + i)));
    }

    /** The field's tooltip: why its value is refused, or nothing when it is fine. */

    private void openStructurePicker(String[] row) {
        // RegistryQuery#structuresFor picks the registries: the open world's, else the startup pack-built
        // ones (RS-1).
        var structures = RegistryQuery.structuresFor(
                this.minecraft != null && this.minecraft.level != null
                        ? worldRegistries() : null);
        if (structures.isEmpty()) {
            refuse("No structures: the game data could not be read at game start (see the log)");
            return;
        }
        pushUndo();
        pendingSnapshot = Snapshot.of(data);
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "Pick a structure",
                structures,
                id -> {
                    row[1] = id;
                    pendingSnapshot = Snapshot.of(data);
                }));
    }

    private void removeRow(String[] target) {
        pushUndo();
        data.removeIf(r -> r == target);
        rebuild();
    }

    private void pushUndo() {
        undoMgr.pushState(Snapshot.of(data));
    }

    private void applySnapshot(Snapshot snap) {
        data.clear();
        data.addAll(snap.rules());
        scrollOffset = 0;
        rebuild();
    }

    private void undoAction() {
        Snapshot prev = undoMgr.undo();
        if (prev != null) applySnapshot(prev);
    }

    private void save() {
        // One call: SpawnRulesOps checks the set, writes it, and reloads.
        if (!report(SpawnRulesOps.replaceAll(scope, scopeId, entity, variantName, data))) {
            return;
        }
        // Save stays: the saved rows are the new start, and they are this scope's own now.
        baseline = Snapshot.of(data);
        inherits = false;
        savedHere();
        rebuild();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // The context line above the bar, and the empty text, from the layout.
        titles(graphics, layout);
        text(graphics, layout.get("context"), Palette.LABEL);
        if (layout.get("empty") != null) {
            text(graphics, layout.get("empty"), Palette.MUTED);
        }
    }
}

