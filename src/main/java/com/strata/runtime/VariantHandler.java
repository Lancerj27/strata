package com.strata.runtime;

import com.strata.Strata;
import com.strata.model.Resolution;
import com.strata.model.Spawn;
import com.strata.model.WeightedPick;
import com.strata.model.SpawnCategory;
import com.strata.model.Variant;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consolidated variant handler: rolling, stat application, ability ticks, damage handlers, equipment, and
 * texture indexing.
 */
public class VariantHandler {

    // ---- Rolling ----

    public static Variant roll(LivingEntity entity, Resolution resolution, RandomSource random) {
        return roll(entity, resolution, random, s -> true);
    }

    /** As above, among the roster rows {@code allowed} passes: a natural spawn's conditions. */
    public static Variant roll(LivingEntity entity, Resolution resolution, RandomSource random,
                               java.util.function.Predicate<Spawn> allowed) {
        List<Variant> candidates = resolution.variants();
        if (candidates.isEmpty()) {
            return null;
        }

        Optional<String> promotedId = VariantEntityRegistry.variantIdForType(entity.getType());
        if (promotedId.isPresent()) {
            return rollPromoted(candidates, promotedId);
        }

        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String id = type == null ? "" : type.toString();
        if (id.isEmpty()) {
            return null;
        }

        return rollRostered(id, resolution, random, allowed);
    }

    /**
     * The promoted-variant branch of {@link #roll}: a promoted variant is rolled deterministically — the
     * registry resolved its id from the entity's (derived) type, so the matching roster candidate is chosen,
     * or null when the resolution does not roster that variant here.
     *
     * @return the matching candidate variant, or null when {@code promotedId} is empty or does not match a
     *     candidate.
     */
    public static Variant rollPromoted(List<Variant> candidates, Optional<String> promotedId) {
        if (promotedId.isEmpty()) {
            return null;
        }
        for (Variant v : candidates) {
            if (v.id().equals(promotedId.get())) {
                return v;
            }
        }
        return null;
    }

    private static Variant rollRostered(String entityId, Resolution resolution,
                                        RandomSource random, java.util.function.Predicate<Spawn> allowed) {
        List<Spawn> rows = new ArrayList<>();
        int total = 0;
        for (SpawnCategory category : SpawnCategory.values()) {
            for (Spawn spawn : resolution.mobs(category)) {
                if (!spawn.entity().equals(entityId) || spawn.weight() <= 0 || !allowed.test(spawn)) {
                    continue;
                }
                // A variant with an entity of its own spawns from its own entry (SpawnsModifier#keyOf), never
                // out of its base mob's.
                if (spawn.isVariant() && VariantEntityRegistry.isPromoted(spawn.variant())) {
                    continue;
                }
                rows.add(spawn);
                total += spawn.weight();
            }
        }
        if (total <= 0) {
            return null;
        }

        // nextInt over the total, exactly as before: changing the draw to a double would change which variant
        // a given world seed produces.
        int[] weights = new int[rows.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = rows.get(i).weight();
        }
        int winner = WeightedPick.index(weights, random.nextInt(total));
        if (winner == WeightedPick.NONE) {
            return null;
        }

        Spawn spawn = rows.get(winner);
        if (!spawn.isVariant()) {
            return null;
        }
        for (Variant variant : resolution.variants()) {
            if (variant.id().equals(spawn.variant())) {
                return variant;
            }
        }
        return null;
    }

    // ---- Stat application ----

    public static void applyMultipliers(LivingEntity entity, Variant variant) {
        entity.setData(MobLevel.VARIANT_ID, variant.id());
        MobProperties.forget(entity);
        entity.setCustomName(Component.literal(variant.displayName()));

        multipliersOf(entity, variant).forEach((attributeId, multiplier) -> {
            AttributeInstance instance = attributeInstance(entity, attributeId);
            if (instance != null && multiplier != 1.0) {
                instance.setBaseValue(instance.getBaseValue() * multiplier);
            }
        });
    }

