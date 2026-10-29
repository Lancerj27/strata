package com.strata.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The CSV schema: every table's file name and every column name, as constants. */
public final class Schema {

    private Schema() {}

    /** Runtime-only scaling lookups S2 does not require in the shipped header. */
    public static final Set<String> SCALING_OPTIONAL_COLUMNS =
            Set.of(Scaling.START_LEVEL, Scaling.MODIFIER_ID);

    /** Named design templates. */
    public static final class Tiers {
        private Tiers() {}
        public static final String FILE = "tiers.csv";
        public static final String NAME = "name";
        public static final String LEVEL = "level";
        public static final String SPAWN_MODE = "spawn_mode";
        /** Blocks the spawns not from a Replace tier's list: true, false, or blank for the global option. */
        public static final String BLOCK_OTHER_SPAWNS = "block_other_spawns";
        public static final String COEFFICIENT = "coefficient";
    }

    /** Which biomes use which tier. */
    public static final class TierBiomes {
        private TierBiomes() {}
        public static final String FILE = "tier_biomes.csv";
        public static final String TIER = "tier";
        public static final String BIOME = "biome";
    }

    /** Per-biome overrides, for biomes that differ from their tier. */
    public static final class BiomeOverrides {
        private BiomeOverrides() {}
        public static final String FILE = "biome_overrides.csv";
        public static final String BIOME = "biome";
        public static final String LEVEL = "level";
        public static final String SCALE_MOBS = "scale_mobs";
        public static final String RESTRICT_ORES = "restrict_ores";
        public static final String SUBSTITUTE_SPAWNS = "substitute_spawns";
        /** Blocks the spawns not from a Replace list: true, false, or blank to inherit the tier's. */
        public static final String BLOCK_OTHER_SPAWNS = "block_other_spawns";
        public static final String COEFFICIENT = "coefficient";
    }

    /** Mob spawn lists. */
    public static final class MobSpawns {
        private MobSpawns() {}
        public static final String FILE = "mob_spawns.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String CATEGORY = "category";
        public static final String WEIGHT = "weight";
        /** 0 or more: a group that rolls 0 spawns nothing. */
        public static final String MIN_COUNT = "min_count";
        public static final String MAX_COUNT = "max_count";
        public static final String ENABLED = "enabled";
        public static final String COEFFICIENT = "coefficient";
        /** The chance, 0 to 100, that the mob spawns when the game picks it by weight; blank is 100. */
        public static final String SPAWN_CHANCE = "spawn_chance";
        public static final String IS_VARIANT = "is_variant";
        public static final String VARIANT_NAME = "variant_name";
        public static final String VARIANT_MODE = "variant_mode";

        /** WHO PUT THIS ROW HERE - the seeder, or the designer. */
        public static final String ORIGIN = "origin";

        /** The VALUES {@link #ORIGIN} takes — not columns. */
        public static final class Origin {
            private Origin() {}

            /** Written by the seeder before, from the registries: inventory. */
            public static final String SEEDED = "seeded";

            /** Written by a designer through the editor: intent. */
            public static final String DESIGNER = "designer";
        }

        /** True when a row is the designer's own, not seeded inventory. */
        public static boolean isDesignerRow(java.util.Map<String, String> row) {
            String origin = row.getOrDefault(ORIGIN, "").strip();
            if (!origin.isEmpty()) {
                return Origin.DESIGNER.equals(origin);
            }
            return !"biome".equals(row.getOrDefault(SCOPE, "").strip());
        }
    }

    /** Base stats before any scaling. */
    public static final class MobBaseStats {
        private MobBaseStats() {}
        public static final String FILE = "mob_base_stats.csv";
        public static final String ENTITY = "entity";
        public static final String ATTRIBUTE = "attribute";
        public static final String VALUE = "value";
    }

    /** Spawn conditions; several on one entity AND-combine. */
    public static final class SpawnRules {
        private SpawnRules() {}
        public static final String FILE = "spawn_rules.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        /** WHICH VARIANT this row belongs to, or blank for the base mob. */
        public static final String VARIANT_NAME = "variant_name";
        public static final String RULE_TYPE = "rule_type";
        public static final String VALUE = "value";
    }

    /** How attributes grow with level. */
    public static final class Scaling {
        private Scaling() {}
        public static final String MOBS_FILE = "scaling_mobs.csv";
        public static final String PLAYER_FILE = "scaling_player.csv";
        public static final String ATTRIBUTE = "attribute";
        public static final String PER_LEVEL = "per_level";
        public static final String EXPONENT = "exponent";
        public static final String ENABLED = "enabled";
        // These two are OPTIONAL at the file level: a row without them gets the runtime defaults (start 1,
        // auto-derived unique modifier id).
        public static final String START_LEVEL = "start_level";
        public static final String MODIFIER_ID = "modifier_id";
    }

    /** The scaling formulas (ScalingFormulas): one row for each formula a designer changed. */
    public static final class ScalingFormulas {
        private ScalingFormulas() {}
        public static final String FILE = "scaling_formulas.csv";
        public static final String FORMULA = "formula";
        public static final String EXPRESSION = "expression";
    }

    /** Ore control. */
    public static final class Ores {
        private Ores() {}
        public static final String FILE = "ores.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ORE_ID = "ore_id";
        public static final String ENABLED = "enabled";
        public static final String FREQUENCY_PCT = "frequency_pct";
    }

    /** The flora rules: a plant feature (a tree, a flower, grass) on or off in a tier or a biome. */
    public static final class Vegetation {
        private Vegetation() {}
        public static final String FILE = "vegetation.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String FEATURE = "feature";
        public static final String ENABLED = "enabled";
    }

    /** Named mob variants. */
    public static final class Variants {
        private Variants() {}
        public static final String FILE = "variants.csv";
        public static final String NAME = "name";
        /** WHICH SCOPE this definition applies in. */
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String BASE_ENTITY = "base_entity";
        public static final String DISPLAY_NAME = "display_name";
        public static final String COEFFICIENT = "coefficient";
        public static final String TEXTURE_PATH = "texture_path";
        public static final String IS_OWN_ENTITY = "is_own_entity";
        /** The mob's own scaling switch: "on", "off", or blank for Default (the Scaling screen's switch). */
        public static final String SCALING = "scaling";
        /**
         * The mob's own mob griefing switch: "on", "off", or blank for Default (the game's mobGriefing rule).
         */
        public static final String MOB_GRIEFING = "mob_griefing";
        /**
         * THE MOB'S OWN PASSIVE SWITCH: "on" drops every targeting goal that hunts players; "" (Default) or
         * "off" leaves them.
         */
        public static final String PASSIVE = "passive";
        /**
         * THE MOB'S OWN CONVERSION SWITCH: "off" stops it converting - a zombie into a drowned, a villager
         * into a witch, a piglin into a zombified piglin, and any mod's.
         */
        public static final String CONVERTS = "converts";
        /** The mob's base level. */
        public static final String START_LEVEL = "start_level";
    }

