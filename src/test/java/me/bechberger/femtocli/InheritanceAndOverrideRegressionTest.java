package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InheritanceAndOverrideRegressionTest {

    static class BaseMethodCmd implements Runnable {
        @Command(name = "status")
        protected int status() {
            return 7;
        }

        @Override
        public void run() {
            // no-op
        }
    }

    @Command(name = "child")
    static class ChildMethodCmd extends BaseMethodCmd {
    }

    static class BasePrivateMethodCmd implements Runnable {
        @Command(name = "hidden")
        private int hidden() {
            return 11;
        }

        @Override
        public void run() {
            // no-op
        }
    }

    @Command(name = "private-child")
    static class ChildOfPrivateMethodCmd extends BasePrivateMethodCmd {
    }

    static class GrandBase implements Runnable {
        @Option(names = "--v")
        int grand;

        @Override
        public void run() {
            // no-op
        }
    }

    static class MidBase extends GrandBase {
        @Option(names = "--v")
        int mid;
    }

    @Command(name = "ovr")
    static class OverrideCmd extends MidBase {
    }

    static class AliasBase implements Runnable {
        @Option(names = {"-v", "--value"}, required = true)
        int base;

        @Override
        public void run() {
            // no-op
        }
    }

    @Command(name = "alias")
    static class AliasOverrideCmd extends AliasBase {
        @Option(names = "--value")
        int child;
    }

    @Test
    void inheritedNonPrivateMethodSubcommandIsDiscovered() {
        RunResult res = FemtoCli.runCaptured(new ChildMethodCmd(), "status");
        assertEquals(7, res.exitCode());
    }

    @Test
    void inheritedPrivateMethodSubcommandIsNotDiscovered() {
        RunResult res = FemtoCli.runCaptured(new ChildOfPrivateMethodCmd(), "hidden");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Unexpected parameter: hidden");
    }

    @Test
    void childOptionOverridesParentOptionWithSameName() {
        OverrideCmd cmd = new OverrideCmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--v", "9");

        assertEquals(0, res.exitCode(), () -> "stderr was: " + res.err());
        assertEquals(9, cmd.mid);
        assertEquals(0, cmd.grand);
    }

    @Test
    void childAliasOverrideRemovesParentOptionCompletely() {
        AliasOverrideCmd cmd = new AliasOverrideCmd();
        RunResult res = FemtoCli.runCaptured(cmd, "--value", "2");

        assertEquals(0, res.exitCode(), () -> "stderr was: " + res.err());
        assertEquals(2, cmd.child);
        assertEquals(0, cmd.base);
        assertFalse(res.err().contains("Missing required option"));
    }
}
