package com.strata.runtime;

import com.strata.data.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A MOB'S OWN SWITCHES: a Default, On, or Off cell of a variants.csv column, set in the mob editor. */
public final class MobSwitches {

    private MobSwitches() {
    }

    public static final String ON = "on";
    public static final String OFF = "off";

    /** "on", "off", or "" (Default) from a stored cell. */
    public static String normal(String cell) {
        String v = cell == null ? "" : cell.strip().toLowerCase(java.util.Locale.ROOT);
        return ON.equals(v) || OFF.equals(v) ? v : "";
    }

    /** One column's switches by ScopeIndex key, "on" or "off" only; Default rows are not held. */
    public static Map<String, String> index(List<Map<String, String>> variantRows, String column) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, String> row : variantRows) {
            String value = normal(row.get(column));
            String name = row.getOrDefault(Schema.Variants.NAME, "").strip();
            if (!value.isEmpty() && !name.isEmpty()) {
                out.put(ScopeIndex.key(row.getOrDefault(Schema.Variants.SCOPE, ""),
                        row.getOrDefault(Schema.Variants.SCOPE_ID, ""), name), value);
            }
        }
        return Map.copyOf(out);
    }

    /** The mob's own switch: true On, false Off, null Default. */
    public static Boolean own(Map<String, String> index, String variant, String base, String tier, String biome) {
        String own = ScopeIndex.resolveOrBase(index, variant, base, tier, biome);
        return own == null ? null : ON.equals(own);
    }
}
