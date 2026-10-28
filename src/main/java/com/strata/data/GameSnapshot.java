package com.strata.data;

import com.strata.game.GameData;
import com.strata.game.GameFactsAdapter;
import com.strata.game.RegistryQuery;
import com.strata.Strata;
import com.strata.model.Resolver;
import com.strata.model.Source;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The spawning GAME LAYER: config/strata/game/spawns.csv. */
public final class GameSnapshot {

    private GameSnapshot() {}

    private static Path file() {
        return GameLayer.dir().resolve(GameLayer.SPAWNS);
    }

    /** biome id -> the spawns and ores it had. */
    private static volatile Map<String, Entry> snapshot = Map.of();
    private static volatile boolean loaded;

    /** One spawner entry. */
    public record SpawnData(String entity, String category, int weight, int minCount, int maxCount) {}

    /** GAME FACTS ONLY. */
    public record Entry(List<SpawnData> spawns, List<String> ores) {

        /** The spawner entity ids; for code that predates the triples. */
        public List<String> mobs() {
            return spawns.stream().map(SpawnData::entity).toList();
        }
    }

    private static final String COMMENT = """
            The GAME LAYER: what the game spawns, rewritten at every game start. Do not edit: your spawn rows are in
            mob_spawns.csv and your caps in mob_caps.csv. entity: a biome, a structure, or a mob category for a cap row.
            spawn value: category|entity|weight|min|max. cost value: charge|energy budget.
            structure_spawn value: category|entity|weight|min|max|box (piece or full: where in the structure).""";

    /**
     * One entry of a structure's own spawn list: a nether fortress spawns blazes and wither skeletons from
     * it, not from its biome's list.
     */
    public record StructureSpawn(String structure, SpawnData spawn, String box) {}

