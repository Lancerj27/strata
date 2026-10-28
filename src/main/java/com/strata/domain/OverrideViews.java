package com.strata.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strata.data.JsonOverrides;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * THE VIEWS OF THE OVERRIDE LIST: one screen can show more than one kind of datapack JSON, and group its rows
 * by something other than a namespace.
 */
public final class OverrideViews {

    private OverrideViews() {
    }

    /** What a view groups its rows by. */
    public enum Grouping { NAMESPACE, STRUCTURE_TYPE, BIOME, KIND, MOB_THEME }

    /**
     * One view.
     *
     * @param id the view's name, for the fold memory
     * @param title the screen's name ("Structures")
     * @param kind the main kind
     * @param also the kinds listed with it, as further groups
     * @param grouping how the rows are grouped
     * @param sideLabel the button that opens the selected row's companion entry ("Spacing"), or null
     * @param groupTitles a group's name for a kind, shorter than the kind's own label ("Dimension types" for
     *     "Dimension types (height, light, ceiling, beds)"), by default the kind's own label
     */
    public record View(String id, String title, JsonOverrides.Kind kind, List<JsonOverrides.Kind> also,
                       Grouping grouping, String sideLabel, Map<JsonOverrides.Kind, String> groupTitles) {

        public View(String id, String title, JsonOverrides.Kind kind, List<JsonOverrides.Kind> also,
                    Grouping grouping, String sideLabel) {
            this(id, title, kind, also, grouping, sideLabel, Map.of());
        }

        /** A kind's group in this view: the view's short name for it, or the kind's own words. */
        public String groupTitle(JsonOverrides.Kind k) {
            String t = groupTitles.get(k);
            return t == null ? k.label() : t;
        }
    }

