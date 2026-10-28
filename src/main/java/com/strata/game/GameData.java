package com.strata.game;

import com.strata.Strata;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/** What the running game says, read at the moment it is asked. */
public final class GameData {

    private GameData() {}

    private static volatile RegistryAccess cachedFor;
    private static final Map<ResourceLocation, List<SpawnEntry>> mobCache = new ConcurrentHashMap<>();

    /** The spawner entries the strata:spawns modifier replaced, per biome. */
    private static final Map<Biome, Map<EntityType<?>, List<MobSpawnSettings.SpawnerData>>> strataReplaced =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Records what Strata's spawns modifier took out of a biome, so {@link #mobsIn} reports the biome as it
     * was.
     */
    public static void recordStrataReplaced(Biome biome,
                                            Map<EntityType<?>, List<MobSpawnSettings.SpawnerData>> replaced) {
        if (replaced.isEmpty()) {
            strataReplaced.remove(biome);
        } else {
            strataReplaced.put(biome, Map.copyOf(replaced));
        }
        // mobsIn reads this: a biome cached before the modifier ran is stale.
        mobCache.clear();
    }
    private static final Map<ResourceLocation, List<String>> oreCache = new ConcurrentHashMap<>();

    /**
     * One spawner entry of a biome, with its group size (it was dropped).
     *
     * @param weight the biome's own spawn weight, straight from its MobSettings
     */
    public record SpawnEntry(ResourceLocation entity, MobCategory category, int weight, int minCount, int maxCount) {}

    /** Drops every cache when the world changes. */
    private static void ensureCache(RegistryAccess registries) {
        if (cachedFor != registries) {
            cachedFor = registries;
            mobCache.clear();
            oreCache.clear();
        }
    }


    // ------------------------------------------------------------------ what spawns where
    // ------------------------------------------------------------------

