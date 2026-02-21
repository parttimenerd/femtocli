package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Combined tests for enum-related features:
 * - Enums with descriptions (showEnumDescriptions=true)
 * - Enums without descriptions (default behavior)
 * - Error handling when showEnumDescriptions=true but getDescription() is missing
 */
public class EnumTest {

    // ========== Example Classes ==========

    @Command(name = "enumwithdesc", description = "Example of enum with descriptions")
    static class EnumWithDescriptionExample implements Runnable {

        enum Mode {
            FAST("fast", "optimized for speed"),
            SAFE("safe", "optimized for safety");

            private final String name, description;

            Mode(String name, String description) {
                this.name = name;
                this.description = description;
            }

            public String getDescription() { return description; }

            @Override
            public String toString() { return name; }
        }

        @Option(names = "--mode", defaultValue = "safe",
                description = "Mode: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE}",
                showEnumDescriptions = true)
        Mode mode;

        public void run() {
            System.out.println("Mode: " + mode + " (" + mode.getDescription() + ")");
        }
    }

    @Command(name = "enumnodesc")
    static class EnumWithoutDescriptionExample implements Runnable {

        public enum Mode {
            FAST,
            SAFE
        }

        @Option(names = "--mode",
                defaultValue = "safe",
                description = "Mode (${COMPLETION-CANDIDATES}), default: ${DEFAULT-VALUE}")
        Mode mode;

        public void run() {
            System.out.println("Mode: " + mode);
        }
    }

    @Command(name = "enumfail")
    static class EnumMissingDescriptionExample implements Runnable {

        public enum Status {
            ACTIVE,
            INACTIVE
        }

        @Option(names = "--status",
                defaultValue = "active",
                description = "Status (${COMPLETION-CANDIDATES}), default: ${DEFAULT-VALUE}",
                showEnumDescriptions = true)  // This will fail - enum doesn't have getDescription()
        Status status;

        public void run() {
            System.out.println("Status: " + status);
        }
    }

    // ========== EnumWithDescriptionExample Tests ==========

    @Test
    public void testEnumWithDescription_Help() {
        var res = FemtoCli.runCaptured(new EnumWithDescriptionExample(), "--help");
        assertEquals("""
                Usage: enumwithdesc [-hV] [--mode=<mode>]
                Example of enum with descriptions
                  -h, --help       Show this help message and exit.
                      --mode=<mode>
                                   Mode: fast (optimized for speed), safe (optimized for
                                   safety), default: safe
                  -V, --version    Print version information and exit.
                """, res.out());
    }

    @Test
    public void testEnumWithDescription_Default() {
        var res = FemtoCli.runCaptured(new EnumWithDescriptionExample());
        assertEquals("Mode: safe (optimized for safety)\n", res.out());
    }

    @Test
    public void testEnumWithDescription_Fast() {
        var res = FemtoCli.runCaptured(new EnumWithDescriptionExample(), "--mode", "fast");
        assertEquals("Mode: fast (optimized for speed)\n", res.out());
    }

    // ========== EnumWithoutDescriptionExample Tests ==========

    @Test
    public void testEnumWithoutDescription_Help() {
        var res = FemtoCli.runCaptured(new EnumWithoutDescriptionExample(), "--help");
        // Should show simple enum names without descriptions
        assertTrue(res.out().contains("Mode (FAST, SAFE)"));
        assertTrue(res.out().contains("default: safe"));
    }

    @Test
    public void testEnumWithoutDescription_Default() {
        var res = FemtoCli.runCaptured(new EnumWithoutDescriptionExample());
        assertEquals("Mode: SAFE\n", res.out());
    }

    @Test
    public void testEnumWithoutDescription_Fast() {
        var res = FemtoCli.runCaptured(new EnumWithoutDescriptionExample(), "--mode", "fast");
        assertEquals("Mode: FAST\n", res.out());
    }

    // ========== EnumMissingDescriptionExample Tests ==========

    @Test
    public void testEnumMissingDescription_HelpShouldFail() {
        // Should throw IllegalStateException because showEnumDescriptions=true
        // but the enum doesn't have getDescription() method
        var exception = assertThrows(IllegalStateException.class, () -> {
            FemtoCli.runCaptured(new EnumMissingDescriptionExample(), "--help");
        });

        assertTrue(exception.getMessage().contains("showEnumDescriptions=true"));
        assertTrue(exception.getMessage().contains("missing getDescription() method"));
    }
}