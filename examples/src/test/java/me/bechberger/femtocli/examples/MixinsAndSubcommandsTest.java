package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MixinsAndSubcommandsTest {

    private static String normalize(String s) {
        // Make assertions stable across minor whitespace formatting differences.
        return s.replaceAll("[ \\t]+(?=\\r?\\n)", "");
    }

    @Test
    public void testHelp() {
        var res = FemtoCli.runCaptured(new MixinsAndSubcommands(), "--help");
        assertEquals(normalize("""
                Usage: mixins [-hV] [COMMAND]
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                Commands:
                  a
                  b
                """), normalize(res.out()));
    }

    @Test
    public void testSubCommandHelp() {
        var res = FemtoCli.runCaptured(new MixinsAndSubcommands(), "a", "--help");
        assertEquals(normalize("""
                Usage: mixins a [-hV] [--verbose]
                  -h, --help       Show this help message and exit.
                  -v, --verbose
                  -V, --version    Print version information and exit.
                """), normalize(res.out()));
    }

    @Test
    public void testSubCommand() {
        var res = FemtoCli.runCaptured(new MixinsAndSubcommands(), "a");
        assertEquals("Verbose: false\n", res.out());
    }
}