package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.ProjectilePreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * THE DESIGNER'S PROJECTILES, for the Projectiles list and editor: projectiles.csv, and each one's abilities
 * in projectile_effects.csv.
 */
public final class ProjectileOps {

    private ProjectileOps() {
    }

    /** What the list and the editor say about when a change applies. */
    public static final String APPLIES = "A change applies at once, from the next shot.";

    /** The editor's fields, as text: what is typed is what is saved. */
    public record Form(String name, String base, String speed, String gravity, String damage, String explosion,
                       String fireSeconds, boolean keepBase, String look, String texture) {

        Map<String, String> row() {
            Map<String, String> row = new LinkedHashMap<>();
            row.put(Schema.Projectiles.NAME, strip(name));
            row.put(Schema.Projectiles.BASE, strip(base));
            row.put(Schema.Projectiles.SPEED, strip(speed));
            row.put(Schema.Projectiles.GRAVITY, strip(gravity));
            row.put(Schema.Projectiles.DAMAGE, strip(damage));
            row.put(Schema.Projectiles.EXPLOSION, strip(explosion));
            row.put(Schema.Projectiles.FIRE_SECONDS, strip(fireSeconds));
            row.put(Schema.Projectiles.KEEP_BASE, String.valueOf(keepBase));
            row.put(Schema.Projectiles.LOOK, strip(look));
            row.put(Schema.Projectiles.TEXTURE, strip(texture));
            return row;
        }
    }

    /** Every projectile row, in file order. */
    public static List<Map<String, String>> rows() {
        return CsvTable.read(Paths.table(Schema.Projectiles.FILE));
    }

    /** Every projectile, in file order. */
    public static List<ProjectilePreset> all() {
        return all(rows());
    }

    /** The rows as projectiles, a nameless row left out. */
    public static List<ProjectilePreset> all(List<Map<String, String>> rows) {
        List<ProjectilePreset> out = new ArrayList<>();
        for (Map<String, String> row : rows) {
            ProjectilePreset p = ProjectilePreset.of(row);
            if (!p.name().isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /** The names, in file order. */
    public static List<String> names() {
        return all().stream().map(ProjectilePreset::name).toList();
    }

    /** The projectile of this name, or null. */
    public static ProjectilePreset find(String name) {
        return all().stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    /** The form for a projectile, or an empty one for a new projectile. */
    public static Form formFor(String name) {
        for (Map<String, String> row : rows()) {
            if (cell(row, Schema.Projectiles.NAME).equals(name)) {
                return new Form(name, cell(row, Schema.Projectiles.BASE), cell(row, Schema.Projectiles.SPEED),
                        cell(row, Schema.Projectiles.GRAVITY), cell(row, Schema.Projectiles.DAMAGE),
                        cell(row, Schema.Projectiles.EXPLOSION), cell(row, Schema.Projectiles.FIRE_SECONDS),
                        ProjectilePreset.of(row).keepBase(), cell(row, Schema.Projectiles.LOOK),
                        cell(row, Schema.Projectiles.TEXTURE));
            }
        }
        return new Form("", "", "", "", "", "", "", true, "", "");
    }

    /** Why the form cannot be saved, or null. */
    public static String formProblem(Form f, boolean isNew, List<String> existing) {
        String problem = ProjectilePreset.problem(f.row());
        if (problem == null && isNew && existing.contains(strip(f.name()))) {
            problem = "A projectile named " + strip(f.name()) + " exists";
        }
        return problem;
    }

    /** Saves the form; a saved projectile keeps its name. */
    public static WriteReport saveForm(String originalName, Form f) {
        boolean isNew = originalName == null || originalName.isEmpty();
        String problem = formProblem(f, isNew, names());
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        Map<String, String> row = f.row();
        String name = isNew ? row.get(Schema.Projectiles.NAME) : originalName;
        row.remove(Schema.Projectiles.NAME);
        return Commit.reloadIfOk(new WriteReport().record("the projectile", TableWriter.upsert(
                Schema.Projectiles.FILE, Map.of(Schema.Projectiles.NAME, name), row)));
    }

    /** Switches projectiles on or off: an off one fires as its base, with nothing of its own. */
    public static WriteReport setEnabled(List<String> names, boolean on) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String name : names) {
                report.record("the projectile " + name, TableWriter.upsert(Schema.Projectiles.FILE,
                        Map.of(Schema.Projectiles.NAME, name), Map.of(Schema.Projectiles.ENABLED, String.valueOf(on))));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Removes projectiles with their abilities. */
    public static WriteReport remove(List<String> names) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String name : names) {
                report.record("the projectile " + name, TableWriter.delete(Schema.Projectiles.FILE,
                        Map.of(Schema.Projectiles.NAME, name)));
                report.cascaded("its abilities", TableWriter.delete(Schema.ProjectileEffects.FILE,
                        Map.of(Schema.ProjectileEffects.PROJECTILE, name)));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** A projectile's abilities, for the Abilities editor: On impact only. */
    public static AbilitiesOps.Store effectsStore(String name) {
        return AbilitiesOps.flatStore(Schema.ProjectileEffects.FILE, Schema.ProjectileEffects.PROJECTILE, name,
                List.of(TriggerOps.IMPACT));
    }

    /** How many abilities a projectile has, for its row in the list. */
    public static int abilityCount(String name) {
        int n = 0;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ProjectileEffects.FILE))) {
            if (cell(row, Schema.ProjectileEffects.PROJECTILE).equals(name)) {
                n++;
            }
        }
        return n;
    }

    /** A projectile's row in a list: what it is fired as, and what it changes. */
    public static String summary(ProjectilePreset p, int abilities) {
        List<String> parts = new ArrayList<>();
        parts.add("fired as " + com.strata.util.Labels.pretty(net.minecraft.resources.ResourceLocation.tryParse(p.base())));
        if (!Double.isNaN(p.damage())) {
            parts.add("damage " + trim(p.damage()));
        }
        if (p.explosion() > 0) {
            parts.add("explodes " + trim(p.explosion()));
        }
        if (p.fireSeconds() > 0) {
            parts.add("burns " + trim(p.fireSeconds()) + "s");
        }
        parts.add(com.strata.ui.Words.count(abilities, "ability", "abilities"));
        return (p.enabled() ? "" : "off · ") + String.join(" · ", parts);
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String cell(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.strip();
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }
}
