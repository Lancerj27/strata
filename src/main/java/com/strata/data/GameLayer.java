package com.strata.data;

import com.strata.Strata;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The GAME LAYER. */
public final class GameLayer {

    private GameLayer() {
    }

    /** The extracted mob sheet. */
    public static final String MOBS = "mobs.csv";
    /**
     * Every mob's own AI goals: group GOAL, key the goal's place in the list, value the goal as
     * GameGoal#encode writes it.
     */
    public static final String GOALS = "goals.csv";
    /**
     * Every mob's default equipment as the game runs it, one row per value (DefaultEquipmentTable), with the
     * family row.
     */
    public static final String EQUIPMENT = "equipment.csv";
    /** What the game spawns: GameSnapshot writes and reads it. */
    public static final String SPAWNS = "spawns.csv";
    /** Every mob's loot table, one row per value (runtime.LootExtractor). */
    public static final String LOOT = "loot.csv";
    /** A loot sheet row: one value of a table (entity = table id), or a mob's table (entity = mob id). */
    public static final String LOOT_VALUE = "loot";
    public static final String LOOT_TABLE = "table";
    /**
     * Every villager profession's and the wandering trader's trades: entity = the profession
     * id (or minecraft:wandering_trader), group "trade", key "level|index", value Trade#encode.
     */
    public static final String TRADES = "trades.csv";
    public static final String TRADE = "trade";
    /** A villager type and a profession the game has, in trades.csv: entity = key = the id. */
    public static final String VILLAGER_TYPE = "villager_type";
    public static final String PROFESSION = "profession";
    /**
     * Every item's stats (runtime.ItemStatsRuntime): entity = the item id; group "component" (key a
     * model.ItemStats key, value the game's), "note" (the same key: a tool rule's blocks, a modifier's
     * operation), and "kind" (key "kind", value a model.ItemStats kind).
     */
    public static final String ITEMS = "items.csv";
    public static final String COMPONENT = "component";
    public static final String NOTE = "note";
    public static final String KIND = "kind";
    /**
     * Every block's stats (runtime.BlockStatsRuntime): entity = the block id; group "component" (key a
     * model.BlockStats key, value the game's), "note" (light_level: LIT when only its lit states give light),
     * and "kind" (key "kind", value a model.BlockStats kind).
     */
    public static final String BLOCKS = "blocks.csv";
    /**
     * The block and item tags of each block variant's base block (runtime.BlockVariants):
     * entity = the base block id; group "block_tag" or "item_tag", key = the tag.
     */
    public static final String BLOCK_TAGS = "block_tags.csv";
    /** Every block's tool, tool level, and tags, read from the tag files at game start. */
    public static final String BLOCK_INFO = "block_info.csv";
    /** A block's loot table in loot.csv, as a chest's is (its Drops). */
    public static final String BLOCK_LOOT = "block_loot";
    public static final String BLOCK_TAG = "block_tag";
    public static final String ITEM_TAG = "item_tag";
    /**
     * Every crafting recipe (runtime.GameRecipes): entity = the recipe id; group "recipe", key type, result,
     * count, or s0 to s8 (the grid: an item id or "#tag").
     */
    public static final String RECIPES = "crafting_recipes.csv";
    public static final String RECIPE = "recipe";
    /** A chest loot table: entity = the table id, key "table", value the table id. */
    public static final String CHEST = "chest";
    public static final String SPAWN = "spawn";
    /** A structure's own spawn list: value category|entity|weight|min|max|box. */
    public static final String STRUCTURE_SPAWN = "structure_spawn";
    public static final String ORE = "ore";
    /** A biome's plant feature (a tree, a flower, grass), key = the placed feature. */
    public static final String VEGETATION = "vegetation";
    public static final String COST = "cost";
    public static final String BIOME = "biome";
    public static final String CAP = "cap";
    public static final String GOAL = "goal";
    public static final String HEADER = "entity,group,key,value";

    /** The groups of the mob sheet. */
    public static final String TYPE = "type";
    public static final String ATTRIBUTE = "attribute";
    public static final String TAG = "tag";
    public static final String FIELD = "field";
    /** A mob's model, texture, and sounds (group "model"): what it is drawn as and what it sounds like. */
    public static final String MODEL = "model";

