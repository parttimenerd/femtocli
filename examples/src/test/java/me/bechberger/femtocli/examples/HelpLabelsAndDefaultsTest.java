package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelpLabelsAndDefaultsTest {

    @Test
    public void testHelp() {
        var res = FemtoCli.runCaptured(new HelpLabelsAndDefaults(), "--help");
        assertEquals(
            "Usage: help-labels [-hV] [--output=FILE] INPUT [LEVEL]\n"
                + "      INPUT        Input file\n"
                + "      [LEVEL]      Log level (default: info)\n"
                + "  -h, --help       Show this help message and exit.\n"
                + "      --output=FILE\n"
                + "                   Write result to FILE (default: out.txt)\n"
                + "  -V, --version    Print version information and exit.\n",
            res.out());
    }

     @Test
     public void testDefault() {
          var res = FemtoCli.runCaptured(new HelpLabelsAndDefaults(), "in.txt");
          assertEquals("""
                  Input: in.txt
                  Output: out.txt
                  Level: info
                  """, res.out());
     }
}