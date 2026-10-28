package com.strata.runtime;

import com.mojang.serialization.MapCodec;
import com.strata.Strata;
import com.strata.data.Config;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;
import com.strata.model.SpawnPlan;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Puts a biome's spawn rows on the biome, a code biome modifier (strata:spawns), run as a world loads, in the
 * MODIFY phase.
 */
public record SpawnsModifier() implements BiomeModifier {

    public static final SpawnsModifier INSTANCE = new SpawnsModifier();

    public static final MapCodec<SpawnsModifier> CODEC = MapCodec.unit(INSTANCE);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<SpawnsModifier>>
            SPAWNS = OreRestrictionModifier.BIOME_MODIFIERS.register("spawns", () -> CODEC);

    /** Loads the class during mod construction, so the serializer registers in time. */
    public static void init() {
    }

    /**
     * A spawn's spawner key: the promoted variant's own entity id when it has one, the base entity id
     * otherwise.
     */
    public static String keyOf(Spawn spawn) {
        if (spawn.isVariant() && VariantEntityRegistry.isPromoted(spawn.variant())) {
            return VariantEntityRegistry.getRegistered(spawn.variant())
                    .map(BuiltInRegistries.ENTITY_TYPE::getKey)
                    .map(ResourceLocation::toString)
                    .orElse(spawn.entity());
        }
        return spawn.entity();
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        // AFTER_EVERYTHING: every other mod's biome modifiers have run, so their spawns, values and features
        // are here to be read and changed, and Strata's change is the last word.
        if (phase != Phase.AFTER_EVERYTHING) {
            return;
        }
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse("");
        if (id.isEmpty()) {
            return;
        }
        try {
            var spawns = builder.getMobSpawnSettings();
            // A MOB'S CATEGORY, EDITED: its entries move to its new category's list before anything else
            // reads the lists.
            List<Map<String, String>> mobData =
                    com.strata.data.CsvTable.read(com.strata.data.Paths.table(com.strata.data.Schema.MobData.FILE));
            Map<String, String> edited = new java.util.LinkedHashMap<>(com.strata.data.GameLayer.editedCategories(mobData));
            // A VARIANT'S OWN CATEGORY, under its own mob type's id: its entries go to that category's list.
            com.strata.data.GameLayer.editedVariantCategories(mobData).forEach((key, category) ->
                    VariantEntityRegistry.getRegistered(key.substring(key.indexOf('|') + 1))
                            .flatMap(t -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(t))
                            .ifPresent(k -> edited.put(k.location().toString(), category)));
            moveEdited(spawns, edited);
            List<Spawn> declared = new ArrayList<>();
            for (SpawnCategory category : SpawnCategory.values()) {
                declared.addAll(Config.declaredSpawns(id, category));
            }
            // A VARIANT THAT SPAWNS AS SPECIAL stays off the biome's lists: it comes only as its base mob's
            // special spawner makes it.
            declared.removeIf(s -> s.isVariant() && SpecialSpawnsRuntime.special(s.entity(), s.variant()));
            // A REPLACE TIER'S ROSTER IS ALL THAT SPAWNS: the game's entries go, in every category, and a mob
            // with only a variant row starts from 0.
            boolean replaces = Config.replacesGameSpawns(id);
            // Nothing of the designer's for this biome: the game's lists, and every other mod's, stand.
            if (edited.isEmpty() && !replaces && Replacements.in(biome.unwrapKey().get().location()).isEmpty()
                    && declared.stream().allMatch(s -> s.source().layer() == com.strata.model.Source.Layer.GAME)) {
                return;
            }
            Map<String, SpawnPlan.Entry> plan = new java.util.LinkedHashMap<>(SpawnPlan.plan(declared,
                    SpawnsModifier::keyOf, key -> replaces ? null : existing(spawns, key, edited)));
            List<String> biomeKeys = new ArrayList<>();
            for (net.minecraft.world.entity.MobCategory category : net.minecraft.world.entity.MobCategory.values()) {
                for (MobSpawnSettings.SpawnerData d : spawns.getSpawner(category)) {
                    BuiltInRegistries.ENTITY_TYPE.getResourceKey(d.type)
                            .ifPresent(k -> biomeKeys.add(k.location().toString()));
                }
            }
            for (String key : SpawnPlan.dropped(biomeKeys, plan.keySet(), replaces)) {
                plan.put(key, new SpawnPlan.Entry(0, 1, 1));
            }
            // What each replaced type had, so GameData reports the game's weights and not these.
            Map<EntityType<?>, List<MobSpawnSettings.SpawnerData>> replaced = new java.util.LinkedHashMap<>();
            plan.forEach((key, entry) -> {
                EntityType<?> type = type(key);
                if (type != null) {
                    replaced.put(type, spawns.getSpawner(categoryOf(type, edited)).stream()
                            .filter(d -> d.type == type).toList());
                    apply(spawns, key, entry, edited);
                }
            });
            // ENTITY REPLACEMENT: after Strata's own plan, so a replaced mob's entries - the game's, a mod's,
            // or the ones Strata just set - go to the new mob.
            Replacements.applyToBiome(biome.unwrapKey().get().location(), spawns, replaced);
            // THE HOSTILE MOBS TAKE THE EMPTY SLOTS (option "Hostile mobs spawn in passive slots"): each
            // other category the list leaves empty gets the list's hostile entries, so a Replace biome with
            // only a skeleton fills its sheep's and cows' slots, and new chunks, with it.
            Map<net.minecraft.world.entity.MobCategory, Integer> counts = new java.util.LinkedHashMap<>();
            for (net.minecraft.world.entity.MobCategory category : net.minecraft.world.entity.MobCategory.values()) {
                if (category != net.minecraft.world.entity.MobCategory.MISC) {
                    counts.put(category, spawns.getSpawner(category).size());
                }
            }
            List<MobSpawnSettings.SpawnerData> hostile =
                    List.copyOf(spawns.getSpawner(net.minecraft.world.entity.MobCategory.MONSTER));
            for (net.minecraft.world.entity.MobCategory open : SpawnPlan.openSlots(c -> monstersTake(c, com.strata.data.Config.resolve(id).tier(), id),
                    replaces, counts, net.minecraft.world.entity.MobCategory.MONSTER)) {
                spawns.getSpawner(open).addAll(hostile);
            }
            com.strata.game.GameData.recordStrataReplaced(biome.value(), replaced);
        } catch (RuntimeException e) {
            // A world load must not fail over one biome's spawns.
            Strata.LOGGER.warn("Spawns: biome {} left as the game made it: {}", id, e.toString());
        }
    }

