package com.strata.domain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** MODS' MACHINE RECIPES, READ WHOLE. */
public final class MachineRecipes {

    private MachineRecipes() {
    }

    /** One output: the item (or "#tag"), how many, and its chance in percent (100 for sure). */
    public record Out(String id, String count, double chance) {
    }

    /** One input: an item or "#tag", and how many. */
    public record In(String id, int count) {
    }

    /**
     * A recipe read whole.
     *
     * @param pattern a crafter's pattern laid out, "" for an empty slot; empty for a recipe with none
     */
    public record Machine(List<Out> outs, List<In> ins, List<String> other, List<List<String>> pattern) {
    }

    /**
     * What one mod's recipes need beyond the general reader.
     *
     * @param mod the namespace of its recipe types
     * @param inputs more keys that are inputs (IE's "additives", "mold")
     * @param outputs more keys that are outputs (IE's "slag")
     * @param needs keys that are a tool or a condition, said as "needs..." (Farmer's Delight's "tool")
     * @param words keys whose value is worth saying, with how ("mode" -> "mode")
     */
    public record Shape(String mod, List<String> inputs, List<String> outputs, List<String> needs, Map<String, String> words) {
    }

    /** The values any mod's recipe may carry, said as written: time in seconds, the rest with their name. */
    private static final Map<String, String> COMMON_WORDS = Map.of(
            "processing_time", "time", "processingTime", "time", "duration", "time", "time", "time",
            "cookingtime", "time", "cooking_time", "time", "energy", "energy", "experience", "xp",
            "heat_requirement", "heat", "loops", "loops");

    /** THE MODS (add one line for a mod whose recipes the general reader misses something of). */
    public static final List<Shape> SHAPES = List.of(
            new Shape("create", List.of(), List.of(), List.of(), Map.of()),
            new Shape("mekanism", List.of(), List.of(), List.of(), Map.of("per_tick_usage", "per tick")),
            new Shape("ae2", List.of(), List.of(), List.of(), Map.of("mode", "mode")),
            new Shape("immersiveengineering", List.of("additives", "mold", "catalyst"), List.of("slag", "byproducts"),
                    List.of(), Map.of()),
            new Shape("farmersdelight", List.of("container"), List.of(), List.of("tool"), Map.of()),
            new Shape("thermal", List.of(), List.of(), List.of(), Map.of()),
            new Shape("ars_nouveau", List.of("pedestalItems", "reagent"), List.of(), List.of(), Map.of("sourceCost", "source")),
            new Shape("mysticalagriculture", List.of(), List.of(), List.of(), Map.of()));

    /** The mod's shape, or the general one. */
    public static Shape shapeOf(String type) {
        String mod = type == null || type.indexOf(':') < 0 ? "" : type.substring(0, type.indexOf(':'));
        for (Shape s : SHAPES) {
            if (s.mod().equals(mod)) {
                return s;
            }
        }
        return new Shape(mod, List.of(), List.of(), List.of(), Map.of());
    }

