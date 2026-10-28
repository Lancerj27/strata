package com.strata.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strata.Strata;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes the changes that the GAME can apply itself out as a real datapack, instead of Strata re-implementing
 * them at runtime.
 */
public final class StrataDatapack {

    private StrataDatapack() {}

    /** Sits next to the CSVs, so everything Strata owns is under one folder. */
    public static final Path PACK_DIR = Paths.DATAPACK;

    /** The pack's id and its own namespace. */
    public static final String NAMESPACE = "strata_generated";


    /** Rebuilds the pack from the current tables. */
    public static void regenerate() {
        try {
            writePackMeta();
            // The override sheet's files of last time go first, before any writer of this time.
            JsonOverrides.clearWritten(PACK_DIR.resolve("data"));
            clearSpawnModifiers();
            int vegetation = writeVegetation();
            if (vegetation > 0) {
                Strata.LOGGER.info("Generated datapack: the vegetation of {} biome(s); it applies on the next world load", vegetation);
            }
            int tables = writeLootTables();
            int tags = writeTags();
            int variantTags = writeBlockVariantTags();
            int recipes = writeRecipes();
            // The designer's structures: each as the game's own structure, pool and set.
            CustomStructures.clear(PACK_DIR.resolve("data"));
            int structures = CustomStructures.writePack(Paths.ROOT, PACK_DIR.resolve("data"));
            if (structures > 0) {
                Strata.LOGGER.info("Generated datapack: {} custom structure(s); they generate in land made after the next world load", structures);
            }
            // The override sheet: after the crafting recipes, so an override of an id the crafting table also
            // writes is the last word.
            int overrides = JsonOverrides.writePack(PACK_DIR.resolve("data"),
                    com.strata.domain.DisabledOps.allIds());
            writeDisabledLoot();
            if (overrides > 0) {
                Strata.LOGGER.info("Generated datapack: {} override(s); they apply on the next world load or /reload", overrides);
            }
            if (recipes > 0) {
                Strata.LOGGER.info("Generated datapack: {} crafting recipe(s); they apply on the next world load or /reload", recipes);
            }
            if (variantTags > 0) {
                Strata.LOGGER.info("Generated datapack: block variants in {} tag(s) of their base blocks", variantTags);
            }
            if (tags > 0) {
                Strata.LOGGER.info("Generated datapack: {} edited entity tag(s); they apply on the next world load", tags);
            }
            if (tables > 0) {
                Strata.LOGGER.info("Generated datapack: {} edited loot table(s); they apply on the next world load", tables);
            }
        } catch (Exception e) {
            Strata.LOGGER.error("Could not write the generated datapack: {}", e.toString(), e);
        }
    }

    private static void writePackMeta() throws IOException {
        Files.createDirectories(PACK_DIR);

        JsonObject pack = new JsonObject();
        // 48 is the 1.21.1 data pack format.
        pack.addProperty("pack_format", 48);
        pack.addProperty("description", "Strata - generated from your config. Do not edit by hand.");

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        write(PACK_DIR.resolve("pack.mcmeta"), root);
    }

