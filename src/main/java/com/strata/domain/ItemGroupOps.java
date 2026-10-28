package com.strata.domain;

import com.strata.model.ItemStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** THE ITEM LIST'S SUB-GROUPS. */
public final class ItemGroupOps {

    private ItemGroupOps() {
    }

    public static final String MISC = "Misc";

    /** Sub-groups of Other, in order, Misc last. */
    public static final List<String> OTHER_SUBS = List.of("Spawn eggs", "Dyes", "Ingots and nuggets",
            "Gems and raw materials", "Dusts and powders", "Seeds and crops", "Buckets", "Music discs",
            "Templates and sherds", "Banner patterns", "Potions and arrows", "Vehicles", MISC);

    /** Sub-groups of Blocks, in order, Other last. */
    public static final List<String> BLOCK_SUBS = List.of("Ores", "Redstone", "Colored", "Building", "Wood",
            "Plants", "Stone", "Other");

    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** The sub-groups of a kind, in order; empty for a kind that is not split. */
    public static List<String> subsOf(String kind) {
        return ItemStats.OTHER.equals(kind) ? OTHER_SUBS : ItemStats.BLOCKS.equals(kind) ? BLOCK_SUBS : List.of();
    }

    /** The sub-group an item of this kind belongs to, or "" when the kind is not split. */
    public static String sub(String kind, String id) {
        String path = path(id);
        if (ItemStats.OTHER.equals(kind)) {
            return otherSub(path);
        }
        if (ItemStats.BLOCKS.equals(kind)) {
            return blockSub(path);
        }
        return "";
    }

    private static String path(String id) {
        int colon = id.indexOf(':');
        return (colon < 0 ? id : id.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }

    private static boolean has(String path, String... parts) {
        for (String p : parts) {
            if (path.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ends(String path, String... suffixes) {
        for (String s : suffixes) {
            if (path.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    private static String otherSub(String p) {
        if (p.endsWith("_spawn_egg")) return "Spawn eggs";
        if (p.endsWith("_dye")) return "Dyes";
        if (p.endsWith("_ingot") || p.endsWith("_nugget") || p.equals("netherite_scrap")) return "Ingots and nuggets";
        if (p.startsWith("raw_") || p.endsWith("_gem") || ends(p, "diamond", "emerald", "amethyst_shard", "lapis_lazuli",
                "quartz", "coal", "charcoal", "prismarine_shard", "prismarine_crystals")) return "Gems and raw materials";
        if (p.endsWith("_dust") || ends(p, "redstone", "glowstone_dust", "gunpowder", "sugar", "blaze_powder", "bone_meal")) return "Dusts and powders";
        if (p.endsWith("_seeds") || ends(p, "wheat", "carrot", "potato", "beetroot", "melon_slice", "sweet_berries",
                "glow_berries", "cocoa_beans", "nether_wart")) return "Seeds and crops";
        if (p.endsWith("bucket")) return "Buckets";
        if (p.startsWith("music_disc_") || p.equals("disc_fragment_5")) return "Music discs";
        if (p.endsWith("_smithing_template") || p.endsWith("_pottery_sherd") || p.equals("armor_trim_smithing_template")) return "Templates and sherds";
        if (p.endsWith("_banner_pattern")) return "Banner patterns";
        if (has(p, "potion") || p.equals("arrow") || p.equals("tipped_arrow") || p.equals("spectral_arrow")) return "Potions and arrows";
        if (p.endsWith("_boat") || p.endsWith("_raft") || p.endsWith("minecart") || p.endsWith("_chest_boat")) return "Vehicles";
        return MISC;
    }

    private static boolean colored(String p) {
        for (String c : COLORS) {
            if (p.startsWith(c + "_")) {
                return true;
            }
        }
        return has(p, "terracotta") || ends(p, "glass", "glass_pane") || has(p, "stained_glass");
    }

    private static String blockSub(String p) {
        if (p.endsWith("_ore") || p.equals("ancient_debris") || p.startsWith("raw_") || p.equals("nether_gold_ore")) return "Ores";
        if (has(p, "redstone", "piston", "repeater", "comparator", "observer", "dispenser", "dropper", "hopper", "lever",
                "button", "pressure_plate", "tripwire", "rail", "lamp", "daylight_detector", "target", "note_block",
                "sculk_sensor", "sculk_shrieker", "tnt", "jukebox", "lightning_rod", "crafter")) return "Redstone";
        if (colored(p)) return "Colored";
        if (ends(p, "_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_door", "_trapdoor", "_bars", "_pane")
                || p.equals("iron_bars") || p.equals("ladder") || p.equals("scaffolding")) return "Building";
        if (has(p, "planks", "_log", "_wood", "stem", "hyphae", "bamboo_block", "bamboo_mosaic", "mangrove_roots")
                || ends(p, "_sign", "_hanging_sign", "_shelf")) return "Wood";
        if (has(p, "sapling", "leaves", "flower", "grass", "fern", "mushroom", "vine", "moss", "coral", "azalea",
                "kelp", "seagrass", "lily", "dandelion", "poppy", "orchid", "allium", "tulip", "cornflower", "lilac",
                "rose", "peony", "sunflower", "pitcher", "torchflower", "spore_blossom", "dripleaf", "cactus", "sugar_cane",
                "bamboo", "bush", "crop", "wart", "roots", "fungus", "propagule", "sprouts", "hay_block", "pumpkin", "melon")) return "Plants";
        if (has(p, "stone", "cobble", "deepslate", "andesite", "granite", "diorite", "tuff", "basalt", "brick", "sandstone",
                "obsidian", "prismarine", "quartz", "calcite", "dripstone", "netherrack", "end_stone", "purpur", "terracotta",
                "dirt", "gravel", "sand", "clay", "mud", "concrete", "blackstone", "amethyst", "copper", "iron_block",
                "gold_block", "diamond_block", "emerald_block", "lapis_block", "coal_block", "netherite_block")) return "Stone";
        return "Other";
    }

    /**
     * One section of the item list: a kind and, when the kind is split, one of its sub-groups.
     *
     * @param sub "" for a kind that is not split
     * @param indexes positions in the list given, in that list's order
     */
    public record Section(String kind, String sub, List<Integer> indexes) {
    }

    /**
     * The list's sections, in order: kinds as they come (the list is sorted by kind), then each kind's
     * sub-groups in their fixed order with only the non-empty ones, each one's items in the order given (by
     * id).
     *
     * @param kinds each item's kind, parallel to ids
     */
    public static List<Section> sections(List<String> kinds, List<String> ids) {
        Map<String, Map<String, List<Integer>>> byKind = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            String kind = kinds.get(i);
            byKind.computeIfAbsent(kind, k -> new LinkedHashMap<>())
                    .computeIfAbsent(sub(kind, ids.get(i)), s -> new ArrayList<>()).add(i);
        }
        List<Section> out = new ArrayList<>();
        byKind.forEach((kind, subs) -> {
            List<String> order = new ArrayList<>(subsOf(kind));
            subs.keySet().stream().filter(s -> !order.contains(s)).forEach(order::add);
            for (String s : order) {
                List<Integer> idx = subs.get(s);
                if (idx != null && !idx.isEmpty()) {
                    out.add(new Section(kind, s, idx));
                }
            }
        });
        return out;
    }
}
