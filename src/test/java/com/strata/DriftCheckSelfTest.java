package com.strata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** HEADLESS DRIFT CHECK — standing source-code sweeps. */
public final class DriftCheckSelfTest {

    private DriftCheckSelfTest() {}

    /** Canonical screen inventory — ONE source of truth for the screen tree. */
    private static final List<String> CANONICAL_SCREENS = List.of(
            "AbilitiesScreen", "AssetMasterScreen", "BiomeDataScreen", "BiomeEditScreen", "BiomeListScreen",
            "BlockStatScreen", "BlockStatsScreen", "BlockVariantScreen",
            "ChestEntryScreen", "ChestLootScreen", "ChestPoolScreen", "ChestsScreen",
            "ClientScreen",
            "CustomAssetScreen", "CustomAssetsScreen", "CustomStructureScreen", "CustomStructuresScreen",
            "EquipmentItemScreen", "EquipmentScreen",
            "GameRulesScreen",
            "GoalScreen", "GoalSetsScreen", "GoalsScreen",
            "HelpScreen",
            "ItemsScreen", "ItemStatScreen", "ItemVariantScreen", "ItemVariantStatsScreen",
            "LootEntryScreen", "LootScreen", "LootTableScreen",
            "MobBatchScreen", "MobBiomesScreen", "MobCapScreen", "MobDataScreen", "MobEditScreen", "MobListScreen",
            "OreListScreen",
            "PlayerBaseStatsScreen", "PlayerSpawnScreen", "ProfileScreen",
            "ProjectileEditScreen", "ProjectileListScreen",
            "RegistryPickerScreen",
            "ScalingEditScreen", "ScalingScreen", "FormulasScreen",
            "SpawnRulesScreen",
            "SpecialMobsScreen", "SpecialSpawnsScreen",
            "StrataOptionsScreen", "StrataScreen",
            "TestModeScreen",
            "TradeEntryScreen", "TradesScreen", "ReplacementsScreen", "OverridesScreen", "SectionNav",
            "TierEditScreen", "TierListScreen",
            "VegetationScreen", "WorldSettingsScreen",
            "GroupBar",
            "GlanceHeaders",
            "BlockSwapScreen",
            "RulesScreen"
    );

    // A private METHOD declaration, not a field: it needs a return type AND a name before the paren, so
    // "private Foo" (a constructor) and "private static final Pattern X = Pattern.compile(" (the '=' stops
    // the type run) are both correctly skipped.
    private static final Pattern PRIVATE_METHOD = Pattern.compile(
            "private\\s+(?:static\\s+|final\\s+|synchronized\\s+)*"
                    + "[\\w<>\\[\\],.?\\s]+?\\s+(\\w+)\\s*\\(");

    // A single-type import; a wildcard import has no name to test and falls through to the body.
    private static final Pattern IMPORT_LINE =
            Pattern.compile("import\\s+(?:static\\s+)?[\\w.]*?(\\w+)\\s*;");

    // A private FIELD declaration, not a method: the name is followed by ';' or '=', never '(' — which is
    // what separates it from PRIVATE_METHOD.
    private static final Pattern PRIVATE_FIELD = Pattern.compile(
            "private\\s+(?:static\\s+|final\\s+|volatile\\s+|transient\\s+)*"
                    + "[\\w<>\\[\\],.?\\s]+?\\s+(\\w+)\\s*([;=])");

    // A labeled statement: an identifier, a colon, then a block.
    private static final Pattern LABELED_BLOCK = Pattern.compile(
            "(?m)^[ \\t]*([A-Za-z_]\\w*)[ \\t]*:[ \\t]*\\r?\\n?[ \\t]*\\{");

    // A FREE-STANDING empty block — one that is a statement rather than the body of something.
    private static final Pattern EMPTY_BLOCK_STATEMENT = Pattern.compile(
            "[;}{]\\s*\\{\\s*\\}");

