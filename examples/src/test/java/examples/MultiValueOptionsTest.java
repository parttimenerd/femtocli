package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.MultiValueOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiValueOptionsTest {
    @Test
    public void testMultiValueOptions() {
        var res = MiniCli.runCaptured(new MultiValueOptions(), new String[]{
                "--tags=a,b,c", "-I", "a", "-I", "b", "--tags", "d,e"
        });
        assertEquals("""
                Include Dirs: [a, b]
                Tags: [a, b, c, d, e]
                """, res.out());
    }
}