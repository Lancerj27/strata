package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.GameLayer;
import com.strata.data.Schema;
import com.strata.data.GameSnapshot;
import com.strata.data.Parse;
import com.strata.data.StrataDatapack;
import com.strata.game.ExtractionLevel;
import com.strata.game.GameData;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** THE GAME LAYER, FILLED AT GAME START. */
public final class MobExtractor {

    private MobExtractor() {
    }

    private static final String COMMENT = """
            The GAME LAYER: every value Strata extracted from the game, one row per value. Rewritten at every game start.
            Do not edit: your values go in the Strata tables and are put over these.
            group: type, attribute, tag, or field (a value of the mob's class chain, keyed Class.field).""";

    private static final String GOALS_COMMENT = """
            The GAME LAYER: every mob's own AI goals, read from a freshly made mob at every game start. Do not edit.
            key: the goal's place in the list. value: who|priority|chain|class|settings (who: t picks a target, h does).""";

    /** Fills every game-layer sheet. */
    public static void extractAll(RegistryAccess registries, ResourceManager resources) {
        applyBiomeModifiers(registries);
        MADE.clear();
        Level level = null;
        try {
            level = new ExtractionLevel(registries);
        } catch (RuntimeException | LinkageError e) {
            Strata.LOGGER.warn("Extract: no extraction level ({}): fields, equipment, and goals are left out", e.toString());
        }
        Map<String, Collection<String>> tags = tags(resources);

        List<GameLayer.Row> mobRows = new ArrayList<>();
        Map<String, Mob> made = new LinkedHashMap<>();
        List<String> equipment = new ArrayList<>();
        int types = 0;
        int unmade = 0;
        int armed = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).map(k -> k.location().toString()).orElse("");
            if (id.isEmpty()) {
                continue;
            }
            types++;
            try {
                typeRows(type, id, mobRows);
                // EVERY TAG, IN OR NOT: a tag a mob is not in is a value the user can switch on.
                Collection<String> own = tags.getOrDefault(id, List.of());
                tags.get("").forEach(tag -> mobRows.add(new GameLayer.Row(id, GameLayer.TAG, tag,
                        String.valueOf(own.contains(tag)))));
                attributeRows(type, id, mobRows);
            } catch (RuntimeException e) {
                Strata.LOGGER.debug("Extract: could not read {}: {}", id, e.toString());
            }
            Entity entity = make(type, level);
            if (entity == null) {
                if (type.canSummon()) {
                    unmade++;
                }
                continue;
            }
            // ONE MOD'S MOB NEVER STOPS THE REST (a modpack log: Ars Nouveau's Scryer
            // Camera threw from its own remove off a world, and that one throw ended the whole read, so
            // nothing filled).
            try {
                fieldRows(entity, id, mobRows);
                modelRows(entity, id, mobRows);
                String family = EquipmentSeeder.familyOf(entity);
                List<String> lines = EquipmentSeeder.lines(id, family);
                if (!lines.isEmpty()) {
                    equipment.addAll(lines);
                    armed++;
                }
                if (entity instanceof Mob mob) {
                    EquipmentSeeder.equipSure(mob, family);
                    made.put(id, mob);
                    if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                        MADE.put(id, mob);
                    }
                } else {
                    discard(entity, id);
                }
            } catch (RuntimeException | LinkageError e) {
                Strata.LOGGER.warn("Extract: {} could not be read off a world and is left out: {}", id, e.toString());
                made.remove(id);
                MADE.remove(id);
                discard(entity, id);
            }
        }
        if (GameLayer.write(GameLayer.dir().resolve(GameLayer.MOBS), COMMENT, mobRows)) {
            Strata.LOGGER.info("Game layer: extracted {} value(s) of {} entity type(s); {} could not be made off a world",
                    mobRows.size(), types, unmade);
        }
        EquipmentSeeder.write(equipment, armed);

        try {
            afterMade.run();
        } catch (RuntimeException | LinkageError e) {
            Strata.LOGGER.warn("Extract: could not read the mob textures: {}", e.toString());
        }
        // The goal targets read their classes from the mob sheet, so it is in place before the goals are
        // read.
        GoalTargets.load(mobRows);
        List<GameLayer.Row> goalRows = new ArrayList<>();
        made.forEach((id, mob) -> {
            try {
                List<String> goals = GameData.goalsOf(mob);
                for (int i = 0; i < goals.size(); i++) {
                    goalRows.add(new GameLayer.Row(id, GameLayer.GOAL, String.valueOf(i), goals.get(i)));
                }
            } catch (RuntimeException e) {
                Strata.LOGGER.debug("Extract: could not read the goals of {}: {}", id, e.toString());
            } finally {
                discard(mob, id);
            }
        });
        if (GameLayer.write(GameLayer.dir().resolve(GameLayer.GOALS), GOALS_COMMENT, goalRows)) {
            Strata.LOGGER.info("Game layer: extracted {} goal(s)", goalRows.size());
        }
        // The mob families become archetypes, once each: a name the library holds is never overwritten.
        int seeded = com.strata.domain.GoalsOps.seedFamilies(goalRows, mobRows);
        if (seeded < 0) {
            Strata.LOGGER.warn("Goal sets: could not seed the family goal sets into goal_sets.csv");
        } else if (seeded > 0) {
            Strata.LOGGER.info("Goal sets: {} family goal set(s) seeded from the game", seeded);
        }

        LootExtractor.extract(resources);
        // Every block's tool, tool level, and tags.
        BlockTagExtractor.extract(resources);
        // The override sheets' game values - recipes, loot tables, worldgen, tags, data maps, advancements -
        // and every game rule's default, at game start rather than first world start, so those menus are
        // filled from the title screen.
        com.strata.data.JsonOverrides.extract(resources);
        GameRulesData.read();
        // Every villager profession's and the wandering trader's trades.
        TradesRuntime.extractAtStart();
        // The game's own spawn conditions, read from its code, seeded once; the Default profile, the game's
        // own values, written again.
        SpecialSpawnsRuntime.seedAtStart();
        // The ores, from the game's features and the c:ores tag in the pack files, before the biomes' ores
        // are read.
        java.util.Set<net.minecraft.world.level.block.Block> ores = oreBlocks(resources);
        com.strata.game.OreDetector.detect(registries, ores::contains);
        GameSnapshot.capture(registries);
        GameSnapshot.invalidate();
    }

    /**
     * The mobs made at the last extraction, kept for the client, which reads each one's texture from its
     * renderer (com.strata.client.ModelSwap).
     */
    public static final Map<String, Mob> MADE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Run once the mobs are made: the client reads their textures (ModelSwap#readTextures). */
    public static volatile Runnable afterMade = () -> { };

    /**
     * A mob's model and sounds (group "model"): its own id, and the sounds its class plays when idle, hurt,
     * and dying.
     */
    private static void modelRows(Entity made, String id, List<GameLayer.Row> out) {
        if (!(made instanceof LivingEntity living)) {
            return;
        }
        out.add(new GameLayer.Row(id, GameLayer.MODEL, "model", id));
        modelSound(out, id, "sound_ambient", living, "getAmbientSound");
        modelSound(out, id, "sound_hurt", living, "getHurtSound");
        modelSound(out, id, "sound_death", living, "getDeathSound");
        // THE SAME SOUNDS, AGAIN, IN THE "sounds" GROUP.
        soundProperty(out, id, Schema.MobSounds.AMBIENT_SOUND, living, "getAmbientSound");
        soundProperty(out, id, Schema.MobSounds.HURT_SOUND, living, "getHurtSound");
        soundProperty(out, id, Schema.MobSounds.DEATH_SOUND, living, "getDeathSound");
        // These three are on Entity, not LivingEntity, and are protected there, so a mob that overrides them
        // is found by the same class walk.
        soundProperty(out, id, Schema.MobSounds.STEP_SOUND, living, "getStepSound");
        soundProperty(out, id, Schema.MobSounds.SWIM_SOUND, living, "getSwimSound");
        soundProperty(out, id, Schema.MobSounds.FALL_SOUND, living, "getFallSound");
        // attack_sound has NO game value and gets no row.
    }

    /** One sound property's game value, in the "sounds" group, or no row. */
    private static void soundProperty(List<GameLayer.Row> out, String id, String column,
                                      LivingEntity living, String method) {
        net.minecraft.sounds.SoundEvent event = readSound(living, method);
        if (event != null) {
            out.add(new GameLayer.Row(id, GameLayer.SOUNDS, column, event.getLocation().toString()));
        }
    }

    /**
     * A mob's own sound from one of its sound getters, or null when it has no such getter or the getter would
     * not run off a world.
     */
    private static net.minecraft.sounds.SoundEvent readSound(LivingEntity living, String method) {
        try {
            for (Class<?> c = living.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(method) && m.getParameterCount() <= 1) {
                        m.setAccessible(true);
                        Object sound = m.getParameterCount() == 0 ? m.invoke(living)
                                : m.invoke(living, living.damageSources().generic());
                        if (sound instanceof net.minecraft.sounds.SoundEvent event) {
                            return event;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Strata.LOGGER.debug("Extract: could not read {} of {}: {}", method,
                    living.getType(), e.toString());
        }
        return null;
    }

    private static void modelSound(List<GameLayer.Row> out, String id, String key, LivingEntity living, String method) {
        net.minecraft.sounds.SoundEvent event = readSound(living, method);
        if (event != null) {
            out.add(new GameLayer.Row(id, GameLayer.MODEL, key, event.getLocation().toString()));
        }
    }

    /** The mob, made once in the extraction level, or null when it cannot be made there. */
    private static Entity make(EntityType<?> type, Level level) {
        if (level == null) {
            return null;
        }
        try {
            return type.create(level);
        } catch (RuntimeException | LinkageError e) {
            Strata.LOGGER.debug("Extract: {} cannot be made off a world: {}", type, e.toString());
            return null;
        }
    }

    /**
     * Applies the mods' biome modifiers to the startup biomes, as the game does when a server starts, so the
     * spawns and ores of a mod show at game start.
     */
    private static void applyBiomeModifiers(RegistryAccess registries) {
        try {
            List<BiomeModifier> modifiers = new ArrayList<>();
            registries.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).holders().forEach(h -> {
                String ns = h.key().location().getNamespace();
                if (!StrataDatapack.NAMESPACE.equals(ns) && !Strata.MOD_ID.equals(ns)) {
                    modifiers.add(h.value());
                }
            });
            int biomes = 0;
            for (Holder.Reference<Biome> biome : registries.registryOrThrow(Registries.BIOME).holders().toList()) {
                biome.value().modifiableBiomeInfo().applyBiomeModifiers(biome, modifiers, registries);
                biomes++;
            }
            Strata.LOGGER.info("Extract: applied {} biome modifier(s) from the game and mods to {} biome(s)",
                    modifiers.size(), biomes);
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Extract: could not apply the biome modifiers ({}): a mod's spawns may be missing",
                    e.toString());
        }
    }

    /** Each entity id's tags, read from the packs' tag files (tags are not bound at game start). */
    private static Map<String, Collection<String>> tags(ResourceManager resources) {
        Map<String, Collection<String>> out = new LinkedHashMap<>();
        Collection<String> all = new TreeSet<>();
        out.put("", all);
        try {
            TagLoader<Holder<EntityType<?>>> loader = new TagLoader<>(
                    id -> BuiltInRegistries.ENTITY_TYPE.getHolder(id).map(h -> (Holder<EntityType<?>>) h),
                    Registries.tagsDirPath(Registries.ENTITY_TYPE));
            var built = loader.loadAndBuild(resources);
            built.forEach((tag, members) -> all.add(tag.toString()));
            built.forEach((tag, members) -> members.forEach(h ->
                    h.unwrapKey().ifPresent(k -> out.computeIfAbsent(k.location().toString(), x -> new TreeSet<>())
                            .add(tag.toString()))));
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Extract: could not read the entity tags: {}", e.toString());
        }
        return out;
    }

    /** The blocks in c:ores, read from the packs' tag files (tags are not bound at game start). */
    private static java.util.Set<net.minecraft.world.level.block.Block> oreBlocks(ResourceManager resources) {
        java.util.Set<net.minecraft.world.level.block.Block> out = new java.util.HashSet<>();
        try {
            TagLoader<Holder<net.minecraft.world.level.block.Block>> loader = new TagLoader<>(
                    id -> BuiltInRegistries.BLOCK.getHolder(id).map(h -> (Holder<net.minecraft.world.level.block.Block>) h),
                    Registries.tagsDirPath(Registries.BLOCK));
            var ores = loader.loadAndBuild(resources).get(net.neoforged.neoforge.common.Tags.Blocks.ORES.location());
            if (ores != null) {
                ores.forEach(h -> out.add(h.value()));
            }
        } catch (RuntimeException e) {
            Strata.LOGGER.warn("Extract: could not read the ore tag: {}", e.toString());
        }
        return out;
    }

    private static void attributeRows(EntityType<?> type, String id, List<GameLayer.Row> out) {
        if (!DefaultAttributes.hasSupplier(type)) {
            return;
        }
        @SuppressWarnings("unchecked")
        AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        BuiltInRegistries.ATTRIBUTE.holders().forEach(h -> {
            if (supplier.hasAttribute(h)) {
                out.add(new GameLayer.Row(id, GameLayer.ATTRIBUTE, h.key().location().toString(),
                        Parse.number(supplier.getBaseValue(h))));
            }
        });
    }

    private static void typeRows(EntityType<?> type, String id, List<GameLayer.Row> out) {
        put(out, id, "category", type.getCategory().getName());
        put(out, id, "width", type.getWidth());
        put(out, id, "height", type.getHeight());
        put(out, id, "eye_height", type.getDimensions().eyeHeight());
        put(out, id, "fixed_size", type.getDimensions().fixed());
        put(out, id, "fire_immune", type.fireImmune());
        put(out, id, "summonable", type.canSummon());
        put(out, id, "spawn_far_from_player", type.canSpawnFarFromPlayer());
        put(out, id, "tracking_range", type.clientTrackingRange());
        put(out, id, "update_interval", type.updateInterval());
        // Blocks it ignores (EntityType#immuneTo), the game's own list.
        put(out, id, "immune_to", MobDataRuntime.immuneToOf(type));
        if (SpawnPlacements.hasPlacement(type)) {
            // The game's own, not Strata's change of them (SpawnPlacementEdits keeps the game's).
            var ownPlacement = SpawnPlacementEdits.originalPlacement(type);
            var ownHeightmap = SpawnPlacementEdits.originalHeightmap(type);
            put(out, id, "spawn_placement", placement(ownPlacement != null ? ownPlacement : SpawnPlacements.getPlacementType(type)));
            put(out, id, "spawn_heightmap", (ownHeightmap != null ? ownHeightmap : SpawnPlacements.getHeightmapType(type)).getSerializedName());
        }
    }

    /** The game's placement types by name; a mod's own type by its class. */
    private static String placement(SpawnPlacementType p) {
        if (p == SpawnPlacementTypes.ON_GROUND) return "on_ground";
        if (p == SpawnPlacementTypes.IN_WATER) return "in_water";
        if (p == SpawnPlacementTypes.IN_LAVA) return "in_lava";
        if (p == SpawnPlacementTypes.NO_RESTRICTIONS) return "no_restrictions";
        return p.getClass().getName();
    }

    /**
     * The made mob's class, its class chain (the families are named by it, com.strata.domain.GoalFamilies),
     * and every simple field of its class chain up to Entity.
     */
    private static void fieldRows(Entity made, String id, List<GameLayer.Row> out) {
        put(out, id, "class", made.getClass().getName());
        List<String> chain = new ArrayList<>();
        for (Class<?> c = made.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(c.getSimpleName());
        }
        put(out, id, com.strata.domain.GoalFamilies.CLASS_CHAIN, String.join(com.strata.domain.GoalFamilies.CHAIN_SEP, chain));
        // What goals it can be given (the editor says why one will not run).
        put(out, id, com.strata.model.GoalType.NEEDS_MET, String.join(com.strata.domain.GoalFamilies.CHAIN_SEP,
                GoalsRuntime.needsMet(made)));
        for (Class<?> c = made.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!GameLayer.wanted(f)) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    out.add(new GameLayer.Row(id, GameLayer.FIELD, c.getSimpleName() + "." + f.getName(),
                            GameLayer.text(f.get(made))));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Strata.LOGGER.debug("Extract: {} {}.{} unreadable: {}", id, c.getSimpleName(), f.getName(), e.toString());
                }
            }
        }
    }

    private static void put(List<GameLayer.Row> out, String id, String key, Object value) {
        out.add(new GameLayer.Row(id, GameLayer.TYPE, key, GameLayer.text(value)));
    }

    /** Discards a mob made off a world. */
    private static void discard(Entity entity, String id) {
        try {
            entity.discard();
        } catch (RuntimeException | LinkageError e) {
            Strata.LOGGER.debug("Extract: {} could not be discarded off a world: {}", id, e.toString());
        }
    }
}
