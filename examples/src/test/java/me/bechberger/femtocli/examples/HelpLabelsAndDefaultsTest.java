package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelpLabelsAndDefaultsTest {

    @Test
    public void testHelp() {
        var res = FemtoCli.runCaptured(new HelpLabelsAndDefaults(), new String[]{"--help"});
        assertEquals("""
                Usage: help-labels [-hV] [--output=FILE] INPUT [LEVEL]
                      INPUT        Input file
                      [LEVEL]      Log level (default: ${DEFAULT-VALUE})
                  -h, --help       Show this help message and exit.
                      --output=FILE
                                   Write result to FILE (default: out.txt)
                  -V, --version    Print version information and exit.
                """, res.out());
    }

     @Test
     public void testDefault() {
          var res = FemtoCli.runCaptured(new HelpLabelsAndDefaults(), new String[]{"in.txt"});
          assertEquals("""
                  Input: in.txt
                  Output: out.txt
                  Level: info
                  """, res.out());
     }
}