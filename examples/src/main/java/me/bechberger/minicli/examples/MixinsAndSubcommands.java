package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;

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
        }
    }

    @Command(name = "b")
    static class B implements Runnable {
        @Mixin
        Common common;

        public void run() {
        }
    }

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.run(new MixinsAndSubcommands(), args);
    }
}