package com.strata.ui;

import java.util.Map;

/**
 * SCREEN TITLES AS A PATH FROM THE ROOT: "Strata / World / Biomes / Plains", "Strata / Items / Diamond
 * Sword".
 */
public final class TitlePath {

    private TitlePath() {
    }

    public static final String STRATA = "Strata";
    public static final String SEP = " / ";
    public static final String WORLD = "World";
    public static final String ITEMS = "Items";
    public static final String BLOCKS = "Blocks";
    public static final String OPTIONS = "Options";
    public static final String HELP = "Help";
    public static final String PROFILES = "Profiles";
    public static final String SCALING = "Scaling";

    private static final String SHORT_PREFIX = "Strata — ";
    private static final String PATH_PREFIX = STRATA + SEP;

    /** A section named in the singular reads in the plural in a path ("Mob: Zombie" is Mobs / Zombie). */
    private static final Map<String, String> PLURAL = Map.of("Mob", "Mobs", "Biome", "Biomes", "Item", "Items",
            "Block", "Blocks", "Tier", "Tiers");

    /** Each screen class's sub-root. */
    private static final Map<String, String> ROOTS = Map.ofEntries(
            // World: tiers, biomes, mobs and everything that hangs off a mob, loot, chests, rule lists.
            Map.entry("TierListScreen", WORLD), Map.entry("TierEditScreen", WORLD),
            Map.entry("ProjectileListScreen", WORLD), Map.entry("ProjectileEditScreen", WORLD),
            Map.entry("RulesScreen", WORLD),
            // The Options section: what a player rarely touches.
            Map.entry("WorldSettingsScreen", OPTIONS),
            Map.entry("BiomeListScreen", WORLD), Map.entry("BiomeEditScreen", WORLD),
            Map.entry("BiomeDataScreen", WORLD), Map.entry("MobListScreen", WORLD), Map.entry("MobEditScreen", WORLD),
            Map.entry("MobDataScreen", WORLD), Map.entry("MobBatchScreen", WORLD), Map.entry("GoalSetsScreen", WORLD),
            Map.entry("GoalsScreen", WORLD), Map.entry("GoalScreen", WORLD), Map.entry("AbilitiesScreen", WORLD),
            Map.entry("EquipmentScreen", WORLD), Map.entry("EquipmentItemScreen", WORLD),
            Map.entry("LootScreen", WORLD), Map.entry("LootEntryScreen", WORLD), Map.entry("LootTableScreen", WORLD),
            Map.entry("TradesScreen", WORLD), Map.entry("TradeEntryScreen", WORLD),
            Map.entry("SpawnRulesScreen", WORLD), Map.entry("SpecialMobsScreen", WORLD),
            Map.entry("SpecialSpawnsScreen", WORLD), Map.entry("ReplacementsScreen", WORLD),
            // Loot tables and Chest loot are Items.
            Map.entry("ChestsScreen", ITEMS), Map.entry("ChestLootScreen", ITEMS),
            Map.entry("ChestEntryScreen", ITEMS), Map.entry("ChestPoolScreen", ITEMS),
            // Items and Blocks.
            Map.entry("ItemsScreen", ITEMS), Map.entry("ItemStatScreen", ITEMS), Map.entry("ItemVariantScreen", ITEMS),
            Map.entry("ItemVariantStatsScreen", ITEMS),
            Map.entry("BlockStatsScreen", BLOCKS), Map.entry("BlockStatScreen", BLOCKS),
            Map.entry("BlockVariantScreen", BLOCKS),
            // Ores, Vegetation and block replacement are Blocks.
            Map.entry("OreListScreen", BLOCKS), Map.entry("VegetationScreen", BLOCKS),
            Map.entry("BlockSwapScreen", BLOCKS),
            // The top bar (Profiles, Scaling, Options, ?), global.
            Map.entry("ProfileScreen", PROFILES), Map.entry("ScalingScreen", SCALING), Map.entry("ScalingEditScreen", SCALING), Map.entry("FormulasScreen", SCALING), Map.entry("MobBiomesScreen", WORLD),
            Map.entry("StrataOptionsScreen", OPTIONS), Map.entry("TestModeScreen", OPTIONS),
            // Mob caps is opened from the Mobs list.
            Map.entry("MobCapScreen", WORLD),
            Map.entry("PlayerBaseStatsScreen", OPTIONS), Map.entry("PlayerSpawnScreen", OPTIONS),
            // Custom textures & sounds: a machine-wide library in Options.
            Map.entry("CustomAssetsScreen", OPTIONS), Map.entry("CustomAssetScreen", OPTIONS),
            Map.entry("AssetMasterScreen", OPTIONS),
            Map.entry("CustomStructuresScreen", WORLD), Map.entry("CustomStructureScreen", WORLD),
            Map.entry("HelpScreen", HELP));