    /**
     * Mod-bus AddPackFindersEvent hook: makes the game read the generated folder as a real data pack so the
     * written biome modifiers actually apply.
     */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        if (!Files.isRegularFile(PACK_DIR.resolve("pack.mcmeta"))) {
            return;
        }
        PackLocationInfo location = new PackLocationInfo(
                NAMESPACE,
                Component.literal("Strata - generated from your config"),
                PackSource.BUILT_IN,
                Optional.empty());
        Pack pack = Pack.readMetaAndCreate(
                location,
                BuiltInPackSource.fromName(info -> new PathPackResources(info, PACK_DIR)),
                PackType.SERVER_DATA,
                new PackSelectionConfig(false, Pack.Position.TOP, false));
        if (pack == null) {
            Strata.LOGGER.error("Could not create the generated datapack from {}", PACK_DIR);
            return;
        }
        event.addRepositorySource(packConsumer -> packConsumer.accept(pack));
    }

    /**
     * Writes each loot table the user changed, the game's with the user's values over it, where the game
     * looks for it: data/<namespace>/loot_table/<path>.json.
     */
    private static int writeLootTables() throws IOException {
        Path data = PACK_DIR.resolve("data");
        if (Files.isDirectory(data)) {
            try (var namespaces = Files.list(data)) {
                for (Path ns : namespaces.toList()) {
                    deleteTree(ns.resolve("loot_table"));
                }
            }
        }
        Map<String, JsonElement> tables = LootJson.editedTables(
                GameLayer.read(GameLayer.dir().resolve(GameLayer.LOOT)),
                CsvTable.read(Paths.table(Schema.LootTables.FILE)));
        for (Map.Entry<String, JsonElement> e : tables.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !e.getValue().isJsonObject()) {
                continue;
            }
            Path file = data.resolve(id.getNamespace()).resolve("loot_table").resolve(id.getPath() + ".json");
            Files.createDirectories(file.getParent());
            write(file, e.getValue().getAsJsonObject());
        }
        return tables.size();
    }

    /**
     * Writes each entity tag the user changed as its whole member list, over the game's ("replace": the pack
     * sits on top), where the game looks for it: data/<namespace>/tags/entity_type/<path>.json.
     */
    private static int writeTags() throws IOException {
        Path data = PACK_DIR.resolve("data");
        if (Files.isDirectory(data)) {
            try (var namespaces = Files.list(data)) {
                for (Path ns : namespaces.toList()) {
                    deleteTree(ns.resolve("tags").resolve("entity_type"));
                }
            }
        }
        Map<String, List<String>> tags = GameLayer.editedTags(
                GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS)),
                CsvTable.read(Paths.table(Schema.MobData.FILE)));
        for (Map.Entry<String, List<String>> e : tags.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null) {
                continue;
            }
            JsonObject tag = new JsonObject();
            tag.addProperty("replace", true);
            JsonArray values = new JsonArray();
            e.getValue().forEach(values::add);
            tag.add("values", values);
            Path file = data.resolve(id.getNamespace()).resolve("tags").resolve("entity_type").resolve(id.getPath() + ".json");
            Files.createDirectories(file.getParent());
            write(file, tag);
        }
        return tags.size();
    }

    /**
     * Puts each block variant in its base block's block and item tags:
     * data/<namespace>/tags/block|item/<path>.json, adding to the game's ("replace": false).
     */
    private static int writeBlockVariantTags() throws IOException {
        Path data = PACK_DIR.resolve("data");
        if (Files.isDirectory(data)) {
            try (var namespaces = Files.list(data)) {
                for (Path ns : namespaces.toList()) {
                    deleteTree(ns.resolve("tags").resolve("block"));
                    deleteTree(ns.resolve("tags").resolve("item"));
                }
            }
        }
        Map<String, List<String>> byBase = new java.util.HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockVariants.FILE))) {
            String name = row.getOrDefault(Schema.BlockVariants.NAME, "").strip();
            String base = row.getOrDefault(Schema.BlockVariants.BASE_BLOCK, "").strip();
            if (!name.isEmpty() && !base.isEmpty()) {
                byBase.computeIfAbsent(base, k -> new java.util.ArrayList<>()).add(Strata.MOD_ID + ":" + name);
            }
        }
        Map<String, java.util.Set<String>> tags = new java.util.TreeMap<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCK_TAGS))) {
            List<String> variants = byBase.get(r.entity());
            String folder = GameLayer.BLOCK_TAG.equals(r.group()) ? "block"
                    : GameLayer.ITEM_TAG.equals(r.group()) ? "item" : null;
            if (variants != null && folder != null) {
                tags.computeIfAbsent(folder + "|" + r.key(), k -> new java.util.TreeSet<>()).addAll(variants);
            }
        }
        // THE TOOL AND TOOL LEVEL THE USER SET: the block leaves the game's other mineable or needs_*_tool
        // tags (NeoForge's "remove") and joins the one picked.
        Map<String, java.util.Set<String>> removes = new java.util.TreeMap<>();
        Map<String, Map<String, String>> toolEdits = new java.util.HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockStats.FILE))) {
            toolEdits.computeIfAbsent(row.getOrDefault(Schema.BlockStats.BLOCK, "").strip(), k -> new java.util.HashMap<>())
                    .put(row.getOrDefault(Schema.BlockStats.KEY, "").strip(), row.getOrDefault(Schema.BlockStats.VALUE, "").strip());
        }
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockVariantValues.FILE))) {
            toolEdits.computeIfAbsent(Strata.MOD_ID + ":" + row.getOrDefault(Schema.BlockVariantValues.VARIANT, "").strip(),
                    k -> new java.util.HashMap<>()).put(row.getOrDefault(Schema.BlockVariantValues.KEY, "").strip(),
                    row.getOrDefault(Schema.BlockVariantValues.VALUE, "").strip());
        }
        toolEdits.forEach((block, values) -> {
            if (block.isEmpty()) return;
            String tool = values.get(com.strata.model.BlockStats.TOOL);
            if (tool != null && com.strata.model.BlockStats.TOOLS.contains(tool)) {
                String chosen = com.strata.model.BlockStats.toolTag(tool);
                for (String t : com.strata.model.BlockStats.TOOL_TAGS) {
                    (t.equals(chosen) ? tags : removes).computeIfAbsent("block|" + t, k -> new java.util.TreeSet<>()).add(block);
                }
            }
            String level = values.get(com.strata.model.BlockStats.TOOL_LEVEL);
            if (level != null && com.strata.model.BlockStats.TOOL_LEVELS.contains(level)) {
                String chosen = com.strata.model.BlockStats.levelTag(level);
                for (String t : com.strata.model.BlockStats.LEVEL_TAGS) {
                    (t.equals(chosen) ? tags : removes).computeIfAbsent("block|" + t, k -> new java.util.TreeSet<>()).add(block);
                }
            }
        });
        for (String key : removes.keySet()) {
            tags.computeIfAbsent(key, k -> new java.util.TreeSet<>());
        }
        for (Map.Entry<String, java.util.Set<String>> e : tags.entrySet()) {
            String folder = e.getKey().substring(0, e.getKey().indexOf('|'));
            ResourceLocation id = ResourceLocation.tryParse(e.getKey().substring(folder.length() + 1));
            if (id == null) {
                continue;
            }
            JsonObject tag = new JsonObject();
            tag.addProperty("replace", false);
            JsonArray values = new JsonArray();
            for (String v : e.getValue()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", v);
                entry.addProperty("required", false);
                values.add(entry);
            }
            tag.add("values", values);
            java.util.Set<String> gone = removes.getOrDefault(e.getKey(), java.util.Set.of());
            if (!gone.isEmpty()) {
                JsonArray remove = new JsonArray();
                for (String v : gone) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", v);
                    entry.addProperty("required", false);
                    remove.add(entry);
                }
                tag.add("remove", remove);
            }
            Path file = data.resolve(id.getNamespace()).resolve("tags").resolve(folder).resolve(id.getPath() + ".json");
            Files.createDirectories(file.getParent());
            write(file, tag);
        }
        return tags.size();
    }

    /**
     * Writes each of the designer's crafting recipes as the game's own recipe JSON,
     * data/strata/recipe/<name>.json.
     */
    /** The loot modifier that strips disabled items from every loot table, named in NeoForge's list. */
    private static void writeDisabledLoot() throws IOException {
        Path own = PACK_DIR.resolve("data").resolve(NAMESPACE).resolve("loot_modifiers").resolve("strip_disabled.json");
        Path global = PACK_DIR.resolve("data").resolve("neoforge").resolve("loot_modifiers").resolve("global_loot_modifiers.json");
        // Nothing disabled: no loot modifier at all, so every loot table runs as the game's.
        if (com.strata.domain.DisabledOps.allIds().isEmpty()) {
            Files.deleteIfExists(own);
            Files.deleteIfExists(global);
            return;
        }
        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", Strata.MOD_ID + ":strip_disabled");
        modifier.add("conditions", new com.google.gson.JsonArray());
        Files.createDirectories(own.getParent());
        write(own, modifier);
        JsonObject list = new JsonObject();
        list.addProperty("replace", false);
        com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
        entries.add(NAMESPACE + ":strip_disabled");
        list.add("entries", entries);
        Files.createDirectories(global.getParent());
        write(global, list);
    }

    private static int writeRecipes() throws IOException {
        Path data = PACK_DIR.resolve("data");
        if (Files.isDirectory(data)) {
            try (var namespaces = Files.list(data)) {
                for (Path ns : namespaces.toList()) {
                    deleteTree(ns.resolve("recipe"));
                }
            }
        }
        int written = 0;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Recipes.FILE))) {
            String name = row.getOrDefault(Schema.Recipes.NAME, "").strip();
            String type = row.getOrDefault(Schema.Recipes.TYPE, "").strip();
            String result = row.getOrDefault(Schema.Recipes.RESULT, "").strip();
            String count = row.getOrDefault(Schema.Recipes.COUNT, "").strip();
            List<String> cells = new java.util.ArrayList<>();
            for (int i = 0; i < com.strata.model.CraftingRecipe.CELLS; i++) {
                cells.add(row.getOrDefault(Schema.Recipes.cell(i), "").strip());
            }
            ResourceLocation id = ResourceLocation.tryParse(com.strata.model.CraftingRecipe.id(name, Strata.MOD_ID));
            boolean off = com.strata.model.CraftingRecipe.OFF.equals(type);
            String problem = id == null || (!name.contains(":")
                    && com.strata.model.CraftingRecipe.nameProblem(name, List.of()) != null) ? "bad name"
                    : off ? null : com.strata.model.CraftingRecipe.problem(type, cells, result, count);
            if (problem != null) {
                Strata.LOGGER.warn("Recipe '{}' is left out: {}", name, problem);
                continue;
            }
            Path file = data.resolve(id.getNamespace()).resolve("recipe").resolve(id.getPath() + ".json");
            Files.createDirectories(file.getParent());
            write(file, off ? com.strata.model.CraftingRecipe.offJson()
                    : com.strata.model.CraftingRecipe.json(type, cells, result, Integer.parseInt(count)));
            written++;
        }
        return written;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static Path modifierDir() throws IOException {
        Path dir = PACK_DIR.resolve("data").resolve(NAMESPACE).resolve("neoforge").resolve("biome_modifier");
        // Wiped and rebuilt: a modifier for a biome whose rows were just deleted has to disappear, and
        // leaving stale files behind would mean deletions never took effect.
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                for (Path file : stream.toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        Files.createDirectories(dir);
        return dir;
    }

    /** Clears the spawn modifiers older builds wrote (neoforge:add_spawns and remove_spawns). */
    private static void clearSpawnModifiers() throws IOException {
        modifierDir();
    }

    /** ORE RESTRICTION IS NOT WRITTEN HERE. */

    /**
     * THE VEGETATION RULES AS BIOME MODIFIERS.
     *
     * @return how many biomes changed
     */
    private static int writeVegetation() throws IOException {
        Map<String, String> tierOfBiome = new java.util.LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.TierBiomes.FILE))) {
            String biome = row.getOrDefault(Schema.TierBiomes.BIOME, "").strip();
            String tier = row.getOrDefault(Schema.TierBiomes.TIER, "").strip();
            if (!biome.isEmpty() && !tier.isEmpty()) {
                tierOfBiome.putIfAbsent(biome, tier);
            }
        }
        Map<String, Map<String, Boolean>> tierRules = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Boolean>> biomeRules = new java.util.LinkedHashMap<>();
        // Global rules, under every tier and biome.
        Map<String, Boolean> globalRules = new java.util.LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Vegetation.FILE))) {
            String scope = row.getOrDefault(Schema.Vegetation.SCOPE, "").strip();
            String id = row.getOrDefault(Schema.Vegetation.SCOPE_ID, "").strip();
            String feature = row.getOrDefault(Schema.Vegetation.FEATURE, "").strip();
            boolean on = Parse.flag(row.get(Schema.Vegetation.ENABLED), true);
            if ("global".equals(scope) && !feature.isEmpty()) {
                globalRules.put(feature, on);
                continue;
            }
            if (id.isEmpty() || feature.isEmpty()) {
                continue;
            }
            if ("tier".equals(scope)) {
                tierRules.computeIfAbsent(id, k -> new java.util.LinkedHashMap<>()).put(feature, on);
            } else if ("biome".equals(scope)) {
                biomeRules.computeIfAbsent(id, k -> new java.util.LinkedHashMap<>()).put(feature, on);
            }
        }
        Map<String, com.strata.model.VegetationPlan.Change> plan = com.strata.model.VegetationPlan.plan(globalRules, tierOfBiome,
                tierRules, biomeRules, GameSnapshot.vegetation());
        if (plan.isEmpty()) {
            return 0;
        }
        Path dir = PACK_DIR.resolve("data").resolve(NAMESPACE).resolve("neoforge").resolve("biome_modifier");
        Files.createDirectories(dir);
        int n = 0;
        for (Map.Entry<String, com.strata.model.VegetationPlan.Change> e : plan.entrySet()) {
            n++;
            if (!e.getValue().add().isEmpty()) {
                write(dir.resolve("vegetation_add_" + n + ".json"),
                        featureModifier("neoforge:add_features", e.getKey(), e.getValue().add(), "step"));
            }
            if (!e.getValue().remove().isEmpty()) {
                write(dir.resolve("vegetation_remove_" + n + ".json"),
                        featureModifier("neoforge:remove_features", e.getKey(), e.getValue().remove(), "steps"));
            }
        }
        return plan.size();
    }

    /** One biome's feature modifier, in the vegetal decoration step. */
    private static JsonObject featureModifier(String type, String biome, List<String> features, String stepKey) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("biomes", biome);
        JsonArray list = new JsonArray();
        features.forEach(list::add);
        json.add("features", list);
        json.addProperty(stepKey, "vegetal_decoration");
        return json;
    }

    private static void write(Path path, JsonObject json) throws IOException {
        Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
    }
}
