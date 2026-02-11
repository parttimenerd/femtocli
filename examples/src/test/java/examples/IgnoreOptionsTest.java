package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.IgnoreOptionsExample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IgnoreOptionsTest {

    @Test
    public void testHelp() {
        var res = Util.run(IgnoreOptionsExample.class, "--help");
        assertEquals("""
                Usage: ignore-options [OPTIONS]
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                """, res);

    }
}