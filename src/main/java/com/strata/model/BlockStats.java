package com.strata.model;

import java.util.List;

/** THE BLOCK STATS, AS RULES. */
public final class BlockStats {

    private BlockStats() {
    }

    public static final String HARDNESS = "hardness";
    public static final String BLAST_RESISTANCE = "blast_resistance";
    public static final String FRICTION = "friction";
    public static final String SPEED_FACTOR = "speed_factor";
    public static final String JUMP_FACTOR = "jump_factor";
    public static final String LIGHT_LEVEL = "light_level";
    public static final String REQUIRES_TOOL = "requires_tool";
    public static final String FLAMMABILITY = "flammability";
    public static final String FIRE_SPREAD = "fire_spread";

    // ALL THE BLOCK'S INFO.
    /** The tool that mines it fast: the game's mineable tags. */
    public static final String TOOL = "tool";
    /** The lowest tool tier that gets its drops: the game's needs_*_tool tags. */
    public static final String TOOL_LEVEL = "tool_level";
    public static final String REPLACEABLE = "replaceable";
    public static final String PUSH_REACTION = "push_reaction";
    public static final String INSTRUMENT = "instrument";
    public static final String IGNITED_BY_LAVA = "ignited_by_lava";
    // Shown, not edited: what the game builds the block from.
    public static final String SOUND = "sound";
    public static final String MAP_COLOR = "map_color";
    public static final String RANDOM_TICKS = "random_ticks";
    public static final String SOLID = "solid";
    public static final String COLLISION = "collision";
    public static final String OCCLUDES = "occludes";
    public static final String REDSTONE_CONDUCTOR = "redstone_conductor";
    public static final String BLOCK_ENTITY = "block_entity";
    public static final String ITEM = "item";
    public static final String LOOT_TABLE = "loot_table";
    public static final String TAGS = "tags";
    // PLAN C STEP 5: the rest of what the game builds a block from.
    public static final String TERRAIN_PARTICLES = "terrain_particles";
    public static final String EMISSIVE = "emissive";
    public static final String MOBS_SPAWN_ON = "mobs_spawn_on";
    public static final String SUFFOCATING = "suffocating";
    public static final String VIEW_BLOCKING = "view_blocking";
    public static final String OFFSET = "offset";
    /** Its state properties (facing, age): fixed when the block registers, so shown. */
    public static final String STATES = "states";

    /** A block's position offset (the game's OffsetType): none, sideways, or sideways and down. */
    public static final List<String> OFFSETS = List.of("none", "xz", "xyz");

    /**
     * The game's sound types and map colours by name, filled by the runtime at game start
     * (BlockStatsRuntime): the names are the game's own constants.
     */
    public static volatile List<String> soundNames = List.of();
    public static volatile List<String> mapColorNames = List.of();

    /** Every key Strata edits, in the sheet's order. */
    public static final List<String> KEYS = List.of(HARDNESS, BLAST_RESISTANCE, REQUIRES_TOOL, TOOL, TOOL_LEVEL,
            FRICTION, SPEED_FACTOR, JUMP_FACTOR, LIGHT_LEVEL, FLAMMABILITY, FIRE_SPREAD, REPLACEABLE, PUSH_REACTION,
            INSTRUMENT, IGNITED_BY_LAVA, SOUND, MAP_COLOR, RANDOM_TICKS, SOLID, COLLISION, OCCLUDES, REDSTONE_CONDUCTOR,
            TERRAIN_PARTICLES, EMISSIVE, MOBS_SPAWN_ON, SUFFOCATING, VIEW_BLOCKING, OFFSET);
    /**
     * Every key shown and not edited, after the edited ones: what the block is registered as (its block
     * entity, item and states) and what has an editor of its own (loot, tags).
     */
    public static final List<String> SHOWN = List.of(BLOCK_ENTITY, ITEM, STATES, LOOT_TABLE, TAGS);

    /** The switches: true or false. */
    public static final List<String> SWITCHES = List.of(REQUIRES_TOOL, REPLACEABLE, IGNITED_BY_LAVA, RANDOM_TICKS,
            SOLID, COLLISION, OCCLUDES, REDSTONE_CONDUCTOR, TERRAIN_PARTICLES, EMISSIVE, MOBS_SPAWN_ON, SUFFOCATING,
            VIEW_BLOCKING);
    /** The sheet's order: the edited values, then the shown ones. */
    public static final List<String> ORDER;
    static {
        List<String> all = new java.util.ArrayList<>(KEYS);
        all.addAll(SHOWN);
        ORDER = List.copyOf(all);
    }

