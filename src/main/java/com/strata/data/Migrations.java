package com.strata.data;

import com.strata.Strata;

import java.util.List;
import java.util.Map;

/** Brings an older config on disk up to the current schema. */
public final class Migrations {

    private Migrations() {
    }

    /** The columns 6.35 added, so a variant can be tuned per tier and per biome instead of once globally. */
    private static final Map<String, List<String>> ADDED_COLUMNS = withEffectTables(Map.of(
            Schema.Variants.FILE,
            // : the mob's own scaling switch.: the mob's own start level, written from the mob editor.
            List.of(Schema.Variants.SCOPE, Schema.Variants.SCOPE_ID, Schema.Variants.SCALING,
                    Schema.Variants.MOB_GRIEFING, Schema.Variants.START_LEVEL,
                    Schema.Variants.PASSIVE, Schema.Variants.CONVERTS),
            Schema.Loot.FILE, List.of(Schema.Loot.VARIANT_NAME),
            // : whether Replace blocks the spawns not from its list.
            Schema.Tiers.FILE, List.of(Schema.Tiers.BLOCK_OTHER_SPAWNS),
            Schema.BiomeOverrides.FILE, List.of(Schema.BiomeOverrides.BLOCK_OTHER_SPAWNS),
            Schema.SpawnRules.FILE, List.of(Schema.SpawnRules.VARIANT_NAME),
            Schema.Equipment.FILE,
            List.of(Schema.Equipment.SCOPE, Schema.Equipment.SCOPE_ID,
                    Schema.Equipment.MODE, Schema.Equipment.CHANCE_HARD,
                    Schema.Equipment.DROP_PCT, Schema.Equipment.ENCHANT_PCT,
                    // the player XP level a piece needs (blank = no gate).
                    Schema.Equipment.PLAYER_LEVEL),
            Schema.Abilities.FILE,
            // : the effect model - a class, a trigger, a chance plus the attributes the classes read.
            List.of(Schema.Abilities.SCOPE, Schema.Abilities.SCOPE_ID,
                    Schema.Abilities.EFFECT_CLASS, Schema.Abilities.TRIGGER,
                    Schema.Abilities.CHANCE,
                    Schema.Abilities.DAMAGE, Schema.Abilities.DURATION,
                    Schema.Abilities.CAN_KILL, Schema.Abilities.DAMAGE_TYPE,
                    Schema.Abilities.PARTICLE, Schema.Abilities.HUD_ICON,
                    Schema.Abilities.CURE, Schema.Abilities.ENTITY),
            Schema.VariantStats.FILE,
            List.of(Schema.VariantStats.SCOPE, Schema.VariantStats.SCOPE_ID),
            // the written behaviour of a swapped model was to drop the base mob's own
            // particles, with no way to turn it off.
            Schema.MobVisuals.FILE, List.of(Schema.MobVisuals.SUPPRESS_BASE_PARTICLES),
            // mob_spawns tells seeded inventory from designer intent.
            Schema.MobSpawns.FILE, List.of(Schema.MobSpawns.ORIGIN)));

    /**
     * item and block variant effects take the six classes, so they grow abilities.csv's
     * class columns.
     */
    private static Map<String, List<String>> withEffectTables(Map<String, List<String>> base) {
        Map<String, List<String>> out = new java.util.HashMap<>(base);
        out.put(Schema.ItemVariantEffects.FILE, Schema.Abilities.classColumns());
        out.put(Schema.BlockVariantEffects.FILE, Schema.Abilities.classColumns());
        out.put(Schema.ProjectileEffects.FILE, Schema.Abilities.classColumns());
        //, the Boss flag grows into existing mob_flags.csv files., scoreboard tags,
        // and the seven per-slot drop chances.
        out.put(Schema.MobFlags.FILE, List.of(Schema.MobFlags.BOSS, Schema.MobFlags.SCOREBOARD_TAGS));
        out.put(Schema.MobLootXp.FILE, List.of(Schema.MobLootXp.DROP_CHANCE_MAINHAND, Schema.MobLootXp.DROP_CHANCE_OFFHAND,
                Schema.MobLootXp.DROP_CHANCE_HEAD, Schema.MobLootXp.DROP_CHANCE_CHEST, Schema.MobLootXp.DROP_CHANCE_LEGS,
                Schema.MobLootXp.DROP_CHANCE_FEET, Schema.MobLootXp.DROP_CHANCE_BODY));
        return Map.copyOf(out);
    }

