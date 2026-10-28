package com.strata.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What each ability does and what each of its fields means. */
public final class AbilityGuide {

    private AbilityGuide() {
    }

    /** What a tick is, for every tooltip that uses one. */
    public static final String TICK = "A tick is one step of the game: 20 ticks are 1 second.";

    /**
     * One ability explained.
     *
     * @param what what the ability does
     * @param parameter what the Parameter field means for it
     * @param amount what the Amount field means for it
     * @param ticks what the Ticks field means for it
     * @param radius what the Radius field means for it
     */
    public record Entry(String ability, String what, String parameter, String amount,
                        String ticks, String radius) {
    }

    /** A field's text when the ability does not use it: the field then greys. */
    public static final String NOT_USED = "Not used by this ability.";
    private static final String EFFECT_ID = "The effect to give, as an id: minecraft:speed,"
            + " minecraft:poison, minecraft:slowness. A mod's effects work too.";
    private static final String STRENGTH = "The effect's strength: 0 is level I, 1 is level II, 2 is level III.";

    private static final Map<String, Entry> KNOWN = new LinkedHashMap<>();

    private static void put(String ability, String what, String parameter, String amount,
                            String ticks, String radius) {
        KNOWN.put(ability, new Entry(ability, what, parameter, amount, ticks, radius));
    }

    static {
        put("sun_immune",
                "Puts out any fire on it at once, so daylight never burns it. Lava and fire still hurt"
                        + " it while it stands in them.",
                NOT_USED, NOT_USED, NOT_USED, NOT_USED);
        put("sun_weakness",
                "Burns in daylight under open sky, as a zombie or skeleton does; a helmet wears down instead."
                        + " If it walks, it seeks shade. Sun immune wins over it.",
                NOT_USED, NOT_USED, NOT_USED, NOT_USED);
        put("heal",
                "Heals itself while it is hurt, a set amount on a set timer.",
                NOT_USED,
                "Health healed each time. 2 is one heart.",
                "How often it heals, in ticks. Needed. 20 is once a second. " + TICK,
                NOT_USED);
        put("effect",
                "Gives itself an effect.",
                EFFECT_ID, STRENGTH,
                "Blank: the effect is on for good from its spawn. A number: the effect is given again"
                        + " that often, for effects milk or a mod can remove. " + TICK,
                NOT_USED);
        put("aura_players",
                "Gives an effect to every player near it, again and again. The effect lasts twice the"
                        + " timer, so it stays on while the player is near and ends soon after.",
                EFFECT_ID, STRENGTH,
                "How often it gives the effect, in ticks. Needed. " + TICK,
                "How near, in blocks. Blank is 8.");
        put("aura_mobs",
                "Gives an effect to every other creature near it (not players), again and again:"
                        + " monsters and animals alike. The effect lasts twice the timer.",
                EFFECT_ID, STRENGTH,
                "How often it gives the effect, in ticks. Needed. " + TICK,
                "How near, in blocks, measured as a box around it. Blank is 8.");
        put("on_hit_effect",
                "When it hurts a creature, that creature gets an effect.",
                EFFECT_ID, STRENGTH,
                "How long the effect lasts, in ticks. Blank is 100 (5 seconds). " + TICK,
                NOT_USED);
        put("on_hurt_effect",
                "When a creature hurts it, the attacker gets an effect.",
                EFFECT_ID, STRENGTH,
                "How long the effect lasts, in ticks. Blank is 100 (5 seconds). " + TICK,
                NOT_USED);
        put("damage_immunity",
                "It takes no damage of one kind.",
                "The kind of damage: a damage type like minecraft:lava, or a group with # like"
                        + " #minecraft:is_fire (all fire) or #minecraft:is_explosion.",
                NOT_USED, NOT_USED, NOT_USED);
    }

    /** The explanation for an ability name. */
    public static Entry of(String ability) {
        String key = ability == null ? "" : ability.strip().toLowerCase(Locale.ROOT);
        Entry known = KNOWN.get(key);
        return known != null ? known
                : new Entry(key, "An ability Strata does not know.", NOT_USED, NOT_USED, NOT_USED, NOT_USED);
    }

