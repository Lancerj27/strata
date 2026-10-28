package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.domain.CustomAssetsOps;
import com.strata.game.RegistryQuery;
import com.strata.ui.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Every sound, texture or block and item model the game has loaded, in Options: Replace with a custom one, Restore. */
public class AssetMasterScreen extends StrataScreen {

    private static final String GROUP = "grp:";

    private final String kind;
    private BrowseList list;
    private EditField search;
    /** Target id to the custom asset that replaces it. */
    private Map<String, String> replaced = Map.of();

    @Override
    protected String helpKey() {
        return "AssetMasterScreen:" + kind;
    }

    public AssetMasterScreen(String kind) {
        super("Strata — " + kind + "s");
        this.kind = kind;
    }

    private boolean sound() {
        return CustomAssetsOps.SOUND.equals(kind);
    }

    @Override
    protected void init() {
        super.init();
        BrowseList.State keepList = list == null ? null : list.state();
        String keepSearch = search == null ? "" : search.getValue();
        list = null;
        com.strata.ui.Layout layout = layout();

        search = new EditField(this.font, 0, 0, 0, 0, "Search", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> list.setFilter(s));
        addRenderableWidget(place(search.getWidget(), layout.get("search")));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("AssetMasterScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            if (sound() && !entry.id.startsWith(GROUP)) {
                play();
            }
        });
        addRenderableWidget(list);

        Button replace = Button.builder(Component.literal("Replace..."), b -> pickReplacement()).build();
        Button restore = Button.builder(Component.literal("Restore"), b -> restore()).build();
        placeAll(layout, new String[]{"replace", "restore"}, replace, restore);
        needsSelection(() -> selected() != null, replace);
        needsSelection(() -> selected() != null && replaced.containsKey(selected()), restore);
        if (sound()) {
            Button play = Button.builder(Component.literal("Play"), b -> play()).build();
            placeAll(layout, new String[]{"play"}, play);
            needsSelection(() -> selected() != null, play);
        }

        rebuild();
        list.restore(keepList);
        if (!keepSearch.isBlank()) {
            list.setFilter(keepSearch);
        }
    }

    private com.strata.ui.Layout layout() {
        return com.strata.ui.screen.ListScreenLayouts.assetMaster(frame(), this.font::width, title(), kind,
                "Replacements are saved in the profile. Load to server sends them, and the custom files they use.");
    }

    /** Every id of this kind the game has loaded, except the custom ones. */
    private List<ResourceLocation> gameIds() {
        java.util.Collection<ResourceLocation> ids = sound()
                ? this.minecraft.getSoundManager().getAvailableSounds()
                : CustomAssetsOps.MODEL.equals(kind)
                ? this.minecraft.getResourceManager().listResources("models", rl -> rl.getPath().endsWith(".json")
                        && (rl.getPath().startsWith("models/block/") || rl.getPath().startsWith("models/item/"))).keySet()
                : this.minecraft.getResourceManager().listResources("textures", rl -> rl.getPath().endsWith(".png")).keySet();
        return ids.stream().filter(id -> !CustomAssetsOps.NAMESPACE.equals(id.getNamespace())).sorted().toList();
    }

    /** "minecraft · entity": the namespace and the first folder, the list's groups. */
    private String groupOf(ResourceLocation id) {
        String path = sound() ? id.getPath() : id.getPath().substring(id.getPath().indexOf('/') + 1);
        int cut = sound() ? path.indexOf('.') : path.indexOf('/');
        return id.getNamespace() + " · " + (cut < 0 ? path : path.substring(0, cut));
    }

    private void rebuild() {
        replaced = new java.util.HashMap<>();
        for (CustomAssetsOps.Replacement r : CustomAssetsOps.replacements()) {
            if (r.kind().equals(kind)) {
                replaced.put(r.target(), r.customId());
            }
        }
        Map<String, List<ResourceLocation>> groups = new TreeMap<>();
        for (ResourceLocation id : gameIds()) {
            groups.computeIfAbsent(groupOf(id), g -> new ArrayList<>()).add(id);
        }
        List<BrowseList.Entry> entries = new ArrayList<>();
        groups.forEach((group, ids) -> {
            String gid = GROUP + group;
            long changed = ids.stream().filter(id -> replaced.containsKey(id.toString())).count();
            entries.add(new BrowseList.Entry(gid, group, com.strata.ui.Words.count(ids.size(), "file", "files")
                    + (changed == 0 ? "" : ", " + changed + " replaced"), Palette.GROUP, gid, null, 0));
            for (ResourceLocation id : ids) {
                String with = replaced.get(id.toString());
                entries.add(new BrowseList.Entry(id.toString(), id.toString(),
                        with == null ? "" : "Replaced by the custom " + kind.toLowerCase() + " " + with,
                        with == null ? Palette.TEXT : Palette.EDITED, null, gid, 1));
            }
        });
        list.refresh(entries);
    }

    /** The selected game id, or null (a group, or none). */
    private String selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith(GROUP) ? null : sel.id;
    }

    private void play() {
        String id = selected();
        if (id != null && !com.strata.client.SoundPreview.play(id, 1F, 1F)) {
            refuse("No sound " + id + " in the game");
        }
    }

    /** The custom files of this kind, and "+ Add custom..." to import one: the only things a target takes. */
    private List<RegistryQuery.Option> customOptions() {
        List<RegistryQuery.Option> out = new ArrayList<>();
        out.add(RegistryQuery.addCustomOption());
        for (CustomAssetsOps.Asset a : CustomAssetsOps.ofKind(com.strata.data.Paths.ROOT, kind)) {
            out.add(new RegistryQuery.Option(a.resource(), a.label(), RegistryQuery.CUSTOM_GROUP));
        }
        return out;
    }

    private void pickReplacement() {
        String target = selected();
        if (target == null) {
            return;
        }
        RegistryPickerScreen picker = new RegistryPickerScreen("Replace " + target + " with", customOptions(),
                resource -> {
                    CustomAssetsOps.Asset custom = CustomAssetsOps.byResource(com.strata.data.Paths.ROOT, resource);
                    if (custom != null && report(CustomAssetsOps.replace(kind, target, custom.id()))) {
                        this.minecraft.reloadResourcePacks();
                    }
                });
        picker.withCustom(kind, this::customOptions);
        if (sound()) {
            picker.withPlay();
        }
        ScreenNavigator.get().navigateTo(picker);
    }

    private void restore() {
        String target = selected();
        if (target != null && report(CustomAssetsOps.restoreGame(kind, target))) {
            this.minecraft.reloadResourcePacks();
            rebuild();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.id().startsWith("hint")) {
                text(graphics, e, Palette.HINT);
            }
        }
        if (layout.get("footer") != null) {
            text(graphics, layout.get("footer"), Palette.MUTED);
        }
    }
}
