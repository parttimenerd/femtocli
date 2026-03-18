package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;

import java.util.concurrent.Callable;

/**
 * Demonstrates how to remove command classes dynamically at runtime.
 */
@Command(name = "tool", description = "Tool with optional experimental features", mixinStandardHelpOptions = true,
        subcommands = {RemoveCommandsDynamic.Status.class, RemoveCommandsDynamic.Experimental.class})
public class RemoveCommandsDynamic implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'tool --help' to inspect available commands");
    }

    @Command(name = "status", description = "Stable status command")
    public static class Status implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("status: ok");
            return 0;
        }
    }

    @Command(name = "experimental", description = "Experimental command that can be disabled")
    public static class Experimental implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("experimental feature");
            return 0;
        }
    }

    public static void main(String[] args) {
        // Remove Experimental at runtime: it is no longer routable and not listed in help.
        int exitCode = FemtoCli.builder()
                .removeCommands(Experimental.class)
                .run(new RemoveCommandsDynamic(), args);
        System.exit(exitCode);
    }
}