    /** Variant stat overrides. */
    public static final class VariantStats {
        private VariantStats() {}
        public static final String FILE = "variant_stats.csv";
        public static final String VARIANT_NAME = "variant_name";
        /**
         * WHICH SCOPE this row applies in - global, tier or biome - with {@link #SCOPE_ID} naming the tier or
         * biome.
         */
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ATTRIBUTE = "attribute";
        public static final String MULTIPLIER = "multiplier";
        public static final String ABSOLUTE = "absolute";
    }

    /** Variant abilities. */
    public static final class Abilities {
        private Abilities() {}
        public static final String FILE = "abilities.csv";
        /** The owning variant. */
        public static final String VARIANT_NAME = "variant_name";
        /**
         * WHICH SCOPE this row applies in - global, tier or biome - with {@link #SCOPE_ID} naming the tier or
         * biome.
         */
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        /**
         * THE LEGACY NAME - one of the nine the runtime knows today: sun_immune, sun_weakness, heal, effect,
         * aura_players, aura_mobs, on_hit_effect, on_hurt_effect, damage_immunity.
         */
        public static final String ABILITY = "ability";
        /**
         * WHICH OF THE SIX BEHAVIOUR CLASSES this row is: apply_effect, damage_over_time, life_steal,
         * attribute_change, explode, spawn.
         */
        public static final String EFFECT_CLASS = "effect_class";
        /** WHAT FIRES IT - the trigger this row hangs off. */
        public static final String TRIGGER = "trigger";
        /** PERCENT CHANCE the row fires when its trigger fires, 0 to 100. */
        public static final String CHANCE = "chance";
        // VARIANCE USED TO BE HERE and is gone.
        public static final String PARAMETER = "parameter";
        public static final String AMOUNT = "amount";
        public static final String INTERVAL_TICKS = "interval_ticks";
        /** TOTAL TIME the row's effect lasts, in ticks. */
        public static final String DURATION = "duration";
        public static final String RADIUS = "radius";
        /** DAMAGE PER HIT. */
        public static final String DAMAGE = "damage";
        /** WHETHER DAMAGE MAY TAKE THE LAST HEART: true or false, blank is false. */
        public static final String CAN_KILL = "can_kill";
        /** ONE ID from the game's damage type registry. */
        public static final String DAMAGE_TYPE = "damage_type";
        /** Overrides the particle the game effect already shows. */
        public static final String PARTICLE = "particle";
        /** Overrides the HUD icon the game effect already shows. */
        public static final String HUD_ICON = "hud_icon";
        /** ONE ITEM ID that ends an attribute_change when it is taken. */
        public static final String CURE = "cure";
        public static final String ENABLED = "enabled";
        /** ONE ENTITY TYPE ID the class acts on. */
        public static final String ENTITY = "entity";

        /**
         * THE CLASS COLUMNS, in file order: what a row of one of the six classes carries beyond the legacy
         * five.
         */
        public static java.util.List<String> classColumns() {
            return java.util.List.of(EFFECT_CLASS, TRIGGER, CHANCE, DAMAGE, DURATION, CAN_KILL, DAMAGE_TYPE,
                    PARTICLE, HUD_ICON, CURE, ENTITY);
        }
    }

    /** Variant equipment loadouts; one row per ITEM a slot might hold. */
    public static final class Equipment {
        private Equipment() {}
        public static final String FILE = "equipment.csv";
        public static final String VARIANT_NAME = "variant_name";
        /**
         * WHICH SCOPE this row applies in - global, tier or biome - with {@link #SCOPE_ID} naming the tier or
         * biome.
         */
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String SLOT = "slot";
        public static final String ITEM = "item";
        public static final String CHANCE_PCT = "chance_pct";
        /** "add" (default) or "deny". */
        public static final String MODE = "mode";
        /**
         * THE REST OF A PIECE: its chance on Hard (blank = chance_pct), its chance to drop on death, and its
         * chance to be enchanted at spawn.
         */
        public static final String CHANCE_HARD = "chance_hard";
        public static final String DROP_PCT = "drop_pct";
        public static final String ENCHANT_PCT = "enchant_pct";
        /** EACH PIECE ROLLS ALONE. */
        public static final String LOCAL_DIFFICULTY = "local_difficulty";
        /**
         * THE PLAYER'S EXPERIENCE LEVEL this piece needs: blank ("no gate") or a {@link
         * com.strata.model.LevelRange} test - a number like 5, a range like 3-10, or a minimum like 5+.
         */
        public static final String PLAYER_LEVEL = "player_level";
    }

    /**
     * A mob's own AI list: one row per goal, keyed like the equipment on the variant instance (a plain mob's
     * on its default variant).
     */
    public static final class MobGoals {
        private MobGoals() {}
        public static final String FILE = "mob_goals.csv";
        public static final String VARIANT_NAME = "variant_name";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        /** The row's place in the list, from 0. */
        public static final String ROW = "row";
        /** who (target selector) or how (goal selector). */
        public static final String LIST = "list";
        public static final String PRIORITY = "priority";
        /** A GoalType's simple name (built), or a class name (game). */
        public static final String GOAL = "goal";
        /** built or game. */
        public static final String KIND = "kind";
        /** "key=value;key=value", GoalType keys. */
        public static final String SETTINGS = "settings";
        public static final String CONDITION = "condition";
        public static final String CONDITION_VALUE = "condition_value";
        public static final String ENABLED = "enabled";
    }

    /**
     * Villager and wandering trader trades: one row per edit of a trade list (a profession's level, or the
     * wandering trader's common (level 1) or rare (level 2) list), in a scope.
     */
    public static final class Trades {
        private Trades() {}
        public static final String FILE = "trades.csv";
        /** The profession id (minecraft:farmer), or minecraft:wandering_trader. */
        public static final String PROFESSION = "profession";
        public static final String LEVEL = "level";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ROW = "row";
        /** edit, remove, or add. */
        public static final String MODE = "mode";
        public static final String COST_A = "cost_a";
        public static final String COST_A_COUNT = "cost_a_count";
        public static final String COST_B = "cost_b";
        public static final String COST_B_COUNT = "cost_b_count";
        public static final String RESULT = "result";
        public static final String RESULT_COUNT = "result_count";
        public static final String MAX_USES = "max_uses";
        public static final String XP = "xp";
        public static final String PRICE_MULTIPLIER = "price_multiplier";
    }

