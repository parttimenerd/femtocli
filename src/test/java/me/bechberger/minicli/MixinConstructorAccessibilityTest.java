package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MixinConstructorAccessibilityTest {

    // A mixin type with package-private constructor to reproduce accessibility concerns
    static class MixinWithPackageCtor {
        // package-private ctor
        MixinWithPackageCtor() {}

        @Option(names = "--mix-flag")
        boolean mixFlag;
    }

    @Command(name = "cmd")
    static class CmdWithMixin implements Runnable {
        @Mixin
        MixinWithPackageCtor mixin;

        @Override
        public void run() {}
    }

    @Test
    void mixinIsInstantiatedDespitePackagePrivateCtor() {
        CmdWithMixin cmd = new CmdWithMixin();
        // Run the CLI to force model construction which initializes mixins
        RunResult res = MiniCli.builder().runCaptured(cmd, "--mix-flag");
        // Should not throw IllegalAccessException; exit code 0 and flag set to true
        assertEquals(0, res.exitCode(), res.err());
        assertNotNull(cmd.mixin, "Mixin field should have been instantiated");
        assertTrue(cmd.mixin.mixFlag, "Mixin flag should be set by option");
    }
}