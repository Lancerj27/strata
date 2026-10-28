package com.strata.domain;

import com.strata.Strata;
import com.strata.data.Parse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** THE PURE HALF OF SERVING CUSTOM TEXTURES AND SOUNDS TO PLAYERS. */
public final class AssetSync {

    private AssetSync() {
    }

    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{64}$");

    /** The folder of every server's cache, under config/strata. */
    public static final String CACHE_FOLDER = "server_assets";
    /** A server's cache: the hashes of what it holds. */
    private static final String HASHES = "hashes.csv";

    /**
     * One asset of a manifest.
     *
     * @param category a texture's folder (entity, item, block, misc); "" for a sound
     * @param sha256 64 lowercase hex characters, of the file the server holds
     * @param size the file's bytes: the chunks add up to exactly this
     */
    public record Entry(String kind, String category, String name, String sha256, int size, String subtitle) {

        /** Why this entry cannot be taken, or null. */
        public String problem() {
            if (kind == null || !CustomAssetsOps.KINDS.contains(kind)) {
                return "an unknown kind";
            }
            String n = AssetRules.nameProblem(name);
            if (n != null) {
                return "a bad name";
            }
            if (AssetRules.categoryProblem(kind, category == null ? "" : category) != null) {
                return "a bad category";
            }
            if (sha256 == null || !SHA.matcher(sha256).matches()) {
                return "a bad hash";
            }
            if (size < 1 || size > AssetRules.maxFile(kind)) {
                return "a size outside the limit (" + (AssetRules.maxFile(kind) >> 10) + " KB a file)";
            }
            return null;
        }

        /** The entry with its subtitle made plain text (a sound's; a texture has none). */
        public Entry cleaned() {
            String s = CustomAssetsOps.SOUND.equals(kind) ? AssetRules.cleanSubtitle(subtitle) : "";
            if (s.length() > AssetRules.MAX_SUBTITLE) {
                s = s.substring(0, AssetRules.MAX_SUBTITLE).strip();
            }
            return new Entry(kind, category == null ? "" : category, name, sha256, size, s);
        }

        public String key() {
            return kind + "/" + name;
        }

        CustomAssetsOps.Asset asset() {
            return new CustomAssetsOps.Asset(kind, name, "", category, subtitle);
        }
    }

    /** What a client took of a manifest, and what it dropped (one line each, for the log). */
    public record Accepted(List<Entry> entries, List<String> notices) {
    }

