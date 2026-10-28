package com.strata.model;

import java.util.List;
import java.util.Locale;

/** THE REAL EQUIPMENT CHANCES BY DIFFICULTY. */
public final class EquipmentOdds {

    private EquipmentOdds() {
    }

    /** The difficulties that arm mobs, with the game's regional-difficulty range. */
    public enum Level {
        EASY("Easy", 0.75, 1.5), NORMAL("Normal", 1.5, 4.0), HARD("Hard", 2.25, 6.75);

        public final String word;
        public final double lowest;
        public final double highest;

        Level(String word, double lowest, double highest) {
            this.word = word;
            this.lowest = lowest;
            this.highest = highest;
        }
    }

    /** The area factor for a regional difficulty (DifficultyInstance#getSpecialMultiplier). */
    public static double factor(double regionalDifficulty) {
        if (regionalDifficulty < 2) {
            return 0;
        }
        return regionalDifficulty > 4 ? 1 : (regionalDifficulty - 2) / 2;
    }

    /** A chance times the area factor, lowest and highest, for a difficulty: {low, high}. */
    public static double[] scaled(double pct, Level level) {
        return new double[]{pct * factor(level.lowest), pct * factor(level.highest)};
    }

    /** "never", "12%", or "0 to 25%". */
    public static String range(double[] lowHigh) {
        if (lowHigh[1] <= 0) {
            return "never";
        }
        String lo = pct(lowHigh[0]);
        String hi = pct(lowHigh[1]);
        return lo.equals(hi) ? hi : lo + " to " + hi;
    }

    private static String pct(double v) {
        double r = Math.round(v * 100) / 100.0;
        return (r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.2f", r)
                .replaceAll("0+$", "")) + "%";
    }

    /**
     * The lines an equipment item's tooltip shows.
     *
     * @param scaled its chances scale with local difficulty
     * @param normal its chance on Easy and Normal, at full local difficulty when scaled
     * @param hard its chance on Hard
     * @param enchant its enchant chance, before the area factor
     * @param drop its drop chance
     */
    public static List<String> lines(boolean scaled, double normal, double hard, double enchant, double drop) {
        String carried = scaled
                ? "Carried: Easy never, Normal " + range(scaled(normal, Level.NORMAL)) + ", Hard "
                + range(scaled(hard, Level.HARD)) + " (scaled by local difficulty)."
                : "Carried: Easy " + pct(normal) + ", Normal " + pct(normal) + ", Hard " + pct(hard) + ".";
        return List.of(carried,
                "Enchanted when carried: Easy never, Normal " + range(scaled(enchant, Level.NORMAL))
                        + ", Hard " + range(scaled(enchant, Level.HARD)) + ".",
                "The low end is a new area; the high end an old, long-played area under a full moon.",
                "Drops on death: " + pct(drop) + " (on a player's kill; Looting adds 1% a level).");
    }
}
