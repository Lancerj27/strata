package com.strata.model;

import java.util.Map;
import java.util.regex.Pattern;

/** What the Mob data values do. */
public final class FieldGuide {

    private FieldGuide() {
    }

    /** The fields that are settings, and what each does. */
    public static final Map<String, String> SETTINGS = Map.ofEntries(
            // The Entity fields that are settings (GameLayer#ENTITY_SETTINGS).
            Map.entry("Entity.invulnerable", "true: nothing hurts it but the void and /kill."),
            Map.entry("Entity.noCulling", "true: it is drawn even when out of view or far off."),
            Map.entry("Entity.hasVisualFire", "true: it shows as on fire, without burning."),
            Map.entry("Entity.blocksBuilding", "false: blocks can be placed in its space, as with an item on the ground."),
            Map.entry("Entity.noPhysics", "true: it passes through blocks and is not pushed (as a vex)."),
            Map.entry("Entity.hasGlowingTag", "true: it glows with an outline seen through walls."),
            Map.entry("Mob.xpReward", "The XP it drops when a player kills it (before level scaling)."),
            Map.entry("Mob.canPickUpLoot", "true: it picks up items and armor it walks over."),
            Map.entry("Mob.persistenceRequired", "true: it never despawns."),
            Map.entry("Mob.bodyArmorDropChance", "The chance its body armor drops when it dies, 0 to 1."),
            Map.entry("LivingEntity.invulnerableDuration", "Ticks it cannot be hurt again after a hit (20 is 1 second)."),
            Map.entry("Zombie.canBreakDoors", "true: it breaks wooden doors (on Hard)."),
            Map.entry("Creeper.explosionRadius", "How big its explosion is."),
            Map.entry("Creeper.maxSwell", "Ticks from its hiss to its explosion (30 is 1.5 seconds)."),
            Map.entry("Ghast.explosionPower", "How big its fireball's explosion is."),
            Map.entry("AbstractHorse.canGallop", "true: it can gallop when ridden."),
            Map.entry("AbstractHorse.allowStandSliding", "true: it slides while it rears."),
            Map.entry("Hoglin.cannotBeHunted", "true: piglins do not hunt it."),
            Map.entry("Piglin.cannotHunt", "true: it does not hunt hoglins."),
            Map.entry("Raider.canJoinRaid", "true: it can join a raid."),
            Map.entry("PatrollingMonster.patrolLeader", "true: it leads a patrol and carries the banner."),
            Map.entry("PatrollingMonster.patrolling", "true: it walks a patrol route."),
            Map.entry("Vindicator.isJohnny", "true: it attacks every mob, as a vindicator named Johnny does."),
            Map.entry("SkeletonHorse.isTrap", "true: it is a skeleton trap that makes riders when a player comes near."),
            Map.entry("Vex.hasLimitedLife", "true: it dies after a time."),
            Map.entry("Villager.assignProfessionWhenSpawned", "true: it takes a profession as it spawns."),
            Map.entry("AgeableMob.age", "Below 0 it is a baby, and grows up as this counts to 0."),
            Map.entry("Chicken.isChickenJockey", "true: it is a chicken jockey's chicken, and never despawns."),
            Map.entry("WanderingTrader.despawnDelay", "Ticks until it leaves (24000 is one day)."),
            Map.entry("TraderLlama.despawnDelay", "Ticks until it leaves with its trader."),
            Map.entry("TropicalFish.isSchool", "true: it swims in a school."));

    /** The base classes every mob shares: their fields are running state but for their settings. */
    private static final java.util.Set<String> BASE = java.util.Set.of("Entity", "LivingEntity", "Mob",
            "PathfinderMob", "AgeableMob", "Animal");

    /** A field name that counts time, holds an animation, or keeps last frame's value. */
    private static final Pattern STATE = Pattern.compile(
            // Case does not matter for the endings; it does for "o" + a capital (oFlap, not orderedToSit).
            "(?i:.*(time|timer|timeout|tick|ticks|ticker|tickcount|counter|countdown|cooldown|delay|anim"
                    + "|animation|amount|timestamp|seed|cancelled|steps|angle|flap|swell|squish))"
                    + "|(?:o|old|last|lerp|clientSide)[A-Z].*|.*[a-z]O|.*0");

    /** True when a field is the mob's running state: set again as it lives. */
    public static boolean runningState(String key) {
        if (SETTINGS.containsKey(key)) {
            return false;
        }
        int dot = key.indexOf('.');
        String owner = dot > 0 ? key.substring(0, dot) : "";
        String name = dot > 0 ? key.substring(dot + 1) : key;
        return BASE.contains(owner) || STATE.matcher(name).matches();
    }

    /** What each type value does. */
    public static final Map<String, String> TYPE = Map.ofEntries(
            Map.entry("category", "Its kind: which mob caps and spawn rules it follows (monster, creature ...)."),
            Map.entry("width", "Its hitbox width, in blocks."),
            Map.entry("height", "Its hitbox height, in blocks."),
            Map.entry("eye_height", "How high its eyes are, in blocks: where it sees from."),
            Map.entry("fixed_size", "true: its hitbox does not change with its age or scale."),
            Map.entry("fire_immune", "true: fire and lava do not hurt it."),
            Map.entry("summonable", "true: /summon can make it."),
            Map.entry("spawn_far_from_player", "true: it spawns only far from players."),
            Map.entry("tracking_range", "How far away players see it, in chunks. A new value applies to mobs as they next load."),
            Map.entry("update_interval", "How often its movement is sent to players, in ticks. A new value applies to mobs as they next load."),
            Map.entry("immune_to", "Blocks it ignores: ones that would otherwise hurt it or that it steers clear of (wither rose, sweet berry bush, powder snow). Block ids separated by commas, as minecraft:wither_rose; none for no block at all."),
            Map.entry("spawn_placement", "Where it may spawn: on the ground, in water, in lava, or anywhere. Applies at the next game start."),
            Map.entry("spawn_heightmap", "Which surface its spawn height is measured from: the top block, the top block that stops movement (with or without leaves), or the ocean floor. Applies at the next game start."));

    /**
     * What a Mob data value does, in words, or "" when there are none.
     *
     * @param group the Mob data tab: type, field, or tag (GameLayer)
     */
    public static String what(String group, String key) {
        return switch (group) {
            case "type" -> TYPE.getOrDefault(key, "");
            case "field" -> SETTINGS.containsKey(key) ? SETTINGS.get(key)
                    : runningState(key) ? "Running state: the game sets it again as the mob lives, so a change"
                    + " would not last." : "";
            case "tag" -> "true: the mob is in the tag " + key + ". Tags decide what hunts it, what hurts it more,"
                    + " and what it may do.";
            default -> "";
        };
    }
}
