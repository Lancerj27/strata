package com.strata.game;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.GameSnapshot;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.model.Source;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;
import com.strata.model.Tier;
import com.strata.runtime.VariantEntityRegistry;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The rows of the mob list, for each of its views. */
public final class MobCatalog {

    private MobCatalog() {
    }

    /**
     * One mob-list row.
     *
     * @param changed true when the designer customised this creature beyond listing it: its drops, equipment,
     *     abilities, stats, or spawn conditions
     */
    public record MobEntry(String entity, String variantName, int weight, boolean enabled,
                           Source source, SpawnCategory category, boolean changed) {
    }

    /**
     * The rows for one view of the list.
     *
     * @param scope "" for the Global view, else "tier" or "biome"
     * @param scopeId the tier or biome, or "" for a grouped view
     * @param worldRegistries the open world's registry access, or null
     */
    public static List<MobEntry> entries(String scope, String scopeId, RegistryAccess worldRegistries) {
        Set<String> edited = scanEdited();
        boolean scoped = !scope.isEmpty();
        // Live-fact resolution works with an open world OR the startup pack-built worldgen registries bound
        // to Config, so the title-screen browser shows real spawns before a world loads; CSV is the
        // last-resort fallback when neither exists.
        RegistryAccess registries = worldRegistries != null ? worldRegistries : Config.boundRegistries();

        // A TIER OWNS ONLY THE ROWS A DESIGNER DECLARED ON IT, so the tier browser reads the tier itself and
        // never the master entity list.
        if ("tier".equals(scope) && !scopeId.isEmpty()) {
            return forTier(scopeId, edited);
        }
        // EVERY TIER AT ONCE, grouped by tier in the list.
        if ("tier".equals(scope)) {
            List<MobEntry> rows = new ArrayList<>();
            for (String tierName : Config.tables().tiers().keySet()) {
                rows.addAll(forTier(tierName, edited));
            }
            return rows;
        }
        // EVERY BIOME THAT HAS DECLARED ROWS, or one biome's.
        if ("biome".equals(scope)) {
            return declaredForBiomes(scopeId, edited);
        }
        if (!scoped) {
            // THE GLOBAL VIEW LISTS EVERY MOB THE GAME HAS: the designer's rows, then each game mob they do
            // not cover.
            List<MobEntry> declared = registries != null ? fromResolver(registries, edited)
                    : fromCsv(scope, scopeId, false, edited);
            return withGameMobs(declared, gameMobs(), edited, EntityCategory::of);
        }
        return fromCsv(scope, scopeId, scoped, edited);
    }

    /**
     * The declared rows, then a GAME row for each game mob no row covers: its highest natural spawn weight, 0
     * when it spawns nowhere.
     */
    public static List<MobEntry> withGameMobs(List<MobEntry> declared, Map<String, Integer> gameMobs,
                                              Set<String> edited,
                                              java.util.function.Function<String, SpawnCategory> category) {
        List<MobEntry> out = new ArrayList<>(declared);
        Set<String> covered = new LinkedHashSet<>();
        declared.forEach(e -> covered.add(key(e.entity(), e.variantName())));
        gameMobs.forEach((entity, weight) -> {
            if (!covered.contains(key(entity, ""))) {
                out.add(new MobEntry(entity, "", weight, true, Source.GAME, category.apply(entity),
                        edited.contains(key(entity, ""))));
            }
        });
        return out;
    }

    private static volatile Map<String, Integer> gameMobCache;
    private static volatile long gameMobStamp = -1;

    /**
     * Every mob in the game layer (an entity whose class is a Mob), to its highest natural spawn weight in
     * any biome.
     */
    private static Map<String, Integer> gameMobs() {
        java.nio.file.Path file = GameLayer.dir().resolve(GameLayer.MOBS);
        long stamp;
        try {
            stamp = java.nio.file.Files.getLastModifiedTime(file).toMillis();
        } catch (java.io.IOException e) {
            return Map.of();
        }
        if (gameMobCache != null && stamp == gameMobStamp) {
            return gameMobCache;
        }
        Map<String, Integer> weights = new java.util.HashMap<>();
        GameSnapshot.get().values().forEach(entry -> entry.spawns().forEach(s ->
                weights.merge(s.entity(), s.weight(), Math::max)));
        Map<String, Integer> out = new java.util.TreeMap<>();
        for (GameLayer.Row r : GameLayer.read(file)) {
            if (GameLayer.TYPE.equals(r.group()) && "class".equals(r.key()) && isMob(r.value())) {
                String entityId = r.entity();
                // PROMOTED VARIANTS ARE NOT GAME MOBS: they are registered under strata:<name> and appear in
                // the mob list as variants under their base mob (e.g. minecraft:skeleton:big_guy), not as
                // separate game mobs.
                if (VariantEntityRegistry.isPromotedEntityId(entityId)) {
                    continue;
                }
                out.put(entityId, weights.getOrDefault(entityId, 0));
            }
        }
        gameMobCache = Map.copyOf(out);
        gameMobStamp = stamp;
        return gameMobCache;
    }

