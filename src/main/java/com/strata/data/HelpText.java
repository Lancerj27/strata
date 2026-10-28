package com.strata.data;

import com.strata.Strata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * THE HELP TEXT, AS FILES (help externalization plan, steps 1-3): one.txt per section, so a pack author can
 * fix a word without a new jar.
 */
public final class HelpText {

    private HelpText() {
    }

    /**
     * One section: its file, its heading, its lines (the heading line first), and the screens whose "?" opens
     * it (its "@screen" lines).
     */
    public record Section(String file, String heading, List<String> text, List<String> screens) {
    }

    /** A line naming a screen whose "?" opens the section: "@screen MobListScreen". */
    public static final String SCREEN = "@screen ";

    /** The jar's folder of shipped sections, and the list of them in it. */
    public static final String JAR_DIR = "/strata-help-defaults/";
    private static final String INDEX = "index.txt";
    /** The folder under config/strata/help/ that holds copies to start from. */
    public static final String DEFAULTS = "defaults";

    public static final Section UNAVAILABLE = new Section("", "Help unavailable", List.of(
            "# Help unavailable",
            "The help files are missing or could not be read: the log names each one."), List.of());

    /** Null until asked: then config/strata/help/, or none off the game (the headless gate). */
    private static Path dir;
    private static boolean dirKnown;
    private static List<Section> cached;
    private static long cachedStamp;
    private static long checkedAt;

    /** Every section, in file-name order. */
    public static synchronized List<Section> sections() {
        long now = System.currentTimeMillis();
        if (cached == null || now - checkedAt > 1000) {
            checkedAt = now;
            long stamp = stamp(dir());
            if (cached == null || stamp != cachedStamp) {
                cached = load(dir());
                cachedStamp = stamp;
            }
        }
        return cached;
    }

    /** One section by its heading, or null. */
    public static Section section(String heading) {
        for (Section s : sections()) {
            if (s.heading().equals(heading)) {
                return s;
            }
        }
        return null;
    }

