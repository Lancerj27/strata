package com.strata.domain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strata.data.JsonOverrides;
import com.strata.model.JsonPaths;
import com.strata.util.Labels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * THE OVERRIDE SHEET'S BACK END: the screens ask this for a kind's entries, an entry's fields,
 * and the writes.
 */
public final class OverrideOps {

    private OverrideOps() {
    }

    /** Where an entry stands: the game's, edited here, or switched off. */
    public enum State { GAME, EDITED, OFF }

    /**
     * One entry as the list shows it.
     *
     * @param type its "type" (a recipe's serializer: minecraft:smelting, a mod's own)
     * @param summary what it makes, from what, in words
     */
    public record Entry(String id, String type, String summary, State state) {
    }

    /** The entries of a kind, filtered by a search over id, summary and type ("smelting" finds the furnace's). */
    public static List<Entry> entries(JsonOverrides.Kind kind, String search) {
        Map<String, JsonOverrides.Override> overrides = JsonOverrides.overrides(kind);
        String s = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        JsonOverrides.game(kind).forEach((id, json) -> {
            String type = typeOf(json);
            String summary = summary(json);
            if (!s.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(s) && !summary.toLowerCase(Locale.ROOT).contains(s)
                    && !typeLabel(type).toLowerCase(Locale.ROOT).contains(s)) {
                return;
            }
            JsonOverrides.Override o = overrides.getOrDefault(id, JsonOverrides.Override.NONE);
            out.add(new Entry(id, type, summary, o.off() ? State.OFF
                    : o.set().isEmpty() && o.json() == null ? State.GAME : State.EDITED));
        });
        out.sort(Comparator.comparing(Entry::id, OverrideOps::compareIds));
        return out;
    }

    // SORTED AND GROUPED: entries by id, namespace first (minecraft, c, then each mod), then path; the list
    // groups them by namespace and, in a big one, by first folder.

    /** A namespace with more entries than this splits into its folders (or letters, for a flat one). */
    public static final int BIG_GROUP = 80;

    /** A namespace folder holds at least this many entries; fewer are shown flat. */
    public static final int MIN_FOLD = 25;

    /** The folder-less entries of a big namespace, when few. */
    public static final String ROOT = "(top level)";

    private static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private static int namespaceRank(String ns) {
        return ns.equals("minecraft") ? 0 : ns.equals("c") ? 1 : 2;
    }

    /** Ids in list order: minecraft, then c, then each mod by name; within one, by path. */
    public static int compareIds(String a, String b) {
        String na = namespaceOf(a);
        String nb = namespaceOf(b);
        int c = Integer.compare(namespaceRank(na), namespaceRank(nb));
        if (c == 0) {
            c = na.compareTo(nb);
        }
        return c != 0 ? c : pathOf(a).compareTo(pathOf(b));
    }

    /**
     * One section of the grouped list: a namespace and, when it is big, one of its folders.
     *
     * @param sub "" when the namespace is not split
     * @param indexes positions in the list given, in that list's order
     */
    public record Section(String group, String sub, List<Integer> indexes) {
    }