    /**
     * The special spawners' values by scope: one row per value set, model.SpecialSpawners names each (spawner
     * and key).
     */
    /** ENTITY REPLACEMENT. */
    /** DISABLED BLOCKS AND ITEMS. */
    public static final class Disabled {
        private Disabled() {}
        public static final String FILE = "disabled.csv";
        /** block or item. */
        public static final String KIND = "kind";
        public static final String ID = "id";
    }

    public static final class Replacements {
        private Replacements() {}
        public static final String FILE = "replacements.csv";
        /** global, tier, or biome. */
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String FROM_ENTITY = "from_entity";
        public static final String TO_ENTITY = "to_entity";
        /** How many of the from-mob become the to-mob, 1 to 100. */
        public static final String SHARE_PCT = "share_pct";
        /** natural (blank): the biome's own spawns. */
        public static final String SOURCES = "sources";
    }

    /**
     * Block replacements in newly generated terrain: where a scope would generate from_block, it generates
     * to_block, or nothing (to_block disabled).
     */
    public static final class BlockReplacements {
        private BlockReplacements() {}
        public static final String FILE = "block_replacements.csv";
        /** global, tier, or biome. */
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String FROM_BLOCK = "from_block";
        /** A block id, or "disabled": the block is taken out (air). */
        public static final String TO_BLOCK = "to_block";
    }

    /** A tier's or a biome's spawn rules: one row per rule set. */
    public static final class AreaRules {
        private AreaRules() {}
        public static final String FILE = "area_rules.csv";
        /** tier or biome. */
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        /** One of runtime.AreaRules#RULES. */
        public static final String RULE = "rule";
        /** true or false. */
        public static final String VALUE = "value";
    }

    public static final class SpecialSpawns {
        private SpecialSpawns() {}
        public static final String FILE = "special_spawns.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        /** phantoms, patrols, sieges, wandering_trader, cats, or villagers. */
        public static final String SPAWNER = "spawner";
        public static final String SETTING = "setting";
        public static final String VALUE = "value";
    }
    /**
     * The goal set each mob instance runs: one row per instance, keyed like the equipment on the variant
     * instance (a plain mob's on its default variant).
     */
    public static final class MobGoalSets {
        private MobGoalSets() {}
        public static final String FILE = "mob_goal_sets.csv";
        public static final String VARIANT_NAME = "variant_name";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String GOAL_SET = "goal_set";
    }

    /** The EDIT LAYER for the extracted mob values: one row per value the user changed. */
    public static final class MobData {
        private MobData() {}
        public static final String FILE = "mob_data.csv";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String GROUP = "group";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }
    /** The user's mob caps: one row per changed value of a mob category. */
    /** The world's stages: the designer's ladder of progress, one row per stage, in order. */
    public static final class WorldStages {
        private WorldStages() {}
        public static final String FILE = "world_stages.csv";
        /** 1, 2, 3... in order; a stage 0 row holds the start's offset and coefficient, before any stage. */
        public static final String STAGE = "stage";
        public static final String NAME = "name";
        /** any: one player meeting a condition counts. all: every online player must. */
        public static final String PLAYERS = "players";
        /** all: every condition of the stage must be met. any: one is enough. */
        public static final String CONDITIONS = "conditions";
        /** Added to the level mobs get at spawn while this is the current stage. */
        public static final String MOB_LEVEL_OFFSET = "mob_level_offset";
        /** World level per XP level of the most advanced player, while this is the current stage: 0.25 is one per four. */
        public static final String PLAYER_LEVEL_COEFFICIENT = "player_level_coefficient";
        /** How the offset and coefficient reach a spawn level: add, subtract or multiply (runtime.WorldLevel#MODES). */
        public static final String MODE = "mode";
        /** keep: a reached stage stays. while_met: it is held only while its conditions are met now (runtime.WorldLevel#HOLDS). */
        public static final String HOLD = "hold";
        /** On the start row: which player figure the coefficients multiply (runtime.WorldLevel#LEVEL_SOURCES). */
        public static final String LEVEL_SOURCE = "level_source";
    }

    /** A stage's conditions and locks, one per row (runtime.WorldLevel#KINDS). */
    public static final class WorldStageRules {
        private WorldStageRules() {}
        public static final String FILE = "world_stage_rules.csv";
        public static final String STAGE = "stage";
        public static final String KIND = "kind";
        public static final String VALUE = "value";
        /** lock_tier, lock_biome: the effect given there before the stage, "effect_id amplifier". */
        public static final String EXTRA = "extra";
    }

    public static final class MobCaps {
        private MobCaps() {}
        public static final String FILE = "mob_caps.csv";
        public static final String CATEGORY = "category";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }

    /**
     * The user's item stats: one row per changed value of an item, by the game's component name
     * (model.ItemStats).
     */
    public static final class ItemStats {
        private ItemStats() {}
        public static final String FILE = "item_stats.csv";
        public static final String ITEM = "item";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }

    /** The user's block stats: one row per changed value of a block (model.BlockStats keys). */
    public static final class BlockStats {
        private BlockStats() {}
        public static final String FILE = "block_stats.csv";
        public static final String BLOCK = "block";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }

    /** The designer's block variants: a name and its base block. */
    public static final class BlockVariants {
        private BlockVariants() {}
        public static final String FILE = "block_variants.csv";
        public static final String NAME = "name";
        public static final String BASE_BLOCK = "base_block";
    }

    /** A block variant's own values: display_name or a block stats key. */
    public static final class BlockVariantValues {
        private BlockVariantValues() {}
        public static final String FILE = "block_variant_values.csv";
        public static final String VARIANT = "variant";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }

    /** The designer's projectiles: a name, the projectile it is fired as, and how it flies and hits. */
    public static final class Projectiles {
        private Projectiles() {}
        public static final String FILE = "projectiles.csv";
        public static final String NAME = com.strata.model.ProjectilePreset.NAME_COLUMN;
        public static final String BASE = com.strata.model.ProjectilePreset.BASE;
        public static final String SPEED = com.strata.model.ProjectilePreset.SPEED;
        public static final String GRAVITY = com.strata.model.ProjectilePreset.GRAVITY;
        public static final String DAMAGE = com.strata.model.ProjectilePreset.DAMAGE;
        public static final String EXPLOSION = com.strata.model.ProjectilePreset.EXPLOSION;
        public static final String FIRE_SECONDS = com.strata.model.ProjectilePreset.FIRE_SECONDS;
        public static final String KEEP_BASE = com.strata.model.ProjectilePreset.KEEP_BASE;
        public static final String LOOK = com.strata.model.ProjectilePreset.LOOK;
        public static final String TEXTURE = com.strata.model.ProjectilePreset.TEXTURE;
        public static final String ENABLED = com.strata.model.ProjectilePreset.ENABLED;

