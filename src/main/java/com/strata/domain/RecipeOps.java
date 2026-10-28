package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.CraftingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Client > Items > Crafting recipes reads and does: the designer's own recipes, and every recipe
 * of the game and its mods (game/crafting_recipes.csv) with the user's changes over them.
 */
public final class RecipeOps {

    private RecipeOps() {
    }

    /** When a change applies, said on the screens. */
    public static final String APPLIES = "Recipes apply at the next world load or /reload.";

    /** Where a recipe comes from, and what the user did to it. */
    public enum Source {
        /** The designer's own, strata:name. */
        OWN,
        /** The game's (or a mod's), as it is. */
        GAME,
        /** The game's, replaced by the user's version. */
        EDITED,
        /** The game's, turned off. */
        OFF
    }

    /** One recipe: its name (a game recipe's id, or the designer's name), its values and nine cells, and its source. */
    public record Recipe(String name, String type, String result, String count, List<String> cells, Source source) {
        /** The game's (edited, off, or as it is), not the designer's own. */
        public boolean game() {
            return source != Source.OWN;
        }

        /** Its group in the list: "Your recipes", or the game recipe's namespace. */
        public String group() {
            return game() ? name.substring(0, name.indexOf(':')) : "Your recipes";
        }
    }

    /** Every recipe: the designer's first, then the game's by namespace, each by name. */
    public static List<Recipe> recipes() {
        return recipes(CsvTable.read(Paths.table(Schema.Recipes.FILE)),
                GameLayer.read(GameLayer.dir().resolve(GameLayer.RECIPES)));
    }

