package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.domain.LootOps;
import com.strata.domain.WriteReport;
import com.strata.model.OverrideMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Loot override browser for one entity at a given scope. */
public class LootScreen extends StrataScreen implements GroupBar.Host {

    private final String scope;
    private final String scopeId;
    private final String entity;
    /** WHICH VARIANT's drops these are, blank for the base mob's. */
    private final String variantName;
    /** The group row above the list, as on abilities and equipment. */
    private final GroupBar groups;

    /** The Remove button's two labels: which one depends on the selected row. */
    private static final String REMOVE = "Remove";

    private BrowseList list;
    private Button removeButton;
    private String status = "";
    private List<LootOps.LootRow> allRows = List.of();

    public LootScreen(String scope, String scopeId, String entity, String variantName) {
        // Under its mob.
        super("Strata — Mob: " + Labels.ownerTitle(entity, scopeId, variantName) + " / Loot");
        this.scope = scope == null ? "" : scope;
        this.scopeId = scopeId == null ? "" : scopeId;
        this.entity = entity == null ? "" : entity;
        this.variantName = variantName == null ? "" : variantName.strip();
        this.groups = new GroupBar(this, this, com.strata.domain.GroupOps.KIND_LOOT, "drops");
        // Drops are this mob's data, so saving one is working on the mob.
        editingMob(this.entity, this.variantName);
    }

    /**
     * The same list over a GROUP's own rows.
     *
     * @param title the window title, "Strata — Group: <name>"
     * @param groupId the group's id: the owner key its rows are filed under
     */
    public static LootScreen forGroup(String title, String groupId) {
        return new LootScreen(title, "global", "", "", groupId);
    }

    private LootScreen(String title, String scope, String scopeId, String entity, String variantName) {
        super(title);
        this.scope = scope == null ? "" : scope;
        this.scopeId = scopeId == null ? "" : scopeId;
        this.entity = entity == null ? "" : entity;
        this.variantName = variantName == null ? "" : variantName.strip();
        this.groups = new GroupBar(this, this, com.strata.domain.GroupOps.KIND_LOOT, "drops");
        editingGroup(title);
    }

    @Override
    protected void init() {
        super.init();
        allRows = loadRows();
        // init runs again on the return from the entry editor.
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("LootScreen");
        placeList(list, layout.get("list"));
        // CLICK SELECTS; it does not navigate.
        addRenderableWidget(list);

        Button addButton = Button.builder(Component.literal("+ Add loot entry"), b -> openEditor(null)).build();
        Button editButton = Button.builder(Component.literal("Edit"),
                b -> { LootOps.LootRow row = selectedRow(); if (row != null) openEditor(row); }).build();
        // "Remove" on a row the game supplies cannot delete anything - there is no row to delete - so it
        // DENIES it instead.
        removeButton = Button.builder(Component.literal(REMOVE), b -> removePicked()).build();
        Button restoreButton = Button.builder(Component.literal("Restore all"),
                b -> confirmRestoreDefaults()).build();
        // Every value of the mob's game loot table.
        Button tableButton = Button.builder(Component.literal("Loot table"),
                b -> ScreenNavigator.get().navigateTo(new LootTableScreen(entity))).build();
        // THE BAR WRAPS to the width, and the list ends above it.
        placeAll(layout, new String[]{"add", "edit", "remove", "restore", "table"},
                addButton, editButton, removeButton, restoreButton, tableButton);
        needsSelection(() -> selectedRow() != null && !list.manyPicked(), editButton);
        needsSelection(() -> !list.pickedIds().isEmpty(), removeButton);

        // The group row: made and placed by GroupBar in one call.
        groups.init(this.font, layout, this::addRenderableWidget);

        rebuild();
        list.restore(keepList);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        List<LootOps.LootRow> picked = pickedRows();
        int denies = (int) picked.stream().filter(LootOps::removeDenies).count();
        // A PICKED GROUP OWNS THE FOOTER while one is picked: it answers what this apply will do to this
        // list, which is the question in front of the designer, and the count is one number they already have
        // in the rows above them.
        String groupLine = groups.note();
        return com.strata.ui.screen.ListScreenLayouts.lootList(frame(), this.font::width, title(),
                com.strata.ui.screen.ListLayout.manyLabel(REMOVE, REMOVE, picked.size() - denies, denies),
                groupLine.isEmpty() ? status : groupLine,
                allRows.isEmpty() ? "No loot data for " + Labels.creature(entity)
                        + " — Minecraft's data has none for this mob" : null,
                groups.text(), groups.picked());
    }

    // ---- what the group row asks of this editor ----

    @Override
    public boolean canTakeGroups() {
        return true;
    }

    @Override
    public com.strata.domain.GroupOps.Added adds(String groupId) {
        return com.strata.domain.GroupOps.countsLoot(groupId, entity, variantName, scope, scopeId);
    }

    @Override
    public boolean stamp(String groupId) {
        if (!report(com.strata.domain.GroupOps.applyLoot(groupId, entity, variantName, scope, scopeId))) {
            return false;
        }
        allRows = loadRows();
        rebuild();
        return true;
    }

