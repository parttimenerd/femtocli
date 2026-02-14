package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PositionalParametersTest {
    @Test
    public void testHelp() {
        var res = FemtoCli.runCaptured(new PositionalParameters(), "--help");
        assertEquals("""
                Usage: positionalparameters [-hV] FILE [ARGS...]
                      FILE         Input file
                      [ARGS...]    Extra arguments
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                """, res.out());
    }

    @Test
    public void testPositionalParameters() {
        var res = FemtoCli.runCaptured(new PositionalParameters(), "in.txt", "arg1", "arg2");
        assertEquals("""
                File: in.txt
                Args: [arg1, arg2]
                """, res.out());
    }
}