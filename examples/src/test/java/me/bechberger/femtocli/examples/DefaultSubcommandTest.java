package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultSubcommandTest {

    @Test
    public void testImplicitDefaultSubcommand() {
        // "app 1234" should behave like "app status 1234"
        var res = FemtoCli.runCaptured(new DefaultSubcommand(), "1234");
        assertEquals(0, res.exitCode());
        assertEquals("Status of 1234\n", res.out());
    }

    @Test
    public void testExplicitStatusSubcommand() {
        var res = FemtoCli.runCaptured(new DefaultSubcommand(), "status", "1234");
        assertEquals(0, res.exitCode());
        assertEquals("Status of 1234\n", res.out());
    }

    @Test
    public void testExplicitStatusWithVerbose() {
        var res = FemtoCli.runCaptured(new DefaultSubcommand(), "status", "1234", "--verbose");
        assertEquals(0, res.exitCode());
        assertEquals("Status of 1234 (verbose)\n", res.out());
    }

    @Test
    public void testImplicitDefaultWithVerbose() {
        // "app 1234 --verbose" should route to status with --verbose
        var res = FemtoCli.runCaptured(new DefaultSubcommand(), "1234", "--verbose");
        assertEquals(0, res.exitCode());
        assertEquals("Status of 1234 (verbose)\n", res.out());
    }

    @Test
    public void testExplicitListSubcommand() {
        var res = FemtoCli.runCaptured(new DefaultSubcommand(), "list");
        assertEquals(0, res.exitCode());
        assertEquals("Listing all targets...\n", res.out());
    }

    @Test
    public void testNoArgsInvokesRoot() {
        var res = FemtoCli.runCaptured(new DefaultSubcommand());
        assertEquals(0, res.exitCode());
        assertEquals("Usage: app <command> [options]\n", res.out());
    }

    @Test
    public void testHelp() {
        var res = FemtoCli.runCaptured(new DefaultSubcommand(), "--help");
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: app [-hV] [COMMAND]
                Example app with a default subcommand
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                Commands:
                  status  Show status for a target
                  list    List all targets
                """, res.out());
    }
}