package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge cases for mixin discovery/initialization:
 * <ul>
 *   <li>Mixin declared on a base class (inherited to subclass)</li>
 *   <li>Mixin declared on an implemented interface (should NOT be considered; interfaces have no fields)</li>
 * </ul>
 */
public class InheritedMixinEdgeCasesTest {

    static class BaseMixin {
        @Option(names = "--bm")
        int bm;
    }

    static class BaseWithMixin implements Runnable {
        @Mixin
        BaseMixin mixin;

        @Override
        public void run() {
        }
    }

    static class SubOfBase extends BaseWithMixin {
    }

    @Test
    public void mixinDeclaredInBaseClassIsDiscoveredAndInitializedForSubclass() {
        SubOfBase cmd = new SubOfBase();

        var help = FemtoCli.runCaptured(cmd, new String[]{"--help"});
        assertEquals(0, help.exitCode());
        assertTrue(help.out().contains("--bm"), () -> "Expected help to contain --bm, got:\n" + help.out());

        var run = FemtoCli.runCaptured(cmd, new String[]{"--bm", "5"});
        assertEquals(0, run.exitCode());
        assertNotNull(cmd.mixin);
        assertEquals(5, cmd.mixin.bm);
    }
}