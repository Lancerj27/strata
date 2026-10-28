package com.strata.runtime;

import com.strata.Strata;
import com.strata.domain.EffectOps.EffectRow;
import com.strata.model.EffectClass;
import com.strata.runtime.EffectTable.EffectEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** THE SIX BEHAVIOUR CLASSES, RUN. */
public final class EffectRuntime {

    private EffectRuntime() {
    }

    /** One thing running on one entity: what is running, whose row it is, and when it started. */
    private record Run(EffectEntry entry, LivingEntity holder, long appliedAt) {
    }

    /** Runs in progress, keyed on the entity they act on. */
    private static final Map<LivingEntity, List<Run>> RUNS = new WeakHashMap<>();

    /** A row's own particle shown on a target while its effect lasts. */
    private record Shown(net.minecraft.core.particles.ParticleOptions particle, long until,
                         Holder<net.minecraft.world.effect.MobEffect> whileHas) {
    }

    private static final Map<LivingEntity, List<Shown>> SHOWN = new WeakHashMap<>();

    /** How often a lasting particle is shown again, in ticks. */
    private static final int SHOW_EVERY = 10;

    /** The row's particle, or null for the game's own. */
    /** False when the row's HUD icon is "none": the effect shows no icon. */
    static boolean showsIcon(EffectRow row) {
        return HudIcons.shows(row.hudIcon());
    }

    static net.minecraft.core.particles.ParticleOptions particleOf(EffectRow row) {
        String id = row.particle() == null ? "" : row.particle().strip();
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        var type = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(rl);
        return type instanceof net.minecraft.core.particles.SimpleParticleType simple ? simple : null;
    }

    /** Shows the row's particle on the target now and, for a lasting effect, while it lasts. */
    private static void show(EffectEntry entry, LivingEntity target, ServerLevel level, int duration,
                             Holder<net.minecraft.world.effect.MobEffect> whileHas) {
        var particle = particleOf(entry.row());
        if (particle == null) {
            return;
        }
        burst(level, target, particle);
        if (duration != 0) {
            SHOWN.computeIfAbsent(target, k -> new ArrayList<>(1)).add(new Shown(particle,
                    duration < 0 ? Long.MAX_VALUE : level.getGameTime() + duration, whileHas));
        }
    }

    private static void burst(ServerLevel level, LivingEntity target, net.minecraft.core.particles.ParticleOptions p) {
        level.sendParticles(p, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6,
                target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.02);
    }

    /** Damage types this run has already been told name nothing, so a bad id warns once. */
    private static final Set<ResourceKey<DamageType>> UNKNOWN_DAMAGE_TYPES = ConcurrentHashMap.newKeySet();

    /** The game's explosion for the boom and the blocks, with ITS OWN ENTITY DAMAGE WITHHELD. */
    private static final ExplosionDamageCalculator NO_ENTITY_DAMAGE = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    /** Runs one class row once, against the two entities it can act on. */
    public static void apply(EffectEntry entry, LivingEntity holder, LivingEntity target) {
        if (entry == null || holder == null || target == null) {
            return;
        }
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        EffectRow row = entry.row();
        switch (row.effectClass()) {
            case APPLY_EFFECT -> target.addEffect(instance(entry));
            case DAMAGE_OVER_TIME -> start(entry, holder, target, level);
            case LIFE_STEAL -> lifeSteal(entry, holder, target, level);
            case ATTRIBUTE_CHANGE -> attributeChange(entry, target, level);
            case EXPLODE -> explode(entry, target, level);
            case EXPLODE_AND_DIE -> {
                if (target.isAlive()) {
                    explode(entry, target, level);
                    // As the game ends an exploded creeper: gone, with no death and no drops. A mob its own
                    // blast killed has died as usual.
                    if (target.isAlive() && !(target instanceof net.minecraft.world.entity.player.Player)) {
                        target.discard();
                    }
                }
            }
            case SPAWN -> spawn(entry, target, level);
            case HEAL -> {
                if (target.isAlive() && target.getHealth() < target.getMaxHealth()) {
                    target.heal((float) row.amount());
                }
            }
            case AURA -> aura(entry, holder, level);
            case KILL -> {
                if (target.isAlive()) {
                    target.kill();
                }
            }
        }
        // THE ROW'S PARTICLE, where it has one: for as long as what it did lasts.
        switch (row.effectClass()) {
            case APPLY_EFFECT -> {
                int lasts = row.duration() > 0 ? (int) row.duration() : -1;
                show(entry, target, level, lasts, entry.effect());
                HudIcons.given(target, entry.effect(), row.hudIcon(), lasts);
            }
            case DAMAGE_OVER_TIME, ATTRIBUTE_CHANGE -> show(entry, target, level, (int) row.duration(), null);
            case AURA -> {
            }
            default -> show(entry, target, level, 0, null);
        }
    }