    /** Every ability the guide explains. */
    public static List<Entry> all() {
        return List.copyOf(KNOWN.values());
    }

    // ----------------------------------------------------------------------------------- THE SIX BEHAVIOUR
    // CLASSES -----------------------------------------------------------------------------------

    /** What an unknown class says, so the gate can spot a class with no entry. */
    public static final String UNKNOWN_CLASS = "An effect class Strata does not know.";

    /**
     * One effect class explained.
     *
     * @param effectClass the class id, as it is written in the table
     * @param what what running the class does
     * @param fields what each field means for it; an absent field is one the class does not read
     */
    public record ClassEntry(String effectClass, String what, Map<AbilityField, String> fields) {

        /** What a field says for this class: NOT_USED when it does not read it. */
        public String text(AbilityField field) {
            return field == null ? NOT_USED : fields.getOrDefault(field, NOT_USED);
        }

        /** True when the class reads the field, so the screen leaves it live. */
        public boolean reads(AbilityField field) {
            return !NOT_USED.equals(text(field));
        }
    }

    private static final Map<String, ClassEntry> CLASSES = new LinkedHashMap<>();

    // The row-level fields every class reads: what fires it, how often it lands, how far the numbers move,
    // and the two presentation overrides.
    private static final String TRIGGER_TEXT = "What fires the row: on spawn, on death, on take damage,"
            + " on flee, in sunlight, and the rest of the game's own triggers. One row names one"
            + " trigger, so a mob can hold an on spawn row and an on deal damage row at the same time.";
    private static final String CHANCE_TEXT = "The percent chance it lands on when its trigger fires,"
            + " 0 to 100. Blank is 100, so it always lands: a spider at 50 poisons half the times it hits.";
    private static final String PARTICLE_TEXT = "Overrides the particle the game already shows for this."
            + " Blank keeps the game's own.";
    private static final String HUD_ICON_TEXT = "The icon a player's HUD shows for the effect: another"
            + " effect's id to show its icon, none for no icon. Blank keeps the game's own.";

    private static final String PERMANENT_TICKS = "On a Permanent row, how often it lands, in ticks:"
            + " 20 (once a second) when blank. Other triggers do not read it.";

    private static final String ARMORED_NOTE = " Whether it goes through armor is read off this type's own"
            + " #minecraft:bypasses_armor tag, not from a field here.";

    /**
     * One class, with the row-level fields it shares with every other class and the fields that are its own.
     */
    private static void putClass(String id, String what, Map<AbilityField, String> own) {
        Map<AbilityField, String> fields = new LinkedHashMap<>();
        fields.put(AbilityField.TRIGGER, TRIGGER_TEXT);
        fields.put(AbilityField.CHANCE, CHANCE_TEXT);
        fields.put(AbilityField.PARTICLE, PARTICLE_TEXT);
        fields.put(AbilityField.HUD_ICON, HUD_ICON_TEXT);
        // EVERY CLASS reads Ticks on a Permanent row: how often it lands.
        fields.put(AbilityField.TICKS, PERMANENT_TICKS);
        fields.put(AbilityField.EFFECT_CLASS, what);
        fields.putAll(own);
        CLASSES.put(id, new ClassEntry(id, what, Map.copyOf(fields)));
    }