    /** The recipes of the user's table and the game layer. */
    public static List<Recipe> recipes(List<Map<String, String>> rows, List<GameLayer.Row> game) {
        Map<String, Map<String, String>> own = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String name = cell(row, Schema.Recipes.NAME);
            if (!name.isEmpty()) own.put(name, row);
        }
        Map<String, Map<String, String>> gameRecipes = new LinkedHashMap<>();
        for (GameLayer.Row r : game) {
            if (GameLayer.RECIPE.equals(r.group())) {
                gameRecipes.computeIfAbsent(r.entity(), k -> new HashMap<>()).put(r.key(), r.value());
            }
        }
        List<Recipe> out = new ArrayList<>();
        own.forEach((name, row) -> {
            if (!name.contains(":")) out.add(recipe(name, row, Source.OWN));
        });
        gameRecipes.forEach((id, values) -> {
            Map<String, String> row = own.get(id);
            if (row == null) {
                out.add(recipe(id, values, Source.GAME));
            } else if (CraftingRecipe.OFF.equals(cell(row, Schema.Recipes.TYPE))) {
                out.add(recipe(id, values, Source.OFF));
            } else {
                out.add(recipe(id, row, Source.EDITED));
            }
        });
        out.sort(Comparator.comparing((Recipe r) -> r.game() ? 1 : 0).thenComparing(Recipe::group)
                .thenComparing(Recipe::name));
        return out;
    }

    private static Recipe recipe(String name, Map<String, String> values, Source source) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < CraftingRecipe.CELLS; i++) {
            cells.add(cell(values, Schema.Recipes.cell(i)));
        }
        return new Recipe(name, cell(values, Schema.Recipes.TYPE), cell(values, Schema.Recipes.RESULT),
                cell(values, Schema.Recipes.COUNT), List.copyOf(cells), source);
    }

    /** One recipe, or a new one's (shaped, empty, count 1). */
    public static Recipe formFor(String name) {
        if (name != null && !name.isEmpty()) {
            for (Recipe r : recipes()) {
                if (r.name().equals(name)) return r;
            }
        }
        List<String> empty = new ArrayList<>();
        for (int i = 0; i < CraftingRecipe.CELLS; i++) empty.add("");
        return new Recipe("", CraftingRecipe.SHAPED, "", "1", List.copyOf(empty), Source.OWN);
    }

    /** Why a recipe cannot be opened in the grid, or null: a special one has no grid. */
    public static String editProblem(Recipe r) {
        return CraftingRecipe.SPECIAL.equals(r.type())
                ? "It has no grid (fireworks, banner patterns, and the like): it can be turned off, not edited" : null;
    }

    /** Why the recipe cannot be saved, or null. */
    public static String problem(Recipe r, boolean isNew) {
        if (isNew) {
            String name = CraftingRecipe.nameProblem(r.name(), recipes().stream().map(Recipe::name).toList());
            if (name != null) return name;
        }
        return CraftingRecipe.problem(r.type(), r.cells(), r.result(), r.count());
    }

    /**
     * The item a grid cell draws: the cell's item, or for a "#tag" the item read for it with the recipes (an
     * item of the tag), else "".
     */
    public static String iconOf(String cell) {
        if (cell == null || !cell.startsWith("#")) return cell == null ? "" : cell;
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.RECIPES))) {
            if (GameLayer.ITEM_TAG.equals(r.group()) && r.entity().equals(cell)) return r.value();
        }
        return "";
    }

    /** The recipes that make one item: the designer's first, then the game's. */
    public static List<Recipe> forItem(String item) {
        List<Recipe> out = new ArrayList<>(recipes().stream().filter(r -> r.result().equals(item)).toList());
        // The furnace, smoker, campfire, stonecutter and smithing table's too.
        java.util.Set<String> names = new java.util.HashSet<>(out.stream().map(Recipe::name).toList());
        for (Recipe r : stationRecipes(item)) {
            if (names.add(r.name())) {
                out.add(r);
            }
        }
        return out;
    }

    /** The recipe types made at a station, not on the crafting grid, and the station's name. */
    public static final Map<String, String> STATIONS = Map.of(
            "minecraft:smelting", "Furnace", "minecraft:blasting", "Blast furnace", "minecraft:smoking", "Smoker",
            "minecraft:campfire_cooking", "Campfire", "minecraft:stonecutting", "Stonecutter",
            "minecraft:smithing_transform", "Smithing table");

    /** The cooking stations, one recipe type each: a furnace recipe can become a blast furnace one. */
    public static final List<String> COOKING = List.of("minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking");

    /** The grid cells a station recipe uses: a smithing table's three, else the middle one. */
    public static List<Integer> stationCells(String type) {
        if (!STATIONS.containsKey(type)) {
            return List.of();
        }
        return "minecraft:smithing_transform".equals(type) ? List.of(3, 4, 5) : List.of(4);
    }

    /** A station's name: the game's own, or a mod's machine type in words, "Crushing (mekanism)". */
    public static String stationName(String type) {
        String known = STATIONS.get(type);
        if (known != null || type == null) {
            return known == null ? "" : known;
        }
        int colon = type.indexOf(':');
        String path = type.substring(colon + 1).replace('_', ' ').replace('/', ' ');
        String words = path.isEmpty() ? type : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return colon > 0 ? words + " (" + type.substring(0, colon) + ")" : words;
    }

    /** A mod's machine recipe (Mekanism's crushing, Create's milling...): any type off the crafting grid. */
    static boolean isMachine(String type) {
        return type != null && !type.isEmpty() && !STATIONS.containsKey(type) && !type.startsWith("minecraft:");
    }

    /** The path of a station recipe's input in its JSON, by cell: template, base, addition, or ingredient. */
    private static String inputRoot(String type, int cell) {
        if ("minecraft:smithing_transform".equals(type)) {
            return cell == 3 ? "template" : cell == 4 ? "base" : "addition";
        }
        return "ingredient";
    }

    /**
     * SAVES A STATION RECIPE ON THE ITEM PAGE: its type (between the cooking stations), its input, result and
     * count, written as changes of the recipe's values - the one place Items > Data...
     */
    public static WriteReport saveStation(Recipe shown, String type, List<String> cells, String result, String count) {
        var kind = com.strata.data.JsonOverrides.RECIPE;
        Map<String, String> paths = new LinkedHashMap<>();
        for (OverrideOps.Field f : OverrideOps.fields(kind, shown.name())) {
            paths.put(f.path(), f.current());
        }
        if (isMachine(shown.type())) {
            return WriteReport.refused("A mod's machine recipe is changed in Items > Data... > Recipes");
        }
        Map<String, String> writes = new LinkedHashMap<>();
        if (!type.equals(shown.type())) {
            if (!COOKING.contains(type) || !COOKING.contains(shown.type())) {
                return WriteReport.refused("Only a cooking recipe changes station (furnace, blast furnace, smoker, campfire)");
            }
            writes.put("type", type);
        }
        for (int cell : stationCells(shown.type())) {
            String now = cells.get(cell) == null ? "" : cells.get(cell).strip();
            if (now.equals(shown.cells().get(cell))) {
                continue;
            }
            if (now.isEmpty()) {
                return WriteReport.refused("A station recipe needs its input: pick an item");
            }
            String root = inputRoot(shown.type(), cell);
            String key = now.startsWith("#") ? "tag" : "item";
            String path = paths.containsKey(root + "." + key) ? root + "." + key
                    : paths.containsKey(root + "[0]." + key) ? root + "[0]." + key : null;
            if (path == null) {
                return WriteReport.refused(now.startsWith("#") ? "This recipe takes an item here, not a tag"
                        : "This recipe takes a tag here: pick an item of it in Items > Data... > Recipes");
            }
            writes.put(path, now.startsWith("#") ? now.substring(1) : now);
        }
        if (!result.strip().equals(shown.result())) {
            String path = paths.containsKey("result.id") ? "result.id" : paths.containsKey("result.item") ? "result.item"
                    : paths.containsKey("result") ? "result" : null;
            if (path == null) {
                return WriteReport.refused("This recipe's result cannot be changed here");
            }
            writes.put(path, result.strip());
        }
        if (!count.strip().equals(shown.count())) {
            if (!paths.containsKey("result.count")) {
                return WriteReport.refused("This recipe always makes one: its count cannot be changed");
            }
            writes.put("result.count", count.strip());
        }
        if (writes.isEmpty()) {
            return WriteReport.refused("Nothing changed");
        }
        WriteReport report = new WriteReport();
        for (Map.Entry<String, String> w : writes.entrySet()) {
            report = report.ok() ? OverrideOps.set(kind, shown.name(), w.getKey(), w.getValue()) : report;
        }
        return report;
    }

    /** Whether the recipe is made at a station: shown on the grid, edited in Items > Data > Recipes. */
    public static boolean isStation(Recipe r) {
        return r != null && (STATIONS.containsKey(r.type()) || isMachine(r.type()));
    }

    /**
     * Every station recipe of the game and its mods that makes the item, as it loads (with the user's
     * changes): its input on the grid's middle cell, or a smithing table's template, base and addition across
     * its middle row.
     */
    public static List<Recipe> stationRecipes(String item) {
        List<Recipe> out = new ArrayList<>();
        Map<String, com.strata.data.JsonOverrides.Override> overrides =
                com.strata.data.JsonOverrides.overrides(com.strata.data.JsonOverrides.RECIPE);
        // A tag's item, read once for every recipe (IE's results are tags).
        Map<String, String> tagItems = new HashMap<>();
        for (GameLayer.Row row : GameLayer.read(GameLayer.dir().resolve(GameLayer.RECIPES))) {
            if (GameLayer.ITEM_TAG.equals(row.group())) tagItems.putIfAbsent(row.entity(), row.value());
        }
        java.util.function.Function<String, String> tagItem = t -> tagItems.getOrDefault(t, "");
        com.strata.data.JsonOverrides.game(com.strata.data.JsonOverrides.RECIPE).forEach((id, game) -> {
            com.strata.data.JsonOverrides.Override o = overrides.getOrDefault(id, com.strata.data.JsonOverrides.Override.NONE);
            com.google.gson.JsonObject json = o.isEmpty() ? game
                    : com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.RECIPE, game, o);
            Source src = o.off() ? Source.OFF : o.isEmpty() ? Source.GAME : Source.EDITED;
            String type = json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : "";
            Recipe r;
            if (isMachine(type)) {
                // Any of its outputs: Create's crushing makes experience nuggets too.
                if (!machineMakes(json, item, tagItem)) {
                    return;
                }
                try {
                    r = MachineRecipes.recipe(id, type, MachineRecipes.resolve(MachineRecipes.read(json), tagItem), src, item);
                } catch (RuntimeException e) {
                    r = null;
                }
            } else {
                r = station(id, json, src);
            }
            if (r != null && r.result().equals(item)) {
                out.add(r);
            }
        });
        out.sort(Comparator.comparing(Recipe::type).thenComparing(Recipe::name));
        return out;
    }

    /** A station recipe from its JSON, or null for any other. */
    public static Recipe station(String id, com.google.gson.JsonObject json, Source source) {
        if (json == null || !json.has("type") || !json.get("type").isJsonPrimitive()) {
            return null;
        }
        String type = json.get("type").getAsString();
        if (isMachine(type)) {
            try {
                return machine(id, type, json, source, "");
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (!STATIONS.containsKey(type)) {
            return null;
        }
        List<String> cells = new ArrayList<>(List.of("", "", "", "", "", "", "", "", ""));
        if (type.equals("minecraft:smithing_transform")) {
            cells.set(3, ingredient(json.get("template")));
            cells.set(4, ingredient(json.get("base")));
            cells.set(5, ingredient(json.get("addition")));
        } else {
            cells.set(4, ingredient(json.get("ingredient")));
        }
        String result = "";
        String count = "1";
        com.google.gson.JsonElement res = json.get("result");
        if (res != null && res.isJsonPrimitive()) {
            result = res.getAsString();
        } else if (res != null && res.isJsonObject()) {
            com.google.gson.JsonObject o = res.getAsJsonObject();
            result = o.has("id") ? o.get("id").getAsString() : o.has("item") ? o.get("item").getAsString() : "";
            if (o.has("count")) {
                count = o.get("count").getAsString();
            }
        }
        return new Recipe(id, type, result, count, List.copyOf(cells), source);
    }

    /**
     * A MOD'S MACHINE RECIPE as the item page shows it, read by {@link MachineRecipes}: the item asked for
     * when it comes out at all, else its first output.
     */
    public static Recipe machine(String id, String type, com.google.gson.JsonObject json, Source source, String want) {
        return MachineRecipes.recipe(id, type, MachineRecipes.resolve(MachineRecipes.read(json), RecipeOps::iconOf), source, want);
    }

    /** What a machine recipe's grid cannot show, in words. */
    public static String machineWords(com.google.gson.JsonObject json, String shown) {
        return MachineRecipes.words(MachineRecipes.read(json));
    }

    /** A machine recipe's words (machineWords) as it loads, with the user's changes; "" for any other recipe. */
    public static String machineDetail(Recipe r) {
        if (r == null || !isMachine(r.type())) {
            return "";
        }
        com.google.gson.JsonObject game = com.strata.data.JsonOverrides.game(com.strata.data.JsonOverrides.RECIPE).get(r.name());
        if (game == null) {
            return "";
        }
        com.strata.data.JsonOverrides.Override o = com.strata.data.JsonOverrides.overrides(
                com.strata.data.JsonOverrides.RECIPE).getOrDefault(r.name(), com.strata.data.JsonOverrides.Override.NONE);
        try {
            return machineWords(o.isEmpty() ? game : com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.RECIPE, game, o),
                    r.result());
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Whether an item comes out of a machine recipe, as any of its outputs (a tag output by its item). */
    static boolean machineMakes(com.google.gson.JsonObject json, String item, java.util.function.Function<String, String> tagItem) {
        try {
            return MachineRecipes.read(json).outs().stream()
                    .anyMatch(o -> o.id().equals(item) || o.id().startsWith("#") && item.equals(tagItem.apply(o.id())));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** An ingredient as a cell: an item's id, "#" and a tag, the first of a list; "" for none. */
    private static String ingredient(com.google.gson.JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "";
        }
        if (e.isJsonArray()) {
            return e.getAsJsonArray().isEmpty() ? "" : ingredient(e.getAsJsonArray().get(0));
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        com.google.gson.JsonObject o = e.getAsJsonObject();
        if (o.has("item") && o.get("item").isJsonPrimitive()) {
            return o.get("item").getAsString();
        }
        return o.has("tag") && o.get("tag").isJsonPrimitive() ? "#" + o.get("tag").getAsString() : "";
    }

    /** What a station recipe takes, in words: "Furnace, 10 s, 0.7 xp". */
    public static String stationDetail(Recipe r) {
        StringBuilder out = new StringBuilder(stationName(r.type()));
        com.google.gson.JsonObject game = com.strata.data.JsonOverrides.game(com.strata.data.JsonOverrides.RECIPE).get(r.name());
        if (game != null) {
            com.strata.data.JsonOverrides.Override o = com.strata.data.JsonOverrides.overrides(
                    com.strata.data.JsonOverrides.RECIPE).getOrDefault(r.name(), com.strata.data.JsonOverrides.Override.NONE);
            com.google.gson.JsonObject json = o.isEmpty() ? game
                    : com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.RECIPE, game, o);
            if (json.has("cookingtime")) {
                out.append(", ").append(Math.round(json.get("cookingtime").getAsDouble() / 2.0) / 10.0).append(" s");
            }
            if (json.has("experience")) {
                out.append(", ").append(json.get("experience").getAsString()).append(" xp");
            }
        }
        return out.toString();
    }

    /** Saves the recipe, a whole row: the designer's own, or the user's version of a game recipe (by its id). */
    public static WriteReport save(String originalName, Recipe r) {
        boolean isNew = originalName == null || originalName.isEmpty();
        String problem = problem(r, isNew);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String name = isNew ? r.name().strip() : originalName;
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Recipes.TYPE, r.type());
        values.put(Schema.Recipes.RESULT, r.result().strip());
        values.put(Schema.Recipes.COUNT, r.count().strip());
        for (int i = 0; i < CraftingRecipe.CELLS; i++) {
            values.put(Schema.Recipes.cell(i), r.cells().get(i) == null ? "" : r.cells().get(i).strip());
        }
        return Commit.reloadIfOk(new WriteReport().record("the recipe", TableWriter.upsert(Schema.Recipes.FILE,
                Map.of(Schema.Recipes.NAME, name), values)));
    }

    /**
     * Removes recipes: the designer's own go; a game recipe's change goes, so the game's version is back
     * (Restore).
     */
    public static WriteReport remove(List<String> names) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String name : names) {
                report.cascaded(name.contains(":") ? "your change to " + name : "the recipe " + name,
                        TableWriter.delete(Schema.Recipes.FILE, Map.of(Schema.Recipes.NAME, name)));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Turns game recipes off (a row of type off), or on again (the row goes). */
    public static WriteReport setOff(List<Recipe> picked, boolean off) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (Recipe r : picked) {
                if (!r.game()) continue;
                Map<String, String> keys = Map.of(Schema.Recipes.NAME, r.name());
                if (off) {
                    report.record(r.name() + " off", TableWriter.upsert(Schema.Recipes.FILE, keys,
                            Map.of(Schema.Recipes.TYPE, CraftingRecipe.OFF)));
                } else if (r.source() == Source.OFF) {
                    report.cascaded(r.name() + " on", TableWriter.delete(Schema.Recipes.FILE, keys));
                }
            }
            return Commit.reloadIfOk(report);
        });
    }

    private static String cell(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").strip();
    }
}
