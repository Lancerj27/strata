package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.domain.CustomStructuresOps;
import com.strata.ui.Palette;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The designer's structures,
 * in Options beside Custom textures &amp; sounds.
 */
public class CustomStructuresScreen extends StrataScreen {

    private BrowseList list;
    private EditField search;

    public CustomStructuresScreen() {
        super(com.strata.ui.TitlePath.WORLD, "Strata — Custom structures");
    }

    private com.strata.ui.Layout layout() {
        return com.strata.ui.screen.ListScreenLayouts.customStructureList(frame(), this.font::width, title(), true);
    }

    @Override
    protected void init() {
        super.init();
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table.
        list = null;

        com.strata.ui.Layout layout = layout();
        placeUndo(layout);

        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> { if (list != null) list.setFilter(s); });
        addRenderableWidget(place(search.getWidget(), layout.get("search")));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("CustomStructuresScreen");
        placeList(list, layout.get("list"));
        rebuild();
        list.restore(keepList);
        addRenderableWidget(list);

        Button edit = Button.builder(Component.literal("Edit"), b -> {
            List<String> picked = picked();
            if (!picked.isEmpty()) {
                ScreenNavigator.get().navigateTo(new CustomStructureScreen(picked.get(0)));
            }
        }).build();
        Button on = Button.builder(Component.literal("On"), b -> setEnabled(true)).build();
        Button off = Button.builder(Component.literal("Off"), b -> setEnabled(false)).build();
        Button remove = Button.builder(Component.literal("Remove"), b -> confirmRemove()).build();
        placeAll(layout, new String[]{"add", "edit", "on", "off", "remove"},
                Button.builder(Component.literal("+ Add structure"),
                        b -> ScreenNavigator.get().navigateTo(new CustomStructureScreen(null))).build(),
                edit, on, off, remove);
        needsSelection(() -> !picked().isEmpty(), edit, on, off, remove);
    }

    /** The picked structures' names, in list order. */
    private List<String> picked() {
        List<String> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (String id : list.pickedIds()) {
            if (!id.startsWith("grp:")) {
                out.add(id);
            }
        }
        return out;
    }

    private void setEnabled(boolean on) {
        if (report(CustomStructuresOps.setEnabled(picked(), on))) {
            rebuild();
        }
    }

    private void confirmRemove() {
        List<String> names = picked();
        if (names.isEmpty()) {
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, "Remove " + com.strata.ui.Words.count(names.size(),
                "structure", "structures") + " and their files?\nLand already made keeps what was built.",
                () -> report(CustomStructuresOps.remove(names))));
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (CustomStructuresOps.Entry e : CustomStructuresOps.all()) {
            String sub = (e.enabled() ? "" : "off · ") + e.biomes() + " · every " + e.spacing() + " chunks"
                    + (e.hasFile() ? "" : " · its file is missing");
            entries.add(new BrowseList.Entry(e.name(), e.name(), sub, e.enabled() && e.hasFile() ? Palette.TEXT : Palette.MUTED,
                    null, null, 0));
        }
        list.refresh(entries);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        if (layout.get("footer") != null) {
            text(graphics, layout.get("footer"), Palette.MUTED);
        }
        emptyNote(graphics, layout, list);
    }
}
