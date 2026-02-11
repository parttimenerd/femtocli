package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumsAndCompletionCandidatesTest {
    @Test
    public void testHelp() {
        var res = FemtoCli.runCaptured(new EnumsAndCompletionCandidates(), new String[]{"--help"});
        assertEquals("""
                Usage: enums [-hV] [--mode=<mode>]
                  -h, --help       Show this help message and exit.
                      --mode=<mode>
                                   Mode (fast, safe), default: safe
                  -V, --version    Print version information and exit.
                """, res.out());
    }

    @Test
    public void testDefault() {
         var res = FemtoCli.runCaptured(new EnumsAndCompletionCandidates(), new String[0]);
         assertEquals("Mode: safe\n", res.out());
    }

    @Test
    public void testFast() {
         var res = FemtoCli.runCaptured(new EnumsAndCompletionCandidates(), new String[]{"--mode", "fast"});
         assertEquals("Mode: fast\n", res.out());
    }
}