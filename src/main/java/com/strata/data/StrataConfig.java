package com.strata.data;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Global feature toggles in strata-common.toml. */
public final class StrataConfig {

    private StrataConfig() {}

    /** True once strata-common.toml has been parsed at least once. */
    private static volatile boolean ready;

    /**
     * The worldgen ore modifier runs BEFORE NeoForge loads the mod config on an integrated server start, and
     * ModConfigSpec values cannot be read before load.
     */
    public static boolean isLoaded() {
        return ready;
    }

    /**
     * The enableOreRestriction toggle, safe to read before config load: read from the file before NeoForge
     * loads it (#read).
     */
    public static boolean oreRestrictionEnabled() {
        return read(ENABLE_ORE_RESTRICTION);
    }

    /** Latches {@link #ready} once this mod's config has actually loaded or reloaded. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            ready = true;
        }
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_MOB_SCALING;
    public static final ModConfigSpec.BooleanValue ENABLE_PLAYER_SCALING;
    public static final ModConfigSpec.BooleanValue ENABLE_ORE_RESTRICTION;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_SUBSTITUTION;
    public static final ModConfigSpec.BooleanValue ENABLE_LOOT;
    public static final ModConfigSpec.BooleanValue ENABLE_SPECIAL_SPAWNERS;
    public static final ModConfigSpec.BooleanValue ENABLE_TRADES;
    public static final ModConfigSpec.BooleanValue ENABLE_EXTRA_MOB_ATTRIBUTES;
    /**
     * A Replace biome blocks the spawns that do not come from its spawn list: a structure's, a patrol's, a
     * new chunk's.
     */
    public static final ModConfigSpec.BooleanValue BLOCK_OTHER_SPAWNS;
    /**
     * A Replace biome's hostile mobs also fill the spawn slots its list leaves empty
     * (runtime.SpawnsModifier), one switch per kind of slot: the passive animals' (creature), the bats'
     * (ambient), and every water slot (water creatures, water ambient, underground water, axolotls).
     */
    public static final ModConfigSpec.BooleanValue MONSTERS_IN_CREATURE_SLOTS;
    public static final ModConfigSpec.BooleanValue MONSTERS_IN_AMBIENT_SLOTS;
    public static final ModConfigSpec.BooleanValue MONSTERS_IN_WATER_SLOTS;
    /**
     * A Replace biome's hostile mobs spawn in any light, not only in the dark (runtime.ReplaceSpawnRules).
     */
    public static final ModConfigSpec.BooleanValue MONSTERS_OUTSIDE_DARKNESS;

    /**
     * A switch's value. Before NeoForge loads the file (the attribute event, a world's first biome modifier)
     * it is read from the file itself, so a switch Off is Off from the first event; the default without one.
     */
    public static boolean read(ModConfigSpec.BooleanValue value) {
        if (ready) {
            return value.get();
        }
        java.nio.file.Path file = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve(ProfileManager.COMMON_CONFIG_NAME);
        if (java.nio.file.Files.isRegularFile(file)) {
            try (var cfg = com.electronwill.nightconfig.core.file.CommentedFileConfig.of(file)) {
                cfg.load();
                Object v = cfg.get(value.getPath());
                if (v instanceof Boolean b) {
                    return b;
                }
            } catch (RuntimeException e) {
                com.strata.Strata.LOGGER.warn("Could not read {} early: {}", file, e.toString());
            }
        }
        return value.getDefault();
    }

    public static double read(ModConfigSpec.DoubleValue value) {
        return ready ? value.get() : value.getDefault();
    }

    public static final ModConfigSpec.DoubleValue MOB_XP_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue MOB_XP_EXPONENT;
    public static final ModConfigSpec.DoubleValue CHUNK_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLE_CHUNK_SCALING;

    // the tier/biome editor has no title-screen button any more.
    public static final ModConfigSpec.EnumValue<EditorKey> EDITOR_KEY;

    /**
     * "Server textures & sounds": what this game does when a server offers its custom textures and sounds.
     */
    public static final ModConfigSpec.EnumValue<ServerAssets> SERVER_ASSETS;

    public enum ServerAssets {
        ASK("Ask"), ALWAYS("Always"), NEVER("Never");

        private final String word;