    /** The tool choices: the game's four mineable tags, or none (any tool or a hand, all as slow). */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe", "none");
    /** The tool level choices: the game's needs_stone_tool, needs_iron_tool, needs_diamond_tool, or any. */
    public static final List<String> TOOL_LEVELS = List.of("any", "stone", "iron", "diamond");
    /** The piston reactions (the game's PushReaction). */
    public static final List<String> PUSH_REACTIONS = List.of("normal", "destroy", "block", "ignore", "push_only");
    /** A note block's sound over this block (the game's NoteBlockInstrument). */
    public static final List<String> INSTRUMENTS = List.of("harp", "basedrum", "snare", "hat", "bass", "flute",
            "bell", "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo",
            "pling", "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head");

    /** The game's four mineable tags, in TOOLS order (a block in two names the first as its tool). */
    public static final List<String> TOOL_TAGS = List.of("minecraft:mineable/pickaxe", "minecraft:mineable/axe",
            "minecraft:mineable/shovel", "minecraft:mineable/hoe");
    /** The game's tool level tags, in TOOL_LEVELS order after any. */
    public static final List<String> LEVEL_TAGS = List.of("minecraft:needs_stone_tool", "minecraft:needs_iron_tool",
            "minecraft:needs_diamond_tool");

    /** The tag a Tool value puts a block in, or null for none. */
    public static String toolTag(String tool) {
        int i = TOOLS.indexOf(tool);
        return i >= 0 && i < TOOL_TAGS.size() ? TOOL_TAGS.get(i) : null;
    }

    /** The tag a Tool level value puts a block in, or null for any. */
    public static String levelTag(String level) {
        int i = TOOL_LEVELS.indexOf(level);
        return i > 0 ? LEVEL_TAGS.get(i - 1) : null;
    }

    /** A value shown and not edited. */
    public static boolean shownOnly(String key) {
        return SHOWN.contains(key);
    }

    /** The words a value takes, or empty for a number. */
    public static List<String> choices(String key) {
        return switch (key) {
            case TOOL -> TOOLS;
            case SOUND -> soundNames;
            case MAP_COLOR -> mapColorNames;
            case OFFSET -> OFFSETS;
            case TOOL_LEVEL -> TOOL_LEVELS;
            case PUSH_REACTION -> PUSH_REACTIONS;
            case INSTRUMENT -> INSTRUMENTS;
            default -> SWITCHES.contains(key) ? List.of("true", "false") : List.of();
        };
    }

    /**
     * What a value is, for the sheet's control: the switches are true or false, Tool, Tool level, Push
     * reaction and Instrument are one of their words, the rest are numbers in the range {@link #problem}
     * allows.
     */
    public static ValueType typeOf(String key) {
        return switch (key) {
            case TOOL, TOOL_LEVEL, PUSH_REACTION, INSTRUMENT, SOUND, MAP_COLOR, OFFSET -> ValueType.choice(choices(key));
            case HARDNESS -> ValueType.number(-1, Double.MAX_VALUE);
            case BLAST_RESISTANCE -> ValueType.number(0, Double.MAX_VALUE);
            case FRICTION -> ValueType.number(0, 1);
            case SPEED_FACTOR, JUMP_FACTOR -> ValueType.number(0, MAX_FACTOR);
            case LIGHT_LEVEL -> ValueType.whole(0, MAX_LIGHT);
            case FLAMMABILITY, FIRE_SPREAD -> ValueType.whole(0, MAX_FIRE_ODDS);
            default -> SWITCHES.contains(key) ? ValueType.BOOL : ValueType.TEXT;
        };
    }

    /** The most the game's light goes to. */
    public static final int MAX_LIGHT = 15;
    /** The most a fire odds value may be (the game's own go to 100; a higher one only burns faster). */
    public static final int MAX_FIRE_ODDS = 300;
    /** The most a speed or jump factor may be. */
    public static final double MAX_FACTOR = 10;