    /** A screen class's sub-root, or null (the main menu, and a generic screen that takes its caller's). */
    public static String rootOfClass(String simpleName) {
        return ROOTS.get(simpleName);
    }

    /** A kind's sub-root, from its id. */
    public static String rootOfKind(String kindId) {
        return switch (kindId == null ? "" : kindId) {
            case "recipe", "brewing", "enchantment", "enchantment_provider", "trim_material", "trim_pattern",
                    "banner_pattern", "jukebox_song", "painting_variant", "item_tag", "item_data", "enchantment_tag",
                    "banner_pattern_tag", "instrument_tag", "painting_variant_tag" -> ITEMS;
            case "block_tag", "block_data", "fluid_tag", "game_event_tag", "poi_tag" -> BLOCKS;
            case "game_rule", "damage_type", "damage_tag", "advancement", "chat_type", "world_preset", "flat_preset",
                    "world_preset_tag", "flat_preset_tag" -> OPTIONS;
            default -> WORLD;
        };
    }

    /** The path for a screen's own name under a sub-root. */
    public static String compose(String root, String name) {
        if (name == null || name.equals(STRATA) || name.startsWith(PATH_PREFIX)) {
            return name;
        }
        String rest = name.startsWith(SHORT_PREFIX) ? name.substring(SHORT_PREFIX.length()) : name;
        String head = rest;
        String tail = null;
        int colon = rest.indexOf(": ");
        if (colon > 0) {
            head = rest.substring(0, colon);
            tail = rest.substring(colon + 2);
        }
        head = PLURAL.getOrDefault(head, head);
        StringBuilder path = new StringBuilder(STRATA);
        if (root != null) {
            path.append(SEP).append(root);
        }
        if (!head.equals(root)) {
            path.append(SEP).append(head);
        }
        if (tail != null && !tail.isEmpty()) {
            path.append(SEP).append(tail);
        }
        return path.toString();
    }

    /**
     * A screen's path as a BREADCRUMB of where it was opened from: the opener's path, then the segments of
     * the screen's own path the opener does not already say.
     */
    public static String crumb(String parent, String own) {
        if (parent == null || own == null || !parent.startsWith(PATH_PREFIX) || !own.startsWith(PATH_PREFIX)) {
            return own;
        }
        String[] p = parent.split(SEP);
        String[] o = own.split(SEP);
        java.util.Set<String> said = new java.util.HashSet<>();
        for (String s : p) {
            for (String l : words(s)) {
                said.add(l);
                said.add(l.endsWith("s") ? l.substring(0, l.length() - 1) : l + "s");
            }
        }
        StringBuilder out = new StringBuilder(parent);
        boolean added = false;
        // o[0] is Strata, and o[1] the own sub-root when it has one: the opener's path stands for both.
        int start = o.length > 1 && java.util.Set.of(WORLD, ITEMS, BLOCKS, OPTIONS, HELP, PROFILES, SCALING).contains(o[1]) ? 2 : 1;
        for (int i = start; i < o.length; i++) {
            if (!said.containsAll(words(o[i]))) {
                out.append(SEP).append(o[i]);
                added = true;
            }
        }
        return added ? out.toString() : own;
    }

    /** A segment's words, lower case: "Tier: T1" is [tier, t1]. */
    private static java.util.List<String> words(String segment) {
        return java.util.Arrays.stream(segment.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9_]+"))
                .filter(w -> !w.isEmpty()).toList();
    }

    /** The text after the last " / ": the screen's own name, without its path. */
    public static String last(String path) {
        int i = path.lastIndexOf(SEP);
        return i < 0 ? path : path.substring(i + SEP.length());
    }

    /** A path cut to a room KEEPING ITS END, the screen the person is on: "... */
    public static String clipKeepEnd(String path, int room, TextFit.Measure m) {
        if (path == null || m.width(path) <= room) {
            return path;
        }
        // Whole segments from the end while they fit behind the ellipsis.
        String lead = TextFit.ELLIPSIS + SEP;
        String[] parts = path.split(" / ");
        String kept = "";
        for (int i = parts.length - 1; i >= 0; i--) {
            String next = kept.isEmpty() ? parts[i] : parts[i] + SEP + kept;
            if (m.width(lead + next) > room) {
                break;
            }
            kept = next;
        }
        if (!kept.isEmpty()) {
            return lead + kept;
        }
        // Even the last segment is too long: its end, cut character by character.
        String last = parts[parts.length - 1];
        int start = 0;
        while (start < last.length() && m.width(TextFit.ELLIPSIS + last.substring(start)) > room) {
            start++;
        }
        return TextFit.ELLIPSIS + last.substring(Math.min(start, last.length()));
    }
}
