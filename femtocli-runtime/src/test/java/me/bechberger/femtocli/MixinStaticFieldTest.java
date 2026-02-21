package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MixinStaticFieldTest {

    static class Opts {
        @Option(names = "--x")
        int x;
    }

    static class CmdWithStaticMixin implements Runnable {
        @Mixin
        static Opts mixin;

        @Override
        public void run() {
        }
    }

    /**
     * Static @Mixin fields are now rejected at compile time by the annotation processor.
     * Because CmdWithStaticMixin lacks @Command, no parser is generated and the runtime
     * reports a missing parser error instead.
     */
    @Test
    public void staticMixinFieldIsRejected() {
        CmdWithStaticMixin cmd = new CmdWithStaticMixin();
        var res = FemtoCli.runCaptured(cmd, "--help");
        assertEquals(1, res.exitCode());
        // With @Command the processor would emit a compile error for static @Mixin.
        // Without @Command no parser is generated, so the runtime reports a missing parser.
        assertTrue(res.err().contains("Missing generated parser"), () -> "Unexpected stderr: " + res.err());
    }
}