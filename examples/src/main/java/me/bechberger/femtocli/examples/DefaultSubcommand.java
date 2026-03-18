package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import java.util.concurrent.Callable;

/**
 * Example showing how to use {@code defaultSubcommand} to route unrecognised
 * positional arguments (like a PID) to a specific subcommand automatically.
 *
 * <p>With this setup:
 * <ul>
 *   <li>{@code app 1234}         → behaves like {@code app status 1234}</li>
 *   <li>{@code app status 1234}  → explicit, works as usual</li>
 *   <li>{@code app list}         → routes to the list subcommand</li>
 * </ul>
 */
@Command(name = "app",
        description = "Example app with a default subcommand",
        subcommands = {DefaultSubcommand.Status.class, DefaultSubcommand.ListCmd.class},
        defaultSubcommand = DefaultSubcommand.Status.class,
        mixinStandardHelpOptions = true)
public class DefaultSubcommand implements Runnable {

    @Command(name = "status", description = "Show status for a target")
    static class Status implements Callable<Integer> {
        @Parameters(description = "Target PID or name")
        String target;

        @Option(names = {"-v", "--verbose"}, description = "Verbose output")
        boolean verbose;

        @Override
        public Integer call() {
            System.out.println("Status of " + target + (verbose ? " (verbose)" : ""));
            return 0;
        }
    }

    @Command(name = "list", description = "List all targets")
    static class ListCmd implements Runnable {
        @Override
        public void run() {
            System.out.println("Listing all targets...");
        }
    }

    @Override
    public void run() {
        System.out.println("Usage: app <command> [options]");
    }

    public static void main(String[] args) {
        FemtoCli.run(new DefaultSubcommand(), args);
    }
}