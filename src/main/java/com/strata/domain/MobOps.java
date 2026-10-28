package com.strata.domain;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the editor can DO to a mob's spawn entry. */
public final class MobOps {

    private MobOps() {
    }

    /** Marks a spawn row as the designer's own before it is written. */
    public static Map<String, String> stampDesigner(Map<String, String> values) {
        Map<String, String> stamped = new java.util.LinkedHashMap<>(values);
        stamped.put(Schema.MobSpawns.ORIGIN, Schema.MobSpawns.Origin.DESIGNER);
        return stamped;
    }

    /** Whether a plain mob can be removed from this scope. */
    public static boolean removableFromScope(String scope, String scopeId) {
        String sc = scope == null ? "" : scope.strip();
        return ("tier".equals(sc) || "biome".equals(sc))
                && scopeId != null && !scopeId.isBlank();
    }

    /**
     * Removes a plain mob from one tier or biome: its spawn row there, and the drops and spawn conditions
     * tied to that row.
     */
    public static WriteReport removeFromScope(String scope, String scopeId, String entity) {
        if (!removableFromScope(scope, scopeId) || entity == null || entity.isBlank()) {
            return WriteReport.refused("A plain mob can be removed only from a named tier or biome");
        }
        WriteReport report = removeFromScopeInto(new WriteReport(), scope, scopeId, entity);
        return report.ok() ? Commit.reloadIfOk(report) : report;
    }

    // ---- BATCH ----

    /**
     * Which mobs a batch takes: every mob, or one group of the mob list (a spawn category, named as the list
     * names it).
     *
     * @param category the group's category, or null for every mob
     */
    public record BatchGroup(com.strata.model.SpawnCategory category, String label) {
    }

    /** The groups, in the menu's order: every mob, then each group of the list. */
    public static List<BatchGroup> batchGroups() {
        List<BatchGroup> out = new ArrayList<>();
        out.add(new BatchGroup(null, "All mobs"));
        for (com.strata.model.SpawnCategory c : com.strata.model.SpawnCategory.values()) {
            out.add(new BatchGroup(c, c.word()));
        }
        return out;
    }

    /** What a batch sets on each mob. */
    public enum BatchAction { SPAWN_ON, SPAWN_OFF, SCALING_ON, SCALING_OFF, SCALING_DEFAULT,
        MOB_GRIEFING_ON, MOB_GRIEFING_OFF, MOB_GRIEFING_DEFAULT }


    /** A mob row of the list a batch acts on, in the scope the list shows it. */
    public record BatchMob(String scope, String scopeId, String entity, String variantName,
                           com.strata.model.SpawnCategory category) {
    }

    /** The mobs of a group, in order. */
    public static List<BatchMob> inGroup(List<BatchMob> mobs, BatchGroup group) {
        List<BatchMob> out = new ArrayList<>();
        for (BatchMob m : mobs) {
            if (group.category() == null || m.category() == group.category()) {
                out.add(m);
            }
        }
        return out;
    }

    /** Sets spawning or scaling on every mob given, then reloads once. */
    public static WriteReport batch(List<BatchMob> mobs, BatchAction action) {
        if (mobs.isEmpty()) {
            return WriteReport.refused("There are no mobs of that group in this list");
        }
        WriteReport report = new WriteReport();
        for (BatchMob m : mobs) {
            if (!report.ok()) {
                break;
            }
            switch (action) {
                case SPAWN_ON -> setSpawning(report, m, true);
                case SPAWN_OFF -> setSpawning(report, m, false);
                case SCALING_ON -> setSwitch(report, m, Schema.Variants.SCALING, com.strata.runtime.MobSwitches.ON);
                case SCALING_OFF -> setSwitch(report, m, Schema.Variants.SCALING, com.strata.runtime.MobSwitches.OFF);
                case SCALING_DEFAULT -> setSwitch(report, m, Schema.Variants.SCALING, "");
                case MOB_GRIEFING_ON -> setSwitch(report, m, Schema.Variants.MOB_GRIEFING,
                        com.strata.runtime.MobSwitches.ON);
                case MOB_GRIEFING_OFF -> setSwitch(report, m, Schema.Variants.MOB_GRIEFING,
                        com.strata.runtime.MobSwitches.OFF);
                case MOB_GRIEFING_DEFAULT -> setSwitch(report, m, Schema.Variants.MOB_GRIEFING, "");
            }
        }
        return Commit.reloadIfOk(report);
    }

