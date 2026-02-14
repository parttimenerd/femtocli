package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EndOfOptionsMarkerTest {

    @Test
    public void testEndOfOptions() {
        var res = FemtoCli.runCaptured(new EndOfOptionsMarker(), "--name", "test",
                "--",
                "--not-an-option",
                "-also-not");
        assertEquals(0, res.exitCode());
        assertEquals("""
                name=test
                args=[--not-an-option, -also-not]
                """, res.out());
    }
}