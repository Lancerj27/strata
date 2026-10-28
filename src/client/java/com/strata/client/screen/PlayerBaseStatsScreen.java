package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.PlayerStatsOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The player's base stats. */
public class PlayerBaseStatsScreen extends StrataScreen {

    private List<PlayerStatsOps.Value> values = List.of();
    private BrowseList list;
    private EditField valueField;

    public PlayerBaseStatsScreen() {
        super("Strata — Player scaling: Player base stats");
    }

    @Override
    protected void init() {
        super.init();
        values = PlayerStatsOps.values();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueField == null ? "" : valueField.getValue();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("PlayerBaseStatsScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> valueField.setValue(values.get(Integer.parseInt(entry.id)).effective()));
        addRenderableWidget(list);

        valueField = field(0, 0, 0, 0, "Value", keepValue, null, null);
        place(valueField.getWidget(), layout.get("value"));
        Button set = Button.builder(Component.literal("Set value"), b -> {
                    var v = selected();
                    if (v != null) act(PlayerStatsOps.save(v, valueField.getValue()));
                })
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(PlayerStatsOps.restore(v));
                })
                .build();
        placeAll(layout, new String[]{"set", "restore"}, set, restore);
        needsSelection(() -> selected() != null, set, restore);
        rebuild();
        list.restore(keepList);
    }

    private PlayerStatsOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : values.get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = PlayerStatsOps.values();
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            PlayerStatsOps.Value v = values.get(i);
            String sub = PlayerStatsOps.what(v) + (v.own().isEmpty() ? "" : "  (yours; the game's: " + v.game() + ")");
            entries.add(new BrowseList.Entry(String.valueOf(i), PlayerStatsOps.name(v) + ": " + v.effective(), sub,
                    v.own().isEmpty() ? Palette.TEXT : Palette.EDITED, null, null, 0));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        PlayerStatsOps.Value v = list == null ? null : selected();
        String help = v == null ? "The player's base values. Select one to change it. Yellow values are yours. "
                + PlayerStatsOps.APPLIES
                : PlayerStatsOps.name(v) + ": " + PlayerStatsOps.what(v) + " The game's: " + v.game() + ". "
                        + PlayerStatsOps.APPLIES;
        String empty = values.isEmpty() ? "No values: the player's attributes could not be read at game start (see the log)."
                : null;
        return com.strata.ui.screen.ListScreenLayouts.valueSheet(frame(), this.font::width, title(), 100, help,
                null, empty);
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
