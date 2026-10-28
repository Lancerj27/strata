package com.strata.runtime;

import com.strata.model.Resolution;
import com.strata.model.Variant;

import com.strata.data.Config;

import com.strata.data.StrataConfig;
import com.strata.Strata;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Assigns a mob its level, coefficient, variant and attribute scaling at spawn. */
public class MobScalingHandler {

    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled()) {
            return;
        }

        Mob mob = event.getEntity();

        ServerLevelAccessor levelAccess = event.getLevel();
        Holder<Biome> biome = levelAccess.getBiome(mob.blockPosition());
        Resolution resolution = Config.resolve(
                biome.unwrapKey().map(key -> key.location()).orElse(null));

        // SPAWN CONDITIONS AT EVERY NATURAL SPAWN: they were checked only when a hostile mob was substituted.
        if (ReplaceSpawnRules.blocked(levelAccess, mob.blockPosition(), mob.getType(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
            return;
        }
        // A NEW CHUNK HAS NO PLAYER AWAKE: a mob whose every row needs one (a Time since rest rule, the
        // phantom's by default) is not placed in new land.
        if (event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION
                && com.strata.model.SpawnRule.needPlayer(rowsFor(mob, resolution).stream()
                .map(com.strata.model.Spawn::spawnRules).toList())) {
            event.setSpawnCancelled(true);
            return;
        }
        java.util.function.Predicate<com.strata.model.Spawn> allowed = s -> true;
        if (event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.NATURAL
                && levelAccess instanceof net.minecraft.server.level.ServerLevel level) {
            allowed = s -> s.spawnRules().isEmpty()
                    || SpawnRuleEvaluator.allMatch(s.spawnRules(), level, mob.blockPosition());
            if (!anyAllowed(rowsFor(mob, resolution), allowed)) {
                event.setSpawnCancelled(true);
                return;
            }
        }

        applyTo(mob, resolution, areaLevel(mob, resolution, resolution.level()), allowed);
    }

    /** THE CHUNK'S LOCAL DIFFICULTY COMPOUNDS THE AREA LEVEL. */
    public static int areaLevel(Mob mob, Resolution resolution, int level) {
        if (level <= 0 || !(mob.level() instanceof net.minecraft.server.level.ServerLevel server)
                || !com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.ENABLE_CHUNK_SCALING)) {
            return level;
        }
        double local = server.getCurrentDifficultyAt(mob.blockPosition()).getEffectiveDifficulty();
        double area = com.strata.model.ScalingFormulas.eval(com.strata.model.ScalingFormulas.Kind.AREA_LEVEL,
                com.strata.model.ScalingFormulas.vars("level", level, "local_difficulty", local,
                        "chunk_scale", resolution.chunkScale()));
        return Double.isFinite(area) ? Math.max(0, (int) Math.round(area)) : level;
    }

    /** The roster rows that spawn as this mob's type (ReplaceSpawnRules#rowsFor). */
    private static java.util.List<com.strata.model.Spawn> rowsFor(Mob mob, Resolution resolution) {
        return ReplaceSpawnRules.rowsFor(resolution, mob.getType());
    }

    /**
     * Whether a natural spawn may go ahead: no roster row speaks for the mob, or one of its rows passes its
     * conditions.
     */
    public static boolean anyAllowed(java.util.List<com.strata.model.Spawn> rows,
                                     java.util.function.Predicate<com.strata.model.Spawn> allowed) {
        return rows.isEmpty() || rows.stream().anyMatch(allowed);
    }

    /**
     * The whole spawn pipeline for one mob, reusable by the natural spawn path above and by {@code /strata
     * test spawn}.
     */
    public static void applyTo(Mob mob, Resolution resolution, int level) {
        applyTo(mob, resolution, level, s -> true);
    }

    /** Spawns a mob at its VARIANT'S START LEVEL. */
    public static void applyToAtVariantLevel(Mob mob, Resolution resolution) {
        applyTo(mob, resolution, 0, s -> true);
    }

    /** Spawns a mob at its VARIANT'S START LEVEL with a spawn predicate. */
    public static void applyToAtVariantLevel(Mob mob, Resolution resolution,
                                              java.util.function.Predicate<com.strata.model.Spawn> allowed) {
        applyTo(mob, resolution, 0, allowed);
    }

    /**
     * As above, the variant rolled among the roster rows {@code allowed} passes (a natural spawn's
     * conditions).
     */
    public static void applyTo(Mob mob, Resolution resolution, int level,
                               java.util.function.Predicate<com.strata.model.Spawn> allowed) {
        double coefficient = resolution.coefficient();

        // RS-7: Registry#getKey returns NULL for a type that is not registered (a mod spawning a throwaway
        // type, a partially-loaded registry), and this runs on the spawn bus where a throw is a game crash.
        net.minecraft.resources.ResourceLocation typeKey =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        String entityId = typeKey == null ? "" : typeKey.toString();
        String lookupId = VariantEntityRegistry.baseEntityIdForType(mob.getType())
                .orElse(entityId);
        Double mobCoeff = com.strata.model.Spawn.coefficientIn(resolution::mobs, lookupId);
        if (mobCoeff != null) {
            coefficient *= mobCoeff;
        }

        EntityBaseAttributes.apply(mob);
        // Made under the current config, so a later regenerate knows it is up to date.
        mob.setData(MobLevel.GENERATION, Regeneration.current());

        Variant variant = VariantHandler.roll(mob, resolution, mob.getRandom(), allowed);
        if (variant != null) {
            coefficient *= variant.coefficient();
            applyVariant(mob, variant, resolution);
        } else {
            applyPlainExtras(mob, lookupId, resolution);
        }

        // THE MOB'S OWN SWITCH DECIDES FIRST: its variant's, else its base mob's, else the Scaling screen's.
        String variantId = variant != null ? variant.id() : Variant.defaultNameFor(lookupId);
        Boolean own = MobScalingSwitch.own(variantId, VariantHandler.baseOf(variantId), resolution.tier(),
                resolution.biome());
        boolean scalingEnabled = own != null ? own : StrataConfig.ENABLE_MOB_SCALING.get();

        // READ THE VARIANT'S START LEVEL.
        int startLevel = MobScalingSwitch.startLevel(variantId, VariantHandler.baseOf(variantId),
                resolution.tier(), resolution.biome());

        // LEVEL 0 IS "AT ITS OWN LEVEL" (/strata test spawn <entity> 0): the area level is ignored as if
        // scaling were off, so the mob is its start level.
        if (level <= 0) {
            scalingEnabled = false;
        }

        // EFFECTIVE LEVEL stored on the mob: biome level when scaling on, variant's start level when off.
        int effectiveLevel = scalingEnabled ? level : startLevel;
        mob.setData(MobLevel.MOB_LEVEL, new MobLevel(effectiveLevel));
        // Store the APPLIED coefficient (biome x variant) so a later reload reapplies the same scaling this
        // mob already received.
        mob.setData(MobLevel.COEFFICIENT, coefficient);

        // APPLY SCALING: when enabled, scale from startLevel to biome level (delta); when disabled, scale
        // from level 1 to startLevel so the mob has stats at its natural level and ignores the biome.
        if (scalingEnabled) {
            MobAttributeScaling.applyFromBase(mob, level, startLevel,
                    resolution, coefficient, own);
        } else {
            MobAttributeScaling.apply(mob, startLevel, resolution, coefficient, own);
        }

        // The ordering is kept either way: multipliers ran BEFORE the scaling block, absolutes run AFTER
        // it.
        if (variant != null) {
            VariantHandler.applyAbsolutes(mob, variant);
        }
        VariantHandler.refreshHealth(mob);

        Strata.LOGGER.debug("{} -> level {}, coefficient {}{}",
                mob.getType().getDescription().getString(), level, coefficient,
                scalingEnabled ? "" : " (scaling disabled; composition applied)");
    }

    /** A PLAIN MOB'S EQUIPMENT, ABILITIES, AND BEHAVIOR. */
    private static void applyPlainExtras(Mob mob, String entityId, Resolution resolution) {
        String extras = Variant.defaultNameFor(entityId);
        // THE SCOPE IS STORED EVEN WITH NO EXTRAS YET: the editor makes the default variant later, and a
        // tier's or biome's goals then need to know where this mob spawned.
        mob.setData(MobLevel.VARIANT_SCOPE, ScopeIndex.packScope(resolution.tier(), resolution.biome()));
        if (extras.isEmpty() || !Config.tables().variants().containsKey(extras)) {
            // No extras, but Strata still runs the mob's default equipment, so the join must
            // see it.
            markEquipment(mob);
            return;
        }
        mob.setData(MobLevel.VARIANT_ID, extras);
        mob.setData(MobLevel.VARIANT_SCOPE,
                ScopeIndex.packScope(resolution.tier(), resolution.biome()));
        MobProperties.forget(mob);
        VariantHandler.applyAtSpawn(mob, extras, resolution.tier(), resolution.biome());
        mob.setData(MobLevel.SPAWN_TRIGGER_PENDING, true);
        markEquipment(mob);
    }

    /** Marks the mob for its equipment at the join, unless its spawn already gave it equipment. */
    /**
     * Makes the mob this variant, as a natural spawn of it is made: its scope, stats, spawn abilities, spawn
     * triggers, and its equipment and behavior at the join (after the game's own finalizeSpawn).
     */
    public static void applyVariant(Mob mob, Variant variant, Resolution resolution) {
        mob.setData(MobLevel.VARIANT_SCOPE, ScopeIndex.packScope(resolution.tier(), resolution.biome()));
        MobProperties.forget(mob);
        VariantHandler.applyMultipliers(mob, variant);
        VariantHandler.applyAtSpawn(mob, variant.id(), resolution.tier(), resolution.biome());
        mob.setData(MobLevel.SPAWN_TRIGGER_PENDING, true);
        markEquipment(mob);
    }

    private static void markEquipment(Mob mob) {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.ANIMAL_ARMOR
                    && !mob.getItemBySlot(slot).isEmpty()) {
                return;
            }
        }
        mob.setData(MobLevel.EQUIPMENT_PENDING, true);
    }

    /** Puts on the equipment and behavior of a mob marked at spawn, once it has joined its level. */
    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) {
            MobEquipment.onJoin(mob);
            // After the equipment, so an on_spawn row sees the mob as it will be.
            VariantHandler.fireSpawnTriggers(mob);
        }
        // The edited fields of the mob's values, at every join: the game does not save most of them.
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                && !(living instanceof net.minecraft.world.entity.player.Player)) {
            MobDataRuntime.apply(living);
        }
    }
}