    /**
     * The creatures that spawn in a biome, from the biome's OWN spawn settings - every category, with the
     * game's own weights.
     */
    public static List<SpawnEntry> mobsIn(RegistryAccess registries, ResourceLocation biomeId) {
        ensureCache(registries);
        List<SpawnEntry> cached = mobCache.get(biomeId);
        if (cached != null) {
            return cached;
        }

        List<SpawnEntry> out = new ArrayList<>();
        Holder<Biome> holder = holder(registries, biomeId);
        if (holder != null) {
            MobSpawnSettings settings = holder.value().getMobSettings();
            Map<EntityType<?>, List<MobSpawnSettings.SpawnerData>> replaced =
                    strataReplaced.getOrDefault(holder.value(), Map.of());
            for (MobCategory category : MobCategory.values()) {
                // The modifier replaces a type's entries in its own category's list.
                Map<EntityType<?>, List<MobSpawnSettings.SpawnerData>> here = new LinkedHashMap<>();
                replaced.forEach((type, entries) -> here.put(type, type.getCategory() == category ? entries : List.of()));
                com.strata.model.SpawnPlan.unmodified(settings.getMobs(category).unwrap(), d -> d.type, here)
                        .forEach(spawner -> {
                    // RS-7: getKey is null for an unregistered type.
                    ResourceLocation entityId =
                            BuiltInRegistries.ENTITY_TYPE.getKey(spawner.type);
                    if (entityId == null) {
                        return;
                    }
                    out.add(new SpawnEntry(entityId, category,
                            spawner.getWeight().asInt(), spawner.minCount, spawner.maxCount));
                });
            }
            // WHAT A MODIFIER TOOK AWAY COMES BACK.
            try {
                MobSpawnSettings original = holder.value().modifiableBiomeInfo().getOriginalBiomeInfo().mobSpawnSettings();
                java.util.Set<String> have = new java.util.HashSet<>();
                out.forEach(e -> have.add(e.category() + "|" + e.entity()));
                for (MobCategory category : MobCategory.values()) {
                    for (MobSpawnSettings.SpawnerData spawner : original.getMobs(category).unwrap()) {
                        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(spawner.type);
                        if (entityId != null && have.add(category + "|" + entityId)) {
                            out.add(new SpawnEntry(entityId, category,
                                    spawner.getWeight().asInt(), spawner.minCount, spawner.maxCount));
                        }
                    }
                }
            } catch (RuntimeException e) {
                com.strata.Strata.LOGGER.debug("Spawns: no original spawns for {}: {}", biomeId, e.toString());
            }
        }
        // ON A SERVER THE CLIENT'S BIOMES HAVE NO SPAWNS (the game sends none): Default mobs read nothing.
        if (out.isEmpty()) {
            var snapshot = com.strata.data.GameSnapshot.get().get(biomeId.toString());
            if (snapshot != null) {
                for (var s : snapshot.spawns()) {
                    ResourceLocation entity = ResourceLocation.tryParse(s.entity());
                    MobCategory category;
                    try {
                        category = MobCategory.valueOf(s.category().toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    if (entity != null) {
                        out.add(new SpawnEntry(entity, category, s.weight(), s.minCount(), s.maxCount()));
                    }
                }
            }
        }
        out.sort(Comparator.comparing((SpawnEntry e) -> e.category().ordinal())
                .thenComparing(e -> e.entity().toString()));
        List<SpawnEntry> frozen = List.copyOf(out);
        mobCache.put(biomeId, frozen);
        return frozen;
    }

    // ------------------------------------------------------------------ behavior
    // ------------------------------------------------------------------

    /** True when the mob has attack damage, so the attack behaviors can work on it. */
    @SuppressWarnings("unchecked")
    public static boolean canAttack(String entityId) {
        ResourceLocation id = entityId == null ? null : ResourceLocation.tryParse(entityId.strip());
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return false;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (!DefaultAttributes.hasSupplier(type)) {
            return false;
        }
        return DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type)
                .hasAttribute(Attributes.ATTACK_DAMAGE);
    }

    /**
     * A made mob's goals, each as an encoded GameGoal: the goal selector first, then the target selector.
     */
    public static List<String> goalsOf(Mob mob) {
        Map<String, String> out = new LinkedHashMap<>();
        readGoals(mob, out);
        // A MOB THAT SHOOTS PICKS ITS ATTACK FROM WHAT IT HOLDS.
        if (mob instanceof net.minecraft.world.entity.monster.RangedAttackMob) {
            net.minecraft.world.item.ItemStack held = mob.getMainHandItem().copy();
            for (net.minecraft.world.item.ItemStack other : List.of(net.minecraft.world.item.ItemStack.EMPTY,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW))) {
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, other.copy());
                readGoals(mob, out);
            }
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held);
        }
        return new ArrayList<>(out.values());
    }

    /** Adds the mob's goals not read yet: the goal selector, then the target selector. */
    private static void readGoals(Mob mob, Map<String, String> out) {
        mob.goalSelector.getAvailableGoals().forEach(w -> {
            String g = gameGoal(false, w.getPriority(), w.getGoal()).encode();
            out.putIfAbsent(g, g);
        });
        mob.targetSelector.getAvailableGoals().forEach(w -> {
            String g = gameGoal(true, w.getPriority(), w.getGoal()).encode();
            out.putIfAbsent(g, g);
        });
    }

    /** Default goals per entity id, read once per session. */
    private static final Map<String, List<String>> defaultGoalCache = new ConcurrentHashMap<>();

    /** One of a mob's goals as the editor explains it: its list, priority, and class chain. */
    private static com.strata.model.GameGoal gameGoal(boolean target, int priority,
                                                      net.minecraft.world.entity.ai.goal.Goal goal) {
        List<String> chain = new ArrayList<>();
        for (Class<?> c = goal.getClass();
             c != null && c != net.minecraft.world.entity.ai.goal.Goal.class && c != Object.class;
             c = c.getSuperclass()) {
            // An anonymous class (a skeleton's melee goal) has no name to show.
            if (!c.isAnonymousClass()) {
                chain.add(c.getSimpleName());
            }
        }
        // The exact class and the live settings: a mob's AI list starts from its real values, and Strata can
        // build a goal of a known class again with edited ones.
        return new com.strata.model.GameGoal(target, priority, List.copyOf(chain),
                com.strata.runtime.GoalReader.namedClass(goal).getName(), com.strata.runtime.GoalReader.settings(goal));
    }

    /**
     * The AI goals a freshly made mob of this type has, each as an encoded GameGoal (its list, priority, and
     * class chain; goal selector first, then target selector), for the editor's explanations.
     */
    public static List<String> defaultGoals(String entityId, MinecraftServer server) {
        String key = entityId == null ? "" : entityId.strip();
        List<String> cached = defaultGoalCache.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null || server == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return List.of();
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        java.util.function.Supplier<List<String>> read = () -> {
            Entity made = type.create(server.overworld());
            List<String> out = new ArrayList<>();
            // A skeleton picks its attack from what it holds, and a new one holds nothing, so it read as a
            // melee mob.
            if (made instanceof Mob armed) {
                com.strata.runtime.DefaultEquipmentRuntime.rollGame(armed, key);
            }
            if (made instanceof Mob mob) {
                out.addAll(goalsOf(mob));
            }
            return List.copyOf(out);
        };
        List<String> goals;
        try {
            // ON THE SERVER THREAD ALREADY, READ AT ONCE.
            goals = server.isSameThread() ? read.get()
                    : server.submit(read).get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            Strata.LOGGER.debug("Could not read the default goals of {}: {}", key, e.toString());
            return List.of();
        }
        defaultGoalCache.put(key, goals);
        return goals;
    }

    /**
     * The ores the game itself places in a biome, for the ore editor: from the open world's registries when
     * there are any, else from the last world snapshot.
     */
    public static List<String> gameOresIn(RegistryAccess registries, String biomeId) {
        ResourceLocation id = biomeId == null ? null : ResourceLocation.tryParse(biomeId.strip());
        if (id == null) {
            return List.of();
        }
        if (registries != null) {
            return oresIn(registries, id);
        }
        var entry = com.strata.data.GameSnapshot.get().get(id.toString());
        return entry == null ? List.of() : entry.ores();
    }

    /** The Strata-known ores a biome generates, matched from its own placed features. */
    public static List<String> oresIn(RegistryAccess registries, ResourceLocation biomeId) {
        ensureCache(registries);
        List<String> cached = oreCache.get(biomeId);
        if (cached != null) {
            return cached;
        }

        Map<ResourceLocation, String> oreByFeature = new LinkedHashMap<>();
        for (String ore : OreDetector.knownOreNames()) {
            OreDetector.placedFeaturesFor(ore).ifPresent(features ->
                    features.forEach(feature -> oreByFeature.put(feature, ore)));
        }

        Set<String> found = new LinkedHashSet<>();
        Holder<Biome> holder = holder(registries, biomeId);
        if (holder != null && !oreByFeature.isEmpty()) {
            holder.value().getGenerationSettings().features().forEach(step ->
                    step.forEach(feature -> feature.unwrapKey()
                            .map(key -> oreByFeature.get(key.location()))
                            .ifPresent(found::add)));
        }
        List<String> out = new ArrayList<>(found);
        out.sort(Comparator.naturalOrder());
        List<String> frozen = List.copyOf(out);
        oreCache.put(biomeId, frozen);
        return frozen;
    }

    // ------------------------------------------------------------------ creatures
    // ------------------------------------------------------------------

    private static Holder<Biome> holder(RegistryAccess registries, ResourceLocation id) {
        try {
            HolderLookup.RegistryLookup<Biome> lookup = registries.lookupOrThrow(Registries.BIOME);
            return lookup.get(ResourceKey.create(Registries.BIOME, id)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

}
