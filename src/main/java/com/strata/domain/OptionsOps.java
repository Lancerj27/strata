package com.strata.domain;

import com.strata.Strata;
import com.strata.data.StrataConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Strata's own settings, the ones in config/strata-common.toml, for the editor. */
public final class OptionsOps {

    private OptionsOps() {
    }

    /** What a setting holds. */
    public enum Kind {
        /** On or off. */
        SWITCH,
        /** A number within a range. */
        NUMBER,
        /** One of a few names. */
        CHOICE,
        /** Where players spawn: "biome:" and an id, "tier:" and a name, or blank. */
        SPAWN
    }

    /**
     * One setting as the editor shows it.
     *
     * @param key the name in strata-common.toml
     * @param applies when a change takes effect, in words
     * @param value the value now, as the editor shows it ("On", "5.0", "K")
     * @param choices a CHOICE's names; empty for the others
     */
    public record Setting(String key, String label, String help, String applies, Kind kind, String value,
                          String defaultValue, double min, double max, List<String> choices) {

        /** True when the setting holds its shipped value. */
        public boolean isDefault() {
            return value.equals(defaultValue);
        }
    }

    public static final String MOB_SCALING = "enableMobScaling";
    public static final String PLAYER_SCALING = "enablePlayerScaling";
    /** The debug HUD, listed in Options (top right). */
    public static final String DEBUG_HUD = "enableDebugHud";

    /** The debug settings. */
    private static List<Setting> debugDefs() {
        List<Setting> out = new ArrayList<>();
        out.add(new Setting(GUI_SCALE, "GUI scale",
                "The size of Strata's screens, as Minecraft's GUI Scale. Auto is the largest that leaves"
                        + " 800x400 of room (2 on a 1920x1080 screen); Game is Minecraft's own GUI Scale; 1 is the"
                        + " smallest. A size too large for the window is cut to the largest that fits.",
                "At once.", Kind.CHOICE, guiScaleWord(guiScale()), "Auto", 0, 0, GUI_SCALE_CHOICES));
        out.add(toggle(VIEW_LOCKED, "Show locked values",
                "The editors also list the values the game fixes and Strata cannot change. Off hides them,"
                        + " so each list holds only what you can edit.", "At once.", StrataConfig.VIEW_LOCKED_VALUES));
        out.add(toggle(SHOW_DUPLICATES, "Show duplicate editors",
                "Lists also show what another screen edits: Items > Loot tables lists the mobs' and blocks' tables"
                        + " too. Off: each thing is listed where it is edited, once.", "At once.",
                StrataConfig.SHOW_DUPLICATE_EDITORS));
        out.add(toggle(DEBUG_HUD, "Debug HUD",
                "Shows the biome you stand in, its tier, its area level and coefficient, and your XP level,"
                        + " in the top-left corner. In a server world it reads this machine's config.",
                "At once.", StrataConfig.ENABLE_DEBUG_HUD));
        // THE PLAYER SPAWN: set on its own screen, a biome or a tier picked from lists.
        out.add(new Setting(DEBUG_PLAYER_SPAWN, "Player spawn",
                "Where the world spawn is moved: a biome, or a tier (the nearest of its biomes).",
                "At once in an open world, and when any world starts.", Kind.SPAWN,
                StrataConfig.DEBUG_PLAYER_SPAWN.get(), StrataConfig.DEBUG_PLAYER_SPAWN.getDefault(), 0, 0, List.of()));
        out.add(new Setting(PLAYER_SPAWN_RADIUS, "Search radius (blocks)",
                "How far the player spawn search looks for its biome. Farther finds rarer biomes and takes longer.",
                "When the spawn next moves.", Kind.NUMBER, String.valueOf(StrataConfig.PLAYER_SPAWN_RADIUS.get()),
                String.valueOf(StrataConfig.PLAYER_SPAWN_RADIUS.getDefault()), 256, 6400, List.of()));
        return out;
    }

    public static final String MOB_XP_PER_LEVEL = "mobXpPerLevel";
    public static final String MOB_XP_EXPONENT = "mobXpExponent";
    public static final String CHUNK_SCALE = "chunkScale";
    public static final String CHUNK_SCALING = "enableChunkScaling";

