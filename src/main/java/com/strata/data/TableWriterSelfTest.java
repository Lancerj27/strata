package com.strata.data;

import com.strata.SelfTestHarness;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Round-trip tests for TableWriter. */
public final class TableWriterSelfTest {

    private TableWriterSelfTest() {}

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];

        Path dir;
        try {
            dir = Files.createTempDirectory("strata-selftest");
        } catch (Exception e) {
            lines.add("ERROR could not create temp dir — " + e);
            tally[1]++;
            return new SelfTestHarness.Result(0, 1, lines);
        }

        SelfTestHarness.check(lines, tally, "1  upsert creates a new row", () -> {
            Path root = fresh(dir, "t1");
            write(root, Schema.Tiers.FILE,
                    "# Strata tier table.\n# name,level,spawn_mode,coefficient\n"
                            + "name,level,spawn_mode,coefficient\n");
            boolean wrote = TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("name", "NEW"), vals("level", "5"));
            String out = read(root, Schema.Tiers.FILE);
            return wrote && out.contains("NEW,5")
                    && out.startsWith("# Strata tier table.");
        });

        SelfTestHarness.check(lines, tally, "2  upsert updates in place, no duplicate", () -> {
            Path root = fresh(dir, "t2");
            write(root, Schema.Tiers.FILE,
                    "name,level,spawn_mode,coefficient\nOLD,3,add,1.0\n");
            TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("name", "OLD"), vals("level", "7", "coefficient", "1.5"));
            String out = read(root, Schema.Tiers.FILE);
            return count(out, "OLD") == 1 && out.contains("OLD,7,add,1.5");
        });

        SelfTestHarness.check(lines, tally, "3  upsert defaults enabled=true on new rows", () -> {
            Path root = fresh(dir, "t3");
            write(root, Schema.Tiers.FILE, "name,enabled\n");
            TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("name", "X"), Map.of());
            return read(root, Schema.Tiers.FILE).contains("X,true");
        });

        SelfTestHarness.check(lines, tally, "4  delete removes the row, keeps header", () -> {
            Path root = fresh(dir, "t4");
            write(root, Schema.Tiers.FILE,
                    "# c\nname,level\nOLD,3\nKEEP,4\n");
            boolean removed = TableWriter.delete(root.resolve(Schema.Tiers.FILE),
                    keys("name", "OLD"));
            String out = read(root, Schema.Tiers.FILE);
            return removed && !out.contains("OLD") && out.contains("KEEP,4")
                    && out.contains("# c") && out.contains("name,level");
        });

        SelfTestHarness.check(lines, tally, "5  updateColumn rewrites every matching row", () -> {
            Path root = fresh(dir, "t5");
            write(root, Schema.TierBiomes.FILE,
                    "tier,biome\nTEMP,plains\nTEMP,forest\nOLD,ocean\n");
            boolean updated = TableWriter.updateColumn(root.resolve(Schema.TierBiomes.FILE),
                    "tier", "TEMP", "tier", "RENAMED");
            String out = read(root, Schema.TierBiomes.FILE);
            return updated
                    && out.contains("RENAMED,plains") && out.contains("RENAMED,forest")
                    && out.contains("OLD,ocean") && !out.contains("TEMP");
        });

        SelfTestHarness.check(lines, tally, "6  upsert refuses a missing key column", () -> {
            Path root = fresh(dir, "t6");
            write(root, Schema.Tiers.FILE, "name,level\n");
            boolean wrote = TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("nope", "X"), vals("level", "1"));
            return !wrote && read(root, Schema.Tiers.FILE).equals("name,level\n");
        });

        SelfTestHarness.check(lines, tally, "7  delete with no matching row rewrites nothing", () -> {
            Path root = fresh(dir, "t7");
            write(root, Schema.Tiers.FILE, "name,level\nOLD,3\n");
            boolean removed = TableWriter.delete(root.resolve(Schema.Tiers.FILE),
                    keys("name", "MISSING"));
            return !removed && read(root, Schema.Tiers.FILE).equals("name,level\nOLD,3\n");
        });

        SelfTestHarness.check(lines, tally, "8  updateColumnIf respects the extra predicate", () -> {
            Path root = fresh(dir, "t8");
            write(root, Schema.MobSpawns.FILE,
                    "scope,scope_id,entity\ntier,TEMP,husk\nbiome,TEMP,cow\n");
            boolean updated = TableWriter.updateColumnIf(
                    root.resolve(Schema.MobSpawns.FILE),
                    "scope_id", "TEMP", "scope_id", "RENAMED",
                    "scope", "tier");
            String out = read(root, Schema.MobSpawns.FILE);
            return updated
                    && out.contains("tier,RENAMED,husk")
                    && out.contains("biome,TEMP,cow")
                    && !out.contains("tier,TEMP");
        });

        SelfTestHarness.check(lines, tally, "9  a comma in a value round-trips intact", () -> {
            Path root = fresh(dir, "t9");
            write(root, Schema.Variants.FILE,
                    "name,base_entity,display_name,coefficient\n");
            TableWriter.upsert(root.resolve(Schema.Variants.FILE),
                    keys("name", "husk_frosty"),
                    vals("base_entity", "minecraft:husk",
                            "display_name", "Frosty, the Husk",
                            "coefficient", "1.0"));
            var rows = CsvTable.read(root.resolve(Schema.Variants.FILE));
            if (rows.size() != 1) {
                lines.add("    9  expected 1 row, got " + rows.size());
                return false;
            }
            var row = rows.getFirst();
            boolean ok = "Frosty, the Husk".equals(row.get("display_name"))
                    && "1.0".equals(row.get("coefficient"))
                    && "minecraft:husk".equals(row.get("base_entity"))
                    && "husk_frosty".equals(row.get("name"));
            if (!ok) {
                lines.add("    9  round-trip lost data: " + row);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "9b a quote inside a value round-trips, and only needy cells are quoted", () -> {
            Path root = fresh(dir, "t9b");
            write(root, Schema.Variants.FILE, "name,display_name\n");
            TableWriter.upsert(root.resolve(Schema.Variants.FILE),
                    keys("name", "pipe"), vals("display_name", "a 5\" pipe, bent"));
            var rows = CsvTable.read(root.resolve(Schema.Variants.FILE));
            boolean value = rows.size() == 1
                    && "a 5\" pipe, bent".equals(rows.getFirst().get("display_name"));
            // The key had nothing needing quotes, so it must still be bare — quoting everything would churn
            // every table on every save.
            String out = read(root, Schema.Variants.FILE);
            boolean bare = out.contains("\npipe,");
            if (!value || !bare) {
                lines.add("    9b value=" + value + " bare=" + bare + " file=" + out.strip());
            }
            return value && bare;
        });

        SelfTestHarness.check(lines, tally,
                "9c a 0.1-era unquoted table still reads identically", () -> {
            Path root = fresh(dir, "t9c");
            // Written by 0.1: no quotes anywhere, trailing empty column.
            write(root, Schema.Variants.FILE,
                    "name,base_entity,display_name,coefficient\n"
                            + "husk_frosty,minecraft:husk,Frosty the Husk,\n");
            var rows = CsvTable.read(root.resolve(Schema.Variants.FILE));
            boolean legacy = rows.size() == 1
                    && "Frosty the Husk".equals(rows.getFirst().get("display_name"))
                    && "".equals(rows.getFirst().get("coefficient"));
            // EXTENDED for the '#' fix rather than given a case of its own: the point of that change is that
            // it costs nothing anywhere else, and the file a 0.1 install already has is where that has to
            // hold.
            write(root, Schema.Ores.FILE, "name,note\niron,grade #2 seam\n");
            var ores = CsvTable.read(root.resolve(Schema.Ores.FILE));
            boolean midHashUnquoted = ores.size() == 1
                    && "grade #2 seam".equals(ores.getFirst().get("note"));
            TableWriter.upsert(root.resolve(Schema.Ores.FILE),
                    keys("name", "iron"), vals("note", "grade #2 seam"));
            boolean stillBare = read(root, Schema.Ores.FILE).contains("iron,grade #2 seam");
            if (!legacy || !midHashUnquoted || !stillBare) {
                lines.add("    9c legacy=" + legacy + " midHash=" + midHashUnquoted
                        + " stillBare=" + stillBare);
            }
            return legacy && midHashUnquoted && stillBare;
        });

        SelfTestHarness.check(lines, tally,
                "14 a leading '#' survives the round trip instead of erasing the row", () -> {
            Path root = fresh(dir, "t14");
            write(root, Schema.Tiers.FILE,
                    "# Strata tier table.\nname,level,coefficient\n");
            // The exact shape a designer can type today: TierEditScreen's validator checks non-empty and
            // unique, so "#1 Hardcore" is accepted.
            boolean wrote = TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("name", "#1 Hardcore"), vals("level", "7"));
            var rows = CsvTable.read(root.resolve(Schema.Tiers.FILE));
            boolean readBack = rows.size() == 1
                    && "#1 Hardcore".equals(rows.getFirst().get("name"))
                    && "7".equals(rows.getFirst().get("level"));
            // THE SECOND SAVE IS THE REAL TEST.
            TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("name", "#1 Hardcore"), vals("coefficient", "1.5"));
            var after = CsvTable.read(root.resolve(Schema.Tiers.FILE));
            boolean survived = after.size() == 1
                    && "#1 Hardcore".equals(after.getFirst().get("name"))
                    && "1.5".equals(after.getFirst().get("coefficient"));
            // The file's own documentation is still above the header.
            boolean preamble = read(root, Schema.Tiers.FILE).startsWith("# Strata tier table.");
            if (!wrote || !readBack || !survived || !preamble) {
                lines.add("    14 wrote=" + wrote + " readBack=" + readBack
                        + " survived=" + survived + " preamble=" + preamble
                        + " file=" + read(root, Schema.Tiers.FILE).strip());
            }
            return wrote && readBack && survived && preamble;
        });

        SelfTestHarness.check(lines, tally,
                "15 a ragged row can be deleted", () -> {
            Path root = fresh(dir, "t15");
            // Short by two columns - what a hand-edit, or a file written before addColumn widened the header,
            // actually looks like.
            write(root, Schema.TierBiomes.FILE,
                    "biome,tier,weight\nminecraft:plains,alpha\n");
            boolean removed = TableWriter.delete(root.resolve(Schema.TierBiomes.FILE),
                    keys("biome", "minecraft:plains"));
            boolean gone = CsvTable.read(root.resolve(Schema.TierBiomes.FILE)).isEmpty();
            if (!removed || !gone) {
                lines.add("    15 removed=" + removed + " gone=" + gone);
            }
            return removed && gone;
        });

        SelfTestHarness.check(lines, tally,
                "16 upsert updates a ragged row instead of appending a duplicate", () -> {
            Path root = fresh(dir, "t16");
            write(root, Schema.Tiers.FILE,
                    "name,level,coefficient\nalpha,3\n");
            boolean wrote = TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("name", "alpha"), vals("coefficient", "2.0"));
            var rows = CsvTable.read(root.resolve(Schema.Tiers.FILE));
            // The failure this pins is NOT "the write was refused".
            boolean single = rows.size() == 1;
            boolean updated = single && "2.0".equals(rows.getFirst().get("coefficient"))
                    && "3".equals(rows.getFirst().get("level"));
            if (!wrote || !single || !updated) {
                lines.add("    16 wrote=" + wrote + " rows=" + rows.size()
                        + " file=" + read(root, Schema.Tiers.FILE).strip());
            }
            return wrote && single && updated;
        });

        SelfTestHarness.check(lines, tally,
                "17 updateColumn reaches a ragged row", () -> {
            Path root = fresh(dir, "t17");
            write(root, Schema.MobSpawns.FILE,
                    "scope,scope_id,entity,weight\ntier,alpha\n");
            // The rename cascade runs through this method, so a ragged row meant a pointer that silently
            // failed to follow its tier.
            boolean updated = TableWriter.updateColumn(root.resolve(Schema.MobSpawns.FILE),
                    "scope_id", "alpha", "scope_id", "beta");
            var rows = CsvTable.read(root.resolve(Schema.MobSpawns.FILE));
            boolean moved = rows.size() == 1
                    && "beta".equals(rows.getFirst().get("scope_id"));
            if (!updated || !moved) {
                lines.add("    17 updated=" + updated + " file="
                        + read(root, Schema.MobSpawns.FILE).strip());
            }
            return updated && moved;
        });

        SelfTestHarness.check(lines, tally,
                "18 a missing value column is grown, not silently dropped", () -> {
            Path root = fresh(dir, "t18");
            write(root, Schema.Variants.FILE, "name,display_name\npipe,plain\n");
            boolean wrote = TableWriter.upsert(root.resolve(Schema.Variants.FILE),
                    keys("name", "pipe"), vals("coefficient", "2.0"));
            var rows = CsvTable.read(root.resolve(Schema.Variants.FILE));
            boolean cell = rows.size() == 1
                    && "2.0".equals(rows.getFirst().get("coefficient"))
                    && "plain".equals(rows.getFirst().get("display_name"));
            boolean header = read(root, Schema.Variants.FILE)
                    .contains("name,display_name,coefficient");
            if (!wrote || !cell || !header) {
                lines.add("    18 wrote=" + wrote + " cell=" + cell + " header=" + header
                        + " file=" + read(root, Schema.Variants.FILE).strip());
            }
            return wrote && cell && header;
        });

        SelfTestHarness.check(lines, tally, "10 a newline in a value cannot invent a row", () -> {
            Path root = fresh(dir, "t10");
            write(root, Schema.Variants.FILE, "name,display_name\n");
            TableWriter.upsert(root.resolve(Schema.Variants.FILE),
                    keys("name", "v1"), vals("display_name", "line one\nline two"));
            String out = read(root, Schema.Variants.FILE);
            // Header + exactly one data row.
            return out.lines().filter(l -> !l.isBlank()).count() == 2;
        });

        SelfTestHarness.check(lines, tally,
                "11 addColumn never writes the text \"null\" into a short row", () -> {
            Path root = fresh(dir, "t11");
            // A legacy row with FEWER cells than its header — exactly what an older Strata version left
            // behind.
            write(root, Schema.Equipment.FILE,
                    "variant_name,slot,item,chance_pct\nv1,head\n");
            TableWriter.addColumn(root.resolve(Schema.Equipment.FILE), "local_difficulty");
            String out = read(root, Schema.Equipment.FILE);
            if (out.contains("null")) {
                lines.add("    11 addColumn wrote a literal null: " + out.strip());
                return false;
            }
            String row = out.lines().filter(l -> l.startsWith("v1,")).findFirst().orElse("");
            return row.split(",", -1).length == 5;
        });

        // the class doc promised that a failure leaves the original intact, while the code used a plain
        // truncating write.
        SelfTestHarness.check(lines, tally, "12 a write leaves no temp file behind", () -> {
            Path root = fresh(dir, "t12");
            write(root, Schema.Tiers.FILE, "name,level\nOLD,3\n");
            TableWriter.upsert(root.resolve(Schema.Tiers.FILE), keys("name", "OLD"), vals("level", "9"));
            // The move must have consumed the temp file, not left it as litter beside the table where the
            // next read would ignore it silently.
            try (var stream = Files.list(root)) {
                boolean tempLeft = stream.anyMatch(p -> p.getFileName().toString().endsWith(".tmp"));
                if (tempLeft) {
                    lines.add("    12 a .tmp file survived the write");
                    return false;
                }
            }
            return read(root, Schema.Tiers.FILE).contains("OLD,9");
        });

        SelfTestHarness.check(lines, tally,
                "13 an interrupted write cannot leave a half-file", () -> {
            Path root = fresh(dir, "t13");
            write(root, Schema.Tiers.FILE, "name,level\nA,1\nB,2\n");
            // Simulate the crash window: a stale temp file from a write that died before its move.
            Files.writeString(root.resolve("tiers.csv.tmp"), "GARBAGE",
                    StandardCharsets.UTF_8);
            String before = read(root, Schema.Tiers.FILE);
            if (!before.contains("A,1") || !before.contains("B,2")) {
                return false;
            }
            boolean wrote = TableWriter.upsert(root.resolve(Schema.Tiers.FILE),
                    keys("name", "A"), vals("level", "5"));
            String after = read(root, Schema.Tiers.FILE);
            return wrote && after.contains("A,5") && after.contains("B,2")
                    && !after.contains("GARBAGE");
        });

        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }

    // ---- helpers ----

    private static Path fresh(Path dir, String name) throws Exception {
        Path root = dir.resolve(name);
        Files.createDirectories(root);
        return root;
    }

    private static void write(Path root, String file, String content) throws Exception {
        Files.writeString(root.resolve(file), content, StandardCharsets.UTF_8);
    }

    private static String read(Path root, String file) throws Exception {
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8);
    }

    private static Map<String, String> keys(String k, String v) {
        return vals(k, v);
    }

    private static Map<String, String> vals(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int from = 0;
        int at;
        while ((at = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from = at + needle.length();
        }
        return count;
    }

}