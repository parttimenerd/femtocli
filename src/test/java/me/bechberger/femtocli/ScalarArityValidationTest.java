package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression test for scalar positional parameter arity validation.
 * Scalar fields can only hold one value, so arity > 1 is invalid.
 */
class ScalarArityValidationTest {

    @Command(name = "scalararity")
    static class ScalarArityCmd implements Runnable {
        @Parameters(index = "0", arity = "2", paramLabel = "PAIR")
        String pair;

        @Override
        public void run() {
            System.out.println("pair=" + pair);
        }
    }

    /**
     * When a scalar field has arity > 1, the binding should fail with a clear error.
     * This is a configuration error, so it throws IllegalArgumentException.
     */
    @Test
    void scalarFieldWithArityGreaterThanOneThrowsError() {
        ScalarArityCmd cmd = new ScalarArityCmd();
        var ex = assertThrows(IllegalArgumentException.class,
                () -> FemtoCli.builder().runCaptured(cmd, "a", "b"),
                "Scalar field with arity > 1 should throw IllegalArgumentException");
        assertThat(ex.getMessage().toLowerCase()).contains("arity");
    }

    /**
     * After the fix, a scalar field with arity 1 (single value) should work fine.
     */
    @Test
    void scalarFieldWithArityOneWorks() {
        @Command(name = "scalar1")
        class ScalarOkCmd implements Runnable {
            @Parameters(index = "0", arity = "1", paramLabel = "VAL")
            String val;

            @Override
            public void run() {}
        }

        ScalarOkCmd cmd = new ScalarOkCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "hello");
        assertEquals(0, res.exitCode());
        assertEquals("hello", cmd.val);
    }

    /**
     * Default arity (implicitly 1) on a scalar field should work.
     */
    @Test
    void scalarFieldWithDefaultArityWorks() {
        @Command(name = "scalardefault")
        class ScalarDefaultCmd implements Runnable {
            @Parameters(index = "0", paramLabel = "VAL")
            String val;

            @Override
            public void run() {}
        }

        ScalarDefaultCmd cmd = new ScalarDefaultCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "test");
        assertEquals(0, res.exitCode());
        assertEquals("test", cmd.val);
    }
}
