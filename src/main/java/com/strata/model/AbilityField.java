package com.strata.model;

/**
 * Every field an ability row has, for the two things that ask about a field by name: whether it is live for
 * the row on screen, and what it means there.
 */
public enum AbilityField {

    /** The legacy name: one of the nine the runtime knows today. */
    ABILITY("ability", "Ability"),
    /** Which of the six behaviour classes the row is. */
    EFFECT_CLASS("effect_class", "Class"),
    /** What fires the row. */
    TRIGGER("trigger", "Trigger"),
    /** The percent chance it lands on when its trigger fires. */
    CHANCE("chance", "Chance"),
    // VARIANCE USED TO BE HERE and is gone.
    /** The id the class names: an effect, an attribute, or a creature. */
    PARAMETER("parameter", "Parameter"),
    /** The class's one number: a level, a flat move, a share, or a count. */
    AMOUNT("amount", "Amount"),
    /** How often it happens again, in ticks. */
    TICKS("interval_ticks", "Ticks"),
    /** How near, in blocks. */
    RADIUS("radius", "Radius"),
    /** Damage per hit. */
    DAMAGE("damage", "Damage"),
    /** How long it lasts, in ticks. */
    DURATION("duration", "Duration"),
    /** Whether it may take the last heart. */
    CAN_KILL("can_kill", "Can kill"),
    /** One id from the game's damage type registry. */
    DAMAGE_TYPE("damage_type", "Damage type"),
    /** Overrides the particle the game effect shows. */
    PARTICLE("particle", "Particle"),
    /** Overrides the HUD icon the game effect shows. */
    HUD_ICON("hud_icon", "HUD icon"),
    /** The one item id that ends an attribute_change. */
    CURE("cure", "Cure"),
    /** One entity type id the class acts on: the creature spawn makes. */
    ENTITY("entity", "Entity");

    private final String key;
    private final String label;

    AbilityField(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** The name this field is known by - the CSV column it reads. */
    public String key() {
        return key;
    }

    /** What the editor prints on the field. */
    public String label() {
        return label;
    }

    /** The field a column belongs to, or null when the column is an identity one. */
    public static AbilityField of(String key) {
        if (key == null) {
            return null;
        }
        String want = key.strip();
        for (AbilityField field : values()) {
            if (field.key.equals(want)) {
                return field;
            }
        }
        return null;
    }
}
