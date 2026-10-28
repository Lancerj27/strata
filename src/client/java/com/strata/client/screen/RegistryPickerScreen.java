package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.game.RegistryQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Searchable picker over a pre-built list of options. */
public class RegistryPickerScreen extends StrataScreen {

    private List<RegistryQuery.Option> options;
    private final Consumer<String> onSelect;

    private BrowseList browseList;
    private EditField searchField;
    private DropdownSelect groupFilter;
    /** Survives the screen rebuild, so a chosen filter is not lost on resize. */
    private String groupFilterValue = ALL_GROUPS;

    /**
     * @param title header text ("Pick a mob")
     * @param options the full option set — sorted and grouped by this screen
     * @param onSelect callback receiving the selected option's id
     */
    public RegistryPickerScreen(String title,
                                List<RegistryQuery.Option> options, Consumer<String> onSelect) {
        super(pathUnderCaller(title));
        this.options = List.copyOf(options);
        this.onSelect = onSelect;
    }

    /** True for the sound picker: a click plays the sound, Pick takes it. */
    private boolean sound;

    /** Makes this the sound picker. */
    public RegistryPickerScreen withPlay() {
        this.sound = true;
        return this;
    }

    /** The kind of custom asset this picker can add ("+ Add custom..."), or null. */
    private String customKind;
    /** The options as they are NOW, read again when the picker is shown again after an import. */
    private java.util.function.Supplier<List<RegistryQuery.Option>> fresh;
    /** A name to put in the search when the picker is shown again: the asset just added. */
    private String prefill;

    /**
     * Makes this a Sound or Texture picker that can import a file: its list gets a "+ Add custom..." entry,
     * which opens the import screen preset to {@code assetKind}; coming back, the list is read again from
     * {@code freshOptions} and the new asset is searched for.
     */
    public RegistryPickerScreen withCustom(String assetKind,
                                           java.util.function.Supplier<List<RegistryQuery.Option>> freshOptions) {
        this.customKind = assetKind;
        this.fresh = freshOptions;
        return this;
    }

    private void addCustom() {
        CustomAssetScreen.open(customKind, id -> {
            int cut = Math.max(id.lastIndexOf('/'), id.lastIndexOf(':'));
            String added = id.substring(cut + 1);
            prefill = added.endsWith(".png") ? added.substring(0, added.length() - 4) : added;
        });
    }

    /**
     * The picker's path: the screen that opened it, then its own question, "Strata / World / Mobs / Zombie /
     * Pick an item".
     */
    private static String pathUnderCaller(String title) {
        net.minecraft.client.gui.screens.Screen caller = net.minecraft.client.Minecraft.getInstance() == null
                ? null : net.minecraft.client.Minecraft.getInstance().screen;
        return caller instanceof StrataScreen s && s.title().startsWith(com.strata.ui.TitlePath.STRATA + com.strata.ui.TitlePath.SEP)
                ? s.title() + com.strata.ui.TitlePath.SEP + title : "Strata — " + title;
    }

