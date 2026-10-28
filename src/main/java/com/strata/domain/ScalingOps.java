package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the editor reads and does for scaling curves. */
public final class ScalingOps {

    private ScalingOps() {
    }

    /** One curve, read with the helpers and fallbacks AttributeCurve uses. */
    public record CurveRow(String attribute, double perLevel, double exponent,
                           boolean enabled, int startLevel) {
    }

    /** What the curve editor shows and saves, as typed. */
    public record CurveForm(String attribute, String perLevel, String exponent,
                            String startLevel, boolean enabled) {
    }

    /** The starting form for a new curve. */
    public static final CurveForm NEW_CURVE = new CurveForm("", "0.0", "0.6",
            String.valueOf(com.strata.runtime.AttributeCurve.DEFAULT_START_LEVEL), true);

    /** The curves of one table: mob curves, or player curves. */
    public static List<CurveRow> curves(boolean mobs) {
        List<CurveRow> rows = new ArrayList<>();
        for (Map<String, String> csv : CsvTable.read(Paths.table(fileFor(mobs)))) {
            String attr = csv.getOrDefault(Schema.Scaling.ATTRIBUTE, "").strip();
            if (attr.isEmpty()) continue;
            // AttributeCurve reads these columns with exactly these helpers and fallbacks; the editor
            // previews what that curve will do.
            rows.add(new CurveRow(attr,
                    Parse.number(csv.get(Schema.Scaling.PER_LEVEL), 0.0),
                    Parse.number(csv.get(Schema.Scaling.EXPONENT), 0.6),
                    Parse.flag(csv.get(Schema.Scaling.ENABLED), true),
                    Parse.integer(csv.get(Schema.Scaling.START_LEVEL),
                            com.strata.runtime.AttributeCurve.DEFAULT_START_LEVEL)));
        }
        return rows;
    }

    /** The editor form for a curve, or {@link #NEW_CURVE} when row is null. */
    public static CurveForm formFor(CurveRow row) {
        if (row == null) {
            return NEW_CURVE;
        }
        return new CurveForm(row.attribute(), String.valueOf(row.perLevel()),
                String.valueOf(row.exponent()), String.valueOf(row.startLevel()), row.enabled());
    }

    /**
     * The shape of a curve: bonus = per_level x level^exponent, so an exponent below 1 gives diminishing
     * returns, 1 is linear, and above 1 accelerates.
     */
    public static String shapeOf(double exponent) {
        if (exponent < 1.0) return "diminishing";
        if (exponent > 1.0) return "accelerating";
        return "linear";
    }

    /** The levels a preview shows. */
    public static final List<Integer> PREVIEW_LEVELS = List.of(1, 5, 10, 20, 50);

    /** The mob the preview opens on. */
    public static final String PREVIEW_MOB = "minecraft:zombie";

    /**
     * A curve on one real mob.
     *
     * @param base the mob's base value of the attribute in effect (the game's, or the edit), or null when the
     *     mob has no such attribute
     * @param values the value at each of {@link #PREVIEW_LEVELS}, empty when base is null
     */
    public record MobPreview(String entity, String attribute, Double base, List<Double> values) {
    }

    /**
     * The bonus the game applies at each of {@link #PREVIEW_LEVELS}, as a fraction, or null while a number is
     * half-typed.
     */
    public static List<Double> bonuses(CurveForm form) {
        return bonuses(form, com.strata.model.ScalingFormulas.Kind.MOB_ATTRIBUTE, 1.0);
    }

    /** As above, through the formula of a kind, for an attribute whose base value is {@code base}. */
    public static List<Double> bonuses(CurveForm form, com.strata.model.ScalingFormulas.Kind kind, double base) {
        double perLevel;
        double exponent;
        try {
            perLevel = Double.parseDouble(form.perLevel() == null ? "" : form.perLevel().strip());
            exponent = Double.parseDouble(form.exponent() == null ? "" : form.exponent().strip());
        } catch (NumberFormatException e) {
            return null;
        }
        int start = com.strata.runtime.AttributeCurve.DEFAULT_START_LEVEL;
        try {
            String s = form.startLevel() == null ? "" : form.startLevel().strip();
            start = s.isEmpty() ? start : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            start = com.strata.runtime.AttributeCurve.DEFAULT_START_LEVEL;
        }
        List<Double> out = new ArrayList<>();
        for (int level : PREVIEW_LEVELS) {
            double b = com.strata.runtime.AttributeCurve.bonus(kind, base, perLevel, exponent, level, start, 1.0);
            out.add(Double.isFinite(b) && b > 0 ? b : 0.0);
        }
        return out;
    }

    /**
     * The attribute's value at each bonus: base x (1 + bonus x coefficient), as the game's
     * ADD_MULTIPLIED_BASE modifier computes it.
     */
    public static List<Double> realValues(double base, List<Double> bonuses, double coefficient) {
        List<Double> out = new ArrayList<>();
        for (double b : bonuses) {
            double applied = b * coefficient;
            out.add(Double.isFinite(applied) && applied > 0 ? base * (1 + applied) : base);
        }
        return out;
    }

