package com.strata.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** WHAT ENTITY REPLACEMENT DOES TO A SPAWN, pure. */
public final class ReplacementPlan {

    private ReplacementPlan() {
    }

    /** natural: the biome's own spawns. */
    public static final String NATURAL = "natural";
    public static final String ALL = "all";

    /**
     * One replacement.
     *
     * @param share how many of the from-mob become the to-mob, 1 to 100
     */
    public record Rule(String scope, String scopeId, String from, String to, int share, String sources) {

        /** True when the rule also swaps spawns that are not the biome's own. */
        public boolean everySource() {
            return ALL.equals(sources);
        }
    }

    /** One row of replacements.csv as a rule, or null when it names no from or no to. */
    public static Rule rule(String scope, String scopeId, String from, String to, String share, String sources) {
        String f = clean(from);
        String t = clean(to);
        if (f.isEmpty() || t.isEmpty() || f.equals(t)) {
            return null;
        }
        int pct;
        try {
            pct = share == null || share.isBlank() ? 100 : (int) Math.round(Double.parseDouble(share.strip()));
        } catch (NumberFormatException e) {
            pct = 100;
        }
        String src = ALL.equalsIgnoreCase(clean(sources)) ? ALL : NATURAL;
        return new Rule(clean(scope).isEmpty() ? "global" : clean(scope).toLowerCase(Locale.ROOT), clean(scopeId),
                f, t, Math.max(1, Math.min(100, pct)), src);
    }

    /**
     * The rules in force in one biome, by from-mob: global, then its tier's, then the biome's own, each
     * replacing the one before for the same from-mob - the same precedence every other Strata value has.
     */
    public static Map<String, Rule> rulesFor(List<Rule> all, String tier, String biome) {
        Map<String, Rule> out = new LinkedHashMap<>();
        for (String layer : List.of("global", "tier", "biome")) {
            for (Rule r : all) {
                boolean here = r.scope().equals(layer) && switch (layer) {
                    case "tier" -> r.scopeId().equals(tier == null ? "" : tier);
                    case "biome" -> r.scopeId().equals(biome == null ? "" : biome);
                    default -> true;
                };
                if (here) {
                    out.put(r.from(), r);
                }
            }
        }
        return out;
    }

    /** One spawn entry's share of weight: who spawns, and how often. */
    public record Piece(String entity, int weight) {
    }

    /**
     * A spawn entry of the from-mob, split by its rule: all of its weight to the to-mob at 100, a share of it
     * below.
     */
    public static List<Piece> split(String entity, int weight, Rule rule) {
        List<Piece> out = new ArrayList<>();
        if (rule == null || weight <= 0) {
            out.add(new Piece(entity, weight));
            return out;
        }
        int to = (int) Math.round(weight * rule.share() / 100.0);
        int from = weight - to;
        if (to > 0) {
            out.add(new Piece(rule.to(), to));
        }
        if (from > 0) {
            out.add(new Piece(entity, from));
        }
        if (out.isEmpty()) {
            out.add(new Piece(rule.share() >= 50 ? rule.to() : entity, weight));
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