    /** A mob's sound overrides (group "sounds"): ambient, hurt, death, step, attack, swim, fall + volume/pitch. */
    public static final String SOUNDS = "sounds";
    /** A mob's visual overrides (group "visuals"): texture, model, glow, scale, shadow, silent, glowing. */
    public static final String VISUALS = "visuals";
    /** A mob's physics overrides (group "physics"): width, height, eye_height, no_gravity. */
    public static final String PHYSICS = "physics";
    /** A mob's flag overrides (group "flags"): burn_in_daylight, fire_immune, lava_immune, etc. */
    public static final String FLAGS = "flags";
    /** A mob's attribute overrides (group "attributes"): movement_speed, attack_damage, max_health, etc. */
    public static final String ATTRIBUTES = "attributes";
    /** A mob's combat overrides (group "combat"): melee, ranged, projectile, damage immunities. */
    public static final String COMBAT = "combat";
    /** A mob's particle overrides (group "particles"): type, count, frequency, offset, velocity, enables. */
    public static final String PARTICLES = "particles";
    /** A mob's loot/xp overrides (group "loot_xp"): death loot table, xp min/max, equipment drops. */
    public static final String LOOT_XP = "loot_xp";
    /** A mob's boss bar (group "boss"): colour, style, name, range, sky, fog, music, despawn. */
    public static final String BOSS = "boss";
    /** A mob's terrain costs (group "pathfinding"): one per PathType. */
    public static final String PATHFINDING = "pathfinding";

    /** The client's sheet of each mob's own texture, group "model", key "texture". */
    public static final String TEXTURES = "textures.csv";
    /** The keys of the model group, in the editor's order. */
    public static final List<String> MODEL_KEYS = List.of("model", "texture", "sound_ambient", "sound_hurt",
            "sound_death");

    public record Row(String entity, String group, String key, String value) {
    }

    /** The game layer folder. */
    public static Path dir() {
        return Paths.ROOT.resolve("game");
    }

    /** A value type the sheet can hold and a user can edit: a number, a flag, a word. */
    public static boolean simple(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type == String.class
                || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class;
    }

    /** A field that is one value of each mob: not static, not made by the compiler, a simple type. */
    public static boolean wanted(Field field) {
        return !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic() && simple(field.getType());
    }

    /** Why a value cannot be edited, or null when it can. */
    public static String lockReason(String group, String key) {
        if (FIELD.equals(group) && key.startsWith("Entity.") && !ENTITY_SETTINGS.contains(key)) {
            return "An Entity field is the mob's live state (id, position, ticks)";
        }
        // Running state: the game sets it again as the mob lives.
        if (FIELD.equals(group) && com.strata.model.FieldGuide.runningState(key)) {
            return "The mob's running state: the game sets it again as the mob lives, so a change would not last";
        }
        if (TYPE.equals(group) && !EDITABLE_TYPE.contains(key)) {
            return "The game fixes this for the whole type at startup";
        }
        return null;
    }

    /**
     * THE ENTITY FIELDS THAT ARE SETTINGS, NOT STATE: saved with the mob and left alone by
     * the game as it runs - it cannot be hurt, it is always drawn, it shows as burning, blocks cannot be
     * placed in its space, it passes through blocks, it glows.
     */
    public static final java.util.Set<String> ENTITY_SETTINGS = java.util.Set.of("Entity.invulnerable",
            "Entity.noCulling", "Entity.hasVisualFire", "Entity.blocksBuilding", "Entity.noPhysics",
            "Entity.hasGlowingTag");

    public static boolean locked(String group, String key) {
        return lockReason(group, key) != null;
    }

    /** The type values Strata can set on a running type (MobDataRuntime). */
    public static final java.util.Set<String> EDITABLE_TYPE = java.util.Set.of("category", "summonable",
            "fire_immune", "spawn_far_from_player", "width", "height", "eye_height", "fixed_size",
            //, blocks it ignores, how far players see it, how often it is sent.
            "immune_to", "tracking_range", "update_interval",
            // Where it may spawn and its spawn surface, set at the next game start.
            "spawn_placement", "spawn_heightmap");

