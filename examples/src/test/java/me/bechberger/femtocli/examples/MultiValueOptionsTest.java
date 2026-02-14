package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiValueOptionsTest {
    @Test
    public void testMultiValueOptions() {
        var res = FemtoCli.runCaptured(new MultiValueOptions(), "--tags=a,b,c", "-I", "a", "-I", "b", "--tags", "d,e");
        assertEquals("""
                Include Dirs: [a, b]
                Tags: [a, b, c, d, e]
                """, res.out());
    }
}