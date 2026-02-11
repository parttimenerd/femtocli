package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.VerifierException;
import me.bechberger.minicli.examples.CustomTypeVerifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CustomTypeVerifiersTest {

    @Test
    public void testCustomTypeVerifiers() {
        assertThrows(VerifierException.class, () -> {
            var res = MiniCli.runCaptured(new CustomTypeVerifiers(), new String[]{"--port", "2"});
            System.out.println(res);
        });
    }
}