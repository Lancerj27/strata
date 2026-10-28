package com.strata.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The spawner entries Strata sets in one biome. */
public final class SpawnPlan {

    private SpawnPlan() {
    }

    /**
     * One spawner entry a biome gets for a mob.
     *
     * @param weight 0 when the mob spawns there no more
     */
    public record Entry(int weight, int min, int max) {
    }

    /**
     * The entries by spawner key (the entity id the entry spawns).
     *
     * @param declared every spawn the resolver declares for the biome, the game's own among them (they are
     *     not planned: the biome already has them)
     * @param keyOf a spawn's spawner key: a variant with its own entity has its own, any other its base mob's
     * @param existing what the biome gives a key now: {weight, min, max}
     */
    public static Map<String, Entry> plan(List<Spawn> declared, Function<Spawn, String> keyOf,
                                          Function<String, int[]> existing) {
        Map<String, List<Spawn>> byKey = new LinkedHashMap<>();
        for (Spawn s : declared) {
            if (s.source().layer() != Source.Layer.GAME) {
                byKey.computeIfAbsent(keyOf.apply(s), k -> new ArrayList<>()).add(s);
            }
        }
        Map<String, Entry> out = new LinkedHashMap<>();
        byKey.forEach((key, rows) -> {
            Spawn plain = rows.stream().filter(s -> !s.isVariant()).reduce((a, b) -> b).orElse(null);
            long weight;
            if (plain != null) {
                weight = plain.enabled() && plain.weight() > 0 ? plain.weight() : 0;
            } else {
                int[] now = existing.apply(key);
                weight = now == null ? 0 : now[0];
            }
            for (Spawn s : rows) {
                if (s.isVariant() && s.enabled() && s.weight() > 0) {
                    weight += s.weight();
                }
            }
            out.put(key, new Entry((int) Math.min(weight, Spawn.MAX_WEIGHT), 1, 1));
        });
        return out;
    }

    // ---- WEIGHT, THEN SPAWN CHANCE, THEN GROUP SIZE ----

    /**
     * True when Strata rolls this mob's group: it has a row that is not the game's own (the biome's entry for
     * it is Strata's, #plan).
     */
    public static boolean groupsByStrata(List<Spawn> rows) {
        return rows.stream().anyMatch(s -> s.source().layer() != Source.Layer.GAME);
    }

    /**
     * The row a spawn of this mob is, by weight among its enabled rows (the plain mob and its variants
     * without an entity share one entry), or null when none has weight.
     *
     * @param roll a uniform value in [0, 1)
     */
    public static Spawn pickRow(List<Spawn> rows, double roll) {
        long total = 0;
        for (Spawn s : rows) {
            if (s.enabled() && s.weight() > 0) {
                total += s.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        double at = roll * total;
        Spawn last = null;
        for (Spawn s : rows) {
            if (s.enabled() && s.weight() > 0) {
                last = s;
                at -= s.weight();
                if (at < 0) {
                    return s;
                }
            }
        }
        return last;
    }

    /** True when a spawn picked by weight goes ahead at this chance (0 to 100). */
    public static boolean passesChance(double chance, double roll) {
        return roll * 100 < chance;
    }

    /**
     * The group's size, from Min to Max, each size as likely (as the game rolls it); 0 spawns nothing.
     *
     * @param roll a uniform value in [0, 1)
     */
    public static int groupSize(int min, int max, double roll) {
        int lo = Math.max(0, Math.min(min, max));
        int hi = Math.max(lo, max);
        return lo + Math.min(hi - lo, (int) Math.floor(roll * (hi - lo + 1)));
    }

    /**
     * The spawner keys a biome loses.
     *
     * @param biomeKeys the keys the biome has now
     * @param planned the keys the plan sets
     */
    public static List<String> dropped(List<String> biomeKeys, java.util.Set<String> planned, boolean replaces) {
        if (!replaces) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String key : biomeKeys) {
            if (!planned.contains(key) && !out.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * Whether a spawn may go ahead in a Replace biome.
     *
     * @param worldSpawn the game spawned it on its own: natural, a new chunk, a patrol, a structure's
     *     inhabitants
     * @param rows the list's rows that spawn as this mob
     */
    public static boolean allowedInReplace(boolean replaces, boolean worldSpawn, List<Spawn> rows) {
        return !replaces || !worldSpawn || !rows.isEmpty();
    }

    /**
     * The spawn slots a Replace biome's hostile mobs also take.
     *
     * @param takes whether a category's switch lets the hostile mobs take it
     * @param entries each category's entry count after the list is applied
     * @param hostile the hostile category, never returned
     */
    public static <C> List<C> openSlots(java.util.function.Predicate<C> takes, boolean replaces,
                                        Map<C, Integer> entries, C hostile) {
        List<C> out = new ArrayList<>();
        if (replaces) {
            entries.forEach((category, count) -> {
                if (!category.equals(hostile) && count == 0 && takes.test(category)) {
                    out.add(category);
                }
            });
        }
        return out;
    }

    /**
     * Whether a spawn check lets a hostile mob spawn in any light: the option, a Replace biome, a hostile mob
     * on its list, and a spawn the world makes (natural or a new chunk's).
     */
    public static boolean anyLight(boolean option, boolean replaces, boolean hostile, boolean worldSpawn,
                                   boolean listed) {
        return option && replaces && hostile && worldSpawn && listed;
    }

    /**
     * Whether a hostile spawn is rolled again among the biome's list.
     *
     * @param natural the game spawned it on its own, from the biome's spawners
     * @param configured a tier, a biome row, or a Strata spawn row speaks for the biome
     * @param listed the mob has a row on the biome's list
     */
    public static boolean substitutes(boolean natural, boolean configured, boolean listed) {
        return natural && configured && listed;
    }

    /**
     * A biome's spawner entries as they were before Strata changed them.
     *
     * @param live the biome's entries now
     * @param keyOf an entry's key (its entity type)
     * @param replaced the entries Strata took out, by key
     */
    public static <T, K> List<T> unmodified(List<T> live, Function<T, K> keyOf, Map<K, List<T>> replaced) {
        List<T> out = new ArrayList<>();
        for (T t : live) {
            if (!replaced.containsKey(keyOf.apply(t))) {
                out.add(t);
            }
        }
        replaced.values().forEach(out::addAll);
        return out;
    }

    /**
     * THE LIST A BIOME ENTRY BELONGS IN (a mob's category): a mob whose category the designer changed moves
     * out of the list the game put it in and into its new category's, so it fills that slot and no longer the
     * old one.
     *
     * @param entity the entry's entity id
     * @param listCategory the category list it is in now (monster, creature...)
     * @param edited each edited mob's category (GameLayer#editedCategories)
     * @return the category list to hold it, or "" to drop it
     */
    public static String listFor(String entity, String listCategory, Map<String, String> edited) {
        String category = edited.get(entity);
        if (category == null || category.equals(listCategory)) {
            return listCategory;
        }
        return "misc".equals(category) ? "" : category;
    }
}
