package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BooleanOptionConverterTest {

    public static class OnOffConverter implements TypeConverter<Boolean> {
        @Override
        public Boolean convert(String raw) {
            if (raw == null) return null;
            String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (s.equals("on") || s.equals("true") || s.equals("1")) return Boolean.TRUE;
            if (s.equals("off") || s.equals("false") || s.equals("0")) return Boolean.FALSE;
            throw new IllegalArgumentException("Invalid on/off: " + raw);
        }
    }

    @Command(name = "cmd")
    static class Cmd implements Runnable {
        @Option(names = "--turn", converter = OnOffConverter.class)
        Boolean turn;

        @Override
        public void run() {}
    }

    @Test
    void parsesBooleanWithEquals() {
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--turn=on");
        assertEquals(0, res.exitCode(), res.err());
        assertTrue(Boolean.TRUE.equals(cmd.turn));
    }

    @Test
    void parsesBooleanWithSeparateToken() {
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--turn", "off");
        assertEquals(0, res.exitCode(), res.err());
        assertTrue(Boolean.FALSE.equals(cmd.turn));
    }

    @Test
    void missingValueFails() {
        Cmd cmd = new Cmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--turn");
        assertNotEquals(0, res.exitCode());
        assertTrue(res.err().contains("Missing value") || res.err().contains("Invalid"));
    }
}