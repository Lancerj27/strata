package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DISABLED BLOCKS AND ITEMS. */
public final class DisabledOps {

    private DisabledOps() {
    }

    public static final String BLOCK = "block";
    public static final String ITEM = "item";

    /** Every disabled id of a kind. */
    public static Set<String> ids(String kind) {
        return ids(Paths.table(Schema.Disabled.FILE), kind);
    }

    /** As above, from a given table. */
    public static Set<String> ids(Path table, String kind) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, String> r : CsvTable.read(table)) {
            if (kind.equals(r.getOrDefault(Schema.Disabled.KIND, "").strip())) {
                String id = r.getOrDefault(Schema.Disabled.ID, "").strip();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /** Every disabled id of either kind: what no recipe may make. */
    public static Set<String> allIds() {
        Set<String> out = new LinkedHashSet<>(ids(ITEM));
        out.addAll(ids(BLOCK));
        return out;
    }

    /** On / Off over picked ids: all off when any is on, else all on again. */
    public static WriteReport toggle(String kind, List<String> picked) {
        if (picked.isEmpty()) {
            return WriteReport.refused("Pick a " + kind);
        }
        Set<String> now = ids(kind);
        boolean disable = picked.stream().anyMatch(id -> !now.contains(id));
        WriteReport report = new WriteReport();
        for (String id : picked) {
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put(Schema.Disabled.KIND, kind);
            keys.put(Schema.Disabled.ID, id);
            if (disable && !now.contains(id)) {
                report.record("the " + kind, TableWriter.upsert(Schema.Disabled.FILE, keys, Map.of()));
            } else if (!disable) {
                report.record("the " + kind, TableWriter.delete(Schema.Disabled.FILE, keys));
            }
        }
        boolean block = BLOCK.equals(kind);
        return Commit.reloadIfOk(report).needsReload(disable
                ? (block
                        ? "Disabled. New land loses it at once, and land already made strips it as it loads; its recipes and loot go at the next world load or /reload"
                        : "Disabled. Its recipes and loot go at the next world load or /reload")
                : (block
                        ? "On again. Its recipes and loot come back at the next world load or /reload; land already cleared stays clear"
                        : "On again. Its recipes and loot come back at the next world load or /reload"));
    }
}
