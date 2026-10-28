package com.strata.model;

/** Optional per-variant properties loaded from the new data tables. */
public final class VariantProperties {

    private VariantProperties() {}

    /** A single property value with its scope. */
    public record ScopedValue(String scope, String scopeId, String value) {}

    /** All properties for one variant, layered by scope. */
    public static final class Resolved {
        // Sounds (client+server)
        public final String ambientSound;
        public final String hurtSound;
        public final String deathSound;
        public final String stepSound;
        public final String attackSound;
        public final String swimSound;
        public final String fallSound;
        public final Float ambientVolume;
        public final Float ambientPitch;
        public final Float hurtVolume;
        public final Float hurtPitch;
        public final Float deathVolume;
        public final Float deathPitch;
        public final Float stepVolume;
        public final Float stepPitch;

        // Visuals (client)
        public final String texture;
        public final String model;
        public final String glowTexture;
        public final Boolean emissive;
        public final Integer tint;
        public final Float shadowRadius;
        public final Float scaleVisual;
        public final Boolean silent;
        public final Boolean glowing;
        /** Whether a swapped model drops the base mob's own particles. */
        public final Boolean suppressBaseParticles;

        /** Whether a swapped model drops the base mob's own particles. */
        public boolean dropsBaseParticles() {
            return suppressBaseParticles == null || suppressBaseParticles;
        }

        // Physics (server)
        public final Float width;
        public final Float height;
        public final Float eyeHeight;
        public final Boolean noGravity;

        // Flags (server)
        public final Boolean burnInDaylight;
        public final Boolean fireImmune;
        public final Boolean lavaImmune;
        public final Boolean waterImmune;
        public final Boolean drowningImmune;
        public final Boolean fallDamageImmune;
        public final Boolean suffocationImmune;
        public final Boolean canPickUpItems;
        public final Boolean persistent;
        public final Boolean noDespawn;
        public final Boolean canClimb;
        public final Boolean canWalkOnWater;
        public final Boolean flying;
        public final Boolean swimming;
        public final Boolean boss;

        // Boss bar (server)
        public final String barColor;
        public final String barStyle;
        public final String barName;
        public final Double barRange;
        public final Boolean darkenSky;
        public final Boolean fog;
        public final Boolean bossMusic;
        public final Boolean neverDespawn;

        // Combat (server)
        public final Double attackCooldown;
        public final Double reach;
        public final Double aoeRadius;
        public final String projectileType;
        public final Double projectileSpeed;
        public final Double projectileDamage;
        public final Double projectileExplosion;
        public final Double projectileGravity;
        public final Integer rangedAttackInterval;
        public final Integer rangedAttackRange;
        public final String damageImmunities;

        // Particles (client)
        public final String particleType;
        public final Boolean particleEnabled;
        public final Boolean ambientEnabled;
        public final Boolean hurtEnabled;
        public final Boolean deathEnabled;
        public final Boolean attackEnabled;
        public final Boolean spawnEnabled;
        public final Integer count;
        public final Integer frequencyTicks;
        public final Double offsetX;
        public final Double offsetY;
        public final Double offsetZ;
        public final Double velocityX;
        public final Double velocityY;
        public final Double velocityZ;

        // Loot/XP (server)
        public final String deathLootTable;
        public final Integer xpMin;
        public final Integer xpMax;
        public final Boolean alwaysDropEquipment;
        /** Per-slot drop chance, 0 to 1; above 1 = always. */
        public final Float dropChanceMainhand, dropChanceOffhand, dropChanceHead, dropChanceChest, dropChanceLegs, dropChanceFeet, dropChanceBody;

        // Pathfinding and tags
        /** PathType name (lowercase) to cost; null when none is set. */
        public final java.util.Map<String, Float> pathfinding;
        public final String scoreboardTags;

