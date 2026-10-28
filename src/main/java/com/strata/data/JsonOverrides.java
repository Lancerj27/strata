package com.strata.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strata.Strata;
import com.strata.model.JsonPaths;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * THE OVERRIDE SHEET: any kind of datapack JSON - recipes first - read from the live game and
 * changed by override, for the game's entries and every mod's alike.
 */
public final class JsonOverrides {

    private JsonOverrides() {
    }

    /** HOW A KIND IS SWITCHED OFF, the one way that is safe for it. */
    public enum Off { CONDITIONS, EMPTY_BIOMES, COUNT_ZERO, EMPTY_POOLS, EMPTY_VALUES, IMPOSSIBLE, NONE }

    /** HOW THE GAME COMBINES A KIND ACROSS PACKS. */
    public enum Merge { TOP, TAG, DATA_MAP }

    /** One kind of datapack JSON: its id, the folder under data/&lt;namespace&gt;/, how it goes off, how it merges. */
    public record Kind(String id, String folder, String label, Off off, Merge merge) {
        public Kind(String id, String folder, String label, Off off) {
            this(id, folder, label, off, Merge.TOP);
        }
    }

    public static final Kind RECIPE = new Kind("recipe", "recipe", "Recipes", Off.CONDITIONS);
    /** Structures: where each may generate, and what spawns in it (spawn_overrides). */
    public static final Kind STRUCTURE = new Kind("structure", "worldgen/structure", "Structures", Off.EMPTY_BIOMES);
    /** Structure sets: how far apart their structures are (spacing, separation). */
    public static final Kind STRUCTURE_SET = new Kind("structure_set", "worldgen/structure_set", "Structure spacing",
            Off.NONE);
    /** Placed features: ores, trees, lakes... */
    public static final Kind PLACED_FEATURE = new Kind("placed_feature", "worldgen/placed_feature", "Features",
            Off.COUNT_ZERO);
    /** Configured features: what a feature is - an ore's vein size, the blocks it replaces. */
    public static final Kind CONFIGURED_FEATURE = new Kind("configured_feature", "worldgen/configured_feature",
            "Feature settings", Off.NONE);

    /**
     * Biomes: temperature, rain, the sky's, fog's, water's, grass's and leaves' colours, music and ambience;
     * and the carvers and features each places, one by one - a cave carver or an ore taken out of one biome
     * by Remove element.
     */
    public static final Kind BIOME = new Kind("biome", "worldgen/biome", "Biomes: climate, looks, features", Off.NONE);
    /** Every loot table: chests, blocks, mobs, fishing, archaeology, bartering, gifts, a mod's. */
    public static final Kind LOOT_TABLE = new Kind("loot_table", "loot_table", "Loot tables", Off.EMPTY_POOLS);
    // TAGS OF EVERY REGISTRY THAT DECIDES PLAY: what a tool mines, what burns, what a mob eats or is tamed
    // with, which biomes a structure takes.
    public static final Kind ITEM_TAG = new Kind("item_tag", "tags/item", "Item tags", Off.EMPTY_VALUES, Merge.TAG);
    public static final Kind BLOCK_TAG = new Kind("block_tag", "tags/block", "Block tags", Off.EMPTY_VALUES, Merge.TAG);
    public static final Kind ENTITY_TAG = new Kind("entity_tag", "tags/entity_type", "Mob tags", Off.EMPTY_VALUES, Merge.TAG);
    public static final Kind BIOME_TAG = new Kind("biome_tag", "tags/worldgen/biome", "Biome tags", Off.EMPTY_VALUES,
            Merge.TAG);
    public static final Kind DAMAGE_TAG = new Kind("damage_tag", "tags/damage_type", "Damage type tags",
            Off.EMPTY_VALUES, Merge.TAG);
    public static final Kind ENCHANTMENT_TAG = new Kind("enchantment_tag", "tags/enchantment", "Enchantment tags",
            Off.EMPTY_VALUES, Merge.TAG);
    // NEOFORGE DATA MAPS: an item's fuel time and compost chance, a block's waxed and oxidized forms, a mob's
    // monster-room weight - and any a mod adds.
    public static final Kind ITEM_DATA = new Kind("item_data", "data_maps/item", "Item data (fuel, compost)",
            Off.EMPTY_VALUES, Merge.DATA_MAP);
    public static final Kind BLOCK_DATA = new Kind("block_data", "data_maps/block", "Block data (waxing, oxidizing)",
            Off.EMPTY_VALUES, Merge.DATA_MAP);
    public static final Kind ENTITY_DATA = new Kind("entity_data", "data_maps/entity_type", "Mob data maps",
            Off.EMPTY_VALUES, Merge.DATA_MAP);
    /** Enchantments (1.21: data): levels, cost, weight, what they go on, what they do. */
    public static final Kind ENCHANTMENT = new Kind("enchantment", "enchantment", "Enchantments", Off.NONE);
    /** Damage types: exhaustion, scaling with difficulty, the death message. */
    public static final Kind DAMAGE_TYPE = new Kind("damage_type", "damage_type", "Damage types", Off.NONE);
    /**
     * Brewing: not data - the stand's mixes are built in code, and read and set by runtime.BrewingData in
     * NeoForge's brewing event, last.
     */
    public static final Kind BREWING = new Kind("brewing", "", "Brewing", Off.NONE);
    /** Game rules: read and set in code by runtime.GameRulesData, carried by the profile. */
    public static final Kind GAME_RULES = new Kind("game_rule", "", "Game rules", Off.NONE);
    /** Advancements. */
    public static final Kind ADVANCEMENT = new Kind("advancement", "advancement", "Advancements", Off.IMPOSSIBLE);

