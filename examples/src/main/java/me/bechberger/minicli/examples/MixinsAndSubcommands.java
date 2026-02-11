package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;

/**
 * Shows how to use mixins to share options between subcommands. Run with "a -v" or "b -v" to see the effect.
 */
@Command(name = "mixins", subcommands = {MixinsAndSubcommands.A.class, MixinsAndSubcommands.B.class})
public class MixinsAndSubcommands implements Runnable {
    /** Example how to use mixins to share options between commands */
    static class Common {
        @Option(names = {"-v", "--verbose"})
        boolean verbose;
    }

    @Command(name = "a")
    static class A implements Runnable {
        @Mixin
        Common common;

        public void run() {
            System.out.println("Verbose: " + common.verbose);
        }
    }

    @Command(name = "b")
    static class B implements Runnable {
        @Mixin
        Common common;

        public void run() {
            System.out.println("Verbose: " + common.verbose);
        }
    }

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.run(new MixinsAndSubcommands(), args);
    }
}