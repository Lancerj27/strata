package com.strata.client;

import com.strata.Strata;
import com.strata.client.screen.*;
import com.strata.client.widget.BrowseList;
import com.strata.data.JsonOverrides;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** THE UI TOUR. */
public final class UiTour {

    private UiTour() {
    }

    private record Step(String name, Supplier<Screen> open, Consumer<Screen> after) {
    }

    private static final List<String> REPORT = new ArrayList<>();
    private static final boolean FULL = Boolean.getBoolean("strata.uitour.fullscreen");
    private static int phase;
    private static int ticks;
    private static int step;
    private static List<Step> steps;

    public static void install() {
        if (!Boolean.getBoolean("strata.uitour")) {
            return;
        }
        Strata.LOGGER.info("UI tour: armed");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            try {
                tick(Minecraft.getInstance());
            } catch (Throwable t) {
                REPORT.add("TOUR FAILED at phase " + phase + " step " + step + ": " + t);
                Strata.LOGGER.error("UI tour failure", t);
                finish(Minecraft.getInstance());
            }
        });
    }

    private static void tick(Minecraft mc) {
        ticks++;
        switch (phase) {
            case 0 -> {
                if (mc.screen != null && mc.getOverlay() == null && ticks > 40) {
                    // FULL SCREEN: the monitor's own size and GUI scale, not the 854x480 window.
                    if (Integer.getInteger("strata.uitour.guiscale", -1) >= 0) {
                        com.strata.data.StrataConfig.GUI_SCALE.set(Integer.getInteger("strata.uitour.guiscale"));
                    }
                    if (FULL && !mc.getWindow().isFullscreen()) {
                        mc.getWindow().toggleFullScreen();
                    }
                    phase = 1;
                    ticks = 0;
                    String name = "strata_uitour_" + System.currentTimeMillis();
                    mc.createWorldOpenFlows().createFreshLevel(name,
                            new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(),
                                    WorldDataConfiguration.DEFAULT),
                            new WorldOptions(12345L, false, false),
                            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                    .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                            mc.screen);
                }
            }
            case 1 -> {
                if (mc.level != null && mc.player != null && ticks > 200) {
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    phase = 2;
                    ticks = 0;
                    step = 0;
                    steps = steps();
                    REPORT.add("World loaded. " + steps.size() + " screens to tour.");
                }
            }
            case 2 -> {
                if (step >= steps.size()) {
                    finish(mc);
                    return;
                }
                Step s = steps.get(step);
                if (ticks == 1) {
                    mc.setScreen(s.open().get());
                } else if (ticks == 15 && s.after() != null) {
                    s.after().accept(mc.screen);
                } else if (ticks == 40) {
                    dump(mc, s.name());
                    String file = String.format(FULL ? "uitour_full_%02d_%s.png" : "uitour_%02d_%s.png", step + 1, s.name());
                    Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), m -> { });
                } else if (ticks > 45) {
                    step++;
                    ticks = 0;
                }
            }
            default -> {
            }
        }
    }

    /** Presses the first live button whose words start with this. */
    private static Consumer<Screen> press(String words) {
        return screen -> {
            for (var child : screen.children()) {
                if (child instanceof Button b && b.getMessage().getString().startsWith(words)) {
                    b.onPress();
                    REPORT.add("  (pressed \"" + b.getMessage().getString() + "\")");
                    return;
                }
            }
            REPORT.add("  (NO BUTTON starting \"" + words + "\" to press)");
        };
    }

    private static List<Step> steps() {
        String zombieOwner = com.strata.model.Variant.defaultNameFor("minecraft:zombie");
        return List.of(
                // The menus and the header's screens.
                new Step("main_menu", ClientScreen::new, null),
                new Step("world_tiers", () -> com.strata.client.screen.SectionNav.open("World"), null),
                new Step("scaling", () -> com.strata.client.screen.SectionNav.open("Scaling"), null),
                new Step("options", StrataOptionsScreen::new, null),
                // The Server screen is gone: its status and actions are on Profiles.
                new Step("profiles", ProfileScreen::new, null),
                new Step("world_terrain", () -> com.strata.client.screen.SectionNav.open("World", "Terrain"), null),
                new Step("options_players", () -> com.strata.client.screen.SectionNav.open("Options", "Player spawn"), null),
                new Step("structures", () -> new OverridesScreen(com.strata.domain.OverrideViews.STRUCTURES), null),
                new Step("mob_tags", () -> new OverridesScreen(JsonOverrides.ENTITY_TAG), null),
                new Step("advancements", () -> new OverridesScreen(JsonOverrides.ADVANCEMENT), null),
                new Step("help", HelpScreen::new, null),
                new Step("test_mode", TestModeScreen::new, null),
                new Step("player_spawn", PlayerSpawnScreen::new, null),
                // Mobs, the gold standard, and every screen it opens.
                new Step("mobs_global", () -> new MobListScreen(null, null), null),
                new Step("mobs_plains_game_spawns", () -> new MobListScreen("biome", "minecraft:plains"),
                        press("Default mobs")),
                // The Biomes tab with every biome's natural mobs shown.
                new Step("mobs_biomes_default_mobs", () -> new MobListScreen("biome", ""), press("Default mobs")),
                new Step("mob_caps", MobCapScreen::new, null),
                new Step("special_mobs", () -> new SpecialMobsScreen("global", ""), null),
                new Step("goal_sets", GoalSetsScreen::new, null),
                new Step("replacements_plains", () -> new ReplacementsScreen("biome", "minecraft:plains",
                        "minecraft:zombie"), null),
                new Step("mob_edit_zombie", () -> new MobEditScreen(null, "minecraft:zombie", "global", "", true), null),
                new Step("mob_edit_zombie_scrolled", () -> new MobEditScreen(null, "minecraft:zombie", "global", "", true),
                        screen -> screen.mouseScrolled(200, 150, 0, -20)),
                new Step("mob_data_zombie", () -> new MobDataScreen("minecraft:zombie", "", "global", ""), null),
                new Step("mob_data_zombie_visuals", () -> new MobDataScreen("minecraft:zombie", "", "global", ""),
                        press("Visuals")),
                new Step("spawn_rules_zombie", () -> new SpawnRulesScreen("global", "", "minecraft:zombie", ""), null),
                new Step("loot_zombie", () -> new LootScreen("global", "", "minecraft:zombie", ""), null),
                new Step("loot_table_zombie", () -> new LootTableScreen("minecraft:zombie"), null),
                new Step("equipment_zombie", () -> new EquipmentScreen(zombieOwner, "minecraft:zombie", "global", ""), null),
                new Step("abilities_zombie", () -> new AbilitiesScreen(zombieOwner, "minecraft:zombie", "global", ""), null),
                new Step("trades_villager", () -> new TradesScreen("minecraft:villager", "global", ""), null),
                // Tiers and biomes.
                new Step("tiers", TierListScreen::new, null),
                new Step("tier_new", () -> new TierEditScreen(null), null),
                new Step("biomes", BiomeListScreen::new, null),
                new Step("biome_edit_plains", () -> new BiomeEditScreen("minecraft:plains"), null),
                new Step("biome_data_plains", () -> new BiomeDataScreen("minecraft:plains"), null),
                new Step("ores_plains", () -> new OreListScreen("biome", "minecraft:plains"), null),
                new Step("vegetation_plains", () -> new VegetationScreen("biome", "minecraft:plains"), null),
                // Items and blocks.
                new Step("items", ItemsScreen::new, null),
                new Step("item_stats_diamond_sword", () -> new ItemStatScreen("minecraft:diamond_sword", "Diamond Sword"),
                        null),
                new Step("item_variant_new", () -> new ItemVariantScreen(null, "minecraft:diamond_sword"), null),
                new Step("loot_tables_all", ChestsScreen::lootTables, null),
                new Step("chest_loot_dungeon", () -> ChestLootScreen.forTable("minecraft:chests/simple_dungeon",
                        "Simple Dungeon"), null),
                new Step("blocks", BlockStatsScreen::new, null),
                new Step("block_stats_stone", () -> new BlockStatScreen("minecraft:stone", "Stone"), null),
                new Step("block_variant_new", () -> new BlockVariantScreen(null, "minecraft:stone"), null),
                // Blocks > Replacements, and an item whose only recipe is a furnace's.
                new Step("block_swaps_plains", () -> new BlockSwapScreen("biome", "minecraft:plains"), null),
                new Step("item_stats_charcoal", () -> new ItemStatScreen("minecraft:charcoal", "Charcoal"), null),
                // Scaling.
                new Step("scaling_mobs", () -> new ScalingScreen(true), null),
                new Step("scaling_players", () -> new ScalingScreen(false), null),
                new Step("player_base_stats", PlayerBaseStatsScreen::new, null),
                // The override sheet: the list with the entry's editor beside it.
                new Step("recipes", () -> new OverridesScreen(JsonOverrides.RECIPE), null),
                new Step("recipe_iron_smelting", () -> OverridesScreen.entry(JsonOverrides.RECIPE,
                        "minecraft:iron_ingot_from_smelting_raw_iron"), null),
                new Step("structures", () -> new OverridesScreen(JsonOverrides.STRUCTURE), null),
                new Step("structure_swamp_hut", () -> OverridesScreen.entry(JsonOverrides.STRUCTURE,
                        "minecraft:swamp_hut"), null),
                new Step("features", () -> new OverridesScreen(JsonOverrides.PLACED_FEATURE), null),
                new Step("feature_ore_gold", () -> OverridesScreen.entry(JsonOverrides.PLACED_FEATURE,
                        "minecraft:ore_gold"), null),
                new Step("damage_types", () -> new OverridesScreen(JsonOverrides.DAMAGE_TYPE), null),
                new Step("loot_tables", () -> new OverridesScreen(JsonOverrides.LOOT_TABLE), null),
                new Step("item_data_fuel", () -> OverridesScreen.entry(JsonOverrides.ITEM_DATA,
                        "neoforge:furnace_fuels"), null),
                new Step("game_rules", GameRulesScreen::new, null));
    }

    /** A widget's tooltip text, or "". */
    private static String tip(AbstractWidget w) {
        net.minecraft.client.gui.components.Tooltip t = w.getTooltip();
        if (t == null) {
            return "";
        }
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.components.Tooltip.class.getDeclaredField("message");
            f.setAccessible(true);
            return "  TIP: " + ((net.minecraft.network.chat.Component) f.get(t)).getString().replace('\n', ' ');
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "  TIP: (unreadable)";
        }
    }

    /** Every control of the screen, and a list's rows. */
    private static void dump(Minecraft mc, String name) {
        Screen screen = mc.screen;
        REPORT.add("");
        REPORT.add("=== " + (step + 1) + " " + name + ": " + (screen == null ? "NO SCREEN" : screen.getClass().getSimpleName()
                + " \"" + screen.getTitle().getString() + "\"") + "  (gui " + mc.getWindow().getGuiScaledWidth() + "x"
                + mc.getWindow().getGuiScaledHeight() + ")");
        if (screen == null) {
            return;
        }
        for (var child : screen.children()) {
            if (child instanceof BrowseList list) {
                var rows = list.allRows();
                REPORT.add("  LIST " + rows.size() + " row(s):");
                for (int i = 0; i < Math.min(14, rows.size()); i++) {
                    var r = rows.get(i);
                    REPORT.add("    - " + r.label + (r.subtitle == null || r.subtitle.isEmpty() ? "" : "  |  " + r.subtitle));
                }
            } else if (child instanceof AbstractWidget w) {
                REPORT.add("  " + w.getClass().getSimpleName() + " \"" + w.getMessage().getString() + "\" ["
                        + w.getX() + "," + w.getY() + " " + w.getWidth() + "x" + w.getHeight() + "]"
                        + (w.active ? "" : " (grey)") + (w.visible ? "" : " (HIDDEN)") + tip(w));
            }
        }
    }

    private static void finish(Minecraft mc) {
        phase = 99;
        try {
            Files.write(Path.of(mc.gameDirectory.getPath(), FULL ? "strata-uitour-full.txt" : "strata-uitour.txt"), REPORT);
        } catch (IOException e) {
            Strata.LOGGER.error("UI tour: could not write the report", e);
        }
        mc.stop();
    }
}
