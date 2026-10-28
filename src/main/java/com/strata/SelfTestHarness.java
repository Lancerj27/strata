package com.strata;

import java.util.List;

/** Shared infrastructure for the headless self-test suites. */
public final class SelfTestHarness {

    private SelfTestHarness() {}

    public record Result(int passed, int failed, List<String> lines) {
        public boolean ok() { return failed == 0; }
    }

    @FunctionalInterface
    public interface Case {
        boolean run() throws Exception;
    }

    public static void check(List<String> lines, int[] tally, String name, Case test) {
        boolean ok;
        try {
            ok = test.run();
        } catch (Exception e) {
            lines.add("ERROR " + name + " — " + e);
            tally[1]++;
            return;
        }
        lines.add((ok ? "pass  " : "FAIL  ") + name);
        tally[ok ? 0 : 1]++;
    }
}
