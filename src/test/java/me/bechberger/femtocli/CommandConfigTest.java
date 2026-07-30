package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CommandConfig settings like helpExitCode and usageErrorsToStdout.
 */
class CommandConfigTest {

    @Command(name = "test", description = "Test command")
    static class TestCmd implements Runnable {
        @Option(names = "--value", required = true)
        String value;

        @Override
        public void run() {
        }
    }

    // --- fixture for alertOnMixedStyleInAgent tests ---

    @Command(name = "agent-test", description = "Agent test CLI",
            subcommands = {AgentTestCmd.Start.class, AgentTestCmd.Stop.class})
    static class AgentTestCmd implements Runnable {
        @Override public void run() {}

        @Command(name = "start", description = "Start")
        static class Start implements Callable<Integer> {
            @Option(names = "--config", defaultValue = "default")
            String config;
            @Override public Integer call() { return 0; }
        }

        @Command(name = "stop", description = "Stop")
        static class Stop implements Callable<Integer> {
            @Override public Integer call() { return 0; }
        }
    }

    private static RunResult runAgentCaptured(boolean alert, String... argv) {
        var outBuf = new ByteArrayOutputStream();
        var errBuf = new ByteArrayOutputStream();
        var out = new PrintStream(outBuf);
        var err = new PrintStream(errBuf);
        int code = FemtoCli.builder()
                .alertOnMixedStyleInAgent(alert)
                .runAgent(new AgentTestCmd(), out, err, argv);
        return new RunResult(outBuf.toString(), errBuf.toString(), code);
    }

    @Test
    void alertOnMixedStyleInAgent_appendsHintWhenAllSubTokensAreKnown() {
        // "start --config=lossless" as a single space-containing token — both sub-tokens are known
        var res = runAgentCaptured(true, new String[]{"start --config=lossless"});
        assertThat(res.exitCode()).isNotEqualTo(0);
        assertThat(res.err()).contains("try the agent form: start,--config=lossless");
    }

    @Test
    void alertOnMixedStyleInAgent_noHintWhenSubTokensAreUnknown() {
        // "start /path/with space" — "/path/with" is not a known option or subcommand
        var res = runAgentCaptured(true, new String[]{"start /path/with space"});
        assertThat(res.err()).doesNotContain("try the agent form");
    }

    @Test
    void alertOnMixedStyleInAgent_disabledByDefault() {
        // same bad input but flag off — no hint appended
        var res = runAgentCaptured(false, new String[]{"start --config=lossless"});
        assertThat(res.exitCode()).isNotEqualTo(0);
        assertThat(res.err()).doesNotContain("try the agent form");
    }

    @Test
    void alertOnMixedStyleInAgent_multipleTokensExpanded() {
        // Two good tokens and one space-merged one: "stop start --config=lossless" all known
        var res = runAgentCaptured(true, new String[]{"stop start --config=lossless"});
        assertThat(res.err()).contains("try the agent form");
        assertThat(res.err()).contains("stop");
        assertThat(res.err()).contains("start");
        assertThat(res.err()).contains("--config=lossless");
    }

    @Test
    void configCopyPreservesAlertOnMixedStyleInAgent() {
        var config = new CommandConfig();
        config.alertOnMixedStyleInAgent = true;
        var copy = config.copy();
        assertTrue(copy.alertOnMixedStyleInAgent);
    }

    // --- existing tests ---

    @Test
    void helpExitCodeDefaultsToZero() {
        var config = new CommandConfig();
        assertEquals(0, config.helpExitCode);

        TestCmd cmd = new TestCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(config)
                .runCaptured(cmd, "--help");

        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage:");
    }

    @Test
    void helpExitCodeCanBeCustomized() {
        var config = new CommandConfig();
        config.helpExitCode = 1;

        TestCmd cmd = new TestCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(config)
                .runCaptured(cmd, "--help");

        assertEquals(1, res.exitCode());
        assertThat(res.out()).contains("Usage:");
    }

    @Test
    void usageErrorsGoToStderrByDefault() {
        var config = new CommandConfig();
        assertFalse(config.usageErrorsToStdout);

        TestCmd cmd = new TestCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(config)
                .runCaptured(cmd, "--unknown");

        assertEquals(2, res.exitCode());
        // Error should be on stderr
        assertThat(res.err()).contains("Error: Unknown option: --unknown");
        assertThat(res.err()).contains("Usage:");
        // Stdout should be empty
        assertThat(res.out()).isEmpty();
    }

    @Test
    void usageErrorsCanBeRedirectedToStdout() {
        var config = new CommandConfig();
        config.usageErrorsToStdout = true;

        TestCmd cmd = new TestCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(config)
                .runCaptured(cmd, "--unknown");

        assertEquals(2, res.exitCode());
        // Error should be on stdout
        assertThat(res.out()).contains("Error: Unknown option: --unknown");
        assertThat(res.out()).contains("Usage:");
        // Stderr should be empty
        assertThat(res.err()).isEmpty();
    }

    @Test
    void helpExitCodeAppliesWhenHelpIsTriggeredViaUsageEx() {
        var config = new CommandConfig();
        config.helpExitCode = 42;

        @Command(name = "help-test", description = "Test", mixinStandardHelpOptions = true)
        class HelpTestCmd implements Runnable {
            @Override
            public void run() {
            }
        }

        HelpTestCmd cmd = new HelpTestCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(config)
                .runCaptured(cmd, "-h");

        assertEquals(42, res.exitCode());
        assertThat(res.out()).contains("Usage:");
    }

    @Test
    void configCopyPreservesAllFields() {
        var config = new CommandConfig();
        config.version = "1.0.0";
        config.suggestSimilarOptions = false;
        config.helpExitCode = 5;
        config.usageErrorsToStdout = true;
        config.emptyLineAfterUsage = true;

        var copy = config.copy();

        assertEquals("1.0.0", copy.version);
        assertFalse(copy.suggestSimilarOptions);
        assertEquals(5, copy.helpExitCode);
        assertTrue(copy.usageErrorsToStdout);
        assertTrue(copy.emptyLineAfterUsage);
    }
}
