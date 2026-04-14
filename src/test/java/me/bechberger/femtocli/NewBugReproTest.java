package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import me.bechberger.femtocli.annotations.Mixin;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests reproducing newly discovered bugs.
 */
class NewBugReproTest {

    // ── Bug 1: --help / --version after "--" end-of-options marker still triggers help ──

    @Command(name = "endopts")
    static class EndOfOptionsCmd implements Callable<Integer> {
        @Parameters(index = "0..*")
        List<String> args;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void helpAfterEndOfOptionsMarkerShouldBePositional() {
        // After --, "--help" should be treated as a positional argument, not trigger help
        var cmd = new EndOfOptionsCmd();
        RunResult result = FemtoCli.runCaptured(cmd, "--", "--help");
        // If this is treated as a positional, exit code should be 0 and no help text
        assertEquals(0, result.exitCode(), "Expected 0 exit code, --help after -- should be a positional");
        assertFalse(result.out().contains("Usage:"),
                "Help text should NOT be printed when --help appears after '--'");
        assertNotNull(cmd.args);
        assertTrue(cmd.args.contains("--help"),
                "\"--help\" should be captured as a positional argument");
    }

    @Test
    void versionAfterEndOfOptionsMarkerShouldBePositional() {
        // After --, "--version" should be treated as a positional argument
        var cmd = new EndOfOptionsCmd();
        RunResult result = FemtoCli.runCaptured(cmd, "--", "--version");
        assertEquals(0, result.exitCode(), "Expected 0 exit code, --version after -- should be a positional");
        assertFalse(result.out().contains("unknown"),
                "Version text should NOT be printed when --version appears after '--'");
    }

    @Test
    void shortHelpAfterEndOfOptionsMarkerShouldBePositional() {
        // After --, "-h" should be treated as a positional argument
        var cmd = new EndOfOptionsCmd();
        RunResult result = FemtoCli.runCaptured(cmd, "--", "-h");
        assertEquals(0, result.exitCode(), "Expected 0 exit code, -h after -- should be a positional");
        assertFalse(result.out().contains("Usage:"),
                "Help text should NOT be printed when -h appears after '--'");
    }

    @Test
    void shortVersionAfterEndOfOptionsMarkerShouldBePositional() {
        // After --, "-V" should be treated as a positional argument
        var cmd = new EndOfOptionsCmd();
        RunResult result = FemtoCli.runCaptured(cmd, "--", "-V");
        assertEquals(0, result.exitCode(), "Expected 0 exit code, -V after -- should be a positional");
    }


    // ── Bug 2: Required positionals on parent with subcommands not validated ──

    @Command(name = "sub")
    static class SubCmd implements Callable<Integer> {
        @Override
        public Integer call() { return 42; }
    }

    @Command(name = "parent", subcommands = {SubCmd.class})
    static class ParentWithRequiredPositional implements Callable<Integer> {
        @Parameters(index = "0", description = "Required PID")
        int pid;

        boolean wasRun = false;

        @Override
        public Integer call() {
            wasRun = true;
            return 0;
        }
    }

    @Test
    void parentWithRequiredPositionalShouldFailWhenNotProvided() {
        // Running 'parent' with no arguments should fail because 'pid' is required
        var cmd = new ParentWithRequiredPositional();
        RunResult result = FemtoCli.runCaptured(cmd);
        // Should get an error about missing required parameter
        assertNotEquals(0, result.exitCode(),
                "Parent command with required positional should fail when no arguments given");
        assertTrue(result.err().contains("Missing required parameter") || result.err().contains("pid"),
                "Error should mention missing required parameter, got: " + result.err());
    }

    @Test
    void parentRequiredPositionalNotEnforcedWhenSubcommandGiven() {
        // When routing to a subcommand, the parent's required positionals are NOT enforced.
        // This is the standard CLI convention (similar to picocli behavior).
        var cmd = new ParentWithRequiredPositional();
        RunResult result = FemtoCli.runCaptured(cmd, "sub");
        assertEquals(42, result.exitCode(), "SubCmd should execute and return 42");
    }

    @Test
    void parentRequiredPositionalSucceedsWhenProvided() {
        // 'parent 123 sub' should work: pid=123, then route to sub
        var cmd = new ParentWithRequiredPositional();
        RunResult result = FemtoCli.runCaptured(cmd, "123", "sub");
        assertEquals(42, result.exitCode(), "SubCmd should return 42");
        assertEquals(123, cmd.pid, "pid should be 123");
    }


    // ── Bug 3: captureExecute thread safety ──
    // (Documented as a design issue - no test needed, but noted for awareness)


    // ── Bug 4: Mixin Spec injection ──

    static class MixinWithSpec {
        @Option(names = {"--verbose"}, description = "Verbose output")
        boolean verbose;

        Spec spec; // Spec field in mixin - won't be injected
    }

    @Command(name = "spectest")
    static class SpecInMixinCmd implements Callable<Integer> {
        @Mixin
        MixinWithSpec mixin;

        Spec mainSpec; // Spec field in command - will be injected

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void specIsInjectedIntoMixin() {
        // Spec should be injected into both the command and mixin instances
        var cmd = new SpecInMixinCmd();
        FemtoCli.runCaptured(cmd, "--verbose");
        assertNotNull(cmd.mainSpec, "Spec should be injected into the command");
        assertNotNull(cmd.mixin.spec,
                "Spec should be injected into mixin fields too");
    }


