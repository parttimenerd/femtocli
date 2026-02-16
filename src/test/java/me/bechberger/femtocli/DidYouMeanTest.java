package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the "did you mean" suggestion feature when invalid options are provided.
 */
class DidYouMeanTest {

    @Command(name = "test")
    static class TestCmd implements Runnable {
        @Option(names = "--verbose")
        boolean verbose;

        @Override
        public void run() {
        }
    }

    @Test
    void testDidYouMeanSuggestion() {
        TestCmd cmd = new TestCmd();
        RunResult res = FemtoCli.builder()
                .commandConfig(opt -> opt.similarOptionsSuggestionTemplate = "Did you mean: %s?")
                .runCaptured(new TestCmd(), "--verbse");
        assertEquals(2, res.exitCode());
        assertEquals("""
                Error: Unknown option: --verbse
                Did you mean: %s?
                
                Usage: test [-hV] [--verbose]
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                      --verbose         
                """, res.err());
    }
}