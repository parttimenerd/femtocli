package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.HiddenCommandsAndOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HiddenCommandsAndOptionsTest {

    @Test
    public void testHelpHidesOptionAndCommand() {
        var res = MiniCli.runCaptured(new HiddenCommandsAndOptions(), new String[]{"--help"});
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
        var res = MiniCli.runCaptured(new HiddenCommandsAndOptions(), new String[]{"internal"});
        assertEquals(0, res.exitCode());
        assertEquals("INTERNAL\n", res.out());
    }
}