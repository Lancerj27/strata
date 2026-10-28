package com.strata.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A CRAFTING RECIPE, AS RULES. */
public final class CraftingRecipe {

    private CraftingRecipe() {
    }

    public static final String SHAPED = "shaped";
    public static final String SHAPELESS = "shapeless";
    public static final List<String> TYPES = List.of(SHAPED, SHAPELESS);
    /** A game recipe with no grid (fireworks, banner patterns): it can be turned off, not edited. */
    public static final String SPECIAL = "special";
    /** A user row that turns a game recipe off. */
    public static final String OFF = "off";

    /** The grid's cells. */
    public static final int CELLS = 9;
    /** The most a result stack may hold (the game's cap). */
    public static final int MAX_COUNT = 99;

    /** The recipe's id: a game recipe's own id (it has a colon), else strata:name. */
    public static String id(String name, String modId) {
        return name.contains(":") ? name : modId + ":" + name;
    }

    /**
     * The game's recipe JSON that turns a recipe off: NeoForge skips a recipe whose condition is false, so
     * the game's one under the same id is gone while the pack is on.
     */
    public static JsonObject offJson() {
        JsonObject out = new JsonObject();
        JsonArray conditions = new JsonArray();
        JsonObject never = new JsonObject();
        never.addProperty("type", "neoforge:false");
        conditions.add(never);
        out.add("neoforge:conditions", conditions);
        out.addProperty("type", "minecraft:crafting_shapeless");
        return out;
    }

    /** Why a new recipe's name cannot be used, or null. */
    public static String nameProblem(String name, Collection<String> existing) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return "Type a name";
        }
        if (!n.matches("[a-z0-9_]{1,40}")) {
            return "A name is 1 to 40 of a to z, 0 to 9, and _ (for example reinforced_door)";
        }
        return existing.contains(n) ? "A recipe named " + n + " exists" : null;
    }

    /** Why the recipe cannot be saved, or null. */
    public static String problem(String type, List<String> cells, String result, String count) {
        if (!TYPES.contains(type)) {
            return "The type must be shaped or shapeless";
        }
        if (cells.size() != CELLS) {
            return "A recipe has 9 cells";
        }
        if (cells.stream().allMatch(c -> c == null || c.isBlank())) {
            return "Put at least one item in the grid";
        }
        if (result == null || result.isBlank()) {
            return "Pick the result";
        }
        Integer n = ItemStats.whole(count == null ? "" : count);
        return n == null || n < 1 || n > MAX_COUNT ? "The count must be a whole number from 1 to 99" : null;
    }

    /** The game's recipe JSON (1.21.1: result by "id"). */
    public static JsonObject json(String type, List<String> cells, String result, int count) {
        JsonObject out = new JsonObject();
        out.addProperty("type", SHAPED.equals(type) ? "minecraft:crafting_shaped" : "minecraft:crafting_shapeless");
        out.addProperty("category", "misc");
        if (SHAPED.equals(type)) {
            // The rows and columns with an item, as the game trims a pattern.
            int top = 3, bottom = -1, left = 3, right = -1;
            for (int i = 0; i < CELLS; i++) {
                if (!blank(cells.get(i))) {
                    top = Math.min(top, i / 3);
                    bottom = Math.max(bottom, i / 3);
                    left = Math.min(left, i % 3);
                    right = Math.max(right, i % 3);
                }
            }
            Map<String, Character> keys = new LinkedHashMap<>();
            JsonArray pattern = new JsonArray();
            for (int row = top; row <= bottom; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = left; col <= right; col++) {
                    String item = cells.get(row * 3 + col);
                    if (blank(item)) {
                        line.append(' ');
                    } else {
                        line.append(keys.computeIfAbsent(item.strip(), k -> (char) ('A' + keys.size())));
                    }
                }
                pattern.add(line.toString());
            }
            out.add("pattern", pattern);
            JsonObject key = new JsonObject();
            keys.forEach((item, letter) -> key.add(String.valueOf(letter), ingredient(item)));
            out.add("key", key);
        } else {
            JsonArray ingredients = new JsonArray();
            for (String item : cells) {
                if (!blank(item)) {
                    ingredients.add(ingredient(item.strip()));
                }
            }
            out.add("ingredients", ingredients);
        }
        JsonObject res = new JsonObject();
        res.addProperty("id", result.strip());
        res.addProperty("count", count);
        out.add("result", res);
        return out;
    }

    /** A cell's ingredient: "#tag" (kept from a game recipe) is any item of the tag. */
    private static JsonObject ingredient(String item) {
        JsonObject o = new JsonObject();
        if (item.startsWith("#")) {
            o.addProperty("tag", item.substring(1));
        } else {
            o.addProperty("item", item);
        }
        return o;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
