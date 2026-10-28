package com.strata.data;

import com.strata.Strata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The CSV files Strata creates on first run. */
public final class Defaults {

    private Defaults() {}

    public static void writeIfMissing() {
        try {
            Files.createDirectories(Paths.ROOT);
            Files.createDirectories(Paths.PROFILES);
        } catch (Exception e) {
            Strata.LOGGER.error("Could not create config dirs: {}", e.toString(), e);
            return;
        }

        templates().forEach(Defaults::write);
    }

    /** Every shipped table, file name to starting content. */
    static Map<String, String> templates() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(Schema.Tiers.FILE, TIERS);
        out.put(Schema.TierBiomes.FILE, TIER_BIOMES);
        out.put(Schema.BiomeOverrides.FILE, BIOME_OVERRIDES);
        out.put(Schema.MobSpawns.FILE, MOB_SPAWNS);
        out.put(Schema.MobBaseStats.FILE, MOB_BASE_STATS);
        out.put(Schema.SpawnRules.FILE, SPAWN_RULES);
        out.put(Schema.Scaling.MOBS_FILE, SCALING_MOBS);
        out.put(Schema.Scaling.PLAYER_FILE, SCALING_PLAYER);
        out.put(Schema.ScalingFormulas.FILE, "# Your scaling formulas. formula: area_level, mob_attribute, player_attribute or mob_xp.\n"
                + "# expression: the formula. No row, or a blank one: Strata's default. Edit them in Scaling > Formulas.\n"
                + Schema.ScalingFormulas.FORMULA + "," + Schema.ScalingFormulas.EXPRESSION + "\n");
        out.put(Schema.Ores.FILE, ORES);
        out.put(Schema.Vegetation.FILE, VEGETATION);
        out.put(Schema.Variants.FILE, VARIANTS);
        out.put(Schema.VariantStats.FILE, VARIANT_STATS);
        out.put(Schema.Abilities.FILE, VARIANT_ABILITIES);
        out.put(Schema.Equipment.FILE, VARIANT_EQUIPMENT);
        // The groups a designer can stamp onto a list.
        out.put(Schema.Groups.FILE, "# Your groups: a list you can stamp onto another list instead of entering it again\n"
                + "# and again. Applying one COPIES its rows in, so you can then change either list on its own.\n"
                + "# kind: abilities, loot, or equipment. Its rows are in that kind's own file, under the group_id.\n"
                + String.join(",", Schema.Groups.GROUP_ID, Schema.Groups.GROUP_NAME, Schema.Groups.KIND) + "\n");
        out.put(Schema.Loot.FILE, LOOT_OVERRIDES);
        out.put(com.strata.domain.CustomAssetsOps.REPLACEMENTS_FILE, com.strata.domain.CustomAssetsOps.REPLACEMENTS_TEMPLATE);
        // The user's loot table values.
        out.put(Schema.LootTables.FILE, "# Your loot table values. path: the value's place in the table (game/loot.csv). value: JSON.\n"
                + "table,path,value\n");
        // The user's biome values.
        out.put(Schema.BiomeData.FILE, "# Your biome values. group biome: creature_probability. group cost: entity|charge or entity|energy_budget.\n"
                + "biome,group,key,value\n");
        // The user's mob caps.
        out.put(Schema.MobCaps.FILE, "# Your mob caps. key: max, despawn_distance, friendly, persistent. value replaces the game's.\n"
                + "category,key,value\n");
        // The user's item stats.
        out.put(Schema.ItemStats.FILE, "# Your item stats. key: the game's component name (game/items.csv). value replaces the game's at the next game start.\n"
                + "item,key,value\n");
        // The user's block stats.
        out.put(Schema.BlockStats.FILE, "# Your block stats. key: hardness, blast_resistance, requires_tool, friction, speed_factor,\n"
                + "# jump_factor, light_level, flammability, fire_spread (game/blocks.csv). value replaces the game's at the next reload.\n"
                + "block,key,value\n");
        // The designer's block variants.
        out.put(Schema.BlockVariants.FILE, "# Your block variants: a name (lower case) and its base block. Each is its own block, strata:name,\n"
                + "# added at the next game start. Its values are in block_variant_values.csv.\n"
                + "name,base_block\n");
        out.put(Schema.BlockVariantValues.FILE, "# Your block variants' own values. No row: the base block's value. key: display_name or a block stats key.\n"
                + "variant,key,value\n");
        out.put(Schema.BlockVariantEffects.FILE, "# Your block variants' abilities, run on whoever stands on or in one. The columns of abilities.csv.\n"
                + "variant,ability,parameter,amount,interval_ticks,radius,enabled,"
                + String.join(",", Schema.Abilities.classColumns()) + "\n");
        // The designer's crafting recipes.
        out.put(Schema.Recipes.FILE, "# Your crafting recipes, each strata:name. type: shaped or shapeless. s0 to s8: the 3x3 grid,\n"
                + "# left to right, top to bottom, an item id or blank. They apply at the next world load or /reload.\n"
                + "name,type,result,count,s0,s1,s2,s3,s4,s5,s6,s7,s8\n");
        // The designer's item variants.
        out.put(Schema.ItemVariants.FILE, "# Your item variants: a name (lower case) and its base item. Its values and effects are in the next two files.\n"
                + "name,base_item\n");
        out.put(Schema.ItemVariantValues.FILE, "# Your item variants' own values. No row: the base item's value. key: display_name, lore, rarity, glint,\n"
                + "# custom_model_data, unbreakable, an item stats key (item_stats.csv), or enchantment|id (value: the level).\n"
                + "variant,key,value\n");
        // The designer's structures.
        out.put(Schema.CustomStructures.FILE, "# Your structures: the file is config/strata/structures/<name>.nbt, saved by a structure block.\n"
                + "# biomes: a #tag or biome ids. spacing and separation in chunks. height: surface or a Y level.\n"
                + String.join(",", Schema.CustomStructures.columns()) + "\n");
        // The designer's projectiles.
        out.put(Schema.Projectiles.FILE, "# Your projectiles: a name (lower case), the projectile it is fired as, and how it flies and hits.\n"
                + "# Blank = the base projectile's own. gravity: 0 straight, 1 the base's, 2 twice. keep_base: the base's own impact too.\n"
                + String.join(",", Schema.Projectiles.columns()) + "\n");
        out.put(Schema.ProjectileEffects.FILE, "# Your projectiles' abilities, run on impact. The columns of abilities.csv.\n"
                + "projectile,ability,parameter,amount,interval_ticks,radius,enabled,"
                + String.join(",", Schema.Abilities.classColumns()) + "\n");
        out.put(Schema.ItemVariantEffects.FILE, "# Your item variants' effects, run on whoever wears or holds one. The columns of abilities.csv.\n"
                + "variant,ability,parameter,amount,interval_ticks,radius,enabled,"
                + String.join(",", Schema.Abilities.classColumns()) + "\n");
        // The edit layer over the extracted mob values.
        out.put(Schema.MobData.FILE, "# Your changes to the values Strata extracted from the game (config/strata/game/mobs.csv).\n"
                + "# group: type, field, or tag. A blank variant_name is the base mob. value replaces the game's.\n"
                + "entity,variant_name,scope,scope_id,group,key,value\n");
        // New property tables (created on demand, empty defaults)
        out.put(Schema.MobSounds.FILE, MOB_SOUNDS_TEMPLATE);
        out.put(Schema.MobVisuals.FILE, MOB_VISUALS_TEMPLATE);
        out.put(Schema.MobPhysics.FILE, MOB_PHYSICS_TEMPLATE);
        out.put(Schema.MobFlags.FILE, MOB_FLAGS_TEMPLATE);
        out.put(Schema.MobCombat.FILE, MOB_COMBAT_TEMPLATE);
        out.put(Schema.MobParticles.FILE, MOB_PARTICLES_TEMPLATE);
        out.put(Schema.MobLootXp.FILE, MOB_LOOT_XP_TEMPLATE);
        out.put(Schema.MobBoss.FILE, MOB_BOSS_TEMPLATE);
        out.put(Schema.MobPathfinding.FILE, MOB_PATHFINDING_TEMPLATE);
        // A mob's own AI list.
        out.put(Schema.MobGoals.FILE, "# A mob's own goal list: one row per goal. list: target or goal. kind: built or game.\n"
                + "# settings: key=value;key=value (see the editor). condition: day, night, health_below,\n"
                + "# health_above, raining, in_water, target_within.\n"
                + GoalTable.HEADER + "\n");
        // The archetype library: the mob families are added at game start, the designer's own after.
        out.put(Schema.GoalSets.FILE, "# Goal sets: a whole goal list each, one row per goal, in mob_goals.csv's row format.\n"
                + GoalSetTable.csv(Map.of()));
        // Villager and wandering trader trades.
        out.put(Schema.Trades.FILE, "# Your trade edits. The game's trades are in game/trades.csv. row: g3 is the"
                + " game's fourth trade of the list, a1 one you added. mode: edit, remove, or add. level: 1 Novice to"
                + " 5 Master; the wandering trader's 1 is its common list, 2 its rare.\n"
                + String.join(",", Schema.Trades.PROFESSION, Schema.Trades.LEVEL, Schema.Trades.SCOPE,
                Schema.Trades.SCOPE_ID, Schema.Trades.ROW, Schema.Trades.MODE, Schema.Trades.COST_A,
                Schema.Trades.COST_A_COUNT, Schema.Trades.COST_B, Schema.Trades.COST_B_COUNT, Schema.Trades.RESULT,
                Schema.Trades.RESULT_COUNT, Schema.Trades.MAX_USES, Schema.Trades.XP, Schema.Trades.PRICE_MULTIPLIER)
                + "\n");
        // The special spawners' values by scope.
        out.put(Schema.Disabled.FILE, "# Disabled blocks and items: out of the creative tabs, every recipe that makes one off,\n"
                + "# stripped from all loot, and a block cannot be placed. A disabled block is also taken out of land:\n"
                + "# new land at once, and land already made (structures included) as it loads, the next time this\n"
                + "# set changes. Any id, a mod's too.\n"
                + String.join(",", Schema.Disabled.KIND, Schema.Disabled.ID) + "\n");
        out.put(Schema.Replacements.FILE, "# Mob replacements: where a scope would spawn from_entity, it spawns to_entity.\n"
                + "# Any entity id, a mod's too. scope: global, tier, or biome. share_pct: how many become\n"
                + "# the new mob, blank 100. sources: natural (blank), or all (structures, spawners, patrols too).\n"
                + String.join(",", Schema.Replacements.SCOPE, Schema.Replacements.SCOPE_ID,
                Schema.Replacements.FROM_ENTITY, Schema.Replacements.TO_ENTITY, Schema.Replacements.SHARE_PCT,
                Schema.Replacements.SOURCES) + "\n");
        out.put(Schema.BlockReplacements.FILE, "# Block replacements: where a scope would generate from_block, it\n"
                + "# generates to_block. to_block disabled takes it out (air). scope: global, tier, or biome (biome over tier\n"
                + "# over global). A replacement block is never itself replaced. Applies to NEW land at once, and to land\n"
                + "# already made (structures included) as it loads, the next time the rules change.\n"
                + String.join(",", Schema.BlockReplacements.SCOPE, Schema.BlockReplacements.SCOPE_ID,
                Schema.BlockReplacements.FROM_BLOCK, Schema.BlockReplacements.TO_BLOCK) + "\n");
        out.put(Schema.AreaRules.FILE, "# A tier's or a biome's spawn rules (Rules on its screen). rule: block_other_spawns,\n"
                + "# monsters_in_creature_slots, monsters_in_ambient_slots, monsters_in_water_slots, monsters_outside_darkness.\n"
                + "# value: true or false. A biome's wins over its tier's; with neither, the rule is on.\n"
                + String.join(",", Schema.AreaRules.SCOPE, Schema.AreaRules.SCOPE_ID, Schema.AreaRules.RULE,
                Schema.AreaRules.VALUE) + "\n");
        out.put(Schema.SpecialSpawns.FILE, "# Your special spawner values: phantoms, patrols, sieges, the wandering"
                + " trader, cats, villagers. A value not here is the game's. scope: blank (Global), tier, or biome.\n"
                + String.join(",", Schema.SpecialSpawns.SCOPE, Schema.SpecialSpawns.SCOPE_ID,
                Schema.SpecialSpawns.SPAWNER, Schema.SpecialSpawns.SETTING, Schema.SpecialSpawns.VALUE) + "\n");
        // The goal set each mob instance runs: its own table, not a column of equipment.csv.
        out.put(Schema.MobGoalSets.FILE, "# The goal set each mob runs, one row per mob instance."
                + " goal_set: a name in goal_sets.csv, or none for the game's own goals.\n"
                + String.join(",", Schema.MobGoalSets.VARIANT_NAME, Schema.MobGoalSets.SCOPE,
                Schema.MobGoalSets.SCOPE_ID, Schema.MobGoalSets.GOAL_SET) + "\n");
        return out;
    }

    private static void write(String fileName, String content) {
        Path path = Paths.table(fileName);
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.writeString(path, content);
            Strata.LOGGER.info("Created {}", path.getFileName());
        } catch (Exception e) {
            Strata.LOGGER.error("Could not write {}: {}", path, e.toString(), e);
        }
    }

    // ---- CSV schemas ----

    private static final String TIERS = """
            # Tiers - named design templates.
            # spawn_mode: add (keep vanilla spawns) or replace (discard them)
            # coefficient: blanket multiplier for all mobs in this tier (default 1.0)
            name,level,spawn_mode,coefficient,block_other_spawns
            """;

    private static final String TIER_BIOMES = """
            # Which biomes use which tier. A biome belongs to ONE tier.
            tier,biome
            """;

    private static final String BIOME_OVERRIDES = """
            # Per-biome overrides. Only biomes that differ from their tier.
            # level: wins over the tier. 0 = explicitly vanilla.
            # scale_mobs/restrict_ores/substitute_spawns: blank = inherit global.
            # coefficient: per-biome mob coefficient override. Blank = inherit.
            biome,level,scale_mobs,restrict_ores,substitute_spawns,coefficient,block_other_spawns
            """;

    private static final String MOB_SPAWNS = """
            # Your spawn rows. scope = global, tier, or biome. The game's own are in game/spawns.csv.
            # enabled: separate from weight (can disable without losing weight value)
            # coefficient: per-mob scaling multiplier (blank = inherit from scope)
            # is_variant/variant_name: true + name for variant entries
            # variant_mode: add or replace (how variant entered the list)
            # origin: designer = chosen in the editor (seeded marks an older copy of the game's roster)
            # weight: the mob's share when the game picks among its category's mobs (not a chance).
            # spawn_chance: 0 to 100, how often it then spawns (blank = 100).
            # min_count/max_count: the group size; 0 is allowed (the group spawns nothing).
            scope,scope_id,entity,category,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode,origin,spawn_chance
            """;

    private static final String MOB_BASE_STATS = """
            # Base stats before any scaling. Ships empty — game values read live.
            entity,attribute,value
            """;

    private static final String SPAWN_RULES = """
            # Spawn rules. scope = global, tier, or biome.
            # rule_type: time, light, y_level, weather, moon_phase,
            #            structure, surface, distance, time_since_rest, world_day, player_level
            # time: day, night, dusk, dawn, or daylight / dark (the game's own day check)
            # time_since_rest: days since the nearest player slept, like 3+
            # (the phantom's, from the game's code, is seeded once).
            # Several rules on the same entity must all hold.
            # variant_name blank = the base mob; a variant with no row of its
            # own inherits the layer beneath it (global -> tier -> biome).
            scope,scope_id,entity,variant_name,rule_type,value
            """;

    private static final String SCALING_MOBS = """
            # How mob attributes grow with area level.
            # formula: final = base * (1 + per_level * (level - start_level + 1)^exponent * coefficient)
            # start_level: the first level with a bonus. Blank = 2: level 1 is the game's values.
            attribute,per_level,exponent,enabled
            minecraft:generic.max_health,0.15,0.6,true
            minecraft:generic.attack_damage,0.12,0.6,true
            minecraft:generic.knockback_resistance,0.10,0.6,true
            """;

    private static final String SCALING_PLAYER = """
            # How player attributes grow with XP level.
            # Same formula as mob scaling, start_level too (blank = 2).
            attribute,per_level,exponent,enabled
            minecraft:generic.max_health,0.04,0.6,true
            minecraft:generic.attack_damage,0.03,0.6,true
            minecraft:generic.movement_speed,0.01,0.6,true
            minecraft:player.block_break_speed,0.02,0.6,true
            """;

    private static final String ORES = """
            # Your ore rules. scope = tier or biome. The game's ores per biome are in game/spawns.csv.
            # frequency_pct: 100 = normal, 50 = half density, 0 = disabled.
            scope,scope_id,ore_id,enabled,frequency_pct
            """;

    private static final String VEGETATION = """
            # Your flora rules. scope = tier or biome. The game's plants per biome are in game/spawns.csv.
            # feature: a plant feature (a tree, a flower, grass). enabled: true adds it where the game has
            # none, false removes it. A biome's rule wins over its tier's. From the next world load.
            scope,scope_id,feature,enabled
            """;

    private static final String VARIANTS = """
            # Named mob variants. ID is immutable — other tables key on it.
            # display_name: what the player sees in-world (blank = use the id).
            # is_own_entity: true = register a real EntityType (/summon, spawn egg).
            # scope/scope_id: blank = global. The same name may also have a tier or
            # biome row - that row is its own instance and overrides the global one.
            # scaling: on or off overrides every other scaling rule; blank = Default.
            # mob_griefing: on or off overrides the game's mobGriefing rule for this mob; blank = Default.
            # start_level: the mob's base level. When scaling is on, the biome level
            # applies and the mob scales up/down from this level. When scaling is off,
            # this level is used exactly and the biome level is ignored. Blank = 1.
            name,scope,scope_id,base_entity,display_name,coefficient,texture_path,is_own_entity,scaling,mob_griefing,start_level,passive,converts
            """;

    private static final String VARIANT_STATS = """
            # Variant stat overrides. scope = blank/global, tier or biome;
            # scope_id names the tier or biome. A variant with no row in the
            # scope being resolved inherits the layer beneath it.
            # multiplier: applied BEFORE scaling (compounds with curve)
            # absolute: applied AFTER scaling (final answer, ignores curve)
            variant_name,scope,scope_id,attribute,multiplier,absolute
            """;

    private static final String VARIANT_ABILITIES = """
            # Variant abilities. One of two things in the ability column:
            # a legacy name (sun_immune, sun_weakness, heal, effect, aura_players,
            # sun_immune, sun_weakness, heal, effect, aura_players, aura_mobs,
            # damage_immunity), or a class row.
            # blank one with effect_class naming one of the six behaviour classes
            # instead:
            #   apply_effect      gives a game effect by id, with a level and a duration
            #   damage_over_time  x damage every y interval for z total time - poison,
            #                     wither, fire, bleed and dire poison are all this
            #   life_steal        the target's health moves to the attacker
            #   attribute_change  a named attribute moves by a share of its base,
            #                     or is cured by an item
            #   explode           damage in a radius
            #   spawn             a named creature is spawned, and stays alive
            # trigger names what fires the row (blank on a legacy row) and chance
            # is the percent it lands on (blank is 100).
            #
            # entity is last (the target creature of spawn).
            # The ten columns before it were added later, and they
            # are AT THE END on purpose: TableWriter grows a missing column by
            # appending it, so a profile upgraded by Migrations has these in this
            # very order and a fresh one matches it exactly. variance was an
            # eleventh and is gone - it was parsed and then read by
            # nothing, so an older profile that still carries the column keeps
            # loading unchanged and the cell is simply never looked at.
            variant_name,scope,scope_id,ability,parameter,amount,interval_ticks,radius,enabled,effect_class,trigger,chance,damage,duration,can_kill,damage_type,particle,hud_icon,cure,entity
            """;

    private static final String VARIANT_EQUIPMENT = """
            # Variant equipment loadouts. One row per ITEM a slot might hold -
            # a slot may list several (mainhand,bow + mainhand,iron_sword).
            # chance_pct is that item's WEIGHT inside its slot: one weighted
            # draw per slot picks a single winner, so two items can never be
            # equipped into the same hand. Weights under 100 leave the
            # remainder as "nothing" (bow at 30 = a 30% bow); weights summing
            # to 100 or more are pure relative odds.
            # mode: add (default) or deny. A deny row suppresses a piece of equipment
            #   the game gives the base mob; delete the deny to get it back.
            # chance_hard: the weight on Hard (blank = chance_pct).
            # drop_pct, enchant_pct: the piece's chance to drop on death and to be
            #   enchanted at spawn (blank = the game's default for the mob).
            # local_difficulty: yes = the chances are at full local difficulty and
            #   scale down with it, as the game's own armor does (blank = as typed).
            # player_level: the player's experience level this piece needs: a number
            #   like 5, a range like 3-10, or a minimum like 5+. Blank = no gate, so
            #   the piece applies as before.
            variant_name,scope,scope_id,slot,item,chance_pct,mode,chance_hard,drop_pct,enchant_pct,local_difficulty,player_level
            """;

    private static final String LOOT_OVERRIDES = """
            # Loot table overrides. scope = global, tier, or biome.
            # mode: add (extra drops) or replace (replaces vanilla loot)
            # Layered: global -> tier -> biome (biome wins)
            # variant_name blank = the base mob's drops; a variant with no row
            # of its own inherits them, so equipment set once is not re-entered.
            scope,scope_id,entity,variant_name,mode,pool_index,entry_index,item,count_min,count_max,chance_pct,condition_type,condition_value
            """;

    public static final String MOB_SOUNDS_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,ambient_sound,hurt_sound,death_sound,step_sound,attack_sound,swim_sound,fall_sound,ambient_volume,ambient_pitch,hurt_volume,hurt_pitch,death_volume,death_pitch,step_volume,step_pitch,enabled,\n";
    public static final String MOB_SOUNDS_TEMPLATE = MOB_SOUNDS_HEADER;

    public static final String MOB_VISUALS_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,texture,model,glow_texture,emissive,tint,shadow_radius,scale_visual,silent,glowing,enabled,suppress_base_particles,\n";
    public static final String MOB_VISUALS_TEMPLATE = MOB_VISUALS_HEADER;

    public static final String MOB_PHYSICS_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,width,height,eye_height,no_gravity,enabled,\n";
    public static final String MOB_PHYSICS_TEMPLATE = MOB_PHYSICS_HEADER;

    public static final String MOB_FLAGS_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,burn_in_daylight,fire_immune,lava_immune,water_immune,drowning_immune,fall_damage_immune,suffocation_immune,can_pick_up_items,persistent,no_despawn,can_climb,can_walk_on_water,flying,swimming,enabled,boss,scoreboard_tags,\n";
    public static final String MOB_FLAGS_TEMPLATE = MOB_FLAGS_HEADER;

    public static final String MOB_COMBAT_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,attack_cooldown,reach,aoe_radius,projectile_type,projectile_speed,projectile_damage,projectile_explosion,projectile_gravity,ranged_attack_interval,ranged_attack_range,damage_immunities,enabled,\n";
    public static final String MOB_COMBAT_TEMPLATE = MOB_COMBAT_HEADER;

    public static final String MOB_PARTICLES_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,particle_type,particle_enabled,ambient_enabled,hurt_enabled,death_enabled,attack_enabled,spawn_enabled,count,frequency_ticks,offset_x,offset_y,offset_z,velocity_x,velocity_y,velocity_z,enabled,\n";
    public static final String MOB_PARTICLES_TEMPLATE = MOB_PARTICLES_HEADER;

    public static final String MOB_LOOT_XP_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,death_loot_table,xp_min,xp_max,always_drop_equipment,enabled,drop_chance_mainhand,drop_chance_offhand,drop_chance_head,drop_chance_chest,drop_chance_legs,drop_chance_feet,drop_chance_body,\n";
    public static final String MOB_LOOT_XP_TEMPLATE = MOB_LOOT_XP_HEADER;

    /** Boss bars. */
    public static final String MOB_BOSS_HEADER =
            "scope,scope_id,entity,variant_name,is_variant,bar_color,bar_style,bar_name,bar_range,darken_sky,fog,boss_music,never_despawn,enabled,\n";
    public static final String MOB_BOSS_TEMPLATE = MOB_BOSS_HEADER;

    /** Terrain costs, one column per PathType. */
    public static final String MOB_PATHFINDING_HEADER =
            "scope,scope_id,entity,variant_name,is_variant," + String.join(",", Schema.PATH_TYPES) + ",enabled,\n";
    public static final String MOB_PATHFINDING_TEMPLATE = MOB_PATHFINDING_HEADER;
}
