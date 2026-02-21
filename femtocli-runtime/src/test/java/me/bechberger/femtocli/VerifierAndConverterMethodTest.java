package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerifierAndConverterMethodTest {

    public static class Helpers {
        static int parsePort(String s) {
            return Integer.parseInt(s);
        }

        static void verifyPort(Integer port) {
            if (port < 1 || port > 65535) {
                throw new VerifierException("port out of range");
            }
        }
    }

    static class PositiveIntVerifier implements Verifier<Integer> {
        @Override
        public void verify(Integer value) throws VerifierException {
            if (value <= 0) throw new VerifierException("must be positive");
        }
    }

    @Command(name = "cmd")
    static class Cmd implements Runnable {
        @Option(names = "--port", converterMethod = "me.bechberger.femtocli.VerifierAndConverterMethodTest$Helpers#parsePort",
                verifierMethod = "me.bechberger.femtocli.VerifierAndConverterMethodTest$Helpers#verifyPort")
        int port;

        @Parameters(index = "0", converterMethod = "toInt", verifier = PositiveIntVerifier.class)
        int n;

        int toInt(String s) {
            return Integer.parseInt(s);
        }

        @Override
        public void run() {
        }
    }

    @Test
    void optionConverterAndVerifierMethodWork() {
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--port", "123", "5");
        assertEquals(0, res.exitCode(), res.err());
        assertEquals(123, cmd.port);
        assertEquals(5, cmd.n);
    }

    @Test
    void verifierFailureIsUsageError() {
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--port", "70000", "5");
        assertEquals(2, res.exitCode());
        assertTrue(res.err().contains("port out of range"), res.err());
    }
}