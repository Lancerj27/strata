package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.VegetationOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A tier's or a biome's flora: every plant feature of the game (trees, flowers, grass), by dimension, with
 * its rule.
 */
public class VegetationScreen extends StrataScreen {

    private final String scope;
    private final String scopeId;

    private BrowseList list;
    private EditField search;
    private String status = "";
    private List<VegetationOps.VegetationRow> allRows = List.of();
    /** The list's sections: dimension, then biome. */
    private List<VegetationOps.Section> sections = List.of();

    public VegetationScreen(String scope, String scopeId) {
        super("Strata — Vegetation: " + Labels.scope(scope, scopeId));
        this.scope = scope == null ? "" : scope;
        this.scopeId = scopeId == null ? "" : scopeId;
        if ("tier".equals(this.scope)) {
            editingTier(this.scopeId);
        } else if ("biome".equals(this.scope)) {
            editingBiome(this.scopeId);
        }
    }

    @Override
    protected void init() {
        super.init();
        allRows = loadRows();
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table.
        list = null;

        com.strata.ui.Layout layout = layout(false);
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        // The Blocks tabs, as on Mobs.
        placeAll(layout, new String[]{"tab0", "tab1", "tab2", "tab3"}, BlockSwapScreen.blockTabs(scope, scopeId));

        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> rebuild());
        addRenderableWidget(place(search.getWidget(), layout.get("search")));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("VegetationScreen");
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, allRows, VegetationOps.VegetationRow::feature));
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        Button enable = Button.builder(Component.literal("On"), b -> setPicked(VegetationOps.VegetationState.ENABLE)).build();
        Button disable = Button.builder(Component.literal("Off"), b -> setPicked(VegetationOps.VegetationState.DISABLE)).build();
        Button toDefault = Button.builder(Component.literal("Restore"), b -> setPicked(VegetationOps.VegetationState.DEFAULT)).build();
        placeAll(layout, new String[]{"enable", "disable", "default"}, enable, disable, toDefault);
        needsSelection(() -> !list.pickedIds().isEmpty(), enable, disable, toDefault);

        rebuild();
        list.restore(keepList);
    }

    /** @param picked the list has a picked row */
    private com.strata.ui.Layout layout(boolean picked) {
        return com.strata.ui.screen.ListScreenLayouts.vegetationList(frame(), this.font::width, title(), picked, status,
                allRows.isEmpty() ? "No plants: the game data could not be read at game start (see the log)" : null);
    }


    private List<VegetationOps.VegetationRow> loadRows() {
        List<VegetationOps.VegetationRow> rows = VegetationOps.rowsFor(scope, scopeId);
        sections = VegetationOps.sectionsFor(scope, scopeId,
                rows.stream().map(VegetationOps.VegetationRow::feature).toList());
        return rows;
    }

    /** Sets the picked plants to a state. */
    private void setPicked(VegetationOps.VegetationState state) {
        List<VegetationOps.VegetationRow> picked = list.pickedIds().stream().map(id -> id.substring(0, id.indexOf('@')))
                .distinct().map(i -> allRows.get(Integer.parseInt(i))).toList();
        act(VegetationOps.setState(scope, scopeId, picked, state));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            allRows = loadRows();
            rebuild();
        }
    }

    private static int color(VegetationOps.VegetationRow row) {
        if (!row.hasRule()) return Boolean.FALSE.equals(row.inGame()) ? Palette.MUTED : Palette.INHERITED;
        if (row.inherited()) return Palette.INHERITED;
        return row.enabled() ? Palette.TEXT : Palette.MUTED;
    }

    private void rebuild() {
        String text = search == null ? "" : search.getValue().strip().toLowerCase();
        List<BrowseList.Entry> entries = new ArrayList<>();
        java.util.Set<Integer> shown = new java.util.HashSet<>();
        Map<String, Integer> index = new java.util.HashMap<>();
        for (int i = 0; i < allRows.size(); i++) {
            index.put(allRows.get(i).feature(), i);
        }
        // BY DIMENSION, THEN BIOME: a dimension group, a biome group in it, its plants.
        String dimension = null;
        List<BrowseList.Entry> dimRows = new ArrayList<>();
        int dimCount = 0;
        for (int s = 0; s <= sections.size(); s++) {
            VegetationOps.Section section = s < sections.size() ? sections.get(s) : null;
            if (section == null || !section.dimension().equals(dimension)) {
                if (dimension != null && !dimRows.isEmpty()) {
                    String group = "grp:" + dimension;
                    entries.add(new BrowseList.Entry(group, dimension + " (" + dimCount + ")", "", Palette.GROUP, group, null, 0));
                    entries.addAll(dimRows);
                }
                if (section == null) {
                    break;
                }
                dimension = section.dimension();
                dimRows = new ArrayList<>();
                dimCount = 0;
            }
            String dimGroup = "grp:" + dimension;
            String biomeGroup = dimGroup + "|" + section.biome();
            List<BrowseList.Entry> rows = new ArrayList<>();
            for (String feature : section.features()) {
                Integer i = index.get(feature);
                if (i == null) continue;
                VegetationOps.VegetationRow row = allRows.get(i);
                String name = Labels.pretty(row.feature());
                if (!text.isEmpty() && !row.feature().toLowerCase().contains(text.replace(' ', '_'))
                        && !name.toLowerCase().contains(text)) continue;
                rows.add(new BrowseList.Entry(i + "@" + section.biome(), name, VegetationOps.subtitle(row), color(row),
                        null, section.biome().isEmpty() ? dimGroup : biomeGroup, section.biome().isEmpty() ? 1 : 2));
                shown.add(i);
            }
            if (rows.isEmpty()) continue;
            if (!section.biome().isEmpty()) {
                dimRows.add(new BrowseList.Entry(biomeGroup, Labels.biome(section.biome()) + " (" + rows.size() + ")", "",
                        Palette.GROUP, biomeGroup, dimGroup, 1));
            }
            dimRows.addAll(rows);
            dimCount += rows.size();
        }
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
        status = com.strata.ui.Words.count(shown.size(), "plant", "plants");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout(!list.pickedIds().isEmpty());
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}