    static {
        putClass("apply_effect",
                "Gives itself a game effect by id - speed, slowness, strength, weakness, regeneration,"
                        + " absorption, and the rest of the game's effect registry. One class covers all of"
                        + " them: the effect, its level and how long it lasts are the row.",
                Map.of(
                        AbilityField.PARAMETER, EFFECT_ID,
                        AbilityField.AMOUNT, STRENGTH,
                        AbilityField.DURATION, "How long the effect lasts, in ticks. Blank: from the"
                                + " moment it is given, and for good."));

        putClass("damage_over_time",
                "x damage every y interval for z total time. This is the owner's poison, wither, fire,"
                        + " bleed and dire poison - one class for all five. Bleed is this with can kill set"
                        + " and a bleeding damage type; dire poison is this with more damage per hit.",
                Map.of(
                        AbilityField.DAMAGE, "Damage dealt each time it ticks.",
                        AbilityField.TICKS, "How often it deals that damage, in ticks. 20 is once a second."
                                + " Needed. " + TICK,
                        AbilityField.DURATION, "How long it keeps dealing it, in ticks. Needed. " + TICK,
                        AbilityField.CAN_KILL, "Whether the damage is allowed to take the last heart."
                                + " Bleed sets it; poison does not.",
                        AbilityField.DAMAGE_TYPE, "The kind of damage dealt, one id from the game's damage"
                                + " type registry: minecraft:magic (poison), minecraft:wither (bleed),"
                                + " minecraft:in_fire, or a bleeding one a mod adds." + ARMORED_NOTE));

        putClass("life_steal",
                "The target's health moves to the attacker.",
                Map.of(
                        AbilityField.AMOUNT, "Health moved, as a flat number rather than a share of the"
                                + " damage. 1 is half a heart, 2 is one heart.",
                        AbilityField.TRIGGER, "Triggered on deal damage"));

        putClass("attribute_change",
                "A named attribute is raised or lowered for a period, or cured by taking an item - the"
                        + " owner's weaken or strengthen, one class for both directions.",
                Map.of(
                        AbilityField.PARAMETER, "The attribute to move, as an id:"
                                + " minecraft:generic.attack_damage, minecraft:generic.movement_speed,"
                                + " minecraft:generic.armor.",
                        AbilityField.AMOUNT, "How far it moves, as a SHARE of the attribute's base value."
                                + " 0.5 adds half of what the mob starts with, -0.5 takes half away.",
                        AbilityField.DURATION, "How long the change lasts, in ticks. Needed. " + TICK,
                        AbilityField.CURE, "One item id that ends it when the mob has it:"
                                + " minecraft:milk_bucket, or a mod's own item."));

        putClass("explode",
                "Damage in a radius, and flat inside it: the same damage at the edge as in the middle.",
                Map.of(
                        AbilityField.DAMAGE, "Damage dealt inside the radius. Flat - not stronger at the centre.",
                        AbilityField.RADIUS, "How far it reaches, in blocks. Blank is 8.",
                        AbilityField.CAN_KILL, "Hurts itself: true, and the blast also hurts the mob that set it"
                                + " off. If that kills it, it dies as usual: drops, and On death fires.",
                        AbilityField.DAMAGE_TYPE, "The kind of damage dealt, one id from the game's damage"
                                + " type registry." + ARMORED_NOTE));

        putClass("explode_and_die",
                "Explodes as Explode does, then the mob is gone, as an exploding creeper is: it drops nothing.",
                Map.of(
                        AbilityField.DAMAGE, "Damage dealt inside the radius. Flat - not stronger at the centre.",
                        AbilityField.RADIUS, "How far it reaches, in blocks. Blank is 8.",
                        AbilityField.CAN_KILL, "Hurts itself: true, and the blast also hurts the mob that set it"
                                + " off. If that kills it, it dies as usual: drops, and On death fires.",
                        AbilityField.DAMAGE_TYPE, "The kind of damage dealt, one id from the game's damage"
                                + " type registry." + ARMORED_NOTE));

        putClass("spawn",
                "A named creature is spawned, and stays alive.",
                Map.of(
                        AbilityField.ENTITY, "The creature to spawn, picked from the game's entities:"
                                + " minecraft:zombie, minecraft:cave_spider, or one a mod adds.",
                        AbilityField.AMOUNT, "How many spawn. 1 is one of them.",
                        AbilityField.TRIGGER, "Triggered on spawn"));

        putClass("heal",
                "Exact health back, unlike the regeneration effect. Permanent with Ticks is the old heal"
                        + " ability: so much health every so often.",
                Map.of(
                        AbilityField.AMOUNT, "Health given back. 1 is half a heart, 2 is one heart."));

        putClass("aura",
                "A game effect on the creatures near it, not on itself - one kind of creature, a #tag of"
                        + " them, or all but players. Permanent with Ticks is the old auras.",
                Map.of(
                        AbilityField.PARAMETER, EFFECT_ID,
                        AbilityField.ENTITY, "Who gets it: one creature (minecraft:player for players,"
                                + " minecraft:spider), a #tag of them, or blank for every creature but players.",
                        AbilityField.AMOUNT, STRENGTH,
                        AbilityField.RADIUS, "How far it reaches, in blocks. Blank is 8.",
                        AbilityField.DURATION, "How long the effect lasts, in ticks. Blank: two of its"
                                + " intervals, so it wears off soon after a creature walks away."));

        putClass("kill",
                "The target dies outright, as /kill does. With In sunlight, it is a creature the sun kills.",
                Map.of());
    }

