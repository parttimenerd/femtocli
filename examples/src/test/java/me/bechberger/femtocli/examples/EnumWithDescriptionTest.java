package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumWithDescriptionTest {

    @Test
    void testEnumWithDescription() {
        var res = FemtoCli.runCaptured(new EnumWithDescription(), "--help");
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: enumwithdesc [-hV] [--mode=<mode>] [--verbose-mode=<verboseMode>]
                  -h, --help                      Show this help message and exit.
                      --mode=<mode>               Mode: fast (optimized for speed), safe
                                                  (optimized for safety), default: safe
                  -V, --version                   Print version information and exit.
                      --verbose-mode=<verboseMode>
                                                  Mode (verbose listing):
                                                  fast (optimized for speed)
                                                  safe (optimized for safety), default: safe
                """, res.out());
    }

}