    /**
     * The grouped list's sections, for entries already sorted by {@link #compareIds}: a namespace of up to
     * BIG_GROUP entries is one section; a bigger one splits by the first folder of the path
     * ("minecraft:mineable/pickaxe" is in "mineable"), folders sorted, the entries with no folder under ROOT
     * last, or, when those are many, by their first letter.
     */
    public static List<Section> sections(List<Entry> sorted) {
        Map<String, List<Integer>> byNamespace = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            byNamespace.computeIfAbsent(namespaceOf(sorted.get(i).id()), k -> new ArrayList<>()).add(i);
        }
        List<Section> out = new ArrayList<>();
        boolean single = byNamespace.size() == 1;
        byNamespace.forEach((ns, idx) -> {
            // No namespace folder for a list of one namespace, or one under MIN_FOLD entries: group "" is
            // flat, or the folders only.
            String g = single || idx.size() < MIN_FOLD ? "" : ns;
            if (idx.size() <= BIG_GROUP) {
                out.add(new Section(g, "", idx));
                return;
            }
            TreeMap<String, List<Integer>> folders = new TreeMap<>();
            List<Integer> root = new ArrayList<>();
            for (int i : idx) {
                String path = pathOf(sorted.get(i).id());
                int slash = path.indexOf('/');
                if (slash > 0) {
                    folders.computeIfAbsent(path.substring(0, slash), k -> new ArrayList<>()).add(i);
                } else {
                    root.add(i);
                }
            }
            folders.forEach((f, l) -> out.add(new Section(g, f, l)));
            if (root.size() <= BIG_GROUP) {
                if (!root.isEmpty()) {
                    out.add(new Section(g, folders.isEmpty() ? "" : ROOT, root));
                }
            } else {
                TreeMap<String, List<Integer>> letters = new TreeMap<>();
                for (int i : root) {
                    String p = pathOf(sorted.get(i).id());
                    letters.computeIfAbsent(p.isEmpty() ? "-" : p.substring(0, 1).toUpperCase(Locale.ROOT),
                            k -> new ArrayList<>()).add(i);
                }
                letters.forEach((l, list) -> out.add(new Section(g, "Names " + l, list)));
            }
        });
        return out;
    }

    /** One field of an entry: its path, a name when Strata knows it, the game's value, and the value it loads with. */
    public record Field(String path, String name, String game, String current) {
        public boolean edited() {
            return !game.equals(current);
        }
    }

    /**
     * An entry's fields as it loads now, in file order: an added list element's fields too, with no game
     * value. A tag's members and a data map's values are sorted by name, where they first stand: their file
     * order is the pack's, not one a reader can scan.
     */
    public static List<Field> fields(JsonOverrides.Kind kind, String id) {
        JsonObject game = JsonOverrides.game(kind).get(id);
        if (game == null) {
            return List.of();
        }
        Map<String, String> was = JsonPaths.flatten(game);
        Map<String, String> now = JsonPaths.flatten(edited(kind, id, game));
        List<Field> out = new ArrayList<>();
        List<Field> members = new ArrayList<>();
        int at = -1;
        for (Map.Entry<String, String> e : now.entrySet()) {
            String path = e.getKey();
            if (path.equals("type") || path.equals("replace")) {
                continue;
            }
            Field f = new Field(path, fieldName(path), was.getOrDefault(path, ""), e.getValue());
            if (path.startsWith("values.") || path.startsWith("values[")) {
                at = at < 0 ? out.size() : at;
                members.add(f);
            } else {
                out.add(f);
            }
        }
        // A tag's members by what they name; a data map's by its key, then the field's name.
        // A member written as {id, required} sorts by its id, its two rows kept together.
        members.sort(Comparator.comparing((Field f) -> {
            if (!f.path().startsWith("values[")) {
                return f.name();
            }
            String member = f.path().substring(0, f.path().indexOf(']') + 1);
            return now.getOrDefault(member, now.getOrDefault(member + ".id", ""));
        }, String.CASE_INSENSITIVE_ORDER).thenComparing(Field::path));
        out.addAll(Math.max(at, 0), members);
        return out;
    }

    /** The entry with its changes and not switched off: what the fields are read and set on. */
    private static JsonObject edited(JsonOverrides.Kind kind, String id, JsonObject game) {
        JsonOverrides.Override o = JsonOverrides.overrides(kind).getOrDefault(id, JsonOverrides.Override.NONE);
        JsonObject out = JsonOverrides.applied(kind, game, new JsonOverrides.Override(o.set(), false, o.json()));
        out.remove("replace");
        return out;
    }

    /** Why a value cannot be set at a path, or null. */
    public static String problem(JsonOverrides.Kind kind, String id, String path, String value) {
        JsonObject game = JsonOverrides.game(kind).get(id);
        return game == null ? "Not in this game" : JsonPaths.problem(edited(kind, id, game), path, value);
    }

    /**
     * The list element or map member a field can be duplicated or removed as: a list's element, or a data
     * map's member (values.&lt;id&gt;).
     */
    public static String element(String path) {
        String e = JsonPaths.elementOf(path);
        return e != null && (e.endsWith("]") || e.startsWith("values.")) ? e : null;
    }

    /**
     * Duplicates the field's element: a list element is copied onto the end of its list (a structure's spawn,
     * a tag's member, a loot entry), a map member under newKey (a fuel time for another item).
     */
    public static WriteReport duplicate(JsonOverrides.Kind kind, String id, String path, String newKey) {
        JsonObject game = JsonOverrides.game(kind).get(id);
        String element = element(path);
        if (game == null || element == null) {
            return WriteReport.refused("Pick a field in a list, or an item of a data map");
        }
        JsonObject next = JsonPaths.duplicate(edited(kind, id, game), element, newKey);
        if (next == null) {
            return WriteReport.refused("Type the new id in Value first: one this map does not have");
        }
        JsonOverrides.Override o = JsonOverrides.overrides(kind).getOrDefault(id, JsonOverrides.Override.NONE);
        return save(kind, id, new JsonOverrides.Override(Map.of(), o.off(), next));
    }

    /** Removes the field's element from its list or map. */
    public static WriteReport removeElement(JsonOverrides.Kind kind, String id, String path) {
        JsonObject game = JsonOverrides.game(kind).get(id);
        String element = element(path);
        if (game == null || element == null) {
            return WriteReport.refused("Pick a field in a list, or an item of a data map");
        }
        JsonObject next = JsonPaths.remove(edited(kind, id, game), element);
        if (next == null) {
            return WriteReport.refused("Nothing at " + element);
        }
        JsonOverrides.Override o = JsonOverrides.overrides(kind).getOrDefault(id, JsonOverrides.Override.NONE);
        return save(kind, id, new JsonOverrides.Override(Map.of(), o.off(), next));
    }

    /** Sets one field. */
    public static WriteReport set(JsonOverrides.Kind kind, String id, String path, String value) {
        String problem = problem(kind, id, path, value);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        JsonOverrides.Override o = JsonOverrides.overrides(kind).getOrDefault(id, JsonOverrides.Override.NONE);
        Map<String, String> set = new LinkedHashMap<>(o.set());
        String game = o.json() != null ? null : JsonPaths.flatten(JsonOverrides.game(kind).get(id)).get(path);
        if (value.strip().equals(game)) {
            set.remove(path);
        } else {
            set.put(path, value.strip());
        }
        return save(kind, id, new JsonOverrides.Override(set, o.off(), o.json()));
    }

    /** Puts one field back to the game's. */
    public static WriteReport restore(JsonOverrides.Kind kind, String id, String path) {
        JsonOverrides.Override o = JsonOverrides.overrides(kind).getOrDefault(id, JsonOverrides.Override.NONE);
        Map<String, String> set = new LinkedHashMap<>(o.set());
        set.remove(path);
        return save(kind, id, new JsonOverrides.Override(set, o.off(), o.json()));
    }

    /** Switches an entry off or back on. */
    public static WriteReport setOff(JsonOverrides.Kind kind, String id, boolean off) {
        if (kind.off() == JsonOverrides.Off.NONE) {
            return WriteReport.refused(kind.label() + " are settings: switch off what uses them instead");
        }
        JsonOverrides.Override o = JsonOverrides.overrides(kind).getOrDefault(id, JsonOverrides.Override.NONE);
        return save(kind, id, new JsonOverrides.Override(o.set(), off, o.json()));
    }

    /** Restore all: every change of the entry goes. */
    public static WriteReport reset(JsonOverrides.Kind kind, String id) {
        return save(kind, id, JsonOverrides.Override.NONE);
    }

    private static WriteReport save(JsonOverrides.Kind kind, String id, JsonOverrides.Override o) {
        return Commit.reloadIfOk(new WriteReport().record("the " + kind.id(), JsonOverrides.store(kind, id, o)))
                .needsReload("Saved. It applies on the next world load or /reload");
    }

    // ---------------------------------------------------------------- words

    /** An entry's type: its "type" field, or "" for none. */
    public static String typeOf(JsonObject json) {
        JsonElement t = json.get("type");
        return t != null && t.isJsonPrimitive() ? t.getAsString() : "";
    }

    /** A type in words, with the device it is made in for the game's recipe types ("Smelting (furnace)"). */
    public static String typeLabel(String type) {
        String device = switch (type) {
            case "minecraft:crafting_shaped", "minecraft:crafting_shapeless" -> "crafting table";
            case "minecraft:smelting" -> "furnace";
            case "minecraft:blasting" -> "blast furnace";
            case "minecraft:smoking" -> "smoker";
            case "minecraft:campfire_cooking" -> "campfire";
            case "minecraft:stonecutting" -> "stonecutter";
            case "minecraft:smithing_transform", "minecraft:smithing_trim" -> "smithing table";
            default -> "";
        };
        // A mod's own type names its mod, so two mods' "mixing" are told apart.
        String ns = type.contains(":") ? type.substring(0, type.indexOf(':')) : "minecraft";
        String words = type.isEmpty() ? ""
                : ns.equals("minecraft") ? Labels.pretty(type) : Labels.pretty(ns) + ": " + Labels.pretty(type);
        return device.isEmpty() ? words : words + " (" + device + ")";
    }

    /** The names of the fields Strata knows, by the end of their path. */
    public static String fieldName(String path) {
        String p = path.replaceAll("\\[\\d+]", "");
        if (p.endsWith("cookingtime")) return "Cook time (ticks)";
        if (p.endsWith("experience")) return "XP";
        if (p.equals("result.id") || p.equals("result.item") || p.equals("result")) return "Result";
        if (p.equals("result.count")) return "Result count";
        if (p.equals("count")) return "Count";
        if (p.startsWith("ingredient")) return p.endsWith("tag") ? "Input tag" : "Input";
        if (p.startsWith("ingredients")) return p.endsWith("tag") ? "Ingredient tag" : "Ingredient";
        if (p.startsWith("key.")) return "Key " + p.substring(4, p.indexOf('.', 4) < 0 ? p.length() : p.indexOf('.', 4));
        if (p.startsWith("pattern")) return "Pattern row";
        if (p.startsWith("base")) return "Base";
        if (p.startsWith("addition")) return "Addition";
        if (p.startsWith("template")) return "Template";
        // World generation: the fields an ore, a structure and a set have.
        if (p.endsWith("spacing")) return "Spacing (chunks)";
        if (p.endsWith("separation")) return "Separation (chunks)";
        if (p.endsWith("salt")) return "Salt (keep it unique)";
        if (p.equals("biomes")) return "Biomes (a #tag, or a list)";
        if (p.startsWith("spawn_overrides")) {
            String[] parts = p.split("\\.");
            String what = p.endsWith("type") ? "mob" : p.endsWith("weight") ? "weight"
                    : p.endsWith("minCount") ? "min group" : p.endsWith("maxCount") ? "max group"
                    : p.endsWith("bounding_box") ? "where (piece or full)" : "";
            return "Spawns: " + (parts.length > 1 ? parts[1] : "") + (what.isEmpty() ? "" : " " + what);
        }
        if (p.startsWith("placement") && p.endsWith("min_inclusive.absolute")) return "Lowest Y";
        if (p.startsWith("placement") && p.endsWith("max_inclusive.absolute")) return "Highest Y";
        if (p.startsWith("placement") && p.endsWith("above_bottom")) return "Blocks above the bottom";
        if (p.startsWith("placement") && p.endsWith("below_top")) return "Blocks below the top";
        if (p.startsWith("placement") && p.endsWith("chance")) return "Rarity (1 in this many chunks)";
        if (p.startsWith("placement") && p.endsWith("count")) return "Times per chunk";
        if (p.equals("config.size")) return "Vein size";
        if (p.equals("config.discard_chance_on_air_exposure")) return "Chance to skip when touching air";
        if (p.startsWith("config.targets") && p.endsWith("state.Name")) return "Places this block";
        if (p.startsWith("config.targets") && p.endsWith("target.tag")) return "In place of (tag)";
        if (p.startsWith("config.targets") && p.endsWith("target.block")) return "In place of (block)";
        if (p.equals("feature")) return "The feature it places";
        // A biome: its climate, its looks, what it carves and places.
        // A data map's value: the block or item it is for in words, then the value ("Copper Block: Next
        // Oxidation Stage"); a tag's member.
        if (path.startsWith("values[")) return "Member";
        if (p.startsWith("values.")) {
            int dot = p.indexOf('.', 7);
            // An id's own ':' never holds a '.', so the key ends at the next dot after it.
            int colon = p.indexOf(':', 7);
            if (colon > 0 && colon < (dot < 0 ? p.length() : dot)) {
                dot = p.indexOf('.', colon);
            }
            String key = p.substring(7, dot < 0 ? p.length() : dot);
            String keyWords = (key.startsWith("#") ? "#" : "") + Labels.pretty(key.replaceFirst("^#", ""));
            if (dot < 0) return keyWords;
            String what = p.substring(dot + 1);
            return keyWords + ": " + (what.equals("chance") ? "Compost chance" : Labels.pretty(what.replace('.', ' ')));
        }
        if (p.startsWith("potion_mixes")) return p.endsWith("input") ? "Potion mix: from" : p.endsWith("output") ? "Potion mix: makes" : "Potion mix: ingredient";
        if (p.startsWith("container_mixes")) return p.endsWith("input") ? "Bottle mix: from" : p.endsWith("output") ? "Bottle mix: makes" : "Bottle mix: ingredient";
        if (p.equals("temperature")) return "Temperature";
        if (p.equals("downfall")) return "Rain amount (downfall)";
        if (p.equals("has_precipitation")) return "Rain or snow";
        if (p.equals("temperature_modifier")) return "Temperature rule (frozen: patchy ice)";
        if (p.startsWith("effects.") && p.endsWith("_color")) return Labels.pretty(p.substring(8)) + " (a number)";
        if (p.startsWith("effects.music")) return "Music";
        if (p.startsWith("effects.ambient_sound")) return "Ambient sound";
        if (p.startsWith("effects.mood_sound")) return "Cave mood sound";
        if (p.startsWith("effects.particle")) return "Ambient particle";
        if (p.startsWith("carvers")) return "Carver (caves, canyons)";
        if (p.startsWith("features")) return "Feature it places";
        if (p.equals("group")) return "Recipe book group";
        if (p.equals("category")) return "Recipe book tab";
        return "";
    }

    /** A name for an entry with no summary: the last part of its id in words ("Desert Pyramid"). */
    public static String nameOf(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return Labels.pretty(path.substring(path.lastIndexOf('/') + 1));
    }

    /** What an entry makes, from what: "Iron Ingot from Raw Iron". */
    public static String summary(JsonObject json) {
        // Game rules: how many.
        if (json.has("doDaylightCycle")) {
            return json.size() + " rule(s)";
        }
        // Brewing: its mixes.
        if (json.has("potion_mixes")) {
            return json.getAsJsonArray("potion_mixes").size() + " potion mix(es), "
                    + json.getAsJsonArray("container_mixes").size() + " bottle mix(es)";
        }
        // A tag's or a data map's members.
        JsonElement values = json.get("values");
        if (values != null && values.isJsonArray()) {
            return values.getAsJsonArray().size() + " member(s)";
        }
        if (values != null && values.isJsonObject()) {
            return values.getAsJsonObject().size() + " entr(ies)";
        }
        // World generation: a structure's biomes, a set's spacing, a feature's feature, an ore's block.
        JsonElement biomes = json.get("biomes");
        if (biomes != null && biomes.isJsonPrimitive()) {
            return "in " + biomes.getAsString();
        }
        String spacing = first(json, "placement.spacing");
        if (!spacing.isEmpty()) {
            return "every " + spacing + " chunks, at least " + first(json, "placement.separation") + " apart";
        }
        String feature = first(json, "feature");
        if (!feature.isEmpty()) {
            return "places " + Labels.pretty(feature);
        }
        String block = first(json, "config.targets[0].state.Name", "config.state.Name");
        if (!block.isEmpty()) {
            return Labels.pretty(block) + first(json, "config.size").transform(s -> s.isEmpty() ? "" : ", vein size " + s);
        }
        String result = first(json, "result.id", "result.item", "result");
        String input = first(json, "ingredient.item", "ingredient.tag", "ingredient[0].item", "base.item",
                "ingredients[0].item", "ingredients[0].tag");
        String r = result.isEmpty() ? "" : Labels.pretty(result);
        String i = input.isEmpty() ? "" : Labels.pretty(input);
        return r.isEmpty() ? (i.isEmpty() ? "" : "from " + i) : i.isEmpty() ? r : r + " from " + i;
    }

    private static String first(JsonObject json, String... paths) {
        for (String p : paths) {
            JsonElement e = JsonPaths.get(json, p);
            if (e != null && e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        return "";
    }
}
