package com.strata.data;

import com.strata.model.VariantProperties;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * THE EIGHT MOB PROPERTY TABLES, BY LAYER.
 *
 * @param byInstance instance key to group to column to value
 */
public record MobPropertyLayers(Map<String, Map<String, Map<String, String>>> byInstance) {

    public static final MobPropertyLayers EMPTY = new MobPropertyLayers(Map.of());

    private static final String SEP = "\u001F";
    /** A base mob's owner name: its entity id, marked so no variant name can equal it. */
    private static final String BASE = "@";

    /** The key of one layer: who owns the row, and where it applies. */
    public static String key(String entity, String variant, String scope, String scopeId) {
        String s = scope == null ? "" : scope.strip().toLowerCase(Locale.ROOT);
        if ("global".equals(s)) {
            s = "";
        }
        String id = s.isEmpty() ? "" : (scopeId == null ? "" : scopeId.strip());
        String v = variant == null ? "" : variant.strip();
        String owner = v.isEmpty() ? BASE + (entity == null ? "" : entity.strip()) : v;
        return owner + SEP + s + SEP + id;
    }

    /** Reads every row of the eight tables under its layer. */
    public static MobPropertyLayers load(Path root) {
        Map<String, Map<String, Map<String, String>>> out = new HashMap<>();
        for (Map.Entry<String, String> table : Schema.MOB_PROPERTY_FILES.entrySet()) {
            String group = table.getKey();
            List<Schema.Prop> props = Schema.mobProperties(group);
            for (Map<String, String> row : CsvTable.read(root.resolve(table.getValue()))) {
                if (!Parse.flag(row.get("enabled"), true)) {
                    continue;
                }
                String entity = cell(row, "entity");
                String variant = cell(row, "variant_name");
                if (entity.isEmpty() && variant.isEmpty()) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (Schema.Prop p : props) {
                    String v = cell(row, p.column());
                    if (!v.isEmpty()) {
                        values.put(p.column(), v);
                    }
                }
                if (values.isEmpty()) {
                    continue;
                }
                out.computeIfAbsent(key(entity, variant, cell(row, "scope"), cell(row, "scope_id")),
                                k -> new HashMap<>())
                        .computeIfAbsent(group, k -> new LinkedHashMap<>()).putAll(values);
            }
        }
        return new MobPropertyLayers(Map.copyOf(out));
    }

    /** One layer's own values, group to column to value; empty when it has none. */
    public Map<String, Map<String, String>> layer(String entity, String variant, String scope, String scopeId) {
        return byInstance.getOrDefault(key(entity, variant, scope, scopeId), Map.of());
    }