        public static java.util.List<String> columns() {
            return java.util.List.of(NAME, BASE, SPEED, GRAVITY, DAMAGE, EXPLOSION, FIRE_SECONDS, KEEP_BASE, LOOK, TEXTURE, ENABLED);
        }
    }

    /** The designer's structures: a structure file and where and how often the world builds it. */
    public static final class CustomStructures {
        private CustomStructures() {}
        public static final String FILE = "custom_structures.csv";
        public static final String NAME = com.strata.model.CustomStructure.NAME;
        public static final String BIOMES = com.strata.model.CustomStructure.BIOMES;
        public static final String SPACING = com.strata.model.CustomStructure.SPACING;
        public static final String SEPARATION = com.strata.model.CustomStructure.SEPARATION;
        public static final String STEP = com.strata.model.CustomStructure.STEP;
        public static final String TERRAIN = com.strata.model.CustomStructure.TERRAIN;
        public static final String HEIGHT = com.strata.model.CustomStructure.HEIGHT;
        public static final String ENABLED = com.strata.model.CustomStructure.ENABLED;

        public static java.util.List<String> columns() {
            return java.util.List.of(NAME, BIOMES, SPACING, SEPARATION, STEP, TERRAIN, HEIGHT, ENABLED);
        }
    }

    /**
     * A projectile's abilities: the abilities.csv columns, keyed by the projectile, run on impact - on what
     * it hits, from whoever fired it.
     */
    public static final class ProjectileEffects {
        private ProjectileEffects() {}
        public static final String FILE = "projectile_effects.csv";
        public static final String PROJECTILE = "projectile";
    }

    /**
     * A block variant's abilities: the abilities.csv columns, keyed by the variant, run on whoever stands on
     * or in it.
     */
    public static final class BlockVariantEffects {
        private BlockVariantEffects() {}
        public static final String FILE = "block_variant_effects.csv";
        public static final String VARIANT = "variant";
        public static final String ABILITY = "ability";
        public static final String PARAMETER = "parameter";
        public static final String AMOUNT = "amount";
        public static final String INTERVAL_TICKS = "interval_ticks";
        public static final String RADIUS = "radius";
        public static final String ENABLED = "enabled";
    }

    /**
     * The designer's crafting recipes: one row per recipe, its nine cells s0 to s8 (left to right, top to
     * bottom; an item id or blank).
     */
    public static final class Recipes {
        private Recipes() {}
        public static final String FILE = "recipes.csv";
        public static final String NAME = "name";
        public static final String TYPE = "type";
        public static final String RESULT = "result";
        public static final String COUNT = "count";
        /** The cell columns, s0 to s8. */
        public static String cell(int i) {
            return "s" + i;
        }
    }

    /** The designer's item variants: a name and its base item. */
    public static final class ItemVariants {
        private ItemVariants() {}
        public static final String FILE = "item_variants.csv";
        public static final String NAME = "name";
        public static final String BASE_ITEM = "base_item";
    }

    /**
     * An item variant's own values: one row per value it sets (a look key, an ItemStats key, or
     * "enchantment|id").
     */
    public static final class ItemVariantValues {
        private ItemVariantValues() {}
        public static final String FILE = "item_variant_values.csv";
        public static final String VARIANT = "variant";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }

    /**
     * An item variant's effects: the abilities.csv columns, keyed by the variant, run on whoever wears or
     * holds it.
     */
    public static final class ItemVariantEffects {
        private ItemVariantEffects() {}
        public static final String FILE = "item_variant_effects.csv";
        public static final String VARIANT = "variant";
        public static final String ABILITY = "ability";
        public static final String PARAMETER = "parameter";
        public static final String AMOUNT = "amount";
        public static final String INTERVAL_TICKS = "interval_ticks";
        public static final String RADIUS = "radius";
        public static final String ENABLED = "enabled";
    }

    /**
     * The user's loot table values: one row per changed value of a game loot table, by its path in the
     * table's JSON.
     */
    public static final class LootTables {
        private LootTables() {}
        public static final String FILE = "loot_tables.csv";
        public static final String TABLE = "table";
        public static final String PATH = "path";
        public static final String VALUE = "value";
    }

    /**
     * The user's biome values: a biome's creature probability (group biome) and its spawn costs (group cost,
     * key entity|charge or entity|energy_budget).
     */
    public static final class BiomeData {
        private BiomeData() {}
        public static final String FILE = "biome_data.csv";
        public static final String BIOME = "biome";
        public static final String GROUP = "group";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }

    /** Behaviors: each is a list of the game's own AI goals, one row per goal. */
    public static final class GoalSets {
        private GoalSets() {}
        public static final String FILE = "goal_sets.csv";
        /** The archetype's name (a behavior is an archetype, a whole AI). */
        public static final String GOAL_SET = "goal_set";
        /** The row's place in its archetype, from 0. */
        public static final String ROW = "row";
        // The goal cells are mob_goals.csv's (GoalTable): the same names.
        public static final String LIST = MobGoals.LIST;
        public static final String PRIORITY = MobGoals.PRIORITY;
        public static final String GOAL = MobGoals.GOAL;
        public static final String KIND = MobGoals.KIND;
        public static final String SETTINGS = MobGoals.SETTINGS;
        public static final String CONDITION = MobGoals.CONDITION;
        public static final String CONDITION_VALUE = MobGoals.CONDITION_VALUE;
        public static final String ENABLED = MobGoals.ENABLED;
    }

    /** Loot table overrides, layered global -> tier -> biome. */
    public static final class Loot {
        private Loot() {}
        public static final String FILE = "loot.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        /** WHICH VARIANT this row belongs to, or blank for the base mob. */
        public static final String VARIANT_NAME = "variant_name";
        public static final String MODE = "mode";
        public static final String POOL_INDEX = "pool_index";
        public static final String ENTRY_INDEX = "entry_index";
        public static final String ITEM = "item";
        public static final String COUNT_MIN = "count_min";
        public static final String COUNT_MAX = "count_max";
        public static final String CHANCE_PCT = "chance_pct";
        public static final String CONDITION_TYPE = "condition_type";
        public static final String CONDITION_VALUE = "condition_value";
    }

    /** THE GROUPS A DESIGNER CAN STAMP ONTO A LIST. */
    public static final class Groups {
        private Groups() {}
        public static final String FILE = "groups.csv";
        /** The owner key the group's rows are filed under - the column above it in the kind's table. */
        public static final String GROUP_ID = "group_id";
        /** What the designer calls the group, as typed, in any case. */
        public static final String GROUP_NAME = "group_name";
        /** Which kind of list the group fills: abilities, loot, or equipment. */
        public static final String KIND = "kind";
    }

