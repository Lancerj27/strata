package com.strata.client.screen;

import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.MobBiomesOps;
import com.strata.ui.Palette;
import com.strata.util.Labels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** A mob's Biomes: every biome where it can spawn naturally, by dimension, each On or Off. */
public class MobBiomesScreen extends StrataScreen {

    private final String entity;
    private List<MobBiomesOps.Row> rows = List.of();
    private BrowseList list;
    private EditField search;

    public MobBiomesScreen(String entity) {
        super("Strata — Mob: " + Labels.creature(entity) + ": Biomes");
        this.entity = entity;
        editingMob(entity, "");
    }

    private com.strata.ui.Layout layout() {
        return com.strata.ui.screen.ListScreenLayouts.mobBiomes(frame(), this.font::width, title(),
                rows.isEmpty() ? "It spawns naturally in no biome, or no world is open." : null);
    }

    @Override
    protected void init() {
        super.init();
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table.
        list = null;
        load();
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);

        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> { if (list != null) list.setFilter(s); });
        addRenderableWidget(place(search.getWidget(), layout.get("search")));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("MobBiomesScreen");
        placeList(list, layout.get("list"));
        rebuild();
        list.restore(keepList);
        addRenderableWidget(list);

        Button onOff = Button.builder(Component.literal("On / Off"), b -> toggle()).build();
        placeAll(layout, new String[]{"toggle"}, onOff);
        needsSelection(() -> !picked().isEmpty(), onOff);
    }

    private void load() {
        List<MobBiomesOps.Place> places = new ArrayList<>();
        com.strata.game.BiomeCatalog.all(this.minecraft != null && this.minecraft.level != null ? worldRegistries() : null)
                .forEach(b -> places.add(new MobBiomesOps.Place(b.id(), b.dimension())));
        rows = MobBiomesOps.rows(entity, places, com.strata.data.Config.resolver());
    }

    /** The picked rows. */
    private List<MobBiomesOps.Row> picked() {
        List<MobBiomesOps.Row> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (String id : list.pickedIds()) {
            if (!id.startsWith("dim:")) {
                out.add(rows.get(Integer.parseInt(id)));
            }
        }
        return out;
    }

    private void toggle() {
        List<MobBiomesOps.Row> picked = picked();
        if (!picked.isEmpty() && report(MobBiomesOps.toggle(entity, picked))) {
            load();
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        String dimension = null;
        for (int i = 0; i < rows.size(); i++) {
            MobBiomesOps.Row r = rows.get(i);
            if (!r.dimension().equals(dimension)) {
                dimension = r.dimension();
                entries.add(new BrowseList.Entry("dim:" + dimension, dimension.isBlank() ? "Other" : Labels.pretty(dimension), "", Palette.GROUP,
                        "dim:" + dimension, null, 0));
            }
            String sub = (r.on() ? "On" : "Off") + " · " + r.source() + " · weight " + r.weight()
                    + " · " + r.minCount() + " to " + r.maxCount() + " · " + r.category();
            entries.add(new BrowseList.Entry(String.valueOf(i), Labels.biome(r.biome()), sub,
                    r.on() ? Palette.TEXT : Palette.MUTED, null, "dim:" + dimension, 1));
        }
        list.refresh(entries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        if (layout.get("footer") != null) {
            text(graphics, layout.get("footer"), Palette.MUTED);
        }
        emptyNote(graphics, layout, list);
    }
}
