package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.OreOps;
import com.strata.domain.WriteReport;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Scope-aware ore restriction browser. */
public class OreListScreen extends StrataScreen {

    private final String scope;
    private final String scopeId;

    private BrowseList list;
    private EditField search;
    private String status = "";
    private List<OreOps.OreRow> allRows = List.of();
    /** BY DIMENSION: each ore's dimensions, and the headings this scope shows, read with the rows. */
    private java.util.Map<String, List<String>> dimensions = java.util.Map.of();
    private List<String> headings = List.of();
    /** The biome's dimension, or null in a tier. */
    private String biomeDimension;

    public OreListScreen(String scope, String scopeId) {
        super("Strata — Ores: " + Labels.scope(scope, scopeId));
        this.scope = scope == null ? "" : scope;
        this.scopeId = scopeId == null ? "" : scopeId;
        // An ore rule is work on that tier or biome, for its Recent group.
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
        // init runs again on a return here and on a resize.
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout(false);
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        // The Blocks tabs, as on Mobs.
        placeAll(layout, new String[]{"tab0", "tab1", "tab2", "tab3"}, BlockSwapScreen.blockTabs(scope, scopeId));

        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> rebuild());
        addRenderableWidget(place(search.getWidget(), layout.get("search")));


        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("OreListScreen");
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, allRows, OreOps.OreRow::oreId));
        placeList(list, layout.get("list"));
        // A CLICK SELECTS: it used to change the ore's rule and save, with no word and no undo.
        addRenderableWidget(list);
        Button enable = Button.builder(Component.literal("On"), b -> setPicked(OreOps.OreState.ENABLE)).build();
        Button disable = Button.builder(Component.literal("Off"), b -> setPicked(OreOps.OreState.DISABLE)).build();
        Button toDefault = Button.builder(Component.literal("Restore"), b -> setPicked(OreOps.OreState.DEFAULT)).build();
        placeAll(layout, new String[]{"enable", "disable", "default"}, enable, disable, toDefault);
        needsSelection(() -> !list.pickedIds().isEmpty(), enable, disable, toDefault);

        rebuild();
        list.restore(keepList);
    }

    /** @param picked the list has a picked row (init passes false: its list is new) */
    private com.strata.ui.Layout layout(boolean picked) {
        return com.strata.ui.screen.ListScreenLayouts.oreList(frame(), this.font::width, title(), picked, status,
                allRows.isEmpty() ? "No ores: the game data could not be read at game start (see the log)" : null);
    }


    /** Sets the picked ores to a state. */
    private void setPicked(OreOps.OreState state) {
        // A row's id is its place and its dimension ("3@Overworld"): an ore in two dimensions is listed
        // twice, and picked once.
        List<OreOps.OreRow> picked = list.pickedIds().stream().map(id -> id.substring(0, id.indexOf('@')))
                .distinct().map(i -> allRows.get(Integer.parseInt(i))).toList();
        act(OreOps.setState(scope, scopeId, picked, state));
    }
    private List<OreOps.OreRow> loadRows() {
        // A biome view knows what the game places there.
        java.util.List<String> gameOres = "biome".equals(scope)
                ? com.strata.game.GameData.gameOresIn(this.minecraft != null && this.minecraft.level != null
                        ? worldRegistries() : null, scopeId)
                : null;
        List<OreOps.OreRow> rows = OreOps.rowsFor(scope, scopeId,
                RegistryQuery.ores().stream().map(RegistryQuery.Option::id).toList(), gameOres);
        dimensions = OreOps.dimensionsOf(rows.stream().map(OreOps.OreRow::oreId).toList());
        biomeDimension = "biome".equals(scope) ? OreOps.dimensionOfBiome(scopeId) : null;
        headings = OreOps.headings(dimensions, biomeDimension);
        return rows;
    }

    /** Shows what an ore action reported, and redraws the rows after it. */
    private void act(WriteReport result) {
        if (report(result)) {
            allRows = loadRows();
            rebuild();
        }
    }

    private static String subtitle(OreOps.OreRow row) {
        if (row.inherited()) {
            return (row.enabled() ? "Enabled" : "Disabled") + "  Chance: " + row.frequencyPct()
                    + "%  (from " + row.inheritedFrom() + ")";
        }
        if (!row.hasRule()) return noRuleText(row.inGame());
        return (row.enabled() ? "Enabled" : "Disabled") + "  Chance: " + row.frequencyPct() + "%";
    }

    /** What a row with no rule does: what the game does. */
    private static String noRuleText(Boolean inGame) {
        if (inGame == null) return "No rule — each biome keeps the game's own";
        return inGame ? "No rule — the game generates it here"
                : "No rule — the game does not generate it here";
    }

    /** The blocks the row controls, in words: " · Redstone Ore, Deepslate Redstone Ore". */
    private static String placesText(List<String> blocks) {
        return blocks.isEmpty() ? "" : "  ·  " + String.join(", ", blocks.stream().map(Labels::itemName).toList());
    }

    private static int color(OreOps.OreRow row) {
        if (!row.hasRule()) return Boolean.FALSE.equals(row.inGame()) ? Palette.MUTED : Palette.INHERITED;
        if (row.inherited()) return Palette.INHERITED;
        if (!row.enabled()) return Palette.MUTED;
        return Palette.TEXT;
    }

    private void rebuild() {
        String text = search == null ? "" : search.getValue().strip().toLowerCase();
        List<BrowseList.Entry> entries = new ArrayList<>();
        java.util.Set<Integer> shown = new java.util.HashSet<>();
        // ONE GROUP PER DIMENSION: an ore under each dimension it generates in.
        for (String heading : headings) {
            String group = "grp:" + heading;
            List<BrowseList.Entry> rows = new ArrayList<>();
            for (int i = 0; i < allRows.size(); i++) {
                OreOps.OreRow row = allRows.get(i);
                boolean here = dimensions.getOrDefault(row.oreId(), List.of()).contains(heading)
                        || (biomeDimension != null && row.hasRule());
                if (!here) continue;
                // The search matches the ore and the blocks it places: "deepslate redstone"
                // finds the redstone row.
                List<String> blocks = OreOps.blocksOf(row.oreId());
                if (!text.isEmpty() && !row.oreId().toLowerCase().contains(text)
                        && blocks.stream().noneMatch(b -> b.toLowerCase().contains(text.replace(' ', '_'))
                                || Labels.itemName(b).toLowerCase().contains(text))) continue;
                // An inherited row is read-only here — it belongs to its tier — so it shows the chance with
                // no commit handler.
                rows.add(new BrowseList.Entry(i + "@" + heading,
                        Labels.pretty(row.oreId()), subtitle(row) + placesText(blocks), color(row),
                        null, group, 1, row.chanceCell(),
                        row.inherited() ? null : pct -> act(OreOps.setChance(scope, scopeId, row, pct))));
                shown.add(i);
            }
            if (!rows.isEmpty()) {
                entries.add(new BrowseList.Entry(group, heading + " (" + rows.size() + ")", "", Palette.GROUP, group, null, 0));
                entries.addAll(rows);
            }
        }
        // A search's matches show unfolded.
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
        status = com.strata.ui.Words.count(shown.size(), "ore", "ores");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout(!list.pickedIds().isEmpty());
        titles(graphics, layout);
        text(graphics, layout.get("heading0"), Palette.LABEL);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}

