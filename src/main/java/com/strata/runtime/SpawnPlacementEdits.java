package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A MOB TYPE'S SPAWN PLACEMENT AND SPAWN HEIGHTMAP: where it may spawn -
 * on the ground, in water, in lava, anywhere - and which surface its spawn height is read from.
 */
public final class SpawnPlacementEdits {

    private SpawnPlacementEdits() {
    }

    /** The placement types by the name the game layer writes. */
    public static final Map<String, SpawnPlacementType> PLACEMENTS = Map.of("on_ground", SpawnPlacementTypes.ON_GROUND,
            "in_water", SpawnPlacementTypes.IN_WATER, "in_lava", SpawnPlacementTypes.IN_LAVA,
            "no_restrictions", SpawnPlacementTypes.NO_RESTRICTIONS);

    /** The heightmaps a spawn can be measured from, by their serialized names. */
    public static final List<String> HEIGHTMAPS = List.of("WORLD_SURFACE", "MOTION_BLOCKING",
            "MOTION_BLOCKING_NO_LEAVES", "OCEAN_FLOOR");

    /** Each changed type's own values, before Strata's: placement, then heightmap. */
    private static final Map<EntityType<?>, Object[]> ORIGINAL = new ConcurrentHashMap<>();

    /** The game's own placement type of a type Strata changed, or null when Strata changed none. */
    public static SpawnPlacementType originalPlacement(EntityType<?> type) {
        Object[] o = ORIGINAL.get(type);
        return o == null ? null : (SpawnPlacementType) o[0];
    }

    /** The game's own heightmap of a type Strata changed, or null. */
    public static Heightmap.Types originalHeightmap(EntityType<?> type) {
        Object[] o = ORIGINAL.get(type);
        return o == null ? null : (Heightmap.Types) o[1];
    }

    /** Reads mob_data.csv's two type values and sets them on the event's entries (mod bus, lowest priority). */
    @SuppressWarnings("unchecked")
    public static void onRegister(RegisterSpawnPlacementsEvent event) {
        Map<EntityType<?>, Object> entries;
        Field placementField;
        Field heightmapField;
        try {
            Field map = RegisterSpawnPlacementsEvent.class.getDeclaredField("map");
            map.setAccessible(true);
            entries = (Map<EntityType<?>, Object>) map.get(event);
            placementField = RegisterSpawnPlacementsEvent.MergedSpawnPredicate.class.getDeclaredField("spawnType");
            heightmapField = RegisterSpawnPlacementsEvent.MergedSpawnPredicate.class.getDeclaredField("heightmapType");
            placementField.setAccessible(true);
            heightmapField.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Spawn placements: NeoForge's event could not be read ({}); the game's stay", e.toString());
            return;
        }
        int set = 0;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobData.FILE))) {
            String group = row.getOrDefault(Schema.MobData.GROUP, "").strip();
            String key = row.getOrDefault(Schema.MobData.KEY, "").strip();
            String value = row.getOrDefault(Schema.MobData.VALUE, "").strip();
            if (!"type".equals(group) || !(key.equals("spawn_placement") || key.equals("spawn_heightmap"))
                    || !row.getOrDefault(Schema.MobData.VARIANT_NAME, "").isBlank() || value.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(row.getOrDefault(Schema.MobData.ENTITY, "").strip());
            if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                continue;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            Object entry = entries.get(type);
            if (entry == null) {
                Strata.LOGGER.warn("Spawn placements: {} spawns with no rule of its own; {} is skipped", id, key);
                continue;
            }
            try {
                ORIGINAL.computeIfAbsent(type, t -> {
                    try {
                        return new Object[]{placementField.get(entry), heightmapField.get(entry)};
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
                if (key.equals("spawn_placement")) {
                    SpawnPlacementType p = PLACEMENTS.get(value);
                    if (p == null) {
                        Strata.LOGGER.warn("Spawn placements: {} spawn_placement {} is not one of {}", id, value, PLACEMENTS.keySet());
                        continue;
                    }
                    placementField.set(entry, p);
                } else {
                    if (!HEIGHTMAPS.contains(value)) {
                        Strata.LOGGER.warn("Spawn placements: {} spawn_heightmap {} is not one of {}", id, value, HEIGHTMAPS);
                        continue;
                    }
                    heightmapField.set(entry, Heightmap.Types.valueOf(value));
                }
                set++;
            } catch (ReflectiveOperationException | RuntimeException e) {
                Strata.LOGGER.warn("Spawn placements: could not set {} {}: {}", id, key, e.toString());
            }
        }
        if (set > 0) {
            Strata.LOGGER.info("Spawn placements: {} value(s) of yours set", set);
        }
    }
}