    private static void setSpawning(WriteReport report, BatchMob m, boolean on) {
        Map<String, String> keys = null;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (VariantOps.normalScope(m.scope()).equals(VariantOps.normalScope(row.get(Schema.MobSpawns.SCOPE)))
                    && VariantOps.normalScopeId(m.scope(), m.scopeId()).equals(
                    row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip())
                    && m.entity().equals(row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip())
                    && m.variantName().equals(row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip())) {
                keys = new LinkedHashMap<>();
                keys.put(Schema.MobSpawns.SCOPE, row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip());
                keys.put(Schema.MobSpawns.SCOPE_ID, row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip());
                keys.put(Schema.MobSpawns.ENTITY, m.entity());
                keys.put(Schema.MobSpawns.VARIANT_NAME, m.variantName());
                break;
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (keys == null) {
            keys = new LinkedHashMap<>();
            keys.put(Schema.MobSpawns.SCOPE, m.scope());
            keys.put(Schema.MobSpawns.SCOPE_ID, VariantOps.normalScopeId(m.scope(), m.scopeId()));
            keys.put(Schema.MobSpawns.ENTITY, m.entity());
            keys.put(Schema.MobSpawns.VARIANT_NAME, m.variantName());
            VariantOps.StartValues start = VariantOps.startFor(m.entity());
            values.put(Schema.MobSpawns.CATEGORY, com.strata.game.EntityCategory.of(m.entity()).id());
            values.put(Schema.MobSpawns.WEIGHT, start.weight());
            values.put(Schema.MobSpawns.MIN_COUNT, start.minCount());
            values.put(Schema.MobSpawns.MAX_COUNT, start.maxCount());
            values.put(Schema.MobSpawns.SPAWN_CHANCE, start.spawnChance());
            values.put(Schema.MobSpawns.IS_VARIANT, String.valueOf(!m.variantName().isEmpty()));
            values.put(Schema.MobSpawns.VARIANT_MODE, m.variantName().isEmpty() ? "" : "add");
        }
        values.put(Schema.MobSpawns.ENABLED, String.valueOf(on));
        report.record("the spawn row of " + m.entity(),
                TableWriter.upsert(Schema.MobSpawns.FILE, keys, stampDesigner(values)));
    }

    /** One switch column (Scaling, Mob griefing) on the mob's own variants.csv row in the scope. */
    private static void setSwitch(WriteReport report, BatchMob m, String column, String value) {
        setVariantColumn(report, m, column, value);
    }

    /** One variants.csv column on the mob's own row (a plain mob's default variant), created if missing. */
    private static void setVariantColumn(WriteReport report, BatchMob m, String column, String value) {
        String owner = m.variantName().isEmpty() ? com.strata.model.Variant.defaultNameFor(m.entity())
                : m.variantName();
        if (value.equals(VariantOps.storedCell(column, owner, m.scope(), m.scopeId()))) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (!VariantOps.hasDefinitionRow(owner, m.scope(), m.scopeId())) {
            com.strata.model.Variant v = Config.tables().variants().get(owner);
            values.put(Schema.Variants.BASE_ENTITY, v == null ? m.entity() : v.baseEntity());
            if (v != null) {
                values.put(Schema.Variants.DISPLAY_NAME, v.displayName().equals(v.id()) ? "" : v.displayName());
                values.put(Schema.Variants.COEFFICIENT, String.valueOf(v.coefficient()));
                values.put(Schema.Variants.TEXTURE_PATH, v.texture());
                values.put(Schema.Variants.IS_OWN_ENTITY, String.valueOf(v.isOwnEntity()));
            }
        }
        values.put(column, value);
        for (String failure : VariantOps.saveDefinition(m.scope(), m.scopeId(), owner, values).failures()) {
            report.record(failure, false);
        }
    }

    /** Sets one variants.csv column (the mob's own variant row) on every mob, then reloads once. */
    public static WriteReport setVariantColumn(List<BatchMob> mobs, String column, String value) {
        if (mobs.isEmpty()) {
            return WriteReport.refused("There are no mobs of that group in this list");
        }
        WriteReport report = new WriteReport();
        for (BatchMob m : mobs) {
            if (!report.ok()) {
                break;
            }
            setVariantColumn(report, m, column, value);
        }
        return Commit.reloadIfOk(report);
    }

    /** Sets the coefficient on every mob: a plain mob's spawn row and its default variant's own row. */
    public static WriteReport batchCoefficient(List<BatchMob> mobs, String value) {
        if (mobs.isEmpty()) {
            return WriteReport.refused("There are no mobs of that group in this list");
        }
        WriteReport report = new WriteReport();
        for (BatchMob m : mobs) {
            if (!report.ok()) {
                break;
            }
            setVariantColumn(report, m, Schema.Variants.COEFFICIENT, value);
            // A plain mob's coefficient lives on its spawn row in this scope.
            if (m.variantName().isEmpty()) {
                setSpawnCoefficient(report, m, value);
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** The coefficient cell of a plain mob's spawn row, created with the mob's start values if missing. */
    private static void setSpawnCoefficient(WriteReport report, BatchMob m, String value) {
        Map<String, String> keys = null;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (VariantOps.normalScope(m.scope()).equals(VariantOps.normalScope(row.get(Schema.MobSpawns.SCOPE)))
                    && VariantOps.normalScopeId(m.scope(), m.scopeId()).equals(
                    row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip())
                    && m.entity().equals(row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip())
                    && m.variantName().equals(row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip())) {
                keys = spawnKeys(row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip(),
                        row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip(), m.entity(), m.variantName());
                break;
            }
        }
        if (keys == null) {
            return;
        }
        report.record("the coefficient of " + m.entity(),
                TableWriter.upsert(Schema.MobSpawns.FILE, keys,
                        stampDesigner(Map.of(Schema.MobSpawns.COEFFICIENT, value))));
    }


    // ---- FILL FROM THE GAME'S OWN SPAWNS ----

    /**
     * One mob a fill would ADD to a scope, with the game's own numbers for it.
     *
     * @param category the spawn category, as the game reports it
     * @param weight the game's own spawn weight
     */
    public record FillMob(String entity, String category, String weight, String minCount, String maxCount) {
    }

    /**
     * Several biomes' spawns as ONE list, each mob once - the first biome that lists it wins its numbers.
     */
    public static List<com.strata.game.GameData.SpawnEntry> unionSpawns(
            List<List<com.strata.game.GameData.SpawnEntry>> perBiome) {
        Map<String, com.strata.game.GameData.SpawnEntry> byEntity = new LinkedHashMap<>();
        for (List<com.strata.game.GameData.SpawnEntry> spawns : perBiome) {
            if (spawns == null) {
                continue;
            }
            for (var entry : spawns) {
                if (entry != null && entry.entity() != null) {
                    byEntity.putIfAbsent(entry.entity().toString(), entry);
                }
            }
        }
        return List.copyOf(byEntity.values());
    }

    /**
     * The mobs a fill would add to this scope: the game's own spawns in it that the scope does not list yet.
     *
     * @param scope "tier" or "biome"; anything else fills nothing
     * @param scopeId the tier's name or the biome's id
     * @param listed the entities this scope already lists, {@link #listedInScope}'s answer
     * @param gameSpawns the game's own spawns in the scope, as {@link #unionSpawns} left them
     */
    public static List<FillMob> fillPlan(String scope, String scopeId, java.util.Collection<String> listed,
                                         List<com.strata.game.GameData.SpawnEntry> gameSpawns) {
        if (!removableFromScope(scope, scopeId) || gameSpawns == null || gameSpawns.isEmpty()) {
            return List.of();
        }
        Set<String> have = new java.util.HashSet<>();
        if (listed != null) {
            for (String s : listed) {
                if (s != null && !s.isBlank()) {
                    have.add(s.strip());
                }
            }
        }
        List<FillMob> out = new ArrayList<>();
        for (var entry : gameSpawns) {
            String entity = entry.entity() == null ? "" : entry.entity().toString();
            if (entity.isBlank() || have.contains(entity)) {
                continue;
            }
            // The game's own category, through the mapping the rest of the editor uses, and its own weight
            // and pack size - so a filled row is indistinguishable from one the designer typed in.
            out.add(new FillMob(entity,
                    com.strata.game.EntityCategory.forVanillaCategory(entry.category().name()).id(),
                    String.valueOf(entry.weight()),
                    String.valueOf(entry.minCount()),
                    String.valueOf(entry.maxCount())));
            // A mob listed twice in the game's own settings (two spawners in one biome) is still one mob in
            // the list.
            have.add(entity);
        }
        return List.copyOf(out);
    }

    /** {@link #fillPlan}'s writes: a spawn row per mob, in this scope, with the game's own numbers. */
    public static WriteReport fillFromGame(String scope, String scopeId, List<FillMob> plan) {
        if (plan == null || plan.isEmpty()) {
            return WriteReport.refused("This scope already lists every mob the game spawns in it");
        }
        WriteReport report = fillFromGame(Paths.table(Schema.MobSpawns.FILE), scope, scopeId, plan);
        return report.ok() ? Commit.reloadIfOk(report) : report;
    }

    /**
     * {@link #fillFromGame}'s writes against a named table, with no reload, so the headless gate can drive it
     * against a temp directory the way the rest of this file's persistence is tested.
     */
    public static WriteReport fillFromGame(java.nio.file.Path table, String scope, String scopeId,
                                           List<FillMob> plan) {
        if (plan == null || plan.isEmpty()) {
            return WriteReport.refused("This scope already lists every mob the game spawns in it");
        }
        WriteReport report = new WriteReport();
        String sid = VariantOps.normalScopeId(scope, scopeId);
        for (FillMob m : plan) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put(Schema.MobSpawns.SCOPE, scope.strip());
            values.put(Schema.MobSpawns.SCOPE_ID, sid);
            values.put(Schema.MobSpawns.ENTITY, m.entity());
            values.put(Schema.MobSpawns.VARIANT_NAME, "");
            values.put(Schema.MobSpawns.CATEGORY, m.category());
            values.put(Schema.MobSpawns.WEIGHT, m.weight());
            values.put(Schema.MobSpawns.MIN_COUNT, m.minCount());
            values.put(Schema.MobSpawns.MAX_COUNT, m.maxCount());
            // A filled mob is on by default: the user asked for the biome's own spawns, which is a roster
            // that works, not a set of disabled rows.
            values.put(Schema.MobSpawns.ENABLED, "true");
            values.put(Schema.MobSpawns.IS_VARIANT, "false");
            report.record("the spawn row of " + m.entity(),
                    TableWriter.upsert(table, spawnKeys(scope, sid, m.entity(), ""), stampDesigner(values)));
        }
        return report;
    }

    /** The key columns of one spawn row, for an upsert. */
    private static Map<String, String> spawnKeys(String scope, String scopeId, String entity, String variant) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobSpawns.SCOPE, scope);
        keys.put(Schema.MobSpawns.SCOPE_ID, scopeId);
        keys.put(Schema.MobSpawns.ENTITY, entity);
        keys.put(Schema.MobSpawns.VARIANT_NAME, variant);
        return keys;
    }