    /** @param root project root (src/main/java lives under it) */
    public static SelfTestHarness.Result run(Path root, int resolverTotal, int writerTotal, int dataTotal) {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];
        checks(lines, tally, root, resolverTotal, writerTotal, dataTotal);
        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }

    private static void checks(List<String> lines, int[] tally, Path root, int resolverTotal, int writerTotal, int dataTotal) {
        checksPart01(lines, tally, root, resolverTotal, writerTotal, dataTotal);
        checksPart02(lines, tally, root, resolverTotal, writerTotal, dataTotal);
        checksPart03(lines, tally, root, resolverTotal, writerTotal, dataTotal);
    }

    private static void checksPart01(List<String> lines, int[] tally, Path root, int resolverTotal, int writerTotal, int dataTotal) {

        SelfTestHarness.check(lines, tally, "drift.1 root resolves", () ->
                Files.isDirectory(root.resolve("src/main/java")));

        SelfTestHarness.check(lines, tally, "drift.7 screen inventory is a closed set", () -> {
            List<String> live = new ArrayList<>();
            live.addAll(screens(root.resolve("src/main/java")));
            live.addAll(screens(root.resolve("src/client/java")));
            for (String s : CANONICAL_SCREENS) {
                if (!live.contains(s)) {
                    lines.add("    drift.7 canonical-missing-in-tree: " + s);
                }
            }
            for (String s : live) {
                if (!CANONICAL_SCREENS.contains(s)) {
                    lines.add("    drift.7 tree-extra-not-canonical: " + s);
                }
            }
            return new HashSet<>(live).equals(new HashSet<>(CANONICAL_SCREENS));
        });


        SelfTestHarness.check(lines, tally,
                "drift.10 no unguarded defaulted-registry key dereference", () -> {
            List<String> hits = grepJava(root,
                    Pattern.compile("getKey\\([^;]*\\)\\s*\\.\\s*(toString|getPath|getNamespace)"));
            for (String hit : hits) {
                lines.add("    drift.10 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.11 defaulted registries are probed with containsKey", () -> {
            List<String> hits = grepJava(root,
                    Pattern.compile("BuiltInRegistries\\.[A-Z_]+\\.get\\([^;]*\\)\\s*(!=|==)\\s*null"));
            hits.addAll(grepJava(root,
                    Pattern.compile("==\\s*EntityType\\.PIG|EntityType\\.PIG\\s*==")));
            for (String hit : hits) {
                lines.add("    drift.11 " + hit);
            }
            return hits.isEmpty();
        });


        SelfTestHarness.check(lines, tally,
                "drift.12 no class is referenced nowhere", () -> {
            List<Path> sources = allSources(root);
            String elsewhere = readAll(root);
            List<String> hits = new ArrayList<>();
            for (Path file : sources) {
                if (!file.startsWith(root.resolve("src/main/java"))) {
                    continue;
                }
                String name = fileName(file);
                String own = read(file);
                String rest = elsewhere.replace(own, "");
                if (!Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(rest).find()) {
                    hits.add(name + " is referenced by nothing outside itself");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.12 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.13 no uncalled private method", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String text = read(file);
                Matcher m = PRIVATE_METHOD.matcher(text);
                while (m.find()) {
                    String name = m.group(1);
                    int uses = countOccurrences(text,
                            Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\("))
                            + countOccurrences(text,
                            Pattern.compile("::\\s*" + Pattern.quote(name) + "\\b"));
                    if (uses < 2) {
                        hits.add(fileName(file) + "#" + name + " is never called");
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.13 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.14 no unused import", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String text = read(file);
                StringBuilder body = new StringBuilder();
                List<String> imported = new ArrayList<>();
                for (String line : text.split("\n")) {
                    Matcher m = IMPORT_LINE.matcher(line.strip());
                    if (m.matches()) {
                        imported.add(m.group(1));
                    } else {
                        body.append(line).append('\n');
                    }
                }
                for (String name : imported) {
                    if (!Pattern.compile("(?<![.\\w])" + Pattern.quote(name) + "\\b").matcher(body).find()) {
                        hits.add(fileName(file) + " imports " + name + ", never uses it");
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.14 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.15 no private field that is never read", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String text = stripComments(read(file));
                Matcher m = PRIVATE_FIELD.matcher(text);
                while (m.find()) {
                    String name = m.group(1);
                    boolean hasInitializer = "=".equals(m.group(2));
                    String unshadowed = maskShadowedBodies(text, name);
                    int shadowedReads = countOccurrences(text,
                            Pattern.compile("this\\." + Pattern.quote(name) + "\\b(?!\\s*=(?!=))"))
                            - countOccurrences(unshadowed,
                            Pattern.compile("this\\." + Pattern.quote(name) + "\\b(?!\\s*=(?!=))"));
                    int total = countOccurrences(unshadowed,
                            Pattern.compile("\\b" + Pattern.quote(name) + "\\b")) + shadowedReads;
                    int writes = countOccurrences(unshadowed,
                            Pattern.compile("(?:this\\.)?\\b" + Pattern.quote(name)
                                    + "\\b\\s*=(?!=)"));
                    int reads = total - writes - (hasInitializer ? 0 : 1);
                    if (reads <= 0) {
                        hits.add(fileName(file) + "#" + name + " is assigned and never read");
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.15 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.16 no labeled or free-standing empty block", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String text = read(file);
                Matcher labeled = LABELED_BLOCK.matcher(text);
                while (labeled.find()) {
                    hits.add(fileName(file) + " has a labeled block '"
                            + labeled.group(1) + "'");
                }
                if (EMPTY_BLOCK_STATEMENT.matcher(stripCommentsAndLiterals(text)).find()) {
                    hits.add(fileName(file) + " has a free-standing empty block");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.16 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.17 init() assigns a field before anything dereferences it", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String name = fileName(file);
                if (!name.endsWith("Screen")) {
                    continue;
                }
                String text = stripCommentsAndLiterals(read(file));
                String init = methodBody(text, "protected void init()");
                if (init == null) {
                    continue;
                }
                Set<String> risky = lateBoundFields(text);
                if (risky.isEmpty()) {
                    continue;
                }
                Set<String> assigned = new LinkedHashSet<>();
                Matcher ctor = Pattern.compile("(?m)^[ \\t]*(?:public|protected|private)\\s+"
                        + Pattern.quote(name) + "\\s*\\(").matcher(text);
                while (ctor.find()) {
                    String body = bracedFrom(text, ctor.start());
                    Matcher assign = CTOR_FIELD_ASSIGN.matcher(body == null ? "" : body);
                    while (assign.find()) {
                        assigned.add(assign.group(1));
                    }
                }
                for (String stmt : withoutLambdaBodies(init).split("\n")) {
                    Matcher call = DIRECT_CALL.matcher(stmt);
                    while (call.find()) {
                        String callee = call.group(1);
                        if (JAVA_KEYWORDS.contains(callee)) {
                            continue;
                        }
                        String body = declaredBody(text, callee);
                        if (body == null || callee.equals("init")) {
                            continue;
                        }
                        String stripped = withoutLambdaBodies(body);
                        for (String field : risky) {
                            if (assigned.contains(field)) {
                                continue;
                            }
                            Matcher own = Pattern.compile("(?m)^\\s*(?:this\\.)?" + Pattern.quote(field)
                                    + "\\s*=(?!=)").matcher(stripped);
                            int use = stripped.indexOf(field + ".");
                            if (own.find() && (use < 0 || own.start() < use)) {
                                assigned.add(field);
                                continue;
                            }
                            if (body.contains(field + ".")) {
                                hits.add(name + ": init() calls " + callee
                                        + "() which dereferences '" + field
                                        + "' before init() assigns it");
                            }
                        }
                    }
                    Matcher assign = FIELD_ASSIGN.matcher(stmt);
                    while (assign.find()) {
                        assigned.add(assign.group(1));
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.17 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.18 a screen that writes a mob's data names that mob", () -> {
            List<String> mobWriteOps = List.of(
                    "LootOps.", "SpawnRulesOps.", "EquipmentOps.",
                    "MobStatsOps.", "MobDataOps.", "LootTableOps.", "MobOps.", "VariantOps.save");
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String name = fileName(file);
                if (!name.endsWith("Screen")) {
                    continue;
                }
                String text = stripCommentsAndLiterals(read(file));
                String writer = null;
                for (String op : mobWriteOps) {
                    if (java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(op))
                            .matcher(text).find()) {
                        writer = op;
                        break;
                    }
                }
                if (writer == null) {
                    continue;
                }
                if (!text.contains("editingMob(") && !text.contains("RecentEdits.touch(")) {
                    hits.add(name + " writes mob data through " + writer
                            + " without declaring its mob - its saves will not"
                            + " reach the Recent group");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.18 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.19 the client is a front end: no table access, no reload", () -> {
            List<String> forbidden = List.of("CsvTable.", "TableWriter.", "Schema.", "Config.reload(");
            List<String> hits = new ArrayList<>();
            Path client = root.resolve("src/main/java/com/strata/client");
            for (Path file : allSources(root)) {
                if (!file.startsWith(client)) {
                    continue;
                }
                String text = stripCommentsAndLiterals(read(file));
                for (String token : forbidden) {
                    if (text.contains(token)) {
                        hits.add(fileName(file) + " uses " + token);
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.19 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.20 Back and ESC share one path, and an edit form guards unsaved edits", () -> {
            List<String> hits = new ArrayList<>();
            Path screens = root.resolve("src/main/java/com/strata/client/screen");
            for (Path file : allSources(root)) {
                if (!file.startsWith(screens)) {
                    continue;
                }
                String name = fileName(file);
                String text = stripCommentsAndLiterals(read(file));
                if (name.equals("StrataScreen")) {
                    continue;
                }
                if (text.contains("void onClose(")) {
                    hits.add(name + " overrides onClose - ESC must go through StrataScreen#leave");
                }
                if (text.contains("void save(") && !text.contains("boolean hasUnsavedEdits(")) {
                    hits.add(name + " has a save() but no hasUnsavedEdits() - Back and ESC drop its edits");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.20 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.21 a button that acts on the selected row greys out without one", () -> {
            List<String> hits = new ArrayList<>();
            Path screens = root.resolve("src/main/java/com/strata/client/screen");
            for (Path file : allSources(root)) {
                if (!file.startsWith(screens)) {
                    continue;
                }
                String text = stripCommentsAndLiterals(read(file));
                if (text.contains("selectedRow()") && text.contains("Button.builder(")
                        && !text.contains("needsSelection(")) {
                    hits.add(fileName(file) + " has buttons that act on selectedRow() but no needsSelection()");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.21 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.22 one Back button per screen, from StrataScreen", () -> {
            List<String> hits = new ArrayList<>();
            Path screens = root.resolve("src/main/java/com/strata/client/screen");
            for (Path file : allSources(root)) {
                if (!file.startsWith(screens) || fileName(file).equals("StrataScreen")) {
                    continue;
                }
                if (read(file).contains("Component.literal(\"← Back")) {
                    hits.add(fileName(file) + " builds its own Back button");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.22 " + hit);
            }
            return hits.isEmpty();
        });

    }

    private static void checksPart02(List<String> lines, int[] tally, Path root, int resolverTotal, int writerTotal, int dataTotal) {
        SelfTestHarness.check(lines, tally,
                "drift.24 network code names no client class, so a dedicated server can load it", () -> {
            List<String> hits = new ArrayList<>();
            Path network = root.resolve("src/main/java/com/strata/network");
            for (Path file : allSources(root)) {
                if (file.startsWith(network)
                        && stripCommentsAndLiterals(read(file)).contains("com.strata.client")) {
                    hits.add(fileName(file) + " names a client class");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.24 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.25 the UI layer names no Minecraft or client class", () -> {
            List<String> hits = new ArrayList<>();
            Path ui = root.resolve("src/main/java/com/strata/ui");
            for (Path file : allSources(root)) {
                if (!file.startsWith(ui)) {
                    continue;
                }
                String text = stripCommentsAndLiterals(read(file));
                for (String banned : new String[]{"net.minecraft", "com.mojang", "net.neoforged", "com.strata.client"}) {
                    if (text.contains(banned)) {
                        hits.add(fileName(file) + " names " + banned);
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.25 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.26 every character Strata's screens can show is in the game's font", () -> {
            com.strata.ui.GameFont font = LayoutCheckSelfTest.gameFont(lines, "drift.26");
            if (font == null) {
                return false;
            }
            java.util.Map<Integer, String> hits = new java.util.TreeMap<>();
            Pattern literal = Pattern.compile("\"((?:\\\\.|[^\"\\\\\\n])*)\"");
            for (String rel : List.of("client", "domain", "ui")) {
                Path dir = root.resolve("src/main/java/com/strata/" + rel);
                for (Path file : allSources(root)) {
                    if (!file.startsWith(dir)) {
                        continue;
                    }
                    Matcher m = literal.matcher(stripComments(read(file)));
                    while (m.find()) {
                        for (int cp : font.unknown(decodeLiteral(m.group(1)))) {
                            if (cp < 0x20) {
                                continue;
                            }
                            hits.putIfAbsent(cp, fileName(file));
                        }
                    }
                }
            }
            hits.forEach((cp, file) -> lines.add("    drift.26 U+" + String.format("%04X", cp)
                    + " '" + new String(Character.toChars(cp)) + "' in " + file));
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "drift.29 one action has one name: no retired button word in the UI", () -> {
            java.util.regex.Pattern retired = java.util.regex.Pattern.compile(
                    "\"(Toggle|Restore default|Restore Defaults|Restore game values|Set Value|Start from game|Game table|Game roll|Use archetype|\\+ Add [A-Z][^\"]*)\"");
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!path.contains("/src/main/java/com/strata/client/") && !path.contains("/src/main/java/com/strata/ui/")) {
                    continue;
                }
                String text = read(file);
                if (text == null) {
                    continue;
                }
                java.util.regex.Matcher m = retired.matcher(text);
                while (m.find()) {
                    hits.add(fileName(file) + " " + m.group());
                }
            }
            for (String hit : hits) {
                lines.add("    drift.29 " + hit);
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.32 titles in sentence case, pickers say Pick, a switch says On or Off", () -> {
            java.util.regex.Pattern title = java.util.regex.Pattern.compile(
                    "\"Strata — ([^\"]*)\"|new RegistryPickerScreen\\(\\s*\"([^\"]*)\"");
            java.util.regex.Pattern capital = java.util.regex.Pattern.compile("\\s([A-Z][a-z]+)");
            java.util.regex.Pattern shout = java.util.regex.Pattern.compile("\"(ON|OFF)\"");
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!path.contains("/src/main/java/com/strata/client/") && !path.contains("/src/main/java/com/strata/ui/")) {
                    continue;
                }
                String text = read(file);
                if (text == null) {
                    continue;
                }
                java.util.regex.Matcher m = title.matcher(text);
                while (m.find()) {
                    String t = m.group(1) != null ? m.group(1) : m.group(2);
                    if (t.startsWith("Select ") || capital.matcher(t).find()) {
                        hits.add(fileName(file) + " \"" + t + "\"");
                    }
                }
                java.util.regex.Matcher s = shout.matcher(text);
                while (s.find()) {
                    hits.add(fileName(file) + " " + s.group());
                }
            }
            for (String hit : hits) {
                lines.add("    drift.32 " + hit);
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.33 one name per thing, Minecraft's: no retired word in the UI", () -> {
            java.util.regex.Pattern word = java.util.regex.Pattern.compile("\"[^\"\\n]*(\\b[Aa]rchetypes?\\b"
                    + "|\\b[Bb]ehaviou?rs?\\b|\\b[Pp]resets?\\b|\\b[Gg]ear\\b|\\bDrops\\b|\\bdrop row|\\bWHO\\b|\\bHOW\\b"
                    + "|\\b[Ss]pawn conditions?\\b|\\b[Ff]lora\\b"
                    + "|\\b[Ss]pawn [Ss]pecial [Mm]obs\\b)"
                    + "[^\"\\n]*\"");
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                boolean ui = path.contains("/src/main/java/com/strata/client/screen/")
                        || path.contains("/src/main/java/com/strata/ui/")
                        || path.contains("/src/main/java/com/strata/domain/");
                if (!ui) {
                    continue;
                }
                String[] src = read(file).split("\\R");
                for (int i = 0; i < src.length; i++) {
                    String line = src[i].strip();
                    if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                        continue;
                    }
                    java.util.regex.Matcher m = word.matcher(src[i]);
                    if (m.find()) {
                        hits.add(fileName(file) + ":" + (i + 1) + " " + m.group());
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.33 " + hit);
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.34 Save: a big editor stays or reopens; a small one-row form goes back", () -> {
            java.util.Set<String> small = java.util.Set.of("LootEntryScreen", "EquipmentItemScreen",
                    "ScalingEditScreen", "ChestEntryScreen", "ChestPoolScreen", "TradeEntryScreen");
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!(path.contains("/src/main/java/com/strata/client/screen/") || path.contains("/src/client/java/com/strata/client/screen/"))) {
                    continue;
                }
                String text = read(file).replace("\r\n", "\n");
                int at = text.indexOf("    private void save() {\n");
                if (at < 0) {
                    continue;
                }
                int end = text.indexOf("\n    }\n", at);
                String body = text.substring(at, end < 0 ? text.length() : end);
                boolean back = body.contains("backSaved()");
                boolean stays = body.contains("savedHere()") || body.contains("reopen(") || body.contains("afterSave(");
                if (small.contains(fileName(file)) ? !back : (back || !stays)) {
                    hits.add(fileName(file));
                }
            }
            for (String hit : hits) {
                lines.add("    drift.34 " + hit + " save() does not follow its rule (small goes back, big stays)");
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.35 a screen that rebuilds its list drops the old one first, so no stale pick is read", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!(path.contains("/src/main/java/com/strata/client/screen/") || path.contains("/src/client/java/com/strata/client/screen/"))) {
                    continue;
                }
                String[] src = read(file).split("\\R");
                for (int i = 0; i < src.length; i++) {
                    if (src[i].contains("BrowseList.State keepList = list == null ? null : list.state();")) {
                        boolean dropped = false;
                        for (int j = i + 1; j < Math.min(src.length, i + 4) && !dropped; j++) {
                            dropped = src[j].strip().equals("list = null;");
                        }
                        if (!dropped) {
                            hits.add(fileName(file) + ":" + (i + 1));
                        }
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.35 " + hit + " keeps the old list while it lays out");
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.37 every screen's ? opens a Help section that exists, or the Help list", () -> {
            List<String> sections = com.strata.ui.screen.HelpLayout.sections();
            java.util.Map<String, String> byScreen = com.strata.data.HelpText.screens();
            List<String> found = new ArrayList<>();
            List<String> hits = new ArrayList<>();
            java.util.regex.Pattern screen = java.util.regex.Pattern.compile("class (\\w+) extends StrataScreen\\b");
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!(path.contains("/src/main/java/com/strata/client/screen/") || path.contains("/src/client/java/com/strata/client/screen/"))) {
                    continue;
                }
                var m = screen.matcher(read(file));
                while (m.find()) {
                    String name = m.group(1);
                    found.add(name);
                    // A class that serves several options has a page per option ("ScalingScreen:mob").
                    if (!byScreen.containsKey(name) && byScreen.keySet().stream().noneMatch(k -> k.startsWith(name + ":"))
                            && !com.strata.ui.screen.HelpLayout.TO_MASTER.contains(name)) {
                        hits.add(name + " has no Help section");
                    }
                }
            }
            byScreen.forEach((name, section) -> {
                if (!sections.contains(section)) {
                    hits.add(name + " names the missing section '" + section + "'");
                }
                // "Class:option" names an option of a class that serves several (StrataScreen#helpKey).
                if (!found.contains(name.contains(":") ? name.substring(0, name.indexOf(':')) : name)) {
                    hits.add("a help file names " + name + ", which is not a screen");
                }
            });
            for (String hit : hits) {
                lines.add("    drift.37 " + hit);
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.38 every list's Remove, and Del, act on every picked row", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!(path.contains("/src/main/java/com/strata/client/screen/") || path.contains("/src/client/java/com/strata/client/screen/"))) {
                    continue;
                }
                String src = read(file);
                boolean list = src.contains("new BrowseList");
                boolean removes = src.contains("\"remove\"");
                if (list && removes && !src.contains("pickedIds()")) {
                    hits.add(fileName(file));
                }
            }
            for (String hit : hits) {
                lines.add("    drift.38 " + hit + " removes the selected row alone");
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.43 every mob property value is read by the code that applies it", () -> {
            StringBuilder code = new StringBuilder();
            String own = "";
            for (Path file : allSources(root)) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                if (rel.startsWith("src/test/") || rel.endsWith("/MobPropertyLayers.java")) {
                    continue;
                }
                String text = read(file);
                if (text == null) {
                    continue;
                }
                if (rel.endsWith("/VariantProperties.java")) {
                    text = text.replaceAll("this\\.\\w+ = b\\.\\w+;", "")
                            .replaceAll("public void \\w+\\([^)]*\\) \\{ this\\.\\w+ = v; \\}", "")
                            .replaceAll("public final \\w+ \\w+;", "")
                            .replaceAll("(?m)^\\s*(Boolean|Float|Double|Integer|String)\\s+[\\w, ]+;\\s*$", "");
                    own = stripComments(text);
                    continue;
                }
                code.append(stripComments(text)).append('\n');
            }
            boolean ok = true;
            for (java.lang.reflect.Field f : com.strata.model.VariantProperties.Resolved.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                boolean read = Pattern.compile("\\." + f.getName() + "\\b(?!\\s*\\()").matcher(code).find()
                        || Pattern.compile("\\b" + f.getName() + "\\b(?!\\s*\\()").matcher(own).find();
                if (!read) {
                    lines.add("    drift.43 " + f.getName() + " is saved and never applied");
                    ok = false;
                }
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "drift.42 no layout.get(id).text() or .box(): textOf / boxOf", () -> {
            java.util.regex.Pattern bad = java.util.regex.Pattern.compile(
                    "\\blayout\\w*(\\([^()]*\\))?\\.get\\([^()]*\\)\\.(text|box)\\(\\)");
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!(path.contains("/com/strata/client/screen/") || path.contains("/com/strata/ui/"))) {
                    continue;
                }
                String src = read(file);
                for (String line : src.split("\n")) {
                    String t = line.strip();
                    if (!t.startsWith("*") && !t.startsWith("//") && bad.matcher(t).find()) {
                        hits.add(fileName(file) + ": " + t);
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.42 " + hit);
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.36 each row button keeps its own greying condition", () -> {
            String src = read(root.resolve("src/client/java/com/strata/client/screen/StrataScreen.java"));
            boolean perButton = src != null && src.contains("selectionButtons.put(button, new Needs(present, why));")
                    && !src.contains("selectionPresent");
            if (!perButton) {
                lines.add("    drift.36 StrataScreen#needsSelection shares one condition across its calls");
            }
            return perButton;
        });
        SelfTestHarness.check(lines, tally,
                "drift.31 every remove, delete and clear asks first, but one row of a multi-select list", () -> {
            java.util.regex.Pattern call = java.util.regex.Pattern.compile(
                    "\\b\\w+Ops\\.(remove|delete|clear)(?!Denies|TurnsOff|alPlan|ableFromScope|Parts)\\w*\\(");
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!(path.contains("/src/main/java/com/strata/client/screen/") || path.contains("/src/client/java/com/strata/client/screen/"))) {
                    continue;
                }
                String[] src = read(file).split("\\R");
                for (int i = 0; i < src.length; i++) {
                    String line = src[i].strip();
                    if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")
                            || !call.matcher(src[i]).find()) {
                        continue;
                    }
                    boolean asked = false;
                    for (int j = Math.max(0, i - 8); j <= i && !asked; j++) {
                        asked = src[j].contains("ConfirmDialog(") || src[j].contains("size() == 1");
                    }
                    if (!asked) {
                        hits.add(fileName(file) + ":" + (i + 1));
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.31 " + hit + " removes without asking");
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.30 a command that fails says so in red (sendFailure), not as a plain line", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!path.contains("/src/main/java/com/strata/command/")) {
                    continue;
                }
                String[] src = read(file).split("\\R");
                for (int i = 0; i < src.length; i++) {
                    if (!src[i].contains("sendSuccess(")) {
                        continue;
                    }
                    for (int j = i + 1; j < Math.min(src.length, i + 6); j++) {
                        if (src[j].contains("sendSuccess(") || src[j].contains("return 1")) {
                            break;
                        }
                        if (src[j].contains("return 0")) {
                            hits.add(fileName(file) + ":" + (i + 1));
                            break;
                        }
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    drift.30 " + hit + " reports a failure with sendSuccess");
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.28 the client never moves a text field's cursor with moveCursorTo", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!path.contains("/src/main/java/com/strata/client/")) {
                    continue;
                }
                String text = stripCommentsAndLiterals(read(file));
                if (text != null && text.contains("moveCursorTo")) {
                    hits.add(fileName(file));
                }
            }
            for (String hit : hits) {
                lines.add("    drift.28 " + hit + " calls moveCursorTo*, which fires the field's listener");
            }
            return hits.isEmpty();
        });
        SelfTestHarness.check(lines, tally,
                "drift.27 every screen is laid out by the UI layer, and every layout is in the fit gate", () -> {
            List<String> hits = new ArrayList<>();
            Path screens = root.resolve("src/client/java/com/strata/client/screen");
            Path layouts = root.resolve("src/main/java/com/strata/ui/screen");
            List<Path> drawn = new ArrayList<>();
            for (Path file : allSources(root)) {
                if (file.startsWith(screens) && fileName(file).endsWith("Screen")
                        && !fileName(file).equals("StrataScreen")) {
                    drawn.add(file);
                }
            }
            drawn.add(root.resolve("src/client/java/com/strata/client/widget/ConfirmDialog.java"));
            drawn.add(root.resolve("src/client/java/com/strata/client/widget/BrowseList.java"));
            for (Path file : drawn) {
                if (!stripComments(read(file)).contains("com.strata.ui.screen.")) {
                    hits.add(fileName(file) + " takes no layout from com.strata.ui.screen");
                }
            }
            String gate = stripComments(read(root.resolve("src/test/java/com/strata/FitCheckSelfTest.java")));
            StringBuilder layoutText = new StringBuilder();
            List<Path> layoutFiles = new ArrayList<>();
            for (Path file : allSources(root)) {
                if (file.startsWith(layouts)) {
                    layoutFiles.add(file);
                    layoutText.append(stripComments(read(file))).append('\n');
                }
            }
            for (Path file : layoutFiles) {
                String name = fileName(file);
                String others = layoutText.toString().replace(stripComments(read(file)), "");
                if (!gate.contains(name + ".") && !others.contains(name + ".")) {
                    hits.add(name + " is never run by the fit gate");
                }
            }
            for (String hit : hits) {
                lines.add("    drift.27 " + hit);
            }
            return hits.isEmpty();
        });

    }

    private static void checksPart03(List<String> lines, int[] tally, Path root, int resolverTotal, int writerTotal, int dataTotal) {
        SelfTestHarness.check(lines, tally,
                "drift.23 only Back takes a screen off the history", () -> {
            Path navigator = root.resolve("src/client/java/com/strata/client/nav/ScreenNavigator.java");
            String raw = read(navigator);
            if (raw == null) raw = "";
            String text = stripCommentsAndLiterals(raw);
            String back = methodBody(text, "public void back()");
            int pops = text.split("\\.pop\\(", -1).length - 1;
            boolean ok = pops == 1 && back != null && back.contains(".pop(");
            if (!ok) {
                lines.add("    drift.23 ScreenNavigator has " + pops
                        + " pop call(s); the only one must be in back()");
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally, "drift.44 every list screen has a Recent group", () -> {
            List<String> hits = new ArrayList<>();
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!path.contains("/src/client/java/com/strata/client/screen/")) {
                    continue;
                }
                String text = read(file);
                if (text.contains("list = new BrowseList(") && !text.contains(".setRecent(\"")
                        && !text.contains("\"Recent (\"") && !text.contains("RecentEdits.recent") && !text.contains("RecentEdits.rank(")) {
                    hits.add(fileName(file));
                }
            }
            for (String hit : hits) {
                lines.add("    drift.44 " + hit + " has a list with no Recent group");
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally, "drift.46 every UI color is a named Palette color", () -> {
            List<String> hits = new ArrayList<>();
            Pattern color = Pattern.compile("0x[0-9A-Fa-f]{6,8}");
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!path.contains("/src/client/java/com/strata/client/")) {
                    continue;
                }
                if (color.matcher(stripCommentsAndLiterals(read(file))).find()) {
                    hits.add(fileName(file));
                }
            }
            hits.forEach(h -> lines.add("    drift.46 " + h + " has a raw color: use Palette"));
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally, "drift.45 one verb for one action: Remove, never Delete; a count reads Remove (n)", () -> {
            List<String> hits = new ArrayList<>();
            java.util.regex.Pattern bad = java.util.regex.Pattern.compile(
                    "literal\\(\"Delete\"\\)|Action\\.of\\(\"\\w+\", \"Delete\"\\)|\"Remove \" \\+ picked :|\\), \"Delete\"\\)");
            for (Path file : allSources(root)) {
                String path = file.toString().replace('\\', '/');
                if (!path.contains("/src/client/java/") &&!path.contains("/src/main/java/com/strata/ui/")) {
                    continue;
                }
                if (bad.matcher(read(file)).find()) {
                    hits.add(fileName(file));
                }
            }
            for (String hit : hits) {
                lines.add("    drift.45 " + hit + " says Delete, or a count without parentheses");
            }
            return hits.isEmpty();
        });

    }

    // ---- helpers (plain java.io, headless-safe) ----

    /** A statement-level call: `name(`, not `something.name(`. */
    private static final Pattern DIRECT_CALL =
            Pattern.compile("(?<![\\w.])(\\w+)\\s*\\(");

    /** `field =...`, not `==`, and not a qualified `x.field =`. */
    private static final Pattern FIELD_ASSIGN =
            Pattern.compile("(?<![\\w.])(\\w+)\\s*=(?!=)");

    /** A constructor's `field =...` or `this.field =...`. */
    private static final Pattern CTOR_FIELD_ASSIGN =
            Pattern.compile("(?<![\\w.])(?:this\\.)?(\\w+)\\s*=(?!=)");

    /**
     * A non-final reference field with NO initializer - null until init assigns it, which is what makes an
     * ordering mistake a crash rather than a wrong value.
     */
    private static final Pattern LATE_BOUND_FIELD = Pattern.compile(
            "(?m)^ {4}private\\s+(?!static|final)([\\w<>\\[\\], .]+?)\\s+(\\w+)\\s*;");

    /** Keywords that take a parenthesis, so DIRECT_CALL matches them. */
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "return", "new", "super", "this");

    private static final Set<String> PRIMITIVES = Set.of(
            "int", "long", "short", "byte", "char", "float", "double", "boolean");

    private static Set<String> lateBoundFields(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = LATE_BOUND_FIELD.matcher(text);
        while (m.find()) {
            if (!PRIMITIVES.contains(m.group(1).strip())) {
                out.add(m.group(2));
            }
        }
        return out;
    }

    /**
     * The body of the first method whose signature contains {@code marker}, brace-matched, or null when there
     * is none.
     */
    private static String methodBody(String text, String marker) {
        int at = text.indexOf(marker);
        if (at < 0) {
            return null;
        }
        return bracedFrom(text, at);
    }

    /**
     * The body of the method this file DECLARES with that name, or null when it declares none (a method of a
     * superclass, or a constructor call).
     */
    private static String declaredBody(String text, String name) {
        Matcher m = Pattern.compile("(?m)^[ \\t]*(?:(?:public|protected|private|static|final"
                + "|synchronized)\\s+)*[\\w<>\\[\\], .?]+\\s+" + Pattern.quote(name)
                + "\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w., ]+)?\\{").matcher(text);
        return m.find() ? bracedFrom(text, m.start()) : null;
    }

    /** The brace-matched block that opens at or after {@code at}, or null. */
    private static String bracedFrom(String text, int at) {
        int open = text.indexOf('{', at);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open + 1, i);
                }
            }
        }
        return null;
    }

    /** The same code with every lambda body removed. */
    private static String withoutLambdaBodies(String code) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < code.length()) {
            int arrow = code.indexOf("->", i);
            if (arrow < 0) {
                out.append(code, i, code.length());
                break;
            }
            out.append(code, i, arrow);
            int j = arrow + 2;
            while (j < code.length() && Character.isWhitespace(code.charAt(j))
                    && code.charAt(j) != '\n') {
                j++;
            }
            if (j < code.length() && code.charAt(j) == '{') {
                int depth = 0;
                while (j < code.length()) {
                    char c = code.charAt(j);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            j++;
                            break;
                        }
                    } else if (c == '\n') {
                        out.append('\n');
                    }
                    j++;
                }
            } else {
                while (j < code.length() && code.charAt(j) != '\n') {
                    j++;
                }
            }
            i = j;
        }
        return out.toString();
    }

    /** Source with its comments removed, so a scanner is not fooled by code QUOTED in a javadoc. */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    /** Comment-stripped source with its string literals emptied as well. */
    private static String stripCommentsAndLiterals(String text) {
        return stripComments(text)
                .replaceAll("\"(?:\\\\.|[^\"\\\\\\n])*\"", "\"\"");
    }

    /** The text of a string literal's body, its escapes read as Java reads them. */
    private static String decodeLiteral(String body) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\' || i + 1 >= body.length()) {
                out.append(c);
                continue;
            }
            char e = body.charAt(++i);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'u' -> {
                    while (i + 1 < body.length() && body.charAt(i + 1) == 'u') {
                        i++;
                    }
                    out.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> out.append(e);
            }
        }
        return out.toString();
    }

    /** Every.java file under both source roots. */
    private static List<Path> allSources(Path root) {
        List<Path> out = new ArrayList<>();
        for (String rel : List.of("src/main/java", "src/client/java", "src/test/java")) {
            Path dir = root.resolve(rel);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                out.addAll(walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java")).toList());
            } catch (IOException e) {
            }
        }
        return out;
    }

    /** Both source roots plus resources, concatenated — the reference haystack. */
    private static String readAll(Path root) {
        StringBuilder all = new StringBuilder();
        for (Path file : allSources(root)) {
            all.append(read(file)).append('\n');
        }
        Path resources = root.resolve("src/main/resources");
        if (Files.isDirectory(resources)) {
            try (Stream<Path> walk = Files.walk(resources)) {
                for (Path file : walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json")
                                || p.toString().endsWith(".toml")).toList()) {
                    all.append(read(file)).append('\n');
                }
            } catch (IOException e) {
            }
        }
        return all.toString();
    }

    private static String fileName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    /**
     * The source with every method or constructor body blanked out whose parameter list declares {@code
     * name}.
     */
    private static String maskShadowedBodies(String text, String name) {
        Pattern header = Pattern.compile(
                "\\(([^()]*)\\)\\s*(?:throws[\\w\\s.,]+)?\\{");
        Pattern declares = Pattern.compile(
                "[\\w<>\\[\\].?]+\\s+" + Pattern.quote(name) + "\\s*(?:,|$)");
        StringBuilder out = new StringBuilder(text);
        Matcher m = header.matcher(text);
        while (m.find()) {
            if (!declares.matcher(m.group(1).strip()).find()) {
                continue;
            }
            int open = m.end() - 1;
            int depth = 0;
            int close = text.length();
            for (int i = open; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                if (c == '}' && --depth == 0) {
                    close = i;
                    break;
                }
            }
            for (int i = m.start(); i <= Math.min(close, text.length() - 1); i++) {
                if (out.charAt(i) != '\n') out.setCharAt(i, ' ');
            }
        }
        return out.toString();
    }

    private static int countOccurrences(String text, Pattern pattern) {
        int n = 0;
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Every {@code src/main/java} line matching a pattern, as "RelativePath#line: text". */
    private static List<String> grepJava(Path root, Pattern pattern) {
        List<String> hits = new ArrayList<>();
        Path javaRoot = root.resolve("src/main/java");
        try (Stream<Path> walk = Files.walk(javaRoot)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = javaRoot.relativize(file).toString().replace('\\', '/');
                String[] fileLines = read(file).split("\n");
                for (int i = 0; i < fileLines.length; i++) {
                    String text = fileLines[i].strip();
                    if (text.startsWith("//") || text.startsWith("*")
                            || text.startsWith("/*")) {
                        continue;
                    }
                    if (pattern.matcher(text).find()) {
                        hits.add(rel + " line " + (i + 1) + ": " + text);
                    }
                }
            }
        } catch (IOException e) {
            hits.add("could not scan src/main/java: " + e);
        }
        return hits;
    }

    private static List<String> screens(Path javaRoot) {
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(javaRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getParent() != null
                            && p.getParent().endsWith("client/screen")
                            && p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.getFileName().toString().replace(".java", ""))
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

}