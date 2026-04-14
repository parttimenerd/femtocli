package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for range parsing validation.
 * Regression tests for Bug #6: Range Parsing Validation.
 * 
 * Context: The arity and index range syntax should validate that:
 * - Start value is not negative (ranges must be >= 0)
 * - End value is not less than start value
 * - Invalid number formats produce clear error messages
 */
public class RangeParsingValidationTest {

    @Command(name = "testrng")
    static class RangeTestCmd implements Callable<Integer> {
        @Option(names = "--opt", arity = "INVALID")
        String opt;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "params")
    static class ParametersCmd implements Callable<Integer> {
        @Parameters(arity = "INVALID")
        String param;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void validRangeSingleValue() {
        // Single value range like "5" (arity 5..5)
        @Command(name = "cmd")
        class Cmd implements Runnable {
            @Option(names = "--opt", arity = "5")
            String[] opt;
            @Override
            public void run() {}
        }
        
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--opt=a", "--opt=b", "--opt=c", "--opt=d", "--opt=e");
        assertEquals(0, res.exitCode());
        assertEquals(5, cmd.opt.length);
    }

    @Test
    void validRangeWithStart() {
        // Range with start and end like "0..5"
        @Command(name = "cmd")
        class Cmd implements Runnable {
            @Option(names = "--opt", arity = "0..5")
            String[] opt;
            @Override
            public void run() {}
        }
        
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--opt=a", "--opt=b");
        assertEquals(0, res.exitCode());
    }

    @Test
    void validRangeVarargs() {
        // Varargs range like "0..*" (zero or more)
        @Command(name = "cmd")
        class Cmd implements Runnable {
            @Option(names = "--opt", arity = "0..*")
            String[] opt;
            @Override
            public void run() {}
        }
        
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd);
        assertEquals(0, res.exitCode());
    }

    @Test
    void negativeStartIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> FemtoCli.parseRange("-5..10"));
        assertEquals("Invalid range format: '-5..10' (start cannot be negative)", ex.getMessage());
    }

    @Test
    void endLessThanStartIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> FemtoCli.parseRange("10..5"));
        assertEquals("Invalid range format: '10..5' (end must be >= start)", ex.getMessage());
    }

    @Test
    void invalidNumberFormatProducesGoodError() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> FemtoCli.parseRange("abc..5"));
        assertEquals("Invalid range format: 'abc..5' (expected integers)", ex.getMessage());
    }

    @Test
    void emptyRangeMarker() {
        // Empty/unspecified range returns marker
        var result = FemtoCli.parseRange("");
        assertEquals(-2, result[0]);
        assertEquals(-2, result[1]);
    }

    @Test
    void validZeroRange() {
        // Zero is valid (0 elements required)
        var result = FemtoCli.parseRange("0..5");
        assertEquals(0, result[0]);
        assertEquals(5, result[1]);
    }

    @Test
    void validOneRange() {
        // One is valid (1 element required)
        var result = FemtoCli.parseRange("1..5");
        assertEquals(1, result[0]);
        assertEquals(5, result[1]);
    }

    @Test
    void varargsSyntaxWorks() {
        // Varargs syntax (end is -1 for unbounded)
        var result = FemtoCli.parseRange("1..*");
        assertEquals(1, result[0]);
        assertEquals(-1, result[1]);
    }

    @Test
    void zeroVarargsSyntaxWorks() {
        // Zero or more varargs
        var result = FemtoCli.parseRange("0..*");
        assertEquals(0, result[0]);
        assertEquals(-1, result[1]);
    }

    @Test
    void singleValueAsRange() {
        // Single value like "5" parses as 5..5
        var result = FemtoCli.parseRange("5");
        assertEquals(5, result[0]);
        assertEquals(5, result[1]);
    }

    @Test
    void zeroSingleValue() {
        // Zero as single value
        var result = FemtoCli.parseRange("0");
        assertEquals(0, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    void largeValidRange() {
        // Large but valid range
        var result = FemtoCli.parseRange("0..100");
        assertEquals(0, result[0]);
        assertEquals(100, result[1]);
    }
}