    /**
     * The structure list: grouped by type, the spacing of the selected one a button away, and the pieces,
     * weathering, modifiers and tags of structures as further groups.
     */
    public static final View STRUCTURES = new View("structures", "Structures", JsonOverrides.STRUCTURE,
            List.of(JsonOverrides.STRUCTURE_SET, JsonOverrides.TEMPLATE_POOL, JsonOverrides.PROCESSOR_LIST,
                    JsonOverrides.STRUCTURE_MODIFIER, JsonOverrides.STRUCTURE_TAG),
            Grouping.STRUCTURE_TYPE, "Spacing",
            Map.of(JsonOverrides.TEMPLATE_POOL, "Structure pieces", JsonOverrides.PROCESSOR_LIST, "Structure weathering",
                    JsonOverrides.STRUCTURE_MODIFIER, "Structure modifiers", JsonOverrides.STRUCTURE_TAG, "Structure tags"));
    /** The feature list: grouped by biome, the settings of the selected one a button away. */
    public static final View FEATURES = new View("features", "Features", JsonOverrides.PLACED_FEATURE,
            List.of(JsonOverrides.CONFIGURED_FEATURE), Grouping.BIOME, "Settings");
    /** The damage types, and their tags beside them. */
    public static final View DAMAGE_TYPES = new View("damage_types", "Damage types", JsonOverrides.DAMAGE_TYPE,
            List.of(JsonOverrides.DAMAGE_TAG), Grouping.KIND, null);
    /** Dimensions: the game's dimension types and a mod's or pack's own dimensions. */
    public static final View DIMENSIONS = new View("dimensions", "Dimensions", JsonOverrides.DIMENSION_TYPE,
            List.of(JsonOverrides.DIMENSION), Grouping.KIND, null,
            Map.of(JsonOverrides.DIMENSION_TYPE, "Dimension types", JsonOverrides.DIMENSION, "Added dimensions"));
    /**
     * Terrain: how the land is made - sea level and surface, its shape and noises, and the
     * caves and canyons carved into it.
     */
    public static final View TERRAIN = new View("terrain", "Terrain", JsonOverrides.NOISE_SETTINGS,
            List.of(JsonOverrides.DENSITY_FUNCTION, JsonOverrides.NOISE, JsonOverrides.CARVER),
            Grouping.KIND, null,
            Map.of(JsonOverrides.NOISE_SETTINGS, "Terrain settings", JsonOverrides.DENSITY_FUNCTION, "Terrain shape",
                    JsonOverrides.NOISE, "Noises", JsonOverrides.CARVER, "Caves and canyons"));
    /** World types: the world presets and flat presets a world can be made as, and their tags. */
    public static final View WORLD_TYPES = new View("world_types", "World types", JsonOverrides.WORLD_PRESET,
            List.of(JsonOverrides.FLAT_PRESET, JsonOverrides.WORLD_PRESET_TAG, JsonOverrides.FLAT_PRESET_TAG),
            Grouping.KIND, null,
            Map.of(JsonOverrides.WORLD_PRESET, "World types", JsonOverrides.FLAT_PRESET, "Superflat layouts",
                    JsonOverrides.WORLD_PRESET_TAG, "World type tags", JsonOverrides.FLAT_PRESET_TAG,
                    "Superflat layout tags"));
    /** Cat and wolf variants in one list: the wolf variants and the cat variant tags, a group each. */
    public static final View MOB_VARIANTS = paired("mob_variants", "Cat and wolf variants", JsonOverrides.WOLF_VARIANT,
            "Wolf variants", JsonOverrides.CAT_VARIANT_TAG, "Cat variant tags");
    /** The paintings and their tags in one list. */
    public static final View PAINTINGS = paired("paintings", "Paintings", JsonOverrides.PAINTING_VARIANT, "Paintings",
            JsonOverrides.PAINTING_VARIANT_TAG, "Painting tags");
    /** The banner patterns and their tags in one list. */
    public static final View BANNER_PATTERNS = paired("banner_patterns", "Banner patterns", JsonOverrides.BANNER_PATTERN,
            "Banner patterns", JsonOverrides.BANNER_PATTERN_TAG, "Banner pattern tags");
    /** Armor trim materials and patterns in one list. */
    public static final View ARMOR_TRIMS = paired("armor_trims", "Armor trims", JsonOverrides.TRIM_MATERIAL, "Materials",
            JsonOverrides.TRIM_PATTERN, "Patterns");
    /** The enchantments and their tags in one list. */
    public static final View ENCHANTMENTS = paired("enchantments", "Enchantments", JsonOverrides.ENCHANTMENT,
            "Enchantments", JsonOverrides.ENCHANTMENT_TAG, "Enchantment tags");

    /** The mob tags, grouped by what they are about (Aquatic, Undead, Raiders...), as the Mobs list groups mobs. */
    public static final View MOB_TAGS = new View("mob_tags", "Mob tags", JsonOverrides.ENTITY_TAG, List.of(),
            Grouping.MOB_THEME, null);

    /**
     * A mob tag's theme, by the words of its path, first match wins; "Other" for none.
     * shortcut: a word table over the game's and common mods' tag names, add words when a mod's tags land in Other.
     */
    static final List<Map.Entry<String, List<String>>> MOB_THEMES = List.of(
            Map.entry("Aquatic", List.of("aquatic", "axolotl", "water", "fish", "drown", "swim", "squid", "frog")),
            Map.entry("Undead", List.of("undead", "zombie", "skeleton", "wither")),
            Map.entry("Raiders", List.of("raider", "illager", "pillager")),
            Map.entry("Arthropods", List.of("arthropod", "spider", "bee")),
            Map.entry("Immunities", List.of("immune", "sensitive", "freeze", "fall_damage", "powder_snow")),
            Map.entry("Riding and boats", List.of("boat", "ride", "mount", "dismount", "rideable")),
            Map.entry("Attacks and targets", List.of("attack", "target", "hunt", "arrow", "projectile",
                    "impaling")),
            Map.entry("Spawning", List.of("spawn", "burn", "light", "despawn")));
    public static final String OTHER_THEME = "Other";

