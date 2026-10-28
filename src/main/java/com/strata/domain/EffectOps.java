package com.strata.domain;

import com.strata.data.Parse;
import com.strata.data.Schema;
import com.strata.model.EffectClass;
import java.util.Map;

/**
 * THE EFFECT MODEL: reading one row of the new form - the trigger that fires it, the class it runs, the
 * chance it lands on, and the attributes that class reads.
 */
public final class EffectOps {

    private EffectOps() {
    }

    /**
     * One row of the new form, after {@link #parse}.
     *
     * @param effectClass the class the row runs
     * @param trigger what fires the row, blank on a row that does not say
     * @param chance percent it lands on, 0 to 100, blank read as 100
     * @param parameter the id the class names: an effect, an attribute, a creature
     * @param amount the class's one number: a level, a flat move, a share, a count
     * @param interval how often it happens again, in ticks
     * @param radius how near, in blocks
     * @param damage damage per hit
     * @param duration total time, in ticks
     * @param canKill whether it may take the last heart
     * @param damageType one id from the game's damage type registry
     * @param particle overrides the particle the game shows
     * @param hudIcon overrides the HUD icon the game shows
     * @param cure the one item id that ends an attribute_change
     * @param entity the one entity type id the class acts on (spawn's creature)
     */
    public record EffectRow(EffectClass effectClass, String trigger, double chance,
                            String parameter, double amount, double interval, double radius,
                            double damage, double duration, boolean canKill, String damageType,
                            String particle, String hudIcon, String cure, String entity) {
    }

    /** True when this row belongs to one of the six classes rather than to the legacy nine. */
    public static boolean isEffectRow(Map<String, String> row) {
        return !cell(row, Schema.Abilities.EFFECT_CLASS).isEmpty();
    }

    /**
     * AN OLD ABILITY ROW AS THE CLASS ROW IT ALWAYS WAS, or null when the row is a class row already or one
     * of the abilities that stay as they are (sun immune, sun weakness, damage immunity).
     *
     * @param worn true for an item or block variant's row, which has no spawn
     */
    public static Map<String, String> legacyAsClass(Map<String, String> row, boolean worn) {
        if (row == null || isEffectRow(row)) {
            return null;
        }
        String ability = cell(row, Schema.Abilities.ABILITY).toLowerCase(java.util.Locale.ROOT);
        int interval = (int) Parse.number(cell(row, Schema.Abilities.INTERVAL_TICKS), 0);
        Map<String, String> out = new java.util.HashMap<>(row);
        switch (ability) {
            case "heal" -> {
                out.put(Schema.Abilities.EFFECT_CLASS, "heal");
                out.put(Schema.Abilities.TRIGGER, "permanent");
            }
            case "effect" -> {
                out.put(Schema.Abilities.EFFECT_CLASS, "apply_effect");
                if (interval > 0 || worn) {
                    int every = interval > 0 ? interval : 20;
                    out.put(Schema.Abilities.TRIGGER, "permanent");
                    out.put(Schema.Abilities.INTERVAL_TICKS, String.valueOf(every));
                    out.put(Schema.Abilities.DURATION, String.valueOf(every * 2));
                } else {
                    out.put(Schema.Abilities.TRIGGER, "on_spawn");
                    out.put(Schema.Abilities.DURATION, "");
                }
            }
            case "on_hit_effect", "on_hurt_effect" -> {
                out.put(Schema.Abilities.EFFECT_CLASS, "apply_effect");
                out.put(Schema.Abilities.TRIGGER, ability.equals("on_hit_effect") ? "on_hit" : "on_hurt");
                out.put(Schema.Abilities.DURATION, String.valueOf(interval > 0 ? interval : 100));
            }
            case "aura_players", "aura_mobs" -> {
                out.put(Schema.Abilities.EFFECT_CLASS, "aura");
                out.put(Schema.Abilities.TRIGGER, "permanent");
                // The old aura_mobs kept its one creature in the cure column.
                out.put(Schema.Abilities.ENTITY, ability.equals("aura_players") ? "minecraft:player"
                        : cell(row, Schema.Abilities.CURE));
                out.put(Schema.Abilities.CURE, "");
                out.put(Schema.Abilities.DURATION, "");
            }
            default -> {
                return null;
            }
        }
        out.put(Schema.Abilities.ABILITY, "");
        return out;
    }

    /** Why this row cannot be read as an effect row, or "" when it can. */
    public static String problem(Map<String, String> row) {
        String named = cell(row, Schema.Abilities.EFFECT_CLASS);
        if (named.isEmpty()) {
            return "this row names no effect class";
        }
        if (EffectClass.of(named) == null) {
            return "unknown effect class '" + named + "'";
        }
        return "";
    }

    /** The row, or null when {@link #problem} is not empty. */
    public static EffectRow parse(Map<String, String> row) {
        if (row == null || EffectClass.of(cell(row, Schema.Abilities.EFFECT_CLASS)) == null) {
            return null;
        }
        return new EffectRow(
                EffectClass.of(cell(row, Schema.Abilities.EFFECT_CLASS)),
                cell(row, Schema.Abilities.TRIGGER),
                chance(cell(row, Schema.Abilities.CHANCE)),
                cell(row, Schema.Abilities.PARAMETER),
                Parse.number(cell(row, Schema.Abilities.AMOUNT), 0),
                Parse.number(cell(row, Schema.Abilities.INTERVAL_TICKS), 0),
                Parse.number(cell(row, Schema.Abilities.RADIUS), 0),
                Parse.number(cell(row, Schema.Abilities.DAMAGE), 0),
                Parse.number(cell(row, Schema.Abilities.DURATION), 0),
                Parse.flag(cell(row, Schema.Abilities.CAN_KILL), false),
                cell(row, Schema.Abilities.DAMAGE_TYPE),
                cell(row, Schema.Abilities.PARTICLE),
                cell(row, Schema.Abilities.HUD_ICON),
                cell(row, Schema.Abilities.CURE),
                cell(row, Schema.Abilities.ENTITY));
    }

    /** The percent a row lands on. */
    public static double chance(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            return 100;
        }
        double rolled = Parse.number(text, 100);
        return Math.min(100, Math.max(0, rolled));
    }

    /** A cell, stripped; blank when the file has no such column at all. */
    private static String cell(Map<String, String> row, String column) {
        if (row == null) {
            return "";
        }
        String value = row.get(column);
        return value == null ? "" : value.strip();
    }
}
