package me.bechberger.femtocli.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IgnoreOptionsTest {

    @Test
    public void testHelp() {
        var res = Util.run(IgnoreOptionsExample.class, "--help");
        assertEquals("""
                Usage: cmd [-hV] [--b=<b>]
                      --b=<b>      Inherited option B
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                """, res);

    }
}