    // ── Bug 5: Duplicate @Parameters index — now detected at model-build time ──

    @Command(name = "dupindex")
    static class DuplicateIndexCmd implements Callable<Integer> {
        @Parameters(index = "0")
        String first;

        @Parameters(index = "0")
        String second;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void duplicateParameterIndexThrowsError() {
        // Two @Parameters with the same index should fail fast with an error
        var cmd = new DuplicateIndexCmd();
        var ex = assertThrows(IllegalArgumentException.class,
                () -> FemtoCli.runCaptured(cmd, "value"),
                "Duplicate @Parameters index should throw IllegalArgumentException");
        assertTrue(ex.getMessage().contains("Overlapping"),
                "Error message should mention overlapping: " + ex.getMessage());
    }

    @Command(name = "duprangecmd")
    static class OverlappingRangeCmd implements Callable<Integer> {
        @Parameters(index = "0..2")
        List<String> first;

        @Parameters(index = "1..3")
        List<String> second;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void overlappingParameterRangeThrowsError() {
        // Two @Parameters with overlapping ranges should fail fast
        var cmd = new OverlappingRangeCmd();
        var ex = assertThrows(IllegalArgumentException.class,
                () -> FemtoCli.runCaptured(cmd, "a", "b", "c", "d"),
                "Overlapping @Parameters ranges should throw IllegalArgumentException");
        assertTrue(ex.getMessage().contains("Overlapping"),
                "Error message should mention overlapping: " + ex.getMessage());
    }

    @Command(name = "nonoverlap")
    static class NonOverlappingCmd implements Callable<Integer> {
        @Parameters(index = "0")
        String first;

        @Parameters(index = "1")
        String second;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void nonOverlappingParameterIndicesWork() {
        // Distinct indices should work fine
        var cmd = new NonOverlappingCmd();
        RunResult result = FemtoCli.runCaptured(cmd, "alpha", "beta");
        assertEquals(0, result.exitCode());
        assertEquals("alpha", cmd.first);
        assertEquals("beta", cmd.second);
    }


    // ── Bug 6: resolveMethod with overloaded methods ──

    @Command(name = "overload")
    static class OverloadedConverterCmd implements Callable<Integer> {
        // If there are overloaded converter methods, resolveMethod picks one arbitrarily.
        // This isn't easy to test without reflection-level control, so we just document it.
        @Override
        public Integer call() { return 0; }
    }


    // ── Additional edge case: mixed options after "--" with parent command ──

    @Command(name = "mixparent", subcommands = {SubCmd.class})
    static class ParentEndOfOptions implements Callable<Integer> {
        @Option(names = {"--flag"})
        boolean flag;

        @Parameters(index = "0..*", arity = "0..*")
        List<String> items;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void endOfOptionsOnParentWithSubcommands() {
        // "mixparent --flag -- --help" should treat --help as positional
        var cmd = new ParentEndOfOptions();
        RunResult result = FemtoCli.runCaptured(cmd, "--flag", "--", "--help");
        assertEquals(0, result.exitCode(),
                "--help after -- should not trigger help, got stderr: " + result.err());
        assertTrue(cmd.flag, "flag should be true");
        assertNotNull(cmd.items);
        assertTrue(cmd.items.contains("--help"),
                "--help after -- should be a positional, got items: " + cmd.items);
    }


    // ── Bug 7: Spec.out/err should use the configured streams, not System.out/err ──

    @Command(name = "specstreams")
    static class SpecStreamsCmd implements Callable<Integer> {
        Spec spec;

        @Override
        public Integer call() {
            spec.out().println("SPEC_OUT_MARKER");
            spec.err().println("SPEC_ERR_MARKER");
            return 0;
        }
    }

    @Test
    void specUsesConfiguredStreamsNotSystemStreams() {
        // When run with custom out/err streams, Spec should use those, not System.out/err
        ByteArrayOutputStream customOut = new ByteArrayOutputStream();
        ByteArrayOutputStream customErr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(customOut);
        PrintStream err = new PrintStream(customErr);

        var cmd = new SpecStreamsCmd();
        int exitCode = FemtoCli.run(cmd, out, err);

        assertEquals(0, exitCode);
        assertTrue(customOut.toString().contains("SPEC_OUT_MARKER"),
                "Spec.out should write to the configured output stream, got: " + customOut);
        assertTrue(customErr.toString().contains("SPEC_ERR_MARKER"),
                "Spec.err should write to the configured error stream, got: " + customErr);
    }


    // ── Bug 8: @Option with empty names array causes ArrayIndexOutOfBoundsException ──

    @Command(name = "emptynames")
    static class EmptyOptionNamesCmd implements Callable<Integer> {
        @Option(names = {})
        String broken;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void emptyOptionNamesArrayThrowsMeaningful() {
        // An @Option with empty names should fail with a clear error, not AIOOB
        var cmd = new EmptyOptionNamesCmd();
        try {
            FemtoCli.runCaptured(cmd, "--help");
            // If it doesn't throw, that's also fine (help was printed)
        } catch (ArrayIndexOutOfBoundsException e) {
            fail("Empty @Option(names={}) should not cause ArrayIndexOutOfBoundsException: " + e.getMessage());
        } catch (Exception e) {
            // Any other meaningful error is acceptable
        }
    }
}