    /** ONE SCREEN PER SETTING. */
    /** Replace blocks the spawns not from its list; a tier or biome can set its own. */
    public static final String BLOCK_OTHER_SPAWNS = "blockOtherSpawns";
    /** A Replace biome's hostile mobs take its empty spawn slots, per kind. */
    public static final String MONSTERS_IN_CREATURE_SLOTS = "monstersInCreatureSlots";
    public static final String MONSTERS_IN_AMBIENT_SLOTS = "monstersInAmbientSlots";
    public static final String MONSTERS_IN_WATER_SLOTS = "monstersInWaterSlots";
    /** A Replace biome's hostile mobs spawn in any light. */
    public static final String MONSTERS_OUTSIDE_DARKNESS = "monstersOutsideDarkness";
    /** How the editors behave; rows of Options since (Configuration screen folded in). */
    public static final String VIEW_LOCKED = "viewLockedValues";
    /** The size of Strata's screens, as Minecraft's GUI Scale. */
    public static final String GUI_SCALE = "guiScale";
    /** "Server textures & sounds": Ask, Always or Never. */
    public static final String SERVER_ASSETS = "serverAssets";
    public static final List<String> SERVER_ASSETS_CHOICES = List.of("Ask", "Always", "Never");
    /** Lists show what another screen edits too. */
    public static final String SHOW_DUPLICATES = "showDuplicateEditors";
    // Replace blocks other spawns and the four monster rules are a tier's or biome's Rules: no longer listed
    // here.
    /** The switches that change the game with no rows: they ship Off, and an edit that needs one turns it On. */
    public static final String SPECIAL_SPAWNERS = "enableSpecialSpawners";
    public static final String TRADES = "enableTrades";
    public static final String EXTRA_MOB_ATTRIBUTES = "enableExtraMobAttributes";
    public static final List<String> OPTION_KEYS = List.of("enableSpawnSubstitution",
            "enableOreRestriction", "enableLoot", SPECIAL_SPAWNERS, TRADES, EXTRA_MOB_ATTRIBUTES,
            "editorKey", SERVER_ASSETS, GUI_SCALE, VIEW_LOCKED, SHOW_DUPLICATES, DEBUG_HUD);
    /** The GUI scale's choices, as the game words them: Auto, then the sizes. */
    public static final List<String> GUI_SCALE_CHOICES = List.of("Auto", "Game", "1", "2", "3", "4", "5", "6");

    /** The stored GUI scale in words: "Auto" for 0. */
    public static String guiScaleWord(int stored) {
        return stored == 0 ? "Auto" : stored < 0 ? "Game" : String.valueOf(stored);
    }

    /** The GUI scale words as stored: 0 for Auto. */
    public static int guiScaleOf(String word) {
        if (word == null || word.isBlank() || word.equalsIgnoreCase("Auto")) {
            return 0;
        }
        return word.equalsIgnoreCase("Game") ? -1 : Integer.parseInt(word.strip());
    }

    /** The GUI scale Strata's screens use, 0 for the game's own. */
    public static int guiScale() {
        try {
            return StrataConfig.GUI_SCALE.get();
        } catch (RuntimeException e) {
            return 0;
        }
    }
    /**
     * Turns a switch On for an edit that needs it (a Special spawns or Trades row), as one step of that edit:
     * its Undo puts the switch back too.
     */
    public static void turnOn(String key, ModConfigSpec.BooleanValue value) {
        if (value.get()) {
            return;
        }
        com.strata.data.EditHistory.record(new SettingStep(key, onOff(false), "On"));
        save(value, true);
    }

    public static final List<String> SCALING_KEYS = List.of(MOB_SCALING, PLAYER_SCALING, MOB_XP_PER_LEVEL,
            MOB_XP_EXPONENT, CHUNK_SCALE, CHUNK_SCALING);
    /** Where players spawn: its own screen, Options > Player spawn. */
    public static final String DEBUG_PLAYER_SPAWN = "debugPlayerSpawn";
    /** How far the player spawn search goes. */
    public static final String PLAYER_SPAWN_RADIUS = "playerSpawnRadius";
    public static final List<String> SPAWN_KEYS = List.of(DEBUG_PLAYER_SPAWN, PLAYER_SPAWN_RADIUS);

