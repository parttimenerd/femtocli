package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.SpecInjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpecInjectionTest {

    @Test
    public void testSpecInjection() {
        var res = MiniCli.runCaptured(new SpecInjection(), new String[]{"--interval", "10ms"});
        assertEquals("""
                interval = 10
                Usage: inspect [-hV] [--interval=<interval>]
                Example that uses Spec
                  -h, --help                   Show this help message and exit.
                  -i, --interval=<interval>    Sampling interval (default: 10ms)
                  -V, --version                Print version information and exit.
                """, res.out());
    }
}