    /** The kinds the list groups by, in order. */
    public static final String ORES = "Ores";
    public static final String STONE = "Stone and metal";
    public static final String WOOD = "Wood";
    public static final String PLANTS = "Plants";
    public static final String LIQUIDS = "Liquids";
    public static final String OTHER = "Other";
    public static final List<String> KINDS = List.of(ORES, STONE, WOOD, PLANTS, LIQUIDS, OTHER);

    /** A block's kind from what it is: never from a list of block names. */
    public static String kind(boolean liquid, boolean ore, boolean plant, boolean wood, boolean needsTool) {
        if (liquid) return LIQUIDS;
        if (ore) return ORES;
        if (plant) return PLANTS;
        if (wood) return WOOD;
        if (needsTool) return STONE;
        return OTHER;
    }

    /** A value's name in the list. */
    public static String name(String key) {
        return switch (key) {
            case HARDNESS -> "Hardness";
            case BLAST_RESISTANCE -> "Blast resistance";
            case FRICTION -> "Friction";
            case SPEED_FACTOR -> "Speed factor";
            case JUMP_FACTOR -> "Jump factor";
            case LIGHT_LEVEL -> "Light level";
            case REQUIRES_TOOL -> "Needs the correct tool";
            case FLAMMABILITY -> "Flammability";
            case FIRE_SPREAD -> "Fire spread";
            case TOOL -> "Tool";
            case TOOL_LEVEL -> "Tool level";
            case REPLACEABLE -> "Replaceable";
            case PUSH_REACTION -> "Piston reaction";
            case INSTRUMENT -> "Note block sound";
            case IGNITED_BY_LAVA -> "Lava sets it on fire";
            case SOUND -> "Sound";
            case MAP_COLOR -> "Map color";
            case RANDOM_TICKS -> "Random ticks";
            case SOLID -> "Solid";
            case COLLISION -> "Collision";
            case OCCLUDES -> "Blocks light and view";
            case REDSTONE_CONDUCTOR -> "Conducts redstone";
            case BLOCK_ENTITY -> "Block entity";
            case ITEM -> "Item";
            case LOOT_TABLE -> "Loot table";
            case TAGS -> "Tags";
            case TERRAIN_PARTICLES -> "Breaking particles";
            case EMISSIVE -> "Glows (drawn fully lit)";
            case MOBS_SPAWN_ON -> "Mobs spawn on it";
            case SUFFOCATING -> "Suffocates inside it";
            case VIEW_BLOCKING -> "Blocks the view inside it";
            case OFFSET -> "Position offset";
            case STATES -> "States";
            default -> key;
        };
    }

