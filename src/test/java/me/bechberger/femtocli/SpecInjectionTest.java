package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class SpecInjectionTest {

    @Command(name = "spec-test", mixinStandardHelpOptions = true)
    static class SpecCmd implements Runnable {
        Spec spec;

        @Option(names = "--x")
        int x;

        @Override
        public void run() {
            assertNotNull(spec);
            assertSame(this, spec.command());
            assertNotNull(spec.out());
            assertNotNull(spec.err());

            // exercise usage helpers
            spec.usage();
            spec.usage(new PrintStream(System.out));

            System.out.println("x=" + x);
        }
    }

    @Test
    void specIsInjectedAndUsable() {
        var cmd = new SpecCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--x", "5");
        assertEquals(0, res.exitCode(), res.err());

        // Usage printed + command output
        assertTrue(res.out().contains("Usage: spec-test"), res.out());
        assertTrue(res.out().contains("x=5"), res.out());
    }

    @Command(name = "spec-bad", mixinStandardHelpOptions = true)
    static class BadSpecCmd implements Runnable {
        // Not a Spec type -> should not be injected and should not cause an error.
        String notASpec;

        @Override
        public void run() {
        }
    }

    @Test
    void nonSpecFieldDoesNotCauseUsageError() {
        var cmd = new BadSpecCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd);
        assertEquals(0, res.exitCode(), res.err());
    }

    @Test
    void specUsageRespectsBuilderCommandConfig() {
        @Command(name = "spec-config", description = "Spec config", mixinStandardHelpOptions = true)
        class SpecConfigCmd implements Runnable {
            Spec spec;

            @Override
            public void run() {
                // Print usage to the configured output so we can assert formatting.
                spec.usage();
            }
        }

        var cmd = new SpecConfigCmd();

        var cfg = new CommandConfig();
        cfg.emptyLineAfterUsage = true;

        RunResult res = FemtoCli.builder()
                .commandConfig(cfg)
                .runCaptured(cmd);

        assertEquals(0, res.exitCode(), res.err());

        // When emptyLineAfterUsage=true, HelpRenderer prints a blank line after the synopsis.
        // The synopsis line includes option placeholders (currently "[-hV]" for standard help options).
        assertTrue(res.out().contains("Usage: spec-config"), res.out());
        assertTrue(res.out().contains("Usage: spec-config [-hV]\n\n"), res.out());
    }

    @Test
    void specInjectionIsSafeUnderSynchronizedConcurrentRuns() {
        @Command(name = "spec-sync", description = "Spec sync", mixinStandardHelpOptions = true)
        class SpecSyncCmd implements Runnable {
            Spec spec;

            @Override
            public void run() {
                synchronized (SpecInjectionTest.class) {
                    assertNotNull(spec);
                    spec.usage();
                }
            }
        }

        // Two independent runs should not interfere. We don't use real parallelism to keep it deterministic,
        // but the synchronized block documents the intended usage.
        RunResult r1 = FemtoCli.builder().runCaptured(new SpecSyncCmd());
        RunResult r2 = FemtoCli.builder().runCaptured(new SpecSyncCmd());

        assertEquals(0, r1.exitCode(), r1.err());
        assertEquals(0, r2.exitCode(), r2.err());
        assertTrue(r1.out().contains("Usage: spec-sync"));
        assertTrue(r2.out().contains("Usage: spec-sync"));
    }
}