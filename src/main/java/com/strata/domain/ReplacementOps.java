package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.ReplacementPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** THE REPLACEMENTS EDITOR'S BACK END. */
public final class ReplacementOps {

    private ReplacementOps() {
    }

    /** What Sources offers: the biome's own spawns, or every spawn but a player's. */
    public static final List<String> SOURCES = List.of(ReplacementPlan.NATURAL, ReplacementPlan.ALL);

    /** One replacement as the list shows it. */
    public record Row(String scope, String scopeId, String from, String to, String share, String sources) {

        /** "Zombie -> Husk (50%, all spawns)". */
        public String sentence() {
            String pct = share.isBlank() ? "100" : share;
            return from + " -> " + to + " (" + pct + "%" + (ReplacementPlan.ALL.equals(sources)
                    ? ", every spawn" : ", natural spawns") + ")";
        }
    }

    /** The replacements of one scope, in file order. */
    public static List<Row> rows(String scope, String scopeId) {
        return rows(Paths.table(Schema.Replacements.FILE), scope, scopeId);
    }

    /** As above, from a given table, so a test can drive it. */
    public static List<Row> rows(Path table, String scope, String scopeId) {
        List<Row> out = new ArrayList<>();
        for (Map<String, String> r : CsvTable.read(table)) {
            if (scope.equals(cell(r, Schema.Replacements.SCOPE)) && scopeId.equals(cell(r, Schema.Replacements.SCOPE_ID))) {
                out.add(new Row(scope, scopeId, cell(r, Schema.Replacements.FROM_ENTITY),
                        cell(r, Schema.Replacements.TO_ENTITY), cell(r, Schema.Replacements.SHARE_PCT),
                        cell(r, Schema.Replacements.SOURCES)));
            }
        }
        return out;
    }

    /** Each replaced mob of a scope, by its id: what the mob list notes beside it. */
    public static Map<String, Row> byFrom(String scope, String scopeId) {
        Map<String, Row> out = new LinkedHashMap<>();
        rows(scope, scopeId).forEach(r -> out.put(r.from(), r));
        return out;
    }

    /** Why a replacement cannot be saved, or null. */
    public static String problem(String from, String to, String share) {
        if (blank(from)) return "Pick the mob to replace";
        if (blank(to)) return "Pick the mob to spawn instead";
        if (from.strip().equals(to.strip())) return "A mob cannot replace itself";
        for (String id : List.of(from, to)) {
            ResourceLocation rl = ResourceLocation.tryParse(id.strip());
            if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                return "No mob " + id.strip() + " in this game";
            }
        }
        if (!blank(share)) {
            try {
                double pct = Double.parseDouble(share.strip());
                if (pct < 1 || pct > 100) return "Share must be 1 to 100";
            } catch (NumberFormatException e) {
                return "Share must be a number";
            }
        }
        return null;
    }

    /** Adds or replaces the scope's replacement of the from-mob. */
    public static WriteReport save(String scope, String scopeId, String from, String to, String share, String sources) {
        String problem = problem(from, to, share);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Replacements.TO_ENTITY, to.strip());
        values.put(Schema.Replacements.SHARE_PCT, blank(share) ? "" : share.strip());
        values.put(Schema.Replacements.SOURCES, ReplacementPlan.ALL.equals(sources) ? ReplacementPlan.ALL : "");
        return note(Commit.reloadIfOk(new WriteReport().record("the replacement",
                TableWriter.upsert(Schema.Replacements.FILE, key(scope, scopeId, from), values))));
    }

    /** Removes one replacement: the from-mob spawns as itself again. */
    public static WriteReport remove(Row row) {
        return note(Commit.reloadIfOk(new WriteReport().record("the replacement",
                TableWriter.delete(Schema.Replacements.FILE, key(row.scope(), row.scopeId(), row.from())))));
    }

    /** A biome's own spawn lists are built as a world loads; the other sources change now. */
    private static WriteReport note(WriteReport report) {
        return report.ok() ? report.needsReload("Saved. Natural spawns change at the next world load") : report;
    }

    private static Map<String, String> key(String scope, String scopeId, String from) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.Replacements.SCOPE, scope);
        keys.put(Schema.Replacements.SCOPE_ID, scopeId);
        keys.put(Schema.Replacements.FROM_ENTITY, from.strip());
        return keys;
    }

    private static String cell(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").strip();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