    /** Optional sound overrides per variant/entity (ResourceLocation only; relative to Strata assets). */
    public static final class MobSounds {
        private MobSounds() {}
        public static final String FILE = "mob_sounds.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String AMBIENT_SOUND = "ambient_sound";
        public static final String HURT_SOUND = "hurt_sound";
        public static final String DEATH_SOUND = "death_sound";
        public static final String STEP_SOUND = "step_sound";
        public static final String ATTACK_SOUND = "attack_sound";
        public static final String SWIM_SOUND = "swim_sound";
        public static final String FALL_SOUND = "fall_sound";
        public static final String AMBIENT_VOLUME = "ambient_volume";
        public static final String AMBIENT_PITCH = "ambient_pitch";
        public static final String HURT_VOLUME = "hurt_volume";
        public static final String HURT_PITCH = "hurt_pitch";
        public static final String DEATH_VOLUME = "death_volume";
        public static final String DEATH_PITCH = "death_pitch";
        public static final String STEP_VOLUME = "step_volume";
        public static final String STEP_PITCH = "step_pitch";
        public static final String ENABLED = "enabled";
    }

    /** Optional visuals/texture/model overrides per variant (ResourceLocation only). */
    public static final class MobVisuals {
        private MobVisuals() {}
        public static final String FILE = "mob_visuals.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String TEXTURE = "texture";
        public static final String MODEL = "model";
        public static final String GLOW_TEXTURE = "glow_texture";
        public static final String EMISSIVE = "emissive";
        public static final String TINT = "tint";
        public static final String SHADOW_RADIUS = "shadow_radius";
        public static final String SCALE_VISUAL = "scale_visual";
        public static final String SILENT = "silent";
        public static final String GLOWING = "glowing";
        /** Whether a swapped model DROPS the base mob's own particles. */
        public static final String SUPPRESS_BASE_PARTICLES = "suppress_base_particles";
        // It sits AFTER enabled, not before it, and that is a Migrations question rather than a taste one:
        // addColumn appends at the end (TableWriter line 778), so a profile the migration grows ends up
        // "...,enabled,suppress_base_particles".
        public static final String ENABLED = "enabled";
    }

    /** Optional physics/hitbox overrides per variant. */
    public static final class MobPhysics {
        private MobPhysics() {}
        public static final String FILE = "mob_physics.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String WIDTH = "width";
        public static final String HEIGHT = "height";
        public static final String EYE_HEIGHT = "eye_height";
        public static final String NO_GRAVITY = "no_gravity";
        public static final String ENABLED = "enabled";
    }

    /** Optional flags per variant (class-filtered). */
    public static final class MobFlags {
        private MobFlags() {}
        public static final String FILE = "mob_flags.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String BURN_IN_DAYLIGHT = "burn_in_daylight";
        public static final String FIRE_IMMUNE = "fire_immune";
        public static final String LAVA_IMMUNE = "lava_immune";
        public static final String WATER_IMMUNE = "water_immune";
        public static final String DROWNING_IMMUNE = "drowning_immune";
        public static final String FALL_DAMAGE_IMMUNE = "fall_damage_immune";
        public static final String SUFFOCATION_IMMUNE = "suffocation_immune";
        public static final String CAN_PICK_UP_ITEMS = "can_pick_up_items";
        public static final String PERSISTENT = "persistent";
        public static final String NO_DESPAWN = "no_despawn";
        public static final String CAN_CLIMB = "can_climb";
        public static final String CAN_WALK_ON_WATER = "can_walk_on_water";
        public static final String FLYING = "flying";
        public static final String SWIMMING = "swimming";
        /** Shows the game's boss health bar to players near it; its settings are MobBoss. */
        public static final String BOSS = "boss";
        /** Comma-separated scoreboard tags added to the mob. */
        public static final String SCOREBOARD_TAGS = "scoreboard_tags";
        public static final String ENABLED = "enabled";
    }

    /** A mob's cost to walk each terrain type, one column per PathType. */
    public static final class MobPathfinding {
        private MobPathfinding() {}
        public static final String FILE = "mob_pathfinding.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String BLOCKED = "blocked";
        public static final String OPEN = "open";
        public static final String WALKABLE = "walkable";
        public static final String WALKABLE_DOOR = "walkable_door";
        public static final String TRAPDOOR = "trapdoor";
        public static final String POWDER_SNOW = "powder_snow";
        public static final String DANGER_POWDER_SNOW = "danger_powder_snow";
        public static final String FENCE = "fence";
        public static final String LAVA = "lava";
        public static final String WATER = "water";
        public static final String WATER_BORDER = "water_border";
        public static final String RAIL = "rail";
        public static final String UNPASSABLE_RAIL = "unpassable_rail";
        public static final String DANGER_FIRE = "danger_fire";
        public static final String DAMAGE_FIRE = "damage_fire";
        public static final String DANGER_OTHER = "danger_other";
        public static final String DAMAGE_OTHER = "damage_other";
        public static final String DOOR_OPEN = "door_open";
        public static final String DOOR_WOOD_CLOSED = "door_wood_closed";
        public static final String DOOR_IRON_CLOSED = "door_iron_closed";
        public static final String BREACH = "breach";
        public static final String LEAVES = "leaves";
        public static final String STICKY_HONEY = "sticky_honey";
        public static final String COCOA = "cocoa";
        public static final String DAMAGE_CAUTIOUS = "damage_cautious";
        public static final String DANGER_TRAPDOOR = "danger_trapdoor";
        public static final String ENABLED = "enabled";
    }

    /** Every net.minecraft PathType, lowercase, in the game's order: the columns of mob_pathfinding.csv. */
    public static final List<String> PATH_TYPES = List.of("blocked", "open", "walkable", "walkable_door", "trapdoor",
            "powder_snow", "danger_powder_snow", "fence", "lava", "water", "water_border", "rail", "unpassable_rail",
            "danger_fire", "damage_fire", "danger_other", "damage_other", "door_open", "door_wood_closed",
            "door_iron_closed", "breach", "leaves", "sticky_honey", "cocoa", "damage_cautious", "danger_trapdoor");

    /** The equipment slots whose drop chance a mob can set: the columns drop_chance_SLOT. */
    public static final List<String> DROP_CHANCE_SLOTS = List.of("mainhand", "offhand", "head", "chest", "legs", "feet", "body");

