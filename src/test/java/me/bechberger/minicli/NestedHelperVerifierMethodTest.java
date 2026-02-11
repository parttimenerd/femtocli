package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NestedHelperVerifierMethodTest {

    @Command(name = "verifiers")
    static class Cmd implements Runnable {

        static class Helpers {
            static void checkPort(int p) {
                if (p < 1 || p > 65535) throw new VerifierException("port out of range");
            }
        }

        @Option(names = "--port", verifierMethod = "Helpers#checkPort")
        int port;

        @Override
        public void run() {}
    }

    @Test
    void nestedHelperVerifierMethodIsResolved() {
        Cmd cmd = new Cmd();
        RunResult res = MiniCli.builder().runCaptured(cmd, "--port", "123");
        assertEquals(0, res.exitCode(), res.err());
        assertEquals(123, cmd.port);
    }

    @Test
    void nestedHelperVerifierMethodFailureIsReported() {
        Cmd cmd = new Cmd();
        RunResult res = MiniCli.builder().runCaptured(cmd, "--port", "70000");
        assertEquals(2, res.exitCode(), res.err());
        assertTrue(res.err().contains("port out of range"), res.err());
    }
}