    // PLAN C STEP 6: the tags of every other registry the game reads tags of.
    public static final Kind STRUCTURE_TAG = tag("structure_tag", "tags/worldgen/structure", "Structure tags");
    public static final Kind FLUID_TAG = tag("fluid_tag", "tags/fluid", "Fluid tags");
    public static final Kind BANNER_PATTERN_TAG = tag("banner_pattern_tag", "tags/banner_pattern", "Banner pattern tags");
    public static final Kind GAME_EVENT_TAG = tag("game_event_tag", "tags/game_event", "Game event tags (vibrations)");
    public static final Kind INSTRUMENT_TAG = tag("instrument_tag", "tags/instrument", "Goat horn tags");
    public static final Kind POI_TAG = tag("poi_tag", "tags/point_of_interest_type", "Workstation and point tags");
    public static final Kind CAT_VARIANT_TAG = tag("cat_variant_tag", "tags/cat_variant", "Cat variant tags");
    public static final Kind PAINTING_VARIANT_TAG = tag("painting_variant_tag", "tags/painting_variant", "Painting tags");
    public static final Kind WORLD_PRESET_TAG = tag("world_preset_tag", "tags/worldgen/world_preset", "World type tags");
    public static final Kind FLAT_PRESET_TAG = tag("flat_preset_tag", "tags/worldgen/flat_level_generator_preset",
            "Superflat layout tags");

    // PLAN C STEP 7: the game's other data: edited, not switched off.
    public static final Kind JUKEBOX_SONG = edited("jukebox_song", "jukebox_song", "Jukebox songs");
    public static final Kind PAINTING_VARIANT = edited("painting_variant", "painting_variant", "Paintings");
    public static final Kind WOLF_VARIANT = edited("wolf_variant", "wolf_variant", "Wolf variants");
    public static final Kind BANNER_PATTERN = edited("banner_pattern", "banner_pattern", "Banner patterns");
    public static final Kind TRIM_MATERIAL = edited("trim_material", "trim_material", "Armor trim materials");
    public static final Kind TRIM_PATTERN = edited("trim_pattern", "trim_pattern", "Armor trim patterns");
    public static final Kind CHAT_TYPE = edited("chat_type", "chat_type", "Chat types");
    public static final Kind ENCHANTMENT_PROVIDER = edited("enchantment_provider", "enchantment_provider",
            "Enchantment rolls (mob gear, trades, loot)");