    @Override
    protected void init() {
        // A resize runs init again: keep the search and the place in the list.
        String keepSearch = searchField == null ? "" : searchField.getValue();
        BrowseList.State keepList = browseList == null ? null : browseList.state();
        if (prefill != null) {
            // Back from the import screen: the new asset is in the list; search for it.
            options = List.copyOf(fresh.get());
            keepSearch = prefill;
            keepList = null;
            prefill = null;
        }
        super.init();

        List<String> groupOptions = new ArrayList<>();
        groupOptions.add(ALL_GROUPS);
        groupOptions.addAll(orderGroups(options.stream()
                .map(RegistryQuery.Option::namespace)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))));
        if (!groupOptions.contains(groupFilterValue)) {
            groupFilterValue = ALL_GROUPS;
        }
        // Search shares the top row with the group filter, so a long registry can be narrowed to one source
        // (e.g. just your own variants) before typing anything at all.
        com.strata.ui.Layout layout = com.strata.ui.screen.ListScreenLayouts.picker(frame(), this.font::width,
                title(), groupOptions, sound);
        pickerLayout = layout;
        searchField = new EditField(this.font, 0, 0, 0, 0, "Search", keepSearch);
        searchField.setValidator(s -> true);
        searchField.setResponder(s -> browseList.setFilter(s));
        addRenderableWidget(place(searchField.getWidget(), layout.get("search")));

        groupFilter = new DropdownSelect(0, 0, 0, 0, "Show", groupOptions, groupFilterValue);
        groupFilter.setOnChange(value -> {
            groupFilterValue = value;
            populateList();
        });
        // A group shows as its heading does.
        groupFilter.setWords(ns -> ALL_GROUPS.equals(ns) ? ns : groupLabel(ns));
        addRenderableWidget(place(groupFilter.getWidget(), layout.get("group")));
        groupFilter.fitTo(this.font::width,
                layout.boxOf("group").w() - com.strata.ui.screen.ListLayout.CHOICE_MARGIN);

        browseList = new BrowseList(this.minecraft, 1, 1, 0, scaled(24));
        placeList(browseList, layout.get("list"));

        populateList();
        browseList.restore(keepList);

        browseList.setOnSelect(entry -> {
            if (customKind != null && RegistryQuery.ADD_CUSTOM.equals(entry.id)) {
                // Opens the import screen; it comes back to this picker, which stays open.
                addCustom();
                return;
            }
            if (sound) {
                playSelected();
                return;
            }
            onSelect.accept(entry.id);
            ScreenNavigator.get().back();
        });

        addRenderableWidget(browseList);
        if (sound) {
            net.minecraft.client.gui.components.Button play = net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Play"), b -> playSelected()).build();
            net.minecraft.client.gui.components.Button pick = net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Pick"), b -> {
                onSelect.accept(browseList.getSelected().id);
                ScreenNavigator.get().back();
            }).build();
            placeAll(layout, new String[]{"play", "pick"}, play, pick);
            needsSelection(() -> browseList.getSelected() != null && browseList.getSelected().groupId == null
                    && !RegistryQuery.ADD_CUSTOM.equals(browseList.getSelected().id), play, pick);
        }
    }
    /**
     * Picker options for plain names that are not registry ids, such as tier names: each shows as itself, in
     * one group.
     */
    public static List<RegistryQuery.Option> plainOptions(String group, List<String> names) {
        List<RegistryQuery.Option> out = new ArrayList<>();
        for (String name : names) {
            out.add(new RegistryQuery.Option(name, name, group));
        }
        return out;
    }

    /** The "Show" filter value meaning "every group". */
    public static final String ALL_GROUPS = "All";

    /** Namespace Strata uses for the designer's own variants. */
    public static final String VARIANT_GROUP = "variant";
    /** The group of a choice that empties what is picked (the recipe grid's Empty cell): first of all. */
    public static final String CLEAR_GROUP = "clear";
    /** The group of what the designer worked on this session, first but for Clear. */
    public static final String RECENT_GROUP = "recent";

    /** Group order: the designer's own VARIANTS first, then vanilla, then mods alphabetically. */
    public static List<String> orderGroups(Collection<String> namespaces) {
        List<String> out = new ArrayList<>(namespaces);
        out.sort((a, b) -> {
            int ra = groupRank(a);
            int rb = groupRank(b);
            return ra != rb ? Integer.compare(ra, rb) : a.compareToIgnoreCase(b);
        });
        return out;
    }

    private static int groupRank(String namespace) {
        if (RegistryQuery.ADD_GROUP.equals(namespace)) return -3;
        if (CLEAR_GROUP.equals(namespace)) return -2;
        if (RECENT_GROUP.equals(namespace)) return -1;
        if (VARIANT_GROUP.equals(namespace) || RegistryQuery.CUSTOM_GROUP.equals(namespace)) return 0;
        if ("minecraft".equals(namespace)) return 1;
        return 2;
    }

    /**
     * The options with a Recent group in front: each recent id the options hold, newest first, again under
     * {@link #RECENT_GROUP}.
     */
    public static List<RegistryQuery.Option> withRecent(List<RegistryQuery.Option> options, List<String> recent) {
        Map<String, RegistryQuery.Option> byId = new LinkedHashMap<>();
        options.forEach(o -> byId.putIfAbsent(o.id(), o));
        List<RegistryQuery.Option> out = new ArrayList<>();
        for (String id : recent) {
            RegistryQuery.Option o = byId.get(id);
            if (o != null) {
                out.add(new RegistryQuery.Option(o.id(), o.displayName(), RECENT_GROUP));
            }
        }
        out.addAll(options);
        return out;
    }

    /** The game's mobs with the recent ones first, for every mob picker. */
    public static List<RegistryQuery.Option> entitiesWithRecent() {
        return withRecent(RegistryQuery.entities(), com.strata.client.util.RecentEdits.recentEntities());
    }

    private void playSelected() {
        var entry = browseList.getSelected();
        if (entry != null && !com.strata.client.SoundPreview.play(entry.id, 1F, 1F)) {
            refuse("No sound " + entry.id + " in the game");
        }
    }

    /** The layout as built in init, for the search's title. */
    private com.strata.ui.Layout pickerLayout;

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, pickerLayout);
    }

    /** A picker's folds are its own per title: "Minecraft" means a mob in one, an item in another. */
    @Override
    protected String foldKey() {
        return super.foldKey() + ":" + title();
    }

    /** A group's heading: its name with a capital first letter. */
    private static String groupLabel(String ns) {
        return ns.isEmpty() ? ns : ns.substring(0, 1).toUpperCase() + ns.substring(1);
    }

    /** True when a group passes the current "Show" filter. */
    public static boolean matchesGroup(String filter, String namespace) {
        return filter == null || ALL_GROUPS.equals(filter) || filter.equals(namespace);
    }

    private void populateList() {
        Map<String, List<RegistryQuery.Option>> groups = new LinkedHashMap<>();
        List<RegistryQuery.Option> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparing(RegistryQuery.Option::displayName, String.CASE_INSENSITIVE_ORDER));

        for (RegistryQuery.Option opt : sorted) {
            groups.computeIfAbsent(opt.namespace(), k -> new ArrayList<>()).add(opt);
        }

        List<String> namespaces = orderGroups(groups.keySet());

        List<BrowseList.Entry> entries = new ArrayList<>();
        for (String ns : namespaces) {
            if (!matchesGroup(groupFilterValue, ns)) {
                continue;
            }
            // Collapsible group header — clicking toggles children
            entries.add(new BrowseList.Entry(
                    "__group__" + ns, groupLabel(ns),
                    groups.get(ns).size() + " entries",
                    Palette.EDITED, ns, null, 0));

            for (RegistryQuery.Option opt : groups.get(ns)) {
                // Child entry — hidden when group is collapsed
                entries.add(new BrowseList.Entry(
                        opt.id(), opt.displayName(), opt.id(),
                        Palette.TEXT, null, ns, 1));
            }
        }
        browseList.refresh(entries);
    }
}