    /**
     * The curve on one mob at coefficient 1: its base value of the attribute and the value at each preview
     * level.
     */
    public static MobPreview mobPreview(String entity, CurveForm form) {
        if (bonuses(form) == null || form.attribute() == null || form.attribute().isBlank()) {
            return null;
        }
        String attribute = form.attribute().strip();
        Double base = baseValue(entity, attribute);
        List<Double> bonuses = base == null ? List.of()
                : bonuses(form, com.strata.model.ScalingFormulas.Kind.MOB_ATTRIBUTE, base);
        return new MobPreview(entity, attribute, base,
                base == null ? List.of() : realValues(base, bonuses, 1.0));
    }

    /**
     * A mob's base value of an attribute in effect: the edit over the game's value, as the mob data editor
     * shows it.
     */
    public static Double baseValue(String entity, String attribute) {
        for (MobDataOps.Value v : MobDataOps.sheet(new MobDataOps.Target(entity, "", "", "")).values()) {
            if (v.group().equals(com.strata.data.GameLayer.ATTRIBUTE) && v.key().equals(attribute)) {
                double d = Parse.number(v.effective(), Double.NaN);
                return Double.isFinite(d) ? d : null;
            }
        }
        return null;
    }

    /** Why a curve form cannot be saved, or null when it can. */
    public static String formProblem(CurveForm form) {
        if (form.attribute() == null || form.attribute().isBlank()) {
            return "Pick an attribute first";
        }
        if (!finite(form.perLevel()) || !finite(form.exponent())) {
            return "Per level and exponent must be finite numbers";
        }
        // A negative bonus is skipped by AttributeCurve#apply, so the curve would do nothing.
        if (Double.parseDouble(form.perLevel().strip()) < 0) {
            return "Per level must be 0 or more: a curve only adds";
        }
        String start = form.startLevel() == null ? "" : form.startLevel().strip();
        if (!start.isEmpty()) {
            try {
                if (Integer.parseInt(start) < 1) return "The start level must be 1 or more";
            } catch (NumberFormatException e) {
                return "The start level must be a whole number";
            }
        }
        return null;
    }

    private static boolean finite(String value) {
        try {
            return Double.isFinite(Double.parseDouble(value == null ? "" : value.strip()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Saves a curve from the editor, then reloads.
     *
     * @param originalAttribute the attribute the editor opened, or null for a new curve
     */
    public static WriteReport save(boolean mobs, String originalAttribute, CurveForm form) {
        String problem = formProblem(form);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String file = fileFor(mobs);
        String attribute = form.attribute().strip();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Scaling.PER_LEVEL, form.perLevel().strip());
        values.put(Schema.Scaling.EXPONENT, form.exponent().strip());
        values.put(Schema.Scaling.ENABLED, String.valueOf(form.enabled()));
        String start = form.startLevel() == null ? "" : form.startLevel().strip();
        if (!start.isEmpty()) {
            values.put(Schema.Scaling.START_LEVEL, start);
        }
        WriteReport report = new WriteReport().record("the scaling curve",
                TableWriter.upsert(file, Map.of(Schema.Scaling.ATTRIBUTE, attribute), values));
        if (report.ok() && originalAttribute != null && !originalAttribute.equals(attribute)) {
            report.cascaded("the previous attribute", TableWriter.delete(
                    file, Map.of(Schema.Scaling.ATTRIBUTE, originalAttribute)));
        }
        return Commit.reloadIfOk(report);
    }

    /**
     * On / Off for the picked curves (every picked row, not the selected one alone): all go Off when any is
     * On, else all go On.
     */
    public static WriteReport toggle(boolean mobs, List<CurveRow> rows) {
        String on = String.valueOf(Picks.switchedOn(rows.stream().map(CurveRow::enabled).toList()));
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (CurveRow row : rows) {
                if (!report.ok()) {
                    break;
                }
                report.record("the curve", TableWriter.upsert(fileFor(mobs),
                        Map.of(Schema.Scaling.ATTRIBUTE, row.attribute()), Map.of(Schema.Scaling.ENABLED, on)));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Removes the picked curves, then reloads once. */
    public static WriteReport remove(boolean mobs, List<CurveRow> rows) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (CurveRow row : rows) {
                if (!report.ok()) {
                    break;
                }
                report.record("the curve", TableWriter.delete(fileFor(mobs),
                        Map.of(Schema.Scaling.ATTRIBUTE, row.attribute())));
            }
            return Commit.reloadIfOk(report);
        });
    }

    private static String fileFor(boolean mobs) {
        return mobs ? Schema.Scaling.MOBS_FILE : Schema.Scaling.PLAYER_FILE;
    }

    // ---- HOW THE COEFFICIENTS COMBINE.

    /** The tier coefficient's tooltip. */
    public static final List<String> COEFFICIENT_TIER = List.of(
            "Coefficient: multiplies every scaling bonus in this tier's biomes (1.0 = normal, 2.0 = twice).",
            "A biome's own coefficient replaces this one. A mob's multiplies on top.");
    /** The biome coefficient's tooltip. */
    public static final List<String> COEFFICIENT_BIOME = List.of(
            "Blank = the tier's coefficient. A number replaces the tier's for this biome (1.0 = normal).",
            "A mob's coefficient multiplies on top.");
    /** The mob coefficient's tooltip. */
    public static final List<String> COEFFICIENT_MOB = List.of(
            "Coefficient: multiplies this mob's scaling bonus (1.0 = normal).",
            "It multiplies on top of the biome's or the tier's: 1.5 in a tier at 2.0 gives 3 times the bonus.");
}