package com.strata.model;

/** One weighted draw, used everywhere Strata picks one thing out of many. */
public final class WeightedPick {

    private WeightedPick() {}

    /**
     * Returned when nothing wins: an empty list, a non-positive total, or a roll that landed in the empty
     * share.
     */
    public static final int NONE = -1;

    /**
     * The winner of a draw whose roll is already an integer inside the total.
     *
     * @param weights the candidates' weights, in table order
     * @param roll a value in [0, sum of clamped weights)
     * @return the winning index, or NONE if the total is not positive
     */
    public static int index(int[] weights, int roll) {
        if (weights == null || weights.length == 0) {
            return NONE;
        }
        double[] widened = new double[weights.length];
        for (int i = 0; i < weights.length; i++) {
            widened[i] = Math.max(0, weights[i]);
        }
        return walk(widened, roll);
    }

    /**
     * The winner of a draw spread over at least {@code spreadFloor}, where a short total leaves a share in
     * which nothing wins.
     *
     * @param weights the candidates' weights, in table order
     * @param roll01 a uniform value in [0, 1)
     * @param spreadFloor the minimum width the roll is spread over; weights summing below it leave the
     *     remainder as "nothing"
     * @return the winning index, or NONE
     */
    public static int indexWithRemainder(double[] weights, double roll01, double spreadFloor) {
        if (weights == null || weights.length == 0) {
            return NONE;
        }
        double[] clamped = new double[weights.length];
        double total = 0.0;
        for (int i = 0; i < weights.length; i++) {
            clamped[i] = Double.isFinite(weights[i]) ? Math.max(0.0, weights[i]) : 0.0;
            total += clamped[i];
        }
        if (total <= 0.0) {
            return NONE;
        }
        return walk(clamped, roll01 * Math.max(spreadFloor, total));
    }

    /** The shared walk: the first index whose cumulative weight passes the point, or NONE. */
    private static int walk(double[] weights, double point) {
        double total = 0.0;
        for (double w : weights) {
            total += w;
        }
        if (total <= 0.0) {
            return NONE;
        }
        double seen = 0.0;
        for (int i = 0; i < weights.length; i++) {
            seen += weights[i];
            if (point < seen) {
                return i;
            }
        }
        return NONE;
    }
}
