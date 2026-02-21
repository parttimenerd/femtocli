package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Description;
import me.bechberger.femtocli.annotations.Option;

@Command(name = "enumwithdesc")
public class EnumWithDescription implements Runnable {

    enum Mode {
        @Description("optimized for speed")
        FAST("fast", "optimized for speed"),
        @Description("optimized for safety")
        SAFE("safe", "optimized for safety");

        private final String name;
        private final String description;

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

    @Option(names = "--verbose-mode", defaultValue = "safe",
            description = "Mode (verbose listing):\n${COMPLETION-CANDIDATES:\\n}, default: ${DEFAULT-VALUE}",
            showEnumDescriptions = true)
    Mode verboseMode;

    public void run() {
        System.out.println("Mode: " + mode + " (" + mode.getDescription() + ")");
        System.out.println("Verbose Mode: " + verboseMode + " (" + verboseMode.getDescription() + ")");
    }

    public static void main(String[] args) {
        FemtoCli.run(new EnumWithDescription(), args);
    }
}