package me.bechberger.femtocli.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParseOnlyTest {

    @Test
    public void testParseRoot() {
        Object parsed = ParseOnly.parse("--profile=prod", "--verbose");

        ParseOnly root = assertInstanceOf(ParseOnly.class, parsed);
        assertEquals("prod", root.profile);
        assertTrue(root.verbose);
    }

    @Test
    public void testParseSubcommand() {
        Object parsed = ParseOnly.parse("server", "--port", "9090");

        ParseOnly.Server server = assertInstanceOf(ParseOnly.Server.class, parsed);
        assertEquals(9090, server.port);
    }

    @Test
    public void testParseDefaults() {
        ParseOnly root = assertInstanceOf(ParseOnly.class, ParseOnly.parse());
        assertEquals("dev", root.profile);
        assertFalse(root.verbose);
    }
}