package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproducing tests for new bugs found during code review (April 2026).
 */
class CodeReviewBugReproTest {

    // ====================================================================
    // Bug A: findSimilarOption suggests hidden options, leaking their names
    // ====================================================================

    @Command(name = "hidden-suggest")
    static class HiddenSuggestCmd implements Runnable {
        @Option(names = "--verbose", description = "Verbose")
        boolean verbose;

        @Option(names = "--secret-debug", description = "Secret debug mode", hidden = true)
        boolean secretDebug;

        @Override
        public void run() {}
    }

    @Test
    void hiddenOptionsShouldNotBeLeakedViaSuggestions() {
        // Typo close to hidden option name; suggestion should NOT reveal hidden option
        var res = FemtoCli.builder()
                .commandConfig(c -> c.suggestSimilarOptions = true)
                .runCaptured(new HiddenSuggestCmd(), "--secret-debu");
        assertThat(res.exitCode()).isEqualTo(2);
        assertThat(res.err()).doesNotContain("--secret-debug");
    }

    // ====================================================================
    // Bug B: Mixin converter method resolution uses command class, not mixin
    // ====================================================================

    static class MixinWithConverter {
        @Option(names = "--value", converterMethod = "parseCustom")
        String value;

        String parseCustom(String raw) {
            return "CONVERTED:" + raw;
        }
    }

    @Command(name = "mixin-converter")
    static class MixinConverterCmd implements Callable<Integer> {
        @Mixin
        MixinWithConverter mixin;

        @Override
        public Integer call() {
            System.out.println(mixin.value);
            return 0;
        }
    }

    @Test
    void mixinConverterMethodShouldResolveAgainstMixinClass() {
        MixinConverterCmd cmd = new MixinConverterCmd();
        var res = FemtoCli.builder().runCaptured(cmd, "--value=hello");
        assertThat(res.exitCode()).as("err: %s", res.err()).isEqualTo(0);
        assertThat(cmd.mixin.value).isEqualTo("CONVERTED:hello");
    }

    // ====================================================================
    // Bug C: Mixin verifier method resolution uses command class, not mixin
    // ====================================================================

    static class MixinWithVerifier {
        @Option(names = "--port", verifierMethod = "validatePort")
        int port;

        void validatePort(int p) {
            if (p < 1 || p > 65535) {
                throw new VerifierException("Port must be 1-65535");
            }
        }
    }

    @Command(name = "mixin-verifier")
    static class MixinVerifierCmd implements Runnable {
        @Mixin
        MixinWithVerifier mixin;

        @Override
        public void run() {}
    }

    @Test
    void mixinVerifierMethodShouldResolveAgainstMixinClass() {
        MixinVerifierCmd cmd = new MixinVerifierCmd();
        var res = FemtoCli.builder().runCaptured(cmd, "--port=8080");
        assertThat(res.exitCode()).as("err: %s", res.err()).isEqualTo(0);

        cmd = new MixinVerifierCmd();
        var res2 = FemtoCli.builder().runCaptured(cmd, "--port=99999");
        assertThat(res2.exitCode()).isEqualTo(2);
        assertThat(res2.err()).contains("Port must be 1-65535");
    }

    // ====================================================================
    // Bug D: Agent mode normalizes tokens meant for subcommands
    // ====================================================================

    @Command(name = "child")
    static class AgentSubCmd implements Callable<Integer> {
        @Parameters(index = "0")
        String value;

        @Override
        public Integer call() {
            System.out.println("child:" + value);
            return 0;
        }
    }

    @Command(name = "parent-agent", subcommands = {AgentSubCmd.class})
    static class AgentParentCmd implements Callable<Integer> {
        @Option(names = "--flag")
        boolean flag;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void agentModeDoesNotNormalizeSubcommandArguments() {
        // "parent-agent,child,flag=test"
        // "flag=test" is a positional for child, but could incorrectly match
        // parent's --flag during normalization
        AgentParentCmd cmd = new AgentParentCmd();
        var res = FemtoCli.runAgentCaptured(cmd, "child,flag=test");
        assertThat(res.exitCode()).as("err: %s", res.err()).isEqualTo(0);
        assertThat(res.out()).contains("child:flag=test");
    }

    // ====================================================================
    // Bug E: captureExecute is not thread-safe (System.setOut/setErr)
    // ====================================================================

    @Command(name = "thread-safe-test")
    static class ThreadSafeCmd implements Callable<Integer> {
        @Option(names = "--id")
        String id = "default";

        @Override
        public Integer call() {
            // Deliberately uses System.out, which is the stream that gets corrupted
            System.out.println("ID=" + id);
            return 0;
        }
    }

    @Test
    void captureExecuteThreadSafety() throws Exception {
        int iterations = 50;
        AtomicInteger failures = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(iterations * 2);
        AtomicReference<String> corrupted = new AtomicReference<>();

        for (int i = 0; i < iterations; i++) {
            final String id = String.valueOf(i);
            new Thread(() -> {
                try {
                    startLatch.await();
                    var res = FemtoCli.builder().runCaptured(new ThreadSafeCmd(), "--id=A" + id);
                    if (!res.out().contains("ID=A" + id)) {
                        failures.incrementAndGet();
                        corrupted.compareAndSet(null, "Expected A" + id + " in stdout: '" + res.out() + "'");
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    corrupted.compareAndSet(null, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }).start();

            new Thread(() -> {
                try {
                    startLatch.await();
                    var res = FemtoCli.builder().runCaptured(new ThreadSafeCmd(), "--id=B" + id);
                    if (!res.out().contains("ID=B" + id)) {
                        failures.incrementAndGet();
                        corrupted.compareAndSet(null, "Expected B" + id + " in stdout: '" + res.out() + "'");
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    corrupted.compareAndSet(null, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertThat(failures.get())
                .as("Thread safety failures: %s", corrupted.get())
                .isEqualTo(0);
    }

    // ====================================================================
    // Bug F: parseRange("-1") not validated — negative single values allowed
    // ====================================================================

    @Test
    void parseRangeRejectsNegativeSingleValue() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> FemtoCli.parseRange("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be >= 0");
    }

    @Test
    void parseRangeAcceptsZero() {
        int[] result = FemtoCli.parseRange("0");
        assertThat(result).containsExactly(0, 0);
    }

    // ====================================================================
    // Bug G: Spec not injected into @Mixin fields (now fixed)
    // ====================================================================

    static class MixinNeedingSpec {
        @Option(names = "--debug")
        boolean debug;

        Spec spec;
    }

    @Command(name = "mixin-spec-test")
    static class MixinSpecCmd implements Callable<Integer> {
        @me.bechberger.femtocli.annotations.Mixin
        MixinNeedingSpec mixin;

        Spec cmdSpec;

        @Override
        public Integer call() { return 0; }
    }

    @Test
    void specIsInjectedIntoMixinFields() {
        MixinSpecCmd cmd = new MixinSpecCmd();
        var res = FemtoCli.builder().runCaptured(cmd, "--debug");
        assertThat(res.exitCode()).isEqualTo(0);
        assertThat(cmd.cmdSpec).as("Spec should be injected into command").isNotNull();
        assertThat(cmd.mixin.spec).as("Spec should be injected into mixin").isNotNull();
    }
}
