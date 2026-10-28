package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.domain.BlockReplacementOps;
import com.strata.domain.WriteReport;
import com.strata.game.RegistryQuery;
import com.strata.model.BlockSwap;
import com.strata.ui.Palette;
import com.strata.util.Labels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Blocks > Replacements: BLOCK REPLACEMENT IN WORLD GENERATION. */
public class BlockSwapScreen extends StrataScreen {

    private final String scope;
    private final String scopeId;
    private List<BlockReplacementOps.Row> rows = List.of();
    private BrowseList list;
    /** The add row's blocks, kept across the picker's return. */
    private String from = "";
    private String to = "";

    public BlockSwapScreen(String scope, String scopeId) {
        super("Strata — Replacements: " + Labels.scope(scope, scopeId));
        this.scope = scope == null || scope.isEmpty() ? BlockReplacementOps.GLOBAL : scope;
        this.scopeId = BlockReplacementOps.GLOBAL.equals(this.scope) || scopeId == null ? "" : scopeId;
    }

    /**
     * The Blocks tabs: Blocks, then Ores, Vegetation, Block replacements of one place, each swapping in the other
     * list. Blocks has no scope: from a tier's or biome's list it opens the one Blocks list, Back returning.
     */
    static Button[] blockTabs(String scope, String scopeId) {
        // The global lists are Blocks entries: through SectionNav they keep the Section dropdown.
        boolean global = "global".equals(scope);
        return new Button[]{
                Button.builder(Component.literal("Blocks"), b -> {
                    StrataScreen blocks = SectionNav.open(com.strata.ui.screen.Sections.BLOCKS, "Blocks");
                    if (global) {
                        com.strata.client.nav.ScreenNavigator.get().swapTo(blocks);
                    } else {
                        com.strata.client.nav.ScreenNavigator.get().navigateTo(blocks);
                    }
                }).build(),
                Button.builder(Component.literal("Ores"), b -> com.strata.client.nav.ScreenNavigator.get().swapTo(
                        global ? SectionNav.open(com.strata.ui.screen.Sections.BLOCKS, "Ores")
                                : new OreListScreen(scope, scopeId))).build(),
                Button.builder(Component.literal("Vegetation"), b -> com.strata.client.nav.ScreenNavigator.get().swapTo(
                        global ? SectionNav.open(com.strata.ui.screen.Sections.BLOCKS, "Vegetation")
                                : new VegetationScreen(scope, scopeId))).build(),
                Button.builder(Component.literal("Block replacements"), b -> com.strata.client.nav.ScreenNavigator.get()
                        .swapTo(global ? SectionNav.open(com.strata.ui.screen.Sections.BLOCKS, "Replacements")
                                : new BlockSwapScreen(scope, scopeId))).build()};
    }

    private static String blockText(String id) {
        if (id.isEmpty()) {
            return "(pick a block)";
        }
        return BlockSwap.DISABLED.equals(id) ? "Disabled (air)" : Labels.blockName(id);
    }

    private BlockReplacementOps.Row selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : rows.get(Integer.parseInt(sel.id));
    }

    private com.strata.ui.Layout layout() {
        return com.strata.ui.screen.ListScreenLayouts.blockSwapList(frame(), this.font::width, title(), blockText(from), blockText(to), selected() != null,
                com.strata.ui.Words.count(rows.size(), "replacement", "replacements") + " here.",
                "No replacements here: pick From and To below, then + Add.");
    }

    @Override
    protected void init() {
        super.init();
        rows = BlockReplacementOps.rows(scope, scopeId);
        BrowseList.State keep = list == null ? null : list.state();
        list = null;
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);
        placeAll(layout, new String[]{"tab0", "tab1", "tab2", "tab3"}, blockTabs(scope, scopeId));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("BlockSwapScreen");
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, rows, BlockReplacementOps.Row::from));
        placeList(list, layout.get("list"));
        addRenderableWidget(list);

        placeAll(layout, new String[]{"from", "to", "add"},
                Button.builder(Component.literal(blockText(from)), b -> ScreenNavigator.get().navigateTo(
                        new RegistryPickerScreen("Replace which block?", RegistryQuery.blocks(), id -> from = id))).build(),
                Button.builder(Component.literal(blockText(to)), b -> {
                    List<RegistryQuery.Option> choices = new ArrayList<>();
                    choices.add(new RegistryQuery.Option(BlockSwap.DISABLED, "Disabled (air)", "Remove it"));
                    choices.addAll(RegistryQuery.blocks());
                    ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Put which block there?", choices,
                            id -> to = id));
                }).build(),
                Button.builder(Component.literal("+ Add"), b -> {
                    if (report(BlockReplacementOps.save(scope, scopeId, from, to))) {
                        from = "";
                        to = "";
                        rebuildWidgets();
                    }
                }).build());
        // Remove takes every picked rule (Ctrl or Shift for several), after a question naming them.
        Button remove = Button.builder(Component.literal("Remove"), b -> {
            List<BlockReplacementOps.Row> picked = list.pickedIds().stream()
                    .map(id -> rows.get(Integer.parseInt(id))).toList();
            if (picked.isEmpty()) {
                return;
            }
            minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, "Remove "
                    + com.strata.domain.Picks.listed(picked.stream().map(BlockReplacementOps.Row::sentence).toList())
                    + "?\nThose blocks generate as the game has them again, in new chunks.", () -> {
                WriteReport report = new WriteReport();
                for (BlockReplacementOps.Row row : picked) {
                    report = report.ok() ? BlockReplacementOps.remove(row) : report;
                }
                act(report);
            }));
        }).build();
        placeAll(layout, new String[]{"remove"}, remove);
        needsSelection(() -> !list.pickedIds().isEmpty(), remove);

        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            BlockReplacementOps.Row r = rows.get(i);
            entries.add(new BrowseList.Entry(String.valueOf(i), Labels.blockName(r.from()) + "  ->  " + blockText(r.to()),
                    r.from() + "  ->  " + r.to(), BlockSwap.DISABLED.equals(r.to()) ? Palette.MUTED : Palette.TEXT));
        }
        list.refresh(entries);
        list.restore(keep);
    }

    private void act(WriteReport result) {
        if (report(result)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}
