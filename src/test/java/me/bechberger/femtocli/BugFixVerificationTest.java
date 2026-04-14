package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for three fixed bugs.
 */
class BugFixVerificationTest {

    // ── Bug 1: arity "0..1" default overwrites explicit value ──

    @Command(name = "arity01")
    static class Arity01Cmd implements Callable<Integer> {
        @Option(names = "--opt", arity = "0..1", defaultValue = "DEFAULT")
        String opt;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void arity01_noValueThenExplicit_shouldKeepExplicit() {
        var cmd = new Arity01Cmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--opt", "--opt=foo");
        assertEquals(0, res.exitCode(), "stderr: " + res.err());
        assertEquals("foo", cmd.opt);
    }

    @Test
    void arity01_explicitThenNoValue_shouldApplyDefault() {
        var cmd = new Arity01Cmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--opt=foo", "--opt");
        assertEquals(0, res.exitCode(), "stderr: " + res.err());
        assertEquals("DEFAULT", cmd.opt);
    }

    @Test
    void arity01_singleExplicit_shouldKeepValue() {
        var cmd = new Arity01Cmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--opt=bar");
        assertEquals(0, res.exitCode(), "stderr: " + res.err());
        assertEquals("bar", cmd.opt);
    }

    @Test
    void arity01_singleNoValue_shouldApplyDefault() {
        var cmd = new Arity01Cmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--opt");
        assertEquals(0, res.exitCode(), "stderr: " + res.err());
        assertEquals("DEFAULT", cmd.opt);
    }

    // ── Bug 2: --help always intercepted even with mixinStandardHelpOptions=false ──

    @Command(name = "nohelp", mixinStandardHelpOptions = false)
    static class NoHelpCmd implements Callable<Integer> {
        @Option(names = {"--help"}, description = "custom help")
        boolean customHelp;

        @Override
        public Integer call() { return customHelp ? 42 : 0; }
    }

    @Test
    void customHelpOption_notIntercepted_whenHelpDisabled() {
        var cmd = new NoHelpCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(c -> c.mixinStandardHelpOptions = false)
                .runCaptured(cmd, "--help");
        assertEquals(42, res.exitCode(),
                "With mixinStandardHelpOptions=false, --help should be a normal option. " +
                "stdout: " + res.out() + " stderr: " + res.err());
        assertTrue(cmd.customHelp);
    }

    @Command(name = "noversion", mixinStandardHelpOptions = false)
    static class NoVersionCmd implements Callable<Integer> {
        @Option(names = {"--version"})
        boolean customVersion;

        @Override
        public Integer call() { return customVersion ? 43 : 0; }
    }

    @Test
    void customVersionOption_notIntercepted_whenHelpDisabled() {
        var cmd = new NoVersionCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(c -> c.mixinStandardHelpOptions = false)
                .runCaptured(cmd, "--version");
        assertEquals(43, res.exitCode());
        assertTrue(cmd.customVersion);
    }

    @Test
    void helpStillWorks_whenHelpEnabled() {
        RunResult res = FemtoCli.runCaptured(new Arity01Cmd(), "--help");
        assertEquals(0, res.exitCode());
        assertTrue(res.out().contains("Usage:"));
    }

    // ── Bug 3: subcommand routed after -- end-of-options ──

    @Command(name = "parent", subcommands = {SubRouted.class})
    static class ParentWithSub implements Callable<Integer> {
        @Option(names = "--flag")
        boolean flag;

        @Parameters(index = "0..*", arity = "0..*")
        List<String> items;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "sub")
    static class SubRouted implements Callable<Integer> {
        @Override
        public Integer call() { return 99; }
    }

    @Test
    void subcommandAfterEndOfOptions_isPositional() {
        var cmd = new ParentWithSub();
        RunResult res = FemtoCli.runCaptured(cmd, "--flag", "--", "sub");
        assertEquals(0, res.exitCode(),
                "After --, 'sub' should be positional, not a subcommand. stderr: " + res.err());
        assertTrue(cmd.flag);
        assertNotNull(cmd.items);
        assertTrue(cmd.items.contains("sub"), "items=" + cmd.items);
    }

    @Test
    void subcommandWithoutEndOfOptions_stillRoutes() {
        var cmd = new ParentWithSub();
        RunResult res = FemtoCli.runCaptured(cmd, "--flag", "sub");
        assertEquals(99, res.exitCode(), "Without --, 'sub' should route to subcommand");
    }

    @Test
    void helpAfterEndOfOptions_isPositional() {
        var cmd = new ParentWithSub();
        RunResult res = FemtoCli.runCaptured(cmd, "--flag", "--", "--help");
        assertEquals(0, res.exitCode(),
                "--help after -- should be positional. stderr: " + res.err());
        assertNotNull(cmd.items);
        assertTrue(cmd.items.contains("--help"), "items=" + cmd.items);
    }
}
