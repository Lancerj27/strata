package com.strata.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** WHAT BLOCK REPLACEMENT DOES TO NEWLY GENERATED TERRAIN, pure. */
public final class BlockSwap {

    private BlockSwap() {
    }

    /** The to-block that means "remove it": the runtime puts air there. */
    public static final String DISABLED = "disabled";

    /** The block id air has, which is never replaced (there is nothing to take out). */
    public static final String AIR = "minecraft:air";

    /**
     * One replacement.
     *
     * @param scope global, tier, or biome
     * @param scopeId the tier's name or the biome's id; blank for global
     * @param from the block id replaced
     * @param to the block id put there, or {@link #DISABLED}
     */
    public record Rule(String scope, String scopeId, String from, String to) {

        /** True when the rule takes the block out (air) rather than putting another there. */
        public boolean disables() {
            return DISABLED.equals(to);
        }
    }

    /** One row of block_replacements.csv as a rule, or null when it names no from or no to, or names itself. */
    public static Rule rule(String scope, String scopeId, String from, String to) {
        String f = clean(from);
        String t = clean(to);
        if (f.isEmpty() || t.isEmpty() || f.equals(t) || AIR.equals(f)) {
            return null;
        }
        String s = clean(scope).isEmpty() ? "global" : clean(scope).toLowerCase(Locale.ROOT);
        return new Rule(s, "global".equals(s) ? "" : clean(scopeId), f,
                DISABLED.equalsIgnoreCase(t) ? DISABLED : t);
    }

    /**
     * The rules in force in one biome, by from-block: global, then its tier's, then the biome's own, each
     * replacing the one before for the same from-block.
     */
    public static Map<String, String> rulesFor(List<Rule> all, String tier, String biome) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String layer : List.of("global", "tier", "biome")) {
            for (Rule r : all) {
                boolean here = r.scope().equals(layer) && switch (layer) {
                    case "tier" -> !clean(tier).isEmpty() && r.scopeId().equals(clean(tier));
                    case "biome" -> r.scopeId().equals(clean(biome));
                    default -> true;
                };
                if (here) {
                    out.put(r.from(), r.to());
                }
            }
        }
        return out;
    }

    /** Every block id any rule replaces: what a section's palette is asked about. */
    public static Set<String> sources(Collection<Rule> rules) {
        Set<String> out = new LinkedHashSet<>();
        for (Rule r : rules) {
            out.add(r.from());
        }
        return out;
    }

    /**
     * Whether a section needs its blocks looked at: its palette (the block ids it holds) has one of the
     * replaced blocks.
     */
    public static boolean needsScan(Set<String> sources, Collection<String> palette) {
        if (sources.isEmpty()) {
            return false;
        }
        for (String id : palette) {
            if (sources.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** Why a rule cannot be added to the rules there are, or null. */
    public static String problem(String from, String to, Collection<Rule> existing, String scope, String scopeId) {
        String f = clean(from);
        String t = clean(to);
        if (f.isEmpty()) return "Pick the block to replace";
        if (t.isEmpty()) return "Pick the block to put there, or Disabled";
        if (AIR.equals(f)) return "Air cannot be replaced: there is nothing to take out";
        if (f.equals(t)) return "A block cannot replace itself";
        // The chain and swap rule: no block is both replaced and a replacement, in any scope.
        for (Rule r : existing) {
            boolean same = r.scope().equals(clean(scope)) && r.scopeId().equals(clean(scopeId)) && r.from().equals(f);
            if (same) {
                continue;  // this save replaces that rule
            }
            if (r.to().equals(f)) {
                return nameOf(f) + " is already what " + nameOf(r.from()) + " becomes. A block cannot be both";
            }
            if (r.from().equals(t)) {
                return nameOf(t) + " is replaced by another rule, so it cannot be a replacement";
            }
        }
        return null;
    }

    private static String nameOf(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** The rules as a list, from rows of values (scope, scope_id, from, to), skipping the unusable. */
    public static List<Rule> rules(Collection<String[]> rows) {
        List<Rule> out = new ArrayList<>();
        for (String[] r : rows) {
            Rule rule = rule(r[0], r[1], r[2], r[3]);
            if (rule != null) {
                out.add(rule);
            }
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