    /** A mob marked Boss: its health bar's look and range. */
    public static final class MobBoss {
        private MobBoss() {}
        public static final String FILE = "mob_boss.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String BAR_COLOR = "bar_color";
        public static final String BAR_STYLE = "bar_style";
        public static final String BAR_NAME = "bar_name";
        public static final String BAR_RANGE = "bar_range";
        public static final String DARKEN_SKY = "darken_sky";
        public static final String FOG = "fog";
        public static final String BOSS_MUSIC = "boss_music";
        public static final String NEVER_DESPAWN = "never_despawn";
        public static final String ENABLED = "enabled";
    }

    /** BossEvent.BossBarColor names, the bar_color dropdown. */
    public static final List<String> BOSS_COLORS = List.of("pink", "blue", "red", "green", "yellow", "purple", "white");
    /** BossEvent.BossBarOverlay names, the bar_style dropdown. */
    public static final List<String> BOSS_STYLES = List.of("progress", "notched_6", "notched_10", "notched_12", "notched_20");
    public static final String BOSS_DEFAULT_COLOR = "purple";
    public static final String BOSS_DEFAULT_STYLE = "progress";
    /** Players within this many blocks see the bar when bar_range is blank. */
    public static final double BOSS_DEFAULT_RANGE = 64.0;

    /** Optional attributes per variant (whitelisted by class). */
    public static final class MobAttributes {
        private MobAttributes() {}
        /** Retired its rows move to the stats tables (Migrations#moveMobAttributes). */
        public static final String RETIRED_TABLE = "mob_attributes.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String MOVEMENT_SPEED = "movement_speed";
        public static final String ATTACK_DAMAGE = "attack_damage";
        public static final String ATTACK_KNOCKBACK = "attack_knockback";
        public static final String ATTACK_SPEED = "attack_speed";
        public static final String FOLLOW_RANGE = "follow_range";
        public static final String MAX_HEALTH = "max_health";
        public static final String ARMOR = "armor";
        public static final String ARMOR_TOUGHNESS = "armor_toughness";
        public static final String KNOCKBACK_RESISTANCE = "knockback_resistance";
        public static final String FLYING_SPEED = "flying_speed";
        public static final String WATER_MOVEMENT_SPEED = "water_movement_speed";
        public static final String ATTACK_RANGE = "attack_range";
        public static final String ENABLED = "enabled";
    }

    /** Optional combat overrides per variant. */
    public static final class MobCombat {
        private MobCombat() {}
        public static final String FILE = "mob_combat.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String ATTACK_COOLDOWN = "attack_cooldown";
        public static final String REACH = "reach";
        public static final String AOE_RADIUS = "aoe_radius";
        public static final String PROJECTILE_TYPE = "projectile_type";
        public static final String PROJECTILE_SPEED = "projectile_speed";
        public static final String PROJECTILE_DAMAGE = "projectile_damage";
        public static final String PROJECTILE_EXPLOSION = "projectile_explosion";
        public static final String PROJECTILE_GRAVITY = "projectile_gravity";
        public static final String RANGED_ATTACK_INTERVAL = "ranged_attack_interval";
        public static final String RANGED_ATTACK_RANGE = "ranged_attack_range";
        public static final String DAMAGE_IMMUNITIES = "damage_immunities";
        public static final String ENABLED = "enabled";
    }

    /** Optional particle overrides per variant (client-side). */
    public static final class MobParticles {
        private MobParticles() {}
        public static final String FILE = "mob_particles.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String PARTICLE_TYPE = "particle_type";
        public static final String PARTICLE_ENABLED = "particle_enabled";
        public static final String AMBIENT_ENABLED = "ambient_enabled";
        public static final String HURT_ENABLED = "hurt_enabled";
        public static final String DEATH_ENABLED = "death_enabled";
        public static final String ATTACK_ENABLED = "attack_enabled";
        public static final String SPAWN_ENABLED = "spawn_enabled";
        public static final String COUNT = "count";
        public static final String FREQUENCY_TICKS = "frequency_ticks";
        public static final String OFFSET_X = "offset_x";
        public static final String OFFSET_Y = "offset_y";
        public static final String OFFSET_Z = "offset_z";
        public static final String VELOCITY_X = "velocity_x";
        public static final String VELOCITY_Y = "velocity_y";
        public static final String VELOCITY_Z = "velocity_z";
        public static final String ENABLED = "enabled";
    }

    /** Optional loot/XP overrides per variant. */
    public static final class MobLootXp {
        private MobLootXp() {}
        public static final String FILE = "mob_loot_xp.csv";
        public static final String SCOPE = "scope";
        public static final String SCOPE_ID = "scope_id";
        public static final String ENTITY = "entity";
        public static final String VARIANT_NAME = "variant_name";
        public static final String IS_VARIANT = "is_variant";
        public static final String DEATH_LOOT_TABLE = "death_loot_table";
        public static final String XP_MIN = "xp_min";
        public static final String XP_MAX = "xp_max";
        public static final String ALWAYS_DROP_EQUIPMENT = "always_drop_equipment";
        /** Chance (0 to 1; above 1 = always, undamaged) each equipment slot's item drops. */
        public static final String DROP_CHANCE_MAINHAND = "drop_chance_mainhand";
        public static final String DROP_CHANCE_OFFHAND = "drop_chance_offhand";
        public static final String DROP_CHANCE_HEAD = "drop_chance_head";
        public static final String DROP_CHANCE_CHEST = "drop_chance_chest";
        public static final String DROP_CHANCE_LEGS = "drop_chance_legs";
        public static final String DROP_CHANCE_FEET = "drop_chance_feet";
        public static final String DROP_CHANCE_BODY = "drop_chance_body";
        public static final String ENABLED = "enabled";
    }

    // ================================================================== THE EIGHT PROPERTY GROUPS, DECLARED
    // ONCE ==================================================================

    /**
     * WHAT A PROPERTY COLUMN HOLDS, so the editor picks its own widget and its own check without a screen
     * knowing which group it is drawing.
     */
    public enum Kind {
        /** A namespaced id the game has: a sound, a texture, a particle, a loot table. */
        RESOURCE,
        /** True or false. */
        FLAG,
        /** A whole number. */
        NUMBER,
        /** A number that may be fractional. */
        DECIMAL,
        /** Free text: a hex colour, a comma list of damage types. */
        TEXT,
        /** One word of a fixed set, picked from a dropdown (the Prop's choices). */
        CHOICE
    }

    /**
     * ONE EDITABLE PROPERTY: which group it belongs to, which column of that group's table holds it, what it
     * holds, and what the editor calls it.
     *
     * @param group the game-layer group ("sounds", "physics"), which is also the sheet's group and picks the
     *     owning table
     * @param column the column in that table
     * @param kind what the value holds, and so what widget it gets
     * @param label the words the editor shows over the field
     */
    public record Prop(String group, String column, Kind kind, String label, List<String> choices) {
        public Prop(String group, String column, Kind kind, String label) {
            this(group, column, kind, label, List.of());
        }