    /** Adds any column the current schema expects and the file on disk lacks. */
    public static void run() {
        moveMobAttributes(Paths.ROOT);
        for (var entry : ADDED_COLUMNS.entrySet()) {
            for (String column : entry.getValue()) {
                try {
                    // addColumn answers "the column is there now", so a present one is not news.
                    if (!TableWriter.hasColumn(entry.getKey(), column) && TableWriter.addColumn(entry.getKey(), column)) {
                        Strata.LOGGER.info("Migrated {}: added column '{}'",
                                entry.getKey(), column);
                    }
                } catch (RuntimeException e) {
                    // A table that cannot be migrated still READS correctly - the missing column reads as ""
                    // - so this must not abort the reload.
                    Strata.LOGGER.error("Could not migrate {} (column '{}'): {}",
                            entry.getKey(), column, e.toString(), e);
                }
            }
        }
    }

    /**
     * MOB_ATTRIBUTES.CSV IS RETIRED: its twelve columns were the game's attributes a second time, saved and
     * never applied, while the mob data editor's Attributes holds every attribute already.
     *
     * @param root the config folder
     */
    public static void moveMobAttributes(java.nio.file.Path root) {
        moveToStats(root, Schema.MobAttributes.RETIRED_TABLE, ATTRIBUTE_OF, true);
        // The same for mob_combat.csv's melee damage and knockback, which are the attack damage and attack
        // knockback attributes; its attack range becomes its reach.
        moveToStats(root, Schema.MobCombat.FILE, Map.of("melee_damage", "minecraft:generic.attack_damage",
                "melee_knockback", "minecraft:generic.attack_knockback"), false);
        foldColumn(root.resolve(Schema.MobCombat.FILE), "attack_range", Schema.MobCombat.REACH);
        // And mob_physics.csv's step height, which is the step height attribute.
        moveToStats(root, Schema.MobPhysics.FILE, Map.of("step_height", "minecraft:generic.step_height"), false);
    }