    /** Undoes the multiplier application for a reload, so re-applying does not compound. */
    public static void resetMultipliers(LivingEntity entity, Variant variant) {
        Map<String, Double> multipliers = multipliersOf(entity, variant);
        if (multipliers.isEmpty()) {
            return;
        }
        // The base mob's edits for an own-entity variant, as EntityBaseAttributes#apply.
        ResourceLocation entityId = EntityBaseAttributes.statOwner(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                VariantEntityRegistry.baseEntityIdForType(entity.getType()));
        Map<ResourceLocation, Double> vanillaDefaults = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Double> entry : EntityBaseAttributes.defaultsFor(entity.getType())) {
            vanillaDefaults.put(entry.getKey(), entry.getValue());
        }

        multipliers.forEach((attributeId, multiplier) -> {
            if (multiplier == 1.0) {
                return;
            }
            ResourceLocation attrId = ResourceLocation.tryParse(attributeId);
            if (attrId == null) {
                return;
            }
            if (entityId != null && EntityBaseAttributes.overrideFor(entityId, attrId) != null) {
                return;
            }
            AttributeInstance instance = attributeInstance(entity, attributeId);
            if (instance == null) {
                return;
            }
            Double vanilla = vanillaDefaults.get(attrId);
            if (vanilla != null) {
                instance.setBaseValue(vanilla);
            }
        });
    }

    /** The multipliers of the instance the mob spawned in (MobLevel#VARIANT_SCOPE). */
    private static Map<String, Double> multipliersOf(LivingEntity entity, Variant variant) {
        String packed = entity.getExistingData(MobLevel.VARIANT_SCOPE).orElse("");
        return variant.multipliersIn(ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
    }

    public static void applyAbsolutes(LivingEntity entity, Variant variant) {
        String packed = entity.getExistingData(MobLevel.VARIANT_SCOPE).orElse("");
        Map<String, Double> absolutes = variant.absolutesIn(ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
        if (absolutes.isEmpty()) {
            return;
        }
        boolean healthChanged = false;
        for (Map.Entry<String, Double> entry : absolutes.entrySet()) {
            AttributeInstance instance = attributeInstance(entity, entry.getKey());
            if (instance == null) {
                continue;
            }
            instance.setBaseValue(entry.getValue());
            if (isMaxHealth(entry.getKey())) {
                healthChanged = true;
            }
        }
        if (healthChanged) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /** Whether a CSV attribute id names max health, however it was written. */
    public static boolean isMaxHealth(String attributeId) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        return id != null && id.equals(MAX_HEALTH_ID);
    }

    private static final ResourceLocation MAX_HEALTH_ID =
            ResourceLocation.parse("minecraft:generic.max_health");

    public static void refreshHealth(LivingEntity entity) {
        entity.setHealth(entity.getMaxHealth());
    }

    private static AttributeInstance attributeInstance(LivingEntity entity, String attributeId) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            return null;
        }
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE, id))
                .orElse(null);
        return holder == null ? null : entity.getAttribute(holder);
    }

    // ---- Texture indexing ----

    private static volatile Map<String, ResourceLocation> textureByName = Map.of();

    /** Rebuilds the display-name → texture index the client renders from. */
    public static void indexTextures(List<Variant> variants) {
        Map<String, ResourceLocation> out = new LinkedHashMap<>();
        Map<String, String> ownerOfName = new LinkedHashMap<>();
        for (Variant variant : variants) {
            // A variant with no texture stores "", not null (Tables), and "" must not be read as a texture
            // path.
            if (variant.texture() == null || variant.texture().isBlank()) {
                continue;
            }
            ResourceLocation texture = ResourceLocation.tryParse(variant.texture());
            if (texture == null) {
                Strata.LOGGER.warn("variants.csv: {} has an invalid texture path '{}' - ignoring",
                        variant.id(), variant.texture());
                continue;
            }
            String name = variant.displayName();
            String previous = ownerOfName.put(name, variant.id());
            if (previous != null && !previous.equals(variant.id())) {
                Strata.LOGGER.warn("variants.csv: '{}' and '{}' share the display name "
                        + "'{}', so only one texture can render for both - give them "
                        + "distinct display names", previous, variant.id(), name);
            }
            out.put(name, texture);
        }
        textureByName = Map.copyOf(out);
    }

    /** The texture index as text, display name to texture, for a server to send. */
    public static Map<String, String> textureIndex() {
        Map<String, String> out = new LinkedHashMap<>();
        textureByName.forEach((name, texture) -> out.put(name, texture.toString()));
        return out;
    }

    /**
     * Uses a server's texture index on this game, so a player sees the server's variant textures with no
     * config on their machine.
     */
    public static void useTextureIndex(Map<String, String> index) {
        Map<String, ResourceLocation> out = new LinkedHashMap<>();
        index.forEach((name, texture) -> {
            ResourceLocation parsed = ResourceLocation.tryParse(texture);
            if (parsed != null && !texture.isBlank()) {
                out.put(name, parsed);
            }
        });
        textureByName = Map.copyOf(out);
    }

    public static ResourceLocation textureFor(LivingEntity entity) {
        if (textureByName.isEmpty()) {
            return null;
        }
        Component name = entity.getCustomName();
        return name == null ? null : textureByName.get(name.getString());
    }

    // ---- Ability event handlers (dispatched via Ability enum strategies) ----

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        boolean sunlightRows = EffectTable.hasTrigger("in_sunlight")
                || EffectTable.hasTrigger(TriggeredEffects.PERMANENT);
        if (!MobAbilities.anyTicking() && !sunlightRows) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity entity)
                || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        String variant = MobAbilities.variantOf(entity);
        if (variant.isEmpty()) {
            return;
        }

        for (MobAbilities.AbilityEntry entry : MobAbilities.forEntity(entity)) {
            entry.ability().onTick(entity, entry, level);
        }

        // TRIGGER EVALUATION: evaluate "in_sunlight" triggers.
        if (sunlightRows) {
            evaluateTriggers(entity, level, variant);
        }
    }

    /** Fires effect triggers for an entity with the given trigger name. */
    private static void fireEffectTriggers(LivingEntity entity, ServerLevel level, String variant,
                                           String triggerName, LivingEntity holder, LivingEntity target) {
        String packed = entity.getData(MobLevel.VARIANT_SCOPE);
        String tier = ScopeIndex.tierOf(packed);
        String biome = ScopeIndex.biomeOf(packed);
        // The subject of the trigger's condition (the one in the sun, the one whose interval counts) is the
        // entity the event is about.
        TriggeredEffects.fire(EffectTable.forVariant(variant, tier, biome), triggerName, entity, holder, target);
    }

    /** The tick triggers: in sunlight, and Permanent every interval. */
    private static void evaluateTriggers(LivingEntity entity, ServerLevel level, String variant) {
        if (EffectTable.hasTrigger("in_sunlight")) {
            fireEffectTriggers(entity, level, variant, "in_sunlight", entity, entity);
        }
        if (EffectTable.hasTrigger(TriggeredEffects.PERMANENT)) {
            fireEffectTriggers(entity, level, variant, TriggeredEffects.PERMANENT, entity, entity);
        }
    }

    /**
     * The two of the six classes that keep running: damage over time until its duration is up, and an
     * attribute change until its duration is up or the cure item is held.
     */
    @SubscribeEvent
    public void onEffectTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity
                && entity.level() instanceof ServerLevel level) {
            EffectRuntime.tick(entity, level);
        }
    }

    /**
     * Fires a mob's on_spawn rows, once, at its first join after the spawn that gave it its variant
     * (MobLevel#SPAWN_TRIGGER_PENDING).
     */
    public static void fireSpawnTriggers(LivingEntity entity) {
        if (!entity.getData(MobLevel.SPAWN_TRIGGER_PENDING)
                || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        entity.removeData(MobLevel.SPAWN_TRIGGER_PENDING);
        String variant = MobAbilities.variantOf(entity);
        if (!variant.isEmpty() && EffectTable.hasTrigger("on_spawn")) {
            fireEffectTriggers(entity, level, variant, "on_spawn", entity, entity);
        }
    }

    /**
     * On flee: a mob starts to run - panicking, avoiding what it fears (a skeleton from a wolf), or fleeing
     * the sun.
     */
    public static void onFlee(net.minecraft.world.entity.Mob mob) {
        if (mob.level() instanceof ServerLevel level && EffectTable.hasTrigger("on_flee")) {
            String variant = MobAbilities.variantOf(mob);
            if (!variant.isEmpty()) {
                fireEffectTriggers(mob, level, variant, "on_flee", mob, mob);
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof LivingEntity entity) {
            String variant = MobAbilities.variantOf(entity);
            if (!variant.isEmpty()) {
                fireEffectTriggers(entity, (ServerLevel) entity.level(), variant, "on_death", entity, entity);
            }
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        boolean hurtRows = EffectTable.hasTrigger("on_take_damage") || EffectTable.hasTrigger("on_hurt");
        if (!MobAbilities.anyImmunity() && !MobAbilities.anyOnHurt() && !hurtRows) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        String variant = MobAbilities.variantOf(entity);
        if (variant.isEmpty()) {
            return;
        }

        for (MobAbilities.AbilityEntry entry : MobAbilities.forEntity(entity)) {
            if (entry.ability().onIncomingDamage(entity, entry, event)) {
                event.setCanceled(true);
                return;
            }
        }

        // FIRE EFFECT TRIGGERS: on_take_damage / on_hurt (synonyms) Holder = victim, target = attacker (for
        // effects like life_steal)
        if (!hurtRows) {
            return;
        }
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
        if (attacker == null) {
            return;
        }
        fireEffectTriggers(entity, (ServerLevel) entity.level(), variant, "on_take_damage", entity, attacker);
        fireEffectTriggers(entity, (ServerLevel) entity.level(), variant, "on_hurt", entity, attacker);
    }

    @SubscribeEvent
    public void onDamageDealt(LivingDamageEvent.Post event) {
        if (!MobAbilities.anyOnHit() && !EffectTable.hasTrigger("on_hit")) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        String variant = MobAbilities.variantOf(attacker);
        if (variant.isEmpty()) {
            return;
        }

        // THE ATTACKER's abilities, resolved in the scope the ATTACKER spawned in - it is the one that has
        // on-hit effects, and it may have spawned in a different biome from its victim.
        for (MobAbilities.AbilityEntry entry : MobAbilities.forEntity(attacker)) {
            entry.ability().onDamageDealt(victim, entry);
        }

        // FIRE EFFECT TRIGGER: on_hit Holder = attacker, target = victim
        String packed = attacker.getData(MobLevel.VARIANT_SCOPE);
        TriggeredEffects.fire(EffectTable.forVariant(variant, ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed)),
                "on_hit", victim, attacker, victim);
    }

    /**
     * The extras variant a loaded variant inherits from (Variant#inheritsFrom), or "" for a plain mob's own
     * or an unknown name.
     */
    public static String baseOf(String variantId) {
        Variant variant = variantId == null ? null
                : com.strata.data.Config.tables().variants().get(variantId);
        return variant == null ? "" : variant.inheritsFrom();
    }

    /** Spawn-time abilities for the instance of this variant in THIS scope. */
    public static void applyAtSpawn(LivingEntity entity, String variant,
                                    String tier, String biome) {
        for (MobAbilities.AbilityEntry entry
                : MobAbilities.forVariant(variant, tier, biome)) {
            entry.ability().onSpawn(entity, entry);
        }
    }
}
