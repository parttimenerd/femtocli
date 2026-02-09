package me.bechberger.minicli.examples;

import me.bechberger.minicli.RunResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

public class ToolTest {

    String run(Class<?> cls, String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        try {
            cls.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        System.setOut(originalOut);
        System.setErr(originalErr);
        return outContent.toString() + errContent.toString();
    }

    @Test
    public void testCustomHeaderAndSynopsis() {
        assertEquals("""
                My Tool
                Copyright 2026
                Usage: mytool [OPTIONS] <file>
                Process files
                      --flag
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                
                Examples:
                  mytool --flag
                """, run(CustomHeaderAndSynopsis.class, "--help"));
    }

}