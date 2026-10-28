package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.domain.BiomeOps;
import com.strata.game.BiomeCatalog;
import com.strata.game.DimensionBiomes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Biome browser with text search and a tier filter. */
public class BiomeListScreen extends StrataScreen {

    /** The tier filter's two states: every biome, or only the ones with no tier yet, in a dropdown. */
    private static final String ALL_TIERS = "All tiers";
    private static final String NO_TIER = "Unassigned";
    private static final List<String> FILTER_STATES = List.of(ALL_TIERS, NO_TIER);

    /** Group header id for the biomes edited this session. */
    private static final String RECENT_GROUP = "recent:";

    private List<BiomeCatalog.Entry> universe = List.of();

    private BrowseList list;
    private EditField search;
    /** Kept on the screen, so the filter survives init. */
    private String tierFilterValue = ALL_TIERS;
    private String status = "";
    /** The tier this list adds biomes to, or null for the Biomes list. */
    private final String forTier;
    /** The biomes the list shows now: what the assign button assigns. */
    private List<String> shownIds = List.of();
    private Button assignButton;

    public BiomeListScreen() {
        this(null);
    }

    /** The biome list that adds biomes to a tier; it opens on the biomes with no tier. */
    public BiomeListScreen(String forTier) {
        super(forTier == null ? "Strata — Biomes" : "Strata — Add biomes to " + forTier);
        this.forTier = forTier;
        if (forTier != null) {
            tierFilterValue = NO_TIER;
        }
    }

