package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.util.Labels;
import com.strata.client.widget.EditField;
import com.strata.domain.MobOps;
import com.strata.domain.StatColumns;
import com.strata.game.MobCatalog;
import com.strata.model.Source;
import com.strata.model.SpawnCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Context-aware mob spawn browser. */
public class MobListScreen extends StrataScreen {

    private final String fixedScope;
    /** Opened with no scope: the Global list, which greys the top bar's Mobs (StrataScreen). */
    private final boolean global;

    boolean isGlobal() {
        return global;
    }
    private final String fixedScopeId;
    private final boolean isScoped;

    private BrowseList list;
    private EditField search;
    private String status = "";
    /** The rows, from {@link MobCatalog#entries}. */
    private List<MobCatalog.MobEntry> allRows = List.of();

    private static String displayName(MobCatalog.MobEntry row, String listScopeId) {
        // A LEADING DOT MEANS STRATA HAS TOUCHED THIS CREATURE.
        String name = (row.changed() ? "\u2022 " : "") + Labels.creature(row.entity());
        if (row.variantName().isEmpty()) {
            return name;
        }
        // THE QUALIFIED INSTANCE ID, not the bare variant name: a variant can exist in global and in several
        // tiers at once, each its own instance.
        return name + " [" + Labels.instanceId(row.entity(),
                Labels.scopeIdOf(row.source(), listScopeId), row.variantName()) + "]";
    }

    /** The mobs set to Spawn as Special, read as the list rebuilds. */
    private java.util.Set<String> special = java.util.Set.of();
    /** The mobs whose category was edited, to "Creature (the game's: Ambient)". */
    private Map<String, String> moved = Map.of();

    /** What Fill from default would add here, planned once per init. */
    private List<MobOps.FillMob> fillPlan = List.of();

    /** SHOW GAME SPAWNS. */
    private static final java.util.Set<String> SHOWING_GAME = new java.util.HashSet<>();
    /** The mobs the game spawns in this scope, the grey rows' and Restore's source. */
    private java.util.Set<String> gameHere = java.util.Set.of();
    /** The grey rows: index in allRows to the game spawn it shows. */
    private Map<Integer, MobOps.FillMob> gameRows = Map.of();
    /**
     * On the Tiers and Biomes tabs, the tier or biome each fill row belongs to, in the order of fillPlan.
     */
    private List<String> fillScopes = List.of();
    /** A grey row on a grouped tab, to the tier or biome it is under. */
    private final Map<MobCatalog.MobEntry, String> greyScope = new java.util.IdentityHashMap<>();

    /** This scope's replacements, by the mob replaced: a row says what it became. */
    private Map<String, com.strata.domain.ReplacementOps.Row> replacedHere = Map.of();

    private boolean showingGame() {
        return SHOWING_GAME.contains(foldKey());
    }

    private String subtitle(MobCatalog.MobEntry row) {
        // A game mob's own row: its highest natural spawn weight, the structures it spawns in, or that it has
        // neither.
        String s = row.source().layer() != Source.Layer.GAME ? row.source().describe() + "  weight " + row.weight()
                : gameSpawns(row);
        if (GAME_HERE.equals(row.source().detail())) {
            // Off beside its variant where the game's spawns are replaced (a Replace tier): the base mob
            // starts from 0 there, so it reads as off.
            if (variantOffHere(row.entity())) {
                return "game spawn here  off: only its variant spawns  [disabled]";
            }
            return "game spawn here  weight " + row.weight() + "  - click to change it";
        }
        // A SPECIAL MOB: a tier or biome can turn it off.
        if (row.variantName().isEmpty() ? special.contains(row.entity())
                : com.strata.domain.SpecialSpawnsOps.special(row.entity(), row.variantName())) {
            s += "  [spawns as Special]";
        }
        // A MOVED MOB SAYS SO: it is filed under its new category's group.
        if (row.variantName().isEmpty() && moved.containsKey(row.entity())) s += "  [" + moved.get(row.entity()) + "]";
        if (row.variantName().isEmpty() && replacedHere.containsKey(row.entity())) {
            var r = replacedHere.get(row.entity());
            s += "  [-> " + Labels.creature(r.to()) + " " + (r.share().isBlank() ? "100" : r.share()) + "%]";
        }
        if (!row.enabled()) s += "  [disabled]";
        if (row.changed()) s += "  [edited]";
        return s;
    }

    /** Where a game mob spawns, with its weights: its biomes, then its structures. */
    private static String gameSpawns(MobCatalog.MobEntry row) {
        List<MobCatalog.StructurePlace> places = MobCatalog.structuresOf(row.entity());
        StringBuilder s = new StringBuilder("game  ");
        if (row.weight() > 0) {
            s.append("spawns naturally, weight up to ").append(row.weight());
        } else if (places.isEmpty()) {
            return "game  no natural spawn";
        }
        for (int i = 0; i < Math.min(2, places.size()); i++) {
            MobCatalog.StructurePlace p = places.get(i);
            s.append(i == 0 && row.weight() <= 0 ? "spawns in " : ", ")
                    .append(Labels.pretty(p.structure())).append(" weight ").append(p.weight());
        }
        if (places.size() > 2) {
            s.append(", +").append(places.size() - 2).append(" more");
        }
        return s.toString();
    }

