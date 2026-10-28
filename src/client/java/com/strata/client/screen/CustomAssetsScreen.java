package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.domain.CustomAssetsOps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The custom textures and sounds of this machine, in Options: the library. */
public class CustomAssetsScreen extends StrataScreen {

    private static final String SEP = "|";

    private List<CustomAssetsOps.Asset> assets = List.of();
    private BrowseList list;

    public CustomAssetsScreen() {
        super("Strata — Custom textures & sounds");
    }

    @Override
    protected void init() {
        super.init();
        assets = CustomAssetsOps.all();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old list.
        list = null;
        com.strata.ui.Layout layout = layout();

        Button add = Button.builder(Component.literal(layout.textOf("add")),
                b -> CustomAssetScreen.open(CustomAssetsOps.TEXTURE, null)).build();
        placeAll(layout, new String[]{"add"}, add);

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("CustomAssetsScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);

        Button reimport = Button.builder(Component.literal("Re-import"), b -> {
            CustomAssetsOps.Asset a = selected();
            if (a != null) {
                ScreenNavigator.get().navigateTo(new CustomAssetScreen(a));
            }
        }).build();
        Button remove = Button.builder(Component.literal("Remove"), b -> removePicked()).build();
        placeAll(layout, new String[]{"reimport", "remove"}, reimport, remove);
        needsSelection(() -> selected() != null, reimport, remove);
        list.setOnActivate(entry -> {
            CustomAssetsOps.Asset a = selected();
            if (a != null) {
                ScreenNavigator.get().navigateTo(new CustomAssetScreen(a));
            }
        });
        rebuild();
        list.restore(keepList);
    }

    private com.strata.ui.Layout layout() {
        return com.strata.ui.screen.ListScreenLayouts.customAssets(frame(), this.font::width, title(),
                list != null && selected() != null, CustomAssetsOps.where(),
                "No custom files yet. + Add custom... imports a PNG texture, an Ogg Vorbis sound or a JSON model.");
    }

    /** The selected asset, or null (a group, or none). */
    private CustomAssetsOps.Asset selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        if (sel == null || !sel.id.contains(SEP)) {
            return null;
        }
        int cut = sel.id.indexOf(SEP);
        return CustomAssetsOps.find(sel.id.substring(0, cut), sel.id.substring(cut + 1));
    }

    /** The assets of every picked row (Ctrl or Shift picks several), else the selected one. */
    private List<CustomAssetsOps.Asset> pickedAssets() {
        List<CustomAssetsOps.Asset> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (String id : list.pickedIds()) {
            int cut = id.indexOf(SEP);
            CustomAssetsOps.Asset a = cut < 0 ? null : CustomAssetsOps.find(id.substring(0, cut), id.substring(cut + 1));
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }

    /** Removes every picked row, after asking. */
    private void removePicked() {
        List<CustomAssetsOps.Asset> picked = pickedAssets();
        if (picked.isEmpty()) {
            return;
        }
        String what = picked.size() == 1 ? "the custom " + picked.get(0).kind().toLowerCase() + " " + picked.get(0).id()
                : picked.size() + " custom files";
        minecraft.setScreen(new ConfirmDialog(this, "Remove " + what + "?\nTheir files leave the pack. A mob that uses one"
                + " shows the missing texture, or plays no sound.", () -> {
            for (CustomAssetsOps.Asset a : picked) {
                if (!report(CustomAssetsOps.remove(a.kind(), a.id()))) {
                    break;
                }
            }
            this.minecraft.reloadResourcePacks();
            assets = CustomAssetsOps.all();
            rebuild();
        }));
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        // No files: no group rows, so the empty note has the list to itself.
        for (String kind : assets.isEmpty() ? List.<String>of() : CustomAssetsOps.KINDS) {
            String group = "grp:" + kind;
            List<CustomAssetsOps.Asset> of = assets.stream().filter(a -> a.kind().equals(kind)).toList();
            entries.add(new BrowseList.Entry(group, kind + "s",
                    com.strata.ui.Words.count(of.size(), "file", "files"), Palette.GROUP, group, null, 0));
            for (CustomAssetsOps.Asset a : of) {
                String detail = a.resource() + "   from " + a.source()
                        + (a.subtitle().isEmpty() ? "" : "   subtitle: " + a.subtitle());
                entries.add(new BrowseList.Entry(a.kind() + SEP + a.id(), a.id(), detail, Palette.TEXT, null, group, 1));
            }
        }
        list.refresh(entries);
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