    /**
     * Moves a property table's attribute columns to the stats tables, as {@link #moveMobAttributes} says.
     */
    private static void moveToStats(java.nio.file.Path root, String fileName, Map<String, String> columns,
                                    boolean retired) {
        java.nio.file.Path file = root.resolve(fileName);
        if (!java.nio.file.Files.isRegularFile(file)) {
            return;
        }
        int moved = 0;
        for (Map<String, String> row : CsvTable.read(file)) {
            String entity = cell(row, Schema.MobAttributes.ENTITY);
            String variant = cell(row, Schema.MobAttributes.VARIANT_NAME);
            String scope = cell(row, Schema.MobAttributes.SCOPE).toLowerCase(java.util.Locale.ROOT);
            boolean global = scope.isEmpty() || scope.equals("global");
            boolean kept = false;
            Map<String, String> cleared = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> column : columns.entrySet()) {
                String value = cell(row, column.getKey());
                if (value.isEmpty()) {
                    continue;
                }
                if (column.getValue() == null) {
                    Strata.LOGGER.warn("{}: {} {} has no game attribute; left in the file", fileName,
                            column.getKey(), value);
                    kept = true;
                } else if (variant.isEmpty() && global) {
                    TableWriter.upsert(root.resolve(Schema.MobBaseStats.FILE), Map.of(Schema.MobBaseStats.ENTITY, entity,
                            Schema.MobBaseStats.ATTRIBUTE, column.getValue()), Map.of(Schema.MobBaseStats.VALUE, value));
                    cleared.put(column.getKey(), "");
                    moved++;
                } else if (!variant.isEmpty()) {
                    TableWriter.upsert(root.resolve(Schema.VariantStats.FILE), Map.of(
                            Schema.VariantStats.VARIANT_NAME, variant, Schema.VariantStats.SCOPE, global ? "" : scope,
                            Schema.VariantStats.SCOPE_ID, global ? "" : cell(row, Schema.MobAttributes.SCOPE_ID),
                            Schema.VariantStats.ATTRIBUTE, column.getValue()), Map.of(Schema.VariantStats.ABSOLUTE, value));
                    cleared.put(column.getKey(), "");
                    moved++;
                } else {
                    Strata.LOGGER.warn("{}: {}'s {} {} row has no home (a base mob's stats have no tier or biome);"
                            + " left in the file", fileName, entity, scope, column.getKey());
                    kept = true;
                }
            }
            Map<String, String> keys = new java.util.LinkedHashMap<>();
            for (String k : List.of(Schema.MobAttributes.SCOPE, Schema.MobAttributes.SCOPE_ID,
                    Schema.MobAttributes.ENTITY, Schema.MobAttributes.VARIANT_NAME)) {
                keys.put(k, row.getOrDefault(k, ""));
            }
            if (retired && !kept) {
                TableWriter.delete(file, keys);
            } else if (!cleared.isEmpty()) {
                TableWriter.upsert(file, keys, cleared);
            }
        }
        if (moved > 0) {
            Strata.LOGGER.info("Migrated {}: {} value(s) moved to the stats tables", fileName, moved);
        }
    }

    /** A retired column's values into the column that replaces it, where that one is blank. */
    private static void foldColumn(java.nio.file.Path file, String from, String to) {
        if (!java.nio.file.Files.isRegularFile(file)) {
            return;
        }
        for (Map<String, String> row : CsvTable.read(file)) {
            String value = cell(row, from);
            if (value.isEmpty()) {
                continue;
            }
            Map<String, String> keys = new java.util.LinkedHashMap<>();
            for (String k : List.of("scope", "scope_id", "entity", "variant_name")) {
                keys.put(k, row.getOrDefault(k, ""));
            }
            Map<String, String> values = new java.util.LinkedHashMap<>();
            values.put(from, "");
            if (cell(row, to).isEmpty()) {
                values.put(to, value);
            }
            TableWriter.upsert(file, keys, values);
            Strata.LOGGER.info("Migrated {}: {} {} folded into {}", file.getFileName(), from, value, to);
        }
    }

    /** The retired columns, and the game attribute each one is. */
    private static final Map<String, String> ATTRIBUTE_OF = attributeOf();

    private static Map<String, String> attributeOf() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put(Schema.MobAttributes.MOVEMENT_SPEED, "minecraft:generic.movement_speed");
        m.put(Schema.MobAttributes.ATTACK_DAMAGE, "minecraft:generic.attack_damage");
        m.put(Schema.MobAttributes.ATTACK_KNOCKBACK, "minecraft:generic.attack_knockback");
        m.put(Schema.MobAttributes.ATTACK_SPEED, "minecraft:generic.attack_speed");
        m.put(Schema.MobAttributes.FOLLOW_RANGE, "minecraft:generic.follow_range");
        m.put(Schema.MobAttributes.MAX_HEALTH, "minecraft:generic.max_health");
        m.put(Schema.MobAttributes.ARMOR, "minecraft:generic.armor");
        m.put(Schema.MobAttributes.ARMOR_TOUGHNESS, "minecraft:generic.armor_toughness");
        m.put(Schema.MobAttributes.KNOCKBACK_RESISTANCE, "minecraft:generic.knockback_resistance");
        m.put(Schema.MobAttributes.FLYING_SPEED, "minecraft:generic.flying_speed");
        m.put(Schema.MobAttributes.WATER_MOVEMENT_SPEED, "minecraft:generic.water_movement_efficiency");
        m.put(Schema.MobAttributes.ATTACK_RANGE, null);
        return m;
    }

    private static String cell(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.strip();
    }
}