    /** The grey rows' source detail: a natural spawn of this scope that the list has no row for. */
    private static final String GAME_HERE = "game spawn here";

    /** Writes the fill plan as rows: one scope's, or each tier's or biome's on a grouped tab. */
    private void fillList() {
        Map<String, List<MobOps.FillMob>> byScope = new java.util.LinkedHashMap<>();
        for (int i = 0; i < fillPlan.size(); i++) {
            String place = groupedByScope() ? fillScopes.get(i) : fixedScopeId;
            byScope.computeIfAbsent(place, k -> new ArrayList<>()).add(fillPlan.get(i));
        }
        for (Map.Entry<String, List<MobOps.FillMob>> e : byScope.entrySet()) {
            if (!report(MobOps.fillFromGame(fixedScope, e.getKey(), e.getValue()))) {
                break;
            }
        }
        rebuildWidgets();
    }

    /** True when this one tier or biome replaces the game's spawns and lists the mob only as a variant. */
    private boolean variantOffHere(String entity) {
        if (fixedScopeId.isBlank()) {
            return false;
        }
        List<String> biomes = "biome".equals(fixedScope) ? List.of(fixedScopeId)
                : com.strata.domain.TierOps.biomesIn(fixedScopeId);
        if (biomes.isEmpty() || !com.strata.data.Config.replacesGameSpawns(biomes.get(0))) {
            return false;
        }
        return allRows.stream().anyMatch(r -> r.entity().equals(entity) && !r.variantName().isEmpty());
    }

    private int color(MobCatalog.MobEntry row) {
        // A game row that is off here (only its variant spawns) is drawn as off, like any disabled row.
        if (GAME_HERE.equals(row.source().detail())) {
            return variantOffHere(row.entity()) ? Palette.MUTED : Palette.INHERITED;
        }
        if (!row.enabled()) return Palette.MUTED;
        if (row.source().layer() == Source.Layer.GAME) return Palette.INHERITED;
        if (row.source().layer() == Source.Layer.TIER) return Palette.INHERITED;
        return Palette.TEXT;
    }


    /**
     * @param scope null for global browser (from root), or "tier"/"biome" for scoped
     * @param scopeId the tier name or biome id when scoped
     */
    public MobListScreen(String scope, String scopeId) {
        // "GLOBAL" rather than "ALL", because it is not all of them.
        super(scope == null ? "Strata — Mobs: Global"
                : scopeId == null || scopeId.isBlank()
                        // A grouped view: a scope with no particular id named.
                        ? ("biome".equals(scope) ? "Strata — Biome mob spawns"
                                : "Strata — Tier mob spawns")
                        : "Strata — Mobs: " + Labels.scope(scope, scopeId));
        this.fixedScope = scope == null ? "" : scope;
        this.fixedScopeId = scopeId == null ? "" : scopeId;
        this.global = scope == null;
        this.isScoped = scope != null;
    }

    /** Folds per view: the Global view, each tier, and each biome. */
    @Override
    protected String foldKey() {
        return foldKey(getClass().getSimpleName(), fixedScope, fixedScopeId);
    }

    /** Pure, so the gate pins it. */
    public static String foldKey(String screen, String scope, String scopeId) {
        return scope.isEmpty() ? screen : screen + "|" + scope + "|" + scopeId;
    }

    @Override
    protected void init() {
        super.init();
        loadRows();
        // init runs again on every return here, after each edit.
        String keepSearch = search == null ? "" : search.getValue();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // STAT COLUMNS: the chosen stats, read before the layout, which sizes the columns from their
        // headings.
        glance = new GlanceHeaders(this, this.font, com.strata.domain.GlanceColumns.MOBS, this::rebuildWidgets);
        glance.load(com.strata.ui.screen.MobListLayout.columnRoom(frame()));
        baseStats.clear();
        // The layout is the UI layer's, as the fit gate checked it.
        List<String> tabIds = scopeTabs();
        // Fill from default is planned BEFORE the layout, because the layout greys the button when there is
        // nothing left to add - so the plan has to be known by the time the button's box is asked for.
        fillPlan = planFill();
        addGameRows();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        placeTools(layout, tabIds);
        placeListAndSearch(layout, keepSearch, keepList);
    }

