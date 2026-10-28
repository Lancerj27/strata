package com.strata.domain;

import com.strata.Strata;
import java.util.List;
import java.util.Locale;

/** THE OFFICIAL TRIGGER LIST. */
public final class TriggerOps {

    private TriggerOps() {}

    /**
     * One trigger definition.
     *
     * @param id the trigger id (e.g. "on_spawn")
     * @param displayName what the editor shows (e.g. "On spawn")
     * @param description when it fires, in the designer's words
     * @param hostable true when Strata has a hook that can fire this
     * @param event the game event it maps to, for documentation
     */
    public record Trigger(String id, String displayName, String description,
                          boolean hostable, String event) {

        /** The ID in lower case with underscores. */
        public String id() {
            return id.toLowerCase(Locale.ROOT);
        }
    }

    /** All triggers Strata knows, in the order the editor shows them. */
    private static final List<Trigger> ALL = List.of(
            new Trigger("on_spawn",
                    "On spawn",
                    "When the mob first spawns into the world.",
                    true,
                    "FinalizeSpawnEvent"),
            new Trigger("on_death",
                    "On death",
                    "When the mob dies.",
                    true,
                    "LivingDeathEvent"),
            new Trigger("on_take_damage",
                    "On take damage",
                    "When the mob takes any damage.",
                    true,
                    "LivingIncomingDamageEvent"),
            new Trigger("on_flee",
                    "On flee",
                    "When the mob starts to run: panicking, avoiding what it fears (a skeleton from a wolf), or fleeing the sun.",
                    true,
                    "PanicGoal, AvoidEntityGoal, FleeSunGoal start (PanicFleeMixin and the others)"),
            new Trigger("on_hit",
                    "On deal damage",
                    "When the mob deals damage to another creature, up close or with a projectile.",
                    true,
                    "LivingDamageEvent.Post (attacker)"),
            new Trigger("on_hurt",
                    "On take damage from a creature",
                    "When the mob is hurt by another entity.",
                    true,
                    "LivingIncomingDamageEvent"),
            new Trigger("in_sunlight",
                    "In sunlight",
                    "Every tick the mob stands in direct sunlight.",
                    true,
                    "EntityTickEvent.Post + sunlight check"),
            // What a worn item or a block stood
            // on needs, and the old always-on abilities (an aura, heal) are.
            new Trigger("permanent",
                    "Permanent",
                    "All the time: again every Ticks (20 when blank) while the mob lives,"
                            + " the item is worn, or something stands on the block.",
                    true,
                    "EntityTickEvent.Post every interval")
,
            // a projectile's abilities, run where it lands.
            new Trigger("on_impact",
                    "On impact",
                    "When the projectile hits a creature or a block. Spawn, Explode and Aura also"
                            + " work on a block; the rest need a creature hit.",
                    true,
                    "ProjectileImpactEvent")
    );

    /** The trigger a projectile's abilities run on. */
    public static final String IMPACT = "on_impact";

    /** Every trigger but On impact: what a creature, a worn item or a block can fire. */
    public static List<String> creatureTriggers() {
        return ALL.stream().map(Trigger::id).filter(id -> !IMPACT.equals(id)).toList();
    }

    /** All triggers, in order. */
    public static List<Trigger> all() {
        return ALL;
    }

    /** The trigger with this id, or null. */
    public static Trigger byId(String id) {
        String key = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        for (Trigger t : ALL) {
            if (t.id().equals(key)) {
                return t;
            }
        }
        return null;
    }

    /** True when this id names a trigger Strata knows. */
    public static boolean isKnown(String id) {
        return byId(id) != null;
    }

    /** True when this trigger is hostable (Strata can fire it). */
    public static boolean isHostable(String id) {
        Trigger t = byId(id);
        return t != null && t.hostable();
    }

    /** The trigger's display name, or the id if unknown. */
    public static String displayName(String id) {
        Trigger t = byId(id);
        return t != null ? t.displayName() : id;
    }

    /** The trigger's description, or "". */
    public static String description(String id) {
        Trigger t = byId(id);
        return t != null ? t.description() : "";
    }

    /** All hostable triggers (for the editor's dropdown). */
    public static List<Trigger> hostable() {
        return ALL.stream().filter(Trigger::hostable).toList();
    }

    /** All non-hostable triggers (for the editor's "not yet" section). */
    public static List<Trigger> unhostable() {
        return ALL.stream().filter(t -> !t.hostable()).toList();
    }

    /** Problem text for a trigger id, or "" when it passes. */
    public static String problem(String triggerId) {
        if (triggerId == null || triggerId.isBlank()) {
            return "trigger is blank";
        }
        String key = triggerId.strip().toLowerCase(Locale.ROOT);
        if (!isKnown(key)) {
            return "unknown trigger '" + triggerId + "'. Valid: " + validIds();
        }
        return "";
    }

    /** Comma-separated list of valid trigger ids. */
    public static String validIds() {
        return ALL.stream().map(Trigger::id).reduce((a, b) -> a + ", " + b).orElse("");
    }
}