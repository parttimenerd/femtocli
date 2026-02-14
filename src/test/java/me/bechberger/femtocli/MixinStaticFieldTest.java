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

    @Test
    public void staticMixinFieldIsRejected() {
        CmdWithStaticMixin cmd = new CmdWithStaticMixin();
        var res = FemtoCli.runCaptured(cmd, "--help");
        assertEquals(1, res.exitCode());
        assertTrue(res.err().contains("@Mixin field must not be static"), () -> "Unexpected stderr: " + res.err());
    }
}