    // PLAN C STEP 8: world generation.
    public static final Kind DIMENSION_TYPE = edited("dimension_type", "dimension_type",
            "Dimension types (height, light, ceiling, beds)");
    /** A mod's or a datapack's own dimensions; the game's three are inside World types (world_preset). */
    public static final Kind DIMENSION = edited("dimension", "dimension", "Added dimensions (a mod's or pack's)");
    public static final Kind NOISE_SETTINGS = edited("noise_settings", "worldgen/noise_settings",
            "Terrain settings (sea level, blocks, surface)");
    public static final Kind DENSITY_FUNCTION = edited("density_function", "worldgen/density_function",
            "Terrain shape (density functions)");
    public static final Kind NOISE = edited("noise", "worldgen/noise", "Noises");
    public static final Kind BIOME_PARAMETERS = edited("biome_parameters", "worldgen/multi_noise_biome_source_parameter_list",
            "Biome placement (climate ranges)");
    public static final Kind CARVER = edited("carver", "worldgen/configured_carver", "Caves and canyons (carvers)");
    public static final Kind TEMPLATE_POOL = edited("template_pool", "worldgen/template_pool",
            "Structure pieces (template pools)");
    public static final Kind PROCESSOR_LIST = edited("processor_list", "worldgen/processor_list",
            "Structure weathering (processor lists)");
    public static final Kind WORLD_PRESET = edited("world_preset", "worldgen/world_preset", "World types");
    public static final Kind FLAT_PRESET = edited("flat_preset", "worldgen/flat_level_generator_preset", "Superflat layouts");
    public static final Kind BIOME_MODIFIER = edited("biome_modifier", "neoforge/biome_modifier",
            "Biome modifiers (a mod's additions)");
    public static final Kind STRUCTURE_MODIFIER = edited("structure_modifier", "neoforge/structure_modifier",
            "Structure modifiers (a mod's additions)");

    /** A tag kind: switched off, it holds nothing; packs below merge into it. */
    private static Kind tag(String id, String folder, String label) {
        return new Kind(id, folder, label, Off.EMPTY_VALUES, Merge.TAG);
    }

    /** A kind that is edited and never switched off. */
    private static Kind edited(String id, String folder, String label) {
        return new Kind(id, folder, label, Off.NONE);
    }

    /** Step 6's tags. */
    public static final List<Kind> MORE_TAGS = List.of(STRUCTURE_TAG, FLUID_TAG, BANNER_PATTERN_TAG, GAME_EVENT_TAG,
            INSTRUMENT_TAG, POI_TAG, CAT_VARIANT_TAG, PAINTING_VARIANT_TAG, WORLD_PRESET_TAG, FLAT_PRESET_TAG);
    /** Step 7's kinds. */
    public static final List<Kind> OTHER_DATA = List.of(PAINTING_VARIANT, WOLF_VARIANT, BANNER_PATTERN, TRIM_MATERIAL,
            TRIM_PATTERN, JUKEBOX_SONG, ENCHANTMENT_PROVIDER, CHAT_TYPE);
    /** Step 8's kinds. */
    public static final List<Kind> WORLD_GENERATION = List.of(DIMENSION_TYPE, DIMENSION, NOISE_SETTINGS, DENSITY_FUNCTION,
            NOISE, BIOME_PARAMETERS, CARVER, TEMPLATE_POOL, PROCESSOR_LIST, WORLD_PRESET, FLAT_PRESET, BIOME_MODIFIER,
            STRUCTURE_MODIFIER);

    /** Every kind the sheet handles. */
    public static final List<Kind> KINDS = kinds();