    /** The Scaling screen's settings: its two switches and the mob XP curve. */
    private static List<Setting> scalingDefs() {
        List<Setting> out = new ArrayList<>();
        out.add(toggle(MOB_SCALING, "Mob scaling", "Mobs grow with the area's level, by the mob curves in Scaling.",
                "At once for new mobs; loaded mobs at the next save or reload.", StrataConfig.ENABLE_MOB_SCALING));
        out.add(toggle(PLAYER_SCALING, "Player scaling",
                "Players grow with their XP level, by the player curves in Scaling.",
                "At once for players in the world.", StrataConfig.ENABLE_PLAYER_SCALING));
        out.add(number(MOB_XP_PER_LEVEL, "Mob XP per level",
                "Extra XP a leveled mob drops: 1 + this x level ^ exponent times the game's XP.",
                "At once, for the next kill.", StrataConfig.MOB_XP_PER_LEVEL, 0.0, 100.0));
        out.add(number(MOB_XP_EXPONENT, "Mob XP exponent", "The XP curve's shape. Below 1 it flattens.",
                "At once, for the next kill.", StrataConfig.MOB_XP_EXPONENT, 0.1, 10.0));
        out.add(toggle(CHUNK_SCALING, "Chunk scaling",
                "A harder chunk raises the level a mob spawns at, by Chunk scale and the area level formula.",
                "At once, for new mobs.", StrataConfig.ENABLE_CHUNK_SCALING));
        out.add(number(CHUNK_SCALE, "Chunk scale",
                "How much local difficulty compounds area level: area level = level * (1 + chunkScale * localDifficulty). 0 = off.",
                "At once.", StrataConfig.CHUNK_SCALE, 0.0, 2.0));
        return out;
    }

    /**
     * The settings each screen lists, in its key list's order: the key lists decide, so a setting cannot show
     * on two screens (DataFlow OP2).
     */
    public static List<Setting> all() {
        return pick(OPTION_KEYS);
    }