    /** The explanation for an effect class name. */
    public static ClassEntry ofClass(String effectClass) {
        String key = effectClass == null ? "" : effectClass.strip().toLowerCase(Locale.ROOT);
        ClassEntry known = CLASSES.get(key);
        return known != null ? known : new ClassEntry(key, UNKNOWN_CLASS, Map.of());
    }

    /** What ONE field says for ONE row, by id - a class id or one of the nine ability names. */
    public static String fieldText(String id, AbilityField field) {
        if (field == null) {
            return NOT_USED;
        }
        if (EffectClass.of(id) != null) {
            ClassEntry entry = ofClass(id);
            return field == AbilityField.EFFECT_CLASS ? entry.what() : entry.text(field);
        }
        Entry entry = of(id);
        return switch (field) {
            case ABILITY -> entry.ability() + ": " + entry.what();
            case PARAMETER -> entry.parameter();
            case AMOUNT -> entry.amount();
            case TICKS -> entry.ticks();
            case RADIUS -> entry.radius();
            default -> NOT_USED;
        };
    }

    /** Ticks in words: "40 ticks (2 seconds)". */
    public static String ticksWords(String ticks) {
        double t;
        try {
            t = Double.parseDouble(ticks == null ? "" : ticks.strip());
        } catch (NumberFormatException e) {
            return "";
        }
        double seconds = t / 20.0;
        String s = seconds == Math.rint(seconds) ? String.valueOf((long) seconds) : String.valueOf(seconds);
        return (long) t + " ticks (" + s + (seconds == 1 ? " second)" : " seconds)");
    }

    /**
     * One ability row in a sentence: "Heals 2 health (1 heart) every 20 ticks (1 second)." Pure, so the gate
     * can pin it.
     */
    public static String sentence(String ability, String parameter, String amount, String ticks, String radius) {
        String a = ability == null ? "" : ability.strip().toLowerCase(Locale.ROOT);
        String p = parameter == null ? "" : parameter.strip();
        String level = levelWords(amount);
        String every = ticks == null || ticks.isBlank() ? "" : ticksWords(ticks);
        String near = radius == null || radius.isBlank() ? "8" : radius.strip();
        return switch (a) {
            case "sun_immune" -> "Never burns in daylight.";
            case "sun_weakness" -> "Burns in daylight.";
            case "heal" -> "Heals " + (amount == null || amount.isBlank() ? "0" : amount.strip())
                    + " health every " + every + ".";
            case "effect" -> "Has " + p + " " + level + (every.isEmpty() ? " for good." : ", given again every " + every + ".");
            case "aura_players" -> "Gives players within " + near + " blocks " + p + " " + level
                    + " every " + every + ".";
            case "aura_mobs" -> "Gives other creatures within " + near + " blocks " + p + " " + level
                    + " every " + every + ".";
            case "on_hit_effect" -> "What it hurts gets " + p + " " + level + " for "
                    + (every.isEmpty() ? ticksWords("100") : every) + ".";
            case "on_hurt_effect" -> "Whoever hurts it gets " + p + " " + level + " for "
                    + (every.isEmpty() ? ticksWords("100") : every) + ".";
            case "damage_immunity" -> "Takes no " + p + " damage.";
            default -> "";
        };
    }

    private static String levelWords(String amount) {
        int amplifier;
        try {
            amplifier = (int) Double.parseDouble(amount == null || amount.isBlank() ? "0" : amount.strip());
        } catch (NumberFormatException e) {
            amplifier = 0;
        }
        String[] roman = {"I", "II", "III", "IV", "V"};
        return "level " + (amplifier >= 0 && amplifier < roman.length ? roman[amplifier] : String.valueOf(amplifier + 1));
    }
}