    @Override
    protected void init() {
        super.init();
        universe = BiomeCatalog.all(this.minecraft != null && this.minecraft.level != null
                ? worldRegistries() : null);

        // init runs again on every return here.
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);

        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> rebuild());
        addRenderableWidget(place(search.getWidget(), layout.get("search")));


        // The Tier filter stays in the HEADER band beside the search box, NEVER at the bottom of the list -
        // controls belong at the top where they are found.
        DropdownSelect tierFilter = new DropdownSelect(0, 0, 0, 0, "Show", FILTER_STATES, tierFilterValue);
        tierFilter.setOnChange(value -> {
            tierFilterValue = value;
            rebuild();
        });
        addRenderableWidget(place(tierFilter.getWidget(), layout.get("filter")));
        // The biome data beside the biomes, one Data...
        if (forTier == null) {
            placeDataMenu(layout, "data", com.strata.ui.screen.ListScreenLayouts.DATA_MENU,
                    dataEntry("Biome placement", com.strata.data.JsonOverrides.BIOME_PARAMETERS),
                    dataEntry("Biome modifiers", com.strata.data.JsonOverrides.BIOME_MODIFIER),
                    dataEntry("Biome tags", com.strata.data.JsonOverrides.BIOME_TAG));
        }

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22));
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            if (!entry.id.startsWith("band:")) {
                // From a tier, a click adds that one biome to it.
                if (forTier != null) {
                    confirmAssign(List.of(entry.id), forTier);
                } else {
                    ScreenNavigator.get().navigateTo(new BiomeEditScreen(entry.id));
                }
            }
        });
        addRenderableWidget(list);

        // From a tier only: the Biomes list sets a biome's tier in its editor.
        if (forTier != null) {
            assignButton = Button.builder(Component.literal(layout.textOf("assign")), b -> assignShown()).build();
            placeAll(layout, new String[]{"assign"}, assignButton);
        }

        rebuild();
        list.restore(keepList);
    }

    private com.strata.ui.Layout layout() {
        String label = forTier == null ? null : "Add shown to " + forTier + " (" + shownIds.size() + ")";
        return com.strata.ui.screen.ListScreenLayouts.biomeList(frame(), this.font::width, title(), FILTER_STATES,
                label, !shownIds.isEmpty(), status, universe.isEmpty()
                        ? "No biomes: the game data could not be read at game start (see the log)" : null);
    }

    /** Adds every biome the list shows to this list's tier. */
    private void assignShown() {
        confirmAssign(List.copyOf(shownIds), forTier);
    }

    /** Asks first, naming what is added and what moves from another tier, then assigns. */
    private void confirmAssign(List<String> ids, String tier) {
        BiomeOps.AssignPlan plan = BiomeOps.assignPlan(tier, ids);
        if (plan.changes() == 0) {
            report(BiomeOps.assignAll(tier, ids));
            return;
        }
        StringBuilder text = new StringBuilder("Assign " + com.strata.ui.Words.count(plan.changes(), "biome", "biomes") + " to " + tier + "?");
        if (!plan.add().isEmpty()) {
            text.append("\nAdd: ").append(names(plan.add()));
        }
        if (!plan.move().isEmpty()) {
            List<String> moves = new ArrayList<>();
            plan.move().forEach((biome, from) -> moves.add(Labels.biome(biome) + " (from " + from + ")"));
            text.append("\nMove: ").append(com.strata.domain.Picks.listed(moves));
        }
        if (!plan.already().isEmpty()) {
            text.append("\n").append(plan.already().size()).append(" already in ").append(tier).append(".");
        }
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, text.toString(),
                // The dialog returns here, and init reads the tiers again.
                () -> report(BiomeOps.assignAll(tier, ids))));
    }

    private static String names(List<String> ids) {
        return com.strata.domain.Picks.listed(ids.stream().map(Labels::biome).toList());
    }


    private void rebuild() {
        String text = search == null ? "" : search.getValue();
        String filter = tierFilterValue;

        // RECENT FIRST, as on the mob list: a biome edited this session shows at the top, newest first, and
        // ALSO in its own group below, so it never vanishes from where it belongs.
        List<String> recentOrder = com.strata.client.util.RecentEdits.recentBiomes();
        List<String> recent = new ArrayList<>();

        // SOURCE -> dimension -> temperature band -> biomes: Minecraft's first, then each mod's.
        Map<String, Map<String, Map<String, List<String>>>> grouped = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Map<String, List<String>>>>> bySource = new java.util.TreeMap<>(
                java.util.Comparator.comparing((String ns) -> !"minecraft".equals(ns)).thenComparing(ns -> ns));

        int shown = 0;
        List<String> ids = new ArrayList<>();
        for (BiomeCatalog.Entry biome : universe) {
            String id = biome.id();
            if (!matchesTier(BiomeOps.tierOf(id), filter)) continue;
            // The name on the row as well as the id.
            if (!com.strata.ui.Search.matches(text, id, Labels.biome(id))) continue;
            shown++;
            ids.add(id);
            if (recentOrder.contains(id)) {
                recent.add(id);
            }
            bySource.computeIfAbsent(namespace(id), k -> new LinkedHashMap<>())
                    .computeIfAbsent(biome.dimension(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(biome.band(), k -> new LinkedHashMap<>())
                    .computeIfAbsent("", k -> new ArrayList<>()).add(id);
        }
        recent.sort(java.util.Comparator.comparingInt(recentOrder::indexOf));

        List<BrowseList.Entry> entries = new ArrayList<>();
        if (!recent.isEmpty()) {
            entries.add(new BrowseList.Entry(RECENT_GROUP, "Recent (" + recent.size() + ")", "",
                    Palette.GROUP, RECENT_GROUP, null, 0));
            for (String id : recent) {
                entries.add(biomeRow(id, RECENT_GROUP, 1));
            }
        }
        boolean sources = bySource.size() > 1;
        for (var source : bySource.entrySet()) {
            grouped = source.getValue();
            String srcGroupId = sources ? "src:" + source.getKey() : null;
            int top = sources ? 1 : 0;
            if (sources) {
                int srcCount = grouped.values().stream().flatMap(m -> m.values().stream())
                        .flatMap(m -> m.values().stream()).mapToInt(List::size).sum();
                entries.add(new BrowseList.Entry(srcGroupId, sourceName(source.getKey()) + " (" + srcCount + ")", "",
                        Palette.GROUP, srcGroupId, null, 0));
            }
            for (String dimension : dimensionOrder(grouped.keySet())) {
                Map<String, Map<String, List<String>>> byBand = grouped.get(dimension);
                if (byBand == null || byBand.isEmpty()) continue;
                int dimCount = byBand.values().stream()
                        .flatMap(m -> m.values().stream())
                        .mapToInt(List::size).sum();
                String dimGroupId = (sources ? srcGroupId + "/" : "") + "dim:" + dimension;
                entries.add(new BrowseList.Entry(dimGroupId,
                        dimension + " (" + dimCount + ")", "", Palette.GROUP,
                        dimGroupId, srcGroupId, top));

                for (String band : BiomeCatalog.BANDS) {
                    Map<String, List<String>> inBand = byBand.get(band);
                    if (inBand == null || inBand.isEmpty()) continue;
                    List<String> biomes = inBand.get("");
                    String bandGroupId = dimGroupId + "/band:" + band;
                    entries.add(new BrowseList.Entry(bandGroupId,
                            band + " (" + biomes.size() + ")", "", Palette.GROUP,
                            bandGroupId, dimGroupId, top + 1));
                    for (String id : biomes) {
                        entries.add(biomeRow(id, bandGroupId, top + 2));
                    }
                }
            }
        }
        // A search's matches show unfolded.
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
        status = com.strata.ui.Words.count(shown, "biome", "biomes") + " shown. " + (forTier != null ? "Click a biome to add it to " + forTier + "."
                : "Click a biome to open it.");
        shownIds = List.copyOf(ids);
        if (assignButton != null) {
            com.strata.ui.Layout.Element assign = layout().get("assign");
            assignButton.setMessage(Component.literal(assign.text()));
            assignButton.active = assign.active();
        }
    }

    /** A source heading: Minecraft, or a mod's namespace in words ("biomesoplenty" stays as the mod wrote it). */
    private static String sourceName(String ns) {
        return "minecraft".equals(ns) ? "Minecraft" : ns;
    }

    private static String namespace(String id) {
        int c = id.indexOf(':');
        return c >= 0 ? id.substring(0, c) : "minecraft";
    }

    /**
     * Dimension headings in reading order: the vanilla three, then modded dimensions alphabetically, then the
     * unclaimed bucket last.
     */
    private static List<String> dimensionOrder(java.util.Set<String> present) {
        List<String> out = new ArrayList<>(present);
        out.sort(DimensionBiomes.headingOrder());
        return out;
    }

    /** One biome row: its name, and its tier or "unassigned". */
    private static BrowseList.Entry biomeRow(String id, String group, int indent) {
        String tier = BiomeOps.tierOf(id);
        String subtitle = tier == null ? "unassigned" : "tier: " + tier;
        // The biome's name in words, as its editor's title shows it.
        return new BrowseList.Entry(id, Labels.biome(id), subtitle, Palette.TEXT, null, group, indent);
    }

    private static boolean matchesTier(String tier, String sel) {
        return !NO_TIER.equals(sel) || tier == null;
    }


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}
