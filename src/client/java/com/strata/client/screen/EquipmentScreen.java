package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.domain.EquipmentOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The LIST half of the equipment editor: every equipment row of one variant instance, in one scope, and the
 * actions that are about the list.
 */
public class EquipmentScreen extends StrataScreen implements GroupBar.Host {

    private final String variantName;
    /** The creature this equipment is worn by: for Recent, and for its game equipment. */
    private final String baseEntity;
    /** WHICH INSTANCE of the variant this screen is editing. */
    private final String scope;
    private final String scopeId;
    /** The group row above the list, as on abilities and loot. */
    private final GroupBar groups;

    private BrowseList list;
    private List<EquipmentOps.EquipmentRow> allRows = List.of();
    /** The game's roll for the mob, for the rows' defaults on hover. */
    private com.strata.model.DefaultEquipment gameRoll;

    private Button removeButton;

    /**
     * @param baseEntity the creature this equipment is worn by. Carried for the Recent group, which names a
     *     mob by (entity, variant), and for the base entity's own equipment. A constructor parameter, so a new
     *     caller cannot omit it unnoticed.
     */
    public EquipmentScreen(String variantName, String baseEntity,
                                  String scope, String scopeId) {
        super("Strata — Equipment: " + Labels.ownerTitle(baseEntity, scopeId, variantName));
        this.variantName = variantName;
        this.baseEntity = baseEntity == null ? "" : baseEntity;
        this.scope = scope;
        this.scopeId = scopeId;
        this.groups = new GroupBar(this, this, com.strata.domain.GroupOps.KIND_EQUIPMENT, "equipment");
        editingMob(baseEntity, variantName);
    }

    /**
     * The same list over a GROUP's own rows: a group of equipment is a row set filed under the group's id in
     * the global scope, exactly the way a variant's is, so this editor already knows how to fill it and a
     * second editor for it would be a second thing to keep in step with this one.
     *
     * @param title the window title, "Strata — Group: <name>"
     * @param groupId the group's id: the owner key its rows are filed under
     */
    public static EquipmentScreen forGroup(String title, String groupId) {
        return new EquipmentScreen(title, groupId, "", "global", "");
    }

    private EquipmentScreen(String title, String variantName, String baseEntity,
                            String scope, String scopeId) {
        super(title);
        this.variantName = variantName;
        this.baseEntity = baseEntity == null ? "" : baseEntity;
        this.scope = scope;
        this.scopeId = scopeId;
        this.groups = new GroupBar(this, this, com.strata.domain.GroupOps.KIND_EQUIPMENT, "equipment");
        editingGroup(title);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        EquipmentOps.EquipmentRow selected = list == null ? null : selectedRow();
        List<EquipmentOps.EquipmentRow> picked = pickedRows();
        int countEditable = (int) allRows.stream().filter(r -> !r.inherited()).count();
        // A PICKED GROUP OWNS THE HINT LINE while one is picked: it answers what this apply
        // will do to this list, which is the question in front of the designer.
        String groupLine = groups.note();
        return com.strata.ui.screen.EquipmentLayout.build(frame(), this.font::width, title(),
                com.strata.ui.screen.EquipmentLayout.removeLabel(
                        (int) picked.stream().filter(EquipmentOps::removeDenies).count(),
                        (int) picked.stream().filter(r -> !EquipmentOps.removeDenies(r)).count()),
                groupLine.isEmpty() ? footerNote(selected, picked.size()) : groupLine,
                countEditable + " own + " + com.strata.ui.Words.count((allRows.size() - countEditable), "inherited entry", "inherited entries")
                        + " — one slot is one draw; over 100 in total = relative odds",
                allRows.isEmpty() ? "No equipment data for this variant's base entity" : null,
                groups.text(), groups.picked());
    }

    // ---- what the group row asks of this editor ----

    @Override
    public boolean canTakeGroups() {
        return true;
    }

    @Override
    public com.strata.domain.GroupOps.Added adds(String groupId) {
        return com.strata.domain.GroupOps.countsEquipment(groupId, variantName, scope, scopeId);
    }

    @Override
    public boolean stamp(String groupId) {
        // The list as shown goes with it: a group must not take away what the mob is already wearing in the
        // slots the group fills.
        if (!report(com.strata.domain.GroupOps.applyEquipment(groupId, variantName, scope, scopeId, allRows))) {
            return false;
        }
        allRows = loadRows();
        rebuild();
        return true;
    }

