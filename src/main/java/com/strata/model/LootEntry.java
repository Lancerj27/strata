package com.strata.model;

import java.util.List;

/**
 * One item entry in a loot pool.
 *
 * @param item item id (e.g. "minecraft:diamond")
 * @param countMin minimum drop count
 * @param countMax maximum drop count
 * @param chancePct drop chance as a percentage (100 = always)
 * @param conditions per-entry conditions
 */
public record LootEntry(
        String item,
        int countMin,
        int countMax,
        int chancePct,
        List<LootCondition> conditions
) {

    public LootEntry {
        conditions = conditions == null ? List.of() : conditions;
    }

    /** A drop of {@code count} as the stacks it falls in: full stacks of {@code maxStack}, then the rest. */
    /** The most items one entry drops. */
    public static final int MAX_COUNT = 1000;

    public static List<Integer> stacks(int count, int maxStack) {
        int max = Math.max(1, maxStack);
        List<Integer> out = new java.util.ArrayList<>();
        for (int left = count; left > 0; left -= max) {
            out.add(Math.min(left, max));
        }
        return out;
    }
}