    /**
     * The mobs this scope already lists, plain or as a variant, as one string per line: what a fill would
     * skip.
     */
    public static List<String> listedInScope(String scope, String scopeId, List<Map<String, String>> spawnRows) {
        String sc = scope == null ? "" : scope.strip();
        String sid = scopeId == null ? "" : scopeId.strip();
        List<String> out = new ArrayList<>();
        for (var row : spawnRows) {
            if (!sc.equals(row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip())
                    || !sid.equals(row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip())) {
                continue;
            }
            // A VARIANT IS NOT ITS MOB: only a plain row lists the mob itself, so the game's own spawn of it
            // still shows beside its variant.
            if (!row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").isBlank()) {
                continue;
            }
            String entity = row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip();
            // A mob listed twice in the scope (two packs, say) is one entry.
            if (!entity.isBlank() && !out.contains(entity)) {
                out.add(entity);
            }
        }
        return List.copyOf(out);
    }

    /** A mob row in one tier or biome, for a remove of several. */
    public record ScopedMob(String scope, String scopeId, String entity, String variantName) {
    }

    /**
     * RESET TO GAME: true when every picked row is a plain mob the game itself spawns in this scope.
     *
     * @param gameHere the mobs the game spawns in this scope
     */
    public static boolean allResetToGame(List<ScopedMob> picked, java.util.Set<String> gameHere) {
        return !picked.isEmpty() && picked.stream()
                .allMatch(m -> m.variantName().isEmpty() && gameHere.contains(m.entity()));
    }