    /**
     * What a value does.
     *
     * @param note the game layer's note on the value: "lit" when only the block's lit states give light
     */
    public static String what(String key, String note) {
        return switch (key) {
            case HARDNESS -> "How long it takes to mine. Stone is 1.5, obsidian 50, -1 cannot be broken.";
            case BLAST_RESISTANCE -> "How well it stands up to explosions. Stone is 6, obsidian 1200.";
            case FRICTION -> "How slippery it is to walk on, 0 to 1. Most blocks are 0.6, ice 0.98.";
            case SPEED_FACTOR -> "Walking speed on it. 1 is normal, soul sand 0.4.";
            case JUMP_FACTOR -> "Jump height from it. 1 is normal, honey 0.5.";
            case LIGHT_LEVEL -> "LIT".equals(note)
                    ? "Light it gives, 0 to 15, in its lit states only (as a lit lamp or furnace)."
                    : "Light it gives, 0 to 15. Torches are 14, glowstone 15.";
            case REQUIRES_TOOL -> "true: it drops nothing unless mined with the correct tool, as stone needs a pickaxe.";
            case FLAMMABILITY -> "How fast fire burns it away. 0: fire does not burn it. Planks are 20, wool 60.";
            case FIRE_SPREAD -> "How easily fire spreads to it. 0: fire does not catch on it. Planks are 5, leaves 30.";
            case TOOL -> "The tool that mines it fast: pickaxe, axe, shovel, hoe, or none. Applies at the next world"
                    + " load or /reload.";
            case TOOL_LEVEL -> "The lowest tool that gets its drops when it needs the correct tool: any, stone, iron,"
                    + " or diamond. Applies at the next world load or /reload.";
            case REPLACEABLE -> "true: placing a block in its space replaces it, as with grass or snow layers.";
            case PUSH_REACTION -> "What a piston does to it: normal (pushes and pulls), destroy (breaks it), block"
                    + " (cannot move it), ignore, or push_only.";
            case INSTRUMENT -> "The sound a note block makes when this block is under it.";
            case IGNITED_BY_LAVA -> "true: lava next to it can set it on fire.";
            case SOUND -> "Its sounds when walked on, placed, or broken: one of the game's sound sets.";
            case MAP_COLOR -> "Its color on a map, one of the game's map colors. A map shows it as it next draws.";
            case RANDOM_TICKS -> "true: the game ticks it at random (crops grow, leaves decay, ice melts). Applies to"
                    + " chunks as they load.";
            case SOLID -> "true: the game counts it as solid ground (for snow, plants and the like on it).";
            case COLLISION -> "false: entities pass through it. true: they bump into its shape.";
            case OCCLUDES -> "true: it hides the faces behind it and blocks light and the view. Chunks redraw as they load.";
            case REDSTONE_CONDUCTOR -> "true: redstone power passes through it.";
            case TERRAIN_PARTICLES -> "true: breaking it and running on it throw its bits.";
            case EMISSIVE -> "true: it is drawn fully lit, glowing in the dark (as magma does), without giving light.";
            case MOBS_SPAWN_ON -> "true: mobs that spawn on the ground can spawn on it. false: none can.";
            case SUFFOCATING -> "true: an entity whose head is inside it takes suffocation damage.";
            case VIEW_BLOCKING -> "true: a camera inside it sees nothing past it.";
            case OFFSET -> "How it sits off the block grid, by its position: none, xz (sideways, as grass and flowers)"
                    + " or xyz (sideways and down, as small dripleaf).";
            case STATES -> "Its states (facing, age, waterlogged and so on): fixed when the block registers. Shown.";
            case BLOCK_ENTITY -> "true: it holds data of its own, as a chest or a furnace. Shown, not edited.";
            case ITEM -> "The item it is placed from. Shown, not edited.";
            case LOOT_TABLE -> "The table its drops come from. Loot opens it to edit.";
            case TAGS -> "Every block tag it is in. Tool and Tool level change the tool tags.";
            default -> "";
        };
    }

    /** The value is a word (true or false), saved lower case. */
    public static boolean isSwitch(String key) {
        return !choices(key).isEmpty();
    }

    /** Why a typed value cannot be saved, or null. */
    public static String problem(String key, String typed) {
        String v = typed == null ? "" : typed.strip();
        if (v.isEmpty()) {
            return "Type a value, or press Restore for the game's";
        }
        return switch (key) {
            case HARDNESS -> {
                Double d = ItemStats.number(v);
                yield d == null || (d < 0 && d != -1) ? "The value must be a number, 0 or more, or -1 (unbreakable)" : null;
            }
            case BLAST_RESISTANCE -> {
                Double d = ItemStats.number(v);
                yield d == null || d < 0 ? "The value must be a number, 0 or more" : null;
            }
            case FRICTION -> {
                Double d = ItemStats.number(v);
                yield d == null || d < 0 || d > 1 ? "The value must be a number from 0 to 1" : null;
            }
            case SPEED_FACTOR, JUMP_FACTOR -> {
                Double d = ItemStats.number(v);
                yield d == null || d < 0 || d > MAX_FACTOR ? "The value must be a number from 0 to 10" : null;
            }
            case LIGHT_LEVEL -> {
                Integer n = ItemStats.whole(v);
                yield n == null || n < 0 || n > MAX_LIGHT ? "The value must be a whole number from 0 to 15" : null;
            }
            case FLAMMABILITY, FIRE_SPREAD -> {
                Integer n = ItemStats.whole(v);
                yield n == null || n < 0 || n > MAX_FIRE_ODDS ? "The value must be a whole number from 0 to 300" : null;
            }
            default -> {
                if (shownOnly(key) || !KEYS.contains(key)) {
                    yield shownOnly(key) ? "This value is shown, not edited" : "Strata does not edit this value";
                }
                List<String> words = choices(key);
                // A list the runtime fills (sounds, map colours) is empty in the headless gate: no check
                // there.
                yield words.isEmpty() || words.contains(v.toLowerCase(java.util.Locale.ROOT)) ? null
                        : "The value must be one of: " + String.join(", ", words);
            }
        };
    }
}
