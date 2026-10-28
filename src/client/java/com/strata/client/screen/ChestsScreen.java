package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.ChestsOps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Every chest loot table: the game's and every mod's, grouped by the structure (Village, Shipwreck,
 * Bastion...).
 */
public class ChestsScreen extends StrataScreen {

    private BrowseList list;
    private EditField search;
    private List<ChestsOps.ChestTable> tables = List.of();
    private String status = "";
    /** Items > Chest loot (true) or Items > Loot tables (false). */
    private final boolean chests;

    /**
     * Items > Loot tables: every table but the chests', and but the mobs' and blocks', which their own Loot
     * edits, unless Options > Show duplicate editors is on.
     */
    @Override
    protected String helpKey() {
        return chests ? "ChestsScreen:chests" : "ChestsScreen:tables";
    }

    public static ChestsScreen lootTables() {
        return new ChestsScreen(false);
    }

    /** Items > Chest loot: the chests' tables, by structure. */
    public static ChestsScreen chestLoot() {
        return new ChestsScreen(true);
    }

    private ChestsScreen(boolean chests) {
        super(com.strata.ui.TitlePath.ITEMS, chests ? "Strata — Chest loot" : "Strata — Loot tables");
        this.chests = chests;
    }

    @Override
    protected void init() {
        super.init();
        tables = ChestsOps.tables(chests, com.strata.domain.OptionsOps.showDuplicates());
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old list.
        list = null;

        com.strata.ui.Layout layout = layout(false);
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> rebuild());
        addRenderableWidget(place(search.getWidget(), layout.get("search")));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ChestsScreen");
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, tables, ChestsOps.ChestTable::table));
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        Button edit = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        placeAll(layout, new String[]{"edit"}, edit);
        needsSelection(() -> selected() != null, edit);
        // A double-click presses Edit.
        list.setOnActivate(entry -> editSelected());

        rebuild();
        list.restore(keepList);
    }

    private com.strata.ui.Layout layout(boolean picked) {
        return com.strata.ui.screen.ListScreenLayouts.lootList(frame(), this.font::width, title(), picked, status,
                tables.isEmpty() ? "No loot tables: they could not be read at game start (see the log)" : null);
    }

    private ChestsOps.ChestTable selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith("grp:") ? null : tables.get(Integer.parseInt(sel.id));
    }

    private void editSelected() {
        ChestsOps.ChestTable t = selected();
        if (t != null) {
            ScreenNavigator.get().navigateTo(ChestLootScreen.forTable(t.table(), t.group() + " — " + t.name()));
        }
    }

    private void rebuild() {
        String text = search == null ? "" : search.getValue().strip().toLowerCase();
        List<BrowseList.Entry> entries = new ArrayList<>();
        int shown = 0;
        String group = null;
        List<BrowseList.Entry> rows = new ArrayList<>();
        for (int i = 0; i <= tables.size(); i++) {
            ChestsOps.ChestTable t = i < tables.size() ? tables.get(i) : null;
            if (t == null || !t.group().equals(group)) {
                if (group != null && !rows.isEmpty()) {
                    String id = "grp:" + group;
                    entries.add(new BrowseList.Entry(id, group + " (" + rows.size() + ")", "", Palette.GROUP, id, null, 0));
                    entries.addAll(rows);
                }
                if (t == null) {
                    break;
                }
                group = t.group();
                rows = new ArrayList<>();
            }
            if (!text.isEmpty() && !t.name().toLowerCase().contains(text) && !t.group().toLowerCase().contains(text)
                    && !t.table().contains(text.replace(' ', '_'))) {
                continue;
            }
            rows.add(new BrowseList.Entry(String.valueOf(i), t.name(), t.table() + ", " + com.strata.ui.Words.count(t.values(), "value", "values")
                    + (t.edited() ? "  (edited)" : ""), t.edited() ? Palette.EDITED : Palette.TEXT, null, "grp:" + group, 1));
            shown++;
        }
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
        status = com.strata.ui.Words.count(shown, "loot table", "loot tables");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout(selected() != null);
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}
