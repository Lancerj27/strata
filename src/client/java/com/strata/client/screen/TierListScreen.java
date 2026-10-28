package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.TierOps;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The tier list. */
public class TierListScreen extends StrataScreen {

    private BrowseList list;
    private EditField search;

    public TierListScreen() {
        super("Strata — Tiers");
    }

    @Override
    protected void init() {
        super.init();
        // init runs again on every return here.
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = com.strata.ui.screen.ListScreenLayouts.tierList(frame(), this.font::width,
                title(), true);
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);

        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> { if (list != null) list.setFilter(s); });
        addRenderableWidget(place(search.getWidget(), layout.get("search")));


        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22));
        placeList(list, layout.get("list"));
        rebuild();
        list.restore(keepList);
        addRenderableWidget(list);

        // A click selects the tier; Edit (or a double-click) opens it, Duplicate copies it.
        Button edit = Button.builder(Component.literal("Edit"), b -> {
            String name = selectedTier();
            if (name != null) {
                ScreenNavigator.get().navigateTo(new TierEditScreen(name));
            }
        }).build();
        Button duplicate = Button.builder(Component.literal("Duplicate"), b -> duplicateSelected()).build();
        placeAll(layout, new String[]{"add", "edit", "duplicate"}, Button.builder(Component.literal("+ Add tier"),
                b -> ScreenNavigator.get().navigateTo(new TierEditScreen(null))).build(), edit, duplicate);
        needsSelection(() -> selectedTier() != null, edit, duplicate);
    }

    /** The selected tier's name, or null (nothing, or a group header). */
    private String selectedTier() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith("grp:") ? null : sel.id;
    }

    /** Copies the selected tier to a new one and opens the copy. */
    private void duplicateSelected() {
        String name = selectedTier();
        if (name == null) {
            return;
        }
        String copyName = TierOps.copyName(name, TierOps.names());
        editingTier(copyName);
        if (report(TierOps.duplicate(name, copyName))) {
            ScreenNavigator.get().navigateTo(new TierEditScreen(copyName));
        }
    }

    private static final String RECENT_GROUP = "grp:recent";
    private static final String ALL_GROUP = "grp:all";

    /**
     * The tiers, with the ones edited this session first in a Recent group, newest first, as the mob list
     * does.
     */
    private void rebuild() {
        java.util.Map<String, TierOps.TierSummary> byName = new java.util.LinkedHashMap<>();
        for (TierOps.TierSummary tier : TierOps.summaries()) {
            byName.put(tier.name(), tier);
        }
        List<String> recent = new ArrayList<>();
        for (String name : com.strata.client.util.RecentEdits.recentTiers()) {
            if (byName.containsKey(name)) {
                recent.add(name);
            }
        }
        List<BrowseList.Entry> entries = new ArrayList<>();
        if (!recent.isEmpty()) {
            entries.add(new BrowseList.Entry(RECENT_GROUP, "Recent (" + recent.size() + ")", "",
                    Palette.GROUP, RECENT_GROUP, null, 0));
            for (String name : recent) {
                entries.add(row(byName.get(name), RECENT_GROUP));
            }
            entries.add(new BrowseList.Entry(ALL_GROUP, "Tiers (" + byName.size() + ")",
                    "", Palette.GROUP, ALL_GROUP, null, 0));
        }
        for (TierOps.TierSummary tier : byName.values()) {
            entries.add(row(tier, recent.isEmpty() ? null : ALL_GROUP));
        }
        list.refresh(entries);
    }

    private static BrowseList.Entry row(TierOps.TierSummary tier, String group) {
        String sub = "level " + tier.level()
                + " · " + tier.spawnMode()
                + " ×" + tier.coefficient()
                + " · " + com.strata.ui.Words.count(tier.biomes(), "biome", "biomes");
        return new BrowseList.Entry(tier.name(), tier.name(), sub, Palette.TEXT,
                null, group, group == null ? 0 : 1);
    }

    /** The footer: how to open a tier. */
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = com.strata.ui.screen.ListScreenLayouts.tierList(frame(), this.font::width,
                title(), true);
        if (layout.get("footer") != null) {
            titles(graphics, layout);
            text(graphics, layout.get("footer"), Palette.MUTED);
        }
        emptyNote(graphics, layout, list);
    }
}