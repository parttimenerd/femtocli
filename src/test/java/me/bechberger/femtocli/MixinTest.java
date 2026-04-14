package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import me.bechberger.femtocli.annotations.Command;
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

    static class PreinitializedMixin {
        @Option(names = "--val")
        int val = 5;
    }

    static class CmdWithPreinitializedMixin implements Runnable {
        @Mixin
        PreinitializedMixin mixin = new PreinitializedMixin();

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

    @Test
    public void preinitializedMixinInstanceIsPreserved() {
        CmdWithPreinitializedMixin cmd = new CmdWithPreinitializedMixin();
        cmd.mixin.val = 42;

        var res = FemtoCli.runCaptured(cmd);
        assertEquals(0, res.exitCode());
        assertNotNull(cmd.mixin);
        assertEquals(42, cmd.mixin.val);
    }

    // --- Bug: @Parameters on mixin objects were silently ignored ---

    static class SharedParams {
        @Parameters(index = "0", description = "Input file")
        String input;
    }

    @Command(name = "test")
    static class CmdWithMixinParams implements Runnable {
        @Mixin
        SharedParams shared;

        @Option(names = "--verbose")
        boolean verbose;

        @Override
        public void run() {
        }
    }

    @Test
    public void parametersFromMixinAreCollectedAndBound() {
        CmdWithMixinParams cmd = new CmdWithMixinParams();
        var res = FemtoCli.runCaptured(cmd, "myfile.txt");
        assertEquals(0, res.exitCode());
        assertNotNull(cmd.shared);
        assertEquals("myfile.txt", cmd.shared.input);
    }

    @Test
    public void parametersFromMixinWorkWithOptions() {
        CmdWithMixinParams cmd = new CmdWithMixinParams();
        var res = FemtoCli.runCaptured(cmd, "--verbose", "myfile.txt");
        assertEquals(0, res.exitCode());
        assertTrue(cmd.verbose);
        assertNotNull(cmd.shared);
        assertEquals("myfile.txt", cmd.shared.input);
    }

    static class SharedVarargs {
        @Parameters(index = "0..*", description = "Files")
        java.util.List<String> files;
    }

    @Command(name = "test")
    static class CmdWithMixinVarargs implements Runnable {
        @Mixin
        SharedVarargs shared;

        @Override
        public void run() {
        }
    }

    @Test
    public void varargsParametersFromMixinWork() {
        CmdWithMixinVarargs cmd = new CmdWithMixinVarargs();
        var res = FemtoCli.runCaptured(cmd, "a.txt", "b.txt", "c.txt");
        assertEquals(0, res.exitCode());
        assertNotNull(cmd.shared);
        assertEquals(java.util.List.of("a.txt", "b.txt", "c.txt"), cmd.shared.files);
    }
}