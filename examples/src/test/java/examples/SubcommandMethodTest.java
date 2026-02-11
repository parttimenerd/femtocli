package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.SubcommandMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubcommandMethodTest {

    @Test
    public void testCommand() {
        var res = MiniCli.runCaptured(new SubcommandMethod(), new String[]{"status"});
        assertEquals("""
                OK
                """, res.out());
    }
}