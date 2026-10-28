package com.strata.domain;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.strata.Strata;
import com.strata.data.Parse;
import com.strata.data.Paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** THE CUSTOM TEXTURES AND SOUNDS LIBRARY. */
public final class CustomAssetsOps {

    private CustomAssetsOps() {
    }

    /** The namespace of every custom asset. */
    public static final String NAMESPACE = "strata_custom";
    public static final String TEXTURE = "Texture";
    public static final String SOUND = "Sound";
    /** A block or item model (JSON). */
    public static final String MODEL = "Model";
    public static final List<String> KINDS = List.of(TEXTURE, SOUND, MODEL);
    /** The folders under textures/ a custom texture goes in. */
    public static final List<String> CATEGORIES = AssetRules.CATEGORIES;
    /** 1.21.1's resource pack format. */
    public static final int PACK_FORMAT = 34;
    /** Every limit of the library is a hard coded constant of {@link AssetRules}; this is only its alias. */
    public static final int MAX_SUBTITLE = AssetRules.MAX_SUBTITLE;

    private static final String INDEX_HEADER = "kind,id,source,category,subtitle";

    /**
     * One custom asset.
     *
     * @param id the name it was given: the file name and the sound event's path
     * @param source the file name it was imported from
     * @param category a texture's folder under textures/; "" for a sound
     * @param subtitle a sound's subtitle text; "" for none
     */
    public record Asset(String kind, String id, String source, String category, String subtitle) {

        public boolean isTexture() {
            return TEXTURE.equals(kind);
        }

        public boolean isSound() {
            return SOUND.equals(kind);
        }

        /** The id the game uses: a texture's full resource path, a sound's event id, a model's id. */
        public String resource() {
            return isTexture() ? NAMESPACE + ":textures/" + category + "/" + id + ".png"
                    : MODEL.equals(kind) ? NAMESPACE + ":custom/" + id : NAMESPACE + ":" + id;
        }

        /** How a picker shows it. */
        public String label() {
            return "Custom · " + id;
        }

        /** The file's place inside the pack folder. */
        public Path packFile(Path pack) {
            Path ns = pack.resolve("assets").resolve(NAMESPACE);
            return isTexture() ? ns.resolve("textures").resolve(category).resolve(id + ".png")
                    : MODEL.equals(kind) ? ns.resolve("models").resolve("custom").resolve(id + ".json")
                    : ns.resolve("sounds").resolve(id + ".ogg");
        }
    }

    /**
     * A game sound or texture that a custom asset of the same kind replaces.
     *
     * @param target a sound event id ("minecraft:entity.creeper.primed"), or a texture's full path
     *     ("minecraft:textures/entity/zombie/zombie.png")
     * @param customId the custom asset's name
     */
    public record Replacement(String kind, String target, String customId) {
    }

    /** What an import is asked to do. */
    public record Request(String kind, Path file, String name, String category, String subtitle, boolean replace) {
    }

    /**
     * What a look at a file found: a problem (the words to show, and nothing is imported), or a summary line
     * for the preview.
     */
    public record Probe(String problem, String summary) {

        public boolean ok() {
            return problem == null;
        }

        static Probe bad(String problem) {
            return new Probe(problem, "");
        }

        static Probe good(String summary) {
            return new Probe(null, summary);
        }
    }

    // ------------------------------------------------------------------ places

    public static Path packDir(Path root) {
        return root.resolve("assets_pack");
    }

    public static Path indexFile(Path root) {
        return root.resolve("custom").resolve("custom_assets.csv");
    }

    /** The replacements table: a profile table, so profiles carry it and Load to server sends it. */
    public static final String REPLACEMENTS_FILE = "asset_replacements.csv";
    public static final String REPLACEMENTS_TEMPLATE =
            "# Game sounds and textures replaced by custom ones (Options > Sounds, Options > Textures).\n"
                    + "kind,target,custom\n";
    /** The most replacements a server's manifest may carry. */
    public static final int MAX_REPLACEMENTS = 4096;

    public static Path replacementsFile(Path root) {
        return root.resolve(REPLACEMENTS_FILE);
    }

    // ------------------------------------------------------------------ checks

    /** Why a name cannot be used, or null: 1 to 32 lowercase letters, digits or _. */
    public static String nameProblem(String name) {
        return AssetRules.nameProblem(name);
    }

