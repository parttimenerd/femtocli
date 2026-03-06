package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests demonstrating parent command access in deeply nested command chains.
 */
public class DeepParentAccessTest {

    @Test
    public void testDeepestLevelDefaults() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(), "db", "migrate");

        String out = res.out();
        assertEquals(0, res.exitCode());
        assertTrue(out.contains("Migration up"), "Expected default direction");
        assertTrue(out.contains("Host: localhost:5432"), "Expected default host/port from parent");
        assertTrue(out.contains("Config: default.conf, verbose: false"), "Expected defaults from root");
    }

    @Test
    public void testIntermediateLevelAccessesRoot() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(), "db");

        String out = res.out();
        assertEquals(0, res.exitCode());
        assertTrue(out.contains("Connecting to localhost:5432"), "Expected database connection info");
        assertTrue(out.contains("Config: default.conf"), "Expected config from root");
        assertTrue(out.contains("Verbose: false"), "Expected verbose flag from root");
    }

    @Test
    public void testDeepestLevelWithCustomDirection() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(), "db", "migrate", "--direction", "down");

        assertEquals(0, res.exitCode());
        assertTrue(res.out().contains("Migration down"), "Expected custom direction");
    }

    @Test
    public void testIntermediateLevelWithCustomPort() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(), "db", "--port", "3306");

        assertEquals(0, res.exitCode());
        String out = res.out();
        assertTrue(out.contains("Connecting to localhost:3306"), "Expected custom port");
        assertTrue(out.contains("Config: default.conf"), "Expected default root config");
    }

    @Test
    public void testDeepestLevelWithIntermediateOptions() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(),
                "db", "--host", "dev-db", "--port", "5433",
                "migrate", "--direction", "up");

        assertEquals(0, res.exitCode());
        String out = res.out();
        assertTrue(out.contains("Host: dev-db:5433"), "Should access parent's parsed host/port");
        assertTrue(out.contains("Config: default.conf"), "Should access root's default config");
    }

    @Test
    public void testRootOptionsBeforeSubcommands() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(),
                "--config", "app.conf", "--verbose",
                "db", "migrate");

        assertEquals(0, res.exitCode());
        String out = res.out();
        assertTrue(out.contains("Config: app.conf, verbose: true"), "Should parse root options");
    }

    @Test
    public void testAllLevelsWithOptions() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(),
                "--config", "custom.conf", "--verbose",
                "db", "--host", "staging-db", "--port", "3307",
                "migrate", "--direction", "down");

        assertEquals(0, res.exitCode());
        String out = res.out();
        assertTrue(out.contains("Migration down"), "Leaf direction should be parsed");
        assertTrue(out.contains("Host: staging-db:3307"), "Mid host/port should be parsed");
        assertTrue(out.contains("Config: custom.conf, verbose: true"), "Root options should be parsed");
    }

    @Test
    public void testRootRunsWhenNoSubcommand() {
        var res = FemtoCli.runCaptured(new DeepParentAccess(), "--config", "test.conf");

        assertEquals(0, res.exitCode());
        assertTrue(res.out().contains("Root command executed with config: test.conf"));
    }
}