    /** A structure a mob spawns in, and its highest weight there. */
    public record StructurePlace(String structure, int weight) {
    }

    /**
     * The structures each mob spawns in from a structure's own spawn list, heaviest first (a nether fortress:
     * blaze, wither skeleton).
     */
    public static Map<String, List<StructurePlace>> structurePlaces(List<GameSnapshot.StructureSpawn> spawns) {
        Map<String, Map<String, Integer>> best = new java.util.TreeMap<>();
        for (GameSnapshot.StructureSpawn s : spawns) {
            best.computeIfAbsent(s.spawn().entity(), k -> new java.util.LinkedHashMap<>())
                    .merge(s.structure(), s.spawn().weight(), Math::max);
        }
        Map<String, List<StructurePlace>> out = new java.util.TreeMap<>();
        best.forEach((entity, byStructure) -> {
            List<StructurePlace> places = new ArrayList<>();
            byStructure.forEach((structure, weight) -> places.add(new StructurePlace(structure, weight)));
            places.sort(java.util.Comparator.comparingInt(StructurePlace::weight).reversed()
                    .thenComparing(StructurePlace::structure));
            out.put(entity, List.copyOf(places));
        });
        return out;
    }

    /** The structures a mob spawns in, from the game layer, heaviest first. */
    public static List<StructurePlace> structuresOf(String entity) {
        return structurePlaces(GameSnapshot.structureSpawns()).getOrDefault(entity, List.of());
    }

