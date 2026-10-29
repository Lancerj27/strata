package com.strata.client.screen;

import com.strata.data.JsonOverrides;
import com.strata.domain.OverrideViews;
import com.strata.ui.screen.Sections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Opens the screens of com.strata.ui.screen.Sections. */
public final class SectionNav {

    private SectionNav() {
    }

    private static final Map<String, Supplier<StrataScreen>> OPENS = opens();

    private static Map<String, Supplier<StrataScreen>> opens() {
        Map<String, Supplier<StrataScreen>> m = new HashMap<>();
        m.put(key(Sections.WORLD, "Tiers"), TierListScreen::new);
        m.put(key(Sections.WORLD, "Biomes"), BiomeListScreen::new);
        m.put(key(Sections.WORLD, "Mobs"), () -> new MobListScreen(null, null));
        m.put(key(Sections.WORLD, "Structures"), () -> new OverridesScreen(OverrideViews.STRUCTURES));
        m.put(key(Sections.WORLD, "Dimensions"), () -> new OverridesScreen(OverrideViews.DIMENSIONS));
        m.put(key(Sections.WORLD, "Terrain"), () -> new OverridesScreen(OverrideViews.TERRAIN));
        m.put(key(Sections.WORLD, "Features"), () -> new OverridesScreen(OverrideViews.FEATURES));
        m.put(key(Sections.WORLD, "World level"), WorldLevelScreen::new);
        m.put(key(Sections.OPTIONS, "World types"), () -> new OverridesScreen(OverrideViews.WORLD_TYPES));
        m.put(key(Sections.ITEMS, "Items"), ItemsScreen::new);
        m.put(key(Sections.ITEMS, "Loot tables"), ChestsScreen::lootTables);
        m.put(key(Sections.ITEMS, "Chest loot"), ChestsScreen::chestLoot);
        m.put(key(Sections.BLOCKS, "Blocks"), BlockStatsScreen::new);
        m.put(key(Sections.BLOCKS, "Ores"), () -> new OreListScreen("global", ""));
        m.put(key(Sections.BLOCKS, "Vegetation"), () -> new VegetationScreen("global", ""));
        m.put(key(Sections.BLOCKS, "Replacements"), () -> new BlockSwapScreen("global", ""));
        m.put(key(Sections.OPTIONS, "Settings"), StrataOptionsScreen::new);
        m.put(key(Sections.OPTIONS, "World settings"), WorldSettingsScreen::new);
        m.put(key(Sections.OPTIONS, "Game rules"), GameRulesScreen::new);
        m.put(key(Sections.OPTIONS, "Damage types"), () -> new OverridesScreen(JsonOverrides.DAMAGE_TYPE));
        m.put(key(Sections.OPTIONS, "Player spawn"), PlayerSpawnScreen::new);
        m.put(key(Sections.OPTIONS, "Player base stats"), PlayerBaseStatsScreen::new);
        m.put(key(Sections.OPTIONS, "Advancements"), () -> new OverridesScreen(JsonOverrides.ADVANCEMENT));
        m.put(key(Sections.OPTIONS, "Chat types"), () -> new OverridesScreen(JsonOverrides.CHAT_TYPE));
        m.put(key(Sections.OPTIONS, "Test mode"), TestModeScreen::new);
        m.put(key(Sections.OPTIONS, "Custom textures & sounds"), CustomAssetsScreen::new);
        m.put(key(Sections.OPTIONS, "Sounds"), () -> new AssetMasterScreen(com.strata.domain.CustomAssetsOps.SOUND));
        m.put(key(Sections.OPTIONS, "Textures"), () -> new AssetMasterScreen(com.strata.domain.CustomAssetsOps.TEXTURE));
        m.put(key(Sections.OPTIONS, "Models"), () -> new AssetMasterScreen(com.strata.domain.CustomAssetsOps.MODEL));
        m.put(key(Sections.SCALING, "Mob scaling"), () -> new ScalingScreen(true));
        m.put(key(Sections.SCALING, "Player scaling"), () -> new ScalingScreen(false));
        m.put(key(Sections.SCALING, "Formulas"), FormulasScreen::new);
        // Every entry opens a screen, and nothing opens that is not an entry: a miss fails at start-up.
        Sections.ENTRIES.forEach((s, list) -> list.forEach(e -> {
            if (!m.containsKey(key(s, e))) {
                throw new IllegalStateException("Strata: section entry with no screen: " + key(s, e));
            }
        }));
        if (m.size() != Sections.ENTRIES.values().stream().mapToInt(java.util.List::size).sum()) {
            throw new IllegalStateException("Strata: a section screen is not in Sections.ENTRIES");
        }
        return m;
    }

    private static String key(String section, String entry) {
        return section + "|" + entry;
    }

    /** The entry's screen, which shows the Section dropdown. */
    public static StrataScreen open(String section, String entry) {
        StrataScreen screen = OPENS.get(key(section, entry)).get();
        screen.inSection(section, entry);
        return screen;
    }

    /** The section's first entry. */
    public static StrataScreen open(String section) {
        return open(section, Sections.ENTRIES.get(section).getFirst());
    }
}
