package com.strata.domain;

import com.strata.data.GoalTable;
import com.strata.data.GoalSetTable;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.GoalRow;
import com.strata.model.GoalSets;
import com.strata.model.GameGoal;
import com.strata.model.GoalGuide;
import com.strata.model.GoalType;
import com.strata.model.Variant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Everything the AI editors read and do: a mob's own AI list, and the archetype library. */
public final class GoalsOps {

    private GoalsOps() {
    }

    // ---- where a mob's AI comes from ----

    /**
     * The list a mob runs, and where it comes from, as the runtime picks it (GoalsRuntime#listFor): its own
     * list, the archetype its behavior field names, its base mob's, or the game's own (rows null).
     */
    public record Effective(List<GoalRow> rows, String source, boolean own, String name) {
    }

    /**
     * The Behavior button's text: the behavior the mob runs, by name, and "(default)" when nothing was chosen
     * here.
     *
     * @param stored the behavior field as stored: blank when nothing was chosen
     */
    public static String goalSetLabel(Effective effective, String stored) {
        boolean chosen = effective.own() || (stored != null && !stored.isBlank());
        return "Goal set: " + effective.name() + (chosen ? "" : " (default)");
    }

    /** What a mob runs when it runs the game's own AI, as its behavior's name. */
    public static final String GAME_GOALS = "game goals";
    /** What a mob runs when it has its own list, as its behavior's name. */
    public static final String OWN_LIST = "own list";

