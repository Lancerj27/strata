package com.strata;

import com.strata.model.Formula;
import com.strata.model.ScalingFormulas;
import com.strata.model.ScalingFormulas.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The scaling formula parser and the default formulas (design 2.1, 2.2). */
public final class FormulaSelfTest {

    private FormulaSelfTest() {}

    private static double ev(String s) {
        return Formula.parse(s, Set.of("x", "y")).eval(Map.of("x", 3.0, "y", 2.0));
    }

    private static boolean refused(String s) {
        return Formula.problem(s, Set.of("x")) != null;
    }

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];

        SelfTestHarness.check(lines, tally, "F1 operators and their order", () ->
                ev("1 + 2 * 3") == 7 && ev("(1 + 2) * 3") == 9 && ev("10 - 4 - 3") == 3
                        && ev("12 / 3 / 2") == 2 && ev("2 ^ 3 ^ 2") == 512 && ev("-2 ^ 2") == -4
                        && ev("x * y + 1") == 7 && ev("1.5 * 2") == 3 && ev("+x") == 3);
        SelfTestHarness.check(lines, tally, "F2 each function", () ->
                ev("min(x, y)") == 2 && ev("max(x, y)") == 3 && ev("clamp(x, 0, 2.5)") == 2.5
                        && ev("floor(2.7)") == 2 && ev("ceil(2.1)") == 3 && ev("round(2.5)") == 3
                        && ev("abs(-x)") == 3 && ev("sqrt(9)") == 3 && ev("log(exp(2))") == 2
                        && ev("if(x - 3, 1, 0)") == 0 && ev("if(x, 1, 0)") == 1 && ev("MAX(x, 1)") == 3);
        SelfTestHarness.check(lines, tally, "F3 a bad formula is refused with a reason", () ->
                refused("") && refused("x +") && refused("(x") && refused("x)") && refused("z * 2")
                        && refused("foo(x)") && refused("min(x)") && refused("1..2") && refused("x $ 2")
                        && Formula.problem("z", Set.of("x")).contains("Use one of: x"));
        SelfTestHarness.check(lines, tally, "F4 division by zero is not finite and falls back to the default", () -> {
            boolean inf = Double.isInfinite(ev("x / 0"));
            List<String> skipped = ScalingFormulas.set(Map.of("mob_xp", "level / 0"));
            double v = ScalingFormulas.eval(Kind.MOB_XP, ScalingFormulas.vars("level", 3, "per_level", 0.15, "exponent", 1.0));
            ScalingFormulas.set(Map.of());
            return inf && skipped.isEmpty() && Math.abs(v - 1.45) < 1e-9;
        });
        SelfTestHarness.check(lines, tally, "F5 a stored row that does not parse keeps the default and is named", () -> {
            List<String> skipped = ScalingFormulas.set(Map.of("area_level", "level *", "nope", "1"));
            boolean kept = ScalingFormulas.get(Kind.AREA_LEVEL).source().equals(Kind.AREA_LEVEL.defaultExpression);
            ScalingFormulas.set(Map.of());
            return kept && skipped.size() == 2;
        });
        SelfTestHarness.check(lines, tally, "F6 a stored formula is used", () -> {
            ScalingFormulas.set(Map.of("area_level", "level + 10"));
            double v = ScalingFormulas.eval(Kind.AREA_LEVEL, ScalingFormulas.vars("level", 3, "local_difficulty", 1, "chunk_scale", 0));
            ScalingFormulas.set(Map.of());
            return v == 13;
        });
        SelfTestHarness.check(lines, tally, "F7 the defaults give the old fixed values, levels 0 to 100", () -> {
            ScalingFormulas.set(Map.of());
            for (int level = 0; level <= 100; level++) {
                for (int start = 1; start <= 5; start++) {
                    int eff = Math.max(0, level - start + 1);
                    for (double exp : new double[] {0.0, 0.6, 1.0, 1.5}) {
                        double old = eff == 0 ? 0.0 : 0.15 * Math.pow(eff, exp) * 1.3;
                        for (Kind k : List.of(Kind.MOB_ATTRIBUTE, Kind.PLAYER_ATTRIBUTE)) {
                            double now = ScalingFormulas.eval(k, ScalingFormulas.vars("base", 20, "level", level,
                                    "start_level", start, "per_level", 0.15, "exponent", exp, "coefficient", 1.3));
                            if (Math.abs(now - old) > 1e-9 * Math.max(1, old)) return false;
                        }
                    }
                }
                double xpOld = level <= 0 ? 1.0 : 1.0 + 5.0 * Math.pow(level, 0.8);
                double xpNow = ScalingFormulas.eval(Kind.MOB_XP, ScalingFormulas.vars("level", level, "per_level", 5.0, "exponent", 0.8));
                if (Math.abs(xpNow - xpOld) > 1e-9 * xpOld) return false;
                for (double local : new double[] {0, 0.75, 6.75}) {
                    for (double rate : new double[] {0, 0.13}) {
                        double areaOld = rate <= 0 || level <= 0 ? level : level * (1.0 + rate * local);
                        double areaNow = ScalingFormulas.eval(Kind.AREA_LEVEL,
                                ScalingFormulas.vars("level", level, "local_difficulty", local, "chunk_scale", rate));
                        if (Math.round(areaNow) != Math.round(areaOld)) return false;
                    }
                }
            }
            return true;
        });

        SelfTestHarness.check(lines, tally, "F8 the editor refuses a bad formula before any write", () -> {
            var report = com.strata.domain.FormulaOps.save(Kind.AREA_LEVEL, "level * chunk");
            return !report.ok() && com.strata.domain.FormulaOps.problem(Kind.AREA_LEVEL, "level * chunk") != null
                    && com.strata.domain.FormulaOps.problem(Kind.AREA_LEVEL, Kind.AREA_LEVEL.defaultExpression) == null
                    && com.strata.domain.FormulaOps.preview(Kind.MOB_XP, "level *").isEmpty()
                    && com.strata.domain.FormulaOps.preview(Kind.MOB_XP, "if(level, 1 + per_level * level ^ exponent, 1)")
                            .startsWith("Lv 1: 1.15");
        });

        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }
}