    /** The top row and the bar: Edit picked, Mob caps, Special spawns, Goal sets, Add default mobs, Replacements, the scope tabs. */
    private void placeTools(com.strata.ui.Layout layout, List<String> tabIds) {
        // Batch: spawning or scaling for every passive or hostile mob here.
        placeAll(layout, new String[]{"batch"}, Button.builder(Component.literal("Edit picked"),
                b -> openBatch()).build());
        Button biomes = Button.builder(Component.literal("Biomes"), b -> {
            String entity = selectedEntity();
            if (entity != null) {
                ScreenNavigator.get().navigateTo(new MobBiomesScreen(entity));
            }
        }).build();
        placeAll(layout, new String[]{"biomes"}, biomes);
        needsSelection(() -> selectedEntity() != null, biomes);
        // Mob caps, Special spawns and Goal sets sit on the top row with the tabs.
        String[] special = specialScope();
        if (!isScoped) {
            placeAll(layout, new String[]{"mobCaps"}, Button.builder(Component.literal("Mob caps"),
                    b -> ScreenNavigator.get().navigateTo(new MobCapScreen())).build());
        }
        if (special != null) {
            placeAll(layout, new String[]{"special"}, Button.builder(Component.literal("Special spawns"),
                    b -> ScreenNavigator.get().navigateTo(new SpecialMobsScreen(special[0], special[1]))).build());
        }
        // Goal sets, moved here from the Client menu.
        if (!isScoped) {
            placeAll(layout, new String[]{"goalSets"}, Button.builder(Component.literal("Goal sets"),
                    b -> ScreenNavigator.get().navigateTo(new GoalSetsScreen())).build());
            // Data...
            placeDataMenu(layout, "data", com.strata.ui.screen.ListScreenLayouts.DATA_MENU,
                    java.util.Map.entry("Projectiles", ProjectileListScreen::new),
                    dataEntry(com.strata.domain.OverrideViews.MOB_VARIANTS),
                    // The mob tags beside the mobs.
                    dataEntry("Mob tags", com.strata.data.JsonOverrides.ENTITY_TAG),
                    dataEntry("Mob data maps", com.strata.data.JsonOverrides.ENTITY_DATA));
        }
        // Fill from default: a tier or biome's list, filled with the mobs the game spawns there that it does
        // not list yet.
        if (isScoped) {
            // FILLS THE LIST: every mob the game and other mods spawn here that the list has no row for
            // becomes a row, with the game's numbers.
            Button fill = Button.builder(Component.literal("Add default mobs"), b -> fillList()).build();
            fill.active = !fillPlan.isEmpty();
            fill.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(fillPlan.isEmpty()
                    ? "The list already has every mob the game spawns here"
                    : "Add the " + com.strata.ui.Words.count(fillPlan.size(), "mob", "mobs") + " the game and other mods spawn in this "
                            + (fixedScopeId.isBlank() ? "tier or biome" : fixedScope)
                            + " as rows, with the game's numbers. Undo takes them back.")));
            placeAll(layout, new String[]{"fill"}, fill);
        }
        if (isScoped && !fixedScopeId.isBlank()) {
            // ENTITY REPLACEMENT: this tier's or biome's, opened on the picked mob when one plain mob is
            // picked, so its row is the one replaced.
            Button replace = Button.builder(Component.literal("Replace mob"), b -> {
                List<MobOps.ScopedMob> picked = pickedMobs();
                String from = picked.size() == 1 && picked.getFirst().variantName().isEmpty()
                        ? picked.getFirst().entity() : "";
                ScreenNavigator.get().navigateTo(new ReplacementsScreen(fixedScope, fixedScopeId, from));
            }).build();
            replace.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Where this " + fixedScope + " would spawn one mob, spawn another. Pick a mob first to"
                            + " replace that one.")));
            placeAll(layout, new String[]{"replace"}, replace);
            replacedHere = fixedScopeId.isBlank() ? Map.of()
                    : com.strata.domain.ReplacementOps.byFrom(fixedScope, fixedScopeId);
        }

        // SCOPE TABS.
        for (int i = 0; i < tabIds.size(); i++) {
            String tabId = tabIds.get(i);
            // The tab you are already on is not a destination: the layout greys it.
            placeAll(layout, new String[]{"tab" + i}, Button.builder(Component.literal(tabLabel(tabId)),
                    b -> openTab(tabId)).build());
        }
    }

    /** The search, + Add mob, the list and its column headings, then the buttons that act on the picked rows. */
    private void placeListAndSearch(com.strata.ui.Layout layout, String keepSearch, BrowseList.State keepList) {
        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> rebuild());
        addRenderableWidget(place(search.getWidget(), layout.get("search")));


        // ADD MOB IS FOR THE SCOPED VIEWS ONLY.
        if (isScoped) {
            placeAll(layout, new String[]{"addMob"}, Button.builder(Component.literal("+ Add mob"),
                    b -> ScreenNavigator.get().navigateTo(new MobEditScreen(null, null,
                            fixedScope, fixedScopeId, true))).build());
        }

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22));
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, allRows, MobCatalog.MobEntry::entity));
        placeList(list, layout.get("list"));
        // ONE CLICK PICKS, A DOUBLE-CLICK OPENS.
        list.setOnSelect(entry -> {
            if (entry.id.startsWith(ADD_VARIANT_PREFIX)) {
                open(entry);
            }
        });
        list.setOnActivate(this::open);
        addRenderableWidget(list);
        if (isScoped) {
            // Remove the picked plain mobs from their tier or biome.
            removeButton = Button.builder(Component.literal(layout.textOf("remove")), b -> confirmRemove()).build();
            placeAll(layout, new String[]{"remove"}, removeButton);
        }

        // The headings are plain text over the columns; a click on one opens the picker.
        glance.layOut(list, layout, com.strata.ui.screen.MobListLayout.columnWidth(frame(), this.font::width,
                glance.headings()));
        rebuild();
        list.restore(keepList);
    }

    /** The scope's mobs, each in the category the designer set, not the one the registry reports. */
    private void loadRows() {
        allRows = MobCatalog.entries(fixedScope, fixedScopeId,
                this.minecraft != null && this.minecraft.level != null
                        ? worldRegistries() : null);
        // THE CATEGORY THE USER SET, not the one the game's registry still reports in this game.
        Map<String, String> setCategories = com.strata.domain.CategoryOps.editedCategories();
        Map<String, String> variantCategories = com.strata.domain.CategoryOps.editedVariantCategories();
        if (!setCategories.isEmpty() || !variantCategories.isEmpty()) {
            allRows = allRows.stream().map(r -> {
                // A variant's own category first, else its base mob's.
                String set = r.variantName().isEmpty() ? null
                        : variantCategories.get(r.entity() + "|" + r.variantName());
                if (set == null) {
                    set = setCategories.get(r.entity());
                }
                return set == null ? r : new MobCatalog.MobEntry(r.entity(), r.variantName(), r.weight(),
                        r.enabled(), r.source(), SpawnCategory.parse(set), r.changed());
            }).toList();
        }
    }

    /** The game's own spawns here that the list does not hold yet, as grey rows after the list's own. */
    private void addGameRows() {
        // The grey rows go after the list's own, so the list's own indexes do not move.
        gameRows = new java.util.HashMap<>();
        if (showingGame()) {
            List<MobCatalog.MobEntry> with = new ArrayList<>(allRows);
            greyScope.clear();
            for (int i = 0; i < fillPlan.size(); i++) {
                MobOps.FillMob m = fillPlan.get(i);
                gameRows.put(with.size(), m);
                MobCatalog.MobEntry grey = new MobCatalog.MobEntry(m.entity(), "", com.strata.data.Parse.integer(m.weight(), 0),
                        true, new Source(Source.Layer.GAME, GAME_HERE), SpawnCategory.parse(m.category()), false);
                if (groupedByScope()) {
                    greyScope.put(grey, fillScopes.get(i));
                }
                with.add(grey);
            }
            allRows = with;
        }
    }

    /** The stat columns' headings, in order. */
    private List<String> headings() {
        return glance.headings();
    }

    /** The layout for the rows as they stand. */
    private com.strata.ui.Layout layout() {
        List<String> tabIds = scopeTabs();
        return com.strata.ui.screen.MobListLayout.build(frame(), this.font::width, title(),
                tabIds.stream().map(this::tabLabel).toList(), tabIds.indexOf(activeTab()), isScoped, headings(),
                isScoped ? (MobOps.allResetToGame(removable(), gameHere) ? "Restore" : "Remove")
                        + " (" + removable().size() + ")" : null,
                !removable().isEmpty(), !isScoped, specialScope() != null, spawnRegistries() != null,
                !fixedScopeId.isBlank(), status,
                allRows.isEmpty() ? emptyMessage() : null);
    }

    /**
     * The scope Special mobs opens at: Global, or this one tier or biome; null on a grouped view that names
     * no tier or biome.
     */
    private String[] specialScope() {
        if (!isScoped) return new String[]{"global", ""};
        return fixedScopeId.isBlank() ? null : new String[]{fixedScope, fixedScopeId};
    }

    /**
     * The picked rows as mobs in their tier or biome: a plain mob's row and a variant's; "+ Add variant" and
     * group rows are not mobs.
     */
    /** The entity of the selected mob row, plain or variant; null for a group or an add row. */
    private String selectedEntity() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        if (sel == null || sel.groupId != null || sel.id.startsWith(ADD_VARIANT_PREFIX)) {
            return null;
        }
        String id = sel.id.startsWith(VARIANT_PREFIX) ? sel.id.substring(VARIANT_PREFIX.length()) : sel.id;
        try {
            return allRows.get(Integer.parseInt(id)).entity();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<MobOps.ScopedMob> pickedMobs() {
        List<MobOps.ScopedMob> out = new ArrayList<>();
        if (list == null || !isScoped) {
            return out;
        }
        for (String id : list.pickedIds()) {
            if (id.startsWith(ADD_VARIANT_PREFIX) || id.startsWith("cat:")
                    || gameRows.containsKey(Integer.parseInt(id.startsWith(VARIANT_PREFIX) ? id.substring(VARIANT_PREFIX.length()) : id))) {
                // A grey game row has no row here to remove or reset.
                continue;
            }
            MobCatalog.MobEntry row = allRows.get(Integer.parseInt(
                    id.startsWith(VARIANT_PREFIX) ? id.substring(VARIANT_PREFIX.length()) : id));
            out.add(new MobOps.ScopedMob(fixedScope, rowScopeId(row), row.entity(), row.variantName()));
        }
        return out;
    }

    /** The picked plain mobs Remove takes. */
    private List<MobOps.ScopedMob> removable() {
        return MobOps.splitRemovable(pickedMobs()).get(true);
    }

    /** FILL FROM DEFAULT: the mobs the GAME spawns in this scope that the scope does not list yet. */
    private List<MobOps.FillMob> planFill() {
        fillScopes = List.of();
        var registries = spawnRegistries();
        if (registries == null) {
            return List.of();
        }
        if (groupedByScope()) {
            // THE TIERS AND BIOMES TABS: every tier's or biome's plan, each row remembering its place.
            List<String> places = allBiomesView()
                    ? com.strata.game.BiomeCatalog.all(registries).stream().map(com.strata.game.BiomeCatalog.Entry::id).toList()
                    : com.strata.domain.TierOps.names();
            List<java.util.Map<String, String>> table = com.strata.data.CsvTable.read(com.strata.data.Paths.table(
                    com.strata.data.Schema.MobSpawns.FILE));
            List<MobOps.FillMob> all = new ArrayList<>();
            List<String> scopes = new ArrayList<>();
            java.util.Set<String> here = new java.util.HashSet<>();
            for (String place : places) {
                var union = MobOps.unionSpawns(spawnsOf(registries, fixedScope, place));
                union.forEach(e -> here.add(e.entity().toString()));
                for (MobOps.FillMob m : MobOps.fillPlan(fixedScope, place,
                        MobOps.listedInScope(fixedScope, place, table), union)) {
                    all.add(m);
                    scopes.add(place);
                }
            }
            gameHere = here;
            fillScopes = scopes;
            return all;
        }
        if (!MobOps.removableFromScope(fixedScope, fixedScopeId)) {
            return List.of();
        }
        List<List<com.strata.game.GameData.SpawnEntry>> perBiome = new ArrayList<>();
        if ("biome".equals(fixedScope)) {
            perBiome.add(com.strata.game.GameData.mobsIn(registries,
                    net.minecraft.resources.ResourceLocation.tryParse(fixedScopeId)));
        } else {
            for (String biomeId : com.strata.domain.TierOps.biomesIn(fixedScopeId)) {
                perBiome.add(com.strata.game.GameData.mobsIn(registries,
                        net.minecraft.resources.ResourceLocation.tryParse(biomeId)));
            }
        }
        var union = MobOps.unionSpawns(perBiome);
        gameHere = union.stream().map(e -> e.entity().toString())
                .collect(java.util.stream.Collectors.toSet());
        return MobOps.fillPlan(fixedScope, fixedScopeId,
                MobOps.listedInScope(fixedScope, fixedScopeId,
                        com.strata.data.CsvTable.read(com.strata.data.Paths.table(
                                com.strata.data.Schema.MobSpawns.FILE))),
                union);
    }

    /**
     * The registries the game's spawns are read from: the open world's, else the ones built at game start, so
     * Default mobs works from the title screen too.
     */
    private net.minecraft.core.RegistryAccess spawnRegistries() {
        var world = worldRegistries();
        return world != null ? world : com.strata.data.Config.startupRegistries();
    }

    /** One biome's natural spawns, or each biome's of a tier. */
    private static List<List<com.strata.game.GameData.SpawnEntry>> spawnsOf(net.minecraft.core.RegistryAccess registries,
                                                                          String scope, String id) {
        List<List<com.strata.game.GameData.SpawnEntry>> perBiome = new ArrayList<>();
        List<String> biomes = "biome".equals(scope) ? List.of(id) : com.strata.domain.TierOps.biomesIn(id);
        for (String biomeId : biomes) {
            perBiome.add(com.strata.game.GameData.mobsIn(registries, net.minecraft.resources.ResourceLocation.tryParse(biomeId)));
        }
        return perBiome;
    }

    /**
     * Removes the picked plain mobs from their tier or biome after a confirmation that names them, and the
     * variants it leaves alone.
     */
    private void confirmRemove() {
        var split = MobOps.splitRemovable(pickedMobs());
        List<MobOps.ScopedMob> go = split.get(true);
        if (go.isEmpty()) {
            return;
        }
        // ONE MOB GOES AT ONCE; SEVERAL ASK.
        if (go.size() == 1 && split.get(false).isEmpty()) {
            if (report(MobOps.removeAllFromScope(go))) {
                com.strata.client.util.RecentEdits.touch(go.getFirst().entity(), "");
                rebuildWidgets();
            }
            return;
        }
        boolean reset = MobOps.allResetToGame(go, gameHere);
        StringBuilder text = new StringBuilder(reset
                ? "Reset " + com.strata.ui.Words.count(go.size(), "mob", "mobs") + " to the game's own spawn here?\n"
                : "Remove " + com.strata.ui.Words.count(go.size(), "mob", "mobs") + " from where they spawn?\n")
                .append(com.strata.domain.Picks.listed(go.stream().map(m -> Labels.creature(m.entity()) + " (" + m.scopeId() + ")").toList()))
                .append(reset ? "\nThe game's weight and group size come back, and their loot and spawn rules"
                        + " here go." : "\nTheir loot and spawn rules there go too.");
        if (!split.get(false).isEmpty()) {
            text.append("\nLeft alone (a variant is removed in its editor): ")
                    .append(com.strata.domain.Picks.listed(split.get(false).stream().map(MobOps.ScopedMob::variantName).toList()));
        }
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, text.toString(),
                // The dialog returns here, and init reads the list again.
                () -> {
                    if (report(MobOps.removeAllFromScope(go))) {
                        go.forEach(m -> com.strata.client.util.RecentEdits.touch(m.entity(), ""));
                    }
                }));
    }

    /** BATCH: the Batch screen, for the mobs this list shows. */
    /** Opens a row: its editor, or a new variant of its mob for "+ Add variant". */
    private void open(BrowseList.Entry entry) {
        if (entry.id.startsWith("cat:") || entry.groupId != null) {
            return;
        }
        if (entry.id.startsWith(ADD_VARIANT_PREFIX)) {
            int idx = Integer.parseInt(entry.id.substring(ADD_VARIANT_PREFIX.length()));
            MobCatalog.MobEntry base = allRows.get(idx);
            ScreenNavigator.get().navigateTo(new MobEditScreen(null, base.entity(),
                    isScoped ? fixedScope : "global",
                    isScoped ? rowScopeId(base) : ""));
            return;
        }
        if (entry.id.startsWith(VARIANT_PREFIX)) {
            int vidx = Integer.parseInt(entry.id.substring(VARIANT_PREFIX.length()));
            MobCatalog.MobEntry vr = allRows.get(vidx);
            // Thread the list's own scope so deleting an existing variant opened from a tier removes ONLY
            // that tier's spawn row, never the global mobs-list entry or the global variant definition.
            ScreenNavigator.get().navigateTo(new MobEditScreen(vr.variantName(),
                    null, isScoped ? fixedScope : null, isScoped ? rowScopeId(vr) : null));
            return;
        }
        int idx = Integer.parseInt(entry.id);
        MobCatalog.MobEntry row = allRows.get(idx);
        // A GREY ROW IS TAKEN OVER: its game numbers become this list's own row, then the editor opens on it.
        MobOps.FillMob game = gameRows.get(idx);
        if (game != null) {
            if (!report(MobOps.fillFromGame(fixedScope, rowScopeId(row), List.of(game)))) {
                return;
            }
            com.strata.client.util.RecentEdits.touch(game.entity(), "");
        }
        // ONE EDITOR.
        boolean base = row.variantName().isEmpty();
        ScreenNavigator.get().navigateTo(new MobEditScreen(
                base ? null : row.variantName(), row.entity(),
                isScoped ? fixedScope : "global",
                isScoped ? rowScopeId(row) : "",
                base));
    }

    /** The mob rows picked in the list (plain and variant; not group headers, add slots or grey game rows). */
    private List<MobCatalog.MobEntry> pickedRows() {
        List<MobCatalog.MobEntry> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (String id : list.pickedIds()) {
            if (id.startsWith(ADD_VARIANT_PREFIX) || id.startsWith("cat:")) {
                continue;
            }
            int idx = Integer.parseInt(id.startsWith(VARIANT_PREFIX) ? id.substring(VARIANT_PREFIX.length()) : id);
            if (idx >= 0 && idx < allRows.size() && !gameRows.containsKey(idx)) {
                out.add(allRows.get(idx));
            }
        }
        return out;
    }

    /** Edit picked: the picked mobs (Ctrl / Shift click), or every mob in this view when none is picked. */
    private void openBatch() {
        List<MobCatalog.MobEntry> picked = pickedRows();
        if (picked.size() == 1 && list.getSelected() != null) {
            open(list.getSelected());
            return;
        }
        List<MobOps.BatchMob> mobs = new ArrayList<>();
        for (MobCatalog.MobEntry row : picked.isEmpty() ? allRows : picked) {
            mobs.add(new MobOps.BatchMob(isScoped ? fixedScope : "global", isScoped ? rowScopeId(row) : "",
                    row.entity(), row.variantName(), row.category()));
        }
        ScreenNavigator.get().navigateTo(new MobBatchScreen(mobs,
                isScoped ? Labels.scope(fixedScope, fixedScopeId) : "Global"));
    }



    /** Remove, on a tier or biome view. */
    private Button removeButton;

    /** The at-a-glance columns: which show, the Columns... */
    private GlanceHeaders glance;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !choicesOpen() && glance != null && list != null && glance.click(list, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Each creature's base stats, read once per visit. */
    private final Map<String, Map<String, Double>> baseStats = new java.util.HashMap<>();

    /** The tier or biome a row lives in. */
    private String rowScopeId(MobCatalog.MobEntry row) {
        String grey = greyScope.get(row);
        if (grey != null) {
            return grey;
        }
        return fixedScopeId.isEmpty()
                ? Labels.scopeIdOf(row.source(), "")
                : fixedScopeId;
    }

    /** True when this is the all-tiers view: tier scope, no particular tier. */
    private boolean allTiersView() {
        return "tier".equals(fixedScope) && fixedScopeId.isEmpty();
    }

    /** True when this is the all-biomes view: biome scope, no particular biome. */
    private boolean allBiomesView() {
        return "biome".equals(fixedScope) && fixedScopeId.isEmpty();
    }

    /** Either of the grouped views - the list groups by scope, not category. */
    private boolean groupedByScope() {
        return allTiersView() || allBiomesView();
    }

    /** The tab this screen is currently showing. */
    private String activeTab() {
        if (!isScoped) {
            return GLOBAL_TAB;
        }
        if ("biome".equals(fixedScope)) {
            return BIOME_TAB;
        }
        return TIERS_TAB;
    }

    private static final String GLOBAL_TAB = "global";
    private static final String BIOME_TAB = "biomes";
    private static final String TIERS_TAB = "tiers";

    /** Group header for mobs worked on this session. */
    private static final String RECENT_GROUP = "Recent";

    /** The three tabs: Global, Tiers, Biomes. */
    private List<String> scopeTabs() {
        return List.of(GLOBAL_TAB, TIERS_TAB, BIOME_TAB);
    }

    private String tabLabel(String tabId) {
        if (GLOBAL_TAB.equals(tabId)) {
            return "Global";
        }
        if (BIOME_TAB.equals(tabId)) {
            return "Biomes";
        }
        return "Tiers";
    }

    private void openTab(String tabId) {
        if (TIERS_TAB.equals(tabId)) {
            // Switches the LIST to tier groups.
            swapKeepingSection(new MobListScreen("tier", ""));
            return;
        }
        if (GLOBAL_TAB.equals(tabId)) {
            swapKeepingSection(new MobListScreen(null, null));
            return;
        }
        if (BIOME_TAB.equals(tabId)) {
            // Same shape as Tiers: a VIEW of this list whose groups are biomes.
            swapKeepingSection(new MobListScreen("biome", ""));
        }
    }

    /** A row's section. */
    public static String groupOf(boolean groupedByScope, boolean recent, MobCatalog.MobEntry row) {
        return groupOf(groupedByScope, recent, false, row);
    }

    /**
     * As above, with the mob's Special flag: in the Global view a flagged mob and its variants sit under
     * Special.
     */
    public static String groupOf(boolean groupedByScope, boolean recent, boolean special, MobCatalog.MobEntry row) {
        if (groupedByScope) {
            return scopeGroup(row);
        }
        if (recent) {
            return RECENT_GROUP;
        }
        if (special) {
            return SPECIAL_GROUP;
        }
        return row.category() != null ? Labels.category(row.category()) : "Other";
    }

    /** The Global view's group of the mobs flagged Special, after Recent and before every category. */
    public static final String SPECIAL_GROUP = "Special";

    /**
     * A row's section in the Tiers and Biomes views: its tier's name as the designer wrote it, or its biome's
     * name in words.
     */
    private static String scopeGroup(MobCatalog.MobEntry row) {
        String detail = row.source().detail();
        if (detail == null || detail.isBlank()) {
            return "Other";
        }
        return row.source().layer() == Source.Layer.BIOME ? Labels.biome(detail) : "Tier: " + detail;
    }

    /** Row-id prefix marking a standalone variant row. */
    public static final String VARIANT_PREFIX = "var:";
    /** Row-id prefix marking the per-mob "Add variant" slot. */
    public static final String ADD_VARIANT_PREFIX = "addvar:";

    /**
     * The row-id plan for ONE base-entity group — a PURE function of the group's shape and the browse
     * context.
     */
    public static List<String> planGroupRowIds(boolean hasBase, int baseIndex,
                                               List<Integer> variantIndexes) {
        List<String> ids = new ArrayList<>();
        if (hasBase) {
            ids.add(String.valueOf(baseIndex));
        }
        for (int vi : variantIndexes) {
            ids.add(VARIANT_PREFIX + vi);
        }
        if (hasBase) {
            ids.add(ADD_VARIANT_PREFIX + baseIndex);
        }
        return List.copyOf(ids);
    }

    private void rebuild() {
        List<MobCatalog.MobEntry> filtered = filteredRows();
        Map<String, List<MobCatalog.MobEntry>> byCategory = groupRows(filtered);
        List<BrowseList.Entry> entries = new ArrayList<>();
        special = com.strata.domain.SpecialSpawnsOps.specialMobs();
        moved = com.strata.domain.CategoryOps.moved();
        byCategory.values().removeIf(List::isEmpty);
        // A recent mob is filed twice under one id: count it once.
        java.util.Set<String> countedIds = new java.util.HashSet<>();
        for (var catEntry : byCategory.entrySet()) {
            addGroup(entries, catEntry.getKey(), catEntry.getValue(), countedIds);
        }
        // A search's matches show unfolded.
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
        status = com.strata.ui.Words.count(countedIds.size(), "spawn entry", "spawn entries") + ". Click a mob to open it.";
    }

    /** The rows the search matches, by category, then by name. */
    private List<MobCatalog.MobEntry> filteredRows() {
        String text = search == null ? "" : search.getValue();

        List<MobCatalog.MobEntry> filtered = new ArrayList<>();
        for (MobCatalog.MobEntry row : allRows) {
            // The name on the row as well as the id.
            if (!com.strata.ui.Search.matches(text, row.entity(), row.variantName(),
                    displayName(row, fixedScopeId))) continue;
            filtered.add(row);
        }

        filtered.sort((a, b) -> {
            int ca = a.category() == null ? SpawnCategory.values().length : a.category().ordinal();
            int cb = b.category() == null ? SpawnCategory.values().length : b.category().ordinal();
            if (ca != cb) return Integer.compare(ca, cb);
            return displayName(a, fixedScopeId).compareToIgnoreCase(displayName(b, fixedScopeId));
        });
        return filtered;
    }

    /** The rows by group: Recent first, then Special, then each category, tier or biome. */
    private Map<String, List<MobCatalog.MobEntry>> groupRows(List<MobCatalog.MobEntry> filtered) {
        // Seeded so Recent is the FIRST key in the insertion-ordered map, whatever order the rows arrive in.
        Map<String, List<MobCatalog.MobEntry>> byCategory = new java.util.LinkedHashMap<>();
        byCategory.put(RECENT_GROUP, new ArrayList<>());
        // Special right after Recent; dropped below when empty, as Recent is.
        byCategory.put(SPECIAL_GROUP, new ArrayList<>());
        java.util.Set<String> flagged = com.strata.domain.SpecialSpawnsOps.flaggedMobs();
        for (MobCatalog.MobEntry row : filtered) {
            // IN THE TIERS VIEW THE GROUPS ARE TIERS, not categories - the same collapsible shape the Global
            // view uses for Hostile and Passive, just grouped by the thing the designer came here for.
            boolean inSpecial = flagged.contains(row.entity()) && (row.variantName().isEmpty()
                    || com.strata.domain.SpecialSpawnsOps.special(row.entity(), row.variantName()));
            // A grey row on a grouped tab goes under its own tier or biome.
            String greyAt = greyScope.get(row);
            MobCatalog.MobEntry grouped = greyAt == null ? row : new MobCatalog.MobEntry(row.entity(), row.variantName(),
                    row.weight(), row.enabled(), new Source("biome".equals(fixedScope) ? Source.Layer.BIOME
                            : Source.Layer.TIER, greyAt), row.category(), false);
            String catName = groupOf(groupedByScope(),
                    com.strata.client.util.RecentEdits.contains(row.entity(), row.variantName()),
                    inSpecial, grouped);
            byCategory.computeIfAbsent(catName, k -> new ArrayList<>()).add(row);
            if (RECENT_GROUP.equals(catName)) {
                byCategory.computeIfAbsent(groupOf(false, false, inSpecial, row),
                        k -> new ArrayList<>()).add(row);
            }
        }

        // NEWEST FIRST, as promised: RecentEdits keeps a ranked session order, and this group used to fall
        // back to the alphabetical sort above.
        if (groupedByScope()) {
            byCategory.values().forEach(rows -> rows.sort(java.util.Comparator.comparingInt(r -> {
                int rank = com.strata.client.util.RecentEdits.rank(r.entity(), r.variantName());
                return rank < 0 ? Integer.MAX_VALUE : rank;
            })));
        }
        List<MobCatalog.MobEntry> recent = byCategory.get(RECENT_GROUP);
        if (recent != null && recent.size() > 1) {
            recent.sort((a, b) -> {
                int ra = com.strata.client.util.RecentEdits.rank(a.entity(), a.variantName());
                int rb = com.strata.client.util.RecentEdits.rank(b.entity(), b.variantName());
                return Integer.compare(ra, rb);
            });
        }
        return byCategory;
    }

    /** One group's heading, then each base mob, its variants and its + Add variant row. */
    private void addGroup(List<BrowseList.Entry> entries, String catName, List<MobCatalog.MobEntry> rows,
                          java.util.Set<String> countedIds) {
        String groupId = "cat:" + catName;
        entries.add(new BrowseList.Entry(groupId,
                catName + " (" + rows.size() + ")", "", Palette.GROUP,
                groupId, null, 0));

        java.util.LinkedHashMap<String, List<MobCatalog.MobEntry>> byBase = new java.util.LinkedHashMap<>();
        for (MobCatalog.MobEntry row : rows) {
            byBase.computeIfAbsent(row.entity(), k -> new ArrayList<>()).add(row);
        }

        for (var baseEntry : byBase.entrySet()) {
            List<MobCatalog.MobEntry> variants = baseEntry.getValue();
            MobCatalog.MobEntry base = null;
            List<MobCatalog.MobEntry> variantRows = new ArrayList<>();
            for (MobCatalog.MobEntry r : variants) {
                if (r.variantName().isEmpty()) base = r;
                else variantRows.add(r);
            }

            List<Integer> variantIndexes = new ArrayList<>();
            for (MobCatalog.MobEntry vr : variantRows) {
                variantIndexes.add(allRows.indexOf(vr));
            }

            for (String id : planGroupRowIds(base != null,
                    base == null ? -1 : allRows.indexOf(base),
                    variantIndexes)) {
                if (id.startsWith(ADD_VARIANT_PREFIX)) {
                    entries.add(new BrowseList.Entry(id,
                            "+ Add variant", "", Palette.MUTED,
                            null, groupId, 2));
                    continue;
                }
                boolean isVariantRow = id.startsWith(VARIANT_PREFIX);
                MobCatalog.MobEntry row = allRows.get(Integer.parseInt(isVariantRow
                        ? id.substring(VARIANT_PREFIX.length()) : id));
                entries.add(new BrowseList.Entry(id,
                        displayName(row, fixedScopeId), subtitle(row), color(row),
                        null, groupId, isVariantRow ? 2 : 1)
                        .columns(StatColumns.valuesFor(glance.shownKeys(), row.entity(), row.variantName(),
                                baseStats)));
                countedIds.add(id);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        if (removeButton != null && layout.get("remove") != null) {
            removeButton.setMessage(Component.literal(layout.textOf("remove")));
            removeButton.active = layout.activeOf("remove");
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        glance.render(graphics, list, layout, mouseX, mouseY);
        titles(graphics, layout);
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }

    /** What to do next when the list is empty. */
    private String emptyMessage() {
        if (!isScoped) {
            return "No mobs: the game data could not be read at game start (see the log)";
        }
        if ("tier".equals(fixedScope)) {
            return fixedScopeId.isEmpty()
                    ? "No tier has mobs yet — use + Add mob to add one"
                    : "No mobs in this tier yet — use + Add mob to add one";
        }
        return "No mobs of your own in this biome yet — use + Add mob";
    }
}