    private static List<Kind> kinds() {
        List<Kind> out = new java.util.ArrayList<>(List.of(RECIPE, STRUCTURE, STRUCTURE_SET, PLACED_FEATURE,
                CONFIGURED_FEATURE, BIOME, LOOT_TABLE, ITEM_TAG, BLOCK_TAG, ENTITY_TAG, BIOME_TAG, DAMAGE_TAG,
                ENCHANTMENT_TAG, ITEM_DATA, BLOCK_DATA, ENTITY_DATA, ENCHANTMENT, DAMAGE_TYPE, ADVANCEMENT, BREWING,
                GAME_RULES));
        out.addAll(MORE_TAGS);
        out.addAll(OTHER_DATA);
        out.addAll(WORLD_GENERATION);
        return List.copyOf(out);
    }

    /** A kind by its id, or null. */
    public static Kind kind(String id) {
        return KINDS.stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SET = "set";
    private static final String OFF = "off";
    private static final String JSON = "json";
    /** The files the sheet wrote into the pack last time, so the next regenerate removes exactly those. */
    private static final String WRITTEN = "strata_overrides_written.txt";

    /** The game layer file of a kind's entries. */
    static Path gameFile(Kind kind) {
        return GameLayer.dir().resolve("json_" + kind.id() + ".json");
    }

    /** The overrides file of a kind. */
    static Path overrideFile(Kind kind) {
        return Paths.ROOT.resolve("overrides").resolve(kind.id() + ".json");
    }

    // ---------------------------------------------------------------- read

    /** Fills the game layer from the data packs: at game start (GameRecipes), and again at server start. */
    public static void extract(ResourceManager resources) {
        for (Kind kind : KINDS) {
            if (kind.folder().isEmpty()) {
                continue;  // Read in code, by its own runtime (BREWING).
            }
            try {
                JsonObject all = new JsonObject();
                Map<ResourceLocation, List<Resource>> stacks = resources.listResourceStacks(kind.folder(),
                        p -> p.getPath().endsWith(".json"));
                for (Map.Entry<ResourceLocation, List<Resource>> e : new TreeMap<>(stacks).entrySet()) {
                    if (kind.merge() != Merge.TOP) {
                        ResourceLocation file = e.getKey();
                        String path = file.getPath();
                        String id = file.getNamespace() + ":"
                                + path.substring(kind.folder().length() + 1, path.length() - ".json".length());
                        JsonObject merged = merged(kind, e.getValue());
                        if (merged != null) {
                            all.add(id, merged);
                        }
                        continue;
                    }
                    Resource top = null;
                    for (Resource r : e.getValue()) {
                        // The stack runs bottom to top: the last pack that is not Strata's wins.
                        if (!StrataDatapack.NAMESPACE.equals(r.sourcePackId())) {
                            top = r;
                        }
                    }
                    if (top == null) {
                        continue;
                    }
                    ResourceLocation file = e.getKey();
                    String path = file.getPath();
                    String id = file.getNamespace() + ":"
                            + path.substring(kind.folder().length() + 1, path.length() - ".json".length());
                    try (Reader reader = top.openAsReader()) {
                        JsonElement json = JsonParser.parseReader(reader);
                        if (json.isJsonObject()) {
                            all.add(id, json);
                        }
                    } catch (IOException | RuntimeException ex) {
                        Strata.LOGGER.debug("Overrides: {} {} unreadable - {}", kind.id(), id, ex.toString());
                    }
                }
                Files.createDirectories(GameLayer.dir());
                Files.writeString(gameFile(kind), GSON.toJson(all), StandardCharsets.UTF_8);
                Strata.LOGGER.info("Read {} {} entries from the game and its mods", all.size(), kind.id());
            } catch (IOException | RuntimeException e) {
                Strata.LOGGER.warn("Overrides: could not read the game's {} - {}", kind.id(), e.toString());
            }
        }
    }

    /**
     * A tag's or a data map's game value: every pack below Strata's, bottom to top, each adding to it, and
     * one that says replace starting it again - as the game merges them.
     */
    static JsonObject merged(Kind kind, List<Resource> stack) {
        JsonArray values = new JsonArray();
        JsonObject map = new JsonObject();
        boolean any = false;
        for (Resource r : stack) {
            if (StrataDatapack.NAMESPACE.equals(r.sourcePackId())) {
                continue;
            }
            try (Reader reader = r.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (!json.isJsonObject()) {
                    continue;
                }
                JsonObject o = json.getAsJsonObject();
                boolean replace = o.has("replace") && o.get("replace").getAsBoolean();
                JsonElement v = o.get("values");
                if (kind.merge() == Merge.TAG) {
                    if (replace) {
                        values = new JsonArray();
                    }
                    if (v != null && v.isJsonArray()) {
                        for (JsonElement el : v.getAsJsonArray()) {
                            if (!values.contains(el)) {
                                values.add(el);
                            }
                        }
                    }
                } else {
                    if (replace) {
                        map = new JsonObject();
                    }
                    if (v != null && v.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> m : v.getAsJsonObject().entrySet()) {
                            map.add(m.getKey(), m.getValue());
                        }
                    }
                }
                any = true;
            } catch (IOException | RuntimeException ex) {
                Strata.LOGGER.debug("Overrides: {} part unreadable - {}", kind.id(), ex.toString());
            }
        }
        if (!any) {
            return null;
        }
        JsonObject out = new JsonObject();
        out.add("values", kind.merge() == Merge.TAG ? values : map);
        return out;
    }

