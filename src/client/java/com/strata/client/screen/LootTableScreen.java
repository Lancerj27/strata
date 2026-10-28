package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.client.widget.ValueInput;
import com.strata.data.LootJson;
import com.strata.domain.LootTableOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A mob's game loot table, every value editable: each pool's rolls, each entry's item and weight, each
 * function's counts and looting bonus, each condition's chance.
 */
public class LootTableScreen extends StrataScreen {

    /** The mob whose table this is, or null for a table opened by its id (a chest's). */
    private final String entity;
    /** The table's id when opened by it, else null. */
    private final String tableId;
    private LootTableOps.Sheet sheet = new LootTableOps.Sheet("", List.of());
    private BrowseList list;
    private ValueInput valueInput;
    private EditField search;

    public LootTableScreen(String entity) {
        super("Strata — Mob: " + Labels.creature(entity) + " / Loot table");
        this.entity = entity;
        this.tableId = null;
        editingMob(entity, "");
    }

    /** A table by its id: a chest's, named in words. */
    private LootTableScreen(String table, String name, boolean block) {
        // A chest's table is under Items, where Chest loot is.
        super(block ? com.strata.ui.TitlePath.BLOCKS : com.strata.ui.TitlePath.ITEMS,
                (block ? "Strata — Block loot: " : "Strata — Chest loot: ") + name);
        this.entity = null;
        this.tableId = table;
    }

    /** A table by its id; {@code block} when it is a block's drops, else a chest's. */
    public static LootTableScreen forTable(String table, String name, boolean block) {
        return new LootTableScreen(table, name, block);
    }

    private LootTableOps.Sheet load() {
        return tableId != null ? LootTableOps.sheetForTable(tableId) : LootTableOps.sheet(entity);
    }

    @Override
    protected void init() {
        super.init();
        sheet = load();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("LootTableScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            var row = sheet.values().get(Integer.parseInt(entry.id));
            valueInput.show(LootTableOps.typeOf(row, sheet.values()), LootJson.shown(row.effective()));
        });
        addRenderableWidget(list);
        // The search: a table often holds over 100 values.
        String keepSearch = search == null ? "" : search.getValue();
        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> list.setFilter(s));
        addRenderableWidget(place(search.getWidget(), layout.get("search")));

        valueInput = valueInput(layout, "value", this::setValue);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> setValue())
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(LootTableOps.restore(sheet.table(), v));
                })
                .build();
        placeAll(layout, new String[]{"set", "restore"}, set, restore);
        // ONE PLACE: loot entries are added and removed in the entry list (ChestLootScreen); this sheet is
        // every raw value.
        needsSelection(() -> selected() != null, set, restore);
        rebuild();
        list.restore(keepList);
    }

    /** Set value: what the control holds, to the selected row (an item picked from the picker calls it too). */
    private void setValue() {
        var v = selected();
        if (v != null) {
            act(LootTableOps.save(sheet.table(), v, valueInput.value()));
        }
    }

    private LootTableOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : sheet.values().get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            sheet = load();
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < sheet.values().size(); i++) {
            LootTableOps.Value v = sheet.values().get(i);
            boolean locked = LootTableOps.lockReason(v) != null;
            // Locked values only when asked for (Options > Show locked values).
            if (locked && !com.strata.domain.OptionsOps.viewLocked()) {
                continue;
            }
            // "Name: value", the entry named by its item, and what it does under it.
            String sub = LootTableOps.what(v.path())
                    + (v.own().isEmpty() ? "" : "  (yours; the game's: " + LootJson.shown(v.game()) + ")")
                    + (locked ? "  (locked)" : "");
            entries.add(new BrowseList.Entry(String.valueOf(i), LootTableOps.label(v.path(), sheet.values(),
                    Labels::itemName) + ": " + LootJson.shown(v.effective()), sub,
                    locked ? Palette.LOCKED : v.own().isEmpty() ? Palette.TEXT : Palette.EDITED));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        LootTableOps.Value v = list == null ? null : selected();
        String lock = v == null ? null : LootTableOps.lockReason(v);
        String help = v == null ? "Select a value. Yellow values are yours. Changes apply from the next world load."
                : lock != null ? "Locked: " + lock + "."
                : LootTableOps.what(v.path()) + " The game's: " + LootJson.shown(v.game())
                        + ". Applies from the next world load.";
        String footer = sheet.table().isEmpty() ? "" : "Table " + sheet.table() + ", "
                + com.strata.ui.Words.count(sheet.values().size(), "value", "values");
        String empty = sheet.table().isEmpty()
                ? "No loot table: the game gives this none, or it could not be read at game start (see the log)."
                : null;
        return com.strata.ui.screen.ListScreenLayouts.valueSheet(frame(), this.font::width, title(), 180, help,
                footer, empty, true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        if (layout.get("footer") != null) {
            text(graphics, layout.get("footer"), Palette.MUTED);
        }
        emptyNote(graphics, layout, list);
    }
}