        private Resolved(Builder b) {
            this.ambientSound = b.ambientSound;
            this.hurtSound = b.hurtSound;
            this.deathSound = b.deathSound;
            this.stepSound = b.stepSound;
            this.attackSound = b.attackSound;
            this.swimSound = b.swimSound;
            this.fallSound = b.fallSound;
            this.ambientVolume = b.ambientVolume;
            this.ambientPitch = b.ambientPitch;
            this.hurtVolume = b.hurtVolume;
            this.hurtPitch = b.hurtPitch;
            this.deathVolume = b.deathVolume;
            this.deathPitch = b.deathPitch;
            this.stepVolume = b.stepVolume;
            this.stepPitch = b.stepPitch;

            this.texture = b.texture;
            this.model = b.model;
            this.glowTexture = b.glowTexture;
            this.emissive = b.emissive;
            this.tint = b.tint;
            this.shadowRadius = b.shadowRadius;
            this.scaleVisual = b.scaleVisual;
            this.silent = b.silent;
            this.glowing = b.glowing;
            this.suppressBaseParticles = b.suppressBaseParticles;

            this.width = b.width;
            this.height = b.height;
            this.eyeHeight = b.eyeHeight;
            this.noGravity = b.noGravity;

            this.burnInDaylight = b.burnInDaylight;
            this.fireImmune = b.fireImmune;
            this.lavaImmune = b.lavaImmune;
            this.waterImmune = b.waterImmune;
            this.drowningImmune = b.drowningImmune;
            this.fallDamageImmune = b.fallDamageImmune;
            this.suffocationImmune = b.suffocationImmune;
            this.canPickUpItems = b.canPickUpItems;
            this.persistent = b.persistent;
            this.noDespawn = b.noDespawn;
            this.canClimb = b.canClimb;
            this.canWalkOnWater = b.canWalkOnWater;
            this.flying = b.flying;
            this.swimming = b.swimming;
            this.boss = b.boss;

            this.barColor = b.barColor;
            this.barStyle = b.barStyle;
            this.barName = b.barName;
            this.barRange = b.barRange;
            this.darkenSky = b.darkenSky;
            this.fog = b.fog;
            this.bossMusic = b.bossMusic;
            this.neverDespawn = b.neverDespawn;


            this.attackCooldown = b.attackCooldown;
            this.reach = b.reach;
            this.aoeRadius = b.aoeRadius;
            this.projectileType = b.projectileType;
            this.projectileSpeed = b.projectileSpeed;
            this.projectileDamage = b.projectileDamage;
            this.projectileExplosion = b.projectileExplosion;
            this.projectileGravity = b.projectileGravity;
            this.rangedAttackInterval = b.rangedAttackInterval;
            this.rangedAttackRange = b.rangedAttackRange;
            this.damageImmunities = b.damageImmunities;

            this.particleType = b.particleType;
            this.particleEnabled = b.particleEnabled;
            this.ambientEnabled = b.ambientEnabled;
            this.hurtEnabled = b.hurtEnabled;
            this.deathEnabled = b.deathEnabled;
            this.attackEnabled = b.attackEnabled;
            this.spawnEnabled = b.spawnEnabled;
            this.count = b.count;
            this.frequencyTicks = b.frequencyTicks;
            this.offsetX = b.offsetX;
            this.offsetY = b.offsetY;
            this.offsetZ = b.offsetZ;
            this.velocityX = b.velocityX;
            this.velocityY = b.velocityY;
            this.velocityZ = b.velocityZ;

            this.deathLootTable = b.deathLootTable;
            this.xpMin = b.xpMin;
            this.xpMax = b.xpMax;
            this.alwaysDropEquipment = b.alwaysDropEquipment;
            this.dropChanceMainhand = b.dropChanceMainhand;
            this.dropChanceOffhand = b.dropChanceOffhand;
            this.dropChanceHead = b.dropChanceHead;
            this.dropChanceChest = b.dropChanceChest;
            this.dropChanceLegs = b.dropChanceLegs;
            this.dropChanceFeet = b.dropChanceFeet;
            this.dropChanceBody = b.dropChanceBody;
            this.pathfinding = b.pathfinding;
            this.scoreboardTags = b.scoreboardTags;
        }

        /** Empty = no overrides at all. */
        public static final Resolved EMPTY = new Builder().build();