    /** Stores a kind's game values read in code rather than from packs (BREWING). */
    public static void storeGame(Kind kind, Map<String, JsonObject> entries) {
        JsonObject all = new JsonObject();
        entries.forEach(all::add);
        try {
            Files.createDirectories(GameLayer.dir());
            Files.writeString(gameFile(kind), GSON.toJson(all), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Strata.LOGGER.warn("Overrides: could not store the game's {} - {}", kind.id(), e.toString());
        }
    }

    /** A kind's entries as the game and its mods left them, by id. */
    public static Map<String, JsonObject> game(Kind kind) {
        return readMap(gameFile(kind));
    }

    // ---------------------------------------------------------------- store

    /**
     * One entry's override: the fields set by path, whether it is off, and - once an element was added or
     * removed - the whole edited JSON the fields are set over.
     */
    public record Override(Map<String, String> set, boolean off, JsonObject json) {
        public static final Override NONE = new Override(Map.of(), false, null);

        public Override(Map<String, String> set, boolean off) {
            this(set, off, null);
        }

        public boolean isEmpty() {
            return set.isEmpty() && !off && json == null;
        }
    }

    /** A kind's overrides, by id. */
    public static Map<String, Override> overrides(Kind kind) {
        Map<String, Override> out = new LinkedHashMap<>();
        readMap(overrideFile(kind)).forEach((id, o) -> {
            Map<String, String> set = new LinkedHashMap<>();
            if (o.has(SET) && o.get(SET).isJsonObject()) {
                o.getAsJsonObject(SET).entrySet().forEach(e -> set.put(e.getKey(), e.getValue().getAsString()));
            }
            JsonObject json = o.has(JSON) && o.get(JSON).isJsonObject() ? o.getAsJsonObject(JSON) : null;
            out.put(id, new Override(Map.copyOf(set), o.has(OFF) && o.get(OFF).getAsBoolean(), json));
        });
        return out;
    }

    /** Stores one entry's override; an empty one removes it, so the entry is the game's again. */
    public static boolean store(Kind kind, String id, Override override) {
        Map<String, Override> all = new LinkedHashMap<>(overrides(kind));
        if (override == null || override.isEmpty()) {
            all.remove(id);
        } else {
            all.put(id, override);
        }
        JsonObject out = new JsonObject();
        all.forEach((key, o) -> {
            JsonObject entry = new JsonObject();
            JsonObject set = new JsonObject();
            o.set().forEach(set::addProperty);
            entry.add(SET, set);
            entry.addProperty(OFF, o.off());
            if (o.json() != null) {
                entry.add(JSON, o.json());
            }
            out.add(key, entry);
        });
        try {
            Files.createDirectories(overrideFile(kind).getParent());
            Files.writeString(overrideFile(kind), GSON.toJson(out), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Strata.LOGGER.warn("Overrides: could not save {} - {}", overrideFile(kind), e.toString());
            return false;
        }
    }

    /**
     * The entry as it will load: the game's JSON with the override's fields set, and a false condition when
     * it is off.
     */
    public static JsonObject applied(JsonObject game, Override override) {
        return applied(RECIPE, game, override);
    }

    /** As above, switched off the kind's own safe way (Off). */
    public static JsonObject applied(Kind kind, JsonObject game, Override override) {
        if (game == null) {
            return null;
        }
        JsonObject out = (override.json() != null ? override.json() : game).deepCopy();
        for (Map.Entry<String, String> e : override.set().entrySet()) {
            JsonObject next = JsonPaths.set(out, e.getKey(), e.getValue());
            if (next != null) {
                out = next;
            }
        }
        if (override.off()) {
            switch (kind.off()) {
                case CONDITIONS -> {
                    JsonArray conditions = new JsonArray();
                    JsonObject never = new JsonObject();
                    never.addProperty("type", "neoforge:false");
                    conditions.add(never);
                    out.add("neoforge:conditions", conditions);
                }
                case EMPTY_BIOMES -> out.add("biomes", new JsonArray());
                case COUNT_ZERO -> {
                    JsonArray placement = new JsonArray();
                    JsonObject none = new JsonObject();
                    none.addProperty("type", "minecraft:count");
                    none.addProperty("count", 0);
                    placement.add(none);
                    if (out.has("placement") && out.get("placement").isJsonArray()) {
                        placement.addAll(out.getAsJsonArray("placement"));
                    }
                    out.add("placement", placement);
                }
                case EMPTY_POOLS -> out.add("pools", new JsonArray());
                case EMPTY_VALUES -> out.add("values", kind.merge() == Merge.DATA_MAP ? new JsonObject() : new JsonArray());
                case IMPOSSIBLE -> {
                    JsonObject never = new JsonObject();
                    JsonObject trigger = new JsonObject();
                    trigger.addProperty("trigger", "minecraft:impossible");
                    never.add("never", trigger);
                    out.add("criteria", never);
                    JsonArray req = new JsonArray();
                    JsonArray one = new JsonArray();
                    one.add("never");
                    req.add(one);
                    out.add("requirements", req);
                }
                case NONE -> {
                }
            }
        }
        // A merged kind is written whole, over every pack below it.
        if (kind.merge() != Merge.TOP) {
            out.addProperty("replace", true);
        }
        return out;
    }

    // ---------------------------------------------------------------- write

    /**
     * Writes every kind's overrides into Strata's pack (StrataDatapack#regenerate).
     *
     * @param disabled disabled block and item ids: every recipe that makes one is written switched off, over
     *     any change the user made to it
     */
    public static int writePack(Path data, java.util.Set<String> disabled) {
        int written = 0;
        List<String> files = new java.util.ArrayList<>();
        for (Kind kind : KINDS) {
            if (kind.folder().isEmpty()) {
                continue;  // Applied in code, not by the pack (BREWING).
            }
            Map<String, JsonObject> game = game(kind);
            Map<String, Override> all = new LinkedHashMap<>(overrides(kind));
            if (kind == RECIPE && !disabled.isEmpty()) {
                game.forEach((id, json) -> {
                    if (disabled.contains(resultOf(json))) {
                        Override o = all.getOrDefault(id, Override.NONE);
                        all.put(id, new Override(o.set(), true));
                    }
                });
            }
            // A DISABLED BLOCK STOPS GENERATING: every placed feature whose feature places it - an ore of it,
            // any mod's ore of it - is placed zero times.
            if (kind == PLACED_FEATURE && !disabled.isEmpty()) {
                Map<String, JsonObject> configured = game(CONFIGURED_FEATURE);
                game.forEach((id, json) -> {
                    JsonElement feature = json.get("feature");
                    JsonElement config = feature == null ? null : feature.isJsonPrimitive()
                            ? configured.get(feature.getAsString()) : feature;
                    if (config != null && placesAny(config, disabled)) {
                        Override o = all.getOrDefault(id, Override.NONE);
                        all.put(id, new Override(o.set(), true));
                    }
                });
            }
            for (Map.Entry<String, Override> e : all.entrySet()) {
                JsonObject json = applied(kind, game.get(e.getKey()), e.getValue());
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                if (json == null || id == null) {
                    Strata.LOGGER.warn("Overrides: {} {} is not in this game - its change is kept and skipped",
                            kind.id(), e.getKey());
                    continue;
                }
                try {
                    Path file = data.resolve(id.getNamespace()).resolve(kind.folder()).resolve(id.getPath() + ".json");
                    // A TAG STRATA ALREADY WROTE (a block variant in its base block's tags) keeps Strata's
                    // own members: they are added to the override's list.
                    if (kind.merge() == Merge.TAG && Files.isRegularFile(file)) {
                        JsonElement before = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                        if (before.isJsonObject() && before.getAsJsonObject().has("values") && !e.getValue().off()) {
                            for (JsonElement el : before.getAsJsonObject().getAsJsonArray("values")) {
                                if (!json.getAsJsonArray("values").contains(el)) {
                                    json.getAsJsonArray("values").add(el);
                                }
                            }
                        }
                    }
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
                    files.add(data.relativize(file).toString().replace('\\', '/'));
                    written++;
                } catch (IOException ex) {
                    Strata.LOGGER.warn("Overrides: could not write {} {} - {}", kind.id(), e.getKey(), ex.toString());
                }
            }
        }
        try {
            Files.createDirectories(data);
            Files.writeString(data.getParent().resolve(WRITTEN), String.join("\n", files), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Strata.LOGGER.warn("Overrides: could not record the written files - {}", e.toString());
        }
        return written;
    }

    /**
     * Removes the files the sheet wrote last time (StrataDatapack#regenerate, FIRST, before any other
     * writer), so a reset entry leaves the pack and nothing another writer makes this time is touched.
     */
    public static void clearWritten(Path data) {
        Path record = data.getParent().resolve(WRITTEN);
        if (!Files.isRegularFile(record)) {
            return;
        }
        try {
            for (String rel : Files.readAllLines(record, StandardCharsets.UTF_8)) {
                if (!rel.isBlank() && !rel.contains("..")) {
                    Files.deleteIfExists(data.resolve(rel.strip()));
                }
            }
            Files.delete(record);
        } catch (IOException e) {
            Strata.LOGGER.warn("Overrides: could not clear last time's files - {}", e.toString());
        }
    }

    /**
     * True when a configured feature places one of these blocks: any "Name" in it, as a block state is
     * written in every feature type (an ore's targets, a tree's trunk).
     */
    public static boolean placesAny(JsonElement feature, java.util.Set<String> blocks) {
        return JsonPaths.flatten(feature).entrySet().stream()
                .anyMatch(e -> e.getKey().endsWith("Name") && blocks.contains(e.getValue()));
    }

    /** The id a recipe makes, over the shapes the game uses: result.id, result.item, or result. */
    public static String resultOf(JsonObject recipe) {
        for (String p : List.of("result.id", "result.item", "result")) {
            JsonElement e = JsonPaths.get(recipe, p);
            if (e != null && e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        return "";
    }

    private static Map<String, JsonObject> readMap(Path file) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (json.isJsonObject()) {
                json.getAsJsonObject().entrySet().forEach(e -> {
                    if (e.getValue().isJsonObject()) {
                        out.put(e.getKey(), e.getValue().getAsJsonObject());
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            Strata.LOGGER.warn("Overrides: {} unreadable - {}", file, e.toString());
        }
        return out;
    }
}
