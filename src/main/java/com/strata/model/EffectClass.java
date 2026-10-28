package com.strata.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The behaviour classes. */
public enum EffectClass {

    /** Gives a game effect by id, with a level and a duration. */
    APPLY_EFFECT("apply_effect"),
    /** x damage every y interval for z total time. */
    DAMAGE_OVER_TIME("damage_over_time"),
    /** The target's health moves to the attacker. */
    LIFE_STEAL("life_steal"),
    /** A named attribute moves by a share of its base, or is cured by an item. */
    ATTRIBUTE_CHANGE("attribute_change"),
    /** Damage in a radius. */
    EXPLODE("explode"),
    /** Damage in a radius, then the mob is gone, as an exploding creeper is. */
    EXPLODE_AND_DIE("explode_and_die"),
    /** A named creature is spawned, and stays alive. */
    SPAWN("spawn"),
    /** Exact health back, unlike the regeneration effect (the old heal ability). */
    HEAL("heal"),
    /** A game effect on the creatures nearby, of one kind or all (the old auras). */
    AURA("aura"),
    /** The target dies outright. */
    KILL("kill");

    private final String id;

    EffectClass(String id) {
        this.id = id;
    }

    /** The name this class is written under in the table. */
    public String id() {
        return id;
    }

    /** The class a name is, or null when it is not one of the six. */
    public static EffectClass of(String id) {
        if (id == null) {
            return null;
        }
        String want = id.strip().toLowerCase(Locale.ROOT);
        for (EffectClass each : values()) {
            if (each.id.equals(want)) {
                return each;
            }
        }
        return null;
    }

    /** Every class id, in table order: what the editor offers. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(values().length);
        for (EffectClass each : values()) {
            out.add(each.id);
        }
        return List.copyOf(out);
    }
}
