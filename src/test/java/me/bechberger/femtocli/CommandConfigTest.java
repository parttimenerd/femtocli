package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(false, config.usageErrorsToStdout);

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
        assertEquals(false, copy.suggestSimilarOptions);
        assertEquals(5, copy.helpExitCode);
        assertEquals(true, copy.usageErrorsToStdout);
        assertEquals(true, copy.emptyLineAfterUsage);
    }
}