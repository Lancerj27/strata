package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.domain.BlockStatsOps;
import com.strata.domain.BlockVariantOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client > Blocks: every block, by kind, with the designer's block variants under their base block. */
public class BlockStatsScreen extends StrataScreen {

    private static final String GROUP = "grp:";
    /** A variant row's id: "var:" and the variant's index. */
    private static final String VARIANT = "var:";
    /** A block's "+ Add variant" row: "addvar:" and the block's index. */
    private static final String ADD_VARIANT = "addvar:";

    private BrowseList list;
    private EditField search;
    private List<BlockStatsOps.Block> blocks = List.of();
    private List<BlockVariantOps.Variant> variants = List.of();
    private String status = "";
    private Button removeButton;

    public BlockStatsScreen() {
        super("Strata — Blocks");
    }

    @Override
    protected void init() {
        super.init();
        blocks = BlockStatsOps.blocks();
        variants = BlockVariantOps.variants();
        // THE AT-A-GLANCE COLUMNS: the user's choice, and every block's values.
        glance = new GlanceHeaders(this, this.font, com.strata.domain.GlanceColumns.BLOCKS, this::rebuildWidgets);
        glance.load(com.strata.ui.screen.GlanceLayout.listRoom(frame()));
        glanceValues = BlockStatsOps.glance();
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old list.
        list = null;

        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> rebuild());
        addRenderableWidget(place(search.getWidget(), layout.get("search")));
        placeAll(layout, new String[]{"tab0", "tab1", "tab2", "tab3"}, BlockSwapScreen.blockTabs("global", ""));
        // Block tags, block data and the block-side tags beside the blocks.
        placeDataMenu(layout, "data", com.strata.ui.screen.ListScreenLayouts.DATA_MENU,
                dataEntry("Block tags", com.strata.data.JsonOverrides.BLOCK_TAG),
                dataEntry("Block data (waxing, oxidizing)", com.strata.data.JsonOverrides.BLOCK_DATA),
                dataEntry("Fluid tags", com.strata.data.JsonOverrides.FLUID_TAG),
                dataEntry("Game event tags (vibrations)", com.strata.data.JsonOverrides.GAME_EVENT_TAG),
                dataEntry("Workstation and point tags", com.strata.data.JsonOverrides.POI_TAG));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("BlockStatsScreen");
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, blocks, BlockStatsOps.Block::id));
        placeList(list, layout.get("list"));
        glance.layOut(list, layout, com.strata.ui.screen.GlanceLayout.columnWidth(
                com.strata.ui.screen.GlanceLayout.listRoom(frame()), this.font::width, glance.headings()));
        addRenderableWidget(list);
        Button edit = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        removeButton = Button.builder(Component.literal("Remove"), b -> removePicked()).build();
        // ON / OFF: the picked blocks.
        Button toggle = Button.builder(Component.literal("On / Off"), b -> togglePicked()).build();
        placeAll(layout, new String[]{"edit", "toggle", "remove"}, edit, toggle, removeButton);
        needsSelection(() -> !pickedBlocks().isEmpty(), toggle);
        needsSelection(() -> list.getSelected() != null && !list.getSelected().id.startsWith(GROUP)
                && !list.getSelected().id.startsWith(ADD_VARIANT) && !list.manyPicked(), edit);
        // "+ Add variant" under a block opens a new variant of it.
        list.setOnSelect(entry -> {
            if (entry.id.startsWith(ADD_VARIANT)) {
                String base = blocks.get(Integer.parseInt(entry.id.substring(ADD_VARIANT.length()))).id();
                ScreenNavigator.get().navigateTo(new BlockVariantScreen(null, base));
            }
        });
        needsSelection(() -> !pickedVariants().isEmpty(), removeButton);
        // A double-click presses Edit.
        list.setOnActivate(entry -> editSelected());

        rebuild();
        list.restore(keepList);
    }

    private com.strata.ui.Layout layout() {
        int n = pickedVariants().size();
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return com.strata.ui.screen.ListScreenLayouts.blockList(frame(), this.font::width, title(),
                sel != null && !sel.id.startsWith(GROUP) && !sel.id.startsWith(ADD_VARIANT),
                n > 1 ? "Remove (" + n + ")" : "Remove", n > 0, status,
                blocks.isEmpty() ? "No blocks: their stats could not be read at game start (see the log)" : null,
                glance.headings());
    }

    /** The at-a-glance columns: which show, the Columns... */
    private GlanceHeaders glance;
    /** Every block's effective values by key, read as the screen opens. */
    private Map<String, Map<String, String>> glanceValues = Map.of();

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !choicesOpen() && glance != null && list != null && glance.click(list, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private BlockStatsOps.Block selectedBlock() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || !sel.id.matches("\\d+") ? null : blocks.get(Integer.parseInt(sel.id));
    }

    private BlockVariantOps.Variant selectedVariant() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || !sel.id.startsWith(VARIANT) ? null
                : variants.get(Integer.parseInt(sel.id.substring(VARIANT.length())));
    }

    /** The block rows picked (not their variants): what On / Off disables. */
    private List<String> pickedBlocks() {
        return list == null ? List.of() : list.pickedIds().stream().filter(id -> id.matches("\\d+"))
                .map(id -> blocks.get(Integer.parseInt(id)).id()).toList();
    }

    /** The variant rows picked with Ctrl and Shift, or the selected one; block rows are left out. */
    private List<BlockVariantOps.Variant> pickedVariants() {
        return list == null ? List.of() : list.pickedIds().stream().filter(id -> id.startsWith(VARIANT))
                .map(id -> variants.get(Integer.parseInt(id.substring(VARIANT.length())))).toList();
    }

    private void editSelected() {
        BlockVariantOps.Variant v = selectedVariant();
        if (v != null) {
            ScreenNavigator.get().navigateTo(new BlockVariantScreen(v.name()));
            return;
        }
        BlockStatsOps.Block block = selectedBlock();
        if (block != null) {
            ScreenNavigator.get().navigateTo(new BlockStatScreen(block.id(), Labels.blockName(block.id())));
        }
    }

    /** Removing a variant always asks, naming them: it is a record, with its values and abilities. */
    private void removePicked() {
        List<String> names = pickedVariants().stream().map(BlockVariantOps.Variant::name).toList();
        if (names.isEmpty()) {
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, "Remove " + com.strata.domain.Picks.listed(names) + "?\n"
                + "Their values and abilities go too. At the next game start their blocks and items\n"
                + "leave the game: placed ones become air.", () -> act(BlockVariantOps.remove(names))));
    }

    private boolean act(WriteReport result) {
        if (report(result)) {
            variants = BlockVariantOps.variants();
            rebuild();
            return true;
        }
        return false;
    }

    /** The On / Off press. */
    private void togglePicked() {
        List<String> picked = pickedBlocks();
        if (act(com.strata.domain.DisabledOps.toggle(com.strata.domain.DisabledOps.BLOCK, picked))
                && !picked.isEmpty()) {
            nameNoteChange(com.strata.domain.Picks.listed(picked.stream().map(Labels::blockName).toList()));
        }
    }

    /** The disabled blocks, read as the list rebuilds. */
    private java.util.Set<String> disabled = java.util.Set.of();

    private void rebuild() {
        disabled = com.strata.domain.DisabledOps.ids(com.strata.domain.DisabledOps.BLOCK);
        String text = search == null ? "" : search.getValue().strip().toLowerCase();
        Map<String, List<Integer>> variantsByBase = new HashMap<>();
        for (int i = 0; i < variants.size(); i++) {
            variantsByBase.computeIfAbsent(variants.get(i).baseBlock(), k -> new ArrayList<>()).add(i);
        }
        List<BrowseList.Entry> entries = new ArrayList<>();
        int shown = 0;
        String group = null;
        List<BrowseList.Entry> rows = new ArrayList<>();
        for (int i = 0; i <= blocks.size(); i++) {
            BlockStatsOps.Block block = i < blocks.size() ? blocks.get(i) : null;
            if (block == null || !block.kind().equals(group)) {
                if (group != null && !rows.isEmpty()) {
                    // "Ores (19)", as an item kind reads.
                    String id = GROUP + group;
                    long count = rows.stream().filter(r -> r.id.matches("\\d+")).count();
                    entries.add(new BrowseList.Entry(id, group + " (" + count + ")", "", Palette.GROUP, id, null, 0));
                    entries.addAll(rows);
                }
                if (block == null) {
                    break;
                }
                group = block.kind();
                rows = new ArrayList<>();
            }
            String name = Labels.blockName(block.id());
            List<Integer> own = variantsByBase.getOrDefault(block.id(), List.of());
            boolean variantHit = own.stream().anyMatch(vi -> variants.get(vi).name().contains(text.replace(' ', '_')));
            if (!text.isEmpty() && !name.toLowerCase().contains(text) && !block.id().contains(text.replace(' ', '_'))
                    && !variantHit) {
                continue;
            }
            boolean edited = block.edited() > 0;
            boolean off = disabled.contains(block.id());
            rows.add(new BrowseList.Entry(String.valueOf(i), name + (off ? "  [disabled]" : ""),
                    block.id() + ", " + com.strata.ui.Words.count(block.values(), "value", "values")
                    + (edited ? "  (" + block.edited() + " yours)" : "")
                    + (own.isEmpty() ? "" : "  " + com.strata.ui.Words.count(own.size(), "variant", "variants")),
                    off ? Palette.MUTED : edited ? Palette.EDITED : Palette.TEXT, null,
                    GROUP + group, 1)
                    .columns(com.strata.domain.GlanceColumns.valuesFor(glance.shownKeys(), glanceValues.get(block.id()))));
            // THE VARIANTS UNDER THEIR BASE BLOCK, as a mob's under its mob.
            for (int vi : own) {
                BlockVariantOps.Variant v = variants.get(vi);
                rows.add(new BrowseList.Entry(VARIANT + vi, v.name(), "strata:" + v.name() + ", "
                        + com.strata.ui.Words.count(v.values(), "value", "values") + (v.registered() ? "" : "  (added at the next game start)"),
                        v.registered() ? Palette.EDITED : Palette.LABEL, null, GROUP + group, 2));
            }
            rows.add(new BrowseList.Entry(ADD_VARIANT + i, "+ Add variant", "", Palette.MUTED, null, GROUP + group, 2));
            shown++;
        }
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
        status = com.strata.ui.Words.count(shown, "block", "blocks") + ", " + com.strata.ui.Words.count(variants.size(), "variant", "variants");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        removeButton.setMessage(Component.literal(layout.textOf("remove")));
        super.render(graphics, mouseX, mouseY, partialTick);
        glance.render(graphics, list, layout, mouseX, mouseY);
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}

