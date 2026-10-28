package com.strata.client.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The mobs this session has been working on, newest first. */
public final class RecentEdits {

    private RecentEdits() {
    }

    /** How many are kept. */
    private static final int LIMIT = 8;

    /** Separator that cannot occur in an entity id or a variant name. */
    private static final String SEP = "\u0000";

    /** Insertion-ordered, so the iteration order IS the recency order. */
    private static final Set<String> RECENT = new LinkedHashSet<>();

    /** Records a mob as just worked on. */
    public static void touch(String entity, String variantName) {
        if (entity == null || entity.isBlank()) {
            return;
        }
        touchIn(RECENT, key(entity, variantName));
    }

    /** True when this mob is in the recent set. */
    public static boolean contains(String entity, String variantName) {
        return RECENT.contains(key(entity, variantName));
    }

    /** The recent keys, MOST RECENT FIRST. */
    public static List<String> newestFirst() {
        return newestFirst(RECENT);
    }

    /** The mobs worked on, as entity ids, newest first, each once: a variant counts as its base mob. */
    public static List<String> recentEntities() {
        return entitiesOf(newestFirst());
    }

    /** The entity ids in recent keys, in order, each once. */
    public static List<String> entitiesOf(List<String> keys) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : keys) {
            int sep = key.indexOf(SEP);
            String entity = sep < 0 ? key : key.substring(0, sep);
            if (!entity.isBlank()) {
                out.add(entity);
            }
        }
        return new ArrayList<>(out);
    }

    /** Where a mob sits in the recent order; lower is newer. */
    public static int rank(String entity, String variantName) {
        return newestFirst().indexOf(key(entity, variantName));
    }

    private static String key(String entity, String variantName) {
        return (entity == null ? "" : entity.strip()) + SEP
                + (variantName == null ? "" : variantName.strip());
    }

    // ---- every other list: one set for each kind ---- A kind is the list's own name (its screen class, as
    // BrowseList#setRecent takes it), so two lists never share a set.

    private static final java.util.Map<String, Set<String>> BY_KIND = new java.util.HashMap<>();

    /** Records a row of a kind of list as just worked on. */
    public static void touchRow(String kind, String id) {
        if (kind == null || kind.isBlank()) {
            return;
        }
        touchIn(BY_KIND.computeIfAbsent(kind, k -> new LinkedHashSet<>()), id);
    }

    /** The recent rows of a kind of list, newest first. */
    public static List<String> recentRows(String kind) {
        Set<String> set = BY_KIND.get(kind);
        return set == null ? List.of() : newestFirst(set);
    }

    /** Records a tier as just worked on. */
    public static void touchTier(String name) {
        touchRow("tier", name);
    }

    /** Records a biome as just worked on. */
    public static void touchBiome(String biomeId) {
        touchRow("biome", biomeId);
    }

    /** The recent tiers, newest first. */
    public static List<String> recentTiers() {
        return recentRows("tier");
    }

    /** The recent biomes, newest first. */
    public static List<String> recentBiomes() {
        return recentRows("biome");
    }

    /** Records a behavior as just saved. */
    public static void touchGoalSet(String name) {
        touchRow("goal_set", name);
    }

    /** The recent behaviors, newest first. */
    public static List<String> recentGoalSets() {
        return recentRows("goal_set");
    }

    private static void touchIn(Set<String> set, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String key = id.strip();
        set.remove(key);
        set.add(key);
        while (set.size() > LIMIT) {
            set.remove(set.iterator().next());
        }
    }

    private static List<String> newestFirst(Set<String> set) {
        List<String> out = new ArrayList<>(set);
        java.util.Collections.reverse(out);
        return out;
    }
}
