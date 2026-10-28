package com.strata.game;

import com.strata.SelfTestHarness;
import com.strata.data.Config;

import java.util.ArrayList;
import java.util.List;

/** Headless coverage for the Section 6.4 worldgen-registry startup seam. */
public final class WorldgenStartupSelfTest {

    private WorldgenStartupSelfTest() {}

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];

        SelfTestHarness.check(lines, tally,
                "W1 createAndBind degrades to false (not a throw) under plain JVM", () -> {
                    boolean result = WorldgenRegistryBuilder.createAndBind();
                    return !result;
                });

        SelfTestHarness.check(lines, tally,
                "W2 failed createAndBind leaves Config resolver null (snapshot fallback intact)", () ->
                        Config.resolver() == null);

        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }
}