package com.strata;

import com.strata.data.DataFlowSelfTest;
import com.strata.data.TableWriterSelfTest;
import com.strata.game.WorldgenStartupSelfTest;
import com.strata.model.ResolverSelfTest;

import java.nio.file.Path;

/** Headless test entry point (no client, no server, no FML bootstrap). */
public final class HeadlessSelfTest {

    private HeadlessSelfTest() {}

    public static void main(String[] args) {
        boolean allOk = true;
        Path root = Path.of(System.getProperty("user.dir", "")).toAbsolutePath().normalize();

        SelfTestHarness.Result resolver = ResolverSelfTest.run();
        allOk &= report("Resolver", resolver);
        SelfTestHarness.Result writer = TableWriterSelfTest.run();
        allOk &= report("TableWriter", writer);
        SelfTestHarness.Result data = DataFlowSelfTest.run();
        allOk &= report("DataFlow", data);

        int resolverTotal = resolver.passed() + resolver.failed();
        int writerTotal = writer.passed() + writer.failed();
        int dataTotal = data.passed() + data.failed();

        SelfTestHarness.Result worldgen = WorldgenStartupSelfTest.run();
        allOk &= report("WorldgenStartup", worldgen);

        SelfTestHarness.Result drift = DriftCheckSelfTest.run(root, resolverTotal, writerTotal, dataTotal);
        allOk &= report("DriftCheck", drift);

        SelfTestHarness.Result layout = LayoutCheckSelfTest.run();
        allOk &= report("LayoutCheck", layout);

        SelfTestHarness.Result fit = FitCheckSelfTest.run();
        allOk &= report("FitCheck", fit);

        SelfTestHarness.Result formula = FormulaSelfTest.run();
        allOk &= report("Formula", formula);

        SelfTestHarness.Result session = com.strata.network.AssetSessionSelfTest.run();
        allOk &= report("AssetSession", session);

        int grandTotal = resolverTotal + writerTotal + dataTotal
                + worldgen.passed() + worldgen.failed()
                + drift.passed() + drift.failed()
                + layout.passed() + layout.failed()
                + fit.passed() + fit.failed()
                + formula.passed() + formula.failed()
                + session.passed() + session.failed();
        System.out.println("=== HeadlessTotal: " + grandTotal + " ===");

        System.out.println();
        if (allOk) {
            System.out.println("HEADLESS SELFTEST: ALL PASSED");
            System.exit(0);
        } else {
            System.out.println("HEADLESS SELFTEST: FAILURES PRESENT");
            System.exit(1);
        }
    }

    private static boolean report(String name, SelfTestHarness.Result result) {
        for (String line : result.lines()) {
            System.out.println("  " + line);
        }
        System.out.println("=== " + name + ": " + result.passed() + " passed, " + result.failed() + " failed ===");
        return result.ok();
    }
}
