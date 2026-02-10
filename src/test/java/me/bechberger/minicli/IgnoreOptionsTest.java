package me.bechberger.minicli;

import me.bechberger.minicli.annotations.IgnoreOptions;
import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;
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

    @Test
    public void excludeInheritedOption() {
        ExcludeOne cmd = new ExcludeOne();

        var res = MiniCli.runCaptured(cmd, new String[]{"--a", "1"});
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));

        var res2 = MiniCli.runCaptured(cmd, new String[]{"--b", "2"});
        assertEquals(0, res2.exitCode());
        assertEquals(2, cmd.b);
    }

    @Test
    public void ignoreAllThenIncludeOne() {
        OnlyB cmd = new OnlyB();

        var res = MiniCli.runCaptured(cmd, new String[]{"--a", "1"});
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));

        var res2 = MiniCli.runCaptured(cmd, new String[]{"--b", "7"});
        assertEquals(0, res2.exitCode());
        assertEquals(7, cmd.b);
    }

    @Test
    public void canFilterOptionsFromMixinsToo() {
        WithMixin cmd = new WithMixin();

        var res = MiniCli.runCaptured(cmd, new String[]{"--m", "3"});
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));
    }

    @Test
    public void canFilterMixinOptionsEvenIfMixinIsDeclaredInBaseClass() {
        SubOfBaseWithMixin cmd = new SubOfBaseWithMixin();

        var res = MiniCli.runCaptured(cmd, new String[]{"--m2", "1"});
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("Unknown option"));
    }

    @Test
    public void mixinsInParentClassesAreConsideredWhenCollectingOptions() {
        SubOfBaseWithCollectableMixin cmd = new SubOfBaseWithCollectableMixin();

        var res = MiniCli.runCaptured(cmd, new String[]{"--mi", "42"});
        assertEquals(0, res.exitCode());
        assertNotNull(cmd.mixin);
        assertEquals(42, cmd.mixin.mi);
    }
}