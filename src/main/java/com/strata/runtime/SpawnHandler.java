package com.strata.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import com.strata.model.SpawnCategory;
import com.strata.model.Spawn;
import com.strata.model.WeightedPick;
import com.strata.model.Resolution;

import com.strata.data.Config;
import com.strata.data.StrataConfig;
import com.strata.Strata;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawn substitution: replaces the spawned entity type at FinalizeSpawnEvent with one from the biome's
 * resolved roster.
 */
public class SpawnHandler {

    private static final Set<String> SUBSTITUTING_NOW = ConcurrentHashMap.newKeySet();

    private static String guardKey(ResourceKey<Level> dimension, long posLong) {
        return dimension.location() + "@" + posLong;
    }

    // After other mods, and before MobScalingHandler (LOWEST): the swap comes before the scaling.
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get()) {
            return;
        }

        Mob mob = event.getEntity();

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        if (VariantEntityRegistry.isPromotedType(mob.getType())) {
            return;
        }

        String guardKey = guardKey(serverLevel.dimension(), mob.blockPosition().asLong());
        if (SUBSTITUTING_NOW.contains(guardKey)) {
            return;
        }

        Holder<Biome> biomeHolder = serverLevel.getBiome(mob.blockPosition());
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) {
            return;
        }

        Resolution resolution = Config.resolve(biomeKey.get().location());

        if (!resolution.allows(resolution.substituteSpawns(),
                StrataConfig.ENABLE_SPAWN_SUBSTITUTION.get())) {
            return;
        }

        List<Spawn> roster = resolution.mobs(SpawnCategory.MONSTER);
        // The game's own list alone: nothing of the designer's to substitute.
        if (roster.stream().allMatch(s -> s.source().layer() == com.strata.model.Source.Layer.GAME)) {
            return;
        }
        String baseKey = BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType())
                .map(k -> k.location().toString()).orElse("");
        // A natural spawn of a listed mob, in a biome Strata configures.
        if (!com.strata.model.SpawnPlan.substitutes(event.getSpawnType() == MobSpawnType.NATURAL,
                resolution.configured(), roster.stream().anyMatch(s -> SpawnsModifier.keyOf(s).equals(baseKey)))) {
            return;
        }

        List<Spawn> eligible = filterBySpawnRules(roster, serverLevel, mob);
        if (eligible.isEmpty()) {
            return;
        }

        EntityType<?> baseType = mob.getType();
        RandomSource random = serverLevel.getRandom();

        pickSubstitute(eligible, baseType, random)
                .filter(t -> t.getCategory() == MobCategory.MONSTER)
                .ifPresent(substituteType -> {
            event.setSpawnCancelled(true);

            SUBSTITUTING_NOW.add(guardKey);
            try {
                Entity created = substituteType.create(serverLevel);
                if (!(created instanceof Mob replacement)) {
                    if (created != null) {
                        created.discard();
                    }
                    // The original spawns after all: it stayed cancelled.
                    event.setSpawnCancelled(false);
                    Strata.LOGGER.warn("Biome {} lists non-mob entity type {} — ignoring",
                            biomeKey.get().location(), substituteType.getDescription().getString());
                    return;
                }

                replacement.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());

                EventHooks.finalizeMobSpawn(replacement, serverLevel,
                        event.getDifficulty(), MobSpawnType.NATURAL, null);

serverLevel.addFreshEntity(replacement);
                Strata.LOGGER.debug("Substituted {} -> {} in {}",
                        baseType.getDescription().getString(),
                        substituteType.getDescription().getString(),
                        biomeKey.get().location());
            } catch (Exception e) {
                // RS-2: NOTHING ON THE BUS.
                event.setSpawnCancelled(false);
                Strata.LOGGER.warn("Spawn substitution {} -> {} in {} failed: {}",
                        baseType.getDescription().getString(),
                        substituteType.getDescription().getString(),
                        biomeKey.get().location(), e.toString(), e);
            } finally {
                SUBSTITUTING_NOW.remove(guardKey);
            }
        });
    }

    private static List<Spawn> filterBySpawnRules(List<Spawn> roster, ServerLevel level, Mob mob) {
        List<Spawn> out = new ArrayList<>();
        for (Spawn spawn : roster) {
            if (!spawn.enabled()) {
                continue;
            }
            if (spawn.spawnRules().isEmpty() ||
                    SpawnRuleEvaluator.allMatch(spawn.spawnRules(), level, mob.blockPosition())) {
                out.add(spawn);
            }
        }
        return out;
    }

    private static Optional<EntityType<?>> pickSubstitute(List<Spawn> roster,
                                                          EntityType<?> baseType,
                                                          RandomSource random) {
        int[] weights = new int[roster.size()];
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.max(0, roster.get(i).weight());
            total += weights[i];
        }
        if (total <= 0) {
            return Optional.empty();
        }

        // nextInt over the total, exactly as before: the draw is part of what a world seed reproduces.
        int winner = WeightedPick.index(weights, random.nextInt(total));
        if (winner == WeightedPick.NONE) {
            return Optional.empty();
        }

        Spawn spawn = roster.get(winner);
        ResourceLocation id = ResourceLocation.tryParse(spawn.entity());
        if (id == null) {
            return Optional.empty();
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return Optional.empty();
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return type == baseType ? Optional.empty() : Optional.of(type);
    }
}
