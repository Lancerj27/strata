package com.strata.data;

import com.strata.Strata;
import com.strata.model.Profile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Export/import/switch complete config profiles. */
public final class ProfileManager {

    private ProfileManager() {}

    /** Name of the common config file (ModConfig.Type.COMMON), at config/strata-common.toml. */
    static final String COMMON_CONFIG_NAME = "strata-common.toml";

    /** Outcome of an export/activate/delete call. */
    public enum Result {
        /** The operation finished and its effects are in place. */
        OK,
        /** The name is not usable as a profile directory name. */
        INVALID_NAME,
        /** The requested profile directory does not exist. */
        NOT_FOUND,
        /** The filesystem refused the operation. */
        IO_ERROR,
        /** The Default profile is the game's own: it cannot be saved over or deleted. */
        PROTECTED;

        /** The failure text every front end shows, or null for OK. */
        public String failureText() {
            return switch (this) {
                case OK -> null;
                case INVALID_NAME -> "Invalid profile name";
                case NOT_FOUND -> "Profile not found";
                case IO_ERROR -> "Profile operation failed";
                case PROTECTED -> "Default is the game's own values: Strata makes it again at every start";
            };
        }
    }

    /**
     * Why a profile name cannot be used, or null when it can: the field's red tooltip gives
     * this reason.
     */
    public static String nameProblem(String name) {
        if (name == null || name.isBlank()) {
            return "Type a name";
        }
        return isSafeName(name.strip()) ? null
                : "A file name: none of < > : \" / \\ | ? *, and no dot or space at the end";
    }

