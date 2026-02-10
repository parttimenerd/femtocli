package me.bechberger.minicli;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Minimal reproduction for MiniCli boolean option parsing.
 *
 * <p>This is designed to be copy/pasted into the MiniCli project as a regression test.
 */
class MiniCliBooleanParsingReproTest {

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

        int exit = MiniCli.run(new Cmd(), new PrintStream(out), new PrintStream(err),
                new String[]{"--boxed", "false"});

        assertEquals(0, exit, "MiniCli should accept an explicit boolean value for boxed Boolean");
    }

    @Test
    void parsesPrimitiveBooleanAsFlag() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int exit = MiniCli.run(new Cmd(), new PrintStream(out), new PrintStream(err),
                new String[]{"--prim"});

        assertEquals(0, exit, "MiniCli should accept primitive boolean flags without a value");
    }

    @Test
    void parsesPrimitiveBooleanWithExplicitValue() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int exit = MiniCli.run(new Cmd(), new PrintStream(out), new PrintStream(err),
                new String[]{"--prim", "false"});

        assertEquals(0, exit, "MiniCli should accept an explicit boolean value for primitive boolean too");
    }
}