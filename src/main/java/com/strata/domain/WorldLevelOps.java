package com.strata.domain;

import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.ValueType;
import com.strata.runtime.WorldLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the World level editor reads and does: the stages of world_stages.csv, a tab each with a form of
 * its fields, and their rules of world_stage_rules.csv, added and edited through a row of cells as abilities are.
 */
public final class WorldLevelOps {

    private WorldLevelOps() {
    }

    public static final List<String> PLAYER_CHOICES = List.of("any", "all");
    public static final List<String> CONDITION_CHOICES = List.of("all", "any");

    /** What each rule kind means, in one short line. */
    public static final Map<String, String> KIND_WORDS = Map.ofEntries(
            Map.entry(WorldLevel.ADVANCEMENT, "a player earned the advancement"),
            Map.entry(WorldLevel.XP_LEVEL, "a player's XP level reached the number"),
            Map.entry(WorldLevel.TIER, "a player entered the tier"),
            Map.entry(WorldLevel.BIOME, "a player entered the biome"),
            Map.entry(WorldLevel.AREA_LEVEL, "a player entered an area of this Strata level or more"),
            Map.entry(WorldLevel.DIMENSION, "a player entered the dimension"),
            Map.entry(WorldLevel.STRUCTURE, "a player entered the structure"),
            Map.entry(WorldLevel.ITEM, "a player carried the item"),
            Map.entry(WorldLevel.KILL_ENTITY, "a player killed this kind of mob"),
            Map.entry(WorldLevel.MOB_KILL_LEVEL, "a player killed a mob of this level or more"),
            Map.entry(WorldLevel.KILL_COUNT, "a player's kills reached the number"),
            Map.entry(WorldLevel.DAYS, "the world's day reached the number"),
            Map.entry(WorldLevel.LOCK_BLOCK, "cannot be broken before this stage"),
            Map.entry(WorldLevel.LOCK_RECIPE, "cannot be crafted before this stage"),
            Map.entry(WorldLevel.LOCK_ITEM, "cannot be used before this stage"),
            Map.entry(WorldLevel.LOCK_TIER, "being in the tier before this stage gives the effect"),
            Map.entry(WorldLevel.LOCK_BIOME, "being in the biome before this stage gives the effect"),
            Map.entry(WorldLevel.LOCK_AREA_LEVEL, "areas of this Strata level or more give the effect before this stage"),
            Map.entry(WorldLevel.LOCK_DIMENSION, "being in the dimension before this stage gives the effect"),
            Map.entry(WorldLevel.LOCK_STRUCTURE, "being in the structure before this stage gives the effect"));

    /** The help line under the list. */
    public static final String HINT = "Offset: flat levels on every spawn. Coefficient: levels per player level (0.1 ="
            + " one per ten; Start's Level source says whose). Mode: how the stage changes the totals. Hold: keep stays,"
            + " while_met switches off when its conditions stop holding. The row above the list adds a rule.";

    // ---- the stage form ----

    /** The start row's values when the table has no stage 0 row yet. */
    private static final WorldLevel.Stage START = new WorldLevel.Stage(0, "", false, true, 0, 0.0, WorldLevel.ADD);

    /** The tables now, as read by the game. */
    public static WorldLevel.Rules rules() {
        return WorldLevel.read(Paths.table(Schema.WorldStages.FILE), Paths.table(Schema.WorldStageRules.FILE));
    }

    /** The stages in order, the start (0) first, for the tabs. */
    public static List<Integer> stages() {
        List<Integer> out = new ArrayList<>();
        out.add(0);
        for (int s : rules().stages().keySet()) {
            if (s != 0) {
                out.add(s);
            }
        }
        return out;
    }

    /** One stage's row, or the start's defaults for 0 with no row; null for a stage with no row. */
    public static WorldLevel.Stage stage(int stage) {
        WorldLevel.Stage s = rules().stages().get(stage);
        return s == null && stage == 0 ? START : s;
    }

    /** A stage's rules, in file order. */
    public static List<WorldLevel.Rule> rulesOf(int stage) {
        return rules().rules().stream().filter(r -> r.stage() == stage).toList();
    }

    /** A stage's tab label: "Start", else its name, else its number. */
    public static String tab(int stage) {
        if (stage == 0) {
            return "Start";
        }
        WorldLevel.Stage s = stage(stage);
        return s == null || s.name().isEmpty() ? String.valueOf(stage) : s.name();
    }

    /** A stage's heading. */
    public static String heading(int stage) {
        if (stage == 0) {
            return "Start (before stage 1)";
        }
        String tab = tab(stage);
        return "Stage " + stage + (tab.equals(String.valueOf(stage)) ? "" : ": " + tab);
    }

    /** The stage form as typed. */
    public record StageForm(String name, String players, String conditions, String offset, String coefficient,
                            String mode, String hold, String levelSource) {

        public StageForm(String name, String players, String conditions, String offset, String coefficient, String mode) {
            this(name, players, conditions, offset, coefficient, mode, WorldLevel.KEEP, WorldLevel.HIGHEST_EVER);
        }
    }

