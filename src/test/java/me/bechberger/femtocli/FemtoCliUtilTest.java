package me.bechberger.femtocli;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FemtoCliUtilTest {

    @Test
    public void testParseDuration() {
        assertEquals(Duration.ofNanos(100), FemtoCli.parseDuration("100ns"));
        assertEquals(Duration.ofMillis(500), FemtoCli.parseDuration("500ms"));
        assertEquals(Duration.ofSeconds(10), FemtoCli.parseDuration("10s"));
        assertEquals(Duration.ofMinutes(5), FemtoCli.parseDuration("5m"));
        assertEquals(Duration.ofHours(2), FemtoCli.parseDuration("2h"));
        assertEquals(Duration.ofDays(3), FemtoCli.parseDuration("3d"));
        assertEquals(Duration.ofMillis(1500), FemtoCli.parseDuration("1.5s"));
        assertEquals(Duration.ofNanos(1), FemtoCli.parseDuration("1ns"));
        assertEquals(Duration.ofNanos(1000), FemtoCli.parseDuration("1us"));
        assertEquals(Duration.ofNanos(1000), FemtoCli.parseDuration("1µs"));
    }

    @Test
    public void testParseDurationWithWhitespace() {
        assertEquals(Duration.ofSeconds(10), FemtoCli.parseDuration(" 10s "));
        assertEquals(Duration.ofSeconds(10), FemtoCli.parseDuration("10 s"));
    }

    @Test
    public void testParseDurationWithNegativeValues() {
        assertEquals(Duration.ofSeconds(-10), FemtoCli.parseDuration("-10s"));
    }

    @Test
    public void testParseDurationInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseDuration(""));
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseDuration("10"), "Missing unit in duration: 10");
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseDuration("s"), "Empty number in duration: s");
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseDuration("10xs"), "Invalid duration unit: xs");
    }

    @Test
    public void testParseDurationUppercaseUnits() {
        assertEquals(Duration.ofSeconds(10), FemtoCli.parseDuration("10S"));
        assertEquals(Duration.ofMinutes(5), FemtoCli.parseDuration("5M"));
        assertEquals(Duration.ofHours(2), FemtoCli.parseDuration("2H"));
        assertEquals(Duration.ofDays(3), FemtoCli.parseDuration("3D"));
        assertEquals(Duration.ofMillis(500), FemtoCli.parseDuration("500MS"));
        assertEquals(Duration.ofNanos(100), FemtoCli.parseDuration("100NS"));
    }

    @Test
    public void testParseRange() {
        assertArrayEquals(new int[]{-2, -2}, FemtoCli.parseRange(""));
        assertArrayEquals(new int[]{-2, -2}, FemtoCli.parseRange(null));
        assertArrayEquals(new int[]{0, 0}, FemtoCli.parseRange("0"));
        assertArrayEquals(new int[]{3, 3}, FemtoCli.parseRange("3"));
        assertArrayEquals(new int[]{0, 1}, FemtoCli.parseRange("0..1"));
        assertArrayEquals(new int[]{0, -1}, FemtoCli.parseRange("0..*"));
        assertArrayEquals(new int[]{2, 5}, FemtoCli.parseRange("2..5"));
    }

    @Test
    public void testParseRangeTrailingDots() {
        // "5.." should be treated as open-ended (same as "5..*")
        assertArrayEquals(new int[]{5, -1}, FemtoCli.parseRange("5.."));
    }

    @Test
    public void testParseRangeInvalidFormats() {
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseRange("..5"));
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseRange("1..2..3"));
    }
}
