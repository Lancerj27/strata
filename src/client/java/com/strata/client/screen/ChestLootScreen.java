package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.domain.ChestsOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One chest loot table as a list of loot entries: the entries by pool, each with its item, count, and share
 * of the pool's picks.
 */
public class ChestLootScreen extends StrataScreen {

    private final String table;
    private final String name;
    private BrowseList list;
    private Button removeButton;
    private List<ChestsOps.Entry> entries = List.of();
    private List<ChestsOps.Pool> pools = List.of();
    private String status = "";

    /** True for a block's drops (forBlock), so the raw table's title says "Block loot". */
    private final boolean block;

    private ChestLootScreen(String table, String name, String title, boolean block) {
        super(block ? com.strata.ui.TitlePath.BLOCKS : com.strata.ui.TitlePath.ITEMS, title);
        this.table = table;
        this.name = name;
        this.block = block;
    }

    /** Any loot table in this editor, from World > Loot tables. */
    public static ChestLootScreen forTable(String table, String name) {
        return new ChestLootScreen(table, name, "Strata — Loot table: " + name, false);
    }

    /** A block's drops: its loot table in this editor. */
    public static ChestLootScreen forBlock(String table, String name) {
        return new ChestLootScreen(table, name, "Strata — Block drops: " + name, true);
    }

    @Override
    protected void init() {
        super.init();
        load();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table.
        list = null;
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ChestLootScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        // A double-click presses Edit.
        list.setOnActivate(entry -> editSelected());

        Button add = Button.builder(Component.literal("+ Add loot entry"), b -> openEntry(null)).build();
        Button edit = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        removeButton = Button.builder(Component.literal("Remove"), b -> removePicked()).build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
            ChestsOps.Entry e = selectedEntry();
            if (e != null) act(ChestsOps.restoreEntry(table, e));
        }).build();
        Button restoreAll = Button.builder(Component.literal("Restore all"), b -> minecraft.setScreen(
                new com.strata.client.widget.ConfirmDialog(this, "Restore " + name + " to the game's loot?\n"
                        + "Every loot entry you added goes, and every value you changed.",
                        () -> act(ChestsOps.restoreAll(table))))).build();
        Button raw = Button.builder(Component.literal("Loot table"),
                b -> ScreenNavigator.get().navigateTo(LootTableScreen.forTable(table, name, block))).build();
        placeAll(layout, new String[]{"add", "edit", "remove", "restore", "restoreAll", "table"},
                add, edit, removeButton, restore, restoreAll, raw);
        needsSelection(() -> selectedEntry() != null || selectedPool() != null, edit);
        needsSelection(() -> !pickedEntries().isEmpty(), removeButton);
        needsSelection(() -> selectedEntry() != null, restore);

        rebuild();
        list.restore(keepList);
    }

    private void load() {
        var sheet = ChestsOps.sheet(table);
        entries = ChestsOps.entries(sheet);
        pools = ChestsOps.pools(sheet);
    }

    private com.strata.ui.Layout layout() {
        int picked = pickedEntries().size();
        return com.strata.ui.screen.ListScreenLayouts.chestLoot(frame(), this.font::width, title(),
                picked > 1 ? "Remove (" + picked + ")" : "Remove", list != null && list.getSelected() != null, status,
                entries.isEmpty() ? "No loot entries: the table could not be read at game start (see the log)" : null);
    }

    private ChestsOps.Entry selectedEntry() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith("pool:") ? null : entries.get(Integer.parseInt(sel.id));
    }

    private ChestsOps.Pool selectedPool() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || !sel.id.startsWith("pool:") ? null : pools.get(Integer.parseInt(sel.id.substring(5)));
    }

    /** The picked entries (Ctrl, Shift), the pools' rows left out. */
    private List<ChestsOps.Entry> pickedEntries() {
        return list == null ? List.of() : list.pickedIds().stream().filter(id -> !id.startsWith("pool:"))
                .map(id -> entries.get(Integer.parseInt(id))).toList();
    }

    private void editSelected() {
        ChestsOps.Pool pool = selectedPool();
        if (pool != null) {
            ScreenNavigator.get().navigateTo(new ChestPoolScreen(table, name, pool));
            return;
        }
        ChestsOps.Entry e = selectedEntry();
        if (e != null) {
            openEntry(e);
        }
    }

    /** The one-entry form: a new entry goes into the selected row's pool, else the first. */
    private void openEntry(ChestsOps.Entry e) {
        ChestsOps.Entry at = selectedEntry();
        ChestsOps.Pool pool = selectedPool();
        int into = e != null ? e.pool() : at != null ? at.pool() : pool != null ? pool.index() : 0;
        ScreenNavigator.get().navigateTo(new ChestEntryScreen(table, name, e, into));
    }

    /** Removes the picked entries: one at once, several after a question that names them (drift.31). */
    private void removePicked() {
        List<ChestsOps.Entry> picked = pickedEntries();
        if (picked.isEmpty()) {
            return;
        }
        if (picked.size() == 1) {
            act(ChestsOps.removeEntries(table, picked));
            return;
        }
        String names = com.strata.domain.Picks.listed(picked.stream().map(e -> Labels.itemName(e.item())).toList());
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                "Remove " + picked.size() + " loot entries?\n" + names,
                () -> act(ChestsOps.removeEntries(table, picked))));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            load();
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> rows = new ArrayList<>();
        for (ChestsOps.Pool pool : pools) {
            String id = "pool:" + pool.index();
            String rolls = pool.rollsMin().equals(pool.rollsMax()) ? pool.rollsMin()
                    : pool.rollsMin() + " to " + pool.rollsMax();
            rows.add(new BrowseList.Entry(id, "Pool " + (pool.index() + 1) + " — picks " + com.strata.ui.Words.count(rolls, "time", "times"),
                    "Edit sets how many times it picks", Palette.GROUP, id, null, 0));
            for (int i = 0; i < entries.size(); i++) {
                ChestsOps.Entry e = entries.get(i);
                if (e.pool() != pool.index()) {
                    continue;
                }
                String item = e.item().isEmpty() ? Labels.pretty(e.type()) : Labels.itemName(e.item());
                String count = e.countMin().equals(e.countMax()) ? e.countMin() : e.countMin() + "-" + e.countMax();
                String sub = e.removed() ? "removed (weight 0): Restore brings it back"
                        : "weight " + e.weight() + " · " + ChestsOps.share(e, pools) + " of the pool's picks"
                        + (e.added() ? " · yours" : e.edited() ? " · changed" : "");
                int color = e.removed() ? Palette.MUTED : e.added() || e.edited() ? Palette.EDITED : Palette.TEXT;
                rows.add(new BrowseList.Entry(String.valueOf(i), item + " ×" + count, sub, color, null, id, 1));
            }
        }
        list.refresh(rows);
        status = entries.size() + " loot entries in " + com.strata.ui.Words.count(pools.size(), "pool", "pools") + ". From the next world load.";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        removeButton.setMessage(Component.literal(layout.textOf("remove")));
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}

