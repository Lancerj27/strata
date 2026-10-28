package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Schema;
import com.strata.model.ReplacementPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** ENTITY REPLACEMENT IN THE WORLD. */
public final class Replacements {

    private static volatile List<ReplacementPlan.Rule> rules = List.of();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** Reads replacements.csv (Config#reload). */
    public static void load(Path file) {
        List<ReplacementPlan.Rule> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(file)) {
            ReplacementPlan.Rule r = ReplacementPlan.rule(row.get(Schema.Replacements.SCOPE),
                    row.get(Schema.Replacements.SCOPE_ID), row.get(Schema.Replacements.FROM_ENTITY),
                    row.get(Schema.Replacements.TO_ENTITY), row.get(Schema.Replacements.SHARE_PCT),
                    row.get(Schema.Replacements.SOURCES));
            if (r != null) {
                out.add(r);
            }
        }
        rules = List.copyOf(out);
    }

    /** The rules in force in one biome. */
    public static Map<String, ReplacementPlan.Rule> in(ResourceLocation biome) {
        if (rules.isEmpty() || biome == null) {
            return Map.of();
        }
        return ReplacementPlan.rulesFor(rules, Config.resolve(biome).tier(), biome.toString());
    }

    /** The entity type an id names, or null (warned once) when the game has none. */
    static EntityType<?> type(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            return BuiltInRegistries.ENTITY_TYPE.get(rl);
        }
        if (WARNED.add(id)) {
            Strata.LOGGER.warn("Replacements: no entity {} in this game - its rule is skipped", id);
        }
        return null;
    }

    /**
     * The biome's spawn lists with each replaced mob's entries handed over.
     *
     * @param originals filled with each changed type's entries as they were, so GameData still reports the
     *     game's spawns (a type already there is kept)
     */
    static void applyToBiome(ResourceLocation biome, MobSpawnSettingsBuilder spawns,
                             Map<EntityType<?>, List<MobSpawnSettings.SpawnerData>> originals) {
        Map<String, ReplacementPlan.Rule> here = in(biome);
        if (here.isEmpty()) {
            return;
        }
        for (net.minecraft.world.entity.MobCategory category : net.minecraft.world.entity.MobCategory.values()) {
            for (MobSpawnSettings.SpawnerData d : spawns.getSpawner(category)) {
                String id = BuiltInRegistries.ENTITY_TYPE.getResourceKey(d.type).map(k -> k.location().toString()).orElse("");
                ReplacementPlan.Rule rule = here.get(id);
                EntityType<?> to = rule == null ? null : type(rule.to());
                if (to != null) {
                    for (EntityType<?> t : List.of(d.type, to)) {
                        originals.computeIfAbsent(t, k -> {
                            List<MobSpawnSettings.SpawnerData> was = new ArrayList<>();
                            for (var c : net.minecraft.world.entity.MobCategory.values()) {
                                spawns.getSpawner(c).stream().filter(x -> x.type == k).forEach(was::add);
                            }
                            return List.copyOf(was);
                        });
                    }
                }
            }
        }
        for (net.minecraft.world.entity.MobCategory category : net.minecraft.world.entity.MobCategory.values()) {
            List<MobSpawnSettings.SpawnerData> list = spawns.getSpawner(category);
            for (MobSpawnSettings.SpawnerData d : List.copyOf(list)) {
                String id = BuiltInRegistries.ENTITY_TYPE.getResourceKey(d.type)
                        .map(k -> k.location().toString()).orElse("");
                ReplacementPlan.Rule rule = here.get(id);
                if (rule == null || type(rule.to()) == null) {
                    continue;
                }
                list.remove(d);
                for (ReplacementPlan.Piece piece : ReplacementPlan.split(id, d.getWeight().asInt(), rule)) {
                    EntityType<?> t = piece.entity().equals(id) ? d.type : type(piece.entity());
                    list.add(new MobSpawnSettings.SpawnerData(t, piece.weight(), d.minCount, d.maxCount));
                }
            }
        }
    }

    /** True while a replacement spawns, so it is not replaced again. */
    private static final ThreadLocal<Boolean> SWAPPING = ThreadLocal.withInitial(() -> false);

    /** A player's own spawns are theirs: an egg, a command, a bucket, breeding. */
    static boolean playerMade(MobSpawnType type) {
        return switch (type) {
            case SPAWN_EGG, COMMAND, BUCKET, BREEDING, NATURAL, CHUNK_GENERATION -> true;
            default -> false;
        };
    }

    /** The other sources: last, after every other mod, the from-mob is swapped as it spawns. */
    public static final class Events {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onFinalizeSpawn(FinalizeSpawnEvent event) {
            // NATURAL and CHUNK_GENERATION came from the biome's lists, changed already.
            if (rules.isEmpty() || SWAPPING.get() || event.isSpawnCancelled()
                    || playerMade(event.getSpawnType())
                    || !(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            Mob mob = event.getEntity();
            ResourceLocation biome = level.getBiome(mob.blockPosition()).unwrapKey().map(k -> k.location()).orElse(null);
            String id = BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType()).map(k -> k.location().toString()).orElse("");
            ReplacementPlan.Rule rule = in(biome).get(id);
            if (rule == null || !rule.everySource() || mob.getRandom().nextInt(100) >= rule.share()) {
                return;
            }
            EntityType<?> to = type(rule.to());
            if (to == null) {
                return;
            }
            SWAPPING.set(true);
            try {
                Entity created = to.create(level);
                if (!(created instanceof Mob replacement)) {
                    if (created != null) {
                        created.discard();
                    }
                    return;
                }
                event.setSpawnCancelled(true);
                replacement.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
                EventHooks.finalizeMobSpawn(replacement, level, event.getDifficulty(), event.getSpawnType(), null);
                level.addFreshEntityWithPassengers(replacement);
            } catch (RuntimeException e) {
                event.setSpawnCancelled(false);
                Strata.LOGGER.warn("Replacements: {} -> {} failed: {}", id, rule.to(), e.toString());
            } finally {
                SWAPPING.set(false);
            }
        }
    }

    private Replacements() {
    }
}
