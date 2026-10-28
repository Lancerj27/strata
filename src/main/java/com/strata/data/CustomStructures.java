package com.strata.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.strata.Strata;
import com.strata.model.CustomStructure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** THE DESIGNER'S STRUCTURES ON DISK AND IN THE PACK. */
public final class CustomStructures {

    private CustomStructures() {
    }

    /** Where the structure files are kept. */
    public static Path dir(Path root) {
        return root.resolve("structures");
    }

    /** One structure's file. */
    public static Path fileOf(Path root, String name) {
        return dir(root).resolve(name + ".nbt");
    }

    /** What a structure file holds, or why it cannot be used. */
    public record Probe(String problem, int x, int y, int z, int blocks) {
        public String summary() {
            return x + " x " + y + " x " + z + " blocks, " + blocks + " placed";
        }
    }

    /**
     * Reads a structure file the way the pack will, within the limits: the file size, the game's compressed
     * NBT only (with a cap on what it may unpack to), then its size and block count.
     */
    public static Probe probe(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                return new Probe("No such file", 0, 0, 0, 0);
            }
            if (!file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".nbt")) {
                return new Probe("A structure is a .nbt file, as a structure block saves it", 0, 0, 0, 0);
            }
            if (Files.size(file) > CustomStructure.MAX_FILE) {
                return new Probe("The file is over " + CustomStructure.MAX_FILE / 1024 + " KB", 0, 0, 0, 0);
            }
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.create(16L << 20));
            ListTag size = tag.getList("size", Tag.TAG_INT);
            if (size.size() != 3 || !tag.contains("blocks", Tag.TAG_LIST)
                    || !(tag.contains("palette", Tag.TAG_LIST) || tag.contains("palettes", Tag.TAG_LIST))) {
                return new Probe("Not a structure file: it has no size, blocks or palette", 0, 0, 0, 0);
            }
            int x = size.getInt(0), y = size.getInt(1), z = size.getInt(2);
            int blocks = tag.getList("blocks", Tag.TAG_COMPOUND).size();
            return new Probe(CustomStructure.shapeProblem(x, y, z, blocks), x, y, z, blocks);
        } catch (IOException | RuntimeException e) {
            return new Probe("The file cannot be read as a structure: " + e.getMessage(), 0, 0, 0, 0);
        }
    }

    /** The rows of custom_structures.csv. */
    public static List<Map<String, String>> rows() {
        return CsvTable.read(Paths.table(Schema.CustomStructures.FILE));
    }

    /** Writes every enabled structure into the pack's data folder, checking each file again. */
    public static int writePack(Path root, Path data) throws IOException {
        int written = 0;
        for (Map<String, String> row : rows()) {
            String name = row.getOrDefault(CustomStructure.NAME, "").strip();
            if (!Parse.flag(row.get(CustomStructure.ENABLED), true)) {
                continue;
            }
            String problem = CustomStructure.problem(row);
            Path file = fileOf(root, name);
            if (problem == null) {
                problem = probe(file).problem();
            }
            if (problem != null) {
                Strata.LOGGER.warn("Custom structure {}: {}; it is left out", name, problem);
                continue;
            }
            String ns = StrataDatapack.NAMESPACE;
            Path template = data.resolve(ns).resolve("structure").resolve("custom").resolve(name + ".nbt");
            Files.createDirectories(template.getParent());
            Files.copy(file, template, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String id = ns + ":custom/" + name;
            write(data.resolve(ns).resolve("worldgen/template_pool/custom").resolve(name + ".json"), pool(id));
            write(data.resolve(ns).resolve("worldgen/structure/custom").resolve(name + ".json"), structure(row, id));
            write(data.resolve(ns).resolve("worldgen/structure_set/custom").resolve(name + ".json"), set(row, id, name));
            written++;
        }
        return written;
    }

    /** A pool of the one piece, as the game writes a single-piece pool. */
    public static JsonObject pool(String id) {
        JsonObject element = new JsonObject();
        element.addProperty("element_type", "minecraft:single_pool_element");
        element.addProperty("location", id);
        element.addProperty("processors", "minecraft:empty");
        element.addProperty("projection", "rigid");
        JsonObject entry = new JsonObject();
        entry.add("element", element);
        entry.addProperty("weight", 1);
        JsonArray elements = new JsonArray();
        elements.add(entry);
        JsonObject pool = new JsonObject();
        pool.addProperty("fallback", "minecraft:empty");
        pool.add("elements", elements);
        return pool;
    }

    /** The structure: a jigsaw of the one piece, in the row's biomes, step, land and height. */
    public static JsonObject structure(Map<String, String> row, String id) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "minecraft:jigsaw");
        String biomes = row.getOrDefault(CustomStructure.BIOMES, "").strip();
        if (biomes.startsWith("#")) {
            s.addProperty("biomes", biomes);
        } else {
            JsonArray list = new JsonArray();
            for (String b : biomes.split(",")) {
                list.add(b.strip());
            }
            s.add("biomes", list);
        }
        s.addProperty("step", row.getOrDefault(CustomStructure.STEP, "surface_structures").strip());
        s.addProperty("terrain_adaptation", row.getOrDefault(CustomStructure.TERRAIN, "none").strip());
        s.add("spawn_overrides", new JsonObject());
        s.addProperty("start_pool", id);
        s.addProperty("size", 1);
        JsonObject height = new JsonObject();
        String h = row.getOrDefault(CustomStructure.HEIGHT, "surface").strip();
        height.addProperty("absolute", "surface".equals(h) ? 0 : Integer.parseInt(h));
        s.add("start_height", height);
        if ("surface".equals(h)) {
            s.addProperty("project_start_to_heightmap", "WORLD_SURFACE_WG");
        }
        s.addProperty("max_distance_from_center", 80);
        s.addProperty("use_expansion_hack", false);
        return s;
    }

    /** Its structure set: random spread at the row's spacing, with a salt of its own from its name. */
    public static JsonObject set(Map<String, String> row, String id, String name) {
        JsonObject entry = new JsonObject();
        entry.addProperty("structure", id);
        entry.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(entry);
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("spacing", Integer.parseInt(row.getOrDefault(CustomStructure.SPACING, "34").strip()));
        placement.addProperty("separation", Integer.parseInt(row.getOrDefault(CustomStructure.SEPARATION, "8").strip()));
        placement.addProperty("salt", Math.floorMod(("strata:" + name).hashCode(), 1_000_000_000));
        JsonObject set = new JsonObject();
        set.add("structures", structures);
        set.add("placement", placement);
        return set;
    }

    private static void write(Path path, JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
    }

    /** Copies a structure file in under a name, after the same checks the pack makes. */
    public static String importFile(Path root, String name, Path from) {
        String problem = CustomStructure.nameProblem(name);
        if (problem == null) {
            problem = probe(from).problem();
        }
        if (problem != null) {
            return problem;
        }
        try {
            Files.createDirectories(dir(root));
            Files.copy(from, fileOf(root, name.strip()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (IOException e) {
            return "Could not copy the file: " + e.getMessage();
        }
    }

    /** Clears last time's structures from the pack, so a removed one leaves. */
    public static void clear(Path data) throws IOException {
        String ns = StrataDatapack.NAMESPACE;
        for (String sub : List.of("structure/custom", "worldgen/template_pool/custom", "worldgen/structure/custom",
                "worldgen/structure_set/custom")) {
            Path dir = data.resolve(ns).resolve(sub);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files = new ArrayList<>();
            try (var walk = Files.list(dir)) {
                walk.forEach(files::add);
            }
            for (Path f : files) {
                Files.deleteIfExists(f);
            }
        }
    }
}
