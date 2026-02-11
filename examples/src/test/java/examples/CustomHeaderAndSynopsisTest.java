package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.CustomHeaderAndSynopsis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomHeaderAndSynopsisTest {

    @Test
    public void testHelp() {
        var res = MiniCli.runCaptured(new CustomHeaderAndSynopsis(), new String[]{"--help"});
        assertEquals(0, res.exitCode());
        assertEquals("""
                My Tool
                Copyright 2026
                Usage: mytool [OPTIONS] <file>
                Process files
                      --flag
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                
                Examples:
                  mytool --flag
                """, res.out());
    }
}