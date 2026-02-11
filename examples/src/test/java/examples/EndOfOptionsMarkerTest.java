package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.EndOfOptionsMarker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EndOfOptionsMarkerTest {

    @Test
    public void testEndOfOptions() {
        var res = MiniCli.runCaptured(new EndOfOptionsMarker(), new String[]{
                "--name", "test",
                "--",
                "--not-an-option",
                "-also-not"
        });
        assertEquals(0, res.exitCode());
        assertEquals("""
                name=test
                args=[--not-an-option, -also-not]
                """, res.out());
    }
}