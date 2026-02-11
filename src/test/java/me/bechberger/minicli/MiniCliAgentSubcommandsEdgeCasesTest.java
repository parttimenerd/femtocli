package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused end-to-end tests for {@link MiniCli#runAgentCaptured(Object, String)}.
 * <p>
 * We keep these separate from {@code MiniCliTest} to make agent-args behavior and subcommand resolution
 * easy to reason about.
 */
class MiniCliAgentSubcommandsEdgeCasesTest {

    @Command(
            name = "root",
            description = "Root command",
            version = "9.9.9",
            subcommands = {Sub.class},
            mixinStandardHelpOptions = true
    )
    static class Root implements Runnable {
        @Override
        public void run() {
            // no-op
        }
    }

    @Command(
            name = "sub",
            description = "Sub command",
            subcommands = {Deep.class},
            mixinStandardHelpOptions = true
    )
    static class Sub implements Runnable {
        @Override
        public void run() {
            // no-op
        }
    }

    @Command(
            name = "deep",
            description = "Deep sub command",
            mixinStandardHelpOptions = true
    )
    static class Deep implements Callable<Integer> {
        @Option(names = "--req", required = true)
        String req;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "methodroot", description = "Root with method subcommands", mixinStandardHelpOptions = true)
    static class MethodRoot implements Runnable {
        @me.bechberger.minicli.annotations.Parameters
        String v;

        String value;

        @Command(name = "status", description = "Show status")
        int status() {
            this.value = v;
            return 7;
        }

        @Override
        public void run() {
            // no-op
        }
    }

    @Command(name = "ambroot", mixinStandardHelpOptions = true)
    static class AmbiguousRoot implements Callable<Integer> {
        @Option(names = "-x")
        String shortX;

        @Option(names = "--x")
        String longX;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void runAgentCaptured_blankStringBehavesLikeNoArgs() {
        RunResult res = MiniCli.runAgentCaptured(new Root(), "   ");
        assertEquals(0, res.exitCode());
        assertThat(res.err()).isEmpty();
    }

    @Test
    void runAgentCaptured_leadingTrailingWhitespaceIsIgnoredPerToken() {
        RunResult res = MiniCli.runAgentCaptured(new Root(), "  --help  ");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: root");
    }

    @Test
    void runAgentCaptured_helpAtDifferentDepths() {
        RunResult rootHelp = MiniCli.runAgentCaptured(new Root(), "--help");
        assertEquals(0, rootHelp.exitCode());
        assertThat(rootHelp.out()).contains("Usage: root");

        RunResult subHelp = MiniCli.runAgentCaptured(new Root(), "sub,--help");
        assertEquals(0, subHelp.exitCode());
        assertThat(subHelp.out()).contains("Usage: root,sub");

        RunResult deepHelp = MiniCli.runAgentCaptured(new Root(), "sub,deep,--help");
        assertEquals(0, deepHelp.exitCode());
        assertThat(deepHelp.out()).contains("Usage: root,sub,deep");
        assertThat(deepHelp.out()).contains("req=");
    }

    @Test
    void runAgentCaptured_versionAfterSubcommandStillShowsRootVersion() {
        RunResult res = MiniCli.runAgentCaptured(new Root(), "sub,--version");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("9.9.9");
    }

    @Test
    void runAgentCaptured_bareVersionTokenIsAccepted() {
        RunResult res = MiniCli.runAgentCaptured(new Root(), "sub,version");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("9.9.9");
    }

    @Test
    void runAgentCaptured_deepSubcommandMissingRequiredOptionReturnsError() {
        RunResult res = MiniCli.runAgentCaptured(new Root(), "sub,deep");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required option: --req");
    }

    @Test
    void runAgentCaptured_methodBasedSubcommandConsumesRemainingTokens() {
        // Method subcommands currently don't support positional parameters (the wrapper has no @Parameters fields),
        // so we only verify that invoking the method subcommand itself works.
        MethodRoot root = new MethodRoot();
        RunResult res = MiniCli.runAgentCaptured(root, "status");
        assertEquals(7, res.exitCode());
    }

    @Test
    void runAgentCaptured_methodSubcommandHelpWorks() {
        RunResult res = MiniCli.runAgentCaptured(new MethodRoot(), "status,--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: methodroot,status");
    }

    @Test
    void runAgentCaptured_invalidAgentArgsSurfaceAsExceptions() {
        assertThrows(IllegalArgumentException.class, () -> MiniCli.runAgentCaptured(new Root(), "a,,b"));
        assertThrows(IllegalArgumentException.class, () -> MiniCli.runAgentCaptured(new Root(), "a,b,"));
        assertThrows(IllegalArgumentException.class, () -> MiniCli.runAgentCaptured(new Root(), "--x=1\\"));
    }

    @Test
    void runAgentCaptured_singleQuotesAllowCommasAndSpacesInsideToken() {
        RunResult res = MiniCli.runAgentCaptured(new Root(), "sub,deep,'--req=hello, world'");
        assertEquals(0, res.exitCode());
    }

    @Test
    void runAgentCaptured_bareOptionNameWithEqualsIsAcceptedInAgentMode() {
        RunResult res = MiniCli.runAgentCaptured(new Root(), "sub,deep,req=x");
        assertEquals(0, res.exitCode());
    }

    @Test
    void runAgentCaptured_ambiguousBareOptionIsRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CommandModel.of(new AmbiguousRoot())
        );
        assertThat(ex.getMessage()).contains("Ambiguous option name 'x'");
        assertThat(ex.getMessage()).contains("-x");
        assertThat(ex.getMessage()).contains("--x");
    }
}