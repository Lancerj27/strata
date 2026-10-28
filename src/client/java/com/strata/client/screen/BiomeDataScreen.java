package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.BiomeDataOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A biome's own values, editable: its creature probability, and the charge and energy budget of each spawn
 * cost.
 */
public class BiomeDataScreen extends StrataScreen {

    private final String biome;
    private List<BiomeDataOps.Value> values = List.of();
    private BrowseList list;
    private EditField valueField;

    public BiomeDataScreen(String biome) {
        super("Strata — Biome data: " + com.strata.util.Labels.biome(biome));
        this.biome = biome;
        editingBiome(biome);
    }

    @Override
    protected void init() {
        super.init();
        values = BiomeDataOps.values(biome);
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueField == null ? "" : valueField.getValue();
        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("BiomeDataScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> valueField.setValue(values.get(Integer.parseInt(entry.id)).effective()));
        addRenderableWidget(list);

        valueField = field(0, 0, 0, 0, "Value", keepValue, null, null);
        place(valueField.getWidget(), layout.get("value"));
        Button set = Button.builder(Component.literal("Set value"), b -> {
                    var v = selected();
                    if (v != null) act(BiomeDataOps.save(biome, v, valueField.getValue()));
                })
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(BiomeDataOps.restore(biome, v));
                })
                .build();
        placeAll(layout, new String[]{"set", "restore"}, set, restore);
        needsSelection(() -> selected() != null, set, restore);
        rebuild();
        list.restore(keepList);
    }

    private BiomeDataOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : values.get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = BiomeDataOps.values(biome);
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            BiomeDataOps.Value v = values.get(i);
            // "Name: value", and what it does under it.
            String entity = BiomeDataOps.entityOf(v);
            String name = (entity.isEmpty() ? "" : com.strata.util.Labels.creature(entity) + ", ")
                    + BiomeDataOps.name(v);
            String sub = BiomeDataOps.what(v) + (v.own().isEmpty() ? "" : "  (yours; the game's: " + v.game() + ")");
            entries.add(new BrowseList.Entry(String.valueOf(i), name + ": " + v.effective(), sub,
                    v.own().isEmpty() ? Palette.TEXT : Palette.EDITED));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        BiomeDataOps.Value v = list == null ? null : selected();
        String help = v == null
                ? "Yellow values are yours. They apply from the next world load. Mobs and weights: the biome's mob list."
                : BiomeDataOps.name(v) + ": " + BiomeDataOps.what(v) + " The game's: " + v.game()
                        + ". Applies from the next world load.";
        String footer = null;
        String empty = values.isEmpty()
                ? "No values read for this biome: the extraction at game start failed (see the log)." : null;
        return com.strata.ui.screen.ListScreenLayouts.valueSheet(frame(), this.font::width, title(), 100, help,
                footer, empty);
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