package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.BooleanExplicitValues;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BooleanExplicitValuesTest {
    @Test
    public void testHelp() {
        var res = MiniCli.runCaptured(new BooleanExplicitValues(), new String[]{"--help"});
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: bools [-hV] [--boxed] [--prim]
                Boolean option parsing example
                      --boxed      Boxed boolean (Boolean)
                  -h, --help       Show this help message and exit.
                      --prim       Primitive boolean (boolean)
                  -V, --version    Print version information and exit.
                """, res.out());
    }

    @Test
    public void testExplicitValues() {
        var res = MiniCli.runCaptured(new BooleanExplicitValues(), new String[]{"--boxed=false", "--prim", "false"});
        assertEquals(0, res.exitCode());
        assertEquals("""
                boxed=false
                prim=false
                """, res.out());
    }
}