package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ValueInput;
import com.strata.domain.BlockStatsOps;
import com.strata.domain.WriteReport;
import com.strata.model.BlockStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One block's stats: each value with what it does and the game's number; Set value writes the user's, Restore
 * takes it away.
 */
public class BlockStatScreen extends StrataScreen {

    private final String block;
    /** The sheet of a block variant (block is then its name), not of a game block. */
    private final boolean variant;
    private List<BlockStatsOps.Value> values = List.of();
    private BrowseList list;
    private ValueInput valueInput;

    public BlockStatScreen(String block, String name) {
        this(block, "Strata — Block: " + name, false);
    }

    private BlockStatScreen(String block, String title, boolean variant) {
        super(title);
        this.block = block;
        this.variant = variant;
    }

    /** A block variant's stats. */
    public static BlockStatScreen forVariant(String name) {
        return new BlockStatScreen(name, "Strata — Block variant stats: " + name, true);
    }

    private List<BlockStatsOps.Value> load() {
        List<BlockStatsOps.Value> all = variant ? com.strata.domain.BlockVariantOps.values(block) : BlockStatsOps.values(block);
        // The values Strata shows and does not edit (sound, map color, tags...) are listed only with Options
        // > Show locked values on.
        if (com.strata.domain.OptionsOps.viewLocked()) {
            return all;
        }
        return all.stream().filter(v -> com.strata.model.BlockStats.KEYS.contains(v.key())).toList();
    }

    @Override
    protected void init() {
        super.init();
        values = load();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("BlockStatScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            var row = values.get(Integer.parseInt(entry.id));
            valueInput.show(BlockStats.typeOf(row.key()), row.effective());
        });
        addRenderableWidget(list);

        // THE VALUE CONTROL is the row's type: a switch or a short list is a dropdown, a number is a field.
        valueInput = valueInput(layout, "value", this::setValue);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> setValue())
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(variant ? com.strata.domain.BlockVariantOps.restore(v) : BlockStatsOps.restore(v));
                })
                .build();
        placeAll(layout, new String[]{"set", "restore"}, set, restore);
        needsSelection(() -> selected() != null && !BlockStatsOps.shownOnly(selected()), set);
        needsSelection(() -> selected() != null, restore);
        // DROPS: the block's loot table in the loot editor.
        if (!variant) {
            String table = BlockStatsOps.lootTable(block);
            Button drops = Button.builder(Component.literal("Loot"),
                    b -> ScreenNavigator.get().navigateTo(ChestLootScreen.forBlock(table, com.strata.ui.TitlePath.last(title()))))
                    .build();
            placeAll(layout, new String[]{"drops"}, drops);
            drops.active = !table.isEmpty();
        }
        rebuild();
        list.restore(keepList);
    }

    /** Set value: the control's value to the selected row, through the same check as before. */
    private void setValue() {
        var v = selected();
        if (v != null) {
            act(variant ? com.strata.domain.BlockVariantOps.save(v, valueInput.value())
                    : BlockStatsOps.save(v, valueInput.value()));
        }
    }

    private BlockStatsOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : values.get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = load();
            rebuild();
        }
    }

    private void rebuild() {
        // Each value's row says what it does.
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            BlockStatsOps.Value v = values.get(i);
            String sub = BlockStatsOps.what(v) + (v.own().isEmpty() ? "" : "  (yours; " + source() + ": " + v.game() + ")");
            // A value shown, not edited, is grey.
            entries.add(new BrowseList.Entry(String.valueOf(i), BlockStatsOps.name(v) + ": " + v.effective(), sub,
                    !v.own().isEmpty() ? Palette.EDITED : BlockStatsOps.shownOnly(v) ? Palette.LABEL : Palette.TEXT, null, null, 0));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        BlockStatsOps.Value v = list == null ? null : selected();
        String applies = variant ? com.strata.domain.BlockVariantOps.APPLIES : BlockStatsOps.APPLIES;
        String help = v == null ? "Select a value to change it. Yellow values are yours. " + applies
                : BlockStatsOps.name(v) + ": " + BlockStatsOps.what(v) + " " + capital(source()) + ": " + v.game()
                        + ". " + applies;
        String empty = values.isEmpty() ? variant
                ? "No values: the base block's stats could not be read at game start (see the log)."
                : "No values: this block's stats could not be read at game start (see the log)."
                : null;
        return com.strata.ui.screen.ListScreenLayouts.blockSheet(frame(), this.font::width, title(), help, !variant,
                true, empty);
    }

    /** Whose number sits under the user's: the game's, or a variant's base block's. */
    private String source() {
        return variant ? "the base block's" : "the game's";
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        emptyNote(graphics, layout, list);
    }
}