    /**
     * A client's check of a manifest: an entry with anything wrong is dropped, so is a second entry of the
     * same kind and name.
     */
    public static Accepted accept(List<Entry> manifest) {
        List<Entry> out = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry e : manifest) {
            String problem = e == null ? "an empty entry" : e.problem();
            if (problem != null) {
                notices.add("Dropped a server asset with " + problem);
                continue;
            }
            Entry clean = e.cleaned();
            if (!seen.add(clean.key())) {
                notices.add("Dropped a second server asset called " + clean.name());
                continue;
            }
            out.add(clean);
        }
        return new Accepted(out, notices);
    }

    // ---------------------------------------------------------------- places

    /** The place of a server's cache: config/strata/server_assets/ and 16 hex characters of the address's hash. */
    public static Path cacheDir(Path configRoot, String address) {
        String key = address == null ? "" : address.strip().toLowerCase(Locale.ROOT);
        return configRoot.resolve(CACHE_FOLDER).resolve(sha256(key.getBytes(StandardCharsets.UTF_8)).substring(0, 16));
    }

    /** The place of an entry's file inside a library or cache root. */
    public static Path fileFor(Path root, Entry e) {
        if (e.problem() != null) {
            throw new IllegalArgumentException("Not an asset: " + e.problem());
        }
        Path pack = CustomAssetsOps.packDir(root).toAbsolutePath().normalize();
        Path file = e.asset().packFile(pack).toAbsolutePath().normalize();
        if (!file.startsWith(pack)) {
            throw new IllegalArgumentException("Not inside the pack");
        }
        return file;
    }

    // ---------------------------------------------------------------- hashes

    public static String sha256(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : d) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What a cache says it holds: "kind/name" to the hash of the file the server sent. */
    static Map<String, String> readHashes(Path cache) {
        Map<String, String> out = new LinkedHashMap<>();
        Path file = cache.resolve(HASHES);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] c = Parse.row(line);
                if (c.length == 3 && CustomAssetsOps.KINDS.contains(c[0]) && SHA.matcher(c[2]).matches()) {
                    out.put(c[0] + "/" + c[1], c[2]);
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read {}: {}", file, e.toString());
        }
        return out;
    }

    private static void writeHashes(Path cache, Map<String, String> hashes) throws IOException {
        Files.createDirectories(cache);
        StringBuilder text = new StringBuilder();
        hashes.forEach((k, v) -> {
            int cut = k.indexOf('/');
            text.append(k, 0, cut).append(',').append(k, cut + 1, k.length()).append(',').append(v).append('\n');
        });
        Path temp = cache.resolve(HASHES + ".tmp");
        Files.writeString(temp, text.toString(), StandardCharsets.UTF_8);
        Files.move(temp, cache.resolve(HASHES), StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------------------------------------------------------------- client: what is needed

    /** The entries whose file the cache does not hold with that hash. */
    public static List<Entry> needed(Path cache, List<Entry> manifest) {
        Map<String, String> held = readHashes(cache);
        List<Entry> out = new ArrayList<>();
        for (Entry e : manifest) {
            if (!e.sha256().equals(held.get(e.key())) || !Files.isRegularFile(fileFor(cache, e))) {
                out.add(e);
            }
        }
        return out;
    }

    /** The distinct hashes to ask a server for, in manifest order. */
    public static List<String> neededHashes(List<Entry> needed) {
        return new ArrayList<>(needed.stream().map(Entry::sha256).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    /** The size of a manifest's files in all: for the words of the question to the player, not a limit. */
    public static long totalBytes(List<Entry> entries) {
        long sum = 0;
        for (Entry e : entries) {
            sum += e.size();
        }
        return sum;
    }

    /**
     * Keeps a verified file in the cache: the CLEAN bytes {@link AssetRules#check} made from what came,
     * written under the entry's derived path, and the hash the server named recorded.
     */
    public static void store(Path cache, Entry e, byte[] clean) throws IOException {
        Path file = fileFor(cache, e);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, clean);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        Map<String, String> hashes = readHashes(cache);
        hashes.put(e.key(), e.sha256());
        writeHashes(cache, hashes);
    }

    /**
     * Makes the cache exactly the manifest's entries that arrived: writes the index, sounds.json and the
     * subtitle lines HERE (the server never sends those), removes every file the index does not list, and
     * drops the hashes of what is gone.
     */
    /** The root's replacements whose custom file is in the manifest, as the lines the manifest carries. */
    public static List<String> replacementLines(Path root, List<Entry> manifest) {
        java.util.Set<String> held = new java.util.HashSet<>();
        for (Entry e : manifest) {
            held.add(e.kind() + "|" + e.name());
        }
        return CustomAssetsOps.replacementLines(CustomAssetsOps.replacements(root).stream()
                .filter(r -> held.contains(r.kind() + "|" + r.customId()))
                .limit(CustomAssetsOps.MAX_REPLACEMENTS).toList());
    }

    public static List<Entry> finishSet(Path cache, List<Entry> manifest) throws IOException {
        Map<String, String> held = readHashes(cache);
        List<Entry> have = new ArrayList<>();
        List<CustomAssetsOps.Asset> assets = new ArrayList<>();
        Map<String, String> keep = new LinkedHashMap<>();
        for (Entry e : manifest) {
            if (e.sha256().equals(held.get(e.key())) && Files.isRegularFile(fileFor(cache, e))) {
                have.add(e);
                assets.add(e.asset());
                keep.put(e.key(), e.sha256());
            }
        }
        CustomAssetsOps.writeIndex(cache, assets);
        CustomAssetsOps.writePack(cache, assets);
        writeHashes(cache, keep);
        return have;
    }

    // ---------------------------------------------------------------- receiving a file

    /** A file arriving. */
    public static final class Receiver {
        private final String kind;
        private final String sha256;
        private final int size;
        private final int chunkBytes;
        private final byte[] buffer;
        private int got;
        private int next;

        public Receiver(String kind, String sha256, int size, int chunkBytes) {
            if (size < 1 || size > AssetRules.maxFile(kind)) {
                throw new IllegalArgumentException("size");
            }
            this.kind = kind;
            this.sha256 = sha256;
            this.size = size;
            this.chunkBytes = chunkBytes;
            this.buffer = new byte[size];
        }

        public int nextIndex() {
            return next;
        }

        public int got() {
            return got;
        }

        public boolean complete() {
            return got == size;
        }

        /** Takes a chunk; the reason it was refused, or null. */
        public String accept(int index, byte[] data) {
            if (index != next) {
                return "a chunk out of order";
            }
            if (data == null || data.length == 0) {
                return "an empty chunk";
            }
            int expected = Math.min(chunkBytes, size - got);
            if (expected <= 0) {
                return "more data than the file's size";
            }
            if (data.length != expected) {
                return data.length > expected ? "a chunk that runs over the file's size" : "a chunk of the wrong length";
            }
            System.arraycopy(data, 0, buffer, got, data.length);
            got += data.length;
            next++;
            return null;
        }

        /** The file's hash checked, then its format (a receive is strict). */
        public AssetRules.Checked finish() {
            if (!complete()) {
                return AssetRules.Checked.bad("the file is not all there");
            }
            if (!sha256(buffer).equals(sha256)) {
                return AssetRules.Checked.bad("the file's hash is not the one named");
            }
            return AssetRules.check(kind, buffer, true);
        }

        /** The file as it arrived, after {@link #finish} said it is good (an upload's own bytes). */
        public byte[] bytes() {
            return Arrays.copyOf(buffer, size);
        }
    }

    /** How many chunks a file of a size is, in chunks of a length. */
    public static int chunkCount(int size, int chunkBytes) {
        return (size + chunkBytes - 1) / chunkBytes;
    }

    /** The chunk of a file at an index: a slice of at most the chunk size. */
    public static byte[] chunk(byte[] file, int index, int chunkBytes) {
        int from = index * chunkBytes;
        if (index < 0 || from >= file.length) {
            return new byte[0];
        }
        return Arrays.copyOfRange(file, from, Math.min(file.length, from + chunkBytes));
    }

    // ---------------------------------------------------------------- server: the manifest

    /** What a server offers: the entries, where each file is (by hash), and the lines about files left out. */
    public record Built(List<Entry> entries, Map<String, Path> files, List<String> notices) {
    }

    private record Verified(long modified, long length, String sha256, String problem) {
    }

    private static final Map<Path, Verified> VERIFIED = new ConcurrentHashMap<>();

    /**
     * The manifest of a library: every asset of the index whose file is still good under the same strict
     * rules a client will apply.
     */
    public static Built buildManifest(Path root) {
        List<Entry> entries = new ArrayList<>();
        Map<String, Path> files = new LinkedHashMap<>();
        List<String> notices = new ArrayList<>();
        for (CustomAssetsOps.Asset a : CustomAssetsOps.all(root)) {
            Path file = a.packFile(CustomAssetsOps.packDir(root)).toAbsolutePath().normalize();
            String label = a.kind().toLowerCase(Locale.ROOT) + " " + a.id();
            try {
                if (!Files.isRegularFile(file)) {
                    notices.add("The custom " + label + " has no file, so it is not served");
                    continue;
                }
                long length = Files.size(file);
                long modified = Files.getLastModifiedTime(file).toMillis();
                Verified known = VERIFIED.get(file);
                if (known == null || known.modified() != modified || known.length() != length) {
                    known = verify(a.kind(), file, modified, length);
                    VERIFIED.put(file, known);
                }
                if (known.problem() != null) {
                    notices.add("The custom " + label + " is not served: " + known.problem());
                    continue;
                }
                Entry e = new Entry(a.kind(), a.category(), a.id(), known.sha256(), (int) length,
                        a.subtitle() == null ? "" : a.subtitle()).cleaned();
                if (e.problem() != null) {
                    notices.add("The custom " + label + " is not served: " + e.problem());
                    continue;
                }
                entries.add(e);
                files.put(e.sha256(), file);
            } catch (IOException ex) {
                notices.add("The custom " + label + " could not be read: " + ex.getMessage());
            }
        }
        return new Built(List.copyOf(entries), files, notices);
    }

    private static Verified verify(String kind, Path file, long modified, long length) throws IOException {
        if (length > AssetRules.maxFile(kind)) {
            return new Verified(modified, length, "", "it is over the " + (AssetRules.maxFile(kind) >> 10) + " KB limit");
        }
        byte[] data = Files.readAllBytes(file);
        AssetRules.Checked checked = AssetRules.check(kind, data, true);
        return new Verified(modified, length, checked.ok() ? sha256(data) : "", checked.problem());
    }

    // ---------------------------------------------------------------- limits of rate

    /** A server's send rate to one player: {@link AssetRules#SEND_BYTES_PER_SECOND}. */
    public static final class Throttle {
        private final long bytesPerSecond;
        private long nextFree;

        public Throttle() {
            this(AssetRules.SEND_BYTES_PER_SECOND);
        }

        public Throttle(long bytesPerSecond) {
            this.bytesPerSecond = bytesPerSecond;
        }

        public synchronized long delayMs(long nowMs, int bytes) {
            long start = Math.max(nowMs, nextFree);
            nextFree = start + bytes * 1000L / bytesPerSecond;
            return start - nowMs;
        }
    }

    /** An operator's uploads: one every {@link AssetRules#UPLOAD_GAP_MS}, {@link AssetRules#UPLOAD_BYTES_PER_MINUTE} a minute. */
    public static final class UploadLimiter {
        private long lastStart = Long.MIN_VALUE / 2;
        private long windowStart = Long.MIN_VALUE / 2;
        private long windowBytes;

        /** Null when the upload may start now (and is counted); otherwise the words that say to wait. */
        public synchronized String allow(long nowMs, long bytes) {
            if (nowMs - lastStart < AssetRules.UPLOAD_GAP_MS) {
                return "Wait a moment between uploads";
            }
            if (nowMs - windowStart >= 60_000) {
                windowStart = nowMs;
                windowBytes = 0;
            }
            if (windowBytes + bytes > AssetRules.UPLOAD_BYTES_PER_MINUTE) {
                return "Too much uploaded this minute: the most is " + (AssetRules.UPLOAD_BYTES_PER_MINUTE >> 20)
                        + " MB a minute";
            }
            lastStart = nowMs;
            windowBytes += bytes;
            return null;
        }
    }

    // ---------------------------------------------------------------- server: files on disk

    /** The folder an operator drops files in, for a server with no editor: /strata assets reload reads it. */
    public static Path importDir(Path root) {
        return root.resolve("custom").resolve("import");
    }

    /**
     * Imports what is in the import folder: textures as import/&lt;category&gt;/&lt;name&gt;.png, sounds as
     * import/sounds/&lt;name&gt;.ogg.
     */
    public static List<String> importFolder(Path root) {
        List<String> lines = new ArrayList<>();
        Path base = importDir(root);
        if (!Files.isDirectory(base)) {
            return lines;
        }
        for (String category : AssetRules.CATEGORIES) {
            importKind(root, base.resolve(category), CustomAssetsOps.TEXTURE, category, ".png", lines);
        }
        importKind(root, base.resolve("sounds"), CustomAssetsOps.SOUND, "", ".ogg", lines);
        importKind(root, base.resolve("models"), CustomAssetsOps.MODEL, "", ".json", lines);
        return lines;
    }

    private static void importKind(Path root, Path dir, String kind, String category, String ext, List<String> lines) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> list = Files.list(dir)) {
            for (Path file : list.filter(Files::isRegularFile).sorted().toList()) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(ext)) {
                    continue;
                }
                String name = fileName.substring(0, fileName.length() - ext.length());
                try {
                    if (Files.size(file) > AssetRules.maxFile(kind)) {
                        lines.add(fileName + ": too big");
                        continue;
                    }
                    CustomAssetsOps.Asset old = CustomAssetsOps.find(root, kind, name);
                    WriteReport r = CustomAssetsOps.addBytes(root, kind, name, category, old == null ? "" : old.subtitle(),
                            Files.readAllBytes(file), fileName, true);
                    // The report opened the rollback window on this thread; no Commit closes it here.
                    com.strata.data.Rollback.close(r.ok());
                    lines.add(fileName + ": " + (r.ok() ? "imported" : r.refusal() != null ? r.refusal() : "failed"));
                } catch (IOException e) {
                    lines.add(fileName + ": could not be read");
                }
            }
        } catch (IOException e) {
            lines.add(dir.getFileName() + ": could not be listed");
        }
    }
}