    /**
     * The entity tags the user changed, each as its whole member list: the game's members, with the mobs
     * switched on added and the ones switched off removed.
     */
    public static Map<String, List<String>> editedTags(List<Row> mobSheet, List<Map<String, String>> mobData) {
        Map<String, java.util.Set<String>> members = new LinkedHashMap<>();
        for (Row r : mobSheet) {
            if (TAG.equals(r.group())) {
                java.util.Set<String> set = members.computeIfAbsent(r.key(), k -> new java.util.TreeSet<>());
                if ("true".equals(r.value())) {
                    set.add(r.entity());
                }
            }
        }
        Map<String, java.util.Set<String>> changed = new LinkedHashMap<>();
        for (Map<String, String> row : mobData) {
            String tag = row.getOrDefault(Schema.MobData.KEY, "").strip();
            if (!TAG.equals(row.getOrDefault(Schema.MobData.GROUP, "").strip())
                    || !row.getOrDefault(Schema.MobData.VARIANT_NAME, "").strip().isEmpty()
                    || !members.containsKey(tag)) {
                continue;
            }
            java.util.Set<String> set = changed.computeIfAbsent(tag, k -> new java.util.TreeSet<>(members.get(k)));
            String entity = row.getOrDefault(Schema.MobData.ENTITY, "").strip();
            if ("true".equals(row.getOrDefault(Schema.MobData.VALUE, "").strip())) {
                set.add(entity);
            } else {
                set.remove(entity);
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        changed.forEach((tag, set) -> out.put(tag, List.copyOf(set)));
        return out;
    }

    /** Each base mob's edited category, entity id to the game's category name (monster, creature,... */
    public static Map<String, String> editedCategories(List<Map<String, String>> mobData) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> row : mobData) {
            if (TYPE.equals(row.getOrDefault(Schema.MobData.GROUP, "").strip())
                    && "category".equals(row.getOrDefault(Schema.MobData.KEY, "").strip())
                    && row.getOrDefault(Schema.MobData.VARIANT_NAME, "").strip().isEmpty()) {
                String entity = row.getOrDefault(Schema.MobData.ENTITY, "").strip();
                String value = row.getOrDefault(Schema.MobData.VALUE, "").strip().toLowerCase(java.util.Locale.ROOT);
                if (!entity.isEmpty() && !value.isEmpty()) {
                    out.put(entity, value);
                }
            }
        }
        return out;
    }

    /** Each variant's own category, "entity|variant" to the category id: its Global row. */
    public static Map<String, String> editedVariantCategories(List<Map<String, String>> mobData) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> row : mobData) {
            String variant = row.getOrDefault(Schema.MobData.VARIANT_NAME, "").strip();
            if (TYPE.equals(row.getOrDefault(Schema.MobData.GROUP, "").strip())
                    && "category".equals(row.getOrDefault(Schema.MobData.KEY, "").strip())
                    && !variant.isEmpty() && row.getOrDefault(Schema.MobData.SCOPE, "").strip().isEmpty()) {
                String entity = row.getOrDefault(Schema.MobData.ENTITY, "").strip();
                String value = row.getOrDefault(Schema.MobData.VALUE, "").strip().toLowerCase(java.util.Locale.ROOT);
                if (!entity.isEmpty() && !value.isEmpty()) {
                    out.put(entity + "|" + variant, value);
                }
            }
        }
        return out;
    }

    /** A value as the sheet writes it. */
    public static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double d) {
            return Parse.number(d);
        }
        if (value instanceof Enum<?> e) {
            return e.name().toLowerCase(java.util.Locale.ROOT);
        }
        return String.valueOf(value);
    }

    /**
     * Writes the whole sheet in one move, so a reader never sees half of it.
     *
     * @return false when the file could not be written (logged)
     */
    public static boolean write(Path file, String comment, List<Row> rows) {
        List<String> body = new ArrayList<>();
        for (Row r : rows) {
            body.add(Parse.line(new String[]{r.entity(), r.group(), r.key(), r.value()}));
        }
        return writeLines(file, comment, HEADER, body);
    }

    /** Writes a sheet in its own row format, whole, in one move. */
    public static boolean writeLines(Path file, String comment, String header, List<String> body) {
        List<String> lines = new ArrayList<>();
        for (String line : comment.split("\n")) {
            lines.add("# " + line);
        }
        lines.add(header);
        lines.addAll(body);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                CsvTable.forget(file);
            }
            return true;
        } catch (IOException e) {
            Strata.LOGGER.error("Could not write the game layer {}: {}", file, e.toString());
            return false;
        }
    }

    /** The sheet's rows, in file order. */
    public static List<Row> read(Path file) {
        List<Row> out = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        for (Map<String, String> row : CsvTable.read(file)) {
            out.add(new Row(row.getOrDefault("entity", ""), row.getOrDefault("group", ""),
                    row.getOrDefault("key", ""), row.getOrDefault("value", "")));
        }
        return out;
    }

    /** The rows of each entity, in file order. */
    public static Map<String, List<Row>> byEntity(List<Row> rows) {
        Map<String, List<Row>> out = new LinkedHashMap<>();
        for (Row r : rows) {
            out.computeIfAbsent(r.entity(), k -> new ArrayList<>()).add(r);
        }
        return out;
    }
}
