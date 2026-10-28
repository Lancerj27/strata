package com.strata.domain;

import com.strata.data.Schema;
import com.strata.data.StrataConfig;
import com.strata.data.TableWriter;
import com.strata.runtime.AreaRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** THE RULES SCREEN'S BACK END. */
public final class AreaRulesOps {

    private AreaRulesOps() {
    }

    /** One rule as the screen lists it: its own value (null: from its tier, or the default), and the one in force. */
    public record Row(String rule, String label, String help, Boolean own, boolean effective) {
    }

    /** A rule's name in words. */
    public static String label(String rule) {
        return switch (rule) {
            case AreaRules.BLOCK_OTHER_SPAWNS -> "Replace blocks other spawns";
            case AreaRules.MONSTERS_IN_CREATURE_SLOTS -> "Monsters take creature slots";
            case AreaRules.MONSTERS_IN_AMBIENT_SLOTS -> "Monsters take ambient slots";
            case AreaRules.MONSTERS_IN_WATER_SLOTS -> "Monsters take water slots";
            case AreaRules.MONSTERS_OUTSIDE_DARKNESS -> "Monsters spawn outside of pure darkness";
            default -> rule;
        };
    }

    /** What a rule does, in a Replace tier's biomes. */
    public static String help(String rule) {
        return switch (rule) {
            case AreaRules.BLOCK_OTHER_SPAWNS -> "Only mobs on the list spawn: no structure mobs, patrols, or new-chunk"
                    + " mobs. At once, for new spawns.";
            case AreaRules.MONSTERS_IN_CREATURE_SLOTS -> "With no creature on the list, its monsters also take the"
                    + " animals' slots. From the next world load.";
            case AreaRules.MONSTERS_IN_AMBIENT_SLOTS -> "With no ambient mob on the list, its monsters also take the"
                    + " bats' slots. From the next world load.";
            case AreaRules.MONSTERS_IN_WATER_SLOTS -> "With no water mob on the list, its monsters also take the"
                    + " water slots. From the next world load.";
            case AreaRules.MONSTERS_OUTSIDE_DARKNESS -> "The monsters on the list spawn in any light, not only in"
                    + " the dark. At once, for new spawns.";
            default -> "";
        };
    }

    /** The rule's value with no tier or biome setting (strata-common.toml, on unless changed there). */
    static boolean fallback(String rule) {
        try {
            return switch (rule) {
                case AreaRules.BLOCK_OTHER_SPAWNS -> StrataConfig.read(StrataConfig.BLOCK_OTHER_SPAWNS);
                case AreaRules.MONSTERS_IN_CREATURE_SLOTS -> StrataConfig.read(StrataConfig.MONSTERS_IN_CREATURE_SLOTS);
                case AreaRules.MONSTERS_IN_AMBIENT_SLOTS -> StrataConfig.read(StrataConfig.MONSTERS_IN_AMBIENT_SLOTS);
                case AreaRules.MONSTERS_IN_WATER_SLOTS -> StrataConfig.read(StrataConfig.MONSTERS_IN_WATER_SLOTS);
                case AreaRules.MONSTERS_OUTSIDE_DARKNESS -> StrataConfig.read(StrataConfig.MONSTERS_OUTSIDE_DARKNESS);
                default -> true;
            };
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Every rule of a tier or a biome. */
    public static List<Row> rows(String scope, String id) {
        String tier = "biome".equals(scope) ? BiomeOps.tierOf(id) : id;
        String biome = "biome".equals(scope) ? id : null;
        List<Row> out = new ArrayList<>();
        for (String rule : AreaRules.RULES) {
            out.add(new Row(rule, label(rule), help(rule), AreaRules.own(scope, id, rule),
                    AreaRules.on(rule, fallback(rule), tier, biome)));
        }
        return out;
    }

    /** Sets one rule: on, off, or null for none of its own (its tier's, or the default). */
    public static WriteReport set(String scope, String id, String rule, Boolean value) {
        if (!AreaRules.RULES.contains(rule)) {
            return WriteReport.refused("No rule named " + rule);
        }
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.AreaRules.SCOPE, scope);
        keys.put(Schema.AreaRules.SCOPE_ID, id);
        keys.put(Schema.AreaRules.RULE, rule);
        WriteReport report = new WriteReport();
        if (value == null) {
            report.cascaded("the rule", TableWriter.delete(Schema.AreaRules.FILE, keys));
        } else {
            report.record("the rule", TableWriter.upsert(Schema.AreaRules.FILE, keys,
                    Map.of(Schema.AreaRules.VALUE, String.valueOf(value))));
        }
        return Commit.reloadIfOk(report);
    }
}
