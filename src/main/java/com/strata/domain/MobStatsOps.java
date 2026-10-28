package com.strata.domain;

import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.util.Map;

/** Writes for a base mob's attribute overrides. */
public final class MobStatsOps {

    private MobStatsOps() {
    }

    /** Why a typed attribute value cannot be saved, or null when it can. */
    public static String valueProblem(String value) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            return "Type a value first";
        }
        try {
            return Double.isFinite(Double.parseDouble(v)) ? null : "The value must be a finite number";
        } catch (NumberFormatException e) {
            return "The value must be a number";
        }
    }

    /** Overrides one attribute for one entity, then reloads. */
    public static WriteReport saveOverride(String entity, String attribute, String value) {
        String problem = valueProblem(value);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return Commit.reloadIfOk(new WriteReport().record("the attribute",
                TableWriter.upsert(Schema.MobBaseStats.FILE,
                        Map.of(Schema.MobBaseStats.ENTITY, entity,
                                Schema.MobBaseStats.ATTRIBUTE, attribute),
                        Map.of(Schema.MobBaseStats.VALUE,
                                String.valueOf(Double.parseDouble(value.strip()))))));
    }

    /** Removes an override, returning the attribute to its vanilla value, then reloads. */
    public static WriteReport resetOverride(String entity, String attribute) {
        return Commit.reloadIfOk(new WriteReport().cascaded("the attribute override",
                TableWriter.delete(Schema.MobBaseStats.FILE,
                        Map.of(Schema.MobBaseStats.ENTITY, entity,
                                Schema.MobBaseStats.ATTRIBUTE, attribute))));
    }
}
