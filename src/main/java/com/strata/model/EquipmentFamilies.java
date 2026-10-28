package com.strata.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The game's own default equipment rolls, by family, with its exact values. */
public final class EquipmentFamilies {

    private EquipmentFamilies() {
    }

    public static final String ZOMBIE = "zombie";
    public static final String DROWNED = "drowned";
    public static final String ZOMBIFIED_PIGLIN = "zombified_piglin";
    public static final String SKELETON = "skeleton";
    public static final String WITHER_SKELETON = "wither_skeleton";
    public static final String PIGLIN = "piglin";
    public static final String PIGLIN_BRUTE = "piglin_brute";
    public static final String PILLAGER = "pillager";
    public static final String VINDICATOR = "vindicator";
    public static final String VEX = "vex";
    public static final String FOX = "fox";

    /** The game's armor material odds: nextInt(2) plus three 9.5% steps. */
    public static Map<String, Double> vanillaMaterials() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int t = 0; t < DefaultEquipment.MATERIALS.size(); t++) {
            out.put(DefaultEquipment.MATERIALS.get(t), Math.round(VanillaEquipmentMath.materialPct(t) * 10000) / 10000.0);
        }
        return out;
    }

    private static DefaultEquipment.Item item(String slot, String item, double pct, double pctHard) {
        return new DefaultEquipment.Item(slot, "minecraft:" + item, pct, pctHard);
    }

    private static DefaultEquipment.Item always(String slot, String item) {
        return item(slot, item, 100, 100);
    }

    private static DefaultEquipment roll(boolean armor, double weaponEnchant, double armorEnchant, double drop,
                                 DefaultEquipment.Item... items) {
        return new DefaultEquipment(armor ? 15 : 0, 25, 10, armor ? vanillaMaterials() : DefaultEquipment.noMaterials(),
                List.of(items), weaponEnchant, armorEnchant, drop);
    }

    /** The game's roll for a family, or null for a family it does not know. */
    public static DefaultEquipment of(String family) {
        return switch (family == null ? "" : family) {
            // Zombie#populateDefaultEquipmentSlots: the armor roll, then a weapon 1% (Hard 5%), a sword 1 in
            // 3, a shovel 2 in 3.
            case ZOMBIE -> roll(true, 25, 50, 8.5,
                    item("mainhand", "iron_sword", 0.3333, 1.6667),
                    item("mainhand", "iron_shovel", 0.6667, 3.3333));
            // Drowned: no armor roll; over 0.9: a trident 10 in 16, a fishing rod 6 in 16; a nautilus shell
            // in the off hand 3%.
            case DROWNED -> roll(false, 25, 50, 8.5,
                    item("mainhand", "trident", 6.25, 6.25),
                    item("mainhand", "fishing_rod", 3.75, 3.75),
                    item("offhand", "nautilus_shell", 3, 3));
            case ZOMBIFIED_PIGLIN -> roll(false, 25, 50, 8.5, always("mainhand", "golden_sword"));
            // AbstractSkeleton: the armor roll, then a bow.
            case SKELETON -> roll(true, 25, 50, 8.5, always("mainhand", "bow"));
            // WitherSkeleton: a stone sword, and no enchanting at all.
            case WITHER_SKELETON -> roll(false, 0, 0, 8.5, always("mainhand", "stone_sword"));
            // Piglin, adults: a crossbow or a golden sword, half each; each golden armor piece 10%.
            case PIGLIN -> roll(false, 25, 50, 8.5,
                    item("mainhand", "golden_sword", 50, 50),
                    item("mainhand", "crossbow", 50, 50),
                    item("head", "golden_helmet", 10, 10),
                    item("chest", "golden_chestplate", 10, 10),
                    item("legs", "golden_leggings", 10, 10),
                    item("feet", "golden_boots", 10, 10));
            case PIGLIN_BRUTE -> roll(false, 25, 50, 8.5, always("mainhand", "golden_axe"));
            case PILLAGER -> roll(false, 25, 50, 8.5, always("mainhand", "crossbow"));
            case VINDICATOR -> roll(false, 25, 50, 8.5, always("mainhand", "iron_axe"));
            // Vex: an iron sword that never drops.
            case VEX -> roll(false, 25, 50, 0, always("mainhand", "iron_sword"));
            // Fox: 20% holds something in its mouth, by the game's split.
            case FOX -> roll(false, 0, 0, 8.5,
                    item("mainhand", "emerald", 1, 1),
                    item("mainhand", "egg", 3, 3),
                    item("mainhand", "rabbit_foot", 2, 2),
                    item("mainhand", "rabbit_hide", 2, 2),
                    item("mainhand", "wheat", 4, 4),
                    item("mainhand", "leather", 4, 4),
                    item("mainhand", "feather", 4, 4));
            default -> null;
        };
    }

    /** Every family, for the gate. */
    public static final List<String> ALL = List.of(ZOMBIE, DROWNED, ZOMBIFIED_PIGLIN, SKELETON, WITHER_SKELETON,
            PIGLIN, PIGLIN_BRUTE, PILLAGER, VINDICATOR, VEX, FOX);
}
