package com.strata.ui.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** THE SECTIONS: each section's screens, in the order its Section dropdown lists them. */
public final class Sections {

    private Sections() {
    }

    public static final String WORLD = "World";
    public static final String ITEMS = "Items";
    public static final String BLOCKS = "Blocks";
    public static final String OPTIONS = "Options";
    public static final String SCALING = "Scaling";

    /** Section name to its entries, in order. */
    public static final Map<String, List<String>> ENTRIES = entries();

    private static Map<String, List<String>> entries() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put(WORLD, List.of("Tiers", "Biomes", "Mobs", "Structures", "Dimensions", "Terrain", "Features"));
        out.put(ITEMS, List.of("Items", "Loot tables", "Chest loot"));
        out.put(BLOCKS, List.of("Blocks", "Ores", "Vegetation", "Replacements"));
        out.put(OPTIONS, List.of("Settings", "World settings", "Game rules", "Damage types", "Player spawn",
                "Player base stats", "World types", "Advancements", "Chat types", "Test mode", "Custom textures & sounds", "Sounds", "Textures", "Models"));
        out.put(SCALING, List.of("Mob scaling", "Player scaling", "Formulas"));
        return java.util.Collections.unmodifiableMap(out);
    }
}
