package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuickStartTest {

    @Test
    public void testHelp() {
        var res = FemtoCli.runCaptured(new QuickStart(), "--help");
        assertEquals("""
                Usage: myapp [-hV] [COMMAND]
                My CLI application
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                Commands:
                  greet  Greet a person
                """, res.out());
    }

    @Test
    public void testGreetHelp() {
        var res = FemtoCli.runCaptured(new QuickStart(), "greet", "--help");
        assertEquals("""
                Usage: myapp greet [-hV] --name=<name> [--count=<count>]
                Greet a person
                  -c, --count=<count>    Count (default: 1)
                  -h, --help             Show this help message and exit.
                  -n, --name=<name>      Name to greet (required)
                  -V, --version          Print version information and exit.
                """, res.out());
    }

    @Test
    public void testGreet() {
        var res = FemtoCli.runCaptured(new QuickStart(), "greet", "--name=World", "--count=1");
        assertEquals("Hello, World!\n", res.out());
    }
}