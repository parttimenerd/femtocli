package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubcommandMethodTest {

    @Test
    public void testCommand() {
        var res = FemtoCli.runCaptured(new SubcommandMethod(), new String[]{"status"});
        assertEquals("""
                OK
                """, res.out());
    }
}