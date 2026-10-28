package com.strata.model;

import java.util.List;

/**
 * A pool within a loot table override.
 *
 * @param entries the items in this pool
 * @param conditions conditions that gate the entire pool
 */
public record LootPool(
        List<LootEntry> entries,
        List<LootCondition> conditions
) {

    public static LootPool of(List<LootEntry> entries) {
        return new LootPool(entries, List.of());
    }
}
