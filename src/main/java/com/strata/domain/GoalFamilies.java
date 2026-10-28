package com.strata.domain;

import com.strata.data.GameLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** THE MOB FAMILIES, FROM THE GAME. */
public final class GoalFamilies {

    private GoalFamilies() {
    }

    /** The type value that holds a mob's class chain: its class first, then each superclass, by simple name. */
    public static final String CLASS_CHAIN = "class_chain";
    /** The separator in a class chain. */
    public static final String CHAIN_SEP = ">";

    /**
     * One family.
     *
     * @param name the archetype's name: the shared class, as a name ("zombie")
     * @param heading the kind it lists under, nearest ("Spellcaster Illager"); "Other" for none
     * @param path every heading it lists under, broadest first, in words
     * @param mobs its mobs' ids, sorted
     * @param goals its goals as the goal sheet holds them, in order
     */
    public record Family(String name, String heading, List<String> path, List<String> mobs, List<String> goals) {
    }

    /** The families of the game layer as the game gave it at start (game/goals.csv, game/mobs.csv). */
    public static List<Family> fromGameLayer() {
        return families(GameLayer.read(GameLayer.dir().resolve(GameLayer.GOALS)),
                GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS)));
    }

    public static List<Family> families(List<GameLayer.Row> goalSheet, List<GameLayer.Row> mobSheet) {
        Map<String, List<String>> chains = new HashMap<>();
        for (GameLayer.Row r : mobSheet) {
            if (GameLayer.TYPE.equals(r.group()) && CLASS_CHAIN.equals(r.key())) {
                chains.put(r.entity(), List.of(r.value().split(CHAIN_SEP)));
            }
        }
        // Each mob's goals, in list order.
        Map<String, TreeMap<Integer, String>> goalsByMob = new TreeMap<>();
        for (GameLayer.Row r : goalSheet) {
            if (GameLayer.GOAL.equals(r.group())) {
                goalsByMob.computeIfAbsent(r.entity(), k -> new TreeMap<>()).put(order(r.key()), r.value());
            }
        }
        // The mobs with the same AI, together.
        Map<List<String>, List<String>> bySignature = new LinkedHashMap<>();
        goalsByMob.forEach((mob, goals) -> bySignature.computeIfAbsent(List.copyOf(goals.values()),
                k -> new ArrayList<>()).add(mob));

        List<List<String>> chainsOf = new ArrayList<>();
        List<Map.Entry<List<String>, List<String>>> groups = new ArrayList<>(bySignature.entrySet());
        for (var group : groups) {
            chainsOf.add(sharedChain(group.getValue(), chains));
        }
        Map<String, Integer> nameUse = new HashMap<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            String name = goalSetName(chainsOf.get(i), groups.get(i).getValue());
            names.add(name);
            nameUse.merge(name, 1, Integer::sum);
        }
        // "none" is the behavior field's game AI, never an archetype's name.
        java.util.Set<String> taken = new java.util.HashSet<>(java.util.Set.of(com.strata.model.GoalSets.NONE));
        for (int i = 0; i < groups.size(); i++) {
            String name = names.get(i);
            if (nameUse.get(name) == 1 || groups.get(i).getValue().stream().anyMatch(m -> path(m).equals(name))) {
                taken.add(name);
            }
        }
        List<Family> out = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<String> mobs = groups.get(i).getValue();
            String shared = names.get(i);
            String name = shared;
            boolean keeps = (nameUse.get(shared) == 1 || mobs.stream().anyMatch(m -> path(m).equals(shared)))
                    && !com.strata.model.GoalSets.NONE.equals(shared);
            if (com.strata.model.GoalSets.NONE.equals(shared)) {
                name = trim(shared + "_mob");
                taken.add(name);
            } else if (!keeps) {
                // Two families with one shared class: this one by its first mob.
                String own = trim(path(mobs.getFirst()));
                name = taken.add(own) ? own : trim(shared + "_" + path(mobs.getFirst()));
                taken.add(name);
            }
            List<String> path = headingPath(i, chainsOf);
            out.add(new Family(name, path.isEmpty() ? "Other" : path.getLast(), path, mobs,
                    groups.get(i).getKey()));
        }
        out.sort(java.util.Comparator.comparing((Family f) -> String.join("/", f.path()))
                .thenComparing(Family::name));
        return out;
    }

    /** A family's goals as AI rows, as the game runs them. */
    public static List<com.strata.model.GoalRow> rows(Family family) {
        return GoalsOps.fromGame(family.goals().stream().map(com.strata.model.GameGoal::decode).toList());
    }

    /** The families the library does not hold yet, by name, with their rows as the game runs them. */
    public static Map<String, List<com.strata.model.GoalRow>> missing(List<Family> families,
                                                                     java.util.Collection<String> existing) {
        Map<String, List<com.strata.model.GoalRow>> out = new LinkedHashMap<>();
        for (Family f : families) {
            if (!existing.contains(f.name())) {
                out.put(f.name(), rows(f));
            }
        }
        return out;
    }

    /**
     * The groups each family lists under in the Behaviors menu, from its path, tidied: a
     * group that holds more than three in four of all families says nothing and is dropped ("Pathfinder
     * Mob"); a group with one group under it and no family of its own folds into it ("Patrolling Monster"
     * above "Raider").
     */
    public static Map<String, List<String>> displayPaths(List<Family> families) {
        Map<String, List<String>> paths = new LinkedHashMap<>();
        Map<List<String>, Integer> holders = new HashMap<>();
        for (Family f : families) {
            for (int i = 1; i <= f.path().size(); i++) {
                holders.merge(List.copyOf(f.path().subList(0, i)), 1, Integer::sum);
            }
        }
        for (Family f : families) {
            List<String> kept = new ArrayList<>();
            for (int i = 1; i <= f.path().size(); i++) {
                if (holders.get(List.copyOf(f.path().subList(0, i))) * 4 <= families.size() * 3) {
                    kept.add(f.path().get(i - 1));
                }
            }
            paths.put(f.name(), kept);
        }
        boolean folded = true;
        while (folded) {
            folded = false;
            Map<List<String>, java.util.Set<String>> children = new HashMap<>();
            Map<List<String>, Integer> direct = new HashMap<>();
            for (List<String> p : paths.values()) {
                direct.merge(p, 1, Integer::sum);
                for (int i = 0; i < p.size(); i++) {
                    children.computeIfAbsent(List.copyOf(p.subList(0, i)), k -> new java.util.HashSet<>()).add(p.get(i));
                }
            }
            for (var node : children.entrySet()) {
                List<String> prefix = node.getKey();
                if (!prefix.isEmpty() && node.getValue().size() == 1 && !direct.containsKey(prefix)) {
                    // Fold this group into its only child group.
                    for (var e : paths.entrySet()) {
                        List<String> p = e.getValue();
                        if (p.size() > prefix.size() && p.subList(0, prefix.size()).equals(prefix)) {
                            List<String> shorter = new ArrayList<>(p);
                            shorter.remove(prefix.size() - 1);
                            e.setValue(shorter);
                        }
                    }
                    folded = true;
                    break;
                }
            }
        }
        return paths;
    }

    /** Each mob's family, by the mob's id. */
    public static Map<String, String> familyByMob(List<Family> families) {
        Map<String, String> out = new HashMap<>();
        families.forEach(f -> f.mobs().forEach(m -> out.put(m, f.name())));
        return out;
    }

    /** The classes every mob of the group has, nearest first; its first mob's own when a mob has no chain. */
    private static List<String> sharedChain(List<String> mobs, Map<String, List<String>> chains) {
        List<String> first = chains.getOrDefault(mobs.getFirst(), List.of());
        List<String> shared = new ArrayList<>();
        for (String c : first) {
            boolean all = mobs.stream().allMatch(m -> chains.getOrDefault(m, List.of()).contains(c));
            if (all) {
                shared.add(c);
            }
        }
        return shared;
    }

    /** The name: a lone mob's own; else the nearest shared class, as an archetype name. */
    private static String goalSetName(List<String> shared, List<String> mobs) {
        return mobs.size() == 1 || shared.isEmpty() ? trim(path(mobs.getFirst())) : trim(snake(shared.getFirst()));
    }

    /**
     * The headings: the classes of this family's shared chain that another family's chain holds too, but not
     * every family's, broadest first, in words.
     */
    private static List<String> headingPath(int i, List<List<String>> chainsOf) {
        List<String> out = new ArrayList<>();
        for (String c : chainsOf.get(i)) {
            int holders = 0;
            for (List<String> other : chainsOf) {
                if (other.contains(c)) {
                    holders++;
                }
            }
            if (holders >= 2 && holders < chainsOf.size()) {
                out.addFirst(words(c));
            }
        }
        return out;
    }

    /** "AbstractIllager" as words: "Illager". */
    public static String words(String className) {
        return className.replaceFirst("^Abstract", "").replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    }

    /** "AbstractSkeleton" as a goal set name: "skeleton"; "ZombieVillager": "zombie_villager". */
    public static String snake(String className) {
        return words(className).replace(' ', '_').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1).replaceAll("[^a-z0-9_\\-]", "_");
    }

    /** An archetype name is at most 40 characters (GoalsOps#nameProblem). */
    private static String trim(String name) {
        String n = name.isEmpty() ? "family" : name;
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    private static int order(String key) {
        try {
            return Integer.parseInt(key.strip());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
