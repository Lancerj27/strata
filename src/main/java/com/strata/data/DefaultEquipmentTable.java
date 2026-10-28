package com.strata.data;

import com.strata.model.DefaultEquipment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The game's default equipment rolls to table rows and back. */
public final class DefaultEquipmentTable {

    private DefaultEquipmentTable() {
    }

    /** The game family the roll was seeded from (EquipmentFamilies). */
    public static final String FAMILY = "family";
    public static final String ARMOR = "armor";
    public static final String ARMOR_STOP = "armor_stop";
    public static final String MATERIAL = "material";
    public static final String ITEM = "item";
    public static final String ENCHANT = "enchant";
    public static final String DROP = "drop";

    /** The columns: one roll per entity type. */
    public static final String ENTITY = "entity";
    public static final String RULE = "rule";
    public static final String SLOT = "slot";
    public static final String ITEM_COLUMN = "item";
    public static final String VALUE = "value";
    public static final String VALUE_HARD = "value_hard";

    /** The game layer's header. */
    public static final String HEADER = String.join(",", ENTITY, RULE, SLOT, ITEM_COLUMN, VALUE, VALUE_HARD);

    /** Game layer rows to rolls, by entity. */
    public static Map<String, DefaultEquipment> fromRows(List<Map<String, String>> rows) {
        Map<String, List<Map<String, String>>> byKey = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String k = cell(row, ENTITY);
            if (!k.isEmpty()) {
                byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(row);
            }
        }
        Map<String, DefaultEquipment> out = new LinkedHashMap<>();
        byKey.forEach((k, cells) -> out.put(k, roll(cells)));
        return out;
    }

    private static DefaultEquipment roll(List<Map<String, String>> cells) {
        DefaultEquipment none = DefaultEquipment.none();
        double armor = 0;
        double stop = none.armorStop();
        double stopHard = none.armorStopHard();
        Map<String, Double> materials = DefaultEquipment.noMaterials();
        List<DefaultEquipment.Item> items = new ArrayList<>();
        double weaponEnchant = none.weaponEnchant();
        double armorEnchant = none.armorEnchant();
        double drop = none.dropChance();
        for (Map<String, String> row : cells) {
            double value = Parse.number(row.get(VALUE), 0);
            String hardCell = cell(row, VALUE_HARD);
            double hard = hardCell.isEmpty() ? value : Parse.number(hardCell, value);
            switch (cell(row, RULE)) {
                case ARMOR -> armor = value;
                case ARMOR_STOP -> {
                    stop = value;
                    stopHard = hard;
                }
                case MATERIAL -> materials.put(cell(row, ITEM_COLUMN), value);
                case ITEM -> items.add(new DefaultEquipment.Item(cell(row, SLOT), cell(row, ITEM_COLUMN), value, hard));
                case ENCHANT -> {
                    if ("armor".equals(cell(row, SLOT))) {
                        armorEnchant = value;
                    } else {
                        weaponEnchant = value;
                    }
                }
                case DROP -> drop = value;
                default -> {
                    // An unknown rule is ignored: a newer file on an older build.
                }
            }
        }
        return new DefaultEquipment(armor, stop, stopHard, materials, List.copyOf(items), weaponEnchant, armorEnchant, drop);
    }

    /** One entity's game roll as game layer rows, in a fixed order. */
    public static List<Map<String, String>> toRows(String entity, DefaultEquipment roll) {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(row(entity, ARMOR, "", "", roll.armorChance(), null));
        out.add(row(entity, ARMOR_STOP, "", "", roll.armorStop(), roll.armorStopHard()));
        roll.materials().forEach((m, w) -> out.add(row(entity, MATERIAL, "", m, w, null)));
        for (DefaultEquipment.Item i : roll.items()) {
            out.add(row(entity, ITEM, i.slot(), i.item(), i.pct(), i.pctHard()));
        }
        out.add(row(entity, ENCHANT, "weapon", "", roll.weaponEnchant(), null));
        out.add(row(entity, ENCHANT, "armor", "", roll.armorEnchant(), null));
        out.add(row(entity, DROP, "", "", roll.dropChance(), null));
        return out;
    }

    private static Map<String, String> row(String entity, String rule, String slot, String item,
                                           double value, Double hard) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put(ENTITY, entity);
        row.put(RULE, rule);
        row.put(SLOT, slot);
        row.put(ITEM_COLUMN, item);
        row.put(VALUE, number(value));
        row.put(VALUE_HARD, hard == null ? "" : number(hard));
        return row;
    }

    /** A value with at most four places. */
    public static String number(double value) {
        double r = Math.round(value * 10000.0) / 10000.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private static String cell(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null ? "" : v.strip();
    }
}
