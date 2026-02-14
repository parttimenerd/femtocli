package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HiddenCommandsAndOptionsTest {

    @Test
    public void testHelpHidesOptionAndCommand() {
        var res = FemtoCli.runCaptured(new HiddenCommandsAndOptions(), "--help");
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: hidden [-hV] [--verbose] [COMMAND]
                Hide commands and options in help
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                      --verbose    Verbose output
                Commands:
                  status  Show status
                """, res.out());
    }

    @Test
    public void testCommandStillRunsWhenHidden() {
        // Hidden commands should still be invokable.
        var res = FemtoCli.runCaptured(new HiddenCommandsAndOptions(), "internal");
        assertEquals(0, res.exitCode());
        assertEquals("INTERNAL\n", res.out());
    }
}