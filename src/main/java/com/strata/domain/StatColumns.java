package com.strata.domain;

import com.strata.data.Config;
import com.strata.model.AttributeGuide;
import com.strata.model.Variant;
import com.strata.runtime.EntityBaseAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The stat columns of the mob lists. */
public final class StatColumns {

    private StatColumns() {
    }

    /** The columns until the user picks others. */
    public static final List<String> DEFAULT = List.of(
            "minecraft:generic.max_health",
            "minecraft:generic.attack_damage",
            "minecraft:generic.movement_speed");

    /** A column's heading: the stat's short name, or "-" for none. */
    public static String heading(String attribute) {
        return attribute == null || attribute.isBlank() ? "-" : AttributeGuide.of(attribute).name();
    }

    /** "minecraft:generic.max_health" for "generic.max_health" too, so two spellings match. */
    static String normal(String attribute) {
        String a = attribute == null ? "" : attribute.strip();
        return a.isEmpty() || a.contains(":") ? a : "minecraft:" + a;
    }

    /**
     * A mob's values for the columns, as text: "-" for a column set to none or a stat the mob does not have.
     *
     * @param base the base mob's stats by attribute id
     * @param multipliers the variant's multipliers, or none for a plain mob
     * @param absolutes the variant's own values, or none for a plain mob
     */
    public static List<String> values(List<String> columns, Map<String, Double> base,
                                      Map<String, Double> multipliers, Map<String, Double> absolutes) {
        Map<String, Double> mult = new HashMap<>();
        multipliers.forEach((k, v) -> mult.put(normal(k), v));
        Map<String, Double> abs = new HashMap<>();
        absolutes.forEach((k, v) -> abs.put(normal(k), v));
        List<String> out = new ArrayList<>();
        for (String column : columns) {
            String id = normal(column);
            Double value = abs.containsKey(id) ? abs.get(id)
                    : base.containsKey(id) ? base.get(id) * mult.getOrDefault(id, 1.0) : null;
            out.add(id.isEmpty() || value == null ? "-" : number(value));
        }
        return out;
    }

    /** A value with at most two places: "20", "0.23". */
    static String number(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /** The values for the mob lists: a plain mob when variant is blank. */
    public static List<String> valuesFor(List<String> columns, String entity, String variant,
                                         Map<String, Map<String, Double>> baseCache) {
        Map<String, Double> base = baseCache.computeIfAbsent(entity, e -> {
            Map<String, Double> stats = new HashMap<>();
            for (EntityBaseAttributes.AttributeRow row : EntityBaseAttributes.rowsFor(e)) {
                stats.put(row.attribute(), row.value());
            }
            return stats;
        });
        Variant v = variant == null || variant.isEmpty() ? null : Config.tables().variants().get(variant);
        return values(columns, base, v == null ? Map.of() : v.multipliers(), v == null ? Map.of() : v.absolutes());
    }

    /** The stats a column can show: every attribute the game has, by name. */
    public static List<com.strata.game.RegistryQuery.Option> choices() {
        List<com.strata.game.RegistryQuery.Option> out = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.keySet().forEach(id -> out.add(
                new com.strata.game.RegistryQuery.Option(id.toString(),
                        AttributeGuide.of(id.toString()).name(), "Stat")));
        return out;
    }
}