    /**
     * Whether the hostile mobs of a Replace list take this category's empty slots: the biome's or its tier's
     * rule, else the old global switch.
     */
    static boolean monstersTake(net.minecraft.world.entity.MobCategory category, String tier, String biome) {
        return switch (category) {
            case CREATURE -> AreaRules.on(AreaRules.MONSTERS_IN_CREATURE_SLOTS,
                    com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.MONSTERS_IN_CREATURE_SLOTS), tier, biome);
            case AMBIENT -> AreaRules.on(AreaRules.MONSTERS_IN_AMBIENT_SLOTS,
                    com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.MONSTERS_IN_AMBIENT_SLOTS), tier, biome);
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> AreaRules.on(
                    AreaRules.MONSTERS_IN_WATER_SLOTS,
                    com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.MONSTERS_IN_WATER_SLOTS), tier, biome);
            default -> false;
        };
    }

    /**
     * Moves each entry of an edited mob to its category's list (SpawnPlan#listFor), or out of every list for
     * misc.
     */
    private static void moveEdited(net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder spawns,
                                   Map<String, String> edited) {
        if (edited.isEmpty()) {
            return;
        }
        for (net.minecraft.world.entity.MobCategory from : net.minecraft.world.entity.MobCategory.values()) {
            List<MobSpawnSettings.SpawnerData> list = spawns.getSpawner(from);
            for (MobSpawnSettings.SpawnerData d : List.copyOf(list)) {
                String id = BuiltInRegistries.ENTITY_TYPE.getResourceKey(d.type)
                        .map(k -> k.location().toString()).orElse("");
                String to = SpawnPlan.listFor(id, from.getName(), edited);
                if (to.equals(from.getName())) {
                    continue;
                }
                list.remove(d);
                net.minecraft.world.entity.MobCategory target = category(to);
                if (target != null) {
                    spawns.getSpawner(target).add(d);
                }
            }
        }
    }

    /** The game's category of a name, or null (misc and unknown names hold no spawns here). */
    private static net.minecraft.world.entity.MobCategory category(String name) {
        for (net.minecraft.world.entity.MobCategory c : net.minecraft.world.entity.MobCategory.values()) {
            if (c.getName().equals(name) && c != net.minecraft.world.entity.MobCategory.MISC) {
                return c;
            }
        }
        return null;
    }

    /** The list a type's entries are in: its edited category's, else the type's own. */
    private static net.minecraft.world.entity.MobCategory categoryOf(EntityType<?> type, Map<String, String> edited) {
        String name = edited.get(BuiltInRegistries.ENTITY_TYPE.getResourceKey(type)
                .map(k -> k.location().toString()).orElse(""));
        net.minecraft.world.entity.MobCategory c = name == null ? null : category(name);
        return c != null ? c : type.getCategory();
    }

    /** What a biome gives an entity now: {weight, min, max}, or null for nothing. */
    private static int[] existing(net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder spawns, String key,
                                  Map<String, String> edited) {
        EntityType<?> type = type(key);
        if (type == null) {
            return null;
        }
        int weight = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (MobSpawnSettings.SpawnerData d : spawns.getSpawner(categoryOf(type, edited))) {
            if (d.type == type) {
                weight += d.getWeight().asInt();
                min = Math.min(min, d.minCount);
                max = Math.max(max, d.maxCount);
            }
        }
        return weight <= 0 ? null : new int[]{weight, min, max};
    }

    private static void apply(net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder spawns, String key,
                              SpawnPlan.Entry entry, Map<String, String> edited) {
        EntityType<?> type = type(key);
        if (type == null) {
            return;
        }
        List<MobSpawnSettings.SpawnerData> list = spawns.getSpawner(categoryOf(type, edited));
        list.removeIf(d -> d.type == type);
        if (entry.weight() > 0) {
            list.add(new MobSpawnSettings.SpawnerData(type, entry.weight(), entry.min(), entry.max()));
        }
    }

    private static EntityType<?> type(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return SPAWNS.get();
    }
}