    /**
     * A mob's values with every layer stacked, lowest first.
     *
     * @param variant the variant's name, or blank for the base mob alone
     * @param tier the tier it spawned in, or blank
     * @param biome the biome it spawned in, or blank
     */
    public Map<String, Map<String, String>> stacked(String entity, String variant, String tier, String biome) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        stack(out, layer(entity, "", "", ""));
        stackScoped(out, entity, "", tier, biome);
        if (variant != null && !variant.isBlank()) {
            stack(out, layer(entity, variant, "", ""));
            stackScoped(out, entity, variant, tier, biome);
        }
        return out;
    }

    private void stackScoped(Map<String, Map<String, String>> out, String entity, String variant,
                             String tier, String biome) {
        if (tier != null && !tier.isBlank()) {
            stack(out, layer(entity, variant, "tier", tier));
        }
        if (biome != null && !biome.isBlank()) {
            stack(out, layer(entity, variant, "biome", biome));
        }
    }

    private static void stack(Map<String, Map<String, String>> out, Map<String, Map<String, String>> layer) {
        layer.forEach((group, values) -> out.computeIfAbsent(group, k -> new LinkedHashMap<>()).putAll(values));
    }

    /** A mob's properties, resolved: its stacked layers read into the record the game reads. */
    public VariantProperties.Resolved resolve(String entity, String variant, String tier, String biome) {
        return build(stacked(entity, variant, tier, biome));
    }

    /** True when no row holds anything. */
    public boolean isEmpty() {
        return byInstance.isEmpty();
    }

    /** Stacked values read into the record. */
    public static VariantProperties.Resolved build(Map<String, Map<String, String>> groups) {
        var b = new VariantProperties.Builder();
        Map<String, String> row;

        if ((row = groups.get("sounds")) != null) {
            b.ambientSound(row.get(Schema.MobSounds.AMBIENT_SOUND));
            b.hurtSound(row.get(Schema.MobSounds.HURT_SOUND));
            b.deathSound(row.get(Schema.MobSounds.DEATH_SOUND));
            b.stepSound(row.get(Schema.MobSounds.STEP_SOUND));
            b.attackSound(row.get(Schema.MobSounds.ATTACK_SOUND));
            b.swimSound(row.get(Schema.MobSounds.SWIM_SOUND));
            b.fallSound(row.get(Schema.MobSounds.FALL_SOUND));
            if (row.containsKey(Schema.MobSounds.AMBIENT_VOLUME)) b.ambientVolume(Parse.floatNumber(row.get(Schema.MobSounds.AMBIENT_VOLUME), 0));
            if (row.containsKey(Schema.MobSounds.AMBIENT_PITCH)) b.ambientPitch(Parse.floatNumber(row.get(Schema.MobSounds.AMBIENT_PITCH), 0));
            if (row.containsKey(Schema.MobSounds.HURT_VOLUME)) b.hurtVolume(Parse.floatNumber(row.get(Schema.MobSounds.HURT_VOLUME), 0));
            if (row.containsKey(Schema.MobSounds.HURT_PITCH)) b.hurtPitch(Parse.floatNumber(row.get(Schema.MobSounds.HURT_PITCH), 0));
            if (row.containsKey(Schema.MobSounds.DEATH_VOLUME)) b.deathVolume(Parse.floatNumber(row.get(Schema.MobSounds.DEATH_VOLUME), 0));
            if (row.containsKey(Schema.MobSounds.DEATH_PITCH)) b.deathPitch(Parse.floatNumber(row.get(Schema.MobSounds.DEATH_PITCH), 0));
            if (row.containsKey(Schema.MobSounds.STEP_VOLUME)) b.stepVolume(Parse.floatNumber(row.get(Schema.MobSounds.STEP_VOLUME), 0));
            if (row.containsKey(Schema.MobSounds.STEP_PITCH)) b.stepPitch(Parse.floatNumber(row.get(Schema.MobSounds.STEP_PITCH), 0));
        }
        if ((row = groups.get("visuals")) != null) {
            b.texture(row.get(Schema.MobVisuals.TEXTURE));
            b.model(row.get(Schema.MobVisuals.MODEL));
            b.glowTexture(row.get(Schema.MobVisuals.GLOW_TEXTURE));
            if (row.containsKey(Schema.MobVisuals.EMISSIVE)) b.emissive(Parse.flag(row.get(Schema.MobVisuals.EMISSIVE), false));
            if (row.containsKey(Schema.MobVisuals.TINT)) b.tint(colour(row.get(Schema.MobVisuals.TINT)));
            if (row.containsKey(Schema.MobVisuals.SHADOW_RADIUS)) b.shadowRadius(Parse.floatNumber(row.get(Schema.MobVisuals.SHADOW_RADIUS), 0));
            if (row.containsKey(Schema.MobVisuals.SCALE_VISUAL)) b.scaleVisual(Parse.floatNumber(row.get(Schema.MobVisuals.SCALE_VISUAL), 0));
            if (row.containsKey(Schema.MobVisuals.SILENT)) b.silent(Parse.flag(row.get(Schema.MobVisuals.SILENT), false));
            if (row.containsKey(Schema.MobVisuals.GLOWING)) b.glowing(Parse.flag(row.get(Schema.MobVisuals.GLOWING), false));
            // BLANK IS TRUE: a swapped model has always dropped the base mob's particles.
            if (row.containsKey(Schema.MobVisuals.SUPPRESS_BASE_PARTICLES))
                b.suppressBaseParticles(Parse.flag(row.get(Schema.MobVisuals.SUPPRESS_BASE_PARTICLES), true));
        }
        if ((row = groups.get("physics")) != null) {
            if (row.containsKey(Schema.MobPhysics.WIDTH)) b.width(Parse.floatNumber(row.get(Schema.MobPhysics.WIDTH), 0));
            if (row.containsKey(Schema.MobPhysics.HEIGHT)) b.height(Parse.floatNumber(row.get(Schema.MobPhysics.HEIGHT), 0));
            if (row.containsKey(Schema.MobPhysics.EYE_HEIGHT)) b.eyeHeight(Parse.floatNumber(row.get(Schema.MobPhysics.EYE_HEIGHT), 0));
            if (row.containsKey(Schema.MobPhysics.NO_GRAVITY)) b.noGravity(Parse.flag(row.get(Schema.MobPhysics.NO_GRAVITY), false));
        }
        if ((row = groups.get("flags")) != null) {
            if (row.containsKey(Schema.MobFlags.BURN_IN_DAYLIGHT)) b.burnInDaylight(Parse.flag(row.get(Schema.MobFlags.BURN_IN_DAYLIGHT), false));
            if (row.containsKey(Schema.MobFlags.FIRE_IMMUNE)) b.fireImmune(Parse.flag(row.get(Schema.MobFlags.FIRE_IMMUNE), false));
            if (row.containsKey(Schema.MobFlags.LAVA_IMMUNE)) b.lavaImmune(Parse.flag(row.get(Schema.MobFlags.LAVA_IMMUNE), false));
            if (row.containsKey(Schema.MobFlags.WATER_IMMUNE)) b.waterImmune(Parse.flag(row.get(Schema.MobFlags.WATER_IMMUNE), false));
            if (row.containsKey(Schema.MobFlags.DROWNING_IMMUNE)) b.drowningImmune(Parse.flag(row.get(Schema.MobFlags.DROWNING_IMMUNE), false));
            if (row.containsKey(Schema.MobFlags.FALL_DAMAGE_IMMUNE)) b.fallDamageImmune(Parse.flag(row.get(Schema.MobFlags.FALL_DAMAGE_IMMUNE), false));
            if (row.containsKey(Schema.MobFlags.SUFFOCATION_IMMUNE)) b.suffocationImmune(Parse.flag(row.get(Schema.MobFlags.SUFFOCATION_IMMUNE), false));
            if (row.containsKey(Schema.MobFlags.CAN_PICK_UP_ITEMS)) b.canPickUpItems(Parse.flag(row.get(Schema.MobFlags.CAN_PICK_UP_ITEMS), false));
            if (row.containsKey(Schema.MobFlags.PERSISTENT)) b.persistent(Parse.flag(row.get(Schema.MobFlags.PERSISTENT), false));
            if (row.containsKey(Schema.MobFlags.NO_DESPAWN)) b.noDespawn(Parse.flag(row.get(Schema.MobFlags.NO_DESPAWN), false));
            if (row.containsKey(Schema.MobFlags.CAN_CLIMB)) b.canClimb(Parse.flag(row.get(Schema.MobFlags.CAN_CLIMB), false));
            if (row.containsKey(Schema.MobFlags.CAN_WALK_ON_WATER)) b.canWalkOnWater(Parse.flag(row.get(Schema.MobFlags.CAN_WALK_ON_WATER), false));
            if (row.containsKey(Schema.MobFlags.FLYING)) b.flying(Parse.flag(row.get(Schema.MobFlags.FLYING), false));
            if (row.containsKey(Schema.MobFlags.SWIMMING)) b.swimming(Parse.flag(row.get(Schema.MobFlags.SWIMMING), false));
            if (row.containsKey(Schema.MobFlags.BOSS)) b.boss(Parse.flag(row.get(Schema.MobFlags.BOSS), false));
            b.scoreboardTags(row.get(Schema.MobFlags.SCOREBOARD_TAGS));
        }
        if ((row = groups.get("combat")) != null) {
            if (row.containsKey(Schema.MobCombat.ATTACK_COOLDOWN)) b.attackCooldown(Parse.number(row.get(Schema.MobCombat.ATTACK_COOLDOWN), 0));
            if (row.containsKey(Schema.MobCombat.REACH)) b.reach(Parse.number(row.get(Schema.MobCombat.REACH), 0));
            if (row.containsKey(Schema.MobCombat.AOE_RADIUS)) b.aoeRadius(Parse.number(row.get(Schema.MobCombat.AOE_RADIUS), 0));
            b.projectileType(row.get(Schema.MobCombat.PROJECTILE_TYPE));
            if (row.containsKey(Schema.MobCombat.PROJECTILE_SPEED)) b.projectileSpeed(Parse.number(row.get(Schema.MobCombat.PROJECTILE_SPEED), 0));
            if (row.containsKey(Schema.MobCombat.PROJECTILE_DAMAGE)) b.projectileDamage(Parse.number(row.get(Schema.MobCombat.PROJECTILE_DAMAGE), 0));
            if (row.containsKey(Schema.MobCombat.PROJECTILE_EXPLOSION)) b.projectileExplosion(Parse.number(row.get(Schema.MobCombat.PROJECTILE_EXPLOSION), 0));
            if (row.containsKey(Schema.MobCombat.PROJECTILE_GRAVITY)) b.projectileGravity(gravity(row.get(Schema.MobCombat.PROJECTILE_GRAVITY)));
            if (row.containsKey(Schema.MobCombat.RANGED_ATTACK_INTERVAL)) b.rangedAttackInterval(Parse.integer(row.get(Schema.MobCombat.RANGED_ATTACK_INTERVAL), 0));
            if (row.containsKey(Schema.MobCombat.RANGED_ATTACK_RANGE)) b.rangedAttackRange(Parse.integer(row.get(Schema.MobCombat.RANGED_ATTACK_RANGE), 0));
            b.damageImmunities(row.get(Schema.MobCombat.DAMAGE_IMMUNITIES));
        }
        if ((row = groups.get("particles")) != null) {
            b.particleType(row.get(Schema.MobParticles.PARTICLE_TYPE));
            if (row.containsKey(Schema.MobParticles.PARTICLE_ENABLED)) b.particleEnabled(Parse.flag(row.get(Schema.MobParticles.PARTICLE_ENABLED), false));
            if (row.containsKey(Schema.MobParticles.AMBIENT_ENABLED)) b.ambientEnabled(Parse.flag(row.get(Schema.MobParticles.AMBIENT_ENABLED), false));
            if (row.containsKey(Schema.MobParticles.HURT_ENABLED)) b.hurtEnabled(Parse.flag(row.get(Schema.MobParticles.HURT_ENABLED), false));
            if (row.containsKey(Schema.MobParticles.DEATH_ENABLED)) b.deathEnabled(Parse.flag(row.get(Schema.MobParticles.DEATH_ENABLED), false));
            if (row.containsKey(Schema.MobParticles.ATTACK_ENABLED)) b.attackEnabled(Parse.flag(row.get(Schema.MobParticles.ATTACK_ENABLED), false));
            if (row.containsKey(Schema.MobParticles.SPAWN_ENABLED)) b.spawnEnabled(Parse.flag(row.get(Schema.MobParticles.SPAWN_ENABLED), false));
            if (row.containsKey(Schema.MobParticles.COUNT)) b.count(Parse.integer(row.get(Schema.MobParticles.COUNT), 0));
            if (row.containsKey(Schema.MobParticles.FREQUENCY_TICKS)) b.frequencyTicks(Parse.integer(row.get(Schema.MobParticles.FREQUENCY_TICKS), 0));
            if (row.containsKey(Schema.MobParticles.OFFSET_X)) b.offsetX(Parse.number(row.get(Schema.MobParticles.OFFSET_X), 0));
            if (row.containsKey(Schema.MobParticles.OFFSET_Y)) b.offsetY(Parse.number(row.get(Schema.MobParticles.OFFSET_Y), 0));
            if (row.containsKey(Schema.MobParticles.OFFSET_Z)) b.offsetZ(Parse.number(row.get(Schema.MobParticles.OFFSET_Z), 0));
            if (row.containsKey(Schema.MobParticles.VELOCITY_X)) b.velocityX(Parse.number(row.get(Schema.MobParticles.VELOCITY_X), 0));
            if (row.containsKey(Schema.MobParticles.VELOCITY_Y)) b.velocityY(Parse.number(row.get(Schema.MobParticles.VELOCITY_Y), 0));
            if (row.containsKey(Schema.MobParticles.VELOCITY_Z)) b.velocityZ(Parse.number(row.get(Schema.MobParticles.VELOCITY_Z), 0));
        }
        if ((row = groups.get("loot_xp")) != null) {
            b.deathLootTable(row.get(Schema.MobLootXp.DEATH_LOOT_TABLE));
            if (row.containsKey(Schema.MobLootXp.XP_MIN)) b.xpMin(Parse.integer(row.get(Schema.MobLootXp.XP_MIN), 0));
            if (row.containsKey(Schema.MobLootXp.XP_MAX)) b.xpMax(Parse.integer(row.get(Schema.MobLootXp.XP_MAX), 0));
            if (row.containsKey(Schema.MobLootXp.ALWAYS_DROP_EQUIPMENT)) b.alwaysDropEquipment(Parse.flag(row.get(Schema.MobLootXp.ALWAYS_DROP_EQUIPMENT), false));
            // DROP CHANCE PER SLOT: 0 or more, above 1 = always; anything else is left unset.
            b.dropChanceMainhand(dropChance(row.get(Schema.MobLootXp.DROP_CHANCE_MAINHAND)));
            b.dropChanceOffhand(dropChance(row.get(Schema.MobLootXp.DROP_CHANCE_OFFHAND)));
            b.dropChanceHead(dropChance(row.get(Schema.MobLootXp.DROP_CHANCE_HEAD)));
            b.dropChanceChest(dropChance(row.get(Schema.MobLootXp.DROP_CHANCE_CHEST)));
            b.dropChanceLegs(dropChance(row.get(Schema.MobLootXp.DROP_CHANCE_LEGS)));
            b.dropChanceFeet(dropChance(row.get(Schema.MobLootXp.DROP_CHANCE_FEET)));
            b.dropChanceBody(dropChance(row.get(Schema.MobLootXp.DROP_CHANCE_BODY)));
        }
        if ((row = groups.get("pathfinding")) != null) {
            // THE COST OF EACH TERRAIN TYPE: a number that does not read is left unset.
            Map<String, Float> costs = new LinkedHashMap<>();
            for (String type : Schema.PATH_TYPES) {
                String raw = row.get(type);
                if (raw != null && !raw.isBlank()) {
                    double v = Parse.number(raw, Double.NaN);
                    if (Double.isFinite(v)) {
                        costs.put(type, (float) v);
                    }
                }
            }
            if (!costs.isEmpty()) b.pathfinding(Map.copyOf(costs));
        }
        if ((row = groups.get("boss")) != null) {
            // THE BOSS BAR: a colour or style outside the game's set is left unset.
            String color = row.get(Schema.MobBoss.BAR_COLOR) == null ? "" : row.get(Schema.MobBoss.BAR_COLOR).strip().toLowerCase(Locale.ROOT);
            if (Schema.BOSS_COLORS.contains(color)) b.barColor(color);
            String style = row.get(Schema.MobBoss.BAR_STYLE) == null ? "" : row.get(Schema.MobBoss.BAR_STYLE).strip().toLowerCase(Locale.ROOT);
            if (Schema.BOSS_STYLES.contains(style)) b.barStyle(style);
            b.barName(row.get(Schema.MobBoss.BAR_NAME));
            if (row.containsKey(Schema.MobBoss.BAR_RANGE)) {
                double range = Parse.number(row.get(Schema.MobBoss.BAR_RANGE), 0);
                if (range > 0) b.barRange(range);
            }
            if (row.containsKey(Schema.MobBoss.DARKEN_SKY)) b.darkenSky(Parse.flag(row.get(Schema.MobBoss.DARKEN_SKY), false));
            if (row.containsKey(Schema.MobBoss.FOG)) b.fog(Parse.flag(row.get(Schema.MobBoss.FOG), false));
            if (row.containsKey(Schema.MobBoss.BOSS_MUSIC)) b.bossMusic(Parse.flag(row.get(Schema.MobBoss.BOSS_MUSIC), false));
            if (row.containsKey(Schema.MobBoss.NEVER_DESPAWN)) b.neverDespawn(Parse.flag(row.get(Schema.MobBoss.NEVER_DESPAWN), false));
        }
        return b.build();
    }


    /** A colour as RRGGBB or AARRGGBB hex, with or without # or 0x; null when it is not one. */
    public static Integer colour(String raw) {
        String s = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x")) {
            s = s.substring(2);
        }
        if (!s.matches("[0-9a-f]{6}|[0-9a-f]{8}")) {
            return null;
        }
        return (int) Long.parseLong(s, 16);
    }

    /** A drop chance: a finite number of 0 or more (above 1 = always); null when it does not read as one. */
    private static Float dropChance(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        double v = Parse.number(raw, Double.NaN);
        return Double.isFinite(v) && v >= 0 ? (float) v : null;
    }

    private static String cell(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.strip();
    }

    /** The Projectile falls FLAG: "true" falls (1), "false" does not (0); a number is kept as it was written. */
    static double gravity(String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.equalsIgnoreCase("true")) return 1;
        if (t.equalsIgnoreCase("false")) return 0;
        return Parse.number(t, 1);
    }
}