    /** Extracts the current world's spawning data to game/spawns.csv. */
    public static void capture(RegistryAccess registries) {
        try {
            List<GameLayer.Row> rows = new ArrayList<>();
            int biomes = 0;
            for (ResourceLocation biome : RegistryQuery.biomes(registries)) {
                biomes++;
                List<SpawnData> spawns = new ArrayList<>();
                GameData.mobsIn(registries, biome).forEach(s -> spawns.add(new SpawnData(s.entity().toString(),
                        s.category().getName(), s.weight(), s.minCount(), s.maxCount())));
                rows.addAll(spawnRows(biome.toString(), spawns));
                MobSpawnSettings settings = registries.lookupOrThrow(Registries.BIOME)
                        .get(ResourceKey.create(Registries.BIOME, biome)).map(h -> h.value().getMobSettings())
                        .orElse(null);
                if (settings != null) {
                    rows.add(new GameLayer.Row(biome.toString(), GameLayer.BIOME, "creature_probability",
                            GameLayer.text((double) settings.getCreatureProbability())));
                    for (var type : settings.getEntityTypes()) {
                        MobSpawnSettings.MobSpawnCost cost = settings.getMobSpawnCost(type);
                        String id = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type)
                                .map(k -> k.location().toString()).orElse("");
                        if (cost != null && !id.isEmpty()) {
                            rows.add(new GameLayer.Row(biome.toString(), GameLayer.COST, id,
                                    Parse.number(cost.charge()) + "|" + Parse.number(cost.energyBudget())));
                        }
                    }
                }
                for (String ore : GameData.oresIn(registries, biome)) {
                    rows.add(new GameLayer.Row(biome.toString(), GameLayer.ORE, ore, "true"));
                }
                // Its plants: the placed features of its vegetation step.
                for (String feature : vegetationIn(registries, biome)) {
                    rows.add(new GameLayer.Row(biome.toString(), GameLayer.VEGETATION, feature, "true"));
                }
            }
            int structures = 0;
            for (var holder : registries.lookupOrThrow(Registries.STRUCTURE).listElements().toList()) {
                List<StructureSpawn> list = new ArrayList<>();
                String id = holder.key().location().toString();
                holder.value().spawnOverrides().forEach((category, override) -> override.spawns().unwrap()
                        .forEach(s -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(s.type).ifPresent(k ->
                                list.add(new StructureSpawn(id, new SpawnData(k.location().toString(),
                                        category.getName(), s.getWeight().asInt(), s.minCount, s.maxCount),
                                        override.boundingBox().getSerializedName())))));
                if (!list.isEmpty()) {
                    structures++;
                    rows.addAll(structureSpawnRows(list));
                }
            }
            for (MobCategory category : MobCategory.values()) {
                String c = category.getName();
                rows.add(new GameLayer.Row(c, GameLayer.CAP, "max", String.valueOf(category.getMaxInstancesPerChunk())));
                rows.add(new GameLayer.Row(c, GameLayer.CAP, "despawn_distance",
                        String.valueOf(category.getDespawnDistance())));
                rows.add(new GameLayer.Row(c, GameLayer.CAP, "no_despawn_distance",
                        String.valueOf(category.getNoDespawnDistance())));
                rows.add(new GameLayer.Row(c, GameLayer.CAP, "friendly", String.valueOf(category.isFriendly())));
                rows.add(new GameLayer.Row(c, GameLayer.CAP, "persistent", String.valueOf(category.isPersistent())));
            }
            if (GameLayer.write(file(), COMMENT, rows)) {
                Strata.LOGGER.info("Game layer: extracted the spawning of {} biomes and {} structures", biomes,
                        structures);
            }
        } catch (Exception e) {
            // Not being able to write this costs the title-screen browser and nothing else, so it is logged
            // and dropped rather than interfering with a world that is trying to start.
            Strata.LOGGER.warn("Could not write the spawning game layer: {}", e.toString(), e);
        }
    }

    /**
     * A biome's plant features: the placed features of its vegetal decoration step, as the game generates
     * them (trees, flowers, grass, mushrooms, cacti).
     */
    public static List<String> vegetationIn(RegistryAccess registries, ResourceLocation biome) {
        List<String> out = new ArrayList<>();
        registries.lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, biome)).ifPresent(h -> {
            var steps = h.value().getGenerationSettings().features();
            int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
            if (step < steps.size()) {
                steps.get(step).forEach(f -> f.unwrapKey().ifPresent(k -> {
                    String id = k.location().toString();
                    if (!out.contains(id)) {
                        out.add(id);
                    }
                }));
            }
        });
        return out;
    }

    private static volatile Map<String, List<String>> vegetation = Map.of();

    /** Each biome's plant features, from the game layer. */
    public static Map<String, List<String>> vegetation() {
        if (!loaded) {
            load();
        }
        return vegetation;
    }

    /** Each biome's plant features in the sheet's rows, in file order. */
    public static Map<String, List<String>> vegetationFromRows(List<GameLayer.Row> rows) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (GameLayer.Row r : rows) {
            if (GameLayer.VEGETATION.equals(r.group())) {
                out.computeIfAbsent(r.entity(), k -> new ArrayList<>()).add(r.key());
            }
        }
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        out.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return Map.copyOf(frozen);
    }

    /** A biome's spawner rows as the sheet writes them. */
    public static List<GameLayer.Row> spawnRows(String biome, List<SpawnData> spawns) {
        List<GameLayer.Row> out = new ArrayList<>();
        for (int i = 0; i < spawns.size(); i++) {
            SpawnData s = spawns.get(i);
            out.add(new GameLayer.Row(biome, GameLayer.SPAWN, String.valueOf(i),
                    String.join("|", s.category(), s.entity(), String.valueOf(s.weight()),
                            String.valueOf(s.minCount()), String.valueOf(s.maxCount()))));
        }
        return out;
    }

    /** A structure's spawn rows as the sheet writes them. */
    public static List<GameLayer.Row> structureSpawnRows(List<StructureSpawn> spawns) {
        List<GameLayer.Row> out = new ArrayList<>();
        for (int i = 0; i < spawns.size(); i++) {
            StructureSpawn s = spawns.get(i);
            SpawnData d = s.spawn();
            out.add(new GameLayer.Row(s.structure(), GameLayer.STRUCTURE_SPAWN, String.valueOf(i),
                    String.join("|", d.category(), d.entity(), String.valueOf(d.weight()),
                            String.valueOf(d.minCount()), String.valueOf(d.maxCount()), s.box())));
        }
        return out;
    }

    /** Every structure spawn in the sheet's rows, in file order. */
    public static List<StructureSpawn> structureSpawnsFromRows(List<GameLayer.Row> rows) {
        List<StructureSpawn> out = new ArrayList<>();
        for (GameLayer.Row r : rows) {
            if (GameLayer.STRUCTURE_SPAWN.equals(r.group())) {
                String[] v = r.value().split("\\|", -1);
                if (v.length == 6) {
                    out.add(new StructureSpawn(r.entity(), new SpawnData(v[1], v[0], Parse.integer(v[2], 0),
                            Parse.integer(v[3], 1), Parse.integer(v[4], 1)), v[5]));
                }
            }
        }
        return List.copyOf(out);
    }

    private static volatile List<StructureSpawn> structureSpawns = List.of();

    /** Every structure's own spawns, from the game layer. */
    public static List<StructureSpawn> structureSpawns() {
        if (!loaded) {
            load();
        }
        return structureSpawns;
    }

    /** Each biome's spawns and ores from the sheet's rows. */
    public static Map<String, Entry> fromRows(List<GameLayer.Row> rows) {
        Map<String, List<SpawnData>> spawns = new LinkedHashMap<>();
        Map<String, List<String>> ores = new LinkedHashMap<>();
        for (GameLayer.Row r : rows) {
            if (GameLayer.SPAWN.equals(r.group())) {
                String[] v = r.value().split("\\|", -1);
                if (v.length == 5) {
                    spawns.computeIfAbsent(r.entity(), k -> new ArrayList<>()).add(new SpawnData(v[1], v[0],
                            Parse.integer(v[2], 0), Parse.integer(v[3], 1), Parse.integer(v[4], 1)));
                }
            } else if (GameLayer.ORE.equals(r.group())) {
                ores.computeIfAbsent(r.entity(), k -> new ArrayList<>()).add(r.key());
            }
        }
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String biome : spawns.keySet()) {
            out.put(biome, new Entry(List.copyOf(spawns.get(biome)), List.copyOf(ores.getOrDefault(biome, List.of()))));
        }
        for (String biome : ores.keySet()) {
            out.putIfAbsent(biome, new Entry(List.of(), List.copyOf(ores.get(biome))));
        }
        return Map.copyOf(out);
    }

    /** Everything the last world had, keyed by biome id. */
    public static Map<String, Entry> get() {
        if (!loaded) {
            load();
        }
        return snapshot;
    }

    public static boolean isEmpty() {
        return get().isEmpty();
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        List<GameLayer.Row> rows = GameLayer.read(file());
        snapshot = fromRows(rows);
        structureSpawns = structureSpawnsFromRows(rows);
        vegetation = vegetationFromRows(rows);
        if (!snapshot.isEmpty()) {
            Strata.LOGGER.info("Loaded the spawning of {} biomes from the game layer", snapshot.size());
        }
    }

    /** Forces a re-read, so a capture during this session is visible without a restart. */
    public static void invalidate() {
        loaded = false;
    }

    /** THE OFFLINE FACTS SEAM. */
    public static final class FactsAdapter implements Resolver.GameFacts {

        @Override
        public List<Spawn> spawns(String biome, SpawnCategory category) {
            MobCategory vanilla = GameFactsAdapter.toVanilla(category);
            if (vanilla == null) {
                return List.of();
            }
            Entry entry = get().get(biome);
            if (entry == null) {
                return List.of();
            }
            List<Spawn> out = new ArrayList<>();
            for (SpawnData data : entry.spawns()) {
                if (data.category().equals(vanilla.getName())) {
                    out.add(Spawn.of(category, data.entity(), data.weight(),
                            data.minCount(), data.maxCount(), Source.GAME));
                }
            }
            return List.copyOf(out);
        }

        @Override
        public List<String> ores(String biome) {
            Entry entry = get().get(biome);
            return entry == null ? List.of() : entry.ores();
        }
    }
}