        ServerAssets(String word) {
            this.word = word;
        }

        public String word() {
            return word;
        }

        public static ServerAssets ofWord(String word) {
            for (ServerAssets v : values()) {
                if (v.word.equalsIgnoreCase(word) || v.name().equalsIgnoreCase(word)) {
                    return v;
                }
            }
            return ASK;
        }
    }

    /** Which key opens the tier/biome config editor. */
    public enum EditorKey {
        NONE(0), K(75), P(80), O(79);

        private final int glfwCode;

        EditorKey(int glfwCode) {
            this.glfwCode = glfwCode;
        }

        /** The GLFW key code this option maps to (0 = disabled). */
        public int glfwCode() {
            return glfwCode;
        }
    }

    // THE DEBUG HUD.
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_HUD;
    /** The editors list values the game fixes and Strata cannot change ("locked") only when this is on. */
    public static final ModConfigSpec.BooleanValue VIEW_LOCKED_VALUES;
    public static final ModConfigSpec.BooleanValue SHOW_DUPLICATE_EDITORS;
    /**
     * The size of Strata's screens, as Minecraft's GUI Scale: 0 is Strata's Auto (Grid#autoGuiScale), -1 the
     * game's own GUI Scale, 1 and up the game's sizes.
     */
    public static final ModConfigSpec.IntValue GUI_SCALE;
    /**
     * Debug: where the world spawn is moved to (runtime.PlayerSpawnBiome): "biome:" and a biome id, "tier:"
     * and a tier name (the nearest of its biomes), or "" for the game's spawn.
     */
    public static final ModConfigSpec.ConfigValue<String> DEBUG_PLAYER_SPAWN;
    public static final ModConfigSpec.IntValue PLAYER_SPAWN_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                        "Feature switches. Each turns off one part of Strata completely.")
                .push("systems");

        ENABLE_MOB_SCALING = builder
                .comment("Apply level-based attribute scaling to mobs.")
                .define("enableMobScaling", true);

        ENABLE_PLAYER_SCALING = builder
                .comment("Grow player attributes with XP level.",
                        "Off by default: the player curves ship as rows, so On alone changes the game.")
                .define("enablePlayerScaling", false);

        ENABLE_ORE_RESTRICTION = builder
                .comment("Let tiers and biomes restrict which ores generate.")
                .define("enableOreRestriction", true);

        ENABLE_SPAWN_SUBSTITUTION = builder
                .comment("Let tiers and biomes change which mobs spawn.")
                .define("enableSpawnSubstitution", true);

        ENABLE_LOOT = builder
                .comment("Apply loot table overrides from the loot.csv.")
                .define("enableLoot", true);

        // THE THREE SWITCHES THAT CHANGE THE GAME WITH NO ROWS ship Off, so the defaults are the game's own.
        // A Special spawns or Trades edit turns its switch On (OptionsOps#turnOn).
        ENABLE_SPECIAL_SPAWNERS = builder
                .comment("Run the game's phantom, patrol, cat, siege and wandering trader spawners through Strata.",
                        "Off: the game's own spawners, untouched (Special spawns edits do nothing). From the next world load.")
                .define("enableSpecialSpawners", false);

        ENABLE_TRADES = builder
                .comment("Serve villager and wandering trader trades through Strata.",
                        "Off: the game's own trade lists, untouched (Trades edits do nothing). At the next launch.")
                .define("enableTrades", false);

        ENABLE_EXTRA_MOB_ATTRIBUTES = builder
                .comment("Give every mob attack damage and flying speed, for Strata's goals and the Flying flag.",
                        "Off: each mob has only the game's own attributes (those goals and Flying wait). At the next launch.")
                .define("enableExtraMobAttributes", false);

        BLOCK_OTHER_SPAWNS = builder
                .comment("In a Replace tier's biomes, block every spawn of a mob not on its list:",
                        "a structure's (fortress, village, hut), a patrol's, a new chunk's.",
                        "A tier or a biome can set this for itself.")
                .define("blockOtherSpawns", true);

        MONSTERS_IN_CREATURE_SLOTS = builder
                .comment("In a Replace tier's biomes, the monsters on its list also take the creatures'",
                        "spawn slots (and new chunks') when its list leaves them empty. From the next world load.")
                .define("monstersInCreatureSlots", true);

        MONSTERS_IN_AMBIENT_SLOTS = builder
                .comment("In a Replace tier's biomes, the monsters on its list also take the ambient (bat)",
                        "spawn slots when its list leaves them empty. From the next world load.")
                .define("monstersInAmbientSlots", true);

        MONSTERS_IN_WATER_SLOTS = builder
                .comment("In a Replace tier's biomes, the monsters on its list also take the water spawn slots",
                        "(fish, squid, underground water, axolotls) when its list leaves them empty. From the next world load.")
                .define("monstersInWaterSlots", true);

        MONSTERS_OUTSIDE_DARKNESS = builder
                .comment("In a Replace tier's biomes, the monsters on its list spawn in any light,",
                        "not only in the dark. They still need ground to stand on.")
                .define("monstersOutsideDarkness", true);

        builder.pop();

        builder.comment("Scaling globals.")
                .push("scaling");

        MOB_XP_PER_LEVEL = builder
                .comment("Extra XP a leveled mob drops, as a fraction per level.")
                .defineInRange("mobXpPerLevel", 5.0, 0.0, 100.0);

        MOB_XP_EXPONENT = builder
                .comment("Curve shape for the XP bonus. Below 1 diminishes.")
                .defineInRange("mobXpExponent", 0.8, 0.1, 10.0);

        ENABLE_CHUNK_SCALING = builder
                .comment("Chunk scaling: a harder chunk raises the level a mob spawns at (Scaling > Formulas, area_level).")
                .define("enableChunkScaling", false);

        CHUNK_SCALE = builder
                .comment("How much local difficulty compounds area level: area level = level * (1 + chunkScale * localDifficulty).")
                .defineInRange("chunkScale", 0.13, 0.0, 2.0);

        builder.pop();

        builder.comment("Debug.")
                .push("hud");

        ENABLE_DEBUG_HUD = builder
                .comment("Debug: show the Strata HUD (biome, tier, area level) in the top-left corner.")
                .define("enableDebugHud", false);

        VIEW_LOCKED_VALUES = builder
                .comment("The editors also list the values the game fixes and Strata cannot change (locked).")
                .define("viewLockedValues", false);
        // "the game should ideally only have one source to edit things".
        SHOW_DUPLICATE_EDITORS = builder
                .comment("Lists also show what another screen edits (a mob's or a block's loot table in Items > Loot",
                        "tables). Off: each thing is listed where it is edited, once.")
                .define("showDuplicateEditors", false);

        GUI_SCALE = builder
                .comment("The size of Strata's screens, as Minecraft's GUI Scale. 0 is Auto: the largest that",
                        "leaves 800x400 of room (2 on a 1920x1080 screen). -1 is the game's own GUI Scale.",
                        "1 is the smallest. A size too large for the window is cut to the largest that fits.")
                .defineInRange("guiScale", 0, -1, 8);

        DEBUG_PLAYER_SPAWN = builder
                .comment("Debug: where the world spawn is moved to: biome:minecraft:plains, tier:<a tier's name>"
                        + " (the nearest of its biomes), or blank for the game's spawn. Options > Player spawn.")
                .define("debugPlayerSpawn", "");

        // 6400 is /locate biome's radius, and the most the server thread can search without the watchdog.
        PLAYER_SPAWN_RADIUS = builder
                .comment("How far, in blocks, the player spawn search looks for its biome (as /locate biome).",
                        "Farther finds rarer biomes and takes longer. Options > Player spawn.")
                .defineInRange("playerSpawnRadius", 6400, 256, 6400);

        builder.pop();

        builder.comment("Editor.")
                .push("editor");

        EDITOR_KEY = builder
                .comment("Key that opens the tier/biome config editor",
                        "(K, P or O; NONE disables). Also reachable in-game",
                        "with /strata ui.")
                .defineEnum("editorKey", EditorKey.NONE);

        SERVER_ASSETS = builder
                .comment("What this game does when a server offers its custom textures and sounds (client side):",
                        "ASK shows a question with the server, how many files and their size; ALWAYS takes them;",
                        "NEVER takes none. Every file is checked by this game whatever the server sends.")
                .defineEnum("serverAssets", ServerAssets.ASK);

        builder.pop();

        SPEC = builder.build();
    }
}
