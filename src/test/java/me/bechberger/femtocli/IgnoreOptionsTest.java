package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.IgnoreOptions;
import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IgnoreOptionsTest {

    static class BaseCmd implements Runnable {
        @Option(names = "--a")
        int a;

        @Option(names = "--b")
        int b;

        @Override
        public void run() {
        }
    }

    @IgnoreOptions(exclude = "--a")
    static class ExcludeOne extends BaseCmd {
    }

    @IgnoreOptions(ignoreAll = true, include = "--b")
    static class OnlyB extends BaseCmd {
    }

    @IgnoreOptions(exclude = "--m")
    static class MixinOpts {
        @Option(names = "--m")
        int m;
    }

    static class WithMixin implements Runnable {
        @Mixin
        MixinOpts mixin;

        @Override
        public void run() {
        }
    }

    @IgnoreOptions(exclude = "--m2")
    static class MixinInBase {
        @Option(names = "--m2")
        int m2;
    }

    static class BaseWithMixin implements Runnable {
        @Mixin
        MixinInBase mixin;

        @Override
        public void run() {
        }
    }

    static class SubOfBaseWithMixin extends BaseWithMixin {
    }

    static class InheritedMixinOpts {
        @Option(names = "--mi")
        int mi;
    }

    static class BaseWithCollectableMixin implements Runnable {
        @Mixin
        InheritedMixinOpts mixin;

        @Override
        public void run() {
        }
    }

    static class SubOfBaseWithCollectableMixin extends BaseWithCollectableMixin {
    }

    static class SharedMixin {
        @Option(names = "--shared")
        int shared;

        @Option(names = "--kept")
        int kept;
    }

    /** Command-level @IgnoreOptions must also filter options contributed by a @Mixin. */
    @IgnoreOptions(exclude = "--shared")
    static class CmdExcludingMixinOption implements Runnable {
        @Mixin
        SharedMixin mixin;

        @Override
        public void run() {
        }
    }

    /** Sibling command reusing the same mixin without filtering — proves the mixin is unmodified. */
    static class CmdKeepingMixinOption implements Runnable {
        @Mixin
        SharedMixin mixin;

        @Override
        public void run() {
        }
    }

    @Test
    public void excludeInheritedOption() {
        ExcludeOne cmd = new ExcludeOne();

        var res = FemtoCli.runCaptured(cmd, "--a", "1");
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));

        var res2 = FemtoCli.runCaptured(cmd, "--b", "2");
        assertEquals(0, res2.exitCode());
        assertEquals(2, cmd.b);
    }

    @Test
    public void ignoreAllThenIncludeOne() {
        OnlyB cmd = new OnlyB();

        var res = FemtoCli.runCaptured(cmd, "--a", "1");
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));

        var res2 = FemtoCli.runCaptured(cmd, "--b", "7");
        assertEquals(0, res2.exitCode());
        assertEquals(7, cmd.b);
    }

    @Test
    public void canFilterOptionsFromMixinsToo() {
        WithMixin cmd = new WithMixin();

        var res = FemtoCli.runCaptured(cmd, "--m", "3");
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));
    }

    @Test
    public void canFilterMixinOptionsEvenIfMixinIsDeclaredInBaseClass() {
        SubOfBaseWithMixin cmd = new SubOfBaseWithMixin();

        var res = FemtoCli.runCaptured(cmd, "--m2", "1");
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));
    }

    @Test
    public void mixinsInParentClassesAreConsideredWhenCollectingOptions() {
        SubOfBaseWithCollectableMixin cmd = new SubOfBaseWithCollectableMixin();

        var res = FemtoCli.runCaptured(cmd, "--mi", "42");
        assertEquals(0, res.exitCode());
        assertNotNull(cmd.mixin);
        assertEquals(42, cmd.mixin.mi);
    }

    @Test
    public void commandLevelIgnoreFiltersMixinOption() {
        CmdExcludingMixinOption cmd = new CmdExcludingMixinOption();

        var excluded = FemtoCli.runCaptured(cmd, "--shared", "3");
        assertEquals(2, excluded.exitCode());
        assertTrue(excluded.err().contains("Unknown option"));

        // sibling options on the same mixin remain available
        var kept = FemtoCli.runCaptured(cmd, "--kept", "5");
        assertEquals(0, kept.exitCode());
        assertEquals(5, cmd.mixin.kept);
    }

    @Test
    public void commandLevelIgnoreDoesNotAffectOtherCommandsSharingMixin() {
        CmdKeepingMixinOption cmd = new CmdKeepingMixinOption();

        var res = FemtoCli.runCaptured(cmd, "--shared", "9");
        assertEquals(0, res.exitCode());
        assertEquals(9, cmd.mixin.shared);
    }
}