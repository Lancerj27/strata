package com.strata.runtime;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.model.CraftingRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** EVERY CRAFTING RECIPE OF THE GAME AND ITS MODS, IN THE GAME LAYER. */
public final class GameRecipes {

    private GameRecipes() {
    }

    private static final String COMMENT = """
            The GAME LAYER: every crafting recipe, rewritten when a server starts and after /reload. Do not edit: your changes go in recipes.csv.
            group recipe: key type (shaped, shapeless, special), result, count, s0 to s8 (the 3x3 grid; an item id or #tag).
            group item_tag: a #tag the grid uses, key icon: an item of it, the one the grid draws.""";

    /** Each item tag a recipe uses, to one of its items: what the grid draws for it. */
    private static final Map<String, String> TAG_ICONS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Reads the recipes as a server starts (game bus). */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        extract(server.getRecipeManager(), server.registryAccess());
    }

    /** Reads them again after /reload (game bus; a player joining is not a reload). */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            MinecraftServer server = event.getPlayerList().getServer();
            extract(server.getRecipeManager(), server.registryAccess());
        }
    }

    /**
     * Reads the recipes a player's game was sent as it joins a world: the editor runs in the player's game,
     * which may be on another machine than the server, so it records what it was sent.
     */
    public static void fromPlayersGame(RecipeManager recipes, HolderLookup.Provider registries) {
        extract(recipes, registries);
    }

    private static void extract(RecipeManager recipeManager, HolderLookup.Provider registries) {
        List<Map.Entry<String, net.minecraft.world.item.crafting.CraftingRecipe>> found = new ArrayList<>();
        for (RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> holder
                : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            found.add(Map.entry(holder.id().toString(), holder.value()));
        }
        extract(found, registries);
    }

    /** READ AT GAME START, LIKE EVERYTHING ELSE. */
    public static void onLoadComplete(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        event.enqueueWork(GameRecipes::fromDataPacks);
    }

    private static void fromDataPacks() {
        try {
            net.minecraft.server.packs.repository.PackRepository repo =
                    net.minecraft.server.packs.repository.ServerPacksSource.createVanillaTrustedRepository();
            net.neoforged.neoforge.resource.ResourcePackLoader.populatePackRepository(repo,
                    net.minecraft.server.packs.PackType.SERVER_DATA, true);
            repo.reload();
            // The game's own and every mod's; not the game's experiments (feature packs).
            repo.setSelected(repo.getAvailablePacks().stream()
                    .filter(p -> p.location().source() != net.minecraft.server.packs.repository.PackSource.FEATURE)
                    .map(net.minecraft.server.packs.repository.Pack::getId).toList());
            HolderLookup.Provider registries = HolderLookup.Provider.create(java.util.stream.Stream.of(
                    BuiltInRegistries.ITEM.asLookup(), BuiltInRegistries.BLOCK.asLookup(),
                    BuiltInRegistries.DATA_COMPONENT_TYPE.asLookup()));
            var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, registries);
            List<Map.Entry<String, net.minecraft.world.item.crafting.CraftingRecipe>> found = new ArrayList<>();
            try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                    net.minecraft.server.packs.PackType.SERVER_DATA, repo.openAllSelected())) {
                readTagIcons(resources);
                // EVERY DATA KIND'S GAME VALUES AT GAME START TOO: machine recipes, structures, features and
                // the rest, before any world.
                try {
                    com.strata.data.JsonOverrides.extract(resources);
                } catch (Exception | LinkageError e) {
                    Strata.LOGGER.warn("Could not read the data packs' values at game start: {}", e.toString());
                }
                var files = resources.listResources("recipe", loc -> loc.getPath().endsWith(".json"));
                for (net.minecraft.resources.ResourceLocation loc : files.keySet()) {
                    String path = loc.getPath();
                    String id = loc.getNamespace() + ":"
                            + path.substring("recipe/".length(), path.length() - ".json".length());
                    try (var reader = files.get(loc).openAsReader()) {
                        JsonElement json = com.google.gson.JsonParser.parseReader(reader);
                        // A recipe a mod turns on only with another mod: left out when its conditions fail.
                        if (json.isJsonObject() && json.getAsJsonObject().has("neoforge:conditions")
                                && !conditionsHold(json.getAsJsonObject().get("neoforge:conditions"), ops)) {
                            continue;
                        }
                        net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, json).result().ifPresent(r -> {
                            if (r instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting) {
                                found.add(Map.entry(id, crafting));
                            }
                        });
                    } catch (Exception | LinkageError e) {
                        Strata.LOGGER.debug("Recipes: could not read {}: {}", id, e.toString());
                    }
                }
            }
            extract(found, registries);
        } catch (Exception | LinkageError e) {
            Strata.LOGGER.warn("Recipes: could not read the game's recipes at game start: {}", e.toString());
        }
    }

    /**
     * Each item tag's first item, from the tag files of the game and each mod (data/<ns>/tags/item): at game
     * start tags are not bound yet, so the grid would have no item to draw for "#c:rods/wooden".
     */
    private static void readTagIcons(net.minecraft.server.packs.resources.ResourceManager resources) {
        Map<String, List<String>> members = new java.util.HashMap<>();
        var stacks = resources.listResourceStacks("tags/item", loc -> loc.getPath().endsWith(".json"));
        for (net.minecraft.resources.ResourceLocation loc : stacks.keySet()) {
            String path = loc.getPath();
            String tag = loc.getNamespace() + ":" + path.substring("tags/item/".length(), path.length() - ".json".length());
            List<String> values = members.computeIfAbsent(tag, k -> new ArrayList<>());
            for (var resource : stacks.get(loc)) {
                try (var reader = resource.openAsReader()) {
                    JsonElement json = com.google.gson.JsonParser.parseReader(reader);
                    if (!json.isJsonObject() || !json.getAsJsonObject().has("values")) continue;
                    for (JsonElement v : json.getAsJsonObject().getAsJsonArray("values")) {
                        String entry = v.isJsonPrimitive() ? v.getAsString()
                                : v.isJsonObject() && v.getAsJsonObject().has("id")
                                ? v.getAsJsonObject().get("id").getAsString() : "";
                        if (!entry.isEmpty()) values.add(entry);
                    }
                } catch (Exception e) {
                    Strata.LOGGER.debug("Recipes: could not read the tag {}: {}", tag, e.toString());
                }
            }
        }
        for (String tag : members.keySet()) {
            String item = firstItem(tag, members, new HashSet<>());
            if (item != null) TAG_ICONS.putIfAbsent("#" + tag, item);
        }
    }

    private static String firstItem(String tag, Map<String, List<String>> members, Set<String> seen) {
        if (!seen.add(tag)) return null;
        for (String v : members.getOrDefault(tag, List.of())) {
            String item = v.startsWith("#") ? firstItem(v.substring(1), members, seen) : v;
            if (item != null && net.minecraft.resources.ResourceLocation.tryParse(item) != null
                    && BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse(item))) {
                return item;
            }
        }
        return null;
    }

    /** NeoForge's conditions on a recipe file, as the game checks them; a mod's own condition we cannot check holds. */
    private static boolean conditionsHold(JsonElement conditions, net.minecraft.resources.RegistryOps<JsonElement> ops) {
        try {
            var parsed = net.neoforged.neoforge.common.conditions.ICondition.LIST_CODEC.parse(ops, conditions).result();
            if (parsed.isEmpty()) return true;
            for (var c : parsed.get()) {
                if (c instanceof net.neoforged.neoforge.common.conditions.ModLoadedCondition loaded
                        && !net.neoforged.fml.ModList.get().isLoaded(loaded.modid())) return false;
                if (c instanceof net.neoforged.neoforge.common.conditions.FalseCondition) return false;
                if (c instanceof net.neoforged.neoforge.common.conditions.NotCondition not
                        && not.value() instanceof net.neoforged.neoforge.common.conditions.ModLoadedCondition loaded
                        && net.neoforged.fml.ModList.get().isLoaded(loaded.modid())) return false;
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    private static void extract(List<Map.Entry<String, net.minecraft.world.item.crafting.CraftingRecipe>> found,
                                HolderLookup.Provider registries) {
        try {
            Set<String> replaced = new HashSet<>();
            for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Recipes.FILE))) {
                String name = row.getOrDefault(Schema.Recipes.NAME, "").strip();
                if (name.contains(":")) replaced.add(name);
            }
            List<GameLayer.Row> rows = new ArrayList<>();
            // The replaced recipes keep the game's rows from the last read.
            for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.RECIPES))) {
                if (replaced.contains(r.entity())) rows.add(r);
            }
            int count = 0;
            for (var holder : found) {
                String id = holder.getKey();
                // Strata's own recipes are the user's rows, not the game's.
                if (id.startsWith(Strata.MOD_ID + ":") || replaced.contains(id)) {
                    continue;
                }
                try {
                    rows.addAll(rows(id, holder.getValue(), registries));
                    count++;
                } catch (RuntimeException | LinkageError e) {
                    Strata.LOGGER.debug("Recipes: could not read {}: {}", id, e.toString());
                }
            }
            // The item each #tag the grids use draws as: this read's, else the last read's.
            Set<String> tags = new java.util.TreeSet<>();
            for (GameLayer.Row r : rows) {
                if (GameLayer.RECIPE.equals(r.group()) && r.value().startsWith("#")) tags.add(r.value());
            }
            for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.RECIPES))) {
                if (GameLayer.ITEM_TAG.equals(r.group()) && !"minecraft:barrier".equals(r.value())) {
                    TAG_ICONS.putIfAbsent(r.entity(), r.value());
                }
            }
            for (String tag : tags) {
                String icon = TAG_ICONS.get(tag);
                if (icon != null) rows.add(new GameLayer.Row(tag, GameLayer.ITEM_TAG, "icon", icon));
            }
            if (GameLayer.write(GameLayer.dir().resolve(GameLayer.RECIPES), COMMENT, rows)) {
                Strata.LOGGER.info("Game layer: extracted {} crafting recipe(s)", count);
            }
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Recipes: could not read the game's crafting recipes: {}", e.toString());
        }
    }

    /** One recipe's rows: its type, its result and count, and its filled cells. */
    private static List<GameLayer.Row> rows(String id, net.minecraft.world.item.crafting.CraftingRecipe recipe,
                                            HolderLookup.Provider registries) {
        List<GameLayer.Row> out = new ArrayList<>();
        String[] cells = new String[CraftingRecipe.CELLS];
        String type;
        if (recipe instanceof ShapedRecipe shaped && shaped.getWidth() <= 3 && shaped.getHeight() <= 3) {
            type = CraftingRecipe.SHAPED;
            List<Ingredient> ings = shaped.getIngredients();
            // A pattern sits as a player lays it: one wide in the middle column, and shorter than the grid at
            // the bottom.
            int left = shaped.getWidth() == 1 ? 1 : 0;
            int top = 3 - shaped.getHeight();
            for (int row = 0; row < shaped.getHeight(); row++) {
                for (int col = 0; col < shaped.getWidth(); col++) {
                    cells[(row + top) * 3 + col + left] = cell(ings.get(row * shaped.getWidth() + col));
                }
            }
        } else if (recipe instanceof ShapelessRecipe && recipe.getIngredients().size() <= CraftingRecipe.CELLS) {
            type = CraftingRecipe.SHAPELESS;
            List<Ingredient> ings = recipe.getIngredients();
            for (int i = 0; i < ings.size(); i++) {
                cells[i] = cell(ings.get(i));
            }
        } else {
            type = CraftingRecipe.SPECIAL;
        }
        out.add(value(id, Schema.Recipes.TYPE, type));
        ItemStack result = recipe.getResultItem(registries);
        if (!result.isEmpty()) {
            out.add(value(id, Schema.Recipes.RESULT, BuiltInRegistries.ITEM.getResourceKey(result.getItem())
                    .map(k -> k.location().toString()).orElse("")));
            out.add(value(id, Schema.Recipes.COUNT, String.valueOf(result.getCount())));
        }
        for (int i = 0; i < CraftingRecipe.CELLS; i++) {
            if (cells[i] != null && !cells[i].isEmpty()) {
                out.add(value(id, Schema.Recipes.cell(i), cells[i]));
            }
        }
        return out;
    }

    /** A cell from an ingredient: its tag as "#tag", else its first item, else "" (empty). */
    private static String cell(Ingredient ing) {
        if (ing.isEmpty()) {
            return "";
        }
        JsonElement json = Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, ing).result().orElse(null);
        ItemStack[] items = ing.getItems();
        if (json != null && json.isJsonObject() && json.getAsJsonObject().has("tag")) {
            String tag = "#" + json.getAsJsonObject().get("tag").getAsString();
            // Tags are bound when a server reads: its first item is the one to draw.
            if (items.length > 0 && !items[0].is(net.minecraft.world.item.Items.BARRIER)) {
                BuiltInRegistries.ITEM.getResourceKey(items[0].getItem())
                        .ifPresent(k -> TAG_ICONS.put(tag, k.location().toString()));
            }
            return tag;
        }
        return items.length == 0 ? "" : BuiltInRegistries.ITEM.getResourceKey(items[0].getItem())
                .map(k -> k.location().toString()).orElse("");
    }

    private static GameLayer.Row value(String id, String key, String value) {
        return new GameLayer.Row(id, GameLayer.RECIPE, key, value);
    }
}
