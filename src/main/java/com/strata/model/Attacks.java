package com.strata.model;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** HOW A LIST FIGHTS. */
public final class Attacks {

    /** The ways a list hurts a target, in the order the editor names them. */
    public enum Kind {
        MELEE("melee"), BOW("bow"), CROSSBOW("crossbow"), RANGED("ranged"), EXPLODES("explodes"),
        OWN("own attack");

        /** The word the editor shows. */
        public final String word;

        Kind(String word) {
            this.word = word;
        }
    }

    /** The game's attack goals, by simple name, and the way each one hurts. */
    private static final Map<String, Kind> STANDARD = Map.of(
            "MeleeAttackGoal", Kind.MELEE, "OcelotAttackGoal", Kind.MELEE,
            "RangedBowAttackGoal", Kind.BOW, "RangedCrossbowAttackGoal", Kind.CROSSBOW,
            "RangedAttackGoal", Kind.RANGED, "SwellGoal", Kind.EXPLODES);

    /** No game layer: built rows by name, no own attacks. */
    public static final Attacks NONE = new Attacks(Map.of(), Set.of());

    /** Each game goal's class chain, by its exact class name. */
    private final Map<String, List<String>> chains;
    /** The exact class names of the mobs' own attacks. */
    private final Set<String> own;

    private Attacks(Map<String, List<String>> chains, Set<String> own) {
        this.chains = Map.copyOf(chains);
        this.own = Set.copyOf(own);
    }

    /**
     * Reads every mob's game goals: the chains, and the own attacks.
     *
     * @param goalsByMob each mob's goals as the game layer holds them
     */
    public static Attacks of(Map<String, List<GameGoal>> goalsByMob) {
        Map<String, List<String>> chains = new HashMap<>();
        Set<String> candidates = new HashSet<>();
        Set<String> withStandard = new HashSet<>();
        for (List<GameGoal> goals : goalsByMob.values()) {
            boolean targets = goals.stream().anyMatch(GameGoal::target);
            boolean standard = goals.stream().anyMatch(g -> !g.target() && standard(g.chain()) != null);
            for (GameGoal g : goals) {
                String key = key(g);
                if (key.isEmpty()) {
                    continue;
                }
                chains.put(key, g.chain());
                if (g.target() || g.buildable() || standard(g.chain()) != null) {
                    continue;
                }
                if (standard) {
                    withStandard.add(key);
                } else if (targets) {
                    candidates.add(key);
                }
            }
        }
        candidates.removeAll(withStandard);
        return new Attacks(chains, candidates);
    }

    /** A goal's key: the exact class name a GAME row carries, or the simple name. */
    private static String key(GameGoal g) {
        return g.className().isEmpty() ? g.name() : g.className();
    }

    /** The way the first standard attack in a chain hurts, or null. */
    private static Kind standard(List<String> chain) {
        for (String name : chain) {
            Kind kind = STANDARD.get(name);
            if (kind != null) {
                return kind;
            }
        }
        return null;
    }

    /** How a row hurts a target, or null for a row that does not. */
    public Kind kind(GoalRow r) {
        if (r.target()) {
            return null;
        }
        if (!r.game()) {
            return STANDARD.get(r.goal());
        }
        Kind kind = standard(chains.getOrDefault(r.goal(), List.of(r.goal().substring(r.goal().lastIndexOf('.') + 1))));
        return kind != null ? kind : own.contains(r.goal()) ? Kind.OWN : null;
    }

    /** The ways the switched-on rows hurt a target, each once, in the editor's order. */
    public List<Kind> kinds(List<GoalRow> rows) {
        EnumSet<Kind> out = EnumSet.noneOf(Kind.class);
        for (GoalRow r : rows) {
            Kind kind = r.enabled() ? kind(r) : null;
            if (kind != null) {
                out.add(kind);
            }
        }
        return List.copyOf(out);
    }

    /** True when a list fights: a switched-on WHO goal, or an attack. */
    public boolean fights(List<GoalRow> rows) {
        return rows.stream().anyMatch(r -> r.enabled() && r.target()) || !kinds(rows).isEmpty();
    }

    /**
     * The Behaviors list's Fights cell: the kinds joined by "+" ("melee+bow"), "missing" for a list that
     * picks targets with no attack, or "no".
     */
    public String column(List<GoalRow> rows) {
        List<Kind> kinds = kinds(rows);
        if (kinds.isEmpty()) {
            return rows.stream().anyMatch(r -> r.enabled() && r.target()) ? "missing" : "no";
        }
        return String.join("+", kinds.stream().map(k -> k == Kind.OWN ? "own" : k.word).toList());
    }

    /**
     * The line over the AI editor's bar, and whether it warns.
     *
     * @param text what the list fights with, and what is wrong
     * @param warning true when the mob cannot fight as the list stands
     */
    public record Line(String text, boolean warning) {
    }

    /**
     * What the list fights with, for the AI editor.
     *
     * @param forMob true on a mob's list: it says whether the mob can melee
     * @param damage the mob's attack damage from the game layer, or null when it has none
     */
    public Line line(List<GoalRow> rows, boolean forMob, Double damage) {
        boolean targets = rows.stream().anyMatch(r -> r.enabled() && r.target());
        List<Kind> kinds = kinds(rows);
        String melee = !forMob ? ""
                : damage != null ? " Can melee: attack damage " + number(damage) + "."
                : " Cannot melee: no attack damage.";
        if (targets && kinds.isEmpty()) {
            return new Line("This mob is missing an attack: it picks targets but never hurts them."
                    + " Add melee attack, or a ranged goal.", true);
        }
        if (kinds.isEmpty()) {
            return new Line("Fights: no. It picks no targets." + melee, false);
        }
        String with = "Fights with: " + String.join(", ", kinds.stream().map(k -> k.word).toList()) + ".";
        if (!targets) {
            return new Line(with + " It picks no targets, so it never attacks. Add a target goal.", true);
        }
        if (forMob && damage == null && kinds.contains(Kind.MELEE)) {
            return new Line(with + " Its melee hurts nothing: this mob has no attack damage.", true);
        }
        if (kinds.stream().allMatch(k -> k == Kind.BOW || k == Kind.CROSSBOW)) {
            return new Line(with + " It needs that weapon in hand. To fight with a sword, add melee attack.", false);
        }
        return new Line(with + melee, false);
    }

    /** 2.0 as "2", 2.5 as "2.5". */
    private static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
