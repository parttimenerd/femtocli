package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Minimal reproduction for FemtoCli boolean option parsing.
 *
 * <p>This is designed to be copy/pasted into the FemtoCli project as a regression test.
 */
class FemtoCliBooleanParsingReproTest {

    @Command(name = "repro", description = "repro")
    static class Cmd implements Runnable {

        @Option(names = "--boxed")
        Boolean boxed;

        @Option(names = "--prim")
        boolean prim;

        @Override
        public void run() {
            // no-op
        }
    }

    @Test
    void parsesBoxedBooleanWithExplicitValue() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int exit = FemtoCli.run(new Cmd(), new PrintStream(out), new PrintStream(err),
                "--boxed", "false");

        assertEquals(0, exit, "FemtoCli should accept an explicit boolean value for boxed Boolean");
    }

    @Test
    void parsesPrimitiveBooleanAsFlag() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int exit = FemtoCli.run(new Cmd(), new PrintStream(out), new PrintStream(err),
                "--prim");

        assertEquals(0, exit, "FemtoCli should accept primitive boolean flags without a value");
    }

    @Test
    void parsesPrimitiveBooleanWithExplicitValue() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int exit = FemtoCli.run(new Cmd(), new PrintStream(out), new PrintStream(err),
                "--prim", "false");

        assertEquals(0, exit, "FemtoCli should accept an explicit boolean value for primitive boolean too");
    }
}