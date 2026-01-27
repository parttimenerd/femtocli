package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproducer for "default Duration option isn't applied when run under Maven/Surefire".
 *
 * This intentionally uses System.out printing (like a typical CLI) and asserts the captured output.
 */
public class ReproducerDefaultDurationMavenTest {

    @Command(name = "defaultTest")
    static class DefaultTest implements Runnable {
        @Option(names = {"-i", "--interval"}, defaultValue = "10ms",
                description = "Sampling interval (default: 10ms)")
        private Duration interval;

        @Override
        public void run() {
            // This throws NPE if default wasn't applied.
            System.out.println(interval.toMillis());
        }
    }

    @Test
    void defaultDurationIsAppliedAndCaptured() {
        DefaultTest cmd = new DefaultTest();
        RunResult res = MiniCli.builder().runCaptured(cmd);

        assertEquals(0, res.exitCode(), () -> "stderr=" + res.err());
        assertEquals("10", res.out().trim(), () -> "stdout was: '" + res.out() + "'");
        assertNotNull(cmd.interval);
        assertEquals(Duration.ofMillis(10), cmd.interval);
    }
}