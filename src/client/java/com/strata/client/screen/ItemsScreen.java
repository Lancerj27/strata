package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.domain.ItemStatsOps;
import com.strata.domain.ItemVariantOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client > Items: every item, by kind, with the designer's item variants under their base item and a "+ Add
 * variant" row under each item, as the mob list has.
 */
public class ItemsScreen extends StrataScreen {

    private static final String GROUP = "grp:";
    /** A variant row's id: "var:" and the variant's index. */
    private static final String VARIANT = "var:";
    /** An item's "+ Add variant" row: "addvar:" and the item's index. */
    private static final String ADD_VARIANT = "addvar:";

    private BrowseList list;
    private EditField search;
    private List<ItemStatsOps.Item> items = List.of();
    private List<ItemVariantOps.Variant> variants = List.of();
    private String status = "";
    private Button removeButton;

    public ItemsScreen() {
        super("Strata — Items");
    }

    @Override
    protected void init() {
        super.init();
        items = ItemStatsOps.items();
        variants = ItemVariantOps.variants();
        // THE AT-A-GLANCE COLUMNS: the user's choice, and every item's values.
        glance = new GlanceHeaders(this, this.font, com.strata.domain.GlanceColumns.ITEMS, this::rebuildWidgets);
        glance.load(com.strata.ui.screen.GlanceLayout.listRoom(frame()));
        glanceValues = ItemStatsOps.glance();
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
        // Blocks moved to the main menu.
        placeDataMenu(layout, "data", com.strata.ui.screen.ListScreenLayouts.DATA_MENU,
                dataEntry("Recipes", com.strata.data.JsonOverrides.RECIPE),
                dataEntry("Brewing", com.strata.data.JsonOverrides.BREWING),
                // A kind and its tags, or its two halves, are one list: a group each.
                dataEntry(com.strata.domain.OverrideViews.ENCHANTMENTS),
                // The item things that were World > Other game data.
                dataEntry("Enchantment rolls", com.strata.data.JsonOverrides.ENCHANTMENT_PROVIDER),
                dataEntry(com.strata.domain.OverrideViews.ARMOR_TRIMS),
                dataEntry(com.strata.domain.OverrideViews.BANNER_PATTERNS),
                dataEntry("Jukebox songs", com.strata.data.JsonOverrides.JUKEBOX_SONG),
                dataEntry(com.strata.domain.OverrideViews.PAINTINGS),
                // The item tags beside the items.
                dataEntry("Item tags", com.strata.data.JsonOverrides.ITEM_TAG),
                dataEntry("Item data (fuel, compost)", com.strata.data.JsonOverrides.ITEM_DATA),
                dataEntry("Goat horn tags", com.strata.data.JsonOverrides.INSTRUMENT_TAG));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ItemsScreen");
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, items, ItemStatsOps.Item::id));
        placeList(list, layout.get("list"));
        glance.layOut(list, layout, com.strata.ui.screen.GlanceLayout.columnWidth(
                com.strata.ui.screen.GlanceLayout.listRoom(frame()), this.font::width, glance.headings()));
        addRenderableWidget(list);
        Button edit = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        removeButton = Button.builder(Component.literal("Remove"), b -> removePicked()).build();
        // ON / OFF: the picked items.
        Button toggle = Button.builder(Component.literal("On / Off"), b -> togglePicked()).build();
        placeAll(layout, new String[]{"edit", "toggle", "remove"}, edit, toggle, removeButton);
        needsSelection(() -> !pickedItems().isEmpty(), toggle);
        needsSelection(() -> selectedId() != null && !list.manyPicked(), edit);
        needsSelection(() -> !pickedVariants().isEmpty(), removeButton);
        // "+ Add variant" under an item opens a new variant of it, as the mob list's does.
        list.setOnSelect(entry -> {
            if (entry.id.startsWith(ADD_VARIANT)) {
                String base = items.get(Integer.parseInt(entry.id.substring(ADD_VARIANT.length()))).id();
                ScreenNavigator.get().navigateTo(new ItemVariantScreen(null, base));
            }
        });
        // A double-click presses Edit.
        list.setOnActivate(entry -> editSelected());

        rebuild();
        list.restore(keepList);
    }

    private com.strata.ui.Layout layout() {
        int n = pickedVariants().size();
        return com.strata.ui.screen.ListScreenLayouts.itemList(frame(), this.font::width, title(),
                selectedId() != null, n > 1 ? "Remove (" + n + ")" : "Remove", n > 0, status,
                items.isEmpty() ? "No items: their stats could not be read at game start (see the log)" : null,
                glance.headings());
    }

    /** The at-a-glance columns: which show, the Columns... */
    private GlanceHeaders glance;
    /** Every item's effective values by key, read as the screen opens. */
    private Map<String, Map<String, String>> glanceValues = Map.of();

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !choicesOpen() && glance != null && list != null && glance.click(list, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The selected item or variant row's id; null for a group or an add row. */
    private String selectedId() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith(GROUP) || sel.id.startsWith(ADD_VARIANT) ? null : sel.id;
    }

    /** The item rows picked (not their variants): what On / Off disables. */
    private List<String> pickedItems() {
        return list == null ? List.of() : list.pickedIds().stream().filter(id -> id.matches("\\d+"))
                .map(id -> items.get(Integer.parseInt(id)).id()).toList();
    }

    private List<ItemVariantOps.Variant> pickedVariants() {
        return list == null ? List.of() : list.pickedIds().stream().filter(id -> id.startsWith(VARIANT))
                .map(id -> variants.get(Integer.parseInt(id.substring(VARIANT.length())))).toList();
    }

    private void editSelected() {
        String id = selectedId();
        if (id == null) {
            return;
        }
        if (id.startsWith(VARIANT)) {
            ScreenNavigator.get().navigateTo(new ItemVariantScreen(
                    variants.get(Integer.parseInt(id.substring(VARIANT.length()))).name()));
        } else {
            ItemStatsOps.Item item = items.get(Integer.parseInt(id));
            ScreenNavigator.get().navigateTo(new ItemStatScreen(item.id(), Labels.itemName(item.id())));
        }
    }

    /** Removing a variant always asks, naming them: it is a record, with its values and effects. */
    private void removePicked() {
        List<String> names = pickedVariants().stream().map(ItemVariantOps.Variant::name).toList();
        if (names.isEmpty()) {
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, "Remove " + com.strata.domain.Picks.listed(names) + "?\n"
                + "Their values and effects go too. Stacks already in the world stay as they are,\n"
                + "and their effects stop.", () -> act(ItemVariantOps.remove(names))));
    }

    private boolean act(WriteReport result) {
        if (report(result)) {
            variants = ItemVariantOps.variants();
            rebuild();
            return true;
        }
        return false;
    }

    /** The On / Off press. */
    private void togglePicked() {
        List<String> picked = pickedItems();
        if (act(com.strata.domain.DisabledOps.toggle(com.strata.domain.DisabledOps.ITEM, picked))
                && !picked.isEmpty()) {
            nameNoteChange(com.strata.domain.Picks.listed(picked.stream().map(Labels::itemName).toList()));
        }
    }

    /** How many items a group's rows hold: its variant and "+ Add variant" rows are not counted. */
    private static int itemRows(List<BrowseList.Entry> rows) {
        return (int) rows.stream().filter(r -> r.id.matches("\\d+")).count();
    }

    /** The disabled items, read as the list rebuilds. */
    private java.util.Set<String> disabled = java.util.Set.of();

    private void rebuild() {
        disabled = com.strata.domain.DisabledOps.ids(com.strata.domain.DisabledOps.ITEM);
        String text = search == null ? "" : search.getValue().strip().toLowerCase();
        String idText = text.replace(' ', '_');
        Map<String, List<Integer>> variantsByBase = new HashMap<>();
        for (int i = 0; i < variants.size(); i++) {
            variantsByBase.computeIfAbsent(variants.get(i).baseItem(), k -> new ArrayList<>()).add(i);
        }
        List<BrowseList.Entry> entries = new ArrayList<>();
        int shown = 0;
        // THE KINDS, AND THE SUB-GROUPS of the big ones: ItemGroupOps' sections, a sub-group nested under its
        // kind.
        Map<String, List<BrowseList.Entry>> byKind = new java.util.LinkedHashMap<>();
        Map<String, Integer> kindRows = new HashMap<>();
        for (com.strata.domain.ItemGroupOps.Section section : com.strata.domain.ItemGroupOps.sections(
                items.stream().map(ItemStatsOps.Item::kind).toList(), items.stream().map(ItemStatsOps.Item::id).toList())) {
            String kindGroup = GROUP + section.kind();
            boolean nested = !section.sub().isEmpty();
            String group = nested ? kindGroup + "/" + section.sub() : kindGroup;
            int depth = nested ? 1 : 0;
            List<BrowseList.Entry> rows = new ArrayList<>();
            for (int i : section.indexes()) {
            ItemStatsOps.Item item = items.get(i);
            String name = Labels.itemName(item.id());
            List<Integer> own = variantsByBase.getOrDefault(item.id(), List.of());
            boolean variantHit = own.stream().anyMatch(vi -> variants.get(vi).name().contains(idText));
            if (!text.isEmpty() && !name.toLowerCase().contains(text) && !item.id().contains(idText) && !variantHit) {
                continue;
            }
            boolean edited = item.edited() > 0;
            boolean off = disabled.contains(item.id());
            rows.add(new BrowseList.Entry(String.valueOf(i), name + (off ? "  [disabled]" : ""),
                    item.id() + ", " + com.strata.ui.Words.count(item.values(), "value", "values")
                    + (edited ? "  (" + item.edited() + " yours)" : "")
                    + (own.isEmpty() ? "" : "  " + com.strata.ui.Words.count(own.size(), "variant", "variants")),
                    off ? Palette.MUTED : edited ? Palette.EDITED : Palette.TEXT, null, group, depth + 1)
                    .columns(com.strata.domain.GlanceColumns.valuesFor(glance.shownKeys(), glanceValues.get(item.id()))));
            // THE VARIANTS UNDER THEIR ITEM, then "+ Add variant", as a mob's.
            for (int vi : own) {
                ItemVariantOps.Variant v = variants.get(vi);
                rows.add(new BrowseList.Entry(VARIANT + vi, v.name(), "variant, " + com.strata.ui.Words.count(v.values(), "value", "values") + ", "
                        + com.strata.ui.Words.count(v.effects(), "effect", "effects"), Palette.EDITED, null, group, depth + 2));
            }
            rows.add(new BrowseList.Entry(ADD_VARIANT + i, "+ Add variant", "", Palette.MUTED, null, group, depth + 2));
            shown++;
            }
            if (!rows.isEmpty()) {
                List<BrowseList.Entry> own = byKind.computeIfAbsent(section.kind(), k -> new ArrayList<>());
                if (nested) {
                    own.add(new BrowseList.Entry(group, section.sub() + " (" + itemRows(rows) + ")", "",
                            Palette.GROUP, group, kindGroup, 1));
                }
                own.addAll(rows);
                kindRows.merge(section.kind(), itemRows(rows), Integer::sum);
            }
        }
        byKind.forEach((kind, rows) -> {
            String id = GROUP + kind;
            entries.add(new BrowseList.Entry(id, kind + " (" + kindRows.get(kind) + ")", "",
                    Palette.GROUP, id, null, 0));
            entries.addAll(rows);
        });
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
        status = com.strata.ui.Words.count(shown, "item", "items") + ", " + com.strata.ui.Words.count(variants.size(), "variant", "variants");
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