    private static boolean isMob(String className) {
        try {
            return net.minecraft.world.entity.Mob.class.isAssignableFrom(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Every (entity, variant) the designer customised, in any table. */
    /** True when a spawn_rules.csv row is exactly one of the game's own conditions. */
    static boolean isGameRule(Map<String, String> row, List<String[]> gameRules) {
        String[] cols = {Schema.SpawnRules.SCOPE, Schema.SpawnRules.SCOPE_ID, Schema.SpawnRules.ENTITY,
                Schema.SpawnRules.VARIANT_NAME, Schema.SpawnRules.RULE_TYPE, Schema.SpawnRules.VALUE};
        for (String[] g : gameRules) {
            boolean same = true;
            for (int i = 0; i < cols.length && same; i++) {
                same = g[i].equals(row.getOrDefault(cols[i], "").strip());
            }
            if (same) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> scanEdited() {
        Set<String> keys = new LinkedHashSet<>();
        for (var row : CsvTable.read(Paths.table(Schema.Loot.FILE))) {
            keys.add(key(row.getOrDefault(Schema.Loot.ENTITY, ""),
                    row.getOrDefault(Schema.Loot.VARIANT_NAME, "")));
        }
        // The game's own spawn conditions, seeded at start (phantoms, patrols), are not an edit.
        List<String[]> gameRules = com.strata.runtime.SpecialSpawnsRuntime.gameRuleRows();
        for (var row : CsvTable.read(Paths.table(Schema.SpawnRules.FILE))) {
            if (isGameRule(row, gameRules)) {
                continue;
            }
            keys.add(key(row.getOrDefault(Schema.SpawnRules.ENTITY, ""),
                    row.getOrDefault(Schema.SpawnRules.VARIANT_NAME, "")));
        }
        // An edit to an extracted value names its entity and, for a variant, the variant.
        for (var row : CsvTable.read(Paths.table(Schema.MobData.FILE))) {
            keys.add(key(row.getOrDefault(Schema.MobData.ENTITY, ""),
                    row.getOrDefault(Schema.MobData.VARIANT_NAME, "")));
        }
        // A base-stat override has no variant column: it changes the creature itself, so it marks the plain
        // row for that entity.
        for (var row : CsvTable.read(Paths.table(Schema.MobBaseStats.FILE))) {
            keys.add(key(row.getOrDefault(Schema.MobBaseStats.ENTITY, ""), ""));
        }
        // Variant-keyed tables name no entity, so they are resolved through the variant's own base entity.
        for (String file : List.of(Schema.Equipment.FILE, Schema.Abilities.FILE,
                Schema.VariantStats.FILE, Schema.MobGoals.FILE, Schema.MobGoalSets.FILE)) {
            for (var row : CsvTable.read(Paths.table(file))) {
                String variant = row.getOrDefault(Schema.Equipment.VARIANT_NAME, "").strip();
                if (variant.isEmpty()) {
                    continue;
                }
                var definition = Config.tables().variants().get(variant);
                if (definition != null) {
                    keys.add(key(definition.baseEntity(), variant));
                }
            }
        }
        return keys;
    }

    private static String key(String entity, String variantName) {
        return entity.strip() + "\u0000" + (variantName == null ? "" : variantName.strip());
    }

    /** The mobs ADDED TO THIS TIER — the tier's own declared rows and nothing else. */
    private static List<MobEntry> forTier(String tierName, Set<String> edited) {
        Tier tier = Config.tables().tiers().get(tierName);
        if (tier == null) {
            return List.of();
        }
        List<MobEntry> rows = new ArrayList<>();
        for (SpawnCategory category : SpawnCategory.values()) {
            var mobsMap = tier.mobs();
            var list = mobsMap.getOrDefault(category, List.of());
            for (Spawn spawn : list) {
                rows.add(new MobEntry(spawn.entity(), spawn.variant(), spawn.weight(),
                        spawn.enabled(), spawn.source(), category,
                        edited.contains(key(spawn.entity(), spawn.variant()))));
            }
        }
        return rows;
    }

    /** The mobs a designer PUT IN a biome — one biome when named, every biome that has any when blank. */
    private static List<MobEntry> declaredForBiomes(String biomeIdFilter, Set<String> edited) {
        String wanted = biomeIdFilter == null ? "" : biomeIdFilter.strip();
        List<MobEntry> rows = new ArrayList<>();
        for (var row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (!"biome".equals(row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip())) continue;
            if (!Schema.MobSpawns.isDesignerRow(row)) continue;
            String biomeId = row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip();
            String entity = row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip();
            if (biomeId.isEmpty() || entity.isEmpty()) continue;
            if (!wanted.isEmpty() && !wanted.equals(biomeId)) continue;
            String variantName = row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip();
            rows.add(new MobEntry(entity, variantName,
                    // 1, as Tables reads it: two readers of one cell agree.
                    Parse.integer(row.get(Schema.MobSpawns.WEIGHT), 1),
                    Parse.flag(row.get(Schema.MobSpawns.ENABLED), true),
                    new Source(Source.Layer.BIOME, biomeId),
                    EntityCategory.of(entity), edited.contains(key(entity, variantName))));
        }
        return rows;
    }

    /** The Global view from live facts: every declared, non-game spawn, once. */
    private static List<MobEntry> fromResolver(RegistryAccess registries, Set<String> edited) {
        List<MobEntry> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> biomeIds = new ArrayList<>(Config.tables().biomes().keySet());
        var biomeReg = registries.registry(net.minecraft.core.registries.Registries.BIOME);
        if (biomeReg.isPresent()) {
            for (var rl : biomeReg.get().keySet()) {
                String id = rl.toString();
                if (!Config.tables().biomes().containsKey(id)) biomeIds.add(id);
            }
        }
        for (String biomeId : biomeIds) {
            for (SpawnCategory category : SpawnCategory.values()) {
                for (Spawn spawn : Config.declaredSpawns(biomeId, category)) {
                    if (spawn.source().layer() == Source.Layer.GAME) continue;
                    if (seen.add(spawn.entity() + "|" + spawn.variant())) {
                        rows.add(new MobEntry(spawn.entity(), spawn.variant(), spawn.weight(),
                                spawn.enabled(), spawn.source(), category,
                                edited.contains(key(spawn.entity(), spawn.variant()))));
                    }
                }
            }
        }
        return rows;
    }

    /**
     * The CSV fallback: the Global view with no live facts, or a list opened on a scope that is neither tier
     * nor biome.
     */
    private static List<MobEntry> fromCsv(String scope, String scopeId, boolean scoped,
                                          Set<String> edited) {
        List<MobEntry> rows = new ArrayList<>();
        for (Map<String, String> csv : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            String rowScope = csv.getOrDefault(Schema.MobSpawns.SCOPE, "").strip();
            String rowScopeId = csv.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip();
            String entity = csv.getOrDefault(Schema.MobSpawns.ENTITY, "").strip();
            if (entity.isEmpty()) continue;
            if (scoped && !(scope.equals(rowScope) && scopeId.equals(rowScopeId))) continue;
            String variantName = csv.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip();
            // Parse, as Tables reads these cells: two readers of one cell must not disagree.
            rows.add(new MobEntry(entity, variantName,
                    Parse.integer(csv.get(Schema.MobSpawns.WEIGHT), 1),
                    Parse.flag(csv.get(Schema.MobSpawns.ENABLED), true),
                    "tier".equals(rowScope) ? Source.tier(rowScopeId) : Source.BIOME,
                    EntityCategory.of(entity), edited.contains(key(entity, variantName))));
        }
        return rows;
    }
}