    /** A name made from a file's name: lowercase, anything else an underscore, no extension, 32 at most. */
    public static String suggestName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf((char) 92));
        base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String out = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (out.length() > AssetRules.MAX_NAME) {
            out = out.substring(0, AssetRules.MAX_NAME);
        }
        return out;
    }


    /** A typed or pasted path as a path: quotes ("Copy as path") and spaces around it dropped. */
    public static Path pathOf(String typed) {
        if (typed == null) {
            return null;
        }
        String s = typed.strip();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).strip();
        }
        if (s.isEmpty()) {
            return null;
        }
        try {
            return java.nio.file.Paths.get(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * A look at a file on disk: the WHOLE file is read (at most one byte over the kind's size limit, so a
     * huge file is never loaded) and checked by {@link AssetRules#check} as an import would, so what the
     * preview says is what Add does.
     */
    public static Probe probe(String kind, Path file) {
        if (file == null) {
            return Probe.bad("Pick a file, or type its path.");
        }
        if (!Files.isRegularFile(file)) {
            return Probe.bad("There is no file at " + file + ".");
        }
        try {
            long size = Files.size(file);
            int max = AssetRules.maxFile(kind);
            if (size > max) {
                return Probe.bad("That file is " + (size >> 10) + " KB: the most is " + (max >> 10) + " KB.");
            }
            AssetRules.Checked checked = AssetRules.check(kind, Files.readAllBytes(file), false);
            return checked.ok() ? Probe.good(checked.summary()) : Probe.bad(checked.problem());
        } catch (IOException e) {
            return Probe.bad("Could not read that file: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------- index

    /** Every custom asset, in the order they were added. */
    public static List<Asset> all(Path root) {
        List<Asset> out = new ArrayList<>();
        Path index = indexFile(root);
        if (!Files.isRegularFile(index)) {
            return out;
        }
        try {
            boolean header = true;
            for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                String t = line.strip();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] c = Parse.row(line);
                if (c.length < 2 || !KINDS.contains(c[0]) || nameProblem(c[1]) != null) {
                    continue;
                }
                out.add(new Asset(c[0], c[1], c.length > 2 ? c[2] : "", c.length > 3 ? c[3] : "",
                        c.length > 4 ? c[4] : ""));
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read the custom assets index {}: {}", index, e.toString());
        }
        return out;
    }

    public static List<Asset> all() {
        return all(Paths.ROOT);
    }

    public static List<Asset> ofKind(Path root, String kind) {
        return all(root).stream().filter(a -> a.kind().equals(kind)).toList();
    }

    /** The asset of a kind and name, or null. */
    public static Asset find(Path root, String kind, String id) {
        for (Asset a : all(root)) {
            if (a.kind().equals(kind) && a.id().equals(id)) {
                return a;
            }
        }
        return null;
    }

    public static void writeIndex(Path root, List<Asset> assets) throws IOException {
        Path index = indexFile(root);
        Files.createDirectories(index.getParent());
        StringBuilder text = new StringBuilder("# Strata custom textures and sounds (this machine's, not a profile's).\n");
        text.append(INDEX_HEADER).append('\n');
        for (Asset a : assets) {
            text.append(Parse.line(new String[]{a.kind(), a.id(), a.source(), a.category(), a.subtitle()})).append('\n');
        }
        writeAtomic(index, text.toString());
    }

    /** Every replacement, in the order they were made. */
    public static List<Replacement> replacements(Path root) {
        List<Replacement> out = new ArrayList<>();
        Path file = replacementsFile(root);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            boolean header = true;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.strip().startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] c = Parse.row(line);
                if (c.length >= 3 && KINDS.contains(c[0]) && targetProblem(c[0], c[1]) == null) {
                    out.add(new Replacement(c[0], c[1], c[2]));
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read the asset replacements {}: {}", file, e.toString());
        }
        return out;
    }

    public static List<Replacement> replacements() {
        return replacements(Paths.ROOT);
    }

    private static void writeReplacements(Path root, List<Replacement> rows) throws IOException {
        StringBuilder text = new StringBuilder(REPLACEMENTS_TEMPLATE);
        for (Replacement r : rows) {
            text.append(Parse.line(new String[]{r.kind(), r.target(), r.customId()})).append('\n');
        }
        writeAtomic(replacementsFile(root), text.toString());
    }

    /** Why a game id cannot be replaced, or null. */
    public static String targetProblem(String kind, String target) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(
                target == null ? "" : target);
        if (id == null) {
            return "Not an id: " + target;
        }
        // The id becomes a file path in the pack: "." and ".." would leave the pack folder.
        for (String part : (id.getNamespace() + "/" + id.getPath()).split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return "Not an id: " + target;
            }
        }
        if (NAMESPACE.equals(id.getNamespace())) {
            return "A custom file is changed by re-importing it, not by a replacement";
        }
        if (TEXTURE.equals(kind) && !(id.getPath().startsWith("textures/") && id.getPath().endsWith(".png"))) {
            return "Not a texture path: " + target;
        }
        if (MODEL.equals(kind) && !((id.getPath().startsWith("models/block/") || id.getPath().startsWith("models/item/"))
                && id.getPath().endsWith(".json"))) {
            return "Not a block or item model path: " + target;
        }
        return null;
    }

    /** The custom asset a picker's id names (its resource()), or null. */
    public static Asset byResource(Path root, String resource) {
        for (Asset a : all(root)) {
            if (a.resource().equals(resource)) {
                return a;
            }
        }
        return null;
    }

    /** Replaces a game sound or texture with a custom one of the same kind, then rewrites the pack. */
    public static WriteReport replace(Path root, String kind, String target, String customId) {
        String problem = targetProblem(kind, target);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        if (find(root, kind, customId) == null) {
            return WriteReport.refused("A " + kind.toLowerCase(Locale.ROOT) + " is replaced only by a custom "
                    + kind.toLowerCase(Locale.ROOT) + ". There is none called " + customId);
        }
        List<Replacement> rows = new ArrayList<>(replacements(root));
        rows.removeIf(r -> r.kind().equals(kind) && r.target().equals(target));
        rows.add(new Replacement(kind, target, customId));
        return writeReplacementsAndPack(root, rows, target);
    }

    /** Puts the game's own sound or texture back. */
    public static WriteReport restoreGame(Path root, String kind, String target) {
        List<Replacement> rows = new ArrayList<>(replacements(root));
        if (!rows.removeIf(r -> r.kind().equals(kind) && r.target().equals(target))) {
            return WriteReport.refused(target + " is not replaced");
        }
        return writeReplacementsAndPack(root, rows, target);
    }

    private static WriteReport writeReplacementsAndPack(Path root, List<Replacement> rows, String target) {
        WriteReport report = new WriteReport();
        try {
            writeReplacements(root, rows);
            writePack(root, all(root));
        } catch (IOException e) {
            Strata.LOGGER.error("Could not write the replacement of {}: {}", target, e.toString());
            report.record("the replacement of " + target, false);
        }
        return report;
    }

    /** The replacements as one CSV line each, as the server's manifest carries them. */
    public static List<String> replacementLines(List<Replacement> rows) {
        return rows.stream().map(r -> Parse.line(new String[]{r.kind(), r.target(), r.customId()})).toList();
    }

    /**
     * Makes a root's replacements the lines a server sent, checked as a file read is (a bad line is dropped),
     * at most {@link #MAX_REPLACEMENTS}.
     */
    public static void writeReplacementLines(Path root, List<String> lines) throws IOException {
        StringBuilder text = new StringBuilder(REPLACEMENTS_TEMPLATE);
        lines.stream().limit(MAX_REPLACEMENTS).forEach(l -> text.append(l.replace("\n", "").replace("\r", "")).append('\n'));
        writeAtomic(replacementsFile(root), text.toString());
        writeReplacements(root, replacements(root));
    }

    /** Writes the pack again from a root's library and replacements; a failure is logged, not thrown. */
    public static void refreshPack(Path root) {
        try {
            writePack(root, all(root));
        } catch (IOException e) {
            Strata.LOGGER.error("Could not write the custom assets pack in {}: {}", root, e.toString());
        }
    }

    // The editor's writes are kept in the active profile as any editor change is (Commit), or the next launch
    // puts the profile's table back.
    public static WriteReport replace(String kind, String target, String customId) {
        return Commit.reloadIfOk(replace(Paths.ROOT, kind, target, customId));
    }

    public static WriteReport restoreGame(String kind, String target) {
        return Commit.reloadIfOk(restoreGame(Paths.ROOT, kind, target));
    }

    // -------------------------------------------------------------------- pack

    /** Makes the pack folder and its pack.mcmeta, so the game can register the pack from the first launch. */
    public static void ensurePack(Path root) throws IOException {
        Path pack = packDir(root);
        Files.createDirectories(pack);
        Path meta = pack.resolve("pack.mcmeta");
        if (!Files.isRegularFile(meta)) {
            JsonObject inner = new JsonObject();
            inner.addProperty("pack_format", PACK_FORMAT);
            inner.addProperty("description", "Strata - your custom textures and sounds");
            JsonObject top = new JsonObject();
            top.add("pack", inner);
            writeAtomic(meta, new GsonBuilder().setPrettyPrinting().create().toJson(top));
        }
    }

    /**
     * Writes sounds.json and the subtitle lines from the index, and removes any file of the pack the index
     * does not list.
     */
    public static void writePack(Path root, List<Asset> assets) throws IOException {
        ensurePack(root);
        Path pack = packDir(root);
        Path ns = pack.resolve("assets").resolve(NAMESPACE);
        Files.createDirectories(ns.resolve("lang"));

        JsonObject sounds = new JsonObject();
        JsonObject lang = new JsonObject();
        for (Asset a : assets) {
            if (!a.isSound()) {
                continue;
            }
            JsonObject event = new JsonObject();
            JsonArray files = new JsonArray();
            files.add(NAMESPACE + ":" + a.id());
            event.add("sounds", files);
            if (!a.subtitle().isBlank()) {
                String key = NAMESPACE + ".subtitle." + a.id();
                event.addProperty("subtitle", key);
                lang.addProperty(key, a.subtitle());
            }
            sounds.add(a.id(), event);
        }
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        writeAtomic(ns.resolve("sounds.json"), gson.toJson(sounds));
        writeAtomic(ns.resolve("lang").resolve("en_us.json"), gson.toJson(lang));

        java.util.Set<Path> keep = new java.util.HashSet<>();
        for (Asset a : assets) {
            keep.add(a.packFile(pack).toAbsolutePath().normalize());
        }
        for (String folder : List.of("textures", "sounds", "models")) {
            Path dir = ns.resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    if (!keep.contains(file.toAbsolutePath().normalize())) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        }
        writeReplacementFiles(root, pack, assets, gson);
    }

    /**
     * The replacements in the pack: a sound as its namespace's sounds.json entry with "replace", a texture as
     * a copy of the custom PNG at the game's path. Every other file of another namespace goes.
     */
    private static void writeReplacementFiles(Path root, Path pack, List<Asset> assets, com.google.gson.Gson gson)
            throws IOException {
        java.util.Set<Path> keep = new java.util.HashSet<>();
        java.util.Map<String, JsonObject> soundsByNs = new java.util.TreeMap<>();
        for (Replacement r : replacements(root)) {
            Asset custom = assets.stream().filter(a -> a.kind().equals(r.kind()) && a.id().equals(r.customId()))
                    .findFirst().orElse(null);
            if (custom == null) {
                continue;
            }
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(r.target());
            Path ns = pack.resolve("assets").resolve(id.getNamespace());
            // A texture or a model: the custom file at the game's path.
            if (!custom.isSound()) {
                Path dest = ns.resolve(id.getPath());
                Files.createDirectories(dest.getParent());
                Files.copy(custom.packFile(pack), dest, StandardCopyOption.REPLACE_EXISTING);
                keep.add(dest.toAbsolutePath().normalize());
            } else {
                JsonObject event = new JsonObject();
                event.addProperty("replace", true);
                JsonArray files = new JsonArray();
                files.add(NAMESPACE + ":" + custom.id());
                event.add("sounds", files);
                soundsByNs.computeIfAbsent(id.getNamespace(), k -> new JsonObject()).add(id.getPath(), event);
            }
        }
        for (var e : soundsByNs.entrySet()) {
            Path file = pack.resolve("assets").resolve(e.getKey()).resolve("sounds.json");
            writeAtomic(file, gson.toJson(e.getValue()));
            keep.add(file.toAbsolutePath().normalize());
        }
        Path assetsDir = pack.resolve("assets");
        if (!Files.isDirectory(assetsDir)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(assetsDir)) {
            for (Path ns : namespaces.filter(Files::isDirectory).toList()) {
                if (ns.getFileName().toString().equals(NAMESPACE)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(ns)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        if (!keep.contains(file.toAbsolutePath().normalize())) {
                            Files.deleteIfExists(file);
                        }
                    }
                }
            }
        }
    }

    private static void writeAtomic(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------ writes

    /**
     * Imports a file: checks the name and the file's bytes, copies it into the pack, adds it to the index,
     * and rewrites sounds.json.
     */
    public static WriteReport add(Path root, Request r) {
        if (!KINDS.contains(r.kind())) {
            return WriteReport.refused("Pick Texture, Sound or Model");
        }
        if (r.file() == null || !Files.isRegularFile(r.file())) {
            return WriteReport.refused("There is no file at " + r.file() + ".");
        }
        byte[] raw;
        try {
            if (Files.size(r.file()) > AssetRules.maxFile(r.kind())) {
                return WriteReport.refused("That file is bigger than the " + (AssetRules.maxFile(r.kind()) >> 10)
                        + " KB a " + r.kind().toLowerCase(Locale.ROOT) + " may be.");
            }
            raw = Files.readAllBytes(r.file());
        } catch (IOException e) {
            return WriteReport.refused("Could not read that file: " + e.getMessage());
        }
        return addBytes(root, r.kind(), r.name(), r.category(), r.subtitle(), raw,
                r.file().getFileName().toString(), r.replace());
    }

    /**
     * Imports a file's BYTES: the one door every import goes through, whether a person picked the file, an
     * operator uploaded it, or the server's import folder held it.
     */
    public static WriteReport addBytes(Path root, String kind, String name, String category, String subtitle,
                                       byte[] raw, String source, boolean replace) {
        if (!KINDS.contains(kind)) {
            return WriteReport.refused("Pick Texture, Sound or Model");
        }
        String nameProblem = nameProblem(name);
        if (nameProblem != null) {
            return WriteReport.refused(nameProblem);
        }
        boolean texture = TEXTURE.equals(kind);
        // A sound has no category: the form's category (meant for a texture) is ignored for it.
        String categoryProblem = texture ? AssetRules.categoryProblem(kind, category) : null;
        if (categoryProblem != null) {
            return WriteReport.refused(categoryProblem);
        }
        AssetRules.Checked checked = AssetRules.check(kind, raw, false);
        if (!checked.ok()) {
            return WriteReport.refused(checked.problem());
        }
        Asset old = find(root, kind, name);
        if (old != null && !replace) {
            return WriteReport.refused("There is a custom " + kind.toLowerCase(Locale.ROOT) + " called "
                    + name + " already. Pick another name, or re-import it from the library.");
        }
        String clean = SOUND.equals(kind) ? AssetRules.cleanSubtitle(subtitle) : "";
        String subtitleProblem = AssetRules.subtitleProblem(clean);
        if (subtitleProblem != null) {
            return WriteReport.refused(subtitleProblem);
        }
        Asset asset = new Asset(kind, name, source == null ? "" : source, texture ? category : "", clean);
        WriteReport report = new WriteReport();
        try {
            ensurePack(root);
            Path dest = asset.packFile(packDir(root));
            Files.createDirectories(dest.getParent());
            Path temp = dest.resolveSibling(dest.getFileName() + ".tmp");
            Files.write(temp, checked.clean());
            Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
            List<Asset> assets = new ArrayList<>(all(root));
            assets.removeIf(a -> a.kind().equals(asset.kind()) && a.id().equals(asset.id()));
            assets.add(asset);
            writeIndex(root, assets);
            writePack(root, assets);
        } catch (IOException e) {
            Strata.LOGGER.error("Could not add the custom asset {}: {}", name, e.toString());
            report.record("the custom " + kind.toLowerCase(Locale.ROOT) + " " + name, false);
        }
        return report;
    }

    /** Removes an asset: its file, its index row, its sounds.json entry. */
    public static WriteReport remove(Path root, String kind, String id) {
        if (find(root, kind, id) == null) {
            return WriteReport.refused("There is no custom " + String.valueOf(kind).toLowerCase(Locale.ROOT) + " "
                    + id);
        }
        WriteReport report = new WriteReport();
        try {
            List<Asset> assets = new ArrayList<>(all(root));
            assets.removeIf(a -> a.kind().equals(kind) && a.id().equals(id));
            writeIndex(root, assets);
            writePack(root, assets);
        } catch (IOException e) {
            Strata.LOGGER.error("Could not remove the custom asset {}: {}", id, e.toString());
            report.record("the custom " + kind.toLowerCase(Locale.ROOT) + " " + id, false);
        }
        return report;
    }

    public static Asset find(String kind, String id) {
        return find(Paths.ROOT, kind, id);
    }

    public static WriteReport add(Request r) {
        return add(Paths.ROOT, r);
    }

    public static WriteReport remove(String kind, String id) {
        return remove(Paths.ROOT, kind, id);
    }

    /** The one-line words about the library, for the list's footer. */
    public static String where() {
        return "Files are copied to config/strata/assets_pack/. A server sends its own files to players.";
    }
}
