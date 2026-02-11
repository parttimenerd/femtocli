package me.bechberger.femtocli.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GlobalConfigurationTest {

    @Test
    public void testVersion() {
        assertEquals("1.2.3\n", Util.run(GlobalConfiguration.class, "--version"));
    }
}