    /**
     * Runs one row where something landed rather than on a creature.
     *
     * @param holder whoever fired it, or null; it is spared, as it is by its own explode
     * @return true when the class acts on a place
     */
    public static boolean applyAt(EffectEntry entry, LivingEntity holder, ServerLevel level, Vec3 at) {
        if (entry == null || level == null || at == null) {
            return false;
        }
        EffectRow row = entry.row();
        switch (row.effectClass()) {
            // On a projectile's impact the projectile is what goes, so both explode alike.
            case EXPLODE, EXPLODE_AND_DIE -> {
                double radius = row.radius() <= 0 ? 8.0 : row.radius();
                DamageSource source = source(entry, level, holder);
                level.explode(holder, source, NO_ENTITY_DAMAGE, at, (float) radius, false, Level.ExplosionInteraction.MOB);
                float damage = (float) row.damage();
                if (damage > 0.0F) {
                    for (LivingEntity inside : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                            other -> other != holder && other.distanceToSqr(at) <= radius * radius)) {
                        inside.hurt(source, damage);
                    }
                }
            }
            case SPAWN -> {
                int count = (int) row.amount();
                BlockPos origin = BlockPos.containing(at);
                for (int i = 0; i < count; i++) {
                    BlockPos pos = origin.offset(level.getRandom().nextInt(3) - 1, 0, level.getRandom().nextInt(3) - 1);
                    entry.creature().value().spawn(level, pos, MobSpawnType.TRIGGERED);
                }
            }
            case AURA -> auraAround(entry, holder, level, at);
            default -> {
                return false;
            }
        }
        var particle = particleOf(row);
        if (particle != null) {
            level.sendParticles(particle, at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0.02);
        }
        return true;
    }

    /** Keeps the runs in progress going. */
    public static void tick(LivingEntity entity, ServerLevel level) {
        List<Shown> shown = SHOWN.get(entity);
        if (shown != null && level.getGameTime() % SHOW_EVERY == 0) {
            long now = level.getGameTime();
            shown.removeIf(s -> now >= s.until() || !entity.isAlive()
                    || (s.whileHas() != null && !entity.hasEffect(s.whileHas())));
            shown.forEach(s -> burst(level, entity, s.particle()));
            if (shown.isEmpty()) {
                SHOWN.remove(entity);
            }
        }
        List<Run> runs = RUNS.get(entity);
        if (runs == null || runs.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Run> each = runs.iterator();
        while (each.hasNext()) {
            Run run = each.next();
            EffectRow row = run.entry().row();
            long elapsed = now - run.appliedAt();
            if (row.effectClass() == EffectClass.DAMAGE_OVER_TIME) {
                if (elapsed >= row.duration()) {
                    each.remove();
                    continue;
                }
                long interval = (long) row.interval();
                // The first hit lands one interval after it was applied, not on whatever multiple of the
                // interval the world clock happened to be at when it landed.
                if (interval > 0 && elapsed > 0 && elapsed % interval == 0) {
                    damageOverTime(entity, run);
                }
            } else if (row.effectClass() == EffectClass.ATTRIBUTE_CHANGE) {
                if (holdsCure(entity, run.entry())) {
                    stripAttribute(entity, run.entry());
                    each.remove();
                } else if (elapsed >= row.duration()) {
                    stripAttribute(entity, run.entry());
                    each.remove();
                }
            } else {
                // The other four leave nothing behind; anything here is a run this class should never have
                // made.
                each.remove();
            }
        }
        if (runs.isEmpty()) {
            RUNS.remove(entity);
        }
    }

    // ---------------------------------------------------------------- each class

    /** Gives the target the effect at amount's level. */
    private static MobEffectInstance instance(EffectEntry entry) {
        EffectRow row = entry.row();
        int duration = row.duration() <= 0 ? -1 : (int) row.duration();
        // The game's own swirl shows unless the row names a particle of its own.
        return new MobEffectInstance(entry.effect(), duration, (int) row.amount(), false,
                particleOf(row) == null, showsIcon(row));
    }

    /** Starts a run of damage. */
    private static void start(EffectEntry entry, LivingEntity holder, LivingEntity target, ServerLevel level) {
        List<Run> runs = RUNS.computeIfAbsent(target, k -> new ArrayList<>(1));
        runs.removeIf(run -> run.entry().row().effectClass() == EffectClass.DAMAGE_OVER_TIME);
        runs.add(new Run(entry, holder, level.getGameTime()));
    }

    /** One tick of the damage over time, clamped to one health left when it may not kill. */
    private static void damageOverTime(LivingEntity entity, Run run) {
        float damage = (float) run.entry().row().damage();
        if (!run.entry().row().canKill()) {
            damage = Math.min(damage, entity.getHealth() - 1.0F);
        }
        if (damage > 0.0F) {
            entity.hurt(source(run.entry(), entity.level(), run.holder()), damage);
        }
    }

    /**
     * The target's health moves to the holder - a flat amount, not a share of any damage, so it goes through
     * the game's damage path rather than being taken off the side: a target the game says is immune gives
     * nothing, the holder receives EXACTLY what the target lost, and the target dying here is the game's own
     * death, not a health count somebody set.
     */
    private static void lifeSteal(EffectEntry entry, LivingEntity holder, LivingEntity target, ServerLevel level) {
        float wanted = (float) entry.row().amount();
        // Draining an entity from itself is a move from a pocket to the same pocket: nothing moves, so it is
        // not an application at all.
        if (wanted <= 0.0F || holder == target) {
            return;
        }
        float before = target.getHealth();
        if (before <= 0.0F) {
            return;
        }
        // AFTER A HIT, the target is inside the game's invulnerability frames, and a drain no bigger than
        // that hit would land nowhere: On deal damage runs just after one.
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().generic(), Math.min(wanted, before));
        float lost = before - target.getHealth();
        if (lost > 0.0F) {
            holder.heal(lost);
        }
    }

    /**
     * Moves the target's attribute by a share of its base, on the game's own modifier, and leaves a run
     * behind so it can come off when the duration is up or when the cure item is held.
     */
    private static void attributeChange(EffectEntry entry, LivingEntity target, ServerLevel level) {
        AttributeInstance attribute = target.getAttribute(entry.attribute());
        if (attribute == null) {
            return;
        }
        ResourceLocation id = modifierId(entry.row().parameter());
        attribute.removeModifier(id);
        attribute.addTransientModifier(new AttributeModifier(id, entry.row().amount(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        List<Run> runs = RUNS.computeIfAbsent(target, k -> new ArrayList<>(1));
        runs.removeIf(run -> run.entry().row().effectClass() == EffectClass.ATTRIBUTE_CHANGE);
        runs.add(new Run(entry, target, level.getGameTime()));
    }

    /** The stable id a row's attribute modifier is filed under. */
    private static ResourceLocation modifierId(String attributeId) {
        return ResourceLocation.fromNamespaceAndPath("strata",
                "effect_attribute_" + attributeId.replace(':', '_').replace('/', '_'));
    }

    /** True when the target is holding the item this row says ends it. */
    private static boolean holdsCure(LivingEntity entity, EffectEntry entry) {
        return entry.cure() != null && entity.getMainHandItem().is(entry.cure());
    }

    /** Takes the modifier back off, whether the duration ran out or the cure ended it. */
    private static void stripAttribute(LivingEntity entity, EffectEntry entry) {
        AttributeInstance attribute = entity.getAttribute(entry.attribute());
        if (attribute != null) {
            attribute.removeModifier(modifierId(entry.row().parameter()));
        }
    }

    /** The boom at the target, then the row's own flat damage to everything inside the radius. */
    private static void explode(EffectEntry entry, LivingEntity target, ServerLevel level) {
        EffectRow row = entry.row();
        double radius = row.radius() <= 0 ? 8.0 : row.radius();
        Vec3 centre = target.position();
        DamageSource source = source(entry, level, target);
        level.explode(target, source, NO_ENTITY_DAMAGE, centre, (float) radius, false,
                Level.ExplosionInteraction.MOB);

        float damage = (float) row.damage();
        if (damage <= 0.0F) {
            return;
        }
        AABB box = target.getBoundingBox().inflate(radius);
        double reach = radius * radius;
        // can_kill on an explode row: the blast hurts its own source too.
        for (LivingEntity inside : level.getEntitiesOfClass(LivingEntity.class, box,
                other -> other != target || row.canKill())) {
            if (inside.distanceToSqr(centre) <= reach) {
                inside.hurt(source, damage);
            }
        }
    }

    /**
     * Amount of the named creature, on the game's own spawn path, so it goes through the same join as any
     * other spawn and stays alive.
     */
    /**
     * Gives the row's effect to the creatures within its radius of the holder, not the holder itself (the old
     * aura_players and aura_mobs, one class).
     */
    private static void aura(EffectEntry entry, LivingEntity holder, ServerLevel level) {
        auraAround(entry, holder, level, holder.position());
    }

    /** The aura around a point: the holder's place, or where its projectile landed. */
    private static void auraAround(EffectEntry entry, LivingEntity holder, ServerLevel level, Vec3 centre) {
        EffectRow row = entry.row();
        double radius = row.radius() <= 0 ? 8.0 : row.radius();
        int lasts = row.duration() > 0 ? (int) row.duration() : TriggeredEffects.interval(row) * 2;
        String who = row.entity() == null ? "" : row.entity().strip();
        net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag = who.startsWith("#")
                ? net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(who.substring(1)))
                : null;
        AABB box = new AABB(centre, centre).inflate(radius);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box, o -> o != holder && o.isAlive())) {
            boolean picked = tag != null ? other.getType().is(tag)
                    : entry.creature() != null ? other.getType() == entry.creature().value()
                    : !(other instanceof net.minecraft.world.entity.player.Player);
            if (picked && other.distanceToSqr(centre) <= radius * radius) {
                other.addEffect(new MobEffectInstance(entry.effect(), lasts, (int) row.amount(), false,
                        particleOf(row) == null, showsIcon(row)));
                show(entry, other, level, lasts, entry.effect());
                HudIcons.given(other, entry.effect(), row.hudIcon(), lasts);
            }
        }
    }

    private static void spawn(EffectEntry entry, LivingEntity target, ServerLevel level) {
        int count = (int) entry.row().amount();
        if (count <= 0) {
            return;
        }
        BlockPos origin = BlockPos.containing(target.position());
        for (int i = 0; i < count; i++) {
            // Scattered, so a count of three is not one mob standing inside another: a step either side, as
            // the game offsets a group.
            BlockPos at = origin.offset(
                    target.getRandom().nextInt(3) - 1, 0,
                    target.getRandom().nextInt(3) - 1);
            String entity = entry.row().entity();
            if (entity != null && entity.startsWith(EffectTable.VARIANT_PREFIX)) {
                spawnVariant(entry, entity.substring(EffectTable.VARIANT_PREFIX.length()), level, at);
            } else {
                entry.creature().value().spawn(level, at, MobSpawnType.TRIGGERED);
            }
        }
    }

    /** One variant, made at a spot as a natural spawn of it is made in that biome. */
    private static void spawnVariant(EffectEntry entry, String name, ServerLevel level, BlockPos at) {
        com.strata.model.Variant variant = com.strata.data.Config.tables().variants().get(name);
        if (variant == null || !(entry.creature().value().create(level) instanceof net.minecraft.world.entity.Mob mob)) {
            return;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
        String biome = level.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        MobScalingHandler.applyVariant(mob, variant, com.strata.data.Config.resolve(biome));
        level.addFreshEntityWithPassengers(mob);
    }

    // ---------------------------------------------------------------- one source of damage

    /**
     * The row's damage type as a live source, or the generic one when the row names none or names a type this
     * run has already found missing.
     */
    private static DamageSource source(EffectEntry entry, Level level, Entity attacker) {
        ResourceKey<DamageType> key = entry.damageType();
        if (key == null) {
            return level.damageSources().generic();
        }
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key).orElse(null);
        if (holder == null) {
            if (UNKNOWN_DAMAGE_TYPES.add(key)) {
                Strata.LOGGER.warn("abilities.csv: damage type {} names nothing in the game -"
                        + " this row falls back to generic damage", key.location());
            }
            return level.damageSources().generic();
        }
        return new DamageSource(holder, attacker, attacker);
    }
}
