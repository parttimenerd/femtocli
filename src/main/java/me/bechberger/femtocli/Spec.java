package me.bechberger.femtocli;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * Provides access to runtime CLI properties for the currently executing command.
 *
 * <p>To use, declare a field of type {@code Spec} on your command class or
 * {@code @Mixin} class. FemtoCli will automatically inject the instance before
 * the command executes.
 */
public final class Spec {
    private final Object command;
    public final PrintStream out;
    public final PrintStream err;
    private final List<String> commandPath;
    private final CommandConfig commandConfig;
    private final List<Object> commandChain;
    private final boolean agentMode;


    Spec(Object command, PrintStream out, PrintStream err, List<String> commandPath, CommandConfig commandConfig, List<Object> commandChain, boolean agentMode) {
        this.command = command;
        this.out = out;
        this.err = err;
        this.commandPath = List.copyOf(commandPath);
        this.commandConfig = commandConfig.copy();
        this.commandChain = List.copyOf(commandChain);
        this.agentMode = agentMode;
    }

    public Object command() { return command; }
    public PrintStream out() { return out; }
    public PrintStream err() { return err; }

    /**
     * Returns the direct parent command object if this is a subcommand, or null if this is the root command.
     */
    public Object getParent() {
        return commandChain.isEmpty() ? null : commandChain.get(commandChain.size() - 1);
    }

    /**
     * Returns the first parent command in the chain that is an instance of the specified type,
     * or null if no such parent exists or this is the root command.
     * <p>
     * For deeper command chains (root -> child1 -> child2 -> ...), this method searches
     * backwards through the chain to find the first matching type.
     * <p>
     * Example: If the chain is Root -> Child -> GrandChild, calling getParent(Root.class)
     * from GrandChild will return the Root instance.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParent(Class<T> type) {
        if (type == null) return null;
        for (int i = commandChain.size() - 1; i >= 0; i--) {
            if (type.isInstance(commandChain.get(i))) {
                return (T) commandChain.get(i);
            }
        }
        return null;
    }

    public PrintWriter outWriter() { return new PrintWriter(out, true); }
    public PrintWriter errWriter() { return new PrintWriter(err, true); }

    /** The resolved command path for this execution. */
    public List<String> commandPath() { return commandPath; }

    /** The effective CommandConfig used for this execution (defensive copy). */
    public CommandConfig commandConfig() { return commandConfig; }

    /** Print usage for the current command to the configured output stream. */
    public void usage() {
        usage(out);
    }

    /** Print usage for the current command to the provided output stream. */
    public void usage(PrintStream out) {
        // Use the contextual overload so output matches the configuring FemtoCli instance.
        FemtoCli.usage(command, commandPath, commandConfig, out, agentMode);
    }
}