        /** True when no property is set. */
        public boolean isEmpty() {
            try {
                for (java.lang.reflect.Field f : FIELDS) {
                    if (f.get(this) != null) {
                        return false;
                    }
                }
                return true;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private static final java.lang.reflect.Field[] FIELDS = java.util.Arrays
                .stream(Resolved.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .toArray(java.lang.reflect.Field[]::new);
    }

    /** Builder for Resolved - collects layered values (global -> tier -> biome). */
    public static final class Builder {
        // Sounds
        String ambientSound, hurtSound, deathSound, stepSound, attackSound, swimSound, fallSound;
        Float ambientVolume, ambientPitch, hurtVolume, hurtPitch, deathVolume, deathPitch, stepVolume, stepPitch;

        // Visuals
        String texture, model, glowTexture;
        Boolean emissive;
        Integer tint;
        Float shadowRadius, scaleVisual;
        Boolean silent, glowing, suppressBaseParticles;

        // Physics
        Float width, height, eyeHeight;
        Boolean noGravity;

        // Flags
        Boolean burnInDaylight, fireImmune, lavaImmune, waterImmune, drowningImmune;
        Boolean fallDamageImmune, suffocationImmune, canPickUpItems, persistent, noDespawn;
        Boolean canClimb, canWalkOnWater, flying, swimming;
        Boolean boss, darkenSky, fog, bossMusic, neverDespawn;
        String barColor, barStyle, barName;
        Double barRange;

        // Combat
        Double attackCooldown, reach, aoeRadius;
        String projectileType;
        Double projectileSpeed, projectileDamage, projectileExplosion, projectileGravity;
        Integer rangedAttackInterval, rangedAttackRange;
        String damageImmunities;

        // Particles
        String particleType;
        Boolean particleEnabled, ambientEnabled, hurtEnabled, deathEnabled, attackEnabled, spawnEnabled;
        Integer count, frequencyTicks;
        Double offsetX, offsetY, offsetZ, velocityX, velocityY, velocityZ;

        // Loot/XP
        String deathLootTable;
        Integer xpMin, xpMax;
        Boolean alwaysDropEquipment;
        Float dropChanceMainhand, dropChanceOffhand, dropChanceHead, dropChanceChest, dropChanceLegs, dropChanceFeet, dropChanceBody;
        java.util.Map<String, Float> pathfinding;
        String scoreboardTags;

        // Setters for method reference compatibility
        public void ambientSound(String v) { this.ambientSound = v; }
        public void hurtSound(String v) { this.hurtSound = v; }
        public void deathSound(String v) { this.deathSound = v; }
        public void stepSound(String v) { this.stepSound = v; }
        public void attackSound(String v) { this.attackSound = v; }
        public void swimSound(String v) { this.swimSound = v; }
        public void fallSound(String v) { this.fallSound = v; }
        public void ambientVolume(Float v) { this.ambientVolume = v; }
        public void ambientPitch(Float v) { this.ambientPitch = v; }
        public void hurtVolume(Float v) { this.hurtVolume = v; }
        public void hurtPitch(Float v) { this.hurtPitch = v; }
        public void deathVolume(Float v) { this.deathVolume = v; }
        public void deathPitch(Float v) { this.deathPitch = v; }
        public void stepVolume(Float v) { this.stepVolume = v; }
        public void stepPitch(Float v) { this.stepPitch = v; }

        public void texture(String v) { this.texture = v; }
        public void model(String v) { this.model = v; }
        public void glowTexture(String v) { this.glowTexture = v; }
        public void emissive(Boolean v) { this.emissive = v; }
        public void tint(Integer v) { this.tint = v; }
        public void shadowRadius(Float v) { this.shadowRadius = v; }
        public void scaleVisual(Float v) { this.scaleVisual = v; }
        public void silent(Boolean v) { this.silent = v; }
        public void glowing(Boolean v) { this.glowing = v; }
        public void suppressBaseParticles(Boolean v) { this.suppressBaseParticles = v; }

        public void width(Float v) { this.width = v; }
        public void height(Float v) { this.height = v; }
        public void eyeHeight(Float v) { this.eyeHeight = v; }
        public void noGravity(Boolean v) { this.noGravity = v; }

        public void burnInDaylight(Boolean v) { this.burnInDaylight = v; }
        public void fireImmune(Boolean v) { this.fireImmune = v; }
        public void lavaImmune(Boolean v) { this.lavaImmune = v; }
        public void waterImmune(Boolean v) { this.waterImmune = v; }
        public void drowningImmune(Boolean v) { this.drowningImmune = v; }
        public void fallDamageImmune(Boolean v) { this.fallDamageImmune = v; }
        public void suffocationImmune(Boolean v) { this.suffocationImmune = v; }
        public void canPickUpItems(Boolean v) { this.canPickUpItems = v; }
        public void persistent(Boolean v) { this.persistent = v; }
        public void noDespawn(Boolean v) { this.noDespawn = v; }
        public void canClimb(Boolean v) { this.canClimb = v; }
        public void canWalkOnWater(Boolean v) { this.canWalkOnWater = v; }
        public void flying(Boolean v) { this.flying = v; }
        public void swimming(Boolean v) { this.swimming = v; }
        public void boss(Boolean v) { this.boss = v; }
        public void barColor(String v) { this.barColor = v; }
        public void barStyle(String v) { this.barStyle = v; }
        public void barName(String v) { this.barName = v; }
        public void barRange(Double v) { this.barRange = v; }
        public void darkenSky(Boolean v) { this.darkenSky = v; }
        public void fog(Boolean v) { this.fog = v; }
        public void bossMusic(Boolean v) { this.bossMusic = v; }
        public void neverDespawn(Boolean v) { this.neverDespawn = v; }


        public void attackCooldown(Double v) { this.attackCooldown = v; }
        public void reach(Double v) { this.reach = v; }
        public void aoeRadius(Double v) { this.aoeRadius = v; }
        public void projectileType(String v) { this.projectileType = v; }
        public void projectileSpeed(Double v) { this.projectileSpeed = v; }
        public void projectileDamage(Double v) { this.projectileDamage = v; }
        public void projectileExplosion(Double v) { this.projectileExplosion = v; }
        public void projectileGravity(Double v) { this.projectileGravity = v; }
        public void rangedAttackInterval(Integer v) { this.rangedAttackInterval = v; }
        public void rangedAttackRange(Integer v) { this.rangedAttackRange = v; }
        public void damageImmunities(String v) { this.damageImmunities = v; }

        public void particleType(String v) { this.particleType = v; }
        public void particleEnabled(Boolean v) { this.particleEnabled = v; }
        public void ambientEnabled(Boolean v) { this.ambientEnabled = v; }
        public void hurtEnabled(Boolean v) { this.hurtEnabled = v; }
        public void deathEnabled(Boolean v) { this.deathEnabled = v; }
        public void attackEnabled(Boolean v) { this.attackEnabled = v; }
        public void spawnEnabled(Boolean v) { this.spawnEnabled = v; }
        public void count(Integer v) { this.count = v; }
        public void frequencyTicks(Integer v) { this.frequencyTicks = v; }
        public void offsetX(Double v) { this.offsetX = v; }
        public void offsetY(Double v) { this.offsetY = v; }
        public void offsetZ(Double v) { this.offsetZ = v; }
        public void velocityX(Double v) { this.velocityX = v; }
        public void velocityY(Double v) { this.velocityY = v; }
        public void velocityZ(Double v) { this.velocityZ = v; }

        public void deathLootTable(String v) { this.deathLootTable = v; }
        public void xpMin(Integer v) { this.xpMin = v; }
        public void xpMax(Integer v) { this.xpMax = v; }
        public void alwaysDropEquipment(Boolean v) { this.alwaysDropEquipment = v; }
        public void dropChanceMainhand(Float v) { this.dropChanceMainhand = v; }
        public void dropChanceOffhand(Float v) { this.dropChanceOffhand = v; }
        public void dropChanceHead(Float v) { this.dropChanceHead = v; }
        public void dropChanceChest(Float v) { this.dropChanceChest = v; }
        public void dropChanceLegs(Float v) { this.dropChanceLegs = v; }
        public void dropChanceFeet(Float v) { this.dropChanceFeet = v; }
        public void dropChanceBody(Float v) { this.dropChanceBody = v; }
        public void pathfinding(java.util.Map<String, Float> v) { this.pathfinding = v; }
        public void scoreboardTags(String v) { this.scoreboardTags = v; }

        public Resolved build() {
            return new Resolved(this);
        }
    }
}