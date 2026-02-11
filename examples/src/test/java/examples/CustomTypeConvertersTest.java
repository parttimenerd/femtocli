package examples;

import me.bechberger.minicli.examples.CustomTypeConverters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomTypeConvertersTest {

    @Test
    public void testCustomTypeConverters() {
        var res = Util.run(CustomTypeConverters.class,
                "--name=max",
                "--turn", "on",
                "--timeout=PT10S"
        );
        assertEquals("""
                Name: MAX
                Timeout: PT10S
                """, res);
    }
}