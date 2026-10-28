package com.strata.runtime;

import com.strata.Strata;
import com.strata.model.VariantProperties;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** WHAT A MOB'S PROPERTY VALUES DO IN THE GAME. */
public final class MobPropertyEffects {

    private MobPropertyEffects() {
    }

    /** A mob joins a world: what is set once on it. */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) {
            apply(mob);
            VariantProperties.Resolved p = MobProperties.forMob(mob);
            if (Boolean.TRUE.equals(p.spawnEnabled) && !event.loadedFromDisk() && event.getLevel() instanceof ServerLevel level) {
                burst(mob, p, level);
            }
        }
    }

    /** Sets what its properties set on the mob itself. */
    public static void apply(Mob mob) {
        VariantProperties.Resolved p = MobProperties.forMob(mob);
        if (p.deathLootTable != null && !p.deathLootTable.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(p.deathLootTable.strip());
            if (id != null) {
                setLootTable(mob, ResourceKey.create(Registries.LOOT_TABLE, id));
            }
        }
        // The game's own switches on the mob: silent, the outline glow, no gravity.
        if (p.silent != null) {
            mob.setSilent(p.silent);
        }
        if (p.glowing != null) {
            mob.setGlowingTag(p.glowing);
        }
        if (p.noGravity != null) {
            mob.setNoGravity(p.noGravity);
        }
        applyRanged(mob, p);
        applyMovement(mob, p);
        // A reload puts the boss bar up, changes it, or takes it down.
        if (p.boss != null || BOSSES.containsKey(mob)) {
            updateBoss(mob, p);
        }
        if (p.canPickUpItems != null) {
            mob.setCanPickUpLoot(p.canPickUpItems);
        }
        if (Boolean.TRUE.equals(p.persistent)) {
            mob.setPersistenceRequired();
        }
        applyDropChances(mob, p);
        applyPathfinding(mob, p);
        applyTags(mob, p);
    }

    // ---------------------------------------------------------------- drop chances, paths, tags

    /** Each mob's own drop chance per slot, kept the first time its properties change them. */
    private static final Map<Mob, float[]> OWN_DROPS = Collections.synchronizedMap(new WeakHashMap<>());
    /** Each mob's own terrain cost per PathType, kept the first time its properties change one. */
    private static final Map<Mob, Map<net.minecraft.world.level.pathfinder.PathType, Float>> OWN_MALUS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** The drop chance a slot's own columns set: the per-slot value, or null. */
    private static Float slotDropChance(VariantProperties.Resolved p, EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> p.dropChanceMainhand;
            case OFFHAND -> p.dropChanceOffhand;
            case HEAD -> p.dropChanceHead;
            case CHEST -> p.dropChanceChest;
            case LEGS -> p.dropChanceLegs;
            case FEET -> p.dropChanceFeet;
            case BODY -> p.dropChanceBody;
        };
    }

    /** The mob's drop chance for a slot, read off the arrays Mob keeps (Mob#getEquipmentDropChance is protected). */
    private static float dropChanceOf(Mob mob, EquipmentSlot slot) {
        try {
            return switch (slot.getType()) {
                case HAND -> ((float[]) field(Mob.class, "handDropChances").get(mob))[slot.getIndex()];
                case HUMANOID_ARMOR -> ((float[]) field(Mob.class, "armorDropChances").get(mob))[slot.getIndex()];
                case ANIMAL_ARMOR -> field(Mob.class, "bodyArmorDropChance").getFloat(mob);
            };
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.085F;
        }
    }

    /** DROP CHANCE PER SLOT: Mob#setDropChance for each slot with a value. */
    static void applyDropChances(Mob mob, VariantProperties.Resolved p) {
        boolean always = Boolean.TRUE.equals(p.alwaysDropEquipment);
        EquipmentSlot[] slots = EquipmentSlot.values();
        float[] own = OWN_DROPS.get(mob);
        boolean any = always;
        for (EquipmentSlot slot : slots) {
            any |= slotDropChance(p, slot) != null;
        }
        if (!any) {
            if (own != null) {
                for (EquipmentSlot slot : slots) {
                    mob.setDropChance(slot, own[slot.ordinal()]);
                }
                OWN_DROPS.remove(mob);
            }
            return;
        }
        if (own == null) {
            own = new float[slots.length];
            for (EquipmentSlot slot : slots) {
                own[slot.ordinal()] = dropChanceOf(mob, slot);
            }
            OWN_DROPS.put(mob, own);
        }
        for (EquipmentSlot slot : slots) {
            Float set = slotDropChance(p, slot);
            mob.setDropChance(slot, set != null ? set : always ? 2.0F : own[slot.ordinal()]);
        }
    }

    /** Puts the Loot &amp; XP drop chances on again, over equipment that has just been armed. */
    public static void reapplyDropChances(Mob mob) {
        OWN_DROPS.remove(mob);
        applyDropChances(mob, MobProperties.forMob(mob));
    }

    /**
     * PATHFINDING COST: Mob#setPathfindingMalus for each PathType with a value (-1 = never walks there, 0 =
     * free, higher = avoids).
     */
    static void applyPathfinding(Mob mob, VariantProperties.Resolved p) {
        Map<net.minecraft.world.level.pathfinder.PathType, Float> own = OWN_MALUS.get(mob);
        if (p.pathfinding == null && own == null) {
            return;
        }
        for (net.minecraft.world.level.pathfinder.PathType type : net.minecraft.world.level.pathfinder.PathType.values()) {
            Float want = p.pathfinding == null ? null
                    : p.pathfinding.get(type.name().toLowerCase(java.util.Locale.ROOT));
            if (want != null) {
                if (own == null) {
                    own = new java.util.EnumMap<>(net.minecraft.world.level.pathfinder.PathType.class);
                    OWN_MALUS.put(mob, own);
                }
                own.putIfAbsent(type, mob.getPathfindingMalus(type));
                mob.setPathfindingMalus(type, want);
            } else if (own != null && own.containsKey(type)) {
                mob.setPathfindingMalus(type, own.remove(type));
            }
        }
        if (own != null && own.isEmpty()) {
            OWN_MALUS.remove(mob);
        }
    }

    /** SCOREBOARD TAGS: the row's comma list is added to the entity (Entity#addTag). */
    static void applyTags(Mob mob, VariantProperties.Resolved p) {
        java.util.Set<String> want = tagList(p.scoreboardTags);
        String saved = mob.getExistingData(MobLevel.STRATA_TAGS).orElse("");
        if (want.isEmpty() && saved.isEmpty()) {
            return;
        }
        java.util.Set<String> added = tagList(saved);
        java.util.Set<String> kept = new java.util.LinkedHashSet<>();
        for (String tag : added) {
            if (want.contains(tag)) {
                kept.add(tag);
            } else {
                mob.removeTag(tag);
            }
        }
        for (String tag : want) {
            if (mob.addTag(tag)) {
                kept.add(tag);
            }
        }
        String now = String.join(",", kept);
        if (!now.equals(saved)) {
            mob.setData(MobLevel.STRATA_TAGS, now);
        }
    }

    /** A comma list as its distinct, non-blank members in order. */
    static java.util.Set<String> tagList(String list) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (list != null) {
            for (String raw : list.split(",")) {
                if (!raw.isBlank()) {
                    out.add(raw.strip());
                }
            }
        }
        return out;
    }

    /** The experience it drops: a whole number from its range, when it has one. */
    public static void onExperience(LivingExperienceDropEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide()) {
            return;
        }
        VariantProperties.Resolved p = MobProperties.forMob(mob);
        if (p.xpMin == null && p.xpMax == null) {
            return;
        }
        int min = Math.max(0, p.xpMin != null ? p.xpMin : event.getDroppedExperience());
        int max = Math.max(min, p.xpMax != null ? p.xpMax : min);
        event.setDroppedExperience(min + mob.getRandom().nextInt(max - min + 1));
    }

    private static Field lootTable;

    /** Mob#lootTable: the game reads it before the type's own table, and saves it with the mob. */
    private static void setLootTable(Mob mob, ResourceKey<?> key) {
        try {
            if (lootTable == null) {
                lootTable = Mob.class.getDeclaredField("lootTable");
                lootTable.setAccessible(true);
            }
            lootTable.set(mob, key);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Could not set {}'s loot table: {}", mob.getType(), e.toString());
        }
    }

    // ---------------------------------------------------------------- combat

    /** Each goal's own values, kept the first time a mob's properties change them. */
    private static final Map<Goal, Object[]> ORIGINAL = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Its ranged goals' interval and range: the game's bow, crossbow and ranged goals hold both on the goal
     * itself, as does Strata's own Shoots a projectile.
     */
    static void applyRanged(Mob mob, VariantProperties.Resolved p) {
        Integer interval = p.rangedAttackInterval;
        Double range = p.rangedAttackRange == null ? null : p.rangedAttackRange.doubleValue();
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            Goal goal = unwrap(wrapped.getGoal());
            if (goal instanceof ShootProjectileGoal shoot) {
                shoot.useRanged(interval, range);
                continue;
            }
            String[] fields = goal instanceof RangedBowAttackGoal<?> ? new String[]{"attackIntervalMin", null, null, "attackRadiusSqr"}
                    : goal instanceof RangedAttackGoal ? new String[]{"attackIntervalMin", "attackIntervalMax", "attackRadius", "attackRadiusSqr"}
                    : goal instanceof RangedCrossbowAttackGoal<?> ? new String[]{null, null, null, "attackRadiusSqr"}
                    : null;
            if (fields == null) {
                continue;
            }
            Object[] own = ORIGINAL.computeIfAbsent(goal, g -> read(g, fields));
            Object[] want = {interval != null ? interval : own[0], interval != null ? interval : own[1],
                    range != null ? (Object) range.floatValue() : own[2],
                    range != null ? (Object) (float) (range * range) : own[3]};
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] != null && want[i] != null) {
                    set(goal, fields[i], want[i]);
                }
            }
        }
    }

    /** A skeleton picks its bow goal's interval again when its gear changes: put the mob's back. */
    public static void onEquipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level
                && MobProperties.forMob(mob).rangedAttackInterval != null) {
            level.getServer().execute(() -> applyRanged(mob, MobProperties.forMob(mob)));
        }
    }

    private static Goal unwrap(Goal goal) {
        for (int depth = 0; depth < 4; depth++) {
            if (goal instanceof ConditionalGoal c) {
                goal = c.inner();
            } else if (goal instanceof SwitchedOffGoal s) {
                goal = s.inner();
            } else {
                break;
            }
        }
        return goal;
    }

    private static Object[] read(Goal goal, String[] fields) {
        Object[] out = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] != null) {
                Field f = field(goal.getClass(), fields[i]);
                try {
                    out[i] = f == null ? null : f.get(goal);
                } catch (ReflectiveOperationException e) {
                    out[i] = null;
                }
            }
        }
        return out;
    }

    private static void set(Goal goal, String name, Object value) {
        Field f = field(goal.getClass(), name);
        try {
            if (f != null) {
                f.set(goal, value);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.debug("Could not set {}.{}: {}", goal.getClass().getSimpleName(), name, e.toString());
        }
    }

    private static final Map<String, Field> FIELDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Field field(Class<?> type, String name) {
        return FIELDS.computeIfAbsent(type.getName() + "#" + name, k -> {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    // up the chain
                }
            }
            return null;
        });
    }

    /** Set while an area hit is dealt, so the hits it deals do not deal area hits of their own. */
    private static final ThreadLocal<Boolean> SPREADING = ThreadLocal.withInitial(() -> false);

    /**
     * A melee hit landed: the wait before the next one (the melee goal's own counter) and the same hit on the
     * creatures around the target, when the mob has either.
     */
    public static void onHit(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Mob mob) || source.getDirectEntity() != mob
                || !(mob.level() instanceof ServerLevel level) || SPREADING.get()) {
            return;
        }
        VariantProperties.Resolved p = MobProperties.forMob(mob);
        if (p.attackCooldown != null && p.attackCooldown >= 0) {
            for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
                if (unwrap(wrapped.getGoal()) instanceof MeleeAttackGoal melee) {
                    set(melee, "ticksUntilNextAttack", (int) Math.round(p.attackCooldown));
                }
            }
        }
        if (p.aoeRadius != null && p.aoeRadius > 0 && event.getNewDamage() > 0) {
            LivingEntity target = event.getEntity();
            double r = p.aoeRadius;
            SPREADING.set(true);
            try {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r),
                        o -> o != target && o != mob && o.isAlive() && !o.isAlliedTo(mob) && o.distanceToSqr(target) <= r * r)) {
                    other.hurt(level.damageSources().mobAttack(mob), event.getNewDamage());
                }
            } finally {
                SPREADING.set(false);
            }
        }
    }

    /** Damage of a type, or a #tag of types, it is immune to is not taken at all. */
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity mob = event.getEntity();
        // Never the void or /kill: a mob immune to them falls forever and cannot be removed.
        if (mob.level().isClientSide() || !(mob instanceof Mob)
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        VariantProperties.Resolved props = MobProperties.forMob(mob);
        if (immuneByFlag(mob, props, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        String list = props.damageImmunities;
        if (list == null || list.isBlank()) {
            return;
        }
        var type = event.getSource().typeHolder();
        for (String raw : list.split(",")) {
            String id = raw.strip();
            if (id.isEmpty()) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(namespaced(id.startsWith("#") ? id.substring(1) : id));
            if (rl == null) {
                continue;
            }
            boolean hit = id.startsWith("#") ? type.is(TagKey.create(Registries.DAMAGE_TYPE, rl)) : type.is(rl);
            if (hit) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** "fire" reads as minecraft:fire, as the guide's examples are written. */
    private static String namespaced(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    // ---------------------------------------------------------------- flags

    /**
     * The damage its immunity flags cancel, by the game's damage types: fire and lava, a fall, suffocating in
     * a block, drowning, and the water a water-sensitive mob takes damage from.
     */
    static boolean immuneByFlag(LivingEntity mob, VariantProperties.Resolved p, DamageSource source) {
        var type = source.typeHolder();
        if (Boolean.TRUE.equals(p.fireImmune) && type.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return true;
        }
        if (Boolean.TRUE.equals(p.lavaImmune) && type.is(net.minecraft.world.damagesource.DamageTypes.LAVA)) {
            return true;
        }
        if (Boolean.TRUE.equals(p.fallDamageImmune) && type.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return true;
        }
        if (Boolean.TRUE.equals(p.suffocationImmune) && type.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            return true;
        }
        if (type.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) {
            return mob.isSensitiveToWater() ? Boolean.TRUE.equals(p.waterImmune) : Boolean.TRUE.equals(p.drowningImmune);
        }
        return false;
    }

    /**
     * Every tick, what a flag keeps true: fire put out on a fire-immune mob, air kept for one that cannot
     * drown, and daylight - a mob that burns in it is lit, an undead one that does not is put out (as the Sun
     * immune ability does).
     */
    public static void onTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
            return;
        }
        VariantProperties.Resolved p = MobProperties.forMob(mob);
        if (p.boss != null || BOSSES.containsKey(mob)) {
            updateBoss(mob, p);
        }
        if (p.isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(p.fireImmune) && mob.isOnFire()) {
            mob.clearFire();
        }
        ambientParticles(mob, p, level);
        if (Boolean.TRUE.equals(p.drowningImmune) && mob.getAirSupply() < mob.getMaxAirSupply()) {
            mob.setAirSupply(mob.getMaxAirSupply());
        }
        // Burns: the sun lights it (SunBurn).
        if (Boolean.TRUE.equals(p.burnInDaylight)) {
            SunBurn.tick(mob, level);
        }
    }

    /** Never despawns: the game's despawn check is told no. */
    public static void onDespawn(net.neoforged.neoforge.event.entity.living.MobDespawnEvent event) {
        var p = MobProperties.forMob(event.getEntity());
        // NEVER DESPAWN: the flag, or a boss's own switch.
        if (Boolean.TRUE.equals(p.noDespawn) || Boolean.TRUE.equals(p.neverDespawn)) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobDespawnEvent.Result.DENY);
        }
    }

    // ---------------------------------------------------------------- boss bar

    /** Each boss's one health bar, kept while it is a boss. */
    private static final Map<Mob, net.minecraft.server.level.ServerBossEvent> BOSSES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** A boss's health bar, or null when the mob has none. */
    public static net.minecraft.server.level.ServerBossEvent bossEventOf(Mob mob) {
        return BOSSES.get(mob);
    }

    /**
     * BOSS: a mob whose Boss flag is on gets the game's own boss bar (a ServerBossEvent, as the Wither's).
     */
    static void updateBoss(Mob mob, VariantProperties.Resolved p) {
        var bar = BOSSES.get(mob);
        if (!Boolean.TRUE.equals(p.boss) || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
            removeBoss(mob);
            return;
        }
        if (bar == null) {
            bar = new net.minecraft.server.level.ServerBossEvent(mob.getDisplayName(),
                    net.minecraft.world.BossEvent.BossBarColor.PURPLE,
                    net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            BOSSES.put(mob, bar);
        }
        net.minecraft.network.chat.Component name = p.barName != null && !p.barName.isBlank()
                ? net.minecraft.network.chat.Component.literal(p.barName.strip()) : mob.getDisplayName();
        if (!name.equals(bar.getName())) {
            bar.setName(name);
        }
        var color = net.minecraft.world.BossEvent.BossBarColor.byName(p.barColor == null
                ? com.strata.data.Schema.BOSS_DEFAULT_COLOR : p.barColor);
        if (color != null && color != bar.getColor()) {
            bar.setColor(color);
        }
        var style = net.minecraft.world.BossEvent.BossBarOverlay.byName(p.barStyle == null
                ? com.strata.data.Schema.BOSS_DEFAULT_STYLE : p.barStyle);
        if (style != null && style != bar.getOverlay()) {
            bar.setOverlay(style);
        }
        if (bar.shouldDarkenScreen() != Boolean.TRUE.equals(p.darkenSky)) {
            bar.setDarkenScreen(Boolean.TRUE.equals(p.darkenSky));
        }
        if (bar.shouldCreateWorldFog() != Boolean.TRUE.equals(p.fog)) {
            bar.setCreateWorldFog(Boolean.TRUE.equals(p.fog));
        }
        if (bar.shouldPlayBossMusic() != Boolean.TRUE.equals(p.bossMusic)) {
            bar.setPlayBossMusic(Boolean.TRUE.equals(p.bossMusic));
        }
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, mob.getHealth() / mob.getMaxHealth())));
        // THE PLAYER LIST: a new bar fills at once, then every 10 ticks.
        if (bar.getPlayers().isEmpty() || mob.tickCount % 10 == 0) {
            double range = p.barRange != null && p.barRange > 0 ? p.barRange : com.strata.data.Schema.BOSS_DEFAULT_RANGE;
            double sq = range * range;
            for (var player : level.players()) {
                boolean near = player.isAlive() && player.distanceToSqr(mob) <= sq;
                boolean on = bar.getPlayers().contains(player);
                if (near && !on) {
                    bar.addPlayer(player);
                } else if (!near && on) {
                    bar.removePlayer(player);
                }
            }
            for (var player : java.util.List.copyOf(bar.getPlayers())) {
                // Gone to another world, or logged out (it stays in the level object until then).
                if (player.level() != level || player.hasDisconnected()) {
                    bar.removePlayer(player);
                }
            }
        }
    }

    /** Takes a mob's boss bar down: every player off it, and the bar dropped. */
    static void removeBoss(Mob mob) {
        var bar = BOSSES.remove(mob);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** A boss that leaves the world (removed, unloaded) takes its bar with it. */
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !event.getLevel().isClientSide()) {
            removeBoss(mob);
        }
    }

    // ---------------------------------------------------------------- particles

    /**
     * A burst of the mob's own particle (mob_particles.csv): Count of them (1 when blank), at its
     * centre moved by its Offset, a little spread over its body, each flying off at its Velocity when it has
     * one.
     */
    static void burst(Mob mob, VariantProperties.Resolved p, ServerLevel level) {
        var particle = MobModels.particleOf(p.particleType);
        if (particle == null) {
            return;
        }
        int count = p.count != null && p.count > 0 ? Math.min(p.count, 200) : 1;
        double x = mob.getX() + (p.offsetX == null ? 0 : p.offsetX);
        double y = mob.getY() + mob.getBbHeight() * 0.5 + (p.offsetY == null ? 0 : p.offsetY);
        double z = mob.getZ() + (p.offsetZ == null ? 0 : p.offsetZ);
        double spread = mob.getBbWidth() * 0.3;
        boolean moving = p.velocityX != null || p.velocityY != null || p.velocityZ != null;
        if (!moving) {
            level.sendParticles(particle, x, y, z, count, spread, mob.getBbHeight() * 0.3, spread, 0.0);
            return;
        }
        double vx = p.velocityX == null ? 0 : p.velocityX;
        double vy = p.velocityY == null ? 0 : p.velocityY;
        double vz = p.velocityZ == null ? 0 : p.velocityZ;
        for (int i = 0; i < count; i++) {
            // A count of 0 is the game's "one particle, this velocity".
            level.sendParticles(particle, x + (mob.getRandom().nextDouble() - 0.5) * 2 * spread,
                    y + (mob.getRandom().nextDouble() - 0.5) * mob.getBbHeight() * 0.6,
                    z + (mob.getRandom().nextDouble() - 0.5) * 2 * spread, 0, vx, vy, vz, 1.0);
        }
    }

    /** Ambient particles: a burst every Frequency ticks, unless Ambient is off. */
    static void ambientParticles(Mob mob, VariantProperties.Resolved p, ServerLevel level) {
        if (p.frequencyTicks != null && p.frequencyTicks > 0 && !Boolean.FALSE.equals(p.ambientEnabled)
                && mob.tickCount % p.frequencyTicks == 0) {
            burst(mob, p, level);
        }
    }

    /** Hurt particles, and attack particles on whoever dealt the hit. */
    public static void onHurtParticles(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Mob hurt && hurt.level() instanceof ServerLevel level) {
            VariantProperties.Resolved p = MobProperties.forMob(hurt);
            if (Boolean.TRUE.equals(p.hurtEnabled)) {
                burst(hurt, p, level);
            }
        }
        if (event.getSource().getEntity() instanceof Mob attacker && attacker.level() instanceof ServerLevel level) {
            VariantProperties.Resolved p = MobProperties.forMob(attacker);
            if (Boolean.TRUE.equals(p.attackEnabled)) {
                burst(attacker, p, level);
            }
        }
    }

    /** Death particles. */
    public static void onDeathParticles(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob dead && !dead.level().isClientSide()) {
            removeBoss(dead);
        }
        if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level) {
            VariantProperties.Resolved p = MobProperties.forMob(mob);
            if (Boolean.TRUE.equals(p.deathEnabled)) {
                burst(mob, p, level);
            }
        }
    }

    // ---------------------------------------------------------------- movement

    /** Each mob's own move control and navigation, kept the first time Flying or Swimming swaps them. */
    private static final Map<Mob, Object[]> OWN_MOVEMENT = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Flying and Swimming (mob_flags.csv): how it moves is two parts the game keeps on the mob,
     * its move control and its path finder.
     */
    static void applyMovement(Mob mob, VariantProperties.Resolved p) {
        boolean fly = Boolean.TRUE.equals(p.flying)
                && mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED) != null;
        boolean swim = !fly && Boolean.TRUE.equals(p.swimming);
        Object[] own = OWN_MOVEMENT.get(mob);
        if (!fly && !swim) {
            if (own != null) {
                setMobField(mob, "moveControl", own[0]);
                setMobField(mob, "navigation", own[1]);
                mob.setNoGravity((Boolean) own[2]);
                OWN_MOVEMENT.remove(mob);
            }
            return;
        }
        if (own == null) {
            OWN_MOVEMENT.put(mob, new Object[]{mob.getMoveControl(), mob.getNavigation(), mob.isNoGravity()});
        }
        if (fly) {
            if (!(mob.getMoveControl() instanceof net.minecraft.world.entity.ai.control.FlyingMoveControl)) {
                setMobField(mob, "moveControl", new net.minecraft.world.entity.ai.control.FlyingMoveControl(mob, 20, true));
                var nav = new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(mob, mob.level());
                nav.setCanOpenDoors(false);
                nav.setCanFloat(true);
                setMobField(mob, "navigation", nav);
            }
            mob.setNoGravity(true);
        } else if (!(mob.getMoveControl() instanceof net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl)) {
            setMobField(mob, "moveControl",
                    new net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl(mob, 85, 10, 0.1F, 0.5F, true));
            setMobField(mob, "navigation", new net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation(mob, mob.level()));
            mob.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.0F);
        }
    }

    private static void setMobField(Mob mob, String name, Object value) {
        Field f = field(Mob.class, name);
        try {
            if (f != null) {
                f.set(mob, value);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Could not set {}'s {}: {}", mob.getType(), name, e.toString());
        }
    }
}
