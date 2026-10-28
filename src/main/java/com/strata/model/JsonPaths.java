package com.strata.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ANY JSON AS A LIST OF FIELDS, pure. */
public final class JsonPaths {

    private JsonPaths() {
    }

    /** Every value in the JSON, by its path, in file order. */
    public static Map<String, String> flatten(JsonElement json) {
        Map<String, String> out = new LinkedHashMap<>();
        walk(json, "", out);
        return out;
    }

    private static void walk(JsonElement e, String path, Map<String, String> out) {
        if (e == null || e.isJsonNull()) {
            out.put(path, "null");
        } else if (e.isJsonPrimitive()) {
            out.put(path, e.getAsJsonPrimitive().getAsString());
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) {
                walk(a.get(i), path + "[" + i + "]", out);
            }
        } else {
            for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
                walk(m.getValue(), path.isEmpty() ? m.getKey() : path + "." + m.getKey(), out);
            }
        }
    }

    /** One step of a path: an object key, or an array index. */
    private record Step(String key, int index) {
    }

    private static List<Step> steps(String path) {
        List<Step> out = new ArrayList<>();
        for (String part : path.split("\\.")) {
            int bracket = part.indexOf('[');
            String key = bracket < 0 ? part : part.substring(0, bracket);
            if (!key.isEmpty()) {
                out.add(new Step(key, -1));
            }
            while (bracket >= 0) {
                int close = part.indexOf(']', bracket);
                out.add(new Step(null, Integer.parseInt(part.substring(bracket + 1, close))));
                bracket = part.indexOf('[', close);
            }
        }
        return out;
    }

    /** The value at a path, or null when the JSON has none there. */
    public static JsonElement get(JsonElement root, String path) {
        JsonElement at = root;
        for (Step s : steps(path)) {
            if (at == null) {
                return null;
            }
            if (s.key() != null) {
                at = at.isJsonObject() ? at.getAsJsonObject().get(s.key()) : null;
            } else {
                at = at.isJsonArray() && s.index() < at.getAsJsonArray().size() ? at.getAsJsonArray().get(s.index()) : null;
            }
        }
        return at;
    }

    /** A copy of the JSON with one value set. */
    public static JsonObject set(JsonObject root, String path, String raw) {
        JsonObject copy = root.deepCopy();
        List<Step> steps = steps(path);
        if (steps.isEmpty()) {
            return null;
        }
        JsonElement parent = copy;
        for (int i = 0; i < steps.size() - 1; i++) {
            parent = step(parent, steps.get(i));
            if (parent == null) {
                return null;
            }
        }
        Step last = steps.getLast();
        JsonElement old = step(parent, last);
        if (old == null || !old.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive value = typed(old.getAsJsonPrimitive(), raw == null ? "" : raw.strip());
        if (value == null) {
            return null;
        }
        if (last.key() != null) {
            parent.getAsJsonObject().add(last.key(), value);
        } else {
            parent.getAsJsonArray().set(last.index(), value);
        }
        return copy;
    }

    private static JsonElement step(JsonElement at, Step s) {
        if (s.key() != null) {
            return at.isJsonObject() ? at.getAsJsonObject().get(s.key()) : null;
        }
        return at.isJsonArray() && s.index() < at.getAsJsonArray().size() ? at.getAsJsonArray().get(s.index()) : null;
    }

    /** The typed value, or null when it cannot be the kind the old one was. */
    static JsonPrimitive typed(JsonPrimitive old, String raw) {
        if (old.isBoolean()) {
            return raw.equals("true") || raw.equals("false") ? new JsonPrimitive(Boolean.parseBoolean(raw)) : null;
        }
        if (old.isNumber()) {
            try {
                return raw.contains(".") || raw.contains("e") ? new JsonPrimitive(Double.parseDouble(raw))
                        : new JsonPrimitive(Long.parseLong(raw));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new JsonPrimitive(raw);
    }

    // ---- LISTS AND MAPS: a tag's members, a structure's spawns, a loot pool's entries, a data map's items.

    /**
     * The element a field belongs to: the innermost list element on its path ("spawns[0]" for
     * "spawns[0].weight", "values[3]" for itself), else the map member holding it ("values.minecraft:coal"
     * for "values.minecraft:coal.burn_time").
     */
    public static String elementOf(String path) {
        if (path.endsWith("]")) {
            return path;
        }
        int bracket = path.lastIndexOf(']');
        if (bracket >= 0) {
            return path.substring(0, bracket + 1);
        }
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(0, dot) : null;
    }

    /**
     * A copy with the element duplicated: a list element appended as a copy of itself; a map member copied
     * under a new key (a fuel time for another item).
     */
    public static JsonObject duplicate(JsonObject root, String element, String newKey) {
        JsonObject copy = root.deepCopy();
        List<Step> steps = steps(element);
        if (steps.isEmpty()) {
            return null;
        }
        JsonElement parent = copy;
        for (int i = 0; i < steps.size() - 1; i++) {
            parent = step(parent, steps.get(i));
            if (parent == null) {
                return null;
            }
        }
        Step last = steps.getLast();
        JsonElement value = step(parent, last);
        if (value == null) {
            return null;
        }
        if (last.key() == null) {
            parent.getAsJsonArray().add(value.deepCopy());
            return copy;
        }
        String key = newKey == null ? "" : newKey.strip();
        if (key.isEmpty() || !parent.isJsonObject() || parent.getAsJsonObject().has(key)) {
            return null;
        }
        parent.getAsJsonObject().add(key, value.deepCopy());
        return copy;
    }

    /** A copy with the element taken out of its list or map. */
    public static JsonObject remove(JsonObject root, String element) {
        JsonObject copy = root.deepCopy();
        List<Step> steps = steps(element);
        if (steps.isEmpty()) {
            return null;
        }
        JsonElement parent = copy;
        for (int i = 0; i < steps.size() - 1; i++) {
            parent = step(parent, steps.get(i));
            if (parent == null) {
                return null;
            }
        }
        Step last = steps.getLast();
        if (step(parent, last) == null) {
            return null;
        }
        if (last.key() == null) {
            parent.getAsJsonArray().remove(last.index());
        } else {
            parent.getAsJsonObject().remove(last.key());
        }
        return copy;
    }

    /**
     * What a field is, for the value control: a value that is true or false is a switch; a field that names
     * an item by its path (an ingredient's or a key's "item", a result's "id") is an item, picked; any other
     * is typed.
     */
    public static ValueType typeOf(String path, String shown) {
        String s = shown == null ? "" : shown.strip();
        if (s.equals("true") || s.equals("false")) {
            return ValueType.BOOL;
        }
        String p = path == null ? "" : path;
        boolean namesItem = p.equals("item") || p.endsWith(".item") || p.equals("result.id") || p.endsWith(".result.id");
        if (namesItem && !s.startsWith("#") && (s.isEmpty() || s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))) {
            return ValueType.registry(ValueType.Registry.ITEM);
        }
        return ValueType.TEXT;
    }

    /** Why a typed value cannot replace the one at this path, or null when it can. */
    public static String problem(JsonObject root, String path, String raw) {
        JsonElement old = get(root, path);
        if (old == null || !old.isJsonPrimitive()) {
            return "No value at " + path;
        }
        if (typed(old.getAsJsonPrimitive(), raw == null ? "" : raw.strip()) == null) {
            return old.getAsJsonPrimitive().isBoolean() ? "true or false" : "a number";
        }
        return null;
    }
}
