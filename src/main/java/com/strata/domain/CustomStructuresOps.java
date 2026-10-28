package com.strata.domain;

import com.strata.data.CustomStructures;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.CustomStructure;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * THE DESIGNER'S STRUCTURES, for the Custom structures list and editor: a structure file brought in as a
 * texture or a sound is, and where and how often the world builds it.
 */
public final class CustomStructuresOps {

    private CustomStructuresOps() {
    }

    /** When a change applies. */
    public static final String APPLIES = "It generates in land made after the next world load.";

    /** One structure in the list. */
    public record Entry(String name, String biomes, String spacing, boolean enabled, boolean hasFile) {
    }

    /** The editor's fields, as text. */
    public record Form(String name, String file, String biomes, String spacing, String separation, String step,
                       String terrain, String height) {

        Map<String, String> row() {
            Map<String, String> row = new LinkedHashMap<>();
            row.put(CustomStructure.NAME, strip(name));
            row.put(CustomStructure.BIOMES, strip(biomes));
            row.put(CustomStructure.SPACING, strip(spacing));
            row.put(CustomStructure.SEPARATION, strip(separation));
            row.put(CustomStructure.STEP, strip(step));
            row.put(CustomStructure.TERRAIN, strip(terrain));
            row.put(CustomStructure.HEIGHT, strip(height));
            return row;
        }
    }

    /** Every structure, in file order. */
    public static List<Entry> all() {
        return CustomStructures.rows().stream().filter(r -> !cell(r, CustomStructure.NAME).isEmpty())
                .map(r -> new Entry(cell(r, CustomStructure.NAME), cell(r, CustomStructure.BIOMES),
                        cell(r, CustomStructure.SPACING), com.strata.data.Parse.flag(r.get(CustomStructure.ENABLED), true),
                        Files.isRegularFile(CustomStructures.fileOf(Paths.ROOT, cell(r, CustomStructure.NAME)))))
                .toList();
    }

    /** The names, in file order. */
    public static List<String> names() {
        return all().stream().map(Entry::name).toList();
    }

    /** The form for a structure, or a new one's defaults. */
    public static Form formFor(String name) {
        for (Map<String, String> r : CustomStructures.rows()) {
            if (cell(r, CustomStructure.NAME).equals(name)) {
                return new Form(name, "", cell(r, CustomStructure.BIOMES), cell(r, CustomStructure.SPACING),
                        cell(r, CustomStructure.SEPARATION), cell(r, CustomStructure.STEP), cell(r, CustomStructure.TERRAIN),
                        cell(r, CustomStructure.HEIGHT));
            }
        }
        Map<String, String> d = CustomStructure.DEFAULTS;
        return new Form("", "", d.get(CustomStructure.BIOMES), d.get(CustomStructure.SPACING),
                d.get(CustomStructure.SEPARATION), d.get(CustomStructure.STEP), d.get(CustomStructure.TERRAIN),
                d.get(CustomStructure.HEIGHT));
    }

    /** What a structure file holds, in words, or why it cannot be used. */
    public static CustomStructures.Probe probe(String typedPath) {
        return CustomStructures.probe(CustomAssetsOps.pathOf(typedPath));
    }

    /** Why the form cannot be saved, or null. */
    public static String formProblem(Form f, boolean isNew) {
        String problem = CustomStructure.problem(f.row());
        if (problem == null && isNew && names().contains(strip(f.name()))) {
            problem = "A structure named " + strip(f.name()) + " exists";
        }
        if (problem == null && (isNew || !strip(f.file()).isEmpty())) {
            problem = probe(f.file()).problem();
            if (problem != null && strip(f.file()).isEmpty()) {
                problem = "Structure file: type the path of a .nbt file a structure block saved";
            }
        }
        return problem;
    }

    /** Brings in the file (when one is given) and saves the settings. */
    public static WriteReport save(String originalName, Form f) {
        boolean isNew = originalName == null || originalName.isEmpty();
        String problem = formProblem(f, isNew);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String name = isNew ? strip(f.name()) : originalName;
        if (!strip(f.file()).isEmpty()) {
            String copied = CustomStructures.importFile(Paths.ROOT, name, CustomAssetsOps.pathOf(f.file()));
            if (copied != null) {
                return WriteReport.refused(copied);
            }
        }
        Map<String, String> row = f.row();
        row.remove(CustomStructure.NAME);
        return Commit.reloadIfOk(new WriteReport().record("the structure", TableWriter.upsert(
                Schema.CustomStructures.FILE, Map.of(Schema.CustomStructures.NAME, name), row)))
                .needsReload(APPLIES);
    }

    /** Switches structures on or off. */
    public static WriteReport setEnabled(List<String> names, boolean on) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String name : names) {
                report.record("the structure " + name, TableWriter.upsert(Schema.CustomStructures.FILE,
                        Map.of(Schema.CustomStructures.NAME, name), Map.of(Schema.CustomStructures.ENABLED, String.valueOf(on))));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Removes structures and their files. */
    public static WriteReport remove(List<String> names) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String name : names) {
                report.record("the structure " + name, TableWriter.delete(Schema.CustomStructures.FILE,
                        Map.of(Schema.CustomStructures.NAME, name)));
                try {
                    Files.deleteIfExists(CustomStructures.fileOf(Paths.ROOT, name));
                } catch (java.io.IOException e) {
                    report.record("its file", false);
                }
            }
            return Commit.reloadIfOk(report);
        });
    }

    private static String cell(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.strip();
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }
}
