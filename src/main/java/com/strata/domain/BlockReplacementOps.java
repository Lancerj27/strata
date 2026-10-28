package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.BlockSwap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** THE BLOCK REPLACEMENTS EDITOR'S BACK END. */
public final class BlockReplacementOps {

    private BlockReplacementOps() {
    }

    /** The scopes, as the file names them. */
    public static final String GLOBAL = "global";
    public static final String TIER = "tier";
    public static final String BIOME = "biome";

    /** One replacement as the list shows it. */
    public record Row(String scope, String scopeId, String from, String to) {

        /** "grass_block -> dirt", or "oak_leaves -> disabled (air)". */
        public String sentence() {
            return from + " -> " + (BlockSwap.DISABLED.equals(to) ? "nothing (air)" : to);
        }
    }

    /** The rules of one scope, in file order. */
    public static List<Row> rows(String scope, String scopeId) {
        return rows(Paths.table(Schema.BlockReplacements.FILE), scope, scopeId);
    }

    /** As above, from a given table, so a test can drive it. */
    public static List<Row> rows(Path table, String scope, String scopeId) {
        String id = GLOBAL.equals(scope) ? "" : scopeId;
        List<Row> out = new ArrayList<>();
        for (Row r : all(table)) {
            if (scope.equals(r.scope()) && id.equals(r.scopeId())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Every row of every scope, in file order. */
    public static List<Row> all(Path table) {
        List<Row> out = new ArrayList<>();
        for (Map<String, String> r : CsvTable.read(table)) {
            BlockSwap.Rule rule = BlockSwap.rule(cell(r, Schema.BlockReplacements.SCOPE),
                    cell(r, Schema.BlockReplacements.SCOPE_ID), cell(r, Schema.BlockReplacements.FROM_BLOCK),
                    cell(r, Schema.BlockReplacements.TO_BLOCK));
            if (rule != null) {
                out.add(new Row(rule.scope(), rule.scopeId(), rule.from(), rule.to()));
            }
        }
        return out;
    }

    private static List<BlockSwap.Rule> rules(Path table) {
        return all(table).stream().map(r -> new BlockSwap.Rule(r.scope(), r.scopeId(), r.from(), r.to())).toList();
    }

    /** Why a replacement cannot be saved, or null. */
    public static String problem(String scope, String scopeId, String from, String to) {
        String p = problem(Paths.table(Schema.BlockReplacements.FILE), scope, scopeId, from, to);
        if (p != null) {
            return p;
        }
        List<String> ids = BlockSwap.DISABLED.equals(to.strip()) ? List.of(from) : List.of(from, to);
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id.strip());
            if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
                return "No block " + id.strip() + " in this game";
            }
        }
        return null;
    }

    /** The checks that need no game registry: a scope with its id, the two blocks, the chain rule. */
    public static String problem(Path table, String scope, String scopeId, String from, String to) {
        if (!GLOBAL.equals(scope) && (scopeId == null || scopeId.isBlank())) {
            return "Pick a " + scope;
        }
        return BlockSwap.problem(from, to, rules(table), scope, GLOBAL.equals(scope) ? "" : scopeId);
    }

    /** Adds the scope's rule for the from-block, or replaces it. */
    public static WriteReport save(String scope, String scopeId, String from, String to) {
        String problem = problem(scope, scopeId, from, to);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.BlockReplacements.TO_BLOCK, to.strip());
        return note(Commit.reloadIfOk(new WriteReport().record("the replacement",
                TableWriter.upsert(Schema.BlockReplacements.FILE, key(scope, scopeId, from), values))));
    }

    /** As above into a given table, with no registry check and no reload: what a test drives. */
    public static WriteReport save(Path table, String scope, String scopeId, String from, String to) {
        String problem = problem(table, scope, scopeId, from, to);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return new WriteReport().record("the replacement", TableWriter.upsert(table, key(scope, scopeId, from),
                new LinkedHashMap<>(Map.of(Schema.BlockReplacements.TO_BLOCK, to.strip()))));
    }

    /** Removes one rule from a given table. */
    public static WriteReport remove(Path table, Row row) {
        return new WriteReport().record("the replacement",
                TableWriter.delete(table, key(row.scope(), row.scopeId(), row.from())));
    }

    /** Removes one rule: the block generates as the game has it again. */
    public static WriteReport remove(Row row) {
        return note(Commit.reloadIfOk(new WriteReport().record("the replacement",
                TableWriter.delete(Schema.BlockReplacements.FILE, key(row.scope(), row.scopeId(), row.from())))));
    }

    private static WriteReport note(WriteReport report) {
        return report.ok() ? report.needsReload(
                "Saved. New chunks replace it at once, and land already made comes in line as it loads") : report;
    }

    private static Map<String, String> key(String scope, String scopeId, String from) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.BlockReplacements.SCOPE, scope);
        keys.put(Schema.BlockReplacements.SCOPE_ID, GLOBAL.equals(scope) ? "" : scopeId);
        keys.put(Schema.BlockReplacements.FROM_BLOCK, from.strip());
        return keys;
    }

    private static String cell(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").strip();
    }
}
