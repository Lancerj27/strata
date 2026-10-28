package com.strata.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mob's default equipment: every value the game uses to arm a mob at spawn, as numbers Strata owns.
 *
 * @param armorChance the chance of an armor set, at full local difficulty (the game scales it by the
 *     local-difficulty factor)
 * @param armorStop the chance, before each slot after the feet, that no more pieces are added (Normal and
 *     Easy)
 * @param armorStopHard the same on Hard
 * @param materials the armor material odds: leather, gold, chainmail, iron, diamond (a weight each)
 * @param items the game's items the mob may hold or wear; the items of one slot share one draw, the rest is
 *     an empty slot
 * @param weaponEnchant the chance its main-hand item is enchanted, at full local difficulty
 * @param armorEnchant the same for each armor piece
 * @param dropChance the chance each piece drops on death
 */
public record DefaultEquipment(double armorChance, double armorStop, double armorStopHard,
                       Map<String, Double> materials, List<Item> items,
                       double weaponEnchant, double armorEnchant, double dropChance) {

    /**
     * The chance a piece in this slot is enchanted: the weapon's for the main hand, the armor's for armor,
     * none for the off hand, as the game does.
     */
    public double enchantFor(String slot) {
        return "mainhand".equals(slot) ? weaponEnchant : ARMOR_SLOTS.contains(slot) ? armorEnchant : 0;
    }

    /**
     * One piece the mob may get, with every number of its own.
     *
     * @param pct its chance in its slot's draw on Easy and Normal
     * @param pctHard the same on Hard
     * @param drop its chance to drop on death
     * @param enchant its chance to be enchanted, at full local difficulty
     * @param scaled true for an armor piece: its chances are at full local difficulty and scale down with it,
     *     as the game's do
     */
    public record Piece(String slot, String item, double pct, double pctHard, double drop, double enchant,
                        boolean scaled) {
    }

    /**
     * The roll as one piece per item: the game's items, then each armor piece of each material, from the feet
     * up.
     */
    public List<Piece> pieces() {
        List<Piece> out = new java.util.ArrayList<>();
        for (Item i : items) {
            out.add(new Piece(i.slot(), i.item(), i.pct(), i.pctHard(), dropChance, enchantFor(i.slot()), false));
        }
        for (int s = 0; armorChance > 0 && s < ARMOR_SLOTS.size(); s++) {
            String slot = ARMOR_SLOTS.get(s);
            for (String material : MATERIALS) {
                double normal = round(armorPiecePct(s, material, false, 1));
                if (normal <= 0) {
                    continue;
                }
                out.add(new Piece(slot, armorItem(material, slot), normal,
                        round(armorPiecePct(s, material, true, 1)), dropChance, armorEnchant, true));
            }
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
    /**
     * One item the roll may give.
     *
     * @param slot mainhand, offhand, head, chest, legs, or feet
     * @param pct its chance in its slot's draw (Easy and Normal)
     * @param pctHard its chance on Hard
     */
    public record Item(String slot, String item, double pct, double pctHard) {
    }

    /** The armor materials, lowest first, as the game counts them. */
    public static final List<String> MATERIALS = List.of("leather", "gold", "chainmail", "iron", "diamond");

    /** The armor slots, from the feet up: the order the game fills them in. */
    public static final List<String> ARMOR_SLOTS = List.of("feet", "legs", "chest", "head");

    /** The item id for a material's piece in a slot: "minecraft:golden_helmet". */
    public static String armorItem(String material, String slot) {
        String prefix = switch (material) {
            case "gold" -> "golden";
            default -> material;
        };
        String piece = switch (slot) {
            case "feet" -> "boots";
            case "legs" -> "leggings";
            case "chest" -> "chestplate";
            default -> "helmet";
        };
        return "minecraft:" + prefix + "_" + piece;
    }

    /** No armor roll, no items: a mob the game does not arm. */
    public static DefaultEquipment none() {
        return new DefaultEquipment(0, 25, 10, noMaterials(), List.of(), 25, 50, 8.5);
    }

    /** Material weights of zero. */
    public static Map<String, Double> noMaterials() {
        Map<String, Double> out = new LinkedHashMap<>();
        MATERIALS.forEach(m -> out.put(m, 0.0));
        return out;
    }

    /** The chance, in percent, that armor lands in this slot (0 feet.. */
    public double armorSlotPct(int slotFromFeet, boolean hard, double factor) {
        double keep = 1 - (hard ? armorStopHard : armorStop) / 100.0;
        return armorChance * factor * Math.pow(keep, slotFromFeet);
    }

    /**
     * The chance, in percent, of one armor piece: its slot's chance times its material's share of the weights
     * (a row per piece, not "random armor").
     */
    public double armorPiecePct(int slotFromFeet, String material, boolean hard, double factor) {
        double total = materials.values().stream().mapToDouble(Double::doubleValue).sum();
        return total <= 0 ? 0 : armorSlotPct(slotFromFeet, hard, factor) * materials.getOrDefault(material, 0.0) / total;
    }
}
