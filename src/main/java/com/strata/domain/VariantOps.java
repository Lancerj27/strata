package com.strata.domain;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.game.EntityCategory;
import com.strata.game.GameData;
import com.strata.model.Variant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the editor can DO to a variant, in one place. */
public final class VariantOps {

    private VariantOps() {
    }

    /** Whether a spawn row with this name is a variant. */
    public static boolean isVariantName(String name) {
        return name != null && !name.isBlank();
    }

    /** INSTANCING. */
    public static boolean clearsPriorSpawnRow(String openedScope, String openedScopeId,
                                              String variantName, String entity,
                                              String rowScope, String rowScopeId,
                                              String rowVariantName, String rowEntity) {
        if (openedScope == null || variantName == null) return false;
        String oid = openedScopeId == null ? "" : openedScopeId.strip();
        return openedScope.strip().equals(rowScope == null ? "" : rowScope.strip())
                && oid.equals(rowScopeId == null ? "" : rowScopeId.strip())
                && variantName.equals(rowVariantName == null ? "" : rowVariantName.strip())
                && sameMob(variantName, entity, rowEntity);
    }

    /**
     * Whether a save clears this prior mob_spawns row: the row saved under the new name, or the row the
     * editor OPENED on, under its old name.
     */
    public static boolean clearsOnSave(String openedScope, String openedScopeId,
                                       String openedName, String name, String openedEntity,
                                       String rowScope, String rowScopeId,
                                       String rowVariantName, String rowEntity) {
        if (clearsPriorSpawnRow(openedScope, openedScopeId, name, openedEntity,
                rowScope, rowScopeId, rowVariantName, rowEntity)) {
            return true;
        }
        return openedName != null && !openedName.equals(name)
                && clearsPriorSpawnRow(openedScope, openedScopeId, openedName, openedEntity,
                        rowScope, rowScopeId, rowVariantName, rowEntity);
    }

    /** True when a row is the same mob as the one being edited. */
    private static boolean sameMob(String variantName, String entity, String rowEntity) {
        if (!variantName.strip().isEmpty()) {
            return true;
        }
        String want = entity == null ? "" : entity.strip();
        return !want.isEmpty() && want.equals(rowEntity == null ? "" : rowEntity.strip());
    }