    /** Reads a recipe. */
    public static Machine read(JsonObject json) {
        String type = json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : "";
        Shape shape = shapeOf(type);
        List<Out> outs = new ArrayList<>();
        List<In> ins = new ArrayList<>();
        List<String> other = new ArrayList<>();
        List<List<String>> pattern = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            String raw = e.getKey();
            String key = raw.toLowerCase(Locale.ROOT);
            if (shape.needs().contains(raw)) {
                for (JsonElement n : asList(e.getValue())) {
                    String id = ingredientOf(n);
                    if (!id.isEmpty()) other.add("needs " + id);
                }
            } else if (shape.outputs().contains(raw) || key.contains("output") || key.contains("result") || key.contains("secondar")) {
                for (JsonElement o : asList(e.getValue())) {
                    readOut(o, key, 100, outs, other);
                }
            } else if (shape.inputs().contains(raw) || key.contains("input") || key.contains("ingredient")
                    || key.equals("template") || key.equals("base") || key.equals("addition")) {
                for (JsonElement i : asList(e.getValue())) {
                    readIn(i, key, ins, other);
                }
            }
        }
        sequence(json, outs, ins, other);
        pattern(json, ins, pattern);
        words(json, shape, other);
        return new Machine(List.copyOf(outs), List.copyOf(ins), List.copyOf(other), pattern);
    }

    /** The recipe with its tag outputs made items, by the resolver (a tag to an item of it, or "" for none). */
    public static Machine resolve(Machine m, Function<String, String> tagItem) {
        List<Out> outs = new ArrayList<>();
        for (Out o : m.outs()) {
            String id = o.id().startsWith("#") ? tagItem.apply(o.id()) : o.id();
            outs.add(new Out(id == null || id.isEmpty() ? o.id() : id, o.count(), o.chance()));
        }
        return new Machine(List.copyOf(outs), m.ins(), m.other(), m.pattern());
    }

    /** A Create sequence: each step's parts, without the half-made item it passes along; its loops. */
    private static void sequence(JsonObject json, List<Out> outs, List<In> ins, List<String> other) {
        if (!json.has("sequence") || !json.get("sequence").isJsonArray()) {
            return;
        }
        for (JsonElement step : json.getAsJsonArray("sequence")) {
            if (!step.isJsonObject()) continue;
            Machine inner = read(step.getAsJsonObject());
            Set<String> passed = new HashSet<>();
            inner.outs().forEach(o -> passed.add(o.id()));
            inner.ins().stream().filter(i -> !passed.contains(i.id())).forEach(ins::add);
            inner.other().stream().filter(s -> s.startsWith("takes ")).forEach(other::add);
        }
        // Its chances are weights, shared out over every result.
        if (outs.size() > 1) {
            double total = outs.stream().mapToDouble(o -> o.chance() == 100 ? 1 : o.chance()).sum();
            List<Out> shared = new ArrayList<>();
            for (Out o : outs) {
                double w = o.chance() == 100 ? 1 : o.chance();
                shared.add(new Out(o.id(), o.count(), Math.round(w / total * 1000) / 10.0));
            }
            outs.clear();
            outs.addAll(shared);
        }
    }

    /** A crafter's pattern (Create's mechanical crafting, any shaped machine): the key's items where it puts them. */
    private static void pattern(JsonObject json, List<In> ins, List<List<String>> pattern) {
        if (!json.has("pattern") || !json.get("pattern").isJsonArray() || !json.has("key") || !json.get("key").isJsonObject()) {
            return;
        }
        JsonObject keyMap = json.getAsJsonObject("key");
        Map<Character, Integer> uses = new LinkedHashMap<>();
        for (JsonElement line : json.getAsJsonArray("pattern")) {
            List<String> row = new ArrayList<>();
            for (char c : line.getAsString().toCharArray()) {
                String id = c == ' ' || !keyMap.has(String.valueOf(c)) ? "" : ingredientOf(keyMap.get(String.valueOf(c)));
                row.add(id);
                if (!id.isEmpty()) uses.merge(c, 1, Integer::sum);
            }
            pattern.add(row);
        }
        uses.forEach((c, n) -> ins.add(new In(ingredientOf(keyMap.get(String.valueOf(c))), n)));
    }

    /** The values worth saying: the time, energy, heat, loops, and the mod's own. */
    private static void words(JsonObject json, Shape shape, List<String> other) {
        boolean timed = false;
        Map<String, String> all = new LinkedHashMap<>(COMMON_WORDS);
        all.putAll(shape.words());
        for (Map.Entry<String, String> w : all.entrySet()) {
            JsonElement v = json.get(w.getKey());
            if (v == null || !v.isJsonPrimitive()) continue;
            String value = v.getAsString();
            switch (w.getValue()) {
                case "time" -> {
                    if (!timed && v.getAsJsonPrimitive().isNumber()) {
                        other.add(Math.round(v.getAsDouble() / 2.0) / 10.0 + " s");
                        timed = true;
                    }
                }
                case "heat" -> other.add(value.replace('_', ' '));
                case "loops" -> other.add(value + " loops");
                case "energy" -> other.add(value + " energy");
                case "xp" -> other.add(value + " xp");
                default -> other.add(w.getValue() + " " + value.replace('_', ' '));
            }
        }
    }

    static List<JsonElement> asList(JsonElement e) {
        if (e == null || e.isJsonNull()) return List.of();
        if (e.isJsonArray()) {
            List<JsonElement> out = new ArrayList<>();
            e.getAsJsonArray().forEach(out::add);
            return out;
        }
        return List.of(e);
    }

    /** The keys a mod wraps an item, a block or a fluid in: unwrapped, their count and chance kept from outside. */
    private static final List<String> WRAPPERS = List.of("item", "block", "basePredicate", "output", "ingredient", "stack");

    /** The inner object a wrapper holds, or null. */
    private static JsonObject wrapped(JsonObject o) {
        for (String w : WRAPPERS) {
            if (o.has(w) && o.get(w).isJsonObject()) {
                return o.getAsJsonObject(w);
            }
        }
        return null;
    }

    /** An ingredient as an id: an item's, "#" and a tag, the first of a list, through wrappers; "" for none. */
    static String ingredientOf(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                String id = ingredientOf(x);
                if (!id.isEmpty()) return id;
            }
            return "";
        }
        if (e.isJsonPrimitive()) return e.getAsString();
        JsonObject o = e.getAsJsonObject();
        for (String k : List.of("id", "item")) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) return o.get(k).getAsString();
        }
        if (o.has("tag") && o.get("tag").isJsonPrimitive()) return "#" + o.get("tag").getAsString();
        // A compound ingredient (NeoForge's "children", a list of "values"): its first.
        for (String k : List.of("children", "values", "items")) {
            if (o.has(k) && o.get(k).isJsonArray()) return ingredientOf(o.get(k));
        }
        JsonObject inner = wrapped(o);
        return inner == null ? "" : ingredientOf(inner);
    }

    /** A fluid or a chemical in words, by its amount; null for an item. */
    private static String notItem(JsonObject o, String key) {
        if (o.has("fluid")) {
            JsonElement f = o.get("fluid");
            String what = f.isJsonPrimitive() ? f.getAsString() : ingredientOf(f);
            return amount(o) + what;
        }
        String amount = o.has("amount") && o.get("amount").isJsonPrimitive() ? o.get("amount").getAsString() : null;
        boolean fluid = key.contains("fluid")
                || o.has("type") && o.get("type").isJsonPrimitive() && o.get("type").getAsString().contains("fluid")
                || o.has("fluid_tag");
        if (fluid) {
            String what = o.has("fluid_tag") ? "#" + o.get("fluid_tag").getAsString() : ingredientOf(o);
            return amount(o) + what;
        }
        boolean chemical = o.has("chemical") || key.contains("chemical") || key.contains("gas")
                || amount != null && !o.has("count");
        if (chemical) {
            String what = o.has("chemical") && o.get("chemical").isJsonPrimitive() ? o.get("chemical").getAsString() : ingredientOf(o);
            return (amount == null ? "" : amount + " ") + what;
        }
        return null;
    }

    private static String amount(JsonObject o) {
        return o.has("amount") && o.get("amount").isJsonPrimitive() ? o.get("amount").getAsString() + " mB " : "";
    }

    /** True for an object that names its slots (AE2's inscriber: top, middle, bottom), each an ingredient. */
    private static boolean namedSlots(JsonObject o) {
        if (o.has("id") || o.has("item") || o.has("tag") || o.has("type") || o.has("count") || o.has("amount")
                || wrapped(o) != null || o.size() == 0) {
            return false;
        }
        return o.entrySet().stream().allMatch(e -> e.getValue().isJsonPrimitive() || e.getValue().isJsonObject()
                || e.getValue().isJsonArray());
    }

    private static void readIn(JsonElement e, String key, List<In> ins, List<String> other) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonPrimitive() || e.isJsonArray()) {
            String id = ingredientOf(e);
            if (!id.isEmpty()) ins.add(new In(id, 1));
            return;
        }
        JsonObject o = e.getAsJsonObject();
        String not = notItem(o, key);
        if (not != null) {
            other.add("takes " + not);
            return;
        }
        if (namedSlots(o)) {
            // In the order the machine shows them: top, middle, bottom where named so.
            for (String slot : List.of("top", "middle", "bottom")) {
                if (o.has(slot)) readIn(o.get(slot), key, ins, other);
            }
            o.entrySet().stream().filter(s -> !List.of("top", "middle", "bottom").contains(s.getKey()))
                    .forEach(s -> readIn(s.getValue(), key, ins, other));
            return;
        }
        int count = o.has("count") && o.get("count").isJsonPrimitive() ? o.get("count").getAsInt() : 1;
        JsonObject inner = wrapped(o);
        if (inner != null && !o.has("id") && !o.has("tag") && !o.has("item") && inner.has("count") && count == 1) {
            count = inner.get("count").getAsInt();
        }
        String id = ingredientOf(o);
        if (!id.isEmpty()) ins.add(new In(id, Math.max(1, count)));
    }

    private static void readOut(JsonElement e, String key, double outerChance, List<Out> outs, List<String> other) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonPrimitive()) {
            outs.add(new Out(e.getAsString(), "1", outerChance));
            return;
        }
        if (!e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        String not = notItem(o, key);
        if (not != null) {
            other.add("makes " + not);
            return;
        }
        double chance = outerChance;
        if (o.has("chance") && o.get("chance").isJsonPrimitive()) {
            double c = o.get("chance").getAsDouble();
            chance = c <= 1 ? Math.round(c * 1000) / 10.0 : c;
        }
        String id = "";
        for (String k : List.of("id", "item")) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) id = o.get(k).getAsString();
        }
        if (id.isEmpty() && o.has("tag") && o.get("tag").isJsonPrimitive()) id = "#" + o.get("tag").getAsString();
        String count = o.has("count") && o.get("count").isJsonPrimitive() ? o.get("count").getAsString() : null;
        if (id.isEmpty()) {
            // A wrapper ({"item": {...}}, IE's basePredicate, a secondary's "output"): its count and chance
            // carry in.
            JsonObject inner = wrapped(o);
            if (inner == null) return;
            List<Out> got = new ArrayList<>();
            readOut(inner, key, chance, got, other);
            for (Out g : got) {
                outs.add(new Out(g.id(), count != null && "1".equals(g.count()) ? count : g.count(), g.chance()));
            }
            return;
        }
        outs.add(new Out(id, count == null ? "1" : count, chance));
    }

    /**
     * The recipe as the item page shows it: the item asked for as its result when it comes out at all (a
     * second output too), else its first; its inputs across the grid (a pattern of three by three as laid
     * out).
     */
    public static RecipeOps.Recipe recipe(String id, String type, Machine m, RecipeOps.Source source, String want) {
        if (m.outs().isEmpty()) {
            return null;
        }
        Out shown = m.outs().get(0);
        for (Out o : m.outs()) {
            if (o.id().equals(want)) {
                shown = o;
                break;
            }
        }
        List<String> cells = new ArrayList<>(List.of("", "", "", "", "", "", "", "", ""));
        if (!m.pattern().isEmpty() && m.pattern().size() <= 3 && m.pattern().stream().allMatch(r -> r.size() <= 3)) {
            for (int r = 0; r < m.pattern().size(); r++) {
                for (int c = 0; c < m.pattern().get(r).size(); c++) {
                    cells.set(r * 3 + c, m.pattern().get(r).get(c));
                }
            }
        } else if (m.ins().size() == 1) {
            cells.set(4, m.ins().get(0).id());
        } else {
            for (int i = 0; i < Math.min(9, m.ins().size()); i++) {
                cells.set(i, m.ins().get(i).id());
            }
        }
        return new RecipeOps.Recipe(id, type, shown.id(), shown.count(), List.copyOf(cells), source);
    }

    /** What the grid cannot show, in words: counts, chances, other outputs, fluids, chemicals, tools, time. */
    public static String words(Machine m) {
        List<String> words = new ArrayList<>();
        if (!m.pattern().isEmpty() && (m.pattern().size() > 3 || m.pattern().stream().anyMatch(r -> r.size() > 3))) {
            words.add(m.pattern().stream().mapToInt(List::size).max().orElse(0) + "x" + m.pattern().size() + " pattern");
        }
        for (In i : m.ins()) {
            if (i.count() > 1) words.add(i.count() + " " + i.id());
        }
        if (m.ins().size() > 9) {
            words.add(m.ins().size() + " inputs");
        }
        for (Out o : m.outs()) {
            if (m.outs().size() > 1 || o.chance() < 100) {
                words.add("makes " + (o.chance() < 100 ? o.chance() + "% " : "") + ("1".equals(o.count()) ? "" : o.count() + " ") + o.id());
            }
        }
        words.addAll(m.other());
        return String.join(", ", words);
    }
}