    /** A mob instance's own rows, in order; empty when it has no own list. */
    public static List<GoalRow> ownList(String owner, String scope, String scopeId) {
        String wantScope = VariantOps.normalScope(scope);
        String wantId = VariantOps.normalScopeId(scope, scopeId);
        List<Map<String, String>> cells = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobGoals.FILE))) {
            if (owner.equals(row.getOrDefault(Schema.MobGoals.VARIANT_NAME, "").strip())
                    && wantScope.equals(VariantOps.normalScope(row.get(Schema.MobGoals.SCOPE)))
                    && wantId.equals(row.getOrDefault(Schema.MobGoals.SCOPE_ID, "").strip())) {
                cells.add(row);
            }
        }
        return GoalTable.rows(cells);
    }

    /**
     * The mob's AI in this scope, for the editor.
     *
     * @param owner the variant, or the plain mob's default variant
     * @param baseEntity the mob it is made from
     * @param preset the behavior field as stored: "" inherit, "none", or an archetype
     */
    public static Effective effective(String owner, String baseEntity, String scope, String scopeId, String goalSet) {
        return effective(owner, baseEntity, scope, scopeId, goalSet, false);
    }

    /**
     * As above, for the mob editor's form.
     *
     * @param picked the designer picked a behavior in the editor, not saved yet
     */
    public static Effective effective(String owner, String baseEntity, String scope, String scopeId, String goalSet,
                                      boolean picked) {
        return effective(ownLists(), chosenGoalSets(), library(), owner, baseEntity,
                com.strata.domain.ScopeLayers.scopeLayers(scope, scopeId), goalSet, picked, familyOf(baseEntity));
    }

    /**
     * The mob's AI in this scope, as the runtime walks it (GoalsRuntime#pick): the nearest scope's own list
     * or behavior, then its base mob's, then its family's, else the game's.
     *
     * @param own own lists by ScopeIndex key
     * @param presets stored behavior choices by ScopeIndex key
     * @param layers the mob's scope and the wider ones, nearest first
     * @param picked the designer picked a behavior in the editor, not saved yet
     * @param family the mob's family archetype, or null
     */
    public static Effective effective(Map<String, List<GoalRow>> own, Map<String, String> chosenGoalSets,
                                      Map<String, List<GoalRow>> library, String owner, String baseEntity,
                                      List<com.strata.domain.ScopeLayers.ScopeLayer> layers, String goalSet, boolean picked,
                                      String family) {
        String chosen = goalSet == null ? "" : goalSet.strip();
        com.strata.domain.ScopeLayers.ScopeLayer here = layers.getFirst();
        String hereKey = com.strata.runtime.ScopeIndex.key(here.scope(), here.scopeId(), owner);
        Map<String, List<GoalRow>> ownNow = new HashMap<>(own);
        Map<String, String> goalSetsNow = new HashMap<>(chosenGoalSets);
        if (VariantOps.replacesOwnList(chosen, chosenGoalSets.getOrDefault(hereKey, ""), picked)) {
            ownNow.remove(hereKey);
        }
        goalSetsNow.put(hereKey, chosen);
        String base = Variant.defaultNameFor(baseEntity);
        List<String[]> walk = layers.stream().map(l -> new String[]{l.scope(), l.scopeId()}).toList();
        var pick = com.strata.runtime.GoalsRuntime.pick(ownNow, goalSetsNow, library, owner,
                base.equals(owner) ? "" : base, walk);
        if (pick == null) {
            // ITS FAMILY'S ARCHETYPE: the mobs with the same AI in the game share one, named for them
            // ("zombie").
            return family != null && library.containsKey(family)
                    ? new Effective(library.get(family), "its family's goal set '" + family + "'", false, family)
                    : gameAi(baseEntity);
        }
        if (pick.rows() == null) {
            return gameAi(baseEntity);
        }
        String where = pick.base() ? "the base mob's " : pick.layer() == 0 ? ""
                : com.strata.domain.ScopeLayers.FROM_TIER.equals(layers.get(pick.layer()).from()) ? "the tier's "
                : "the global mob's ";
        if (pick.ownList()) {
            boolean mine = !pick.base() && pick.layer() == 0;
            return new Effective(pick.rows(), mine ? "this mob's own list" : where + "list", mine,
                    mine ? OWN_LIST : where + "list");
        }
        return new Effective(pick.rows(), where + "goal set '" + pick.goalSet() + "'", false, pick.goalSet());
    }

    /** Every own list on disk, by ScopeIndex key, as GoalsRuntime#load reads them. */
    private static Map<String, List<GoalRow>> ownLists() {
        Map<String, List<Map<String, String>>> grouped = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobGoals.FILE))) {
            String variant = row.getOrDefault(Schema.MobGoals.VARIANT_NAME, "").strip();
            if (!variant.isEmpty()) {
                grouped.computeIfAbsent(com.strata.runtime.ScopeIndex.key(row.getOrDefault(Schema.MobGoals.SCOPE, ""),
                        row.getOrDefault(Schema.MobGoals.SCOPE_ID, ""), variant), k -> new ArrayList<>()).add(row);
            }
        }
        Map<String, List<GoalRow>> out = new HashMap<>();
        grouped.forEach((k, rows) -> out.put(k, GoalTable.rows(rows)));
        return out;
    }

    /** Every stored goal set choice (mob_goal_sets.csv), by ScopeIndex key, as the runtime reads it. */
    private static Map<String, String> chosenGoalSets() {
        Map<String, String> out = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobGoalSets.FILE))) {
            String variant = row.getOrDefault(Schema.MobGoalSets.VARIANT_NAME, "").strip();
            String p = row.getOrDefault(Schema.MobGoalSets.GOAL_SET, "").strip();
            if (!variant.isEmpty() && !p.isEmpty()) {
                out.put(com.strata.runtime.ScopeIndex.key(row.getOrDefault(Schema.MobGoalSets.SCOPE, ""),
                        row.getOrDefault(Schema.MobGoalSets.SCOPE_ID, ""), variant), p);
            }
        }
        return out;
    }

    /** The name of the mob's family archetype, or null when it has none (a brain AI). */
    public static String familyOf(String entity) {
        return GoalFamilies.familyByMob(GoalFamilies.fromGameLayer()).get(entity == null ? "" : entity.strip());
    }

    /**
     * SEEDS THE FAMILY ARCHETYPES: every mob family from the game becomes an archetype in goal_sets.csv,
     * once.
     *
     * @return how many archetypes were added; -1 when the write failed
     */
    public static int seedFamilies(List<GameLayer.Row> goalSheet, List<GameLayer.Row> mobSheet) {
        java.nio.file.Path file = Paths.table(Schema.GoalSets.FILE);
        try {
            if (!java.nio.file.Files.isRegularFile(file)) {
                // The rows are appended below, so the file needs its header.
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, GoalSetTable.csv(Map.of()));
            }
        } catch (java.io.IOException e) {
            return -1;
        }
        Map<String, List<GoalRow>> missing = GoalFamilies.missing(GoalFamilies.families(goalSheet, mobSheet),
                library().keySet());
        if (missing.isEmpty()) {
            return 0;
        }
        List<Map<String, String>> rows = new ArrayList<>();
        missing.forEach((name, list) -> rows.addAll(GoalSetTable.toRows(name, list)));
        return TableWriter.appendRows(Schema.GoalSets.FILE, rows) ? missing.size() : -1;
    }

    /** The mob's own AI from the game layer, with its goals. */
    private static Effective gameAi(String baseEntity) {
        List<GoalRow> rows = fromGame(extractedGoals(baseEntity));
        return rows.isEmpty()
                ? new Effective(null, gameGoalsName(baseEntity) + ", a Brain (goals you add run beside it)", false, GAME_GOALS)
                : new Effective(rows, gameGoalsName(baseEntity) + ", from the game", false, GAME_GOALS);
    }

    /** "zombie's own goals", from the id. */
    public static String gameGoalsName(String entity) {
        String id = entity == null ? "" : entity.strip();
        String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return name.isEmpty() ? "the game's own goals" : name + "'s own goals";
    }

    /** What a list does, in two short parts: who it hunts, and what it does, in priority order. */
    public static String summary(List<GoalRow> rows) {
        java.util.Set<String> who = new java.util.LinkedHashSet<>();
        java.util.Set<String> how = new java.util.LinkedHashSet<>();
        for (GoalRow row : sorted(rows)) {
            if (!row.enabled()) {
                continue;
            }
            String target = row.settings().get("target");
            if (row.target()) {
                who.add(target != null ? readable(target)
                        : row.goal().equals("HurtByTargetGoal") ? "whoever hurts it" : row.name());
            } else {
                how.add(row.name());
            }
        }
        List<String> does = new ArrayList<>(how);
        String doesText = does.size() <= 5 ? String.join(", ", does)
                : String.join(", ", does.subList(0, 5)) + ", and " + (does.size() - 5) + " more";
        return (who.isEmpty() ? "Hunts nothing." : "Hunts: " + String.join(", ", who) + ".")
                + (does.isEmpty() ? "" : " Does: " + doesText + ".");
    }

    /** A mob's AI as text for the mob editor's box. */
    public static List<String> goalsText(List<GoalRow> rows) {
        if (rows == null) {
            return List.of("A Brain runs this mob. Goals you add run beside it:", "while one of them moves the mob, the Brain waits.");
        }
        List<String> out = new ArrayList<>();
        for (boolean target : new boolean[]{true, false}) {
            out.add(target ? "Target goals (who it attacks):" : "Goals (what it does):");
            int before = out.size();
            for (GoalRow row : sorted(rows)) {
                if (row.target() != target) {
                    continue;
                }
                String who = row.settings().get("target");
                String line = "  " + row.priority() + "  " + row.name()
                        + (who != null ? ": " + readable(who) : "")
                        + (row.condition().type() == com.strata.model.GoalCondition.Type.ALWAYS ? ""
                        : ", only " + row.condition().type().word().replace('_', ' ')
                        + (row.condition().type().takesValue() ? " " + row.condition().value() : ""))
                        + (row.enabled() ? "" : "  [off]");
                out.add(line);
            }
            if (out.size() == before) {
                out.add("  none");
            }
        }
        return out;
    }

    /** A list seeded from the mob's live goals: its real values. */
    public static List<GoalRow> fromGame(List<GameGoal> goals) {
        List<GoalRow> out = new ArrayList<>();
        goals.forEach(g -> out.add(GoalRow.fromGame(g)));
        return sorted(out);
    }

    // ---------------------------------------------- the game layer

    /** The mob's own goals as the game layer holds them (game/goals.csv), or empty when it has none. */
    public static List<GameGoal> extractedGoals(String entity) {
        return goalsOf(GameLayer.read(GameLayer.dir().resolve(GameLayer.GOALS)), entity);
    }

    /** One mob's goals from the goal sheet's rows, in list order. */
    public static List<GameGoal> goalsOf(List<GameLayer.Row> sheet, String entity) {
        List<GameGoal> out = new ArrayList<>();
        for (GameLayer.Row r : sheet) {
            if (r.entity().equals(entity) && GameLayer.GOAL.equals(r.group())) {
                out.add(GameGoal.decode(r.value()));
            }
        }
        return out;
    }

    /** A mob's own AI as a list to start from, its own goals included: they apply to a mob that has them. */
    public static List<GoalRow> copyOf(String entity) {
        return new ArrayList<>(fromGame(goalsOf(GameLayer.read(GameLayer.dir().resolve(GameLayer.GOALS)), entity)));
    }

    /** WHY A GOAL WILL NOT RUN ON THIS MOB, or null when it will or it cannot be told. */
    public static String whyNot(GoalRow row, String entity) {
        if (entity == null || entity.isBlank()) {
            return null;
        }
        return whyNot(row, needsMet(entity), ownGoalClasses(entity));
    }

    /** The goal needs a mob type meets, from the game layer, or null when not read. */
    public static java.util.Set<String> needsMet(String entity) {
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS))) {
            if (r.entity().equals(entity) && GameLayer.TYPE.equals(r.group())
                    && com.strata.model.GoalType.NEEDS_MET.equals(r.key())) {
                return new java.util.HashSet<>(List.of(r.value().split(GoalFamilies.CHAIN_SEP)));
            }
        }
        return null;
    }

    /** The classes of a mob type's own goals, from the game layer. */
    public static java.util.Set<String> ownGoalClasses(String entity) {
        java.util.Set<String> own = new java.util.HashSet<>();
        extractedGoals(entity).forEach(g -> own.add(g.className()));
        return own;
    }

    /**
     * Pure: why the row will not run on a mob that meets these needs (null when unknown) and has these goal
     * classes of its own.
     */
    public static String whyNot(GoalRow row, java.util.Set<String> met, java.util.Set<String> own) {
        if (row.game()) {
            // A game goal is taken from the mob's own goals: another mob's is not there.
            return own.contains(row.goal()) ? null
                    : "this mob has no such goal of its own (it is another mob's, and only that mob can run it)";
        }
        com.strata.model.GoalType.Type type = com.strata.model.GoalType.of(row.goal());
        if (type == null) {
            return "Strata cannot build this goal";
        }
        if (met == null || met.contains(type.needs().name())) {
            return null;
        }
        return "needs " + com.strata.model.GoalType.needsWord(type.needs());
    }

    /** Every mob with extracted goals, for "Copy from mob". */
    public static List<String> mobsWithGoals() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.GOALS))) {
            out.add(r.entity());
        }
        return List.copyOf(out);
    }

    /** Who a goal can target: every living entity, then every class group the game's goals use. */
    public static List<String> targetChoices() {
        return targetChoices(GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS)),
                GameLayer.read(GameLayer.dir().resolve(GameLayer.GOALS)));
    }

    /**
     * The player, every entity with attributes (a living one) in the mob sheet, then every "class:" group a
     * game goal names.
     */
    public static List<String> targetChoices(List<GameLayer.Row> mobSheet, List<GameLayer.Row> goalSheet) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        ids.add(GoalType.PLAYER);
        java.util.Set<String> living = new java.util.TreeSet<>();
        for (GameLayer.Row r : mobSheet) {
            if (GameLayer.ATTRIBUTE.equals(r.group())) {
                living.add(r.entity());
            }
        }
        ids.addAll(living);
        java.util.Set<String> groups = new java.util.TreeSet<>();
        Pattern target = Pattern.compile("(?:^|[;|])target=(" + Pattern.quote(GoalType.CLASS) + "[^;|]+)");
        for (GameLayer.Row r : goalSheet) {
            var m = target.matcher(r.value());
            while (m.find()) {
                groups.add(m.group(1));
            }
        }
        ids.addAll(groups);
        return List.copyOf(ids);
    }

    /**
     * A target in words, for the summary: "iron golem", "any villager" (a class group without its "Abstract"
     * prefix).
     */
    public static String readable(String target) {
        if (target.startsWith(GoalType.CLASS)) {
            String simple = target.substring(target.lastIndexOf('.') + 1).replaceFirst("^Abstract", "");
            return "any " + simple.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
        }
        return target.substring(target.indexOf(':') + 1).replace('_', ' ');
    }

    /** A target as the editor shows it: the id, or "any" and the group's class name. */
    public static String targetLabel(String target) {
        return target.startsWith(GoalType.CLASS) ? "any " + target.substring(target.lastIndexOf('.') + 1) : target;
    }

    /** WHO before HOW, each by priority: the order the editor shows. */
    public static List<GoalRow> sorted(Collection<GoalRow> rows) {
        List<GoalRow> out = new ArrayList<>(rows);
        out.sort(Comparator.comparing((GoalRow r) -> !r.target()).thenComparingInt(GoalRow::priority));
        return out;
    }

    // ---------------------------------------------- how a list fights

    /** The attack reader over the game layer's goal chains (game/goals.csv). */
    public static com.strata.model.Attacks attacks() {
        return attacks(GameLayer.read(GameLayer.dir().resolve(GameLayer.GOALS)));
    }

    /** The attack reader over a goal sheet's rows, every mob's goals. */
    public static com.strata.model.Attacks attacks(List<GameLayer.Row> goalSheet) {
        Map<String, List<GameGoal>> byMob = new LinkedHashMap<>();
        for (GameLayer.Row r : goalSheet) {
            if (GameLayer.GOAL.equals(r.group())) {
                byMob.computeIfAbsent(r.entity(), k -> new ArrayList<>()).add(GameGoal.decode(r.value()));
            }
        }
        return com.strata.model.Attacks.of(byMob);
    }

    /** The game's attack damage attribute, as the mob sheet names it. */
    public static final String ATTACK_DAMAGE = "minecraft:generic.attack_damage";

    /** The mob's attack damage from the game layer (game/mobs.csv), or null when it has none. */
    public static Double attackDamage(String entity) {
        return attackDamage(GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS)), entity);
    }

    // ---- the picked goals

    /** The list without the rows at these indexes. */
    public static List<GoalRow> withoutRows(List<GoalRow> rows, Collection<Integer> indexes) {
        List<GoalRow> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (!indexes.contains(i)) {
                out.add(rows.get(i));
            }
        }
        return out;
    }

    /** The list with the rows at these indexes switched: all off when any is on, else all on. */
    public static List<GoalRow> switchedRows(List<GoalRow> rows, Collection<Integer> indexes) {
        boolean on = Picks.switchedOn(indexes.stream().map(i -> rows.get(i).enabled()).toList());
        List<GoalRow> out = new ArrayList<>(rows);
        for (int i : indexes) {
            out.set(i, rows.get(i).withEnabled(on));
        }
        return out;
    }

    /** Returns a new list with ATTACK-PLAYER target goals disabled. */
    public static List<GoalRow> disableAttackPlayerGoals(List<GoalRow> rows) {
        List<GoalRow> out = new ArrayList<>(rows);
        for (int i = 0; i < out.size(); i++) {
            GoalRow row = out.get(i);
            if (!row.target()) continue;  // only WHO list
            if (!row.enabled()) continue;  // already off

            boolean isAttackPlayer = false;
            if (row.game()) {
                // GAME row: class name like SpiderTargetGoal, ZombieAttackGoal, etc.
                String className = row.goal().toLowerCase(java.util.Locale.ROOT);
                if (className.contains("nearestattackabletargetgoal")
                        || className.contains("attackgoaltarget")) {
                    isAttackPlayer = true;
                }
            } else {
                // BUILT row: goal is the GoalType simple name, settings has "target"
                if ("NearestAttackableTargetGoal".equals(row.goal())) {
                    String target = row.settings().get("target");
                    if (target != null && (target.equals("minecraft:player")
                            || target.startsWith("class:") && target.toLowerCase(java.util.Locale.ROOT).contains("player"))) {
                        isAttackPlayer = true;
                    }
                }
            }
            if (isAttackPlayer) {
                out.set(i, row.withEnabled(false));
            }
        }
        return out;
    }

    /** The mob's attack damage from the mob sheet's rows, or null when it has none. */
    public static Double attackDamage(List<GameLayer.Row> mobSheet, String entity) {
        for (GameLayer.Row r : mobSheet) {
            if (r.entity().equals(entity) && GameLayer.ATTRIBUTE.equals(r.group()) && ATTACK_DAMAGE.equals(r.key())) {
                try {
                    return Double.parseDouble(r.value());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---- saving a mob's own list ----

    /** Why a list cannot be saved, or null. */
    public static String listProblem(List<GoalRow> rows) {
        for (GoalRow row : rows) {
            String problem = row.problem();
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    // ---- Save on a mob's AI ----

    /**
     * Where Save puts a mob's AI.
     *
     * @param behavior the behavior the list is saved into
     * @param isNew true when the behavior is made now, and the mob uses it by name
     */
    public record SaveTarget(String goalSet, boolean isNew) {
    }

    /**
     * SAVE EDITS THE BEHAVIOR THE MOB RUNS.
     *
     * @param library the behavior names
     * @param mobName the mob's name, for a new behavior's name
     */
    public static SaveTarget saveTarget(Effective effective, Collection<String> library, String mobName) {
        if (!effective.own() && library.contains(effective.name())) {
            return new SaveTarget(effective.name(), false);
        }
        return new SaveTarget(freeName(mobName, library), true);
    }

    /** A behavior name from a mob's name that no behavior has: "zombie", "zombie_2". */
    public static String freeName(String mobName, Collection<String> taken) {
        String base = (mobName == null ? "" : mobName.strip().toLowerCase(java.util.Locale.ROOT))
                .replaceAll("[^a-z0-9_\\-]", "_").replaceAll("^[_\\-]+", "");
        if (base.isEmpty()) {
            base = "goal_set";
        }
        base = base.substring(0, Math.min(base.length(), 34));
        String n = base;
        for (int i = 2; taken.contains(n) || GoalSets.NONE.equals(n); i++) {
            n = base + "_" + i;
        }
        return n;
    }

    /** A mob's name for a new behavior: the variant's, or the plain mob's id path ("zombie"). */
    public static String mobName(String owner, String baseEntity) {
        return owner.equals(Variant.defaultNameFor(baseEntity))
                ? baseEntity.substring(baseEntity.indexOf(':') + 1) : owner;
    }

    /** A mob's AI save: what was written, and where. */
    public record MobGoalsSave(WriteReport report, SaveTarget target) {
    }

    /**
     * Saves a mob's AI list where {@link #saveTarget} says, then reloads.
     *
     * @param preset the behavior field as the mob's editor holds it
     */
    public static MobGoalsSave saveMobGoals(String owner, String baseEntity, String scope, String scopeId, String goalSet,
                                      List<GoalRow> rows) {
        SaveTarget target = saveTarget(effective(owner, baseEntity, scope, scopeId, goalSet), goalSetNames(),
                mobName(owner, baseEntity));
        if (target.isNew() && owner.equals(Variant.defaultNameFor(baseEntity))) {
            // A plain mob's behavior choice lives on its default variant, made now if it has none
            // (MobOps#ensureDefaultVariant).
            WriteReport made = MobOps.ensureDefaultVariant(baseEntity).report();
            if (!made.ok()) {
                return new MobGoalsSave(made, target);
            }
        }
        if (target.isNew()) {
            return new MobGoalsSave(nameOwnList(owner, scope, scopeId, target.goalSet(), rows), target);
        }
        WriteReport report = saveGoalSetInto(target.goalSet(), target.goalSet(), rows);
        // THE SAVED SET IS THE MOB'S NOW: a set picked in the mob editor but not saved there yet was edited
        // and saved here, and the mob still ran its old one until the mob editor's Save too.
        String chosen = goalSet == null ? "" : goalSet.strip();
        if (report.ok() && chosen.equals(target.goalSet())
                && !chosen.equals(VariantOps.storedGoalSet(owner, scope, scopeId))) {
            if (owner.equals(Variant.defaultNameFor(baseEntity))) {
                WriteReport made = MobOps.ensureDefaultVariant(baseEntity).report();
                if (!made.ok()) {
                    return new MobGoalsSave(made, target);
                }
            }
            for (String failure : VariantOps.saveMobGoalSet(scope, scopeId, owner, chosen).failures()) {
                report.record(failure, false);
            }
            if (report.ok()) {
                removeOwn(report, owner, scope, scopeId);
            }
        }
        return new MobGoalsSave(report.ok() ? Commit.reloadIfOk(report) : report, target);
    }

    /** The behavior stored for a mob instance in this scope, or "" (for Client > Behaviors). */
    public static String storedGoalSet(String owner, String scope, String scopeId) {
        return VariantOps.storedGoalSet(owner, scope, scopeId);
    }

    /** Takes a mob instance's old own list out (a Save turns it into a behavior). */
    static void removeOwn(WriteReport report, String owner, String scope, String scopeId) {
        String wantScope = VariantOps.normalScope(scope);
        String wantId = VariantOps.normalScopeId(scope, scopeId);
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobGoals.FILE))) {
            if (owner.equals(row.getOrDefault(Schema.MobGoals.VARIANT_NAME, "").strip())
                    && wantScope.equals(VariantOps.normalScope(row.get(Schema.MobGoals.SCOPE)))
                    && wantId.equals(row.getOrDefault(Schema.MobGoals.SCOPE_ID, "").strip())) {
                report.record("the old goal rows", TableWriter.delete(Schema.MobGoals.FILE,
                        ownKey(owner, scope, scopeId, row.getOrDefault(Schema.MobGoals.ROW, "").strip())));
            }
        }
    }

    private static Map<String, String> ownKey(String owner, String scope, String scopeId, Object row) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobGoals.VARIANT_NAME, owner);
        keys.put(Schema.MobGoals.SCOPE, VariantOps.normalScope(scope));
        keys.put(Schema.MobGoals.SCOPE_ID, VariantOps.normalScopeId(scope, scopeId));
        keys.put(Schema.MobGoals.ROW, String.valueOf(row));
        return keys;
    }

    // ---- the goal picker and the settings form ----

    /** The goals a list can add, as picker options, grouped WHO and HOW. */
    public static List<com.strata.game.RegistryQuery.Option> goalOptions() {
        List<com.strata.game.RegistryQuery.Option> out = new ArrayList<>();
        for (GoalType.Type type : GoalType.all()) {
            out.add(new com.strata.game.RegistryQuery.Option(type.goal(), GoalGuide.of(type.goal()).name(),
                    type.target() ? "Target goals: pick a target" : "Goals: what they do"));
        }
        return out;
    }

    /**
     * The list with one goal dragged to another's place.
     *
     * @param from the dragged row's index in the sorted rows
     * @param to the index of the row it was dropped on
     */
    public static List<GoalRow> moved(List<GoalRow> rows, int from, int to) {
        List<GoalRow> sorted = sorted(rows);
        if (from < 0 || to < 0 || from >= sorted.size() || to >= sorted.size() || from == to
                || sorted.get(from).target() != sorted.get(to).target()) {
            return null;
        }
        boolean target = sorted.get(from).target();
        List<GoalRow> group = new ArrayList<>();
        int a = -1;
        int b = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).target() == target) {
                if (i == from) {
                    a = group.size();
                }
                if (i == to) {
                    b = group.size();
                }
                group.add(sorted.get(i));
            }
        }
        List<Integer> slots = group.stream().map(GoalRow::priority).sorted().toList();
        GoalRow dragged = group.remove(a);
        group.add(b, dragged);
        List<GoalRow> out = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            out.add(group.get(i).withPriority(slots.get(i)));
        }
        for (GoalRow r : sorted) {
            if (r.target() != target) {
                out.add(r);
            }
        }
        return sorted(out);
    }

    /** A new row of a type, at the game's defaults, after the list's last priority. */
    public static GoalRow newRow(String goal, List<GoalRow> rows) {
        GoalType.Type type = GoalType.of(goal);
        int last = rows.stream().filter(r -> r.target() == type.target())
                .mapToInt(GoalRow::priority).max().orElse(-1);
        return GoalRow.built(type, Math.min(99, last + 1));
    }

    /** A row's settings in one line, for the list: "speed 1.2, keep chasing on". */
    public static String settingsLine(GoalRow row) {
        if (row.game()) {
            return "the mob's own goal";
        }
        GoalType.Type type = GoalType.of(row.goal());
        if (type == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        GoalType.withDefaults(type, row.settings()).forEach((k, v) -> parts.add(
                type.param(k).label().toLowerCase(java.util.Locale.ROOT) + " " + shown(type.param(k), v)));
        return String.join(", ", parts);
    }

    private static String shown(GoalType.Param param, String value) {
        return switch (param.kind()) {
            case FLAG -> "true".equals(value) ? "On" : "Off";
            case ITEMS -> value.split("\\+").length + " item(s)";
            case TARGET -> targetLabel(value);
            case PROJECTILE -> com.strata.util.Labels.pretty(value);
            default -> value;
        };
    }

    // ---- the archetype library ----

    private static Map<String, List<GoalRow>> library() {
        return GoalSetTable.load(Paths.table(Schema.GoalSets.FILE));
    }

    /** Where an archetype comes from. */
    public enum Kind {
        /** A mob family's, from the game. */
        FAMILY,
        /** The user's own. */
        OWN
    }

    /** The Behaviors menu's group for the user's own. */
    public static final String YOUR_OWN = "Your own";

    /**
     * One archetype in the Behaviors menu.
     *
     * @param path the groups it lists under, broadest first
     * @param users the mobs that use it: its family's, and any whose behavior names it
     * @param fights how it fights, Attacks#column: "melee+bow", "missing", "no"
     * @param changed a family's: edited from the game
     */
    public record GoalSetRow(String name, Kind kind, List<String> path, List<String> users, int goals,
                              String fights, boolean changed) {
    }

    /** Every archetype for the Behaviors menu, grouped, from the library and the game layer. */
    public static List<GoalSetRow> goalSetRows() {
        return goalSetRows(library(), GoalFamilies.fromGameLayer(), usersOf(
                CsvTable.read(Paths.table(Schema.MobGoalSets.FILE)), variant -> {
                    Variant v = com.strata.data.Config.tables().variants().get(variant);
                    return v != null && v.isPlainDefault() ? v.baseEntity() : variant;
                }), attacks());
    }

    /**
     * The menu's rows.
     *
     * @param named the mobs whose behavior field names each archetype
     * @param attacks reads how each list fights
     */
    public static List<GoalSetRow> goalSetRows(Map<String, List<GoalRow>> library, List<GoalFamilies.Family> families,
                                                 Map<String, List<String>> named, com.strata.model.Attacks attacks) {
        Map<String, GoalFamilies.Family> familyByName = new LinkedHashMap<>();
        families.forEach(f -> familyByName.put(f.name(), f));
        Map<String, List<String>> paths = GoalFamilies.displayPaths(families);
        List<GoalSetRow> out = new ArrayList<>();
        library.forEach((name, rows) -> {
            GoalFamilies.Family family = familyByName.get(name);
            java.util.Set<String> users = new java.util.LinkedHashSet<>();
            if (family != null) {
                users.addAll(family.mobs());
            }
            users.addAll(named.getOrDefault(name, List.of()));
            Kind kind = family != null ? Kind.FAMILY : Kind.OWN;
            List<String> path = kind == Kind.FAMILY ? paths.getOrDefault(name, List.of()) : List.of(YOUR_OWN);
            boolean changed = kind == Kind.FAMILY && !rows.equals(GoalFamilies.rows(family));
            out.add(new GoalSetRow(name, kind, path, List.copyOf(users), rows.size(), attacks.column(rows),
                    changed));
        });
        out.sort(Comparator.comparing((GoalSetRow r) -> r.kind() != Kind.FAMILY)
                .thenComparing(r -> String.join("/", r.path())).thenComparing(GoalSetRow::name));
        return out;
    }

    /** The mobs whose behavior field names each archetype, from the rows that carry one. */
    public static Map<String, List<String>> usersOf(List<Map<String, String>> choiceRows,
                                                    java.util.function.Function<String, String> userOf) {
        Map<String, java.util.Set<String>> users = new java.util.TreeMap<>();
        for (Map<String, String> row : choiceRows) {
            String goalSet = row.getOrDefault(Schema.MobGoalSets.GOAL_SET, "").strip();
            if (goalSet.isEmpty() || GoalSets.NONE.equals(goalSet)) {
                continue;
            }
            String variant = row.getOrDefault(Schema.MobGoalSets.VARIANT_NAME, "").strip();
            String scopeId = row.getOrDefault(Schema.MobGoalSets.SCOPE_ID, "").strip();
            users.computeIfAbsent(goalSet, k -> new java.util.TreeSet<>())
                    .add(userOf.apply(variant) + (scopeId.isEmpty() ? "" : " in " + scopeId));
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        users.forEach((name, who) -> out.put(name, List.copyOf(who)));
        return out;
    }
    /** An archetype's rows; empty for a new one. */
    public static List<GoalRow> goalSet(String name) {
        List<GoalRow> rows = name == null ? null : library().get(name);
        return rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    }

    /** The archetype names, for the name check and the pickers. */
    public static List<String> goalSetNames() {
        return new ArrayList<>(library().keySet());
    }

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_\\-]{0,39}");

    /** Why an archetype name cannot be used, or null. */
    public static String nameProblem(String name, String originalName, Collection<String> existing) {
        String n = name == null ? "" : name.strip();
        if (!NAME.matcher(n).matches()) {
            return "A name uses a-z, 0-9, _ and -, and starts with a letter or digit";
        }
        if (GoalSets.NONE.equals(n)) {
            return "'" + n + "' is reserved: it means the game's own goals";
        }
        if (n.equals(originalName)) {
            return null;
        }
        return existing.contains(n) ? "Another goal set already has this name" : null;
    }

    /** Saves an archetype whole, then reloads. */
    public static WriteReport saveGoalSet(String originalName, String name, List<GoalRow> rows) {
        WriteReport report = saveGoalSetInto(originalName, name, rows);
        return report.ok() ? Commit.reloadIfOk(report) : report;
    }

    /**
     * Names a mob's list: it becomes a behavior in the library, the mob uses it by that name, and the mob's
     * own copy goes.
     */
    public static WriteReport nameOwnList(String owner, String scope, String scopeId, String name,
                                          List<GoalRow> rows) {
        WriteReport report = saveGoalSetInto("", name, rows);
        if (!report.ok()) {
            return report;
        }
        String n = name.strip();
        for (String failure : VariantOps.saveMobGoalSet(scope, scopeId, owner, n).failures()) {
            report.record(failure, false);
        }
        if (!report.ok()) {
            return report;
        }
        removeOwn(report, owner, scope, scopeId);
        return Commit.reloadIfOk(report);
    }

    /** {@link #saveGoalSet}'s checks and writes, with no reload. */
    private static WriteReport saveGoalSetInto(String originalName, String name, List<GoalRow> rows) {
        String original = originalName == null ? "" : originalName;
        String n = name == null ? "" : name.strip();
        String problem = nameProblem(n, original, goalSetNames());
        if (problem == null) {
            // A mob's own goals may stand in an archetype: a family's archetype holds its mobs' own goals,
            // and they apply to a mob that has them.
            problem = rows.isEmpty() ? "A goal set needs at least one goal" : listProblem(rows);
        }
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        WriteReport report = new WriteReport();
        removeGoalSet(report, original);
        removeGoalSet(report, n);
        for (Map<String, String> row : GoalSetTable.toRows(n, sorted(rows))) {
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put(Schema.GoalSets.GOAL_SET, n);
            keys.put(Schema.GoalSets.ROW, row.get(Schema.GoalSets.ROW));
            Map<String, String> values = new LinkedHashMap<>(row);
            values.remove(Schema.GoalSets.GOAL_SET);
            values.remove(Schema.GoalSets.ROW);
            report.record("the goal set", TableWriter.upsert(Schema.GoalSets.FILE, keys, values));
            if (!report.ok()) {
                return report;
            }
        }
        if (!original.isEmpty() && !original.equals(n)) {
            report.cascaded("the mobs that use it", TableWriter.updateColumn(Schema.MobGoalSets.FILE,
                    Schema.MobGoalSets.GOAL_SET, original, Schema.MobGoalSets.GOAL_SET, n));
        }
        return report;
    }

    /** Deletes several behaviors, then reloads once. */
    public static WriteReport deleteGoalSets(List<String> names) {
        WriteReport report = new WriteReport();
        for (String name : names) {
            if (!report.ok()) {
                break;
            }
            removeGoalSet(report, name);
            report.cascaded("the mobs that use " + name, TableWriter.updateColumn(Schema.MobGoalSets.FILE,
                    Schema.MobGoalSets.GOAL_SET, name, Schema.MobGoalSets.GOAL_SET, ""));
        }
        return Commit.reloadIfOk(report);
    }

    /** Puts a family's archetype back as the game runs it. */
    public static WriteReport restoreGoalSet(String name) {
        List<GoalRow> game = GoalFamilies.fromGameLayer().stream().filter(f -> f.name().equals(name)).findFirst()
                .map(GoalFamilies::rows).orElse(null);
        return game == null ? WriteReport.refused("'" + name + "' is not a family's goal set")
                : saveGoalSet(name, name, game);
    }

    private static void removeGoalSet(WriteReport report, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.GoalSets.FILE))) {
            if (name.equals(row.getOrDefault(Schema.GoalSets.GOAL_SET, "").strip())) {
                report.record("the old goal set rows", TableWriter.delete(Schema.GoalSets.FILE, Map.of(
                        Schema.GoalSets.GOAL_SET, name,
                        Schema.GoalSets.ROW, row.getOrDefault(Schema.GoalSets.ROW, "").strip())));
            }
        }
    }
}
