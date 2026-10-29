package com.strata.model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The scaling formulas a designer can edit (scaling_formulas.csv). */
public final class ScalingFormulas {

    private ScalingFormulas() {}

    public enum Kind {
        AREA_LEVEL("area_level", "Area level",
                "The level a mob spawns at. local_difficulty is the game's, 0.75 to 6.75 (0 on Peaceful)."
                        + " player_level is the nearest player's XP level, world_stage the World level stage, world_level the world level.",
                "level * (1 + chunk_scale * local_difficulty)",
                "level", "local_difficulty", "chunk_scale", "player_level", "world_stage", "world_level"),
        MOB_ATTRIBUTE("mob_attribute", "Mob attribute bonus",
                "The bonus as a fraction of the base value: 0.5 adds half. Each row of Mob scaling runs it. Divide by base for a flat bonus.",
                "if(level - start_level + 1, per_level * (level - start_level + 1) ^ exponent, 0) * coefficient",
                "base", "level", "start_level", "per_level", "exponent", "coefficient"),
        PLAYER_ATTRIBUTE("player_attribute", "Player attribute bonus",
                "As the mob attribute bonus, for each row of Player scaling.",
                "if(level - start_level + 1, per_level * (level - start_level + 1) ^ exponent, 0) * coefficient",
                "base", "level", "start_level", "per_level", "exponent", "coefficient"),
        MOB_XP("mob_xp", "Mob XP multiplier",
                "Multiplies the XP the game drops for a mob: 1 is the game's.",
                "if(level, 1 + per_level * level ^ exponent, 1)",
                "level", "per_level", "exponent");

        public final String id;
        public final String label;
        public final String help;
        public final String defaultExpression;
        public final Set<String> variables;

        Kind(String id, String label, String help, String defaultExpression, String... variables) {
            this.id = id;
            this.label = label;
            this.help = help;
            this.defaultExpression = defaultExpression;
            this.variables = Set.of(variables);
        }

        /** The variables in the order the editor lists them. */
        public List<String> variableList() {
            return variables.stream().sorted().toList();
        }

        public static Kind of(String id) {
            for (Kind k : values()) {
                if (k.id.equalsIgnoreCase(id == null ? "" : id.strip())) return k;
            }
            return null;
        }
    }

    private static final Map<Kind, Formula> DEFAULTS = new EnumMap<>(Kind.class);
    static {
        for (Kind k : Kind.values()) DEFAULTS.put(k, Formula.parse(k.defaultExpression, k.variables));
    }

    private static volatile Map<Kind, Formula> active = Map.copyOf(DEFAULTS);

    /** The formula in force for a kind. */
    public static Formula get(Kind kind) {
        return active.get(kind);
    }

    /** Puts the stored expressions in force: kind id to expression. */
    public static List<String> set(Map<String, String> stored) {
        Map<Kind, Formula> next = new EnumMap<>(DEFAULTS);
        List<String> skipped = new java.util.ArrayList<>();
        stored.forEach((id, expression) -> {
            Kind k = Kind.of(id);
            if (k == null) {
                skipped.add("unknown formula '" + id + "'");
            } else if (expression != null && !expression.isBlank()) {
                try {
                    next.put(k, Formula.parse(expression, k.variables));
                } catch (Formula.Problem e) {
                    skipped.add(id + ": " + e.getMessage() + " - the default is used");
                }
            }
        });
        active = Map.copyOf(next);
        return skipped;
    }

    /** A formula's value, or the default's when the designer's gives NaN or infinity. */
    public static double eval(Kind kind, Map<String, Double> vars) {
        double v = get(kind).eval(vars);
        return Double.isFinite(v) ? v : DEFAULTS.get(kind).eval(vars);
    }

    /** Variables in a map, for the call sites: name, value, name, value... */
    public static Map<String, Double> vars(Object... pairs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return out;
    }
}