    @Override
    public void openGroup(com.strata.domain.GroupOps.Group group) {
        ScreenNavigator.get().navigateTo(
                EquipmentScreen.forGroup("Strata — Group: " + group.name(), group.id()));
    }

    @Override
    public void redraw() {
        rebuild();
    }

    @Override
    protected void init() {
        super.init();
        allRows = loadRows();
        // init runs again after a dialog and on a resize.
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("EquipmentScreen");
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        placeList(list, layout.get("list"));
        addRenderableWidget(list);

        Button addButton = Button.builder(Component.literal("+ Add equipment"), b -> openForm(null)).build();
        Button editButton = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        // The label follows the selected row: the base entity's own equipment is denied, an own
        // row is removed.
        removeButton = Button.builder(Component.literal(com.strata.ui.screen.EquipmentLayout.REMOVE),
                        b -> removePicked())
                .build();
        Button restoreButton = Button.builder(Component.literal("Restore all"),
                b -> confirmRestoreDefaults()).build();
        // THE BAR WRAPS to the width, the hint sits just above it, and the list ends above the hint: the
        // layout places them.
        placeAll(layout, new String[]{"add", "edit", "remove", "restore"},
                addButton, editButton, removeButton, restoreButton);
        needsSelection(() -> selectedRow() != null && !list.manyPicked(), editButton);
        needsSelection(() -> !pickedRows().isEmpty(), removeButton);

        // The group row: made and placed by GroupBar in one call.
        groups.init(this.font, layout, this::addRenderableWidget);

        rebuild();
        list.restore(keepList);
    }
    private List<EquipmentOps.EquipmentRow> loadRows() {
        gameRoll = baseEntity.isEmpty() ? null : com.strata.domain.DefaultEquipmentOps.gameRoll(baseEntity);
        return EquipmentOps.equipmentFor(variantName, baseEntity, scope, scopeId,
                com.strata.domain.DefaultEquipmentOps.gameEquipment(gameRoll));
    }

    private EquipmentOps.EquipmentRow selectedRow() {
        BrowseList.Entry sel = list.getSelected();
        return sel == null ? null : allRows.get(Integer.parseInt(sel.id));
    }

    /** The equipment rows picked with Ctrl and Shift, or the selected row alone. */
    private List<EquipmentOps.EquipmentRow> pickedRows() {
        if (list == null) {
            return List.of();
        }
        return list.pickedIds().stream().map(id -> allRows.get(Integer.parseInt(id))).toList();
    }

