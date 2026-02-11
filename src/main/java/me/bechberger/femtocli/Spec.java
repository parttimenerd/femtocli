package me.bechberger.femtocli;

import java.io.PrintStream;
import java.util.List;

/**
 * Provides access to runtime CLI properties for the currently executing command.
 */
public final class Spec {
    private final Object command;
    public final PrintStream out;
    public final PrintStream err;
    private final List<String> commandPath;
    private final CommandConfig commandConfig;

    Spec(Object command, PrintStream out, PrintStream err, List<String> commandPath, CommandConfig commandConfig) {
        this.command = command;
        this.out = out;
        this.err = err;
        this.commandPath = List.copyOf(commandPath);
        this.commandConfig = commandConfig.copy();
    }

    public Object command() { return command; }
    public PrintStream out() { return out; }
    public PrintStream err() { return err; }

    /** The resolved command path for this execution. */
    public List<String> commandPath() { return commandPath; }

    /** The effective CommandConfig used for this execution (defensive copy). */
    public CommandConfig commandConfig() { return commandConfig.copy(); }

    /** Print usage for the current command to the configured output stream. */
    public void usage() {
        usage(out);
    }

    /** Print usage for the current command to the provided output stream. */
    public void usage(PrintStream out) {
        // Use the contextual overload so output matches the configuring FemtoCli instance.
        FemtoCli.usage(command, commandPath, commandConfig, out);
    }
}