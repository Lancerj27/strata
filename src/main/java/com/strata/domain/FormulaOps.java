package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.Formula;
import com.strata.model.ScalingFormulas;
import com.strata.model.ScalingFormulas.Kind;
import com.strata.util.Labels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything Scaling > Formulas reads and does: each formula's expression, its check, its preview, and the
 * write to scaling_formulas.csv.
 */
public final class FormulaOps {

    private FormulaOps() {}

    /** The levels the preview shows. */
    public static final List<Integer> PREVIEW_LEVELS = List.of(1, 5, 10, 25, 50, 100);

    /** The expression in force for a kind: the stored one, or the default. */
    public static String expression(Kind kind) {
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ScalingFormulas.FILE))) {
            if (kind == Kind.of(row.get(Schema.ScalingFormulas.FORMULA))) {
                String e = row.getOrDefault(Schema.ScalingFormulas.EXPRESSION, "").strip();
                if (!e.isEmpty()) return e;
            }
        }
        return kind.defaultExpression;
    }

    /** Why an expression cannot be saved, or null when it can. */
    public static String problem(Kind kind, String expression) {
        return Formula.problem(expression, kind.variables);
    }

    /** The formula at the preview levels with sample values for the other variables, in one line. */
    public static String preview(Kind kind, String expression) {
        if (problem(kind, expression) != null) {
            return "";
        }
        Formula f = Formula.parse(expression, kind.variables);
        List<String> parts = new ArrayList<>();
        for (int level : PREVIEW_LEVELS) {
            Map<String, Double> v = sample(kind, level);
            parts.add("Lv " + level + ": " + Labels.number(f.eval(v)));
        }
        return String.join("  ·  ", parts);
    }

    /** What the preview assumes for the variables that are not the level. */
    public static String sampleText(Kind kind) {
        return switch (kind) {
            case AREA_LEVEL -> "Preview: the resolved level is the level shown, local_difficulty 3, chunk_scale 0.13, player_level 10, world_stage 1, world_level 3.5.";
            case MOB_ATTRIBUTE, PLAYER_ATTRIBUTE ->
                    "Preview: base 20, start_level 2, per_level 0.15, exponent 0.6, coefficient 1.";
            case MOB_XP -> "Preview: per_level 0.15, exponent 1.";
        };
    }

    private static Map<String, Double> sample(Kind kind, int level) {
        return switch (kind) {
            case AREA_LEVEL -> ScalingFormulas.vars("level", level, "local_difficulty", 3.0, "chunk_scale", 0.13, "player_level", 10, "world_stage", 1, "world_level", 3.5);
            case MOB_ATTRIBUTE, PLAYER_ATTRIBUTE -> ScalingFormulas.vars("base", 20, "level", level,
                    "start_level", 2, "per_level", 0.15, "exponent", 0.6, "coefficient", 1.0);
            case MOB_XP -> ScalingFormulas.vars("level", level, "per_level", 0.15, "exponent", 1.0);
        };
    }

    /** Saves an expression, then reloads. */
    public static WriteReport save(Kind kind, String expression) {
        String e = expression == null ? "" : expression.strip();
        String problem = problem(kind, e);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        Map<String, String> key = Map.of(Schema.ScalingFormulas.FORMULA, kind.id);
        WriteReport report = new WriteReport().record("the " + kind.label.toLowerCase() + " formula",
                TableWriter.upsert(Schema.ScalingFormulas.FILE, key, Map.of(Schema.ScalingFormulas.EXPRESSION, e)));
        return Commit.reloadIfOk(report);
    }
}
