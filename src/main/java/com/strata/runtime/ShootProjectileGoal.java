package com.strata.runtime;

import com.strata.Strata;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** ANY MOB FIRES ANY PROJECTILE. */
public class ShootProjectileGoal extends Goal {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private final Mob mob;
    private final EntityType<?> projectile;
    /** The designer's projectile it fires (Projectiles), or null for the type as it is. */
    private final String preset;
    private int interval;
    private double range;
    private final float speed;
    private final double damage;
    private int cooldown;

    public ShootProjectileGoal(Mob mob, EntityType<?> projectile, int interval, double range, double speed,
                               double damage) {
        this(mob, projectile, null, interval, range, speed, damage);
    }

    /**
     * Fires one of the designer's projectiles: the type is its base, and its name is looked up at each shot,
     * so an edit applies from the next one.
     */
    public ShootProjectileGoal(Mob mob, EntityType<?> projectile, String preset, int interval, double range,
                               double speed, double damage) {
        this.mob = mob;
        this.projectile = projectile;
        this.preset = preset;
        this.interval = Math.max(1, interval);
        this.range = range;
        this.speed = (float) speed;
        this.damage = damage;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    /** A mob's own ranged settings over the goal's (MobPropertyEffects): ticks between shots, range. */
    void useRanged(Integer interval, Double range) {
        if (interval != null && interval > 0) {
            this.interval = interval;
        }
        if (range != null && range > 0) {
            this.range = range;
        }
    }

    private LivingEntity target() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() ? t : null;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = target();
        return t != null && mob.distanceToSqr(t) <= range * range && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    public void start() {
        cooldown = interval / 2;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = target();
        if (t == null) {
            return;
        }
        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (--cooldown > 0) {
            return;
        }
        cooldown = interval;
        fire(t);
    }

    /** Fires one projectile at the target. */
    public boolean fire(LivingEntity t) {
        EntityType<?> type = preset == null ? projectile : Projectiles.typeFor(com.strata.model.ProjectilePreset.NAMESPACE + ":" + preset);
        Entity created = type == null ? null : type.create(mob.level());
        if (!(created instanceof Projectile shot)) {
            if (created != null) {
                created.discard();
            }
            String what = preset != null ? "strata:" + preset : String.valueOf(projectile);
            if (WARNED.add(what)) {
                Strata.LOGGER.warn("Shoots a projectile: {} is not a projectile, or no longer exists - nothing is fired", what);
            }
            return false;
        }
        Vec3 from = new Vec3(mob.getX(), mob.getEyeY() - 0.1, mob.getZ());
        Vec3 aim = new Vec3(t.getX() - from.x, t.getY(0.5) - from.y, t.getZ() - from.z);
        shot.setOwner(mob);
        float speed = Projectiles.speed(preset, this.speed);
        shot.moveTo(from.x, from.y, from.z, mob.getYRot(), mob.getXRot());
        if (shot instanceof AbstractHurtingProjectile pushed) {
            // A fireball's kind flies straight, pushed along its line.
            pushed.setDeltaMovement(aim.normalize().scale(speed));
            pushed.accelerationPower = 0.1;
        } else {
            // Anything else is thrown: aimed a little high for the drop over the distance.
            double arc = Math.sqrt(aim.x * aim.x + aim.z * aim.z) * 0.2;
            shot.shoot(aim.x, aim.y + arc, aim.z, speed, 1.0F);
            if (shot instanceof AbstractArrow arrow) {
                arrow.setBaseDamage(damage);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            }
        }
        // After the goal's own damage, so the projectile's own wins.
        Projectiles.prepare(shot, preset);
        Projectiles.markOwn(shot);
        return mob.level().addFreshEntity(shot);
    }
}