    /** Removes or denies the picked rows: one at once, several after a confirmation that names them. */
    private void removePicked() {
        List<EquipmentOps.EquipmentRow> rows = pickedRows();
        if (rows.isEmpty()) {
            return;
        }
        // ONE ROW GOES AT ONCE; SEVERAL ASK.
        if (rows.size() == 1) {
            act(EquipmentOps.removeEquipment(variantName, scope, scopeId, rows.getFirst(), allRows));
            return;
        }
        EquipmentOps.RemovalPlan plan = EquipmentOps.removalPlan(rows, allRows);
        StringBuilder text = new StringBuilder("Remove " + rows.size() + " equipment rows?");
        if (!plan.delete().isEmpty()) {
            text.append("\nDelete your own: ").append(rowNames(plan.delete()));
        }
        if (!plan.deny().isEmpty()) {
            text.append("\nDeny inherited: ").append(rowNames(plan.deny()));
        }
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, text.toString(),
                () -> act(EquipmentOps.removeEquipmentAll(variantName, scope, scopeId, rows, allRows))));
    }

    private static String rowNames(List<EquipmentOps.EquipmentRow> rows) {
        return com.strata.domain.Picks.listed(rows.stream()
                .map(r -> Labels.pretty(r.slot()) + " " + Labels.itemName(r.item())).toList());
    }

    /** Shows what an equipment action reported, and redraws the rows after it. */
    private void act(WriteReport result) {
        if (report(result)) {
            allRows = loadRows();
            rebuild();
        }
    }

    /** Opens the one-row editor, for a new candidate (null) or the selected row. */
    private void openForm(EquipmentOps.EquipmentRow row) {
        ScreenNavigator.get().navigateTo(new EquipmentItemScreen(
                variantName, baseEntity, scope, scopeId, allRows,
                EquipmentOps.equipmentForm(row)));
    }

    /** ONE FORM FOR EVERY ROW: the game's armor pieces open it too. */
    private void editSelected() {
        EquipmentOps.EquipmentRow row = selectedRow();
        if (row != null) {
            openForm(row);
        }
    }

    private void confirmRestoreDefaults() {
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                "Restore this equipment to its defaults?\n"
                        + "Every override and deny in this scope is removed. It goes back to what it\n"
                        + "inherits: a wider scope's equipment, its base mob's, or the game's.",
                () -> act(EquipmentOps.restoreEquipment(variantName, scope, scopeId))));
    }

    /** A lone row in its slot reads as a plain chance. */
    private static String subtitle(EquipmentOps.EquipmentRow row, double slotTotal, int candidates) {
        String own = EquipmentOps.pieceText(row);
        String s = candidates <= 1
                ? EquipmentOps.chanceText(row.chancePct()) + "% chance"
                : EquipmentOps.chanceText(row.chancePct()) + " of "
                        + EquipmentOps.chanceText(slotTotal) + " weight  ("
                        + EquipmentOps.chanceText(EquipmentOps.oddsPct(row.chancePct(), slotTotal))
                        + "% of spawns)";
        double empty = EquipmentOps.availableForSlot(slotTotal, 0);
        if (empty > 0) {
            s += ", " + EquipmentOps.chanceText(empty) + "% empty";
        }
        s += own;
        return row.inherited() ? s + "  (" + row.sourceLabel() + ")" : s;
    }

    private void rebuild() {
        // Every piece is a weight in its slot's one draw, the game's armor too.
        Map<String, Double> slotTotals = new LinkedHashMap<>();
        Map<String, Integer> slotCounts = new LinkedHashMap<>();
        for (EquipmentOps.EquipmentRow row : allRows) {
            slotTotals.merge(row.slot(), row.chancePct(), Double::sum);
            slotCounts.merge(row.slot(), 1, Integer::sum);
        }

        // THE REAL CHANCES BY DIFFICULTY, on hover: the game's defaults where a row leaves them blank
        // (DefaultEquipmentOps#odds).
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < allRows.size(); i++) {
            EquipmentOps.EquipmentRow row = allRows.get(i);
            double slotTotal = slotTotals.getOrDefault(row.slot(), row.chancePct());
            entries.add(new BrowseList.Entry(String.valueOf(i),
                    Labels.pretty(row.slot()) + ": " + Labels.itemName(row.item()),
                    subtitle(row, slotTotal, slotCounts.getOrDefault(row.slot(), 1)),
                    row.inherited() ? Palette.INHERITED : Palette.TEXT)
                    .tooltip(String.join("\n", com.strata.domain.DefaultEquipmentOps.odds(row, gameRoll, slotTotal))));
        }
        list.refresh(entries);
    }

    /**
     * What the list's own buttons will do to the selected row, or what to click first when nothing is
     * selected.
     */
    private String footerNote(EquipmentOps.EquipmentRow selected, int picked) {
        if (picked > 1) {
            return picked + " rows picked: Remove deletes your own rows and denies inherited ones, together.";
        }
        if (selected == null) {
            return "Select a row to Edit, Remove, or Deny it. Ctrl or Shift picks several.";
        }
        return EquipmentOps.removeDenies(selected)
                ? "Deny blocks a piece with no row here (" + selected.sourceLabel() + " equipment). The rest of the slot is kept."
                : "Remove deletes your own row. The rest of the slot is kept. Edit opens it on its own screen.";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        removeButton.setMessage(Component.literal(layout.textOf("remove")));
        super.render(graphics, mouseX, mouseY, partialTick);
        text(graphics, layout.get("heading0"), Palette.LABEL);
        // The footer, the hint, and the empty-list text, from the layout.
        for (String id : new String[]{"footer", "hint0", "empty"}) {
            if (layout.get(id) != null) {
                text(graphics, layout.get(id), Palette.MUTED);
            }
        }
        text(graphics, layout.get("label.groupName"), Palette.LABEL);
    }}