    @Override
    public void openGroup(com.strata.domain.GroupOps.Group group) {
        ScreenNavigator.get().navigateTo(
                LootScreen.forGroup("Strata — Group: " + group.name(), group.id()));
    }

    @Override
    public void redraw() {
        rebuild();
    }
    private List<LootOps.LootRow> loadRows() {
        List<LootOps.GameLootItem> drops = com.strata.runtime.VanillaData.lootItems(entity, this.minecraft)
                .stream().map(d -> new LootOps.GameLootItem(d.item(), d.countMin(), d.countMax()))
                .toList();
        return LootOps.rowsFor(scope, scopeId, entity, variantName, drops);
    }

    private LootOps.LootRow selectedRow() {
        BrowseList.Entry sel = list.getSelected();
        return sel == null ? null : allRows.get(Integer.parseInt(sel.id));
    }

    /** The rows picked with Ctrl and Shift, or the selected row alone. */
    private List<LootOps.LootRow> pickedRows() {
        return list == null ? List.of()
                : list.pickedIds().stream().map(id -> allRows.get(Integer.parseInt(id))).toList();
    }

    /** Removes or denies the picked drops: one at once, several after a confirmation that names them. */
    private void removePicked() {
        List<LootOps.LootRow> rows = pickedRows();
        if (rows.isEmpty()) {
            return;
        }
        // ONE ROW GOES AT ONCE; SEVERAL ASK.
        if (rows.size() == 1) {
            act(LootOps.remove(scope, scopeId, entity, variantName, rows.getFirst(), allRows));
            return;
        }
        List<String> delete = rows.stream().filter(r -> !LootOps.removeDenies(r)).map(r -> Labels.itemName(r.item())).toList();
        List<String> deny = rows.stream().filter(LootOps::removeDenies).map(r -> Labels.itemName(r.item())).toList();
        StringBuilder text = new StringBuilder("Remove " + rows.size() + " loot entries?");
        if (!delete.isEmpty()) {
            text.append("\nYour own: ").append(com.strata.domain.Picks.listed(delete));
        }
        if (!deny.isEmpty()) {
            text.append("\nStop dropping (the game's or inherited): ").append(com.strata.domain.Picks.listed(deny));
        }
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, text.toString(),
                () -> act(LootOps.removeAll(scope, scopeId, entity, variantName, rows, allRows))));
    }



    /** Opens the entry editor on a row, or on a new entry when row is null. */
    private void openEditor(LootOps.LootRow row) {
        ScreenNavigator.get().navigateTo(new LootEntryScreen(entity, variantName,
                LootOps.targetFor(row, scope, scopeId, allRows), LootOps.formFor(row)));
    }

    /** Shows what a loot action reported, and redraws the rows after it. */
    private void act(WriteReport result) {
        if (report(result)) {
            allRows = loadRows();
            rebuild();
        }
    }

    private void confirmRestoreDefaults() {
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                "Restore this mob's loot to the game's defaults?\n"
                        + "Every override and deny on this entry is removed.",
                () -> act(LootOps.restoreDefaults(scope, scopeId, entity, variantName))));
    }

    private static String displayName(LootOps.LootRow row) {
        String name = Labels.itemName(row.item());
        return row.countMin() == row.countMax()
                ? name + " ×" + row.countMin()
                : name + " ×" + row.countMin() + "-" + row.countMax();
    }

    /** One drop's second line, in words: "50% · Add · loot pool 0 · only if Killed By Player". */
    private static String subtitle(LootOps.LootRow row) {
        // Words as the dropdowns show them.
        String s = row.chancePct() + "% · " + com.strata.ui.ChoiceWords.word(row.mode().lower())
                + " · loot pool " + row.poolIndex();
        if (!row.conditionType().isEmpty()) {
            String value = row.conditionValue();
            if ("death_cause".equals(row.conditionType()) && !value.isEmpty()) {
                value = com.strata.domain.LootOps.deathCauseLabel(value);
            }
            s += " · only if: " + com.strata.ui.ChoiceWords.word(row.conditionType())
                    + (value.isEmpty() ? "" : " " + value);
        }
        if (row.inherited()) s += " · from " + row.inheritedFrom();
        return s;
    }

    private static int color(LootOps.LootRow row) {
        if (row.inherited()) return Palette.INHERITED;
        if (row.mode() == OverrideMode.DENY) return Palette.MUTED;
        return Palette.TEXT;
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < allRows.size(); i++) {
            LootOps.LootRow row = allRows.get(i);
            entries.add(new BrowseList.Entry(String.valueOf(i),
                    displayName(row), subtitle(row), color(row)));
        }
        list.refresh(entries);
        status = entries.size() + " loot entries — Add: dropped as well · Replace: clears the game's loot first";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        removeButton.setMessage(Component.literal(layout.textOf("remove")));
        super.render(graphics, mouseX, mouseY, partialTick);
        text(graphics, layout.get("footer"), Palette.MUTED);
        text(graphics, layout.get("label.groupName"), Palette.LABEL);
        emptyNote(graphics, layout, list);
    }
}
