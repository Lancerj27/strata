package com.strata.model;

/** The game's armor material odds, worked out from its code. */
public final class VanillaEquipmentMath {

    private VanillaEquipmentMath() {
    }

    /** The chance, in percent, of an armor tier: 0 leather, 1 gold, 2 chainmail, 3 iron, 4 diamond. */
    public static double materialPct(int tier) {
        double p = 0.095;
        double total = 0;
        for (int start = 0; start <= 1; start++) {
            int steps = tier - start;
            if (steps < 0 || steps > 3) {
                continue;
            }
            total += 0.5 * choose(3, steps) * Math.pow(p, steps) * Math.pow(1 - p, 3 - steps);
        }
        return 100 * total;
    }

    private static int choose(int n, int k) {
        int out = 1;
        for (int i = 0; i < k; i++) {
            out = out * (n - i) / (i + 1);
        }
        return out;
    }

    /** A percent with at most two places: "8.44", "15". */
    public static String pct(double value) {
        double r = Math.round(value * 100) / 100.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