        /** The sheet's key for this property: the group and the column, joined. */
        public String sheetKey() {
            return group + "|" + column;
        }
    }

    /** THE KEY COLUMNS EVERY PROPERTY TABLE SHARES. */
    public static final List<String> MOB_PROPERTY_KEY_COLUMNS =
            List.of(MobSounds.SCOPE, MobSounds.SCOPE_ID, MobSounds.ENTITY, MobSounds.VARIANT_NAME,
                    MobSounds.IS_VARIANT);

    /** WHICH TABLE OWNS A GROUP. */
    public static final Map<String, String> MOB_PROPERTY_FILES = Map.of(
            "pathfinding", MobPathfinding.FILE,
            "sounds", MobSounds.FILE,
            "visuals", MobVisuals.FILE,
            "physics", MobPhysics.FILE,
            "flags", MobFlags.FILE,
            "combat", MobCombat.FILE,
            "particles", MobParticles.FILE,
            "loot_xp", MobLootXp.FILE,
            "boss", MobBoss.FILE);

    /** EVERY PROPERTY, GROUP BY GROUP, in the order the editor shows them. */
    public static final List<Prop> MOB_PROPERTIES = buildMobProperties();

    /** Every property's sheet key, so a lookup does not scan the list. */
    public static final Map<String, Prop> MOB_PROPERTY_BY_KEY = buildMobPropertyIndex();

