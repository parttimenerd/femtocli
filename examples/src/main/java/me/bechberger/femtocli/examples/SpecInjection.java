package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.Spec;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

import java.time.Duration;

/**
 * Example showcasing injection of the {@link Spec} object.
 * <p>
 * The Spec object contains the configured input and output streams,
 * as well as a method to print usage help with the same formatting as the current FemtoCli run.
 */
@Command(name = "inspect", description = "Example that uses Spec", mixinStandardHelpOptions = true)
public class SpecInjection implements Runnable {
    Spec spec; // injected

    @Option(names = {"-i", "--interval"},
            defaultValue = "10ms",
            description = "Sampling interval (default: ${DEFAULT-VALUE})")
    Duration interval;

    @Override
    public void run() {
        // Use the configured streams
        spec.out.println("interval = " + interval.toMillis());
        // Print usage with the same formatting as the current FemtoCli run
        spec.usage();
    }

    public static void main(String[] args) {
        FemtoCli.run(new SpecInjection(), args);
    }
}