    /** The form for a stage, as stored. */
    public static StageForm formFor(int stage) {
        WorldLevel.Stage s = stage(stage);
        if (s == null) {
            s = START;
        }
        return new StageForm(s.name(), s.allPlayers() ? "all" : "any", s.allConditions() ? "all" : "any",
                Parse.number(s.mobLevelOffset()), Parse.number(s.playerLevelCoefficient()), s.mode(), s.hold(),
                s.levelSource());
    }

    /** Why the stage form cannot be saved, or null. */
    public static String stageProblem(StageForm f) {
        if (!TableWriter.isCsvSafe(f.name())) {
            return "The name cannot hold commas, quotes or line breaks";
        }
        if (!PLAYER_CHOICES.contains(f.players())) {
            return "Players is any or all";
        }
        if (!CONDITION_CHOICES.contains(f.conditions())) {
            return "Conditions is all or any";
        }
        if (ValueType.number(-1000, 1000).problem(f.offset()) != null) {
            return "The mob level offset is a number from -1000 to 1000";
        }
        if (ValueType.number(0, 10).problem(f.coefficient()) != null) {
            return "The player level coefficient is a number from 0 to 10";
        }
        if (!WorldLevel.MODES.contains(f.mode())) {
            return "The mode is add, subtract, multiply or scale_level";
        }
        if (!WorldLevel.HOLDS.contains(f.hold())) {
            return "Hold is keep or while_met";
        }
        if (!WorldLevel.LEVEL_SOURCES.contains(f.levelSource())) {
            return "The level source is highest_ever, highest_online, average_online or total_online";
        }
        return null;
    }

