package com.strata.ui;

/** A COUNT IN WORDS: "1 mob", "16 mobs", in place of "16 mob(s)". */
public final class Words {

    private Words() {
    }

    /** n and the noun: the singular for exactly one, else the plural. */
    public static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** A count written as text ("1-3", "2"): the singular only for "1". */
    public static String count(String n, String one, String many) {
        return n + " " + ("1".equals(n) ? one : many);
    }
}