    /**
     * Picks the spawn row an editor opened in {@code scope}/{@code scopeId} should adopt, out of the rows for
     * one variant.
     *
     * @param entity the entity of the row to find. Used only when {@code variantName} is blank.
     * @return the index into {@code rows} to adopt, or -1 when none match
     */
    public static int spawnRowForScope(List<Map<String, String>> rows, String variantName,
                                       String entity, String scope, String scopeId) {
        // A SCOPE WAS ASKED FOR OR IT WAS NOT.
        boolean scoped = scope != null && !scope.strip().isEmpty();
        int firstMatch = -1;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String vn = row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip();
            // A BLANK NAME IS A REAL ROW, not an absent one: it is the base mob's.
            if (!vn.equals(variantName == null ? "" : variantName.strip())) {
                continue;
            }
            if (!sameMob(vn, entity, row.get(Schema.MobSpawns.ENTITY))) {
                continue;
            }
            if (firstMatch < 0) {
                firstMatch = i;
            }
            if (scope == null || scope.strip().isEmpty()) {
                continue;
            }
            boolean sameScope = scope.strip()
                    .equals(row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip())
                    && (scopeId == null ? "" : scopeId.strip())
                            .equals(row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip());
            if (sameScope) {
                return i;
            }
        }
        // No cross-scope consolation prize.
        return scoped ? -1 : firstMatch;
    }

    /**
     * A name no variant is using yet, by appending the first free number.
     *
     * @param taken every name already in use
     */
    public static String uniqueName(String desired, java.util.Set<String> taken) {
        String base = desired == null ? "" : desired.strip();
        if (base.isEmpty()) {
            base = "variant";
        }
        if (!taken.contains(base)) {
            return base;
        }
        // Bounded so a pathological config cannot spin here; 4 digits is far past the point a designer would
        // want a list of them.
        for (int n = 2; n < 10000; n++) {
            String candidate = base + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base + System.currentTimeMillis();
    }

    /**
     * Carries a variant's new name across every table that keys on the old one, within ONE scope instance.
     */
    public static WriteReport rename(String scope, String scopeId,
                                     String oldName, String newName,
                                     Map<String, String> values) {
        WriteReport report = new WriteReport();
        report.record("the renamed variant", TableWriter.upsert(Schema.Variants.FILE,
                instanceKey(Schema.Variants.NAME, newName, scope, scopeId), values));
        if (!report.ok()) return report;

        renameColumn(report, "the stats", Schema.VariantStats.FILE,
                Schema.VariantStats.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the equipment", Schema.Equipment.FILE,
                Schema.Equipment.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the abilities", Schema.Abilities.FILE,
                Schema.Abilities.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the goal list", Schema.MobGoals.FILE,
                Schema.MobGoals.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the goal set choice", Schema.MobGoalSets.FILE,
                Schema.MobGoalSets.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the mob data", Schema.MobData.FILE,
                Schema.MobData.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the loot", Schema.Loot.FILE,
                Schema.Loot.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the spawn rules", Schema.SpawnRules.FILE,
                Schema.SpawnRules.VARIANT_NAME, oldName, newName, scope, scopeId);
        renameColumn(report, "the spawn rows", Schema.MobSpawns.FILE,
                Schema.MobSpawns.VARIANT_NAME, oldName, newName, scope, scopeId);
        for (String file : Schema.MOB_PROPERTY_FILES.values()) {
            renameColumn(report, "the mob properties", file,
                    Schema.MobSounds.VARIANT_NAME, oldName, newName, scope, scopeId);
        }

        // The old definition MUST go: leaving both names in the table while the editor closes is the silent
        // half-rename this ordering exists to prevent.
        return report.record("the old variant name", TableWriter.delete(Schema.Variants.FILE,
                instanceKey(Schema.Variants.NAME, oldName, scope, scopeId)));
    }

    private static void renameColumn(WriteReport report, String what, String file,
                                     String column, String oldName, String newName,
                                     String scope, String scopeId) {
        // THE SCOPE IS PART OF THE MATCH.
        report.cascaded(what, TableWriter.updateColumnWhere(file,
                instanceKey(column, oldName, scope, scopeId), column, newName));
    }

    // ------------------------------------------------------------ the editor MobEditScreen calls only these.

    /** The scope choices of the Scope button, in cycle order. */
    public static final List<String> SCOPE_CHOICES = List.of("global", "tier", "biome");

    /** The base entity a brand-new variant starts with. */
    public static final String DEFAULT_ENTITY = "minecraft:zombie";

    /** What the variant and mob editor shows and saves, as typed. */
    /**
     * @param scaling the mob's own scaling switch as stored: "" Default, "on", or "off"
     * @param spawnChance the chance, 0 to 100, that it spawns when the game picks it; "" is 100
     * @param mobGriefing the mob's own mob griefing switch as stored: "" Default, "on", or "off"
     * @param category a plain mob's category, as CategoryOps' word; "" for a variant
     * @param startLevel the mob's own level, as typed; "" is 1. With scaling on, the area's level moves its
     *     stats relative to this one; with scaling off, it is the level
     */
    public record VariantForm(String name, String displayName, String baseEntity, String coefficient,
                              String texture, boolean ownEntity, String goalSet,
                              String scope, String scopeId, String weight, String minCount,
                              String maxCount, boolean enabled, String scaling, String spawnChance,
                              String mobGriefing, String category, String startLevel) {

        /** A form that leaves the start level as it is. */
        public VariantForm(String name, String displayName, String baseEntity, String coefficient,
                           String texture, boolean ownEntity, String goalSet,
                           String scope, String scopeId, String weight, String minCount,
                           String maxCount, boolean enabled, String scaling, String spawnChance,
                           String mobGriefing, String category) {
            this(name, displayName, baseEntity, coefficient, texture, ownEntity, goalSet, scope, scopeId,
                    weight, minCount, maxCount, enabled, scaling, spawnChance, mobGriefing, category, "");
        }

        /** A form that leaves the category as it is. */
        public VariantForm(String name, String displayName, String baseEntity, String coefficient,
                           String texture, boolean ownEntity, String goalSet,
                           String scope, String scopeId, String weight, String minCount,
                           String maxCount, boolean enabled, String scaling, String spawnChance,
                           String mobGriefing) {
            this(name, displayName, baseEntity, coefficient, texture, ownEntity, goalSet, scope, scopeId,
                    weight, minCount, maxCount, enabled, scaling, spawnChance, mobGriefing, "");
        }

        /** A form with its mob griefing on Default. */
        public VariantForm(String name, String displayName, String baseEntity, String coefficient,
                           String texture, boolean ownEntity, String goalSet,
                           String scope, String scopeId, String weight, String minCount,
                           String maxCount, boolean enabled, String scaling, String spawnChance) {
            this(name, displayName, baseEntity, coefficient, texture, ownEntity, goalSet, scope, scopeId,
                    weight, minCount, maxCount, enabled, scaling, spawnChance, "");
        }

        /** A form with no spawn chance typed: it spawns whenever it is picked. */
        public VariantForm(String name, String displayName, String baseEntity, String coefficient,
                           String texture, boolean ownEntity, String goalSet,
                           String scope, String scopeId, String weight, String minCount,
                           String maxCount, boolean enabled, String scaling) {
            this(name, displayName, baseEntity, coefficient, texture, ownEntity, goalSet, scope, scopeId,
                    weight, minCount, maxCount, enabled, scaling, "");
        }

        /**
         * The same form with another behavior: a behavior saved by name from the AI list is the mob's now, so
         * the editor's form takes it and its Save does not write the old one back.
         */
        public VariantForm withGoalSet(String goalSet) {
            return new VariantForm(name, displayName, baseEntity, coefficient, texture, ownEntity,
                    goalSet == null ? "" : goalSet, scope, scopeId, weight, minCount, maxCount, enabled, scaling,
                    spawnChance, mobGriefing, category, startLevel);
        }
    }

    // ---- the mob's own switches: Scaling, Mob griefing ----

    /** A switch dropdown's words, in order. */
    public static final List<String> SWITCH_CHOICES = List.of("Default", "On", "Off");

    /** A stored switch as the dropdown's word. */
    public static String switchWord(String stored) {
        String v = com.strata.runtime.MobSwitches.normal(stored);
        return v.isEmpty() ? "Default" : v.equals(com.strata.runtime.MobSwitches.ON) ? "On" : "Off";
    }

    /** A dropdown word as stored: "", "on", or "off". */
    public static String switchStored(String word) {
        return com.strata.runtime.MobSwitches.normal(word);
    }

    /** What each word does, for the dropdown's tooltip. */
    public static final String SCALING_HELP = "Default follows Scaling > Mob scaling. On always scales this mob;"
            + " Off never does. On or Off here overrides every other scaling rule: the tier, the biome, and"
            + " the Mob scaling screen.";

    /** What each Mob griefing word does. */
    public static final String MOB_GRIEFING_HELP = "Default follows the game's mobGriefing rule. Off: this mob"
            + " changes no blocks (a creeper's blast breaks none, an enderman takes none) and picks up no items."
            + " On: it may, even with the rule off. A variant takes its base mob's.";

    /** What each Passive word does. */
    public static final String PASSIVE_HELP = "On: this mob never hunts players. Every goal that picks a player"
            + " as its target is switched off (kept on the mob, never run), the game's own and a mod's alike; it still fights back when hurt."
            + " Default and Off leave its goals as they are. A variant takes its base mob's.";

    /** What each Converts word does. */
    public static final String CONVERTS_HELP = "Off: this mob never converts - a zombie does not become a drowned,"
            + " a villager a witch, a piglin a zombified piglin, nor a mod's mob its other form. Default and On"
            + " leave the game's rule. What it converts into can be changed in Replace mob, with every spawn.";

    /** True when the instance has its own variants.csv row. */
    static boolean hasDefinitionRow(String name, String scope, String scopeId) {
        String wantScope = normalScope(scope);
        String wantId = normalScopeId(scope, scopeId);
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Variants.FILE))) {
            if (name.equals(row.getOrDefault(Schema.Variants.NAME, "").strip())
                    && wantScope.equals(normalScope(row.get(Schema.Variants.SCOPE)))
                    && wantId.equals(row.getOrDefault(Schema.Variants.SCOPE_ID, "").strip())) {
                return true;
            }
        }
        return false;
    }

    /** A switch column's value on an instance's variants.csv row, or "" (Default). */
    /** One cell of a variant's row in this scope, as stored, or "". */
    static String storedCell(String column, String name, String scope, String scopeId) {
        String wantScope = normalScope(scope);
        String wantId = normalScopeId(scope, scopeId);
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Variants.FILE))) {
            if (name.equals(row.getOrDefault(Schema.Variants.NAME, "").strip())
                    && wantScope.equals(normalScope(row.get(Schema.Variants.SCOPE)))
                    && wantId.equals(row.getOrDefault(Schema.Variants.SCOPE_ID, "").strip())) {
                return row.getOrDefault(column, "").strip();
            }
        }
        return "";
    }

    /** Why a typed start level cannot be saved, or null. */
    public static String startLevelProblem(String typed) {
        String t = typed == null ? "" : typed.strip();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(t) >= 1 ? null : "Start level must be 1 or more";
        } catch (NumberFormatException e) {
            return "Start level must be a whole number";
        }
    }

    static String storedSwitch(String column, String name, String scope, String scopeId) {
        String wantScope = normalScope(scope);
        String wantId = normalScopeId(scope, scopeId);
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Variants.FILE))) {
            if (name.equals(row.getOrDefault(Schema.Variants.NAME, "").strip())
                    && wantScope.equals(normalScope(row.get(Schema.Variants.SCOPE)))
                    && wantId.equals(row.getOrDefault(Schema.Variants.SCOPE_ID, "").strip())) {
                return com.strata.runtime.MobSwitches.normal(row.get(column));
            }
        }
        return "";
    }

    /**
     * How the editor was opened.
     *
     * @param originalName the variant opened, or "" for a plain mob or a new row
     * @param prefilledBase the entity the list row had, or null
     * @param isNew a row that does not exist yet
     * @param baseMobRow a plain mob's row: a blank name stays blank on save
     */
    public record EditorOpen(String originalName, String prefilledBase, String sourceScope,
                             String sourceScopeId, boolean isNew, boolean baseMobRow) {

        /** An existing base row is not "new": it exists, it just has no variant name. */
        public static EditorOpen of(String name, String baseEntity, String sourceScope,
                                    String sourceScopeId, boolean baseMobRow) {
            return new EditorOpen(name == null ? "" : name, baseEntity, sourceScope, sourceScopeId,
                    name == null && (!baseMobRow || baseEntity == null), baseMobRow);
        }
    }

    /** What the editor starts with: the form, and the scope it stands in. */
    public record Opened(VariantForm form, String openedScope, String openedScopeId) {
    }

    /** The editor's starting state. */
    public static Opened open(EditorOpen at) {
        Variant existing = at.isNew() ? null : Config.tables().variants().get(at.originalName());
        Map<String, String> spawnRow = null;
        if (!at.isNew()) {
            var rows = CsvTable.read(Paths.table(Schema.MobSpawns.FILE));
            int found = spawnRowForScope(rows, at.originalName(), at.prefilledBase(),
                    at.sourceScope(), at.sourceScopeId());
            spawnRow = found < 0 ? null : rows.get(found);
        }

        String scope;
        if (at.sourceScope() != null && !at.sourceScope().isEmpty()) {
            scope = at.sourceScope();
        } else if (spawnRow != null && !spawnRow.getOrDefault(Schema.MobSpawns.SCOPE, "").isBlank()) {
            scope = spawnRow.get(Schema.MobSpawns.SCOPE).strip();
        } else {
            scope = "tier";
        }
        String initScopeId = spawnRow != null
                ? spawnRow.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip()
                : at.sourceScopeId() == null ? "" : at.sourceScopeId();
        String openedScopeId = "global".equals(scope) ? "" : initScopeId;
        // A new row's Target field needs a real target to start from.
        String formScopeId = at.isNew() && initScopeId.isEmpty()
                ? targetChoices(scope).getFirst() : initScopeId;

        String baseEntity = at.prefilledBase() != null ? at.prefilledBase()
                : existing != null ? existing.baseEntity() : DEFAULT_ENTITY;
        // A NEW ROW STARTS AS THE GLOBAL MOB: not 10, 1 to 4.
        StartValues start = spawnRow == null ? startFor(baseEntity) : null;
        String coefficient = existing != null ? String.valueOf(existing.coefficient())
                : spawnRow != null && !spawnRow.getOrDefault(Schema.MobSpawns.COEFFICIENT, "").isBlank()
                        ? spawnRow.get(Schema.MobSpawns.COEFFICIENT).strip()
                        : start != null ? start.coefficient() : "1.0";
        String name = initialName(at.baseMobRow(), existing == null ? null : existing.id(),
                () -> suggestName(baseEntity, at.sourceScope(), at.sourceScopeId()));

        VariantForm form = new VariantForm(name,
                existing == null || existing.displayName().equals(existing.id()) ? "" : existing.displayName(),
                baseEntity, coefficient,
                existing == null ? "" : existing.texture(),
                existing != null ? existing.isOwnEntity() : at.prefilledBase() != null,
                // THIS SCOPE's preset.
                at.isNew() ? "" : storedGoalSet(at.baseMobRow()
                                ? Variant.defaultNameFor(baseEntity) : at.originalName(),
                        scope, openedScopeId),
                scope, formScopeId,
                start != null ? start.weight() : spawnCell(spawnRow, Schema.MobSpawns.WEIGHT, "10"),
                start != null ? start.minCount() : spawnCell(spawnRow, Schema.MobSpawns.MIN_COUNT, "1"),
                start != null ? start.maxCount() : spawnCell(spawnRow, Schema.MobSpawns.MAX_COUNT, "4"),
                start != null ? start.enabled() : Parse.flag(spawnRow.get(Schema.MobSpawns.ENABLED), true),
                // THIS SCOPE's switches; a plain mob's are on its default variant.
                at.isNew() ? "" : storedSwitch(Schema.Variants.SCALING, at.baseMobRow()
                        ? Variant.defaultNameFor(baseEntity) : at.originalName(), scope, openedScopeId),
                start != null ? start.spawnChance() : spawnCell(spawnRow, Schema.MobSpawns.SPAWN_CHANCE, ""),
                at.isNew() ? "" : storedSwitch(Schema.Variants.MOB_GRIEFING, at.baseMobRow()
                        ? Variant.defaultNameFor(baseEntity) : at.originalName(), scope, openedScopeId),
                // A plain mob's category; a variant shares its base mob's.
                existing == null ? CategoryOps.shownWord(baseEntity) : "",
                // THIS SCOPE's start level; a plain mob's is on its default variant.
                at.isNew() ? "" : storedCell(Schema.Variants.START_LEVEL, at.baseMobRow()
                        ? Variant.defaultNameFor(baseEntity) : at.originalName(), scope, openedScopeId));
        return new Opened(form, scope, openedScopeId);
    }

    /** What a new row starts with: the global mob. */
    public record StartValues(String weight, String minCount, String maxCount, boolean enabled, String coefficient,
                              String spawnChance) {

        /** Start values with no spawn chance: it spawns whenever it is picked. */
        public StartValues(String weight, String minCount, String maxCount, boolean enabled, String coefficient) {
            this(weight, minCount, maxCount, enabled, coefficient, "");
        }
    }

    /** The form with another base mob. */
    public static VariantForm withStartOf(boolean isNew, VariantForm f, String entity) {
        StartValues old = isNew ? startFor(f.baseEntity()) : null;
        boolean untouched = old != null && old.weight().equals(f.weight()) && old.minCount().equals(f.minCount())
                && old.maxCount().equals(f.maxCount()) && old.enabled() == f.enabled()
                && old.coefficient().equals(f.coefficient()) && old.spawnChance().equals(f.spawnChance());
        StartValues s = untouched ? startFor(entity) : null;
        return new VariantForm(f.name(), f.displayName(), entity,
                s != null ? s.coefficient() : f.coefficient(), f.texture(), f.ownEntity(), f.goalSet(),
                f.scope(), f.scopeId(),
                s != null ? s.weight() : f.weight(), s != null ? s.minCount() : f.minCount(),
                s != null ? s.maxCount() : f.maxCount(), s != null ? s.enabled() : f.enabled(), f.scaling(),
                s != null ? s.spawnChance() : f.spawnChance(), f.mobGriefing(),
                // A new mob picked from the list starts with its own category.
                f.category().isEmpty() ? "" : CategoryOps.shownWord(entity), f.startLevel());
    }

    /** The start values for an entity, from the tables and the game layer. */
    public static StartValues startFor(String entity) {
        List<com.strata.data.GameSnapshot.SpawnData> game = new ArrayList<>();
        com.strata.data.GameSnapshot.get().values().forEach(e -> game.addAll(e.spawns()));
        return startFor(CsvTable.read(Paths.table(Schema.MobSpawns.FILE)), game, entity);
    }

    /** The start values, over the spawn rows and the game's spawns. */
    public static StartValues startFor(List<Map<String, String>> spawnRows,
                                       List<com.strata.data.GameSnapshot.SpawnData> game, String entity) {
        String e = entity == null ? "" : entity.strip();
        for (Map<String, String> row : spawnRows) {
            if (normalScope(row.get(Schema.MobSpawns.SCOPE)).isEmpty()
                    && e.equals(row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip())
                    && row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").isBlank()) {
                String coefficient = row.getOrDefault(Schema.MobSpawns.COEFFICIENT, "").strip();
                return new StartValues(spawnCell(row, Schema.MobSpawns.WEIGHT, "10"),
                        spawnCell(row, Schema.MobSpawns.MIN_COUNT, "1"), spawnCell(row, Schema.MobSpawns.MAX_COUNT, "4"),
                        Parse.flag(row.get(Schema.MobSpawns.ENABLED), true), coefficient.isEmpty() ? "1.0" : coefficient,
                        spawnCell(row, Schema.MobSpawns.SPAWN_CHANCE, ""));
            }
        }

        com.strata.data.GameSnapshot.SpawnData best = null;
        for (com.strata.data.GameSnapshot.SpawnData s : game) {
            if (s.entity().equals(e) && (best == null || s.weight() > best.weight())) {
                best = s;
            }
        }
        return best == null ? new StartValues("10", "1", "4", true, "1.0")
                : new StartValues(String.valueOf(best.weight()), String.valueOf(Math.max(1, best.minCount())),
                        String.valueOf(Math.max(Math.max(1, best.minCount()), best.maxCount())), true, "1.0");
    }

    private static String spawnCell(Map<String, String> row, String column, String fallback) {
        return row == null ? fallback : row.getOrDefault(column, fallback).strip();
    }

    /** The behavior choice that takes the base mob's behavior. */
    public static final String FROM_BASE_MOB = "From base mob";

    /** The behavior choice for the game's own AI, for any mob. */
    public static final String GAME_DEFAULT = "Game default";

    /** A plain mob's default choice: its family's archetype. */
    public static final String FAMILY_DEFAULT = "Its family's";

    /**
     * A mob's own behavior choices, before the library: the default first, a variant's "From base mob", a
     * plain mob's "Its family's"; then "Game default", the game's own AI whatever is inherited.
     */
    public static List<String> defaultChoices(boolean plain) {
        return List.of(plain ? FAMILY_DEFAULT : FROM_BASE_MOB, GAME_DEFAULT);
    }

    /** The stored preset for a choice. */
    public static String goalSetOfChoice(String choice, boolean plain) {
        if (choice == null || choice.isBlank() || FROM_BASE_MOB.equals(choice) || FAMILY_DEFAULT.equals(choice)) {
            return "";
        }
        // "Game default" is the game's own AI for any mob: a plain mob's blank is its family's archetype now.
        if (GAME_DEFAULT.equals(choice)) {
            return com.strata.model.GoalSets.NONE;
        }
        return choice;
    }

    /** The choice a stored preset shows as. */
    public static String choiceOfGoalSet(String goalSet, boolean plain) {
        if (goalSet == null || goalSet.isBlank()) {
            return plain ? FAMILY_DEFAULT : FROM_BASE_MOB;
        }
        return com.strata.model.GoalSets.NONE.equals(goalSet) ? GAME_DEFAULT : goalSet;
    }

    /** The behavior choices for this mob, from the runtime's preset library, sorted. */
    public static List<String> goalSetChoices(String entity) {
        return goalSetChoices(com.strata.runtime.GoalsRuntime.goalSets(), GameData.canAttack(entity), GoalsOps.attacks());
    }

    /** The filter itself, pure, so the gate can pin it. */
    public static List<String> goalSetChoices(Map<String, List<com.strata.model.GoalRow>> library,
                                             boolean canAttack, com.strata.model.Attacks attacks) {
        List<String> out = new ArrayList<>();
        library.forEach((name, rows) -> {
            if (canAttack || !attacks.fights(rows)) {
                out.add(name);
            }
        });
        out.sort(String::compareTo);
        return out;
    }

    /** The targets a scope offers: tier names, biome ids, or "" for global. */
    public static List<String> targetChoices(String scope) {
        List<String> opts = new ArrayList<>();
        if ("tier".equals(scope)) {
            opts.addAll(Config.tables().tiers().keySet());
        } else if ("biome".equals(scope)) {
            com.strata.game.BiomeCatalog.all(null).forEach(b -> opts.add(b.id()));
        }
        if (opts.isEmpty()) opts.add("");
        return opts;
    }

    /** Why a target cannot be used, or null when it can. */
    public static String targetProblem(String scope, String target) {
        if ("global".equals(scope)) return null;
        return target != null && targetChoices(scope).contains(target.strip())
                ? null : "Pick a " + scope + " that exists";
    }

    private static final String WEIGHT_RANGE = "A whole number, 0 to " + com.strata.model.Spawn.MAX_WEIGHT;

    /** Why a spawn weight cannot be used, or null when it can. */
    public static String weightProblem(String weight) {
        String t = weight == null ? "" : weight.strip();
        if (t.isEmpty()) return null;
        try {
            int w = Integer.parseInt(t);
            return w >= 0 && w <= com.strata.model.Spawn.MAX_WEIGHT ? null : WEIGHT_RANGE;
        } catch (NumberFormatException e) {
            return WEIGHT_RANGE;
        }
    }

    /** Why a group size cannot be used, or null when it can. */
    public static String groupProblem(String min, String max) {
        Integer lo = count(min, 1);
        Integer hi = count(max, 4);
        if (lo == null || hi == null) return "A whole number, 0 or more";
        return lo > hi ? "Min cannot be more than Max" : null;
    }

    /** A group count, the default when blank, or null when it is not a whole number of 0 or more. */
    private static Integer count(String s, int blank) {
        String t = s == null ? "" : s.strip();
        if (t.isEmpty()) return blank;
        try {
            int v = Integer.parseInt(t);
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final String CHANCE_RANGE = "Blank, or a number from 0 to 100";

    /**
     * Why a spawn chance cannot be used, or null when it can: blank (it spawns whenever the game picks it) or
     * 0 to 100.
     */
    public static String chanceProblem(String chance) {
        String t = chance == null ? "" : chance.strip();
        if (t.isEmpty()) return null;
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) && v >= 0 && v <= 100 ? null : CHANCE_RANGE;
        } catch (NumberFormatException e) {
            return CHANCE_RANGE;
        }
    }
    /** Why a variant name cannot be used, or null when it can. */
    public static String nameProblem(String name, EditorOpen at) {
        String t = name == null ? "" : name.strip();
        if (t.isEmpty()) return null;
        if (!at.isNew() && t.equals(at.originalName())) return null;
        return nameProblem(t, Config.tables().variants().keySet());
    }

    /**
     * Why a new variant name cannot be used, or null.
     *
     * @param taken the names other variants have
     */
    public static String nameProblem(String name, java.util.Collection<String> taken) {
        String t = name == null ? "" : name.strip();
        if (!t.matches("[A-Za-z0-9_ \\-]{1,64}")) {
            return "A name uses letters, digits, spaces, _ and -, up to 64";
        }
        String id = com.strata.runtime.VariantEntityRegistry.registryName(t);
        for (String other : taken) {
            if (other.equals(t) || com.strata.runtime.VariantEntityRegistry.registryName(other).equals(id)) {
                return "Another variant already has this name";
            }
        }
        return null;
    }

    /** True when the scope already holds a plain row (blank variant name) for this entity. */
    public static boolean hasPlainRow(List<Map<String, String>> spawnRows,
                                      String scope, String scopeId, String entity) {
        String sc = normalScope(scope);
        String sid = normalScopeId(scope, scopeId);
        for (Map<String, String> row : spawnRows) {
            if (sc.equals(normalScope(row.get(Schema.MobSpawns.SCOPE)))
                    && sid.equals(row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip())
                    && entity.equals(row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip())
                    && row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The name for an automatic copy of a plain mob: the creature's short id and the first free number from 1
     * ("creeper_1", "creeper_2").
     */
    public static String copyName(String entity, java.util.Set<String> taken) {
        String base = shortToken(entity);
        for (int n = 1; n < 10000; n++) {
            String candidate = base + "_" + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return uniqueName(base + "_copy", taken);
    }

    /** "minecraft:wither_skeleton" as words: "Wither Skeleton". */
    private static String creatureWords(String entity) {
        StringBuilder sb = new StringBuilder();
        for (String word : shortToken(entity).split("_")) {
            if (word.isEmpty()) continue;
            sb.append(sb.isEmpty() ? "" : " ")
                    .append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /** A free variant name for this entity in this scope. */
    public static String suggestName(String entity, String scope, String scopeId) {
        return uniqueName(variantNameFor(entity, scope, scopeId), Config.tables().variants().keySet());
    }

    /**
     * Suggested name for a variant created FROM a mob row in a scoped list: {@code
     * <entity_short>_<scope_id_short>} (minecraft:creeper in minecraft:plains -> "creeper_plains"), so the
     * variant is addressable as its own thing.
     */
    public static String variantNameFor(String entity, String scope, String scopeId) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortToken(entity));
        if ("biome".equals(scope) || "tier".equals(scope)) {
            sb.append('_').append(shortToken(scopeId));
        }
        return sb.toString();
    }

    private static String shortToken(String id) {
        if (id == null) return "";
        String s = id.strip();
        int c = s.indexOf(':');
        if (c >= 0) s = s.substring(c + 1);
        return s.replaceAll("[^A-Za-z0-9_]", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** What the Name field starts with. */
    public static String initialName(boolean baseMobRow, String existingId,
                                     java.util.function.Supplier<String> suggestion) {
        if (baseMobRow) {
            return "";
        }
        return existingId == null ? suggestion.get() : existingId;
    }

    /** What a save wrote, and the name it saved under. */
    public record VariantSave(WriteReport report, String name) {
    }

    /**
     * Saves the editor's form, then reloads: the variant definition (or its rename), its preset, and its
     * spawn row in the scope being saved into.
     *
     * @param openedScope the scope the editor opened in (see {@link Opened})
     * @param openedScopeId its scope id
     */
    public static VariantSave save(EditorOpen at, String openedScope, String openedScopeId,
                                   VariantForm form) {
        return save(at, openedScope, openedScopeId, form, false);
    }

    /**
     * As above.
     *
     * @param goalSetPicked the designer picked a behavior in the editor: it replaces this scope's own AI list
     *     even when it stores blank ("From base mob", "Family default"; )
     */
    public static VariantSave save(EditorOpen at, String openedScope, String openedScopeId,
                                   VariantForm form, boolean goalSetPicked) {
        String entity = form.baseEntity() == null ? "" : form.baseEntity().strip();
        String name = form.name() == null ? "" : form.name().strip();
        String problem = formProblem(at, form, entity, name);
        if (problem != null) {
            return new VariantSave(WriteReport.refused(problem), name);
        }
        if (name.isEmpty() && !at.baseMobRow()) {
            name = suggestName(at.prefilledBase() != null ? at.prefilledBase() : entity,
                    at.sourceScope(), at.sourceScopeId());
        }
        // A SECOND COPY OF A PLAIN MOB.
        String displayName = form.displayName() == null ? "" : form.displayName().strip();
        if (name.isEmpty() && at.isNew() && at.baseMobRow()) {
            String copyScope = form.scope();
            String copyScopeId = "global".equals(copyScope) ? "" : form.scopeId().strip();
            if (hasPlainRow(CsvTable.read(Paths.table(Schema.MobSpawns.FILE)),
                    copyScope, copyScopeId, entity)) {
                name = copyName(entity, Config.tables().variants().keySet());
                if (displayName.isEmpty()) {
                    displayName = creatureWords(entity);
                }
            }
        }
        String coeff = form.coefficient() == null || form.coefficient().isBlank()
                ? "1.0" : form.coefficient().strip();
        // THE SCOPE BEING SAVED INTO.
        String saveScope = form.scope();
        String saveScopeId = "global".equals(saveScope) ? "" : form.scopeId().strip();

        if (isVariantName(name)) {
            Map<String, String> varValues = new LinkedHashMap<>();
            varValues.put(Schema.Variants.BASE_ENTITY, entity);
            varValues.put(Schema.Variants.DISPLAY_NAME, displayName);
            varValues.put(Schema.Variants.COEFFICIENT, coeff);
            varValues.put(Schema.Variants.TEXTURE_PATH, form.texture().strip());
            varValues.put(Schema.Variants.IS_OWN_ENTITY, String.valueOf(form.ownEntity()));
            // The mob's own switches.
            varValues.put(Schema.Variants.SCALING, com.strata.runtime.MobSwitches.normal(form.scaling()));
            varValues.put(Schema.Variants.MOB_GRIEFING, com.strata.runtime.MobSwitches.normal(form.mobGriefing()));
            varValues.put(Schema.Variants.START_LEVEL, form.startLevel() == null ? "" : form.startLevel().strip());
            boolean renaming = !at.isNew() && !at.originalName().isEmpty()
                    && !at.originalName().equals(name);
            WriteReport definition = renaming
                    ? rename(saveScope, saveScopeId, at.originalName(), name, varValues)
                    : saveDefinition(saveScope, saveScopeId, name, varValues);
            // Nothing below is worth attempting if this did not land.
            if (!definition.ok()) {
                return new VariantSave(definition, name);
            }
            WriteReport goalSet = saveChosenMobGoalSet(saveScope, saveScopeId, name,
                    form.goalSet() == null ? "" : form.goalSet(), goalSetPicked);
            if (!goalSet.ok()) {
                return new VariantSave(goalSet, name);
            }
        }

        boolean savingAsVariant = isVariantName(name);
        Map<String, String> spawnVals = spawnValues(form, entity, name, coeff, savingAsVariant);
        // prefilledBase is the entity the row had when the editor opened.
        WriteReport spawn = saveSpawnRow(openedScope, openedScopeId,
                at.isNew() ? null : at.originalName(), name, at.prefilledBase(),
                saveScope, saveScopeId, entity, spawnVals);
        // A NEW VARIANT IN A TIER OR BIOME TURNS ITS BASE MOB OFF THERE.
        if (spawn.ok() && savingAsVariant && at.isNew()) {
            WriteReport baseOff = disableBaseInScope(saveScope, saveScopeId, entity);
            if (!baseOff.ok()) {
                return new VariantSave(baseOff, name);
            }
        }
        if (spawn.ok() && !savingAsVariant) {
            WriteReport plain = savePlainExtras(form, saveScope, saveScopeId, entity, goalSetPicked);
            if (!plain.ok()) {
                return new VariantSave(plain, name);
            }
        }
        WriteReport done = Commit.reloadIfOk(spawn);
        // A NEW VARIANT IS ITS OWN MOB FROM THE NEXT WORLD LOAD (VariantEntityRegistry#registerNow).
        if (savingAsVariant && done.ok() && !com.strata.runtime.VariantEntityRegistry.isPromoted(name.strip())) {
            done.needsReload("Saved. Reload the world for " + name.strip() + " to spawn as its own mob");
        }
        return new VariantSave(done, name);
    }

    /** Why the editor's form cannot be saved, the first problem in the order the form shows; null when it can. */
    private static String formProblem(EditorOpen at, VariantForm form, String entity, String name) {
        String problem = entity.isEmpty() ? "Pick a base entity" : nameProblem(name, at);
        if (problem == null) {
            problem = targetProblem(form.scope(), form.scopeId());
        }
        if (problem == null) {
            problem = weightProblem(form.weight());
        }
        if (problem == null) {
            problem = groupProblem(form.minCount(), form.maxCount());
        }
        if (problem == null) {
            problem = chanceProblem(form.spawnChance());
        }
        if (problem == null) {
            problem = startLevelProblem(form.startLevel());
        }
        return problem;
    }

    /** The spawn row's values for the form. */
    private static Map<String, String> spawnValues(VariantForm form, String entity, String name, String coeff,
                                                   boolean savingAsVariant) {
        Map<String, String> spawnVals = new LinkedHashMap<>();
        // A variant with a category of its own keeps it.
        String ownCategory = savingAsVariant
                ? CategoryOps.editedVariantCategories().get(entity + "|" + name.strip()) : null;
        spawnVals.put(Schema.MobSpawns.CATEGORY, ownCategory != null
                ? EntityCategory.forVanillaCategory(ownCategory).id() : EntityCategory.of(entity).id());
        spawnVals.put(Schema.MobSpawns.WEIGHT, orDefault(form.weight(), "10"));
        spawnVals.put(Schema.MobSpawns.MIN_COUNT, orDefault(form.minCount(), "1"));
        spawnVals.put(Schema.MobSpawns.MAX_COUNT, orDefault(form.maxCount(), "4"));
        // Blank spawns whenever picked.
        spawnVals.put(Schema.MobSpawns.SPAWN_CHANCE, form.spawnChance() == null ? "" : form.spawnChance().strip());
        spawnVals.put(Schema.MobSpawns.ENABLED, String.valueOf(form.enabled()));
        spawnVals.put(Schema.MobSpawns.COEFFICIENT, coeff);
        spawnVals.put(Schema.MobSpawns.IS_VARIANT, String.valueOf(savingAsVariant));
        spawnVals.put(Schema.MobSpawns.VARIANT_MODE, savingAsVariant ? "add" : "");
        return spawnVals;
    }

    /** A plain mob's goal set, switches and category, after its spawn row. */
    private static WriteReport savePlainExtras(VariantForm form, String saveScope, String saveScopeId, String entity,
                                               boolean goalSetPicked) {
        WriteReport goalSet = savePlainMobGoalSet(saveScope, saveScopeId, entity, form.goalSet(), goalSetPicked);
        if (!goalSet.ok()) {
            return goalSet;
        }
        WriteReport switches = savePlainSwitches(saveScope, saveScopeId, entity, form.scaling(),
                form.mobGriefing(), form.startLevel());
        if (!switches.ok() || form.category().isBlank()) {
            return switches;
        }
        return CategoryOps.setCategory(entity, form.category());
    }

    /**
     * A PLAIN MOB'S SWITCHES (Scaling, Mob griefing), on its default variant's row in this scope, as its
     * goal set is.
     */
    private static WriteReport savePlainSwitches(String scope, String scopeId, String entity, String scaling,
                                                 String mobGriefing, String startLevel) {
        String scale = com.strata.runtime.MobSwitches.normal(scaling);
        String grief = com.strata.runtime.MobSwitches.normal(mobGriefing);
        String level = startLevel == null ? "" : startLevel.strip();
        String owner = Variant.defaultNameFor(entity);
        if (scale.equals(storedSwitch(Schema.Variants.SCALING, owner, scope, scopeId))
                && grief.equals(storedSwitch(Schema.Variants.MOB_GRIEFING, owner, scope, scopeId))
                && level.equals(storedCell(Schema.Variants.START_LEVEL, owner, scope, scopeId))) {
            return new WriteReport();
        }
        MobOps.DefaultVariant made = MobOps.ensureDefaultVariant(entity);
        if (!made.report().ok()) {
            return made.report();
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Variants.BASE_ENTITY, entity);
        values.put(Schema.Variants.SCALING, scale);
        values.put(Schema.Variants.MOB_GRIEFING, grief);
        values.put(Schema.Variants.START_LEVEL, level);
        return saveDefinition(scope, scopeId, owner, values);
    }

    /**
     * One switch of a mob or variant in a scope: Scaling or Mob griefing.
     *
     * @param column Schema.Variants.SCALING or MOB_GRIEFING
     * @param word Default, On, or Off
     */
    public static WriteReport saveSwitch(String entity, String variant, String scope, String scopeId,
                                         String column, String word) {
        if (!SWITCH_CHOICES.contains(word)) {
            return WriteReport.refused("Pick Default, On, or Off");
        }
        String value = com.strata.runtime.MobSwitches.normal(switchStored(word));
        String owner = variant == null || variant.isBlank() ? Variant.defaultNameFor(entity) : variant.strip();
        if (value.equals(storedSwitch(column, owner, scope, scopeId))) {
            return new WriteReport();
        }
        if (variant == null || variant.isBlank()) {
            MobOps.DefaultVariant made = MobOps.ensureDefaultVariant(entity);
            if (!made.report().ok()) {
                return made.report();
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Variants.BASE_ENTITY, entity);
        values.put(column, value);
        return Commit.reloadIfOk(saveDefinition(scope, scopeId, owner, values));
    }

    /** A mob's or variant's switch in a scope, as its word (Default, On, Off). */
    public static String switchOf(String entity, String variant, String scope, String scopeId, String column) {
        String owner = variant == null || variant.isBlank() ? Variant.defaultNameFor(entity) : variant.strip();
        return switchWord(storedSwitch(column, owner, scope, scopeId));
    }

    /**
     * A PLAIN MOB'S BEHAVIOR, stored on its default variant in this scope, where its equipment and abilities
     * already live.
     */
    private static WriteReport savePlainMobGoalSet(String scope, String scopeId, String entity,
                                               String goalSet, boolean picked) {
        String p = goalSet == null ? "" : goalSet.strip();
        String owner = Variant.defaultNameFor(entity);
        // With no default variant there is no own list to replace either.
        if (p.isEmpty() && !Config.tables().variants().containsKey(owner)) {
            return new WriteReport();
        }
        MobOps.DefaultVariant made = MobOps.ensureDefaultVariant(entity);
        if (!made.report().ok()) {
            return made.report();
        }
        return saveChosenMobGoalSet(scope, scopeId, made.name(), p, picked);
    }

    /**
     * The mob editor's behavior, saved.
     *
     * @param picked the designer picked a behavior in the editor
     */
    public static WriteReport saveChosenMobGoalSet(String scope, String scopeId, String variantName, String goalSet,
                                               boolean picked) {
        String p = goalSet == null ? "" : goalSet.strip();
        boolean replaces = replacesOwnList(p, storedGoalSet(variantName, scope, scopeId), picked);
        WriteReport report = saveMobGoalSet(scope, scopeId, variantName, p);
        if (replaces && report.ok()) {
            GoalsOps.removeOwn(report, variantName, scope, scopeId);
        }
        return report;
    }

    /**
     * Whether a behavior saved from the mob editor replaces the scope's own AI list: it was picked, or a
     * named one differs from the one stored.
     */
    public static boolean replacesOwnList(String goalSet, String stored, boolean picked) {
        String p = goalSet == null ? "" : goalSet.strip();
        return picked || (!p.isEmpty() && !p.equals(stored == null ? "" : stored.strip()));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    /** Deletes the variant the editor opened, then reloads. */
    public static WriteReport deleteFromEditor(EditorOpen at) {
        String scope = at.sourceScope() == null ? "" : at.sourceScope().strip();
        boolean scoped = !scope.isEmpty() && !"global".equalsIgnoreCase(scope);
        WriteReport report = scoped
                ? deleteInScope(scope, at.sourceScopeId() == null ? "" : at.sourceScopeId().strip(),
                        at.originalName())
                : deleteEverywhere(at.originalName());
        return Commit.reloadIfOk(report);
    }

    /** Writes the variant definition row itself. */
    public static WriteReport saveDefinition(String scope, String scopeId,
                                             String variantName, Map<String, String> values) {
        return new WriteReport().record("the variant", TableWriter.upsert(Schema.Variants.FILE,
                instanceKey(Schema.Variants.NAME, variantName, scope, scopeId), values));
    }

    /** The key of ONE VARIANT INSTANCE: its name plus the scope it lives in. */
    private static Map<String, String> instanceKey(String nameColumn, String variantName,
                                                   String scope, String scopeId) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(nameColumn, variantName);
        keys.put(Schema.Variants.SCOPE, normalScope(scope));
        keys.put(Schema.Variants.SCOPE_ID, normalScopeId(scope, scopeId));
        return keys;
    }

    /** "global" and "" are the same scope; blank is what lands on disk. */
    public static String normalScope(String scope) {
        String s = scope == null ? "" : scope.strip();
        return "global".equalsIgnoreCase(s) ? "" : s;
    }

    /** A global row carries no target, whatever the Target field happens to hold. */
    public static String normalScopeId(String scope, String scopeId) {
        if (normalScope(scope).isEmpty()) {
            return "";
        }
        return scopeId == null ? "" : scopeId.strip();
    }

    /** Stores the goal set for ONE INSTANCE of a mob: the variant in this scope, and no other. */
    public static WriteReport saveMobGoalSet(String scope, String scopeId,
                                         String variantName, String goalSet) {
        String p = goalSet == null ? "" : goalSet.strip();
        Map<String, String> keys = instanceKey(Schema.MobGoalSets.VARIANT_NAME, variantName, scope, scopeId);
        if (p.isEmpty()) {
            WriteReport report = new WriteReport();
            if (!storedGoalSet(variantName, scope, scopeId).isEmpty()) {
                report.record("the goal set choice", TableWriter.delete(Schema.MobGoalSets.FILE, keys));
            }
            return report;
        }
        return new WriteReport().record("the goal set choice",
                TableWriter.upsert(Schema.MobGoalSets.FILE, keys, Map.of(Schema.MobGoalSets.GOAL_SET, p)));
    }

    /** The goal set stored for one mob instance in this scope, or "" (mob_goal_sets.csv). */
    public static String storedGoalSet(String variantName, String scope, String scopeId) {
        String wantScope = normalScope(scope);
        String wantId = normalScopeId(scope, scopeId);
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobGoalSets.FILE))) {
            if (variantName.equals(row.getOrDefault(Schema.MobGoalSets.VARIANT_NAME, "").strip())
                    && wantScope.equals(normalScope(row.get(Schema.MobGoalSets.SCOPE)))
                    && wantId.equals(row.getOrDefault(Schema.MobGoalSets.SCOPE_ID, "").strip())) {
                return row.getOrDefault(Schema.MobGoalSets.GOAL_SET, "").strip();
            }
        }
        return "";
    }

    /**
     * Writes this variant's spawn row for the scope it is being saved into, clearing only that scope's prior
     * row first (see {@link #clearsOnSave}).
     *
     * @param openedName the name the row had when the editor opened, or null for a new row
     * @param openedEntity the entity the row had when the editor opened. It picks out a plain mob's own prior
     *     row.
     */
    /** Sets the plain mob's spawn row in a tier or biome to off, when that scope has one. */
    public static WriteReport disableBaseInScope(String scope, String scopeId, String entity) {
        WriteReport report = new WriteReport();
        String sc = scope == null ? "" : scope.strip();
        String sid = scopeId == null ? "" : scopeId.strip();
        if (!("tier".equals(sc) || "biome".equals(sc)) || sid.isEmpty() || entity == null) {
            return report;
        }
        for (var row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (sc.equals(row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip())
                    && sid.equals(row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip())
                    && entity.strip().equals(row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip())
                    && row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip().isEmpty()) {
                Map<String, String> keys = new LinkedHashMap<>();
                keys.put(Schema.MobSpawns.SCOPE, sc);
                keys.put(Schema.MobSpawns.SCOPE_ID, sid);
                keys.put(Schema.MobSpawns.ENTITY, entity.strip());
                keys.put(Schema.MobSpawns.VARIANT_NAME, "");
                return report.record("the base mob's spawn row",
                        TableWriter.upsert(Schema.MobSpawns.FILE, keys,
                                Map.of(Schema.MobSpawns.ENABLED, "false")));
            }
        }
        return report;
    }

    public static WriteReport saveSpawnRow(String openedScope, String openedScopeId,
                                           String openedName, String variantName,
                                           String openedEntity,
                                           String scope, String scopeId,
                                           String entity, Map<String, String> spawnValues) {
        WriteReport report = new WriteReport();
        for (var prior : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (clearsOnSave(openedScope, openedScopeId, openedName, variantName, openedEntity,
                    prior.get(Schema.MobSpawns.SCOPE),
                    prior.get(Schema.MobSpawns.SCOPE_ID),
                    prior.get(Schema.MobSpawns.VARIANT_NAME),
                    prior.get(Schema.MobSpawns.ENTITY))) {
                Map<String, String> priorKeys = new LinkedHashMap<>(prior);
                priorKeys.replaceAll((k, v) -> v.strip());
                // Read from the file a moment ago, so a false is a real write failure and not an absent row.
                report.record("the variant's previous spawn row",
                        TableWriter.delete(Schema.MobSpawns.FILE, priorKeys));
                if (!report.ok()) return report;
            }
        }
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobSpawns.SCOPE, scope);
        keys.put(Schema.MobSpawns.SCOPE_ID, scopeId);
        keys.put(Schema.MobSpawns.ENTITY, entity);
        keys.put(Schema.MobSpawns.VARIANT_NAME, variantName);
        // A variant placed in a biome is intent by definition - nothing seeds one - so it carries the same
        // stamp a plain mob's save does.
        return report.record("the variant's spawn row",
                TableWriter.upsert(Schema.MobSpawns.FILE, keys,
                        MobOps.stampDesigner(spawnValues)));
    }

    /** SCOPE-AWARE DELETE. */
    public static WriteReport deleteInScope(String scope, String scopeId, String variantName) {
        WriteReport report = new WriteReport();
        String sc = scope == null ? "" : scope.strip();
        String sid = scopeId == null ? "" : scopeId.strip();
        for (var row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (Parse.flag(row.get(Schema.MobSpawns.IS_VARIANT), false)
                    && variantName.equals(row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip())
                    && sc.equals(row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip())
                    && sid.equals(row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip())) {
                String entity = row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip();
                report.record("a spawn row", TableWriter.delete(Schema.MobSpawns.FILE, Map.of(
                        Schema.MobSpawns.SCOPE, sc,
                        Schema.MobSpawns.SCOPE_ID, sid,
                        Schema.MobSpawns.ENTITY, entity,
                        Schema.MobSpawns.VARIANT_NAME, variantName)));
                deleteScopedRows(report, Schema.Loot.FILE, sc, sid, entity, variantName);
                deleteScopedRows(report, Schema.SpawnRules.FILE, sc, sid, entity, variantName);
            }
        }
        return report;
    }

    /**
     * Removes the variant everywhere: its definition, stats, abilities, equipment, and every scope's spawn
     * rows with their loot and conditions.
     */
    public static WriteReport deleteEverywhere(String variantName) {
        WriteReport report = new WriteReport();
        // Deleting the variant outright clears its stats in EVERY scope - that is the difference between
        // removing an instance (deleteInScope) and removing the variant itself.
        for (String scope : allScopesWithStats(variantName)) {
            String[] parts = scope.split("\\|", -1);
            deleteStatsInScope(report, parts[0], parts[1], variantName);
        }
        deleteAllForVariant(report, Schema.Abilities.FILE, variantName);
        deleteAllForVariant(report, Schema.Equipment.FILE, variantName);
        deleteAllForVariant(report, Schema.MobGoals.FILE, variantName);
        deleteAllForVariant(report, Schema.MobGoalSets.FILE, variantName);
        deleteAllForVariant(report, Schema.MobData.FILE, variantName);
        for (String file : Schema.MOB_PROPERTY_FILES.values()) {
            deleteAllForVariant(report, file, variantName);
        }
        // Its Spawn as row ("entity#name"), so a new variant of this name does not take it over.
        for (var row : CsvTable.read(Paths.table(Schema.SpecialSpawns.FILE))) {
            if (row.getOrDefault(Schema.SpecialSpawns.SETTING, "").strip()
                    .endsWith(com.strata.model.SpecialSpawners.VARIANT_SEP + variantName)) {
                Map<String, String> keys = new LinkedHashMap<>(row);
                keys.replaceAll((k, v) -> v.strip());
                report.record("a Spawn as row", TableWriter.delete(Schema.SpecialSpawns.FILE, keys));
            }
        }
        report.record("the variant", TableWriter.delete(Schema.Variants.FILE,
                Map.of(Schema.Variants.NAME, variantName)));
        if (!report.ok()) return report;
        for (var row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
            if (Parse.flag(row.get(Schema.MobSpawns.IS_VARIANT), false)
                    && variantName.equals(row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip())) {
                String scope = row.getOrDefault(Schema.MobSpawns.SCOPE, "").strip();
                String scopeId = row.getOrDefault(Schema.MobSpawns.SCOPE_ID, "").strip();
                String entity = row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip();
                report.record("a spawn row", TableWriter.delete(Schema.MobSpawns.FILE, Map.of(
                        Schema.MobSpawns.SCOPE, scope, Schema.MobSpawns.SCOPE_ID, scopeId,
                        Schema.MobSpawns.ENTITY, entity, Schema.MobSpawns.VARIANT_NAME, variantName)));
                deleteScopedRows(report, Schema.Loot.FILE, scope, scopeId, entity, variantName);
                deleteScopedRows(report, Schema.SpawnRules.FILE, scope, scopeId, entity, variantName);
            }
        }
        return report;
    }

    /** Clears a variant's stat rows IN ONE SCOPE, ahead of rewriting them. */
    public static WriteReport deleteStatsInScope(WriteReport report, String scope,
                                                 String scopeId, String variantName) {
        String wantScope = normalScope(scope);
        String wantScopeId = normalScopeId(scope, scopeId);
        for (var row : CsvTable.read(Paths.table(Schema.VariantStats.FILE))) {
            boolean sameScope =
                    wantScope.equals(normalScope(row.getOrDefault(Schema.VariantStats.SCOPE, "")))
                    && wantScopeId.equals(row.getOrDefault(Schema.VariantStats.SCOPE_ID, "").strip());
            if (sameScope
                    && variantName.equals(row.getOrDefault(Schema.VariantStats.VARIANT_NAME, "").strip())) {
                // Just read from the table, so false is a write failure, not an absent row.
                Map<String, String> keys = instanceKey(
                        Schema.VariantStats.VARIANT_NAME, variantName, scope, scopeId);
                keys.put(Schema.VariantStats.ATTRIBUTE,
                        row.getOrDefault(Schema.VariantStats.ATTRIBUTE, "").strip());
                report.record("a stat row",
                        TableWriter.delete(Schema.VariantStats.FILE, keys));
            }
        }
        return report;
    }

    /** Every scope a variant currently has stat rows in, as "scope|scope_id". */
    private static java.util.Set<String> allScopesWithStats(String variantName) {
        java.util.Set<String> scopes = new java.util.LinkedHashSet<>();
        for (var row : CsvTable.read(Paths.table(Schema.VariantStats.FILE))) {
            if (variantName.equals(row.getOrDefault(Schema.VariantStats.VARIANT_NAME, "").strip())) {
                scopes.add(normalScope(row.getOrDefault(Schema.VariantStats.SCOPE, ""))
                        + "|" + row.getOrDefault(Schema.VariantStats.SCOPE_ID, "").strip());
            }
        }
        return scopes;
    }

    /** Clears every row for a variant in a variant-keyed table. */
    public static WriteReport deleteAllForVariant(WriteReport report, String fileName, String variantName) {
        for (var row : CsvTable.read(Paths.table(fileName))) {
            if (variantName.equals(row.getOrDefault(Schema.Equipment.VARIANT_NAME, "").strip())) {
                Map<String, String> keys = new LinkedHashMap<>(row);
                keys.replaceAll((k, v) -> v.strip());
                report.record("a " + fileName + " row", TableWriter.delete(fileName, keys));
            }
        }
        return report;
    }

    /**
     * Whether a drops / spawn_conditions row belongs to ONE variant instance - the entity and scope from the
     * caller, plus the variant name.
     */
    public static boolean deletesScopedRow(String scope, String scopeId, String entity,
                                           String variantName, String rowScope,
                                           String rowScopeId, String rowEntity,
                                           String rowVariantName) {
        String sc = scope == null ? "" : scope.strip();
        String sid = scopeId == null ? "" : scopeId.strip();
        String ent = entity == null ? "" : entity.strip();
        String vn = variantName == null ? "" : variantName.strip();
        return sc.equals(rowScope == null ? "" : rowScope.strip())
                && sid.equals(rowScopeId == null ? "" : rowScopeId.strip())
                && ent.equals(rowEntity == null ? "" : rowEntity.strip())
                && vn.equals(rowVariantName == null ? "" : rowVariantName.strip());
    }

    /** Package-private: MobOps#removeFromScope clears a plain mob's rows with it. */
    static void deleteScopedRows(WriteReport report, String fileName,
                                         String scope, String scopeId, String entity,
                                         String variantName) {
        for (var row : CsvTable.read(Paths.table(fileName))) {
            if (deletesScopedRow(scope, scopeId, entity, variantName,
                    row.get(Schema.Loot.SCOPE),
                    row.get(Schema.Loot.SCOPE_ID),
                    row.get(Schema.Loot.ENTITY),
                    row.get(Schema.Loot.VARIANT_NAME))) {
                Map<String, String> keys = new LinkedHashMap<>(row);
                keys.replaceAll((k, v) -> v.strip());
                report.record("a " + fileName + " row", TableWriter.delete(fileName, keys));
            }
        }
    }
}