    /** Saves the stage form. */
    public static WriteReport saveStage(int stage, StageForm f) {
        String problem = stageProblem(f);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.WorldStages.NAME, stage == 0 ? "" : f.name().strip());
        values.put(Schema.WorldStages.PLAYERS, f.players());
        values.put(Schema.WorldStages.CONDITIONS, f.conditions());
        values.put(Schema.WorldStages.MOB_LEVEL_OFFSET, f.offset().strip());
        values.put(Schema.WorldStages.PLAYER_LEVEL_COEFFICIENT, f.coefficient().strip());
        values.put(Schema.WorldStages.MODE, f.mode());
        values.put(Schema.WorldStages.HOLD, f.hold());
        values.put(Schema.WorldStages.LEVEL_SOURCE, stage == 0 ? f.levelSource() : "");
        return Commit.reloadIfOk(new WriteReport().record("the stage",
                TableWriter.upsert(Schema.WorldStages.FILE, stageKey(stage), values)));
    }

    /** The number the next added stage gets. */
    public static int nextStage() {
        WorldLevel.Rules rules = rules();
        return rules.stages().isEmpty() ? 1 : rules.stages().lastKey() + 1;
    }

    /** Adds a stage above the top one, with the start values. */
    public static WriteReport addStage() {
        int stage = nextStage();
        return saveStage(stage, new StageForm("Stage " + stage, "any", "all", "0", "0", WorldLevel.ADD));
    }

    /** Removes a whole stage with its rules. */
    public static WriteReport removeStage(int stage) {
        if (stage == 0) {
            return WriteReport.refused("The start is not a stage: set its values to 0 instead");
        }
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            report.record("the stage", TableWriter.delete(Schema.WorldStages.FILE, stageKey(stage)));
            report.cascaded("its rules", TableWriter.delete(Schema.WorldStageRules.FILE,
                    Map.of(Schema.WorldStageRules.STAGE, String.valueOf(stage))));
            return Commit.reloadIfOk(report);
        });
    }

    // ---- the rule row ----

    /** What the value cell of a kind is: a number typed, a tier chosen, or an id picked from a list. */
    public static ValueType valueType(String kind) {
        if (WorldLevel.numeric(kind)) {
            return ValueType.whole(0, Integer.MAX_VALUE);
        }
        return switch (kind) {
            case WorldLevel.TIER, WorldLevel.LOCK_TIER -> ValueType.choice(TierOps.names());
            case WorldLevel.BIOME, WorldLevel.LOCK_BIOME -> ValueType.registry(ValueType.Registry.BIOME);
            case WorldLevel.DIMENSION, WorldLevel.LOCK_DIMENSION -> ValueType.registry(ValueType.Registry.DIMENSION);
            case WorldLevel.STRUCTURE, WorldLevel.LOCK_STRUCTURE -> ValueType.registry(ValueType.Registry.STRUCTURE);
            case WorldLevel.ADVANCEMENT -> ValueType.registry(ValueType.Registry.ADVANCEMENT);
            case WorldLevel.ITEM, WorldLevel.LOCK_ITEM -> ValueType.registry(ValueType.Registry.ITEM);
            case WorldLevel.KILL_ENTITY -> ValueType.registry(ValueType.Registry.LIVING_MOB);
            case WorldLevel.LOCK_BLOCK -> ValueType.registry(ValueType.Registry.BLOCK);
            case WorldLevel.LOCK_RECIPE -> ValueType.registry(ValueType.Registry.RECIPE);
            default -> ValueType.TEXT;
        };
    }

    /** Whether a kind's rule carries an effect (a locked area). */
    public static boolean hasEffect(String kind) {
        return WorldLevel.areaLock(kind);
    }

    /** The effect id of a rule's extra, or "". */
    public static String effectOf(WorldLevel.Rule r) {
        return r.extra().trim().split("\\s+")[0];
    }

    /** The amplifier of a rule's extra, "0" when none. */
    public static String amplifierOf(WorldLevel.Rule r) {
        String[] parts = r.extra().trim().split("\\s+");
        return parts.length > 1 ? parts[1] : "0";
    }

    /** A rule from the row's cells. */
    public static WorldLevel.Rule rule(int stage, String kind, String value, String effect, String amplifier) {
        String k = kind == null ? "" : kind.strip().toLowerCase(Locale.ROOT);
        String v = value == null ? "" : value.strip();
        String e = effect == null ? "" : effect.strip();
        String extra = hasEffect(k) && !e.isEmpty() ? e + " " + (amplifier == null || amplifier.isBlank() ? "0" : amplifier.strip()) : "";
        return new WorldLevel.Rule(stage, k, v, extra);
    }

    /** Why a rule cannot be saved, or null. */
    public static String ruleProblem(WorldLevel.Rule r) {
        if (!WorldLevel.KINDS.contains(r.kind())) {
            return "Pick the kind of rule";
        }
        if (r.value().isEmpty()) {
            return WorldLevel.numeric(r.kind()) ? "Type the number" : "Pick the value";
        }
        if (!TableWriter.isCsvSafe(r.value()) || !TableWriter.isCsvSafe(r.extra())) {
            return "A value cannot hold commas, quotes or line breaks";
        }
        if (WorldLevel.LOCK_BLOCK.equals(r.kind()) && r.value().startsWith("#")) {
            return null;
        }
        String problem = valueType(r.kind()).problem(r.value());
        if (problem != null) {
            return problem;
        }
        if (hasEffect(r.kind())) {
            if (effectOf(r).isEmpty()) {
                return "Pick the effect the locked area gives";
            }
            if (ValueType.whole(0, 255).problem(amplifierOf(r)) != null) {
                return "The amplifier is a whole number from 0 to 255";
            }
        }
        return null;
    }

    /** A rule's words in the list. */
    public static String label(WorldLevel.Rule r) {
        return r.kind() + ": " + r.value() + (r.extra().isEmpty() ? "" : "  (" + r.extra() + ")");
    }

    /** A rule typed as "kind value [extra]" parsed, or null when it does not parse (the headless path). */
    public static WorldLevel.Rule parseRule(int stage, String typed) {
        String[] parts = typed == null ? new String[0] : typed.strip().split("\\s+", 3);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return new WorldLevel.Rule(stage, parts[0].toLowerCase(Locale.ROOT), parts[1], parts.length > 2 ? parts[2] : "");
    }

    /** Adds a rule. */
    public static WriteReport addRule(WorldLevel.Rule rule) {
        String problem = ruleProblem(rule);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        if (rule.stage() <= 0) {
            return WriteReport.refused("The start has no rules");
        }
        if (rulesOf(rule.stage()).contains(rule)) {
            return WriteReport.refused("This stage already has that rule");
        }
        return Commit.reloadIfOk(new WriteReport().record("the rule",
                TableWriter.appendRows(Schema.WorldStageRules.FILE, List.of(ruleRow(rule)))));
    }

    /** Replaces a rule with the row's new one. */
    public static WriteReport replaceRule(WorldLevel.Rule old, WorldLevel.Rule rule) {
        String problem = ruleProblem(rule);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            report.cascaded("the old rule", TableWriter.delete(Schema.WorldStageRules.FILE, ruleRow(old)));
            report.record("the rule", TableWriter.appendRows(Schema.WorldStageRules.FILE, List.of(ruleRow(rule))));
            return Commit.reloadIfOk(report);
        });
    }

    /** Removes every picked rule; a rule stored twice goes in one delete. */
    public static WriteReport remove(List<WorldLevel.Rule> rules) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (WorldLevel.Rule r : new java.util.LinkedHashSet<>(rules)) {
                report.record("the rule", TableWriter.delete(Schema.WorldStageRules.FILE, ruleRow(r)));
            }
            return Commit.reloadIfOk(report);
        });
    }

    private static Map<String, String> stageKey(int stage) {
        return Map.of(Schema.WorldStages.STAGE, String.valueOf(stage));
    }

    private static Map<String, String> ruleRow(WorldLevel.Rule r) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put(Schema.WorldStageRules.STAGE, String.valueOf(r.stage()));
        row.put(Schema.WorldStageRules.KIND, r.kind());
        row.put(Schema.WorldStageRules.VALUE, r.value());
        row.put(Schema.WorldStageRules.EXTRA, r.extra());
        return row;
    }
}
