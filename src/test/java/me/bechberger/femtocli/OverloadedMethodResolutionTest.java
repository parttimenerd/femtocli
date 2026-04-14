package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that resolveMethod correctly handles overloaded converter/verifier methods
 * instead of picking one arbitrarily.
 */
class OverloadedMethodResolutionTest {

    // ── Case 1: Converter with overloaded method — String overload should be preferred ──

    @Command(name = "overconv")
    static class OverloadedConverterCmd implements Runnable {
        // Two overloads of "parseVal": one takes String (correct), one takes int
        static int parseVal(String s) { return Integer.parseInt(s) * 10; }
        static int parseVal(int i) { return i; }

        @Option(names = "--val", converterMethod = "parseVal")
        int val;

        @Override public void run() {}
    }

    @Test
    void converterResolvesStringOverloadWhenMultipleExist() {
        var cmd = new OverloadedConverterCmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--val", "5");
        assertEquals(0, res.exitCode(), "stderr: " + res.err());
        assertEquals(50, cmd.val, "Should use the String overload (multiplies by 10)");
    }

    // ── Case 2: Verifier with overloaded method — single-arg overload should be preferred ──

    @Command(name = "overver")
    static class OverloadedVerifierCmd implements Runnable {
        static void checkVal(int v) {
            if (v < 0) throw new VerifierException("must be non-negative");
        }
        // A no-arg overload that should NOT be picked
        static void checkVal() { throw new VerifierException("wrong overload called"); }

        @Option(names = "--num", verifierMethod = "checkVal")
        int num;

        @Override public void run() {}
    }

    @Test
    void verifierResolvesSingleArgOverload() {
        var cmd = new OverloadedVerifierCmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--num", "5");
        assertEquals(0, res.exitCode(), "stderr: " + res.err());
        assertEquals(5, cmd.num);
    }

    @Test
    void verifierResolvesSingleArgOverloadRejectsInvalid() {
        var cmd = new OverloadedVerifierCmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--num", "-3");
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("must be non-negative"), "stderr: " + res.err());
    }

    // ── Case 3: Truly ambiguous — two overloads both take one String arg ──

    @Command(name = "ambig")
    static class AmbiguousConverterCmd implements Runnable {
        // Both overloads take a single String — this is genuinely ambiguous
        static int convert(String s) { return 1; }
        static String convert(String s, @SuppressWarnings("unused") int dummy) { return s; }
        // Actually this isn't truly ambiguous because one has 1 param and the other has 2.
        // A truly ambiguous case would require two methods with the same (String) signature,
        // which Java doesn't allow. So the disambiguation by param count will work.

        @Option(names = "--x", converterMethod = "convert")
        int x;

        @Override public void run() {}
    }

    @Test
    void converterWithMultipleOverloadsPicksSingleStringArg() {
        var cmd = new AmbiguousConverterCmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--x", "42");
        assertEquals(0, res.exitCode(), "stderr: " + res.err());
        assertEquals(1, cmd.x, "Should use the String-only overload");
    }

    // ── Case 4: No matching single-arg overload at all ──

    @Command(name = "nomatch")
    static class NoMatchConverterCmd implements Runnable {
        static int parseIt(String a, String b) { return 0; }
        static int parseIt(String a, int b, int c) { return 0; }

        @Option(names = "--z", converterMethod = "parseIt")
        int z;

        @Override public void run() {}
    }

    @Test
    void converterWithNoSingleArgOverloadThrowsError() {
        var cmd = new NoMatchConverterCmd();
        // The method resolves but fails the "must take a single String argument" check
        assertThrows(IllegalStateException.class,
                () -> FemtoCli.runCaptured(cmd, "--z", "1"),
                "Should throw because no overload takes a single String");
    }
}