    /**
     * A profile name must be a plain directory name — no path separators, no Windows-forbidden characters and
     * not "." or "..".
     */
    public static boolean isSafeName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20) {
                return false;
            }
            if ("<>:\"/\\|?*".indexOf(c) >= 0) {
                return false;
            }
        }
        char last = name.charAt(name.length() - 1);
        return last != '.' && last != ' ';
    }

    /** The profiles folder, made when missing, for the designer to open and drop a profile folder into. */
    public static Path profilesFolder() {
        try {
            Files.createDirectories(Paths.PROFILES);
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not make the profiles folder: {}", e.toString());
        }
        return Paths.PROFILES;
    }

    public static List<Profile> listProfiles() {
        List<Profile> out = new ArrayList<>();
        Path dir = Paths.PROFILES;
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    out.add(new Profile(entry.getFileName().toString(), ""));
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not list profiles: {}", e.toString());
        }
        return out;
    }

    public static Result exportProfile(String name) {
        if (DEFAULT.equalsIgnoreCase(name == null ? "" : name.strip())) {
            return Result.PROTECTED;
        }
        if (!isSafeName(name)) {
            Strata.LOGGER.warn("Refusing to export profile with invalid name '{}'", name);
            return Result.INVALID_NAME;
        }
        Path target = Paths.profile(name);
        try {
            Files.createDirectories(target);
            copyTables(Paths.ROOT, target);
            copyConfig(Paths.CONFIGDIR, target);
            Strata.LOGGER.info("Exported profile '{}'", name);
            return Result.OK;
        } catch (IOException e) {
            Strata.LOGGER.error("Could not export profile '{}': {}", name, e.toString());
            return Result.IO_ERROR;
        }
    }

    /**
     * Loads a profile into the working config and makes it the ACTIVE profile, so every later launch loads it
     * too.
     */
    public static Result activateProfile(String name) {
        if (!isSafeName(name)) {
            Strata.LOGGER.warn("Refusing to activate profile with invalid name '{}'", name);
            return Result.INVALID_NAME;
        }
        Path source = Paths.profile(name);
        if (!Files.isDirectory(source)) {
            Strata.LOGGER.warn("Profile '{}' not found", name);
            return Result.NOT_FOUND;
        }
        try {
            copyTables(source, Paths.ROOT);
            copyConfig(source, Paths.CONFIGDIR);
            // The profile's sound and texture replacements go into the pack.
            com.strata.domain.CustomAssetsOps.refreshPack(Paths.ROOT);
            writeActive(Paths.ROOT, name);
            Strata.LOGGER.info("Activated profile '{}'", name);
            Config.reload();
            return Result.OK;
        } catch (IOException e) {
            Strata.LOGGER.error("Could not activate profile '{}': {}", name, e.toString());
            return Result.IO_ERROR;
        }
    }

    // ---- the active profile ---- THE OWNER'S RULE: every launch loads the config the modpack ships, so a
    // player never has to find it in the UI.

    /** The profile the modpack ships, loaded when no other is active. */
    public static final String SHIPPED = "shipped";

    /** THE GAME'S OWN VALUES, AS A PROFILE. */
    public static final String DEFAULT = "Default";

    /** Writes the Default profile. */
    public static void writeDefaultProfile(List<String[]> gameRules) {
        Path dir = Paths.profile(DEFAULT);
        try {
            Files.createDirectories(dir);
            // An empty overrides folder: switching to Default clears the last profile's override sheets
            // (copyOverrides keeps them for a profile with no folder, one made before the sheets existed).
            Files.createDirectories(dir.resolve(OVERRIDES));
            for (var e : Defaults.templates().entrySet()) {
                String text = e.getValue();
                if (e.getKey().equals(Schema.SpawnRules.FILE)) {
                    StringBuilder b = new StringBuilder(text.endsWith("\n") ? text : text + "\n");
                    for (String[] r : gameRules) {
                        b.append(String.join(",", r)).append('\n');
                    }
                    text = b.toString();
                }
                Files.writeString(dir.resolve(e.getKey()), text);
            }
            writeVanillaSettings(dir.resolve(COMMON_CONFIG_NAME));
        } catch (IOException | RuntimeException e) {
            Strata.LOGGER.warn("Could not write the Default profile: {}", e.toString());
        }
    }

    /**
     * Strata's settings as it ships them (StrataConfig's defaults). Default is vanilla because it has no rows
     * and every switch that changes the game on its own ships Off: an edit made on Default forks a profile
     * from these settings, and a switch is turned On where its edits are made.
     */
    private static void writeVanillaSettings(Path file) {
        // In memory, then written whole: the file builder's config is one the spec cannot correct.
        var cfg = com.electronwill.nightconfig.toml.TomlFormat.newConfig();
        StrataConfig.SPEC.correct(cfg);
        com.electronwill.nightconfig.toml.TomlFormat.instance().createWriter().write(cfg, file,
                com.electronwill.nightconfig.core.io.WritingMode.REPLACE);
    }

    /** The five switches the old Default turned Off, as they read in strata-common.toml. */
    static final List<String> OLD_DEFAULT_OFF = List.of("enableMobScaling", "enablePlayerScaling",
            "enableOreRestriction", "enableSpawnSubstitution", "enableLoot");

    /**
     * A profile forked from the old Default has all five switches Off, so no edit in it worked. When all five
     * are Off, they go On. A profile with any of them On is left as it is. Returns true when it changed.
     */
    static boolean undoOldDefaultSwitches(Path toml) {
        try {
            if (!Files.isRegularFile(toml)) {
                return false;
            }
            List<String> lines = Files.readAllLines(toml);
            java.util.regex.Pattern off = java.util.regex.Pattern.compile(
                    "^\\s*(" + String.join("|", OLD_DEFAULT_OFF) + ")\\s*=\\s*false\\s*$");
            if (lines.stream().filter(l -> off.matcher(l).matches()).count() != OLD_DEFAULT_OFF.size()) {
                return false;
            }
            Files.write(toml, lines.stream().map(l -> off.matcher(l).matches() ? l.replace("false", "true") : l)
                    .toList());
            return true;
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not check {} for the old Default switches: {}", toml, e.toString());
            return false;
        }
    }

    /** Names the active profile. */
    private static final String ACTIVE_FILE = "active-profile.txt";

    /** The profile every launch loads. */
    public static String activeProfile() {
        return activeProfile(Paths.ROOT);
    }

    static String activeProfile(Path root) {
        Path file = root.resolve(ACTIVE_FILE);
        try {
            if (Files.isRegularFile(file)) {
                String name = Files.readString(file).strip();
                if (isSafeName(name)) {
                    return name;
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read {}: {}", file, e.toString());
        }
        // No profile picked yet: the modpack's shipped profile when there is one, else Default.
        return Files.isDirectory(root.resolve("profiles").resolve(SHIPPED)) ? SHIPPED : DEFAULT;
    }

    /**
     * EVERY CHANGE IS KEPT, AND NEVER IN DEFAULT.
     *
     * @return the profile the edit was saved into, or null when it could not be
     */
    public static String autoSave() {
        try {
            String active = activeProfile();
            if (DEFAULT.equalsIgnoreCase(active)) {
                active = freeName(Paths.PROFILES);
                if (exportProfile(active) != Result.OK) {
                    return null;
                }
                writeActive(Paths.ROOT, active);
                Strata.LOGGER.info("A change made while Default was active: it is now the new profile '{}'", active);
                return active;
            }
            return exportProfile(active) == Result.OK ? active : null;
        } catch (IOException | RuntimeException e) {
            Strata.LOGGER.warn("Could not save the change into the active profile: {}", e.toString());
            return null;
        }
    }

    /** The first "Profile N" no profile folder has. */
    static String freeName(Path profiles) {
        for (int n = 1; ; n++) {
            String name = "Profile " + n;
            if (!Files.isDirectory(profiles.resolve(name))) {
                return name;
            }
        }
    }

    private static void writeActive(Path root, String name) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(ACTIVE_FILE), name + System.lineSeparator());
    }

    /** What the launch load did: the profile it tried, and the outcome. */
    public record LaunchLoad(String profile, Result result) {
    }

    /** Copies the active profile over the working config. */
    public static LaunchLoad loadActiveAtLaunch() {
        LaunchLoad load = loadActiveAtLaunch(Paths.ROOT, Paths.CONFIGDIR);
        launchLoaded = load.result() == Result.OK;
        return load;
    }

    /** True when this launch loaded the active profile over the working files. */
    private static volatile boolean launchLoaded;

    /**
     * After a config load, and after the game-start seeding: the active profile takes back the working files,
     * so the settings NeoForge added, the columns Migrations added and the seeded rows do not read as unsaved
     * edits. Every editor change is kept the same way (autoSave). Default is written by Strata at every start
     * and is not saved into.
     */
    public static void syncAfterLaunch() {
        if (!launchLoaded) {
            return;
        }
        String active = activeProfile();
        if (!DEFAULT.equalsIgnoreCase(active) && exportProfile(active) == Result.OK) {
            Strata.LOGGER.debug("Profile '{}' is in step with the working config", active);
        }
    }

    static LaunchLoad loadActiveAtLaunch(Path root, Path configDir) {
        String name = activeProfile(root);
        Path source = root.resolve("profiles").resolve(name);
        if (!Files.isDirectory(source)) {
            // A FRESH INSTALL STARTS ON DEFAULT'S SETTINGS.
            if (DEFAULT.equals(name) && !Files.exists(configDir.resolve(COMMON_CONFIG_NAME))) {
                try {
                    Files.createDirectories(configDir);
                    writeVanillaSettings(configDir.resolve(COMMON_CONFIG_NAME));
                } catch (IOException | RuntimeException e) {
                    Strata.LOGGER.warn("Could not write Default's settings on a first launch: {}", e.toString());
                }
            }
            return new LaunchLoad(name, Result.NOT_FOUND);
        }
        // A PROFILE WITH NO TABLE IS NOT LOADED: copying an empty folder over the config deleted every
        // table in it.
        if (!hasTables(source)) {
            Strata.LOGGER.warn("Profile '{}' has no tables; the config is left as it is", name);
            return new LaunchLoad(name, Result.NOT_FOUND);
        }
        try {
            copyTables(source, root);
            copyConfig(source, configDir);
            com.strata.domain.CustomAssetsOps.refreshPack(root);
            if (!DEFAULT.equals(name) && undoOldDefaultSwitches(configDir.resolve(COMMON_CONFIG_NAME))) {
                Strata.LOGGER.info("Profile '{}' had every feature switch Off from the old Default: they are On", name);
            }
            return new LaunchLoad(name, Result.OK);
        } catch (IOException e) {
            Strata.LOGGER.error("Could not load profile '{}' at launch: {}", name, e.toString());
            return new LaunchLoad(name, Result.IO_ERROR);
        }
    }

    /**
     * True when the working config has edits the next launch would reset: the active profile exists, and a
     * table differs from it.
     */
    public static boolean hasUnsavedEdits() {
        return hasUnsavedEdits(Paths.ROOT);
    }

    static boolean hasUnsavedEdits(Path root) {
        // On Default an edit becomes a new profile at once (autoSave): nothing on Default is unsaved.
        if (DEFAULT.equalsIgnoreCase(activeProfile(root))) {
            return false;
        }
        Path profile = root.resolve("profiles").resolve(activeProfile(root));
        if (!Files.isDirectory(profile)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.csv")) {
            for (Path working : stream) {
                Path saved = profile.resolve(working.getFileName());
                if (!Files.isRegularFile(saved)) {
                    // If the active profile exists but is missing files that exist in working dir (incomplete
                    // profile), do not flag as unsaved; treat as a profile state issue rather than user
                    // edits.
                    continue;
                }
                if (Files.mismatch(working, saved) != -1) {
                    return true;
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not compare the config with profile: {}", e.toString());
            return true;
        }
        return commonConfigDiffers(root.getParent(), profile);
    }

    /**
     * True when the live strata-common.toml differs from the profile's copy.
     *
     * @param configDir the folder that holds strata-common.toml
     */
    static boolean commonConfigDiffers(Path configDir, Path profile) {
        Path saved = profile.resolve(COMMON_CONFIG_NAME);
        if (configDir == null || !Files.isRegularFile(saved)) {
            return false;
        }
        Path live = configDir.resolve(COMMON_CONFIG_NAME);
        try {
            return !Files.isRegularFile(live) || !settings(Files.readAllLines(live))
                    .equals(settings(Files.readAllLines(saved)));
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not compare {} with the profile: {}", COMMON_CONFIG_NAME, e.toString());
            return true;
        }
    }

    /** A toml file's setting lines, trimmed, without comments and blank lines. */
    static List<String> settings(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String s = line.strip();
            if (!s.isEmpty() && !s.startsWith("#")) {
                out.add(s.replace(" ", ""));
            }
        }
        return out;
    }

    /**
     * True when saving the working config under this name overwrites a profile that is not the active one.
     */
    public static boolean saveReplacesOther(String name) {
        return saveReplacesOther(Paths.ROOT, name);
    }

    static boolean saveReplacesOther(Path root, String name) {
        String n = name == null ? "" : name.strip();
        return isSafeName(n) && !n.equals(activeProfile(root))
                && Files.isDirectory(root.resolve("profiles").resolve(n));
    }

    /**
     * True when uploading this profile would leave out edits: it is the active profile, and the working
     * config differs from it.
     */
    public static boolean uploadMissesEdits(String name) {
        return uploadMissesEdits(Paths.ROOT, name);
    }

    static boolean uploadMissesEdits(Path root, String name) {
        return name != null && name.strip().equals(activeProfile(root)) && hasUnsavedEdits(root);
    }

    // ---- a profile moved to a server ----

    /** The most text one upload may carry, all files together. */
    public static final int MAX_UPLOAD_CHARS = 4 << 20;

    /** The most text one file of an upload may carry: what the network codec takes (StrataPayloads). */
    public static final int MAX_FILE_CHARS = 1 << 20;

    /** A table file name as Strata writes them. */
    private static final java.util.regex.Pattern TABLE_NAME =
            java.util.regex.Pattern.compile("[a-z0-9_\\-]+\\.csv");

    /** The folder of the override sheets (JsonOverrides), in config/strata and in a profile. */
    static final String OVERRIDES = "overrides";

    /** An override sheet as a profile carries it: overrides/{kind}.json. */
    private static final java.util.regex.Pattern OVERRIDE_NAME =
            java.util.regex.Pattern.compile("overrides/[a-z0-9_\\-]+\\.json");

    /** A profile's files as name to text, for an upload from this machine. */
    public static java.util.Map<String, String> profileFiles(String name) {
        if (!isSafeName(name)) {
            return new java.util.LinkedHashMap<>();
        }
        return filesIn(Paths.profile(name), name);
    }

    /** A profile folder's table files and common config, as name to text. */
    static java.util.Map<String, String> filesIn(Path dir, String name) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (Files.isRegularFile(file)
                        && (TABLE_NAME.matcher(fileName).matches() || COMMON_CONFIG_NAME.equals(fileName))) {
                    out.put(fileName, Files.readString(file));
                }
            }
            Path overrides = dir.resolve(OVERRIDES);
            if (Files.isDirectory(overrides)) {
                try (DirectoryStream<Path> sheets = Files.newDirectoryStream(overrides, "*.json")) {
                    for (Path sheet : sheets) {
                        String key = OVERRIDES + "/" + sheet.getFileName();
                        if (Files.isRegularFile(sheet) && OVERRIDE_NAME.matcher(key).matches()) {
                            out.put(key, Files.readString(sheet));
                        }
                    }
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read profile '{}' for upload: {}", name, e.toString());
            return java.util.Map.of();
        }
        return out;
    }

    /** Why the server refuses an uploaded profile, or null when it accepts it. */
    public static String uploadProblem(String name, java.util.Map<String, String> files) {
        return filesProblem(name, files, true);
    }

    /**
     * The one rule for a profile that arrives from outside, an upload to a server or an imported file: a safe
     * name, table files and the common config only, at least one table, and a bounded size.
     *
     * @param upload true for a server upload, false for an import: only the words differ
     */
    private static String filesProblem(String name, java.util.Map<String, String> files, boolean upload) {
        if (!isSafeName(name)) {
            return upload ? "The profile name cannot be used on the server"
                    : "The file name cannot be a profile name: no < > : \" / \\ | ? * and no dot or space at its end";
        }
        if (files == null || files.keySet().stream().noneMatch(f -> TABLE_NAME.matcher(f).matches())) {
            return "The profile has no tables";
        }
        long total = 0;
        for (var entry : files.entrySet()) {
            String fileName = entry.getKey();
            if (!TABLE_NAME.matcher(fileName).matches() && !COMMON_CONFIG_NAME.equals(fileName)
                    && !OVERRIDE_NAME.matcher(fileName).matches()) {
                return "The profile holds a file " + (upload ? "the server" : "Strata") + " does not accept: "
                        + fileName;
            }
            int length = entry.getValue() == null ? 0 : entry.getValue().length();
            if (upload && length > MAX_FILE_CHARS) {
                return "The table " + fileName + " is too large to send (over " + MAX_FILE_CHARS + " characters)";
            }
            total += length;
        }
        return total > MAX_UPLOAD_CHARS ? "The profile is too large to " + (upload ? "send" : "import") : null;
    }

    /** Stores an uploaded profile in config/strata/profiles/{name}, replacing what that folder held. */
    public static Result storeUpload(String name, java.util.Map<String, String> files) {
        return storeUpload(Paths.PROFILES, name, files);
    }

    static Result storeUpload(Path profilesDir, String name, java.util.Map<String, String> files) {
        if (uploadProblem(name, files) != null) {
            return Result.INVALID_NAME;
        }
        Path target = profilesDir.resolve(name);
        try {
            Files.createDirectories(target);
            // Replace, not merge: a table the new profile does not have must not survive from the old one.
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
                for (Path old : stream) {
                    if (Files.isRegularFile(old)) {
                        Files.delete(old);
                    }
                }
            }
            Path overrides = target.resolve(OVERRIDES);
            if (Files.isDirectory(overrides)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(overrides, "*.json")) {
                    for (Path old : stream) {
                        Files.delete(old);
                    }
                }
            }
            for (var entry : files.entrySet()) {
                // uploadProblem allowed only a bare table name or overrides/{name}.json, so no other path is
                // made.
                Path file = target.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, entry.getValue());
            }
            Strata.LOGGER.info("Stored uploaded profile '{}' ({} file(s))", name, files.size());
            return Result.OK;
        } catch (IOException e) {
            Strata.LOGGER.error("Could not store uploaded profile '{}': {}", name, e.toString());
            return Result.IO_ERROR;
        }
    }

    /** Deletes the picked profiles, stopping at the first that fails. */
    public static Result deleteProfiles(List<String> names) {
        for (String name : names) {
            Result result = deleteProfile(name);
            if (result != Result.OK) {
                return result;
            }
        }
        return Result.OK;
    }

    public static Result deleteProfile(String name) {
        if (DEFAULT.equalsIgnoreCase(name == null ? "" : name.strip())) {
            return Result.PROTECTED;
        }
        if (!isSafeName(name)) {
            Strata.LOGGER.warn("Refusing to delete profile with invalid name '{}'", name);
            return Result.INVALID_NAME;
        }
        Path target = Paths.profile(name);
        if (!Files.isDirectory(target)) {
            return Result.NOT_FOUND;
        }
        // The whole tree: a profile holds an overrides folder.
        try (java.util.stream.Stream<Path> walk = Files.walk(target)) {
            for (Path file : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
            Strata.LOGGER.info("Deleted profile '{}'", name);
            return Result.OK;
        } catch (IOException e) {
            Strata.LOGGER.error("Could not delete profile '{}': {}", name, e.toString());
            return Result.IO_ERROR;
        }
    }

    /** Copies the CSV tables (only) between two directories. */
    /** Makes {@code to}'s tables {@code from}'s. */
    static void copyTables(Path from, Path to) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(from, "*.csv")) {
            stream.forEach(sources::add);
        }
        List<Path> staged = new ArrayList<>();
        try {
            for (Path file : sources) {
                Path incoming = to.resolve(file.getFileName() + ".incoming");
                Files.copy(file, incoming, StandardCopyOption.REPLACE_EXISTING);
                staged.add(incoming);
            }
        } catch (IOException e) {
            for (Path incoming : staged) {
                Files.deleteIfExists(incoming);
            }
            throw e;
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Path incoming : staged) {
            String name = incoming.getFileName().toString();
            name = name.substring(0, name.length() - ".incoming".length());
            names.add(name);
            Path target = to.resolve(name);
            try {
                Files.move(incoming, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(incoming, target, StandardCopyOption.REPLACE_EXISTING);
            }
            CsvTable.forget(target);
        }
        for (String table : Defaults.templates().keySet()) {
            if (!names.contains(table)) {
                Path stale = to.resolve(table);
                if (Files.deleteIfExists(stale)) {
                    CsvTable.forget(stale);
                    Strata.LOGGER.info("Profile: {} is not in {}, so it was removed", table, from.getFileName());
                }
            }
        }
        copyOverrides(from.resolve(OVERRIDES), to.resolve(OVERRIDES));
    }

    /** Makes {@code to}'s override sheets {@code from}'s. */
    static void copyOverrides(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        Files.createDirectories(to);
        java.util.Set<String> names = new java.util.HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(from, "*.json")) {
            for (Path sheet : stream) {
                names.add(sheet.getFileName().toString());
                Files.copy(sheet, to.resolve(sheet.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(to, "*.json")) {
            for (Path sheet : stream) {
                if (!names.contains(sheet.getFileName().toString())) {
                    Files.delete(sheet);
                }
            }
        }
    }

    /** Copies the common config file (config/strata-common.toml) between two directories. */
    /** True when the folder holds at least one of Strata's tables. */
    static boolean hasTables(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path p : stream) {
                if (TABLE_NAME.matcher(p.getFileName().toString()).matches()) {
                    return true;
                }
            }
        } catch (IOException e) {
            Strata.LOGGER.warn("Could not read profile folder {}: {}", dir, e.toString());
        }
        return false;
    }

    private static void copyConfig(Path from, Path to) throws IOException {
        Path common = from.resolve(COMMON_CONFIG_NAME);
        if (Files.isRegularFile(common)) {
            Files.copy(common, to.resolve(COMMON_CONFIG_NAME),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
