package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.CustomTypeVerifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomTypeVerifiersTest {

    @Test
    public void testCustomTypeVerifiers() {
        var res = MiniCli.runCaptured(new CustomTypeVerifiers(), new String[]{"--port", "0"});
        assertEquals(2, res.exitCode(), res.err());
        assertTrue(res.err().contains("port out of range"), res.err());
    }
}