    /**
     * The picked rows a remove can take, and the rest: a plain mob in a named tier or biome is removed from
     * it; a variant is skipped, because removing it deletes the variant, which its own editor asks about.
     */
    public static Map<Boolean, List<ScopedMob>> splitRemovable(List<ScopedMob> picked) {
        Map<Boolean, List<ScopedMob>> out = new LinkedHashMap<>();
        out.put(true, new ArrayList<>());
        out.put(false, new ArrayList<>());
        for (ScopedMob m : picked) {
            boolean plain = m.variantName() == null || m.variantName().isBlank();
            out.get(plain && removableFromScope(m.scope(), m.scopeId())
                    && m.entity() != null && !m.entity().isBlank()).add(m);
        }
        return out;
    }

    /**
     * Removes several plain mobs, each from its tier or biome, as {@link #removeFromScope} does, then one
     * reload.
     */
    public static WriteReport removeAllFromScope(List<ScopedMob> picked) {
        List<ScopedMob> removable = splitRemovable(picked).get(true);
        if (removable.isEmpty()) {
            return WriteReport.refused("Pick a plain mob in a tier or biome: a variant is removed in its editor");
        }
        WriteReport report = new WriteReport();
        for (ScopedMob m : removable) {
            removeFromScopeInto(report, m.scope(), m.scopeId(), m.entity());
            if (!report.ok()) {
                return report;
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** {@link #removeFromScope}'s writes, into a report, with no reload. */
    private static WriteReport removeFromScopeInto(WriteReport report, String scope, String scopeId, String entity) {
        String sc = scope.strip();
        String sid = scopeId.strip();
        String ent = entity.strip();
        List<Map<String, String>> spawnRows = new ArrayList<>();
        for (var row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (VariantOps.deletesScopedRow(sc, sid, ent, "",
                    row.get(Schema.MobSpawns.SCOPE), row.get(Schema.MobSpawns.SCOPE_ID),
                    row.get(Schema.MobSpawns.ENTITY), row.get(Schema.MobSpawns.VARIANT_NAME))) {
                spawnRows.add(row);
            }
        }
        if (spawnRows.isEmpty()) {
            return report.record("the mob's spawn row", false);
        }
        VariantOps.deleteScopedRows(report, Schema.Loot.FILE, sc, sid, ent, "");
        VariantOps.deleteScopedRows(report, Schema.SpawnRules.FILE, sc, sid, ent, "");
        if (!report.ok()) return report;
        for (var row : spawnRows) {
            Map<String, String> keys = new LinkedHashMap<>(row);
            keys.replaceAll((k, v) -> v.strip());
            report.record("the mob's spawn row", TableWriter.delete(Schema.MobSpawns.FILE, keys));
        }
        return report;
    }

    /** The variant a plain mob's extras hang off, and whether making it landed. */
    public record DefaultVariant(String name, WriteReport report) {
    }

    /**
     * The variant a plain mob's equipment and abilities hang off, created (then reloaded) when it does not
     * exist yet.
     */
    public static DefaultVariant ensureDefaultVariant(String entity) {
        String ent = entity == null ? "" : entity.strip();
        if (ent.isEmpty()) {
            return new DefaultVariant("", WriteReport.refused("Pick a base entity first"));
        }
        String name = com.strata.model.Variant.defaultNameFor(ent);
        if (Config.tables().variants().containsKey(name)) {
            return new DefaultVariant(name, new WriteReport());
        }
        return new DefaultVariant(name, Commit.reloadIfOk(new WriteReport().record("the default variant",
                TableWriter.upsert(Schema.Variants.FILE,
                        Map.of(Schema.Variants.NAME, name),
                        Map.of(Schema.Variants.BASE_ENTITY, ent,
                                Schema.Variants.COEFFICIENT, "1.0",
                                Schema.Variants.TEXTURE_PATH, "")))));
    }

    // ---- RESET TO THE GAME ----

    /** One table's rows a reset removes, and what they are, in words. */
    public record ResetPart(String file, String what, List<Map<String, String>> rows) {
    }

    /**
     * Every edit of a plain mob, by table: its base stats, mob data, equipment roll, drops, spawn conditions,
     * loot table values, and what its default variant holds (equipment and behavior, abilities, own AI,
     * stats, and the variant row itself, last).
     *
     * @param lootTable the mob's loot table id, or "" for none
     * @param read a table's rows by file name
     */
    public static List<ResetPart> resetParts(String entity, String lootTable,
                                             java.util.function.Function<String, List<Map<String, String>>> read) {
        String e = entity == null ? "" : entity.strip();
        String own = com.strata.model.Variant.defaultNameFor(e);
        List<ResetPart> out = new ArrayList<>();
        if (e.isEmpty()) {
            return out;
        }
        // THE WORDS THE EDITOR USES: each part is named for the button or screen that edits it.
        part(out, read, Schema.MobBaseStats.FILE, "Mob data attribute(s)",
                r -> e.equals(cell(r, Schema.MobBaseStats.ENTITY)));
        part(out, read, Schema.MobData.FILE, "Mob data value(s)",
                r -> e.equals(cell(r, Schema.MobData.ENTITY)) && cell(r, Schema.MobData.VARIANT_NAME).isEmpty());
        part(out, read, Schema.Loot.FILE, "loot entries",
                r -> e.equals(cell(r, Schema.Loot.ENTITY))
                        && cell(r, Schema.Loot.VARIANT_NAME).isEmpty());
        part(out, read, Schema.SpawnRules.FILE, "spawn rule(s)",
                r -> e.equals(cell(r, Schema.SpawnRules.ENTITY))
                        && cell(r, Schema.SpawnRules.VARIANT_NAME).isEmpty());
        if (lootTable != null && !lootTable.isBlank()) {
            part(out, read, Schema.LootTables.FILE, "loot table value(s)",
                    r -> lootTable.strip().equals(cell(r, Schema.LootTables.TABLE)));
        }
        if (!own.isEmpty()) {
            part(out, read, Schema.Equipment.FILE, "equipment item(s)",
                    r -> own.equals(cell(r, Schema.Equipment.VARIANT_NAME)));
            part(out, read, Schema.MobGoalSets.FILE, "goal set choice(s)",
                    r -> own.equals(cell(r, Schema.MobGoalSets.VARIANT_NAME)));
            part(out, read, Schema.Abilities.FILE, "abilit(ies)",
                    r -> own.equals(cell(r, Schema.Abilities.VARIANT_NAME)));
            part(out, read, Schema.MobGoals.FILE, "goal(s) of this mob's own list",
                    r -> own.equals(cell(r, Schema.MobGoals.VARIANT_NAME)));
            part(out, read, Schema.VariantStats.FILE, "Mob data attribute(s)",
                    r -> own.equals(cell(r, Schema.VariantStats.VARIANT_NAME)));
            // Last: the row that holds the rest.
            part(out, read, Schema.Variants.FILE, "the row that holds its settings",
                    r -> own.equals(cell(r, Schema.Variants.NAME)));
        }
        return out;
    }

    private static void part(List<ResetPart> out, java.util.function.Function<String, List<Map<String, String>>> read,
                             String file, String what, java.util.function.Predicate<Map<String, String>> mine) {
        List<Map<String, String>> rows = read.apply(file).stream().filter(mine).toList();
        if (!rows.isEmpty()) {
            out.add(new ResetPart(file, what, rows));
        }
    }

    private static String cell(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null ? "" : v.strip();
    }

    /** The reset of a plain mob, from the tables on disk. */
    public static List<ResetPart> resetParts(String entity) {
        return resetParts(entity, LootTableOps.sheet(entity).table(), file -> CsvTable.read(Paths.table(file)));
    }

    /** What a reset removes, in the editor's words: "3 Mob data attribute(s), 2 drop(s)". */
    public static String resetSummary(List<ResetPart> parts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ResetPart p : parts) {
            if (!Schema.Variants.FILE.equals(p.file())) {
                counts.merge(p.what(), p.rows().size(), Integer::sum);
            }
        }
        List<String> words = new ArrayList<>();
        counts.forEach((what, n) -> words.add(n + " " + what));
        return String.join(", ", words);
    }

    /**
     * Resets a plain mob to the game: removes every row {@link #resetParts} names, in its order (the default
     * variant last), then reloads.
     */
    public static WriteReport resetToGame(String entity) {
        List<ResetPart> parts = resetParts(entity);
        if (parts.isEmpty()) {
            return WriteReport.refused("This mob has no edits: it is as the game makes it");
        }
        WriteReport report = new WriteReport();
        for (ResetPart part : parts) {
            for (Map<String, String> row : part.rows()) {
                if (!report.ok()) {
                    return report;
                }
                Map<String, String> keys = new LinkedHashMap<>(row);
                keys.replaceAll((k, v) -> v == null ? "" : v.strip());
                report.record(part.what(), TableWriter.delete(part.file(), keys));
            }
        }
        return Commit.reloadIfOk(report);
    }
}