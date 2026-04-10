package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MutuallyExclusiveOptionsTest {

    private static RunResult run(Object command, String... args) {
        return FemtoCli.builder().runCaptured(command, args);
    }

    @Command(name = "test")
    static class SimpleExclusiveOptions implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-b"})
        boolean a;

        @Option(names = "-b", description = "Option B", prevents = {"-a"})
        boolean b;

        @Override
        public Integer call() {
            System.out.println("a=" + a + ", b=" + b);
            return 0;
        }
    }

    @Test
    void testNoOptionsProvided() {
        RunResult res = run(new SimpleExclusiveOptions());
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=false, b=false");
    }

    @Test
    void testOnlyFirstOptionProvided() {
        RunResult res = run(new SimpleExclusiveOptions(), "-a");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=false");
    }

    @Test
    void testOnlySecondOptionProvided() {
        RunResult res = run(new SimpleExclusiveOptions(), "-b");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=false, b=true");
    }

    @Test
    void testBothOptionsProvidedShouldFail() {
        RunResult res = run(new SimpleExclusiveOptions(), "-a", "-b");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Command(name = "test")
    static class MultipleOptions implements Callable<Integer> {
        @Option(names = {"-a", "--alpha"}, description = "Option A", prevents = {"-b", "--beta"})
        boolean a;

        @Option(names = {"-b", "--beta"}, description = "Option B", prevents = {"-a", "--alpha"})
        boolean b;

        @Option(names = "-c", description = "Option C")
        boolean c;

        @Override
        public Integer call() {
            System.out.println("a=" + a + ", b=" + b + ", c=" + c);
            return 0;
        }
    }

    @Test
    void testMultipleAliasesWithLongForm() {
        RunResult res = run(new MultipleOptions(), "--alpha");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=false, c=false");
    }

    @Test
    void testMultipleAliasesConflictWithLongForm() {
        RunResult res = run(new MultipleOptions(), "--alpha", "--beta");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testNonConflictingOptionCanBeUsedTogether() {
        RunResult res = run(new MultipleOptions(), "-a", "-c");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=false, c=true");
    }

    @Command(name = "test")
    static class StringOptions implements Callable<Integer> {
        @Option(names = "-o", description = "Output", prevents = {"-q"})
        String output;

        @Option(names = "-q", description = "Quiet", prevents = {"-o"})
        boolean quiet;

        @Override
        public Integer call() {
            System.out.println("output=" + output + ", quiet=" + quiet);
            return 0;
        }
    }

    @Test
    void testBothStringAndBooleanOptionsConflict() {
        RunResult res = run(new StringOptions(), "-o", "file.txt", "-q");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testStringOptionWithoutConflict() {
        RunResult res = run(new StringOptions(), "-o", "file.txt");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("output=file.txt");
    }

    @Test
    void testQuietOptionWithoutConflict() {
        RunResult res = run(new StringOptions(), "-q");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("quiet=true");
    }

    @Command(name = "test")
    static class ChainedPrevents implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-b"})
        boolean a;

        @Option(names = "-b", description = "Option B", prevents = {"-a", "-c"})
        boolean b;

        @Option(names = "-c", description = "Option C", prevents = {"-b"})
        boolean c;

        @Override
        public Integer call() {
            System.out.println("a=" + a + ", b=" + b + ", c=" + c);
            return 0;
        }
    }

    @Test
    void testChainedPreventsAWithB() {
        RunResult res = run(new ChainedPrevents(), "-a", "-b");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testChainedPreventsAWithC() {
        RunResult res = run(new ChainedPrevents(), "-a", "-c");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=false, c=true");
    }

    @Test
    void testChainedPreventsOnlyA() {
        RunResult res = run(new ChainedPrevents(), "-a");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=false, c=false");
    }

    @Command(name = "test")
    static class OneWayPrevents implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-b"})
        boolean a;

        @Option(names = "-b", description = "Option B")
        boolean b;

        @Override
        public Integer call() {
            System.out.println("a=" + a + ", b=" + b);
            return 0;
        }
    }

    @Test
    void testOneWayPreventsWorksBidirectionally() {
        // A prevents B, so using both -a and -b should fail
        RunResult res = run(new OneWayPrevents(), "-a", "-b");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testOneWayPreventsAOnly() {
        RunResult res = run(new OneWayPrevents(), "-a");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=false");
    }

    @Test
    void testOneWayPreventsB() {
        // B alone should work even though A prevents it
        RunResult res = run(new OneWayPrevents(), "-b");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=false, b=true");
    }

    @Test
    void testOneWayPreventsWorksBidirectionallyReversed() {
        // B is not explicitly prevented by A, but since A prevents B, it should be enforced both ways
        RunResult res = run(new OneWayPrevents(), "-b", "-a");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testOneWayPreventsWorksBidirectionallyReversedWithLongForm() {
        // B is not explicitly prevented by A, but since A prevents B, it should be enforced both ways
        RunResult res = run(new OneWayPrevents(), "-b", "-a");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    // --- Bug: default values cause false prevents conflicts ---

    @Command(name = "test")
    static class PreventsWithDefault implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-b"})
        boolean a;

        @Option(names = "-b", description = "Option B", defaultValue = "hello")
        String b;

        @Override
        public Integer call() {
            System.out.println("a=" + a + ", b=" + b);
            return 0;
        }
    }

    @Test
    void testPreventsWithDefaultValueShouldNotConflict() {
        // Only -a is provided; -b gets its default value.
        // This should NOT be treated as a conflict.
        RunResult res = run(new PreventsWithDefault(), "-a");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=hello");
    }

    @Test
    void testPreventsWithDefaultValueExplicitConflict() {
        // Both provided explicitly — should fail
        RunResult res = run(new PreventsWithDefault(), "-a", "-b", "world");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testPreventsWithDefaultOnlyDefaultUsed() {
        // Neither option explicitly provided; -b gets default
        RunResult res = run(new PreventsWithDefault());
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=false, b=hello");
    }

    @Command(name = "test")
    static class BidirectionalPreventsWithDefault implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-b"}, defaultValue = "x")
        String a;

        @Option(names = "-b", description = "Option B", prevents = {"-a"}, defaultValue = "y")
        String b;

        @Override
        public Integer call() {
            System.out.println("a=" + a + ", b=" + b);
            return 0;
        }
    }

    @Test
    void testBidirectionalPreventsWithDefaultsNoArgs() {
        // Neither provided — both get defaults, no conflict
        RunResult res = run(new BidirectionalPreventsWithDefault());
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=x, b=y");
    }

    @Test
    void testBidirectionalPreventsWithDefaultsOneProvided() {
        // Only -a provided; -b gets default. Should not conflict.
        RunResult res = run(new BidirectionalPreventsWithDefault(), "-a", "val");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=val, b=y");
    }

    @Test
    void testBidirectionalPreventsWithDefaultsBothProvided() {
        // Both provided explicitly — should fail
        RunResult res = run(new BidirectionalPreventsWithDefault(), "-a", "v1", "-b", "v2");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    // --- Edge case: self-preventing option ---

    @Command(name = "test")
    static class SelfPreventing implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-a"})
        boolean a;

        @Override
        public Integer call() {
            System.out.println("a=" + a);
            return 0;
        }
    }

    @Test
    void testSelfPreventingOptionCanStillBeUsed() {
        // Self-prevention is degenerate; providing -a once should be OK
        RunResult res = run(new SelfPreventing(), "-a");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true");
    }

    // --- Error message quality: prevented name should use preferred form ---

    @Command(name = "test")
    static class ErrorMessageNames implements Callable<Integer> {
        @Option(names = {"-a", "--alpha"}, description = "Option A", prevents = {"--beta"})
        boolean a;

        @Option(names = {"-b", "--beta"}, description = "Option B")
        boolean b;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void testErrorMessageUsesPreferredNames() {
        RunResult res = run(new ErrorMessageNames(), "--alpha", "--beta");
        assertEquals(2, res.exitCode());
        // Both option names in the error should use -- long form when available
        assertThat(res.err()).contains("--alpha").contains("--beta");
    }

    // --- Bug: parent command prevents not validated when subcommand is dispatched ---

    @Command(name = "sub")
    static class SubCmd implements Callable<Integer> {
        @Option(names = "--out")
        String out;

        @Override
        public Integer call() {
            System.out.println("sub out=" + out);
            return 0;
        }
    }

    @Command(name = "parent", subcommands = {SubCmd.class})
    static class ParentWithPrevents implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-b"})
        boolean a;

        @Option(names = "-b", description = "Option B")
        boolean b;

        @Override
        public Integer call() {
            System.out.println("parent a=" + a + ", b=" + b);
            return 0;
        }
    }

    @Test
    void testParentPreventsEnforcedWithSubcommand() {
        // Parent has -a prevents -b. Both provided before subcommand — should fail.
        RunResult res = run(new ParentWithPrevents(), "-a", "-b", "sub");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testParentPreventsOkWithSubcommand() {
        // Only -a provided on parent — no conflict
        RunResult res = run(new ParentWithPrevents(), "-a", "sub", "--out", "file");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("sub out=file");
    }

    @Test
    void testParentPreventsEnforcedWithoutSubcommand() {
        // No subcommand given, parent invoked directly — prevents should be checked
        RunResult res = run(new ParentWithPrevents(), "-a", "-b");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }

    @Test
    void testParentNoConflictWithoutSubcommand() {
        // No subcommand, only -a — should succeed
        RunResult res = run(new ParentWithPrevents());
        assertEquals(0, res.exitCode());
    }

    // --- Bug: preParsedFields includes default-populated fields causing false prevents ---

    @Command(name = "fallback", subcommands = {SubCmd.class})
    static class FallbackWithPreventsAndDefault implements Callable<Integer> {
        @Option(names = "-a", description = "Option A", prevents = {"-b"})
        boolean a;

        @Option(names = "-b", description = "Option B", defaultValue = "dflt")
        String b;

        @Parameters(description = "positional")
        String pos;

        @Override
        public Integer call() {
            System.out.println("a=" + a + ", b=" + b + ", pos=" + pos);
            return 0;
        }
    }

    @Test
    void testFallbackWithDefaultDoesNotFalseConflict() {
        // -a provided, no -b, command falls through (notasub is not a subcommand).
        // -b gets its default value. Should NOT conflict.
        RunResult res = run(new FallbackWithPreventsAndDefault(), "-a", "notasub");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("a=true, b=dflt, pos=notasub");
    }

    @Test
    void testFallbackExplicitConflict() {
        // Both -a and -b provided explicitly, then positional — should conflict
        RunResult res = run(new FallbackWithPreventsAndDefault(), "-a", "-b", "val", "notasub");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("cannot be used together");
    }
}