    public static String mobTheme(String tagId) {
        String path = tagId.substring(tagId.indexOf(':') + 1);
        for (Map.Entry<String, List<String>> t : MOB_THEMES) {
            if (t.getValue().stream().anyMatch(path::contains)) {
                return t.getKey();
            }
        }
        return OTHER_THEME;
    }

    /** Two kinds that belong together as one list, a group each. */
    private static View paired(String id, String title, JsonOverrides.Kind kind, String kindTitle,
                               JsonOverrides.Kind other, String otherTitle) {
        return new View(id, title, kind, List.of(other), Grouping.KIND, null, Map.of(kind, kindTitle, other, otherTitle));
    }

    /** The view a kind opens in: its own, or a plain list of the kind. */
    public static View of(JsonOverrides.Kind kind) {
        if (kind == JsonOverrides.STRUCTURE) {
            return STRUCTURES;
        }
        if (kind == JsonOverrides.PLACED_FEATURE) {
            return FEATURES;
        }
        if (kind == JsonOverrides.DAMAGE_TYPE) {
            return DAMAGE_TYPES;
        }
        if (kind == JsonOverrides.ENTITY_TAG) {
            return MOB_TAGS;
        }
        return new View(kind.id(), kind.label(), kind, List.of(), Grouping.NAMESPACE, null);
    }

    /** The group of the structure sets no structure of the list names. */
    public static final String LOOSE_SETS = "Spacing not tied to a structure here";
    /** The group of the placed features no biome places. */
    public static final String NO_BIOME = "Not placed by any biome";
    /** The group of the configured features no placed feature uses. */
    public static final String LOOSE_SETTINGS = "Settings used inside other features";
    /** The group of a structure with no type. */
    public static final String NO_TYPE = "Other";

    /** One row: the entry, the kind it is of, and the group it is listed under ("" for a namespace view). */
    public record Row(JsonOverrides.Kind kind, OverrideOps.Entry entry, String group) {
    }

    /** One group of the list: its name, and the positions of its rows. */
    public record Group(String name, List<Integer> rows) {
    }

    /**
     * A view's rows, in list order, already grouped (a group's rows are together, the groups in the order
     * they should show).
     */
    public static List<Row> rows(View view, String search) {
        List<Row> out = new ArrayList<>();
        switch (view.grouping()) {
            case NAMESPACE -> OverrideOps.entries(view.kind(), search)
                    .forEach(e -> out.add(new Row(view.kind(), e, "")));
            case MOB_THEME -> {
                // The themes in MOB_THEMES order, Other last; each group by id, as entries sorts.
                Map<String, List<Row>> byTheme = new java.util.LinkedHashMap<>();
                MOB_THEMES.forEach(t -> byTheme.put(t.getKey(), new ArrayList<>()));
                byTheme.put(OTHER_THEME, new ArrayList<>());
                for (OverrideOps.Entry e : OverrideOps.entries(view.kind(), search)) {
                    String theme = mobTheme(e.id());
                    byTheme.get(theme).add(new Row(view.kind(), e, theme));
                }
                byTheme.values().forEach(out::addAll);
            }
            case KIND -> {
                for (JsonOverrides.Kind k : kinds(view)) {
                    OverrideOps.entries(k, search)
                            .forEach(e -> out.add(new Row(k, e, view.groupTitle(k))));
                }
            }
            case STRUCTURE_TYPE -> {
                TreeMap<String, List<Row>> byType = new TreeMap<>();
                for (OverrideOps.Entry e : OverrideOps.entries(view.kind(), search)) {
                    String label = OverrideOps.typeLabel(e.type());
                    byType.computeIfAbsent(label.isEmpty() ? NO_TYPE : label, k -> new ArrayList<>())
                            .add(new Row(view.kind(), e, label.isEmpty() ? NO_TYPE : label));
                }
                byType.values().forEach(out::addAll);
                Set<String> tied = tiedSets(JsonOverrides.game(JsonOverrides.STRUCTURE_SET),
                        JsonOverrides.game(view.kind()).keySet());
                for (OverrideOps.Entry e : OverrideOps.entries(JsonOverrides.STRUCTURE_SET, search)) {
                    if (!tied.contains(e.id())) {
                        out.add(new Row(JsonOverrides.STRUCTURE_SET, e, LOOSE_SETS));
                    }
                }
                // The other also kinds - template pools, processor lists, structure modifiers, structure tags
                // - as their own groups after the structure types and the loose spacing.
                for (JsonOverrides.Kind k : view.also().subList(1, view.also().size())) {
                    OverrideOps.entries(k, search)
                            .forEach(e -> out.add(new Row(k, e, view.groupTitle(k))));
                }
            }
            case BIOME -> {
                Map<String, List<String>> where = biomesOfFeatures(JsonOverrides.game(JsonOverrides.BIOME));
                TreeMap<String, List<Row>> byBiome = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                List<Row> none = new ArrayList<>();
                for (OverrideOps.Entry e : OverrideOps.entries(view.kind(), search)) {
                    List<String> biomes = where.getOrDefault(e.id(), List.of());
                    if (biomes.isEmpty()) {
                        none.add(new Row(view.kind(), e, NO_BIOME));
                    }
                    for (String biome : biomes) {
                        String group = biomeGroup(biome);
                        byBiome.computeIfAbsent(group, k -> new ArrayList<>()).add(new Row(view.kind(), e, group));
                    }
                }
                byBiome.values().forEach(out::addAll);
                out.addAll(none);
                Set<String> used = usedSettings(JsonOverrides.game(view.kind()));
                for (OverrideOps.Entry e : OverrideOps.entries(JsonOverrides.CONFIGURED_FEATURE,
                        search)) {
                    if (!used.contains(e.id())) {
                        out.add(new Row(JsonOverrides.CONFIGURED_FEATURE, e, LOOSE_SETTINGS));
                    }
                }
            }
        }
        return out;
    }

