package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.MixinsAndSubcommands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MixinsAndSubcommandsTest {

    @Test
    public void testHelp() {
        var res = MiniCli.runCaptured(new MixinsAndSubcommands(), new String[]{"--help"});
        assertEquals("""
                Usage: mixins [OPTIONS] <command>
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                Commands:
                  sub1    Subcommand 1
                  sub2    Subcommand 2
                """, res.out());
    }

    @Test
    public void testSubCommandHelp() {
        var res = MiniCli.runCaptured(new MixinsAndSubcommands(), new String[]{"a", "--help"});
        assertEquals("""
                Usage: mixins a [OPTIONS]
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                """, res.out());
    }

    @Test
    public void testSubCommand() {
        var res = MiniCli.runCaptured(new MixinsAndSubcommands(), new String[]{"a"});
        assertEquals("Running subcommand a\n", res.out());
    }
}