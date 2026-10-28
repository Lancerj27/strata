package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.domain.TradesOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** A trading mob's trades at one scope, opened from the mob editor's Trades. */
public class TradesScreen extends StrataScreen {

    private final String entity;
    private final String scope;
    private final String scopeId;
    private BrowseList list;
    private Button removeButton;
    private List<TradesOps.TradeRow> rows = List.of();
    private List<String> lists = List.of();

    private String status = "";

    public TradesScreen(String entity, String scope, String scopeId) {
        super("Strata — Trades: " + Labels.creature(entity) + " (" + Labels.scope(scope, scopeId) + ")");
        this.entity = entity;
        this.scope = scope;
        this.scopeId = scopeId == null ? "" : scopeId;
    }

    @Override
    protected void init() {
        super.init();
        load();
        BrowseList.State keepList = list == null ? null : list.state();
        list = null;
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("TradesScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        list.setOnActivate(entry -> editSelected());

        Button add = Button.builder(Component.literal("+ Add trade"), b -> openTrade(null)).build();
        Button edit = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        removeButton = Button.builder(Component.literal("Remove"), b -> removePicked()).build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
            TradesOps.TradeRow r = selectedRow();
            if (r != null) act(TradesOps.restore(scope, scopeId, r));
        }).build();
        Button restoreAll = Button.builder(Component.literal("Restore all"), b -> minecraft.setScreen(
                new com.strata.client.widget.ConfirmDialog(this, "Restore " + Labels.creature(entity)
                        + "'s trades here to the wider scope's?\nEvery trade added here goes, and every change made here.",
                        () -> act(TradesOps.restoreAll(entity, scope, scopeId))))).build();
        placeAll(layout, new String[]{"add", "edit", "remove", "restore", "restoreAll"},
                add, edit, removeButton, restore, restoreAll);
        needsSelection(() -> selectedRow() != null, edit);
        needsSelection(() -> !pickedRows().isEmpty(), removeButton);
        needsSelection(() -> selectedRow() != null, restore);

        rebuild();
        list.restore(keepList);
    }

    private void load() {
        rows = TradesOps.rows(entity, scope, scopeId);
        lists = TradesOps.listsFor(entity);

    }

    private com.strata.ui.Layout layout() {
        int picked = pickedRows().size();
        return com.strata.ui.screen.ListScreenLayouts.tradeList(frame(), this.font::width, title(),
                picked > 1 ? "Remove (" + picked + ")" : "Remove", list != null && selectedRow() != null, status,
                lists.isEmpty() ? "No trades: the game's lists are read at game start (see the log)" : null);
    }

    private TradesOps.TradeRow selectedRow() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith("list:") ? null : rows.get(Integer.parseInt(sel.id));
    }

    /** The list a new trade goes into: the selected row's, else the selected group's, else the first. */
    private String selectedList() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        if (sel != null && sel.id.startsWith("list:")) {
            return sel.id.substring(5);
        }
        TradesOps.TradeRow r = selectedRow();
        return r != null ? r.list() : lists.isEmpty() ? null : lists.get(0);
    }

    private List<TradesOps.TradeRow> pickedRows() {
        return list == null ? List.of() : list.pickedIds().stream().filter(id -> !id.startsWith("list:"))
                .map(id -> rows.get(Integer.parseInt(id))).toList();
    }

    private void editSelected() {
        TradesOps.TradeRow r = selectedRow();
        if (r != null && !r.removed()) {
            openTrade(r);
        }
    }

    private void openTrade(TradesOps.TradeRow r) {
        String into = r != null ? r.list() : selectedList();
        if (into != null) {
            ScreenNavigator.get().navigateTo(new TradeEntryScreen(scope, scopeId, into, r));
        }
    }

    /** Removes the picked trades: one at once, several after a question that names them (drift.31). */
    private void removePicked() {
        List<TradesOps.TradeRow> picked = pickedRows();
        if (picked.isEmpty()) {
            return;
        }
        if (picked.size() == 1) {
            act(TradesOps.remove(scope, scopeId, picked));
            return;
        }
        String names = com.strata.domain.Picks.listed(picked.stream().map(r -> tradeText(r)).toList());
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                "Remove " + picked.size() + " trades?\n" + names,
                () -> act(TradesOps.remove(scope, scopeId, picked))));
    }

    private static String tradeText(TradesOps.TradeRow r) {
        return TradesOps.text(r.trade(), Labels::itemName);
    }

    private void act(WriteReport result) {
        if (report(result)) {
            load();
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (String l : lists) {
            String id = "list:" + l;
            String prof = l.substring(0, l.lastIndexOf('|'));
            int level = Integer.parseInt(l.substring(l.lastIndexOf('|') + 1));
            entries.add(new BrowseList.Entry(id, TradesOps.listName(prof, level),
                    "The game picks from this list when the trader reaches it", Palette.GROUP, id, null, 0));
            for (int i = 0; i < rows.size(); i++) {
                TradesOps.TradeRow r = rows.get(i);
                if (!r.list().equals(l)) {
                    continue;
                }
                String sub = r.removed() ? "removed here: Restore brings it back"
                        : "max uses " + r.trade().maxUses() + " · XP " + r.trade().xp() + " · price multiplier "
                        + com.strata.data.Parse.number(r.trade().priceMultiplier())
                        + (r.layer().isEmpty() ? "" : r.addedHere() ? " · added here"
                        : r.here() ? " · changed here" : " · from " + Labels.pretty(r.layer()));
                int color = r.removed() ? Palette.MUTED : r.here() ? Palette.EDITED : r.layer().isEmpty() ? Palette.TEXT : Palette.INHERITED;
                entries.add(new BrowseList.Entry(String.valueOf(i), tradeText(r), sub, color, null, id, 1));
            }
        }
        list.refresh(entries);
        status = "From the next time a trader reaches a level. Trades a trader has stay.";
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