    /**
     * The section heading each screen's "?" opens, from the "@screen" lines, in file order: a screen named
     * twice opens the first.
     */
    public static Map<String, String> screens() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Section s : sections()) {
            for (String screen : s.screens()) {
                if (out.putIfAbsent(screen, s.heading()) != null) {
                    Strata.LOGGER.warn("Help: {} is named by two sections; its ? opens '{}'", screen, out.get(screen));
                }
            }
        }
        return out;
    }

    /**
     * The section a screen's "?" opens, or null for the Help list. "Class:option" falls back to the class's own
     * section when no file names the option.
     */
    public static String sectionFor(String screen) {
        Map<String, String> all = screens();
        String s = all.get(screen);
        int colon = screen == null ? -1 : screen.indexOf(':');
        return s != null || colon < 0 ? s : all.get(screen.substring(0, colon));
    }

    /** Reads every file again at the next ask. */
    public static synchronized void reload() {
        cached = null;
    }

    /** Reads help from this folder instead of config/strata/help/ (the gate); null for the jar alone. */
    public static synchronized void useDir(Path folder) {
        dir = folder;
        dirKnown = true;
        cached = null;
    }

    private static Path dir() {
        if (!dirKnown) {
            dirKnown = true;
            try {
                dir = Paths.ROOT.resolve("help");
            } catch (Throwable e) {
                // Off the game (the headless gate) there is no config folder: the jar's copies serve.
                dir = null;
            }
        }
        return dir;
    }

    /** The sections from the folder over the jar's. */
    static List<Section> load(Path folder) {
        Map<String, Section> byFile = new TreeMap<>();
        for (String name : jarIndex()) {
            Section s = parse(name, jarText(name));
            if (s == null) {
                Strata.LOGGER.error("Help: the shipped {} does not read", name);
            } else {
                byFile.put(name, s);
            }
        }
        if (folder != null && Files.isDirectory(folder)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.txt")) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    Section s = parse(name, read(file));
                    // An edited copy with no "@screen" lines keeps the shipped file's screens, so editing the
                    // words never unhooks a "?".
                    Section shipped = byFile.get(name);
                    if (s != null && s.screens().isEmpty() && shipped != null) {
                        s = new Section(s.file(), s.heading(), s.text(), shipped.screens());
                    }
                    if (s == null) {
                        Strata.LOGGER.warn("Help: {} has no \"# \" heading on its first line, or cannot be read;"
                                + " the shipped text is shown instead", file);
                    } else {
                        byFile.put(name, s);
                    }
                }
            } catch (IOException e) {
                Strata.LOGGER.warn("Help: could not list {}: {}", folder, e.toString());
            }
        }
        return byFile.isEmpty() ? List.of(UNAVAILABLE) : List.copyOf(byFile.values());
    }

    /** One file's text as a section, or null when it has no heading. */
    public static Section parse(String file, String content) {
        if (content == null) {
            return null;
        }
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        List<String> lines = new ArrayList<>(List.of(text.split("\r\n|\r|\n", -1)));
        while (!lines.isEmpty() && lines.getFirst().isBlank()) {
            lines.removeFirst();
        }
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        if (lines.isEmpty() || !lines.getFirst().startsWith("# ") || lines.getFirst().substring(2).isBlank()) {
            return null;
        }
        // A blank line is a gap, whatever spaces it held.
        List<String> screens = new ArrayList<>();
        List<String> shown = new ArrayList<>();
        for (String l : lines) {
            if (l.startsWith(SCREEN)) {
                String screen = l.substring(SCREEN.length()).strip();
                if (!screen.isEmpty()) {
                    screens.add(screen);
                }
            } else {
                shown.add(l.isBlank() ? "" : l);
            }
        }
        return new Section(file, lines.getFirst().substring(2).strip(), List.copyOf(shown), List.copyOf(screens));
    }

    /** Writes the shipped sections to config/strata/help/defaults/ (at start), to copy from. */
    public static void writeDefaults() {
        Path folder = dir();
        if (folder == null) {
            return;
        }
        try {
            Path out = folder.resolve(DEFAULTS);
            Files.createDirectories(out);
            for (String name : jarIndex()) {
                String text = jarText(name);
                if (text != null) {
                    Files.writeString(out.resolve(name), text, StandardCharsets.UTF_8);
                }
            }
            Files.writeString(out.resolve("README.txt"), """
                    The help as Strata ships it, written again at every start. Not read as help.
                    To change a section, copy its file up into config/strata/help/ and edit the copy.
                    The first line is the heading ("# " and its words); every other line is one line of help.
                    A new file there is a new section. Sections show in file-name order.
                    An "@screen ScreenName" line hooks that screen's ? button to the section; it is not shown.
                    A copy with no @screen lines keeps the shipped file's. Press Reload help on the Help list after an edit.
                    """, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Strata.LOGGER.warn("Help: could not write the shipped copies to {}: {}", folder, e.toString());
        }
    }

    private static List<String> jarIndex() {
        String index = jarText(INDEX);
        if (index == null) {
            Strata.LOGGER.error("Help: the jar has no {}{}", JAR_DIR, INDEX);
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : index.split("\r\n|\r|\n")) {
            if (!line.isBlank()) {
                out.add(line.strip());
            }
        }
        return out;
    }

    private static String jarText(String name) {
        try (InputStream in = HelpText.class.getResourceAsStream(JAR_DIR + name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Not UTF-8, or unreadable: logged by load, which shows the shipped text.
            return null;
        }
    }

    /** Changes when a file in the folder is added, removed, or saved. */
    private static long stamp(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return 0;
        }
        // A sum, so the order the folder lists its files in does not change it.
        long stamp = 1;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.txt")) {
            for (Path file : files) {
                stamp += 31L * file.getFileName().toString().hashCode() + Files.getLastModifiedTime(file).toMillis();
            }
        } catch (IOException e) {
            return -1;
        }
        return stamp;
    }
}
