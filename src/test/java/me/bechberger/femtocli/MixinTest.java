package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MixinTest {

    static class InheritedMixin {
        @Option(names = "--mi", description = "mixin option")
        int mi;
    }

    static class BaseWithMixin implements Runnable {
        @Mixin
        InheritedMixin mixin;

        @Override
        public void run() {
        }
    }

    static class SubCmd extends BaseWithMixin {
    }

    static class BasicMixin {
        @Option(names = "--x")
        int x;

        @Option(names = "--flag")
        boolean flag;

        @Option(names = "--d", defaultValue = "7")
        int d;
    }

    static class CmdWithBasicMixin implements Runnable {
        @Mixin
        BasicMixin mixin;

        @Override
        public void run() {
        }
    }

    @Test
    public void helpOutputIncludesOptionsFromMixinsDeclaredInParentClasses() {
        SubCmd cmd = new SubCmd();

        var help = FemtoCli.runCaptured(cmd, "--help");
        assertEquals(0, help.exitCode());
        assertTrue(help.out().contains("--mi"), () -> "Expected help to contain --mi, got:\n" + help.out());

        // parsing doesn't happen on --help, so verify assignment in a separate run
        var run = FemtoCli.runCaptured(cmd, "--mi", "123");
        assertEquals(0, run.exitCode());
        assertNotNull(cmd.mixin, "Expected inherited @Mixin field to be initialized");
        assertEquals(123, cmd.mixin.mi);
    }

    @Test
    public void basicMixinIsInitializedAndValuesAreAssigned() {
        CmdWithBasicMixin cmd = new CmdWithBasicMixin();

        var res = FemtoCli.runCaptured(cmd, "--x", "10", "--flag");
        assertEquals(0, res.exitCode());
        assertNotNull(cmd.mixin);
        assertEquals(10, cmd.mixin.x);
        assertTrue(cmd.mixin.flag);
    }

    @Test
    public void basicMixinDefaultValuesAreApplied() {
        CmdWithBasicMixin cmd = new CmdWithBasicMixin();

        var res = FemtoCli.runCaptured(cmd);
        assertEquals(0, res.exitCode());
        assertNotNull(cmd.mixin);
        assertEquals(7, cmd.mixin.d);
    }
}