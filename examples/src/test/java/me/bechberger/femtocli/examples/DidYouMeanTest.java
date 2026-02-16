package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DidYouMeanTest {

    @Test
    public void testDidYouMean() {
        var res = FemtoCli.runCaptured(new DidYouMean(), "--input_file", "test");
        assertEquals(2, res.exitCode(), res.err());
        assertEquals("""
                Error: Unknown option: --input_file
                
                  tip: a similar argument exists: '--input-file'
                
                Usage: didyoumean [-hV] [--input-file=<inputFile>] [--output-file=<outputFile>]
                                  [--verbose]
                Example showing helpful error suggestions
                  -h, --help                    Show this help message and exit.
                      --input-file=<inputFile>  Input file to process
                      --output-file=<outputFile>
                                                Output file destination
                  -V, --version                 Print version information and exit.
                      --verbose                 Enable verbose output
                """, res.err());
    }

    @Test
    public void testWidlyOff() {
        var res = FemtoCli.runCaptured(new DidYouMean(), "--no-this-is-not-an-option", "test");
        assertEquals(2, res.exitCode(), res.err());
        assertEquals("""
                Error: Unknown option: --no-this-is-not-an-option
                
                Usage: didyoumean [-hV] [--input-file=<inputFile>] [--output-file=<outputFile>]
                                  [--verbose]
                Example showing helpful error suggestions
                  -h, --help                    Show this help message and exit.
                      --input-file=<inputFile>  Input file to process
                      --output-file=<outputFile>
                                                Output file destination
                  -V, --version                 Print version information and exit.
                      --verbose                 Enable verbose output
                """, res.err());
    }
}