    private static List<JsonOverrides.Kind> kinds(View view) {
        List<JsonOverrides.Kind> out = new ArrayList<>();
        out.add(view.kind());
        out.addAll(view.also());
        return out;
    }

    /** The groups of rows, in the order of first appearance, with each one's row positions. */
    public static List<Group> groups(List<Row> rows) {
        Map<String, List<Integer>> by = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            by.computeIfAbsent(rows.get(i).group(), k -> new ArrayList<>()).add(i);
        }
        List<Group> out = new ArrayList<>();
        by.forEach((name, idx) -> out.add(new Group(name, idx)));
        return out;
    }

    // ---------------------------------------------------------------- the links between kinds

    /** A biome's group name: its words, and its mod when it is not the game's ("Plains", "Foo (mymod)"). */
    public static String biomeGroup(String biomeId) {
        String ns = biomeId.contains(":") ? biomeId.substring(0, biomeId.indexOf(':')) : "minecraft";
        return OverrideOps.nameOf(biomeId) + (ns.equals("minecraft") ? "" : " (" + ns + ")");
    }

    /** The id with its namespace: a bare name is the game's. */
    private static String full(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /**
     * Where each placed feature is placed: placed feature id to the ids of the biomes that list it (a biome's
     * "features" is a list per generation step), biomes in id order.
     */
    public static Map<String, List<String>> biomesOfFeatures(Map<String, JsonObject> biomes) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        new TreeMap<>(biomes).forEach((biomeId, json) -> {
            JsonElement steps = json.get("features");
            if (steps == null || !steps.isJsonArray()) {
                return;
            }
            for (JsonElement step : steps.getAsJsonArray()) {
                if (!step.isJsonArray()) {
                    continue;
                }
                for (JsonElement feature : step.getAsJsonArray()) {
                    if (feature.isJsonPrimitive()) {
                        out.computeIfAbsent(full(feature.getAsString()), k -> new LinkedHashSet<>()).add(biomeId);
                    }
                }
            }
        });
        Map<String, List<String>> result = new LinkedHashMap<>();
        out.forEach((k, v) -> result.put(k, List.copyOf(v)));
        return result;
    }

    /**
     * The structure sets a structure is in (a set's "structures" is a list of {structure, weight}), in id
     * order.
     */
    public static List<String> setsOfStructure(String structureId, Map<String, JsonObject> sets) {
        String want = full(structureId);
        List<String> out = new ArrayList<>();
        new TreeMap<>(sets).forEach((setId, json) -> {
            for (String s : structuresOfSet(json)) {
                if (full(s).equals(want)) {
                    out.add(setId);
                    return;
                }
            }
        });
        return out;
    }

    private static List<String> structuresOfSet(JsonObject set) {
        List<String> out = new ArrayList<>();
        JsonElement list = set.get("structures");
        if (list != null && list.isJsonArray()) {
            JsonArray array = list.getAsJsonArray();
            for (JsonElement e : array) {
                if (e.isJsonObject() && e.getAsJsonObject().has("structure")
                        && e.getAsJsonObject().get("structure").isJsonPrimitive()) {
                    out.add(e.getAsJsonObject().get("structure").getAsString());
                }
            }
        }
        return out;
    }

    /** The structure sets that name at least one of the structures given. */
    public static Set<String> tiedSets(Map<String, JsonObject> sets, Set<String> structureIds) {
        Set<String> known = new java.util.HashSet<>();
        structureIds.forEach(s -> known.add(full(s)));
        Set<String> out = new LinkedHashSet<>();
        sets.forEach((setId, json) -> {
            for (String s : structuresOfSet(json)) {
                if (known.contains(full(s))) {
                    out.add(setId);
                    return;
                }
            }
        });
        return out;
    }

    /**
     * The structures a biome takes: each structure whose "biomes" names the biome, directly or through a
     * biome tag (tags inside tags followed, each once).
     *
     * @param structures structure id to its JSON
     * @param biomeTags biome tag id (no "#") to its JSON ("values": ids, "#tags", or {id, required})
     */
    public static Set<String> structuresInBiome(String biomeId, Map<String, JsonObject> structures,
                                                Map<String, JsonObject> biomeTags) {
        String want = full(biomeId);
        Set<String> out = new java.util.TreeSet<>();
        structures.forEach((id, json) -> {
            if (biomesOf(json == null ? null : json.get("biomes"), biomeTags, new java.util.HashSet<>()).contains(want)) {
                out.add(full(id));
            }
        });
        return out;
    }

    /** The biome ids a "biomes" value names: an id, a "#tag", or a list of ids. */
    private static Set<String> biomesOf(JsonElement value, Map<String, JsonObject> tags, Set<String> seen) {
        Set<String> out = new java.util.HashSet<>();
        if (value == null) {
            return out;
        }
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(e -> out.addAll(biomesOf(e, tags, seen)));
        } else if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
            out.addAll(biomesOf(value.getAsJsonObject().get("id"), tags, seen));
        } else if (value.isJsonPrimitive()) {
            String s = value.getAsString();
            if (!s.startsWith("#")) {
                out.add(full(s));
            } else if (seen.add(full(s.substring(1)))) {
                JsonObject tag = tags.get(full(s.substring(1)));
                if (tag != null) {
                    out.addAll(biomesOf(tag.get("values"), tags, seen));
                }
            }
        }
        return out;
    }

    /** The configured feature a placed feature places, or "" for an inline one or none. */
    public static String settingsOf(JsonObject placed) {
        JsonElement f = placed == null ? null : placed.get("feature");
        return f != null && f.isJsonPrimitive() ? full(f.getAsString()) : "";
    }

    /** The configured features some placed feature places. */
    public static Set<String> usedSettings(Map<String, JsonObject> placed) {
        Set<String> out = new LinkedHashSet<>();
        placed.values().forEach(p -> {
            String s = settingsOf(p);
            if (!s.isEmpty()) {
                out.add(s);
            }
        });
        return out;
    }
}
