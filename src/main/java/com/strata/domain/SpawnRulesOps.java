package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.SpawnRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Everything the condition editor reads and writes for one entity's spawn conditions inside a scope. */
public final class SpawnRulesOps {

    private SpawnRulesOps() {
    }

    /** The condition type choices, from SpawnRule.Type. */
    public static final List<String> TYPE_CHOICES = Arrays.stream(SpawnRule.Type.values())
            .map(t -> t.name().toLowerCase(Locale.ROOT)).toList();

    /** The editor's word choices for a type, or null for a typed or picked value. */
    public static List<String> valueChoices(String type) {
        return SpawnRule.choicesFor(SpawnRule.Type.parse(type));
    }

    /**
     * What "+ Add rule" adds: {type, value} for the first type the set does not use yet, or null when it uses
     * every type.
     */
    public static String[] newCondition(List<String[]> existing) {
        Set<String> used = new HashSet<>();
        for (String[] rule : existing) {
            used.add(rule[0] == null ? "" : rule[0].strip().toLowerCase(Locale.ROOT));
        }
        for (String type : TYPE_CHOICES) {
            if (!used.contains(type)) {
                List<String> words = valueChoices(type);
                return new String[]{type, words == null ? "" : words.getFirst()};
            }
        }
        return null;
    }

    /** Why one value cannot be used for this type, in the designer's words, or null when it can. */
    public static String valueProblem(String type, String value) {
        SpawnRule.Type parsed = SpawnRule.Type.parse(type);
        if (parsed == null) {
            return "Unknown rule type '" + type + "'";
        }
        if (value == null || value.isBlank()) {
            return "The " + type + " rule needs a value";
        }
        return SpawnRule.valueProblem(parsed, value);
    }

    /**
     * What a tier or biome row inherits when it has no conditions of its own: the global variant's, else the
     * global mob's, as the runtime reads them (Tables#spawnRulesFor).
     */
    public static List<String[]> inheritedFor(String scope, String scopeId, String entity, String variantName) {
        if (scope == null || scope.isBlank() || "global".equals(scope)) {
            return List.of();
        }
        List<String[]> variant = variantName == null || variantName.isBlank() ? List.of()
                : spawnRulesFor("global", "", entity, variantName);
        return !variant.isEmpty() ? variant : spawnRulesFor("global", "", entity, "");
    }

    /** This mob's saved conditions in one scope, as {type, value} pairs. */
    public static List<String[]> spawnRulesFor(String scope, String scopeId, String entity,
                                               String variantName) {
        List<String[]> out = new ArrayList<>();
        for (var csv : CsvTable.read(Paths.table(Schema.SpawnRules.FILE))) {
            if (!scope.equals(csv.getOrDefault(Schema.SpawnRules.SCOPE, "").strip())) continue;
            if (!scopeId.equals(csv.getOrDefault(Schema.SpawnRules.SCOPE_ID, "").strip())) continue;
            if (!entity.equals(csv.getOrDefault(Schema.SpawnRules.ENTITY, "").strip())) continue;
            if (!variantName.equals(csv.getOrDefault(Schema.SpawnRules.VARIANT_NAME, "").strip())) continue;
            out.add(new String[]{
                    csv.getOrDefault(Schema.SpawnRules.RULE_TYPE, "time").strip(),
                    csv.getOrDefault(Schema.SpawnRules.VALUE, "").strip()});
        }
        return out;
    }

    /** The key cells one condition row is addressed by, variant scoping included. */
    public static Map<String, String> ruleKeys(String scope, String scopeId,
                                                    String entity, String variantName,
                                                    String conditionType) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.SpawnRules.SCOPE, scope);
        keys.put(Schema.SpawnRules.SCOPE_ID, scopeId);
        keys.put(Schema.SpawnRules.ENTITY, entity);
        keys.put(Schema.SpawnRules.VARIANT_NAME, variantName);
        keys.put(Schema.SpawnRules.RULE_TYPE, conditionType);
        return keys;
    }

    /** Why this set of conditions cannot be saved, in the designer's words, or null when it can. */
    public static String problemWith(List<String[]> rules) {
        Set<SpawnRule.Type> seen = new HashSet<>();
        for (String[] rule : rules) {
            String rawType = rule[0] == null ? "" : rule[0].strip();
            SpawnRule.Type type = SpawnRule.Type.parse(rawType);
            if (type == null) {
                return "Unknown rule type '" + rawType + "'";
            }
            if (rule[1] == null || rule[1].isBlank()) {
                return "The " + rawType + " rule has no value";
            }
            // The evaluator's own grammar: a value it cannot read never matches, and the mob never spawns
            //.
            String valueProblem = SpawnRule.valueProblem(type, rule[1]);
            if (valueProblem != null) {
                return valueProblem + " (got \"" + rule[1].strip() + "\")";
            }
            if (!seen.add(type)) {
                return "Two " + rawType + " rules - keep one. A list such as"
                        + " \"1,3\" covers several values";
            }
        }
        return null;
    }

    /**
     * Replaces this mob's whole condition set in one scope: every existing row for (scope, scope_id, entity,
     * variant_name) is cleared, then the given set is written.
     */
    public static WriteReport replaceAll(String scope, String scopeId, String entity,
                                         String variantName, List<String[]> rules) {
        String problem = problemWith(rules);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        WriteReport report = new WriteReport();
        for (var row : CsvTable.read(Paths.table(Schema.SpawnRules.FILE))) {
            if (scope.equals(row.getOrDefault(Schema.SpawnRules.SCOPE, "").strip())
                    && scopeId.equals(row.getOrDefault(Schema.SpawnRules.SCOPE_ID, "").strip())
                    && entity.equals(row.getOrDefault(Schema.SpawnRules.ENTITY, "").strip())
                    && variantName.equals(row.getOrDefault(Schema.SpawnRules.VARIANT_NAME, "").strip())) {
                report.record("the previous spawn rules",
                        TableWriter.delete(Schema.SpawnRules.FILE, ruleKeys(
                                scope, scopeId, entity, variantName,
                                row.getOrDefault(Schema.SpawnRules.RULE_TYPE, "").strip())));
                if (!report.ok()) return report;
            }
        }
        for (String[] rule : rules) {
            report.record("the spawn rules", TableWriter.upsert(Schema.SpawnRules.FILE,
                    ruleKeys(scope, scopeId, entity, variantName, rule[0].strip()),
                    Map.of(Schema.SpawnRules.VALUE, rule[1].strip())));
            if (!report.ok()) return report;
        }
        return Commit.reloadIfOk(report);
    }
}