    private static List<Prop> buildMobProperties() {
        List<Prop> out = new ArrayList<>();
        // ---- sounds: the game's own seven slots, each with its volume and pitch.
        out.add(new Prop("sounds", MobSounds.AMBIENT_SOUND, Kind.RESOURCE, "Ambient sound"));
        out.add(new Prop("sounds", MobSounds.HURT_SOUND, Kind.RESOURCE, "Hurt sound"));
        out.add(new Prop("sounds", MobSounds.DEATH_SOUND, Kind.RESOURCE, "Death sound"));
        out.add(new Prop("sounds", MobSounds.STEP_SOUND, Kind.RESOURCE, "Step sound"));
        out.add(new Prop("sounds", MobSounds.ATTACK_SOUND, Kind.RESOURCE, "Attack sound"));
        out.add(new Prop("sounds", MobSounds.SWIM_SOUND, Kind.RESOURCE, "Swim sound"));
        out.add(new Prop("sounds", MobSounds.FALL_SOUND, Kind.RESOURCE, "Fall sound"));
        out.add(new Prop("sounds", MobSounds.AMBIENT_VOLUME, Kind.DECIMAL, "Ambient volume"));
        out.add(new Prop("sounds", MobSounds.AMBIENT_PITCH, Kind.DECIMAL, "Ambient pitch"));
        out.add(new Prop("sounds", MobSounds.HURT_VOLUME, Kind.DECIMAL, "Hurt volume"));
        out.add(new Prop("sounds", MobSounds.HURT_PITCH, Kind.DECIMAL, "Hurt pitch"));
        out.add(new Prop("sounds", MobSounds.DEATH_VOLUME, Kind.DECIMAL, "Death volume"));
        out.add(new Prop("sounds", MobSounds.DEATH_PITCH, Kind.DECIMAL, "Death pitch"));
        out.add(new Prop("sounds", MobSounds.STEP_VOLUME, Kind.DECIMAL, "Step volume"));
        out.add(new Prop("sounds", MobSounds.STEP_PITCH, Kind.DECIMAL, "Step pitch"));
        // ---- visuals: what it is drawn as.
        out.add(new Prop("visuals", MobVisuals.MODEL, Kind.RESOURCE, "Model"));
        out.add(new Prop("visuals", MobVisuals.TEXTURE, Kind.RESOURCE, "Texture"));
        out.add(new Prop("visuals", MobVisuals.GLOW_TEXTURE, Kind.RESOURCE, "Glowing texture"));
        out.add(new Prop("visuals", MobVisuals.EMISSIVE, Kind.FLAG, "Emissive (glows in the dark)"));
        out.add(new Prop("visuals", MobVisuals.TINT, Kind.TEXT, "Tint colour (RRGGBB)"));
        out.add(new Prop("visuals", MobVisuals.SHADOW_RADIUS, Kind.DECIMAL, "Shadow radius"));
        out.add(new Prop("visuals", MobVisuals.SCALE_VISUAL, Kind.DECIMAL, "Visual scale"));
        out.add(new Prop("visuals", MobVisuals.SILENT, Kind.FLAG, "Silent"));
        out.add(new Prop("visuals", MobVisuals.GLOWING, Kind.FLAG, "Glowing"));
        // The flag, not the absence of it: a FLAG whose blank means false is the wrong shape for a behaviour
        // that was on before the column existed.
        out.add(new Prop("visuals", MobVisuals.SUPPRESS_BASE_PARTICLES, Kind.FLAG,
                "Drops the base mob's particles"));
        // ---- physics: the hitbox.
        out.add(new Prop("physics", MobPhysics.WIDTH, Kind.DECIMAL, "Width"));
        out.add(new Prop("physics", MobPhysics.HEIGHT, Kind.DECIMAL, "Height"));
        out.add(new Prop("physics", MobPhysics.EYE_HEIGHT, Kind.DECIMAL, "Eye height"));
        out.add(new Prop("physics", MobPhysics.NO_GRAVITY, Kind.FLAG, "No gravity"));
        // ---- flags: what it can survive and what it may do.
        for (var flag : List.of(
                new String[]{MobFlags.BURN_IN_DAYLIGHT, "Burns in daylight"},
                new String[]{MobFlags.FIRE_IMMUNE, "Fire immune"},
                new String[]{MobFlags.LAVA_IMMUNE, "Lava immune"},
                new String[]{MobFlags.WATER_IMMUNE, "Water immune"},
                new String[]{MobFlags.DROWNING_IMMUNE, "Drowning immune"},
                new String[]{MobFlags.FALL_DAMAGE_IMMUNE, "Fall damage immune"},
                new String[]{MobFlags.SUFFOCATION_IMMUNE, "Suffocation immune"},
                new String[]{MobFlags.CAN_PICK_UP_ITEMS, "Can pick up items"},
                new String[]{MobFlags.PERSISTENT, "Persistent"},
                new String[]{MobFlags.NO_DESPAWN, "Never despawns"},
                new String[]{MobFlags.CAN_CLIMB, "Can climb"},
                new String[]{MobFlags.CAN_WALK_ON_WATER, "Can walk on water"},
                new String[]{MobFlags.FLYING, "Flying"},
                new String[]{MobFlags.SWIMMING, "Swimming"},
                new String[]{MobFlags.BOSS, "Boss (shows a boss health bar)"})) {
            out.add(new Prop("flags", flag[0], Kind.FLAG, flag[1]));
        }
        // Scoreboard tags: comma-separated, added to the entity.
        out.add(new Prop("flags", MobFlags.SCOREBOARD_TAGS, Kind.TEXT, "Scoreboard tags (comma-separated)"));
        // ---- pathfinding: the cost of each terrain type.
        for (String type : PATH_TYPES) {
            out.add(new Prop("pathfinding", type, Kind.DECIMAL, pathLabel(type)));
        }
        // ---- combat: how it fights.
        for (var atk : List.of(
                new String[]{MobCombat.ATTACK_COOLDOWN, "Attack cooldown", "DECIMAL"},
                new String[]{MobCombat.REACH, "Reach", "DECIMAL"},
                new String[]{MobCombat.AOE_RADIUS, "Area of effect radius", "DECIMAL"},
                new String[]{MobCombat.PROJECTILE_TYPE, "Projectile type", "RESOURCE"},
                new String[]{MobCombat.PROJECTILE_SPEED, "Projectile speed", "DECIMAL"},
                new String[]{MobCombat.PROJECTILE_DAMAGE, "Projectile damage", "DECIMAL"},
                new String[]{MobCombat.PROJECTILE_EXPLOSION, "Projectile explosion radius", "DECIMAL"},
                new String[]{MobCombat.PROJECTILE_GRAVITY, "Projectile falls", "FLAG"},
                new String[]{MobCombat.RANGED_ATTACK_INTERVAL, "Ranged attack interval", "DECIMAL"},
                new String[]{MobCombat.RANGED_ATTACK_RANGE, "Ranged attack range", "DECIMAL"},
                new String[]{MobCombat.DAMAGE_IMMUNITIES, "Damage immunities", "TEXT"})) {
            out.add(new Prop("combat", atk[0], Kind.valueOf(atk[2]), atk[1]));
        }
        // ---- particles: what it throws off.
        for (var part : List.of(
                new String[]{MobParticles.PARTICLE_TYPE, "Particle type", "RESOURCE"},
                new String[]{MobParticles.PARTICLE_ENABLED, "Particles on", "FLAG"},
                new String[]{MobParticles.AMBIENT_ENABLED, "Ambient particles", "FLAG"},
                new String[]{MobParticles.HURT_ENABLED, "Hurt particles", "FLAG"},
                new String[]{MobParticles.DEATH_ENABLED, "Death particles", "FLAG"},
                new String[]{MobParticles.ATTACK_ENABLED, "Attack particles", "FLAG"},
                new String[]{MobParticles.SPAWN_ENABLED, "Spawn particles", "FLAG"},
                new String[]{MobParticles.COUNT, "Particle count", "NUMBER"},
                new String[]{MobParticles.FREQUENCY_TICKS, "Particle frequency (ticks)", "NUMBER"},
                new String[]{MobParticles.OFFSET_X, "Offset X", "DECIMAL"},
                new String[]{MobParticles.OFFSET_Y, "Offset Y", "DECIMAL"},
                new String[]{MobParticles.OFFSET_Z, "Offset Z", "DECIMAL"},
                new String[]{MobParticles.VELOCITY_X, "Velocity X", "DECIMAL"},
                new String[]{MobParticles.VELOCITY_Y, "Velocity Y", "DECIMAL"},
                new String[]{MobParticles.VELOCITY_Z, "Velocity Z", "DECIMAL"})) {
            out.add(new Prop("particles", part[0], Kind.valueOf(part[2]), part[1]));
        }
        // ---- loot and xp: what it drops when it dies, and what it is worth.
        for (var loot : List.of(
                new String[]{MobLootXp.DEATH_LOOT_TABLE, "Death loot table", "RESOURCE"},
                new String[]{MobLootXp.XP_MIN, "XP min", "NUMBER"},
                new String[]{MobLootXp.XP_MAX, "XP max", "NUMBER"},
                new String[]{MobLootXp.ALWAYS_DROP_EQUIPMENT, "Always drops equipment", "FLAG"})) {
            out.add(new Prop("loot_xp", loot[0], Kind.valueOf(loot[2]), loot[1]));
        }
        // Per-slot drop chance.
        for (String slot : DROP_CHANCE_SLOTS) {
            out.add(new Prop("loot_xp", "drop_chance_" + slot, Kind.DECIMAL, "Drop chance: " + slot));
        }
        // ---- boss: the bar's look and reach.
        out.add(new Prop("boss", MobBoss.BAR_COLOR, Kind.CHOICE, "Bar colour", BOSS_COLORS));
        out.add(new Prop("boss", MobBoss.BAR_STYLE, Kind.CHOICE, "Bar style", BOSS_STYLES));
        out.add(new Prop("boss", MobBoss.BAR_NAME, Kind.TEXT, "Bar name"));
        out.add(new Prop("boss", MobBoss.BAR_RANGE, Kind.DECIMAL, "Bar range (blocks)"));
        out.add(new Prop("boss", MobBoss.DARKEN_SKY, Kind.FLAG, "Darkens the sky"));
        out.add(new Prop("boss", MobBoss.FOG, Kind.FLAG, "Creates fog"));
        out.add(new Prop("boss", MobBoss.BOSS_MUSIC, Kind.FLAG, "Plays boss music"));
        out.add(new Prop("boss", MobBoss.NEVER_DESPAWN, Kind.FLAG, "Never despawns"));
        return List.copyOf(out);
    }

    /** "danger_fire" as "Danger fire cost". */
    private static String pathLabel(String type) {
        String words = type.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " cost";
    }

    private static Map<String, Prop> buildMobPropertyIndex() {
        Map<String, Prop> out = new LinkedHashMap<>();
        for (Prop p : MOB_PROPERTIES) {
            out.put(p.sheetKey(), p);
        }
        return Map.copyOf(out);
    }

    /** The properties of one group, in the editor's order. */
    public static List<Prop> mobProperties(String group) {
        return MOB_PROPERTIES.stream().filter(p -> p.group().equals(group)).toList();
    }

    /**
     * The property a sheet key names, or null when the key is not one of the eight groups' -
     * "attribute|max_health" and "model|texture" are real sheet keys and are NOT here, because they live in
     * other tables.
     */
    public static Prop mobProperty(String group, String column) {
        return MOB_PROPERTY_BY_KEY.get(group + "|" + column);
    }

    /**
     * THE TABLE A PROPERTY GROUP'S SAVE GOES TO, or null when the group is not a property group and keeps the
     * path it had.
     */
    public static String mobPropertyFile(String group) {
        return MOB_PROPERTY_FILES.get(group);
    }
}
