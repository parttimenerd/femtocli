package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
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
}