    /** True when lists also show what another screen edits (Options > Show duplicate editors). */
    public static boolean showDuplicates() {
        try {
            return StrataConfig.read(StrataConfig.SHOW_DUPLICATE_EDITORS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the editors list locked values too (Options > Show locked values). */
    public static boolean viewLocked() {
        try {
            return StrataConfig.read(StrataConfig.VIEW_LOCKED_VALUES);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The Scaling screen's: its switches and the mob XP curve. */
    public static List<Setting> scaling() {
        return pick(SCALING_KEYS);
    }

    private static List<Setting> pick(List<String> keys) {
        java.util.Map<String, Setting> byKey = new java.util.LinkedHashMap<>();
        for (Setting s : optionDefs()) byKey.put(s.key(), s);
        for (Setting s : scalingDefs()) byKey.put(s.key(), s);
        for (Setting s : debugDefs()) byKey.put(s.key(), s);
        return keys.stream().map(byKey::get).filter(java.util.Objects::nonNull).toList();
    }

    /** The global settings, as written. */
    private static List<Setting> optionDefs() {
        List<Setting> out = new ArrayList<>();
        out.add(toggle("enableSpawnSubstitution", "Spawn substitution",
                "Tiers and biomes change which mobs spawn.", "At once, for new spawns.",
                StrataConfig.ENABLE_SPAWN_SUBSTITUTION));
        out.add(toggle(BLOCK_OTHER_SPAWNS, "Replace blocks other spawns",
                "In a Replace tier's biomes, only mobs on its list spawn: no structure mobs (fortress, village,"
                        + " hut), patrols, or new-chunk mobs. A tier or biome can set its own.",
                "At once, for new spawns.", StrataConfig.BLOCK_OTHER_SPAWNS));
        // ONE SWITCH PER KIND OF SLOT.
        out.add(toggle(MONSTERS_IN_CREATURE_SLOTS, "Monsters take creature slots",
                "In a Replace tier's biomes, when its list has no creature, the monsters on it also"
                        + " spawn in the animals' slots (sheep, cows), new chunks too.",
                "From the next world load.", StrataConfig.MONSTERS_IN_CREATURE_SLOTS));
        out.add(toggle(MONSTERS_IN_AMBIENT_SLOTS, "Monsters take ambient slots",
                "In a Replace tier's biomes, when its list has no ambient mob, the monsters on it also spawn"
                        + " in the bats' slots.",
                "From the next world load.", StrataConfig.MONSTERS_IN_AMBIENT_SLOTS));
        out.add(toggle(MONSTERS_IN_WATER_SLOTS, "Monsters take water slots",
                "In a Replace tier's biomes, when its list has no water mob, the monsters on it also spawn in"
                        + " the water slots: fish, squid, underground water, axolotls.",
                "From the next world load.", StrataConfig.MONSTERS_IN_WATER_SLOTS));
        out.add(toggle(MONSTERS_OUTSIDE_DARKNESS, "Monsters spawn outside of pure darkness",
                "In a Replace tier's biomes, the monsters on its list spawn in any light, not only in the"
                        + " dark. They still need ground to stand on, and burn in daylight unless sun immune.",
                "At once, for new spawns.", StrataConfig.MONSTERS_OUTSIDE_DARKNESS));
        out.add(toggle("enableOreRestriction", "Ore rules", "Tiers and biomes restrict which ores generate.",
                "From the next world load, in new chunks.", StrataConfig.ENABLE_ORE_RESTRICTION));
        out.add(toggle("enableLoot", "Loot changes", "Your loot entries and denies apply.",
                "At once, for the next drop.", StrataConfig.ENABLE_LOOT));
        out.add(toggle(SPECIAL_SPAWNERS, "Special spawners",
                "Phantoms, patrols, cats, sieges and wandering traders spawn through Strata. Off: the game's own"
                        + " spawners, untouched, and Special spawns edits do nothing. A Special spawns edit turns it On.",
                "From the next world load.", StrataConfig.ENABLE_SPECIAL_SPAWNERS));
        out.add(toggle(TRADES, "Trade changes",
                "Villager and wandering trader trades go through Strata. Off: the game's own trade lists,"
                        + " untouched, and Trades edits do nothing. A Trades edit turns it On.",
                "At the next launch.", StrataConfig.ENABLE_TRADES));
        out.add(toggle(EXTRA_MOB_ATTRIBUTES, "Extra mob attributes",
                "Every mob gets attack damage and flying speed, so Strata's attack goals and the Flying flag"
                        + " work on any mob. Off: each mob has only the game's own attributes.",
                "At the next launch.", StrataConfig.ENABLE_EXTRA_MOB_ATTRIBUTES));
        List<String> keys = Arrays.stream(StrataConfig.EditorKey.values()).map(Enum::name).toList();
        out.add(new Setting("editorKey", "Editor key", "The key that opens Strata: " + String.join(", ", keys)
                + " (NONE: only /strata ui).", "At the next launch.", Kind.CHOICE,
                StrataConfig.EDITOR_KEY.get().name(), StrataConfig.EDITOR_KEY.getDefault().name(), 0, 0, keys));
        // A dropdown: a pick from a set.
        out.add(new Setting(SERVER_ASSETS, "Server textures & sounds",
                "What happens when a server offers its custom textures and sounds. Ask shows a question with"
                        + " the server's name, how many files and their size; Always takes them; Never takes none."
                        + " Every file is checked by your game whatever the server sends.",
                "At the next time you join a server.", Kind.CHOICE,
                StrataConfig.SERVER_ASSETS.get().word(), StrataConfig.SERVER_ASSETS.getDefault().word(), 0, 0,
                SERVER_ASSETS_CHOICES));
        return out;
    }

    private static Setting toggle(String key, String label, String help, String applies,
                                  ModConfigSpec.BooleanValue value) {
        return new Setting(key, label, help, applies, Kind.SWITCH, onOff(value.get()), onOff(value.getDefault()),
                0, 0, List.of());
    }

    private static Setting number(String key, String label, String help, String applies,
                                  ModConfigSpec.DoubleValue value, double min, double max) {
        return new Setting(key, label, help, applies, Kind.NUMBER, String.valueOf(value.get()),
                String.valueOf(value.getDefault()), min, max, List.of());
    }

    /** A switch's word: "On" or "Off". */
    public static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    /** The setting with this key, in Options, Scaling, or Debug, or null. */
    public static Setting find(String key) {
        return java.util.stream.Stream.of(optionDefs(), scalingDefs(), debugDefs()).flatMap(List::stream)
                .filter(s -> s.key().equals(key)).findFirst().orElse(null);
    }

    /** Why a typed value cannot be set, or null when it can. */
    public static String problem(Setting s, String typed) {
        String t = typed == null ? "" : typed.strip();
        return switch (s.kind()) {
            case SWITCH -> parseSwitch(t) == null ? "Type On or Off, or press On / Off" : null;
            case NUMBER -> {
                try {
                    double d = Double.parseDouble(t);
                    yield Double.isFinite(d) && d >= s.min() && d <= s.max() ? null
                            : "A number from " + s.min() + " to " + s.max();
                } catch (NumberFormatException e) {
                    yield "A number from " + s.min() + " to " + s.max();
                }
            }
            case CHOICE -> s.choices().stream().anyMatch(c -> c.equalsIgnoreCase(t)) ? null
                    : "One of: " + String.join(", ", s.choices());
            case SPAWN -> t.isEmpty() || t.matches("biome:[a-z0-9_.-]+:[a-z0-9_./-]+") || t.matches("tier:.+") ? null
                    : "Pick a biome or a tier";
        };
    }

    /**
     * What a setting holds, for the sheet's control: a choice is a dropdown of its names (the editor's key,
     * the GUI scale), a number is a field in its range, a switch is shown as On or Off (the On / Off button
     * changes it, nothing is typed), and a spawn is its text.
     */
    public static com.strata.model.ValueType typeOf(Setting s) {
        return switch (s.kind()) {
            case SWITCH -> com.strata.model.ValueType.choice(List.of("On", "Off"));
            case NUMBER -> com.strata.model.ValueType.number(s.min(), s.max());
            case CHOICE -> com.strata.model.ValueType.choice(s.choices());
            case SPAWN -> com.strata.model.ValueType.TEXT;
        };
    }

    /** On or off from a typed word, or null when it is neither. */
    public static Boolean parseSwitch(String typed) {
        String t = typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> null;
        };
    }

    /** Sets a setting to a typed value, writes the file, and reloads. */
    public static WriteReport set(String key, String typed) {
        Setting s = find(key);
        if (s == null) {
            return WriteReport.refused("No setting named " + key);
        }
        String problem = problem(s, typed);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String t = typed.strip();
        boolean wrote;
        // The editor's undo keeps the old value: Undo sets it again.
        String old = s.value();
        com.strata.data.EditHistory.record(new SettingStep(key, old, t));
        try {
            switch (key) {
                case MOB_SCALING -> save(StrataConfig.ENABLE_MOB_SCALING, parseSwitch(t));
                case CHUNK_SCALING -> save(StrataConfig.ENABLE_CHUNK_SCALING, parseSwitch(t));
                case VIEW_LOCKED -> save(StrataConfig.VIEW_LOCKED_VALUES, parseSwitch(t));
                case SHOW_DUPLICATES -> save(StrataConfig.SHOW_DUPLICATE_EDITORS, parseSwitch(t));
                case GUI_SCALE -> save(StrataConfig.GUI_SCALE, guiScaleOf(t));
                case SERVER_ASSETS -> save(StrataConfig.SERVER_ASSETS, StrataConfig.ServerAssets.ofWord(t));
                case PLAYER_SCALING -> save(StrataConfig.ENABLE_PLAYER_SCALING, parseSwitch(t));
                case "enableSpawnSubstitution" -> save(StrataConfig.ENABLE_SPAWN_SUBSTITUTION, parseSwitch(t));
                case BLOCK_OTHER_SPAWNS -> save(StrataConfig.BLOCK_OTHER_SPAWNS, parseSwitch(t));
                case MONSTERS_IN_CREATURE_SLOTS -> save(StrataConfig.MONSTERS_IN_CREATURE_SLOTS, parseSwitch(t));
                case MONSTERS_IN_AMBIENT_SLOTS -> save(StrataConfig.MONSTERS_IN_AMBIENT_SLOTS, parseSwitch(t));
                case MONSTERS_IN_WATER_SLOTS -> save(StrataConfig.MONSTERS_IN_WATER_SLOTS, parseSwitch(t));
                case MONSTERS_OUTSIDE_DARKNESS -> save(StrataConfig.MONSTERS_OUTSIDE_DARKNESS, parseSwitch(t));
                case "enableOreRestriction" -> save(StrataConfig.ENABLE_ORE_RESTRICTION, parseSwitch(t));
                case "enableLoot" -> save(StrataConfig.ENABLE_LOOT, parseSwitch(t));
                case SPECIAL_SPAWNERS -> save(StrataConfig.ENABLE_SPECIAL_SPAWNERS, parseSwitch(t));
                case TRADES -> save(StrataConfig.ENABLE_TRADES, parseSwitch(t));
                case EXTRA_MOB_ATTRIBUTES -> save(StrataConfig.ENABLE_EXTRA_MOB_ATTRIBUTES, parseSwitch(t));
                case DEBUG_HUD -> save(StrataConfig.ENABLE_DEBUG_HUD, parseSwitch(t));
                case DEBUG_PLAYER_SPAWN -> save(StrataConfig.DEBUG_PLAYER_SPAWN, t);
                case PLAYER_SPAWN_RADIUS -> save(StrataConfig.PLAYER_SPAWN_RADIUS, (int) Math.round(Double.parseDouble(t)));
                case MOB_XP_PER_LEVEL -> save(StrataConfig.MOB_XP_PER_LEVEL, Double.parseDouble(t));
                case MOB_XP_EXPONENT -> save(StrataConfig.MOB_XP_EXPONENT, Double.parseDouble(t));
                case CHUNK_SCALE -> save(StrataConfig.CHUNK_SCALE, Double.parseDouble(t));
                case "editorKey" -> save(StrataConfig.EDITOR_KEY,
                        StrataConfig.EditorKey.valueOf(t.toUpperCase(Locale.ROOT)));
                default -> throw new IllegalArgumentException(key);
            }
            wrote = true;
        } catch (RuntimeException e) {
            Strata.LOGGER.error("Could not set {} to {}: {}", key, t, e.toString(), e);
            wrote = false;
        }
        WriteReport report = new WriteReport().record("the setting " + s.label(), wrote);
        if (report.ok() && DEBUG_PLAYER_SPAWN.equals(key)) {
            // In an open world the spawn moves now, and the players with it.
            com.strata.runtime.PlayerSpawnBiome.applyNow();
        }
        if (report.ok() && PLAYER_SCALING.equals(key)) {
            // Players in the world get the change at once, not at their next XP change.
            com.strata.runtime.PlayerAttributeScaling.refreshOnline();
        }
        return Commit.reloadIfOk(report);
    }

    /** Flips a switch. */
    public static WriteReport toggle(String key) {
        Setting s = find(key);
        if (s == null || s.kind() != Kind.SWITCH) {
            return WriteReport.refused("Only a switch can be toggled");
        }
        return set(key, onOff(!"On".equals(s.value())));
    }

    /** Puts a setting back to the value Strata ships. */
    public static WriteReport restore(String key) {
        Setting s = find(key);
        return s == null ? WriteReport.refused("No setting named " + key) : set(key, s.defaultValue());
    }

    /** A setting's change, for the editor's undo. */
    private record SettingStep(String key, String before, String typedAfter)
            implements com.strata.data.EditHistory.Step {

        private String now() {
            Setting s = find(key);
            return s == null ? "" : s.value();
        }

        private String after() {
            Setting s = find(key);
            return s == null ? typedAfter : shown(s, typedAfter);
        }

        @Override
        public boolean unchangedSince() {
            return now().equals(after());
        }

        @Override
        public boolean noChange() {
            return before.equals(after());
        }

        @Override
        public void restore() throws java.io.IOException {
            if (!set(key, before).ok()) {
                throw new java.io.IOException("the setting " + key);
            }
        }

        @Override
        public String toString() {
            return "the setting " + key;
        }
    }

    /** A typed value as the setting shows it: a switch's On or Off, a number's Java form. */
    private static String shown(Setting s, String typed) {
        String t = typed == null ? "" : typed.strip();
        return switch (s.kind()) {
            case SWITCH -> parseSwitch(t) == null ? t : onOff(parseSwitch(t));
            case NUMBER -> {
                try {
                    yield String.valueOf(Double.parseDouble(t));
                } catch (NumberFormatException e) {
                    yield t;
                }
            }
            case CHOICE -> s.choices().stream().filter(c -> c.equalsIgnoreCase(t)).findFirst().orElse(t);
            case SPAWN -> t;
        };
    }

    private static <T> void save(ModConfigSpec.ConfigValue<T> value, T v) {
        value.set(v);
        value.save();
        // Kept in the active profile, never in Default.
        com.strata.data.ProfileManager.autoSave();
    }
}
