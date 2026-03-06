package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.Spec;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

/**
 * Example showcasing parent command access in subcommands.
 * <p>
 * Demonstrates how a subcommand can access its parent command's options and state
 * using the {@link Spec#getParent()} and {@link Spec#getParent(Class)} methods.
 * <p>
 * Usage:
 * <pre>
 * java -cp ... me.bechberger.femtocli.examples.ParentCommandAccess child
 * </pre>
 * <p>
 * Parent options can be set on the root command when needed.
 */
@Command(name = "app", description = "Application with parent command access", subcommands = {ParentCommandAccess.Child.class})
public class ParentCommandAccess implements Runnable {
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @Option(names = {"-d", "--debug"}, description = "Enable debug mode")
    boolean debug;

    public void run() {
        System.out.println("Root command executed");
    }

    /**
     * Child command that accesses parent command options.
     */
    @Command(name = "child", description = "Child command that accesses parent options")
    public static class Child implements Runnable {
        Spec spec;

        @Option(names = {"-n", "--name"}, description = "Child name", defaultValue = "child")
        String name;

        @Override
        public void run() {
            System.out.println("Child '" + name + "' executed");

            // Access parent command using typed getParent()
            ParentCommandAccess rootCmd = spec.getParent(ParentCommandAccess.class);
            if (rootCmd != null) {
                System.out.println("Parent verbose: " + rootCmd.verbose);
                System.out.println("Parent debug: " + rootCmd.debug);
                System.out.println("Parent command verified via typed access");
            }
        }
    }

    public static void main(String[] args) {
        FemtoCli.run(new ParentCommandAccess(), args);
    }
}