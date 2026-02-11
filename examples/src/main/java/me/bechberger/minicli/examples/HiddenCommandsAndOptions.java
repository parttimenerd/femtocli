package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

/**
 * Demonstrates how to hide commands and options from help output.
 */
@Command(
        name = "hidden",
        description = "Hide commands and options in help",
        mixinStandardHelpOptions = true,
        subcommands = {
                HiddenCommandsAndOptions.Status.class,
                HiddenCommandsAndOptions.Internal.class
        }
)
public class HiddenCommandsAndOptions implements Runnable {

    @Option(names = "--verbose", description = "Verbose output")
    boolean verbose;

    @Option(names = "--secret", hidden = true, description = "A hidden option")
    String secret;

    @Override
    public void run() {
        System.out.println("verbose=" + verbose);
        System.out.println("secret=" + secret);
    }

    @Command(name = "status", description = "Show status", mixinStandardHelpOptions = true)
    public static class Status implements Runnable {
        @Override
        public void run() {
            System.out.println("OK");
        }
    }

    @Command(name = "internal", hidden = true, description = "Internal command")
    public static class Internal implements Runnable {
        @Override
        public void run() {
            System.out.println("INTERNAL");
        }
    }

    public static void main(String[] args) {
        System.exit(MiniCli.run(new HiddenCommandsAndOptions(), args));
    }
}