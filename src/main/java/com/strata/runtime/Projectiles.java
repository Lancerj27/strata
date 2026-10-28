package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.domain.EffectOps;
import com.strata.domain.ProjectileOps;
import com.strata.domain.TriggerOps;
import com.strata.model.ProjectilePreset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** THE DESIGNER'S PROJECTILES, FIRED AND LANDED. */
public final class Projectiles {

    private Projectiles() {
    }

    /** The key a shot's saved data holds its projectile's name under. */
    public static final String TAG = "strata_projectile";

    private static volatile Map<String, ProjectilePreset> presets = Map.of();
    private static volatile Map<String, List<EffectTable.EffectEntry>> effects = Map.of();

    /** Reads projectiles.csv and projectile_effects.csv (Config#reload). */
    public static void load() {
        Map<String, ProjectilePreset> read = new HashMap<>();
        for (Map<String, String> row : ProjectileOps.rows()) {
            String problem = ProjectilePreset.problem(row);
            if (problem != null) {
                Strata.LOGGER.warn("{}: {} - row skipped ({})", Schema.Projectiles.FILE, row, problem);
                continue;
            }
            ProjectilePreset p = ProjectilePreset.of(row);
            read.put(p.name(), p);
        }
        Map<String, List<EffectTable.EffectEntry>> rows = new HashMap<>();
        int count = 0;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ProjectileEffects.FILE))) {
            String owner = row.getOrDefault(Schema.ProjectileEffects.PROJECTILE, "").strip();
            if (owner.isEmpty() || !Parse.flag(row.get(Schema.Abilities.ENABLED), true) || !EffectOps.isEffectRow(row)) {
                continue;
            }
            try {
                EffectTable.EffectEntry entry = EffectTable.entry(Schema.ProjectileEffects.FILE, owner, row);
                if (entry != null) {
                    rows.computeIfAbsent(owner, k -> new ArrayList<>()).add(entry);
                    count++;
                }
            } catch (RuntimeException e) {
                Strata.LOGGER.warn("{}: bad row {} - {}", Schema.ProjectileEffects.FILE, row, e.toString());
            }
        }
        Map<String, List<EffectTable.EffectEntry>> frozen = new HashMap<>();
        rows.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        presets = Map.copyOf(read);
        effects = Map.copyOf(frozen);
        Strata.LOGGER.info("Loaded {} projectile(s) and {} projectile abilit(ies)", read.size(), count);
    }

    /** The projectile of this name, or null. */
    public static ProjectilePreset preset(String name) {
        return name == null ? null : presets.get(name);
    }

    /** The entity type a goal's Fires id is shot as: a designer's projectile's base, or the id itself. */
    public static EntityType<?> typeFor(String id) {
        String name = ProjectilePreset.nameOf(id);
        String base = id;
        if (name != null) {
            ProjectilePreset p = presets.get(name);
            if (p == null) {
                return null;
            }
            base = p.base();
        }
        ResourceLocation rl = ResourceLocation.tryParse(base == null ? "" : base.strip());
        return rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl) ? BuiltInRegistries.ENTITY_TYPE.get(rl) : null;
    }

    /** The speed a shot leaves at: the projectile's own, else the goal's. */
    public static float speed(String name, float goalSpeed) {
        ProjectilePreset p = preset(name);
        return p != null && p.enabled() && !Double.isNaN(p.speed()) ? (float) p.speed() : goalSpeed;
    }

    /**
     * Marks a shot as one of the designer's projectiles, before it is added to the world, and sets what is
     * set at launch: an arrow's damage, no fall at gravity 0.
     */
    public static void prepare(Projectile shot, String name) {
        ProjectilePreset p = preset(name);
        if (p == null || !p.enabled()) {
            return;
        }
        shot.getPersistentData().putString(TAG, p.name());
        if (shot instanceof AbstractArrow arrow && !Double.isNaN(p.damage())) {
            arrow.setBaseDamage(p.damage());
        }
        if (p.gravity() == 0) {
            shot.setNoGravity(true);
        }
        // Looks like item: a base drawn as an item takes it, and the game sends it to players itself.
        net.minecraft.world.item.ItemStack look = lookOf(p);
        if (!look.isEmpty()) {
            if (shot instanceof net.minecraft.world.entity.projectile.ThrowableItemProjectile thrown) {
                thrown.setItem(look);
            } else if (shot instanceof net.minecraft.world.entity.projectile.Fireball fireball) {
                fireball.setItem(look);
            }
        }
    }

    private static net.minecraft.world.item.ItemStack lookOf(ProjectilePreset p) {
        ResourceLocation id = ResourceLocation.tryParse(p.look());
        if (p.look().isEmpty() || id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    /**
     * A player starts to see a shot: an arrow of the designer's with a texture of its own is told to the
     * player, whose game draws it with that texture (ProjectileLooks, client).
     */
    public static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof AbstractArrow arrow)
                || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        ProjectilePreset p = presetOf(arrow);
        if (p == null || p.texture().isEmpty()) {
            return;
        }
        var payload = new com.strata.network.StrataPayloads.ProjectileLook(arrow.getId(), p.texture());
        if (player.connection.hasChannel(payload.type())) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** The key a shot's saved data holds a mob's own projectile values under (#onJoin). */
    public static final String INLINE = "strata_projectile_values";
    /** Marks a shot this class has already looked at, so a replacement is not looked at twice. */
    private static final String SEEN = "strata_projectile_seen";

    /** A shot of Strata's own goal: it fires what its row names, so a mob's Combat values leave it be. */
    public static void markOwn(Projectile shot) {
        shot.getPersistentData().putBoolean(SEEN, true);
    }

    /**
     * What a shot is: one of the designer's projectiles, the values its mob's Combat set on it, or both - the
     * mob's values over the projectile's.
     */
    private static ProjectilePreset presetOf(Entity entity) {
        if (!(entity instanceof Projectile)) {
            return null;
        }
        var data = entity.getPersistentData();
        ProjectilePreset p = null;
        if (data.contains(TAG)) {
            p = presets.get(data.getString(TAG));
            if (p != null && !p.enabled()) {
                p = null;
            }
        }
        if (!data.contains(INLINE)) {
            return p;
        }
        var own = data.getCompound(INLINE);
        double damage = own.contains("damage") ? own.getDouble("damage") : p == null ? Double.NaN : p.damage();
        double explosion = own.contains("explosion") ? own.getDouble("explosion") : p == null ? 0 : p.explosion();
        double gravity = own.contains("gravity") ? own.getDouble("gravity") : p == null ? Double.NaN : p.gravity();
        return new ProjectilePreset(p == null ? "" : p.name(), p == null ? "" : p.base(), Double.NaN, gravity, damage,
                explosion, p == null ? 0 : p.fireSeconds(), p == null || p.keepBase(), p == null ? "" : p.look(),
                p == null ? "" : p.texture(), true);
    }

    /**
     * A MOB'S OWN PROJECTILES (its Combat values): whatever it fires - a skeleton's arrow, a
     * blaze's fireball, a mod's shot - is fired as its projectile type, one of the designer's projectiles or
     * any the game has, at its speed, with its damage, explosion and gravity.
     */
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Projectile shot)
                || !(shot.getOwner() instanceof net.minecraft.world.entity.Mob mob)) {
            return;
        }
        var data = shot.getPersistentData();
        if (data.contains(SEEN) || data.contains(TAG)) {
            return;
        }
        var p = MobProperties.forMob(mob);
        String type = p.projectileType == null ? "" : p.projectileType.strip();
        if (type.isEmpty() && p.projectileSpeed == null && p.projectileDamage == null
                && p.projectileExplosion == null && p.projectileGravity == null) {
            return;
        }
        // Marked only when changed: a shot Strata leaves alone saves as the game's.
        data.putBoolean(SEEN, true);
        Projectile target = shot;
        if (!type.isEmpty()) {
            EntityType<?> want = typeFor(type);
            if (want != null && want != shot.getType()) {
                Entity made = want.create(level);
                if (made instanceof Projectile replacement) {
                    replacement.getPersistentData().putBoolean(SEEN, true);
                    replacement.setOwner(mob);
                    replacement.moveTo(shot.getX(), shot.getY(), shot.getZ(), shot.getYRot(), shot.getXRot());
                    replacement.setDeltaMovement(shot.getDeltaMovement());
                    if (replacement instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile pushed) {
                        pushed.accelerationPower = 0.1;
                    }
                    if (replacement instanceof AbstractArrow arrow) {
                        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                    }
                    target = replacement;
                } else if (made != null) {
                    made.discard();
                }
            }
            String name = ProjectilePreset.nameOf(type);
            if (name != null) {
                prepare(target, name);
            }
        }
        if (p.projectileSpeed != null && p.projectileSpeed > 0 && target.getDeltaMovement().lengthSqr() > 0) {
            target.setDeltaMovement(target.getDeltaMovement().normalize().scale(p.projectileSpeed));
        }
        var own = new net.minecraft.nbt.CompoundTag();
        if (p.projectileDamage != null) {
            own.putDouble("damage", p.projectileDamage);
            if (target instanceof AbstractArrow arrow) {
                arrow.setBaseDamage(p.projectileDamage);
            }
        }
        if (p.projectileExplosion != null) {
            own.putDouble("explosion", p.projectileExplosion);
        }
        if (p.projectileGravity != null) {
            own.putDouble("gravity", p.projectileGravity);
            if (p.projectileGravity == 0) {
                target.setNoGravity(true);
            }
        }
        if (!own.isEmpty()) {
            target.getPersistentData().put(INLINE, own);
        }
        if (target != shot) {
            level.addFreshEntity(target);
            event.setCanceled(true);
        }
    }

    /** A gravity other than the base's: its fall scaled each tick. */
    public static void onTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || entity.isNoGravity() || entity.onGround()) {
            return;
        }
        ProjectilePreset p = presetOf(entity);
        if (p == null || Double.isNaN(p.gravity()) || p.gravity() == 1) {
            return;
        }
        double fall = entity.getGravity();
        entity.setDeltaMovement(entity.getDeltaMovement().add(0, -(p.gravity() - 1) * fall, 0));
    }

    /** Where a shot lands: its damage, fire, explosion and abilities. */
    public static void onImpact(ProjectileImpactEvent event) {
        Projectile shot = event.getProjectile();
        if (!(shot.level() instanceof ServerLevel level)) {
            return;
        }
        ProjectilePreset p = presetOf(shot);
        if (p == null) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        LivingEntity holder = shot.getOwner() instanceof LivingEntity owner ? owner : null;
        Vec3 at = hit.getLocation();
        List<EffectTable.EffectEntry> rows = effects.getOrDefault(p.name(), List.of());

        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
            if (target == holder) {
                return;
            }
            // An arrow's damage was set at launch; any other kind deals it here.
            if (!Double.isNaN(p.damage()) && p.damage() > 0 && (!(shot instanceof AbstractArrow) || !p.keepBase())) {
                target.hurt(level.damageSources().mobProjectile(shot, holder), (float) p.damage());
            }
            if (p.fireSeconds() > 0) {
                target.igniteForSeconds((float) p.fireSeconds());
            }
            TriggeredEffects.fire(rows, TriggerOps.IMPACT, target, holder == null ? target : holder, target);
            // The base's own hit (an arrow's) comes after this event: the frames this one's damage opened
            // must not swallow it.
            if (p.keepBase()) {
                target.invulnerableTime = 0;
            }
        } else {
            for (EffectTable.EffectEntry entry : rows) {
                if (TriggerOps.IMPACT.equals(entry.row().trigger())
                        && TriggeredEffects.rollChance(holder == null ? null : holder, entry.row().chance(), level)) {
                    EffectRuntime.applyAt(entry, holder, level, at);
                }
            }
        }
        if (p.explosion() > 0) {
            level.explode(shot, at.x, at.y, at.z, (float) p.explosion(), false, Level.ExplosionInteraction.MOB);
        }
        if (!p.keepBase()) {
            event.setCanceled(true);
            shot.discard();
        }
    }
}
