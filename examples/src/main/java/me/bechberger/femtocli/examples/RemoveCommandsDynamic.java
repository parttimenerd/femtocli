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
        FemtoCli.Builder builder = FemtoCli.builder();
        try {
            // Keep this example buildable against older femtocli artifacts.
            builder.getClass()
                    .getMethod("removeCommands", Class[].class)
                    .invoke(builder, (Object) new Class<?>[]{Experimental.class});
        } catch (ReflectiveOperationException ignored) {
            // Older versions do not support dynamic command removal.
        }
        int exitCode = builder.run(new RemoveCommandsDynamic(), args);
        System.exit(exitCode);
    }
}