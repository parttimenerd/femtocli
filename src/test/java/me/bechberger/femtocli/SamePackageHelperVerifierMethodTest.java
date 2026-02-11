package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SamePackageHelperVerifierMethodTest {

    // Top-level helper in the same package as the command.
    static class Helpers {
        static void checkPort(int p) {
            if (p < 1 || p > 65535) throw new VerifierException("port out of range");
        }
    }

    @Command(name = "verifiers")
    static class Cmd implements Runnable {
        @Option(names = "--port", verifierMethod = "Helpers#checkPort")
        int port;

        @Override
        public void run() {}
    }

    @Test
    void samePackageHelperVerifierMethodIsResolved() {
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--port", "123");
        assertEquals(0, res.exitCode(), res.err());
        assertEquals(123, cmd.port);
    }

    @Test
    void samePackageHelperVerifierMethodFailureIsReported() {
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--port", "70000");
        assertEquals(2, res.exitCode(), res.err());
        assertTrue(res.err().contains("port out of range"), res.err());
    }
}