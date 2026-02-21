package me.bechberger.femtocli;

import java.io.PrintStream;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Contract between the generated per-command parser and the runtime.
 * <p>
 * An implementation is generated for every {@code @Command}-annotated class
 * by the femtocli annotation processor.
 */
public interface CommandParser<T> {

    /** Parse tokens into the command's fields. */
    void parse(T cmd, Deque<String> tokens, Map<Class<?>, TypeConverter<?>> converters,
               CommandConfig config) throws Exception;

    /** Initialise mixins, inject {@link Spec}, etc. — called once before parsing. */
    default void init(T cmd, PrintStream out, PrintStream err,
                      List<String> commandPath, CommandConfig config) {}

    /** The command name (from {@code @Command(name=...)} or class simple-name). */
    default String name() { return ""; }

    /** The version string (from annotation or config), or empty. */
    default String version() { return ""; }

    /**
     * Render help/usage text for this command to the given stream.
     * Generated at compile time — no model object needed at runtime.
     */
    default void renderHelp(PrintStream out, String displayPath,
                            CommandConfig config, boolean agentMode) {}

    /**
     * Check whether {@code token} names a known subcommand (class-based or method-based).
     * Returns the canonical subcommand name, or {@code null} if not a subcommand.
     */
    default String resolveSubcommand(String token) { return null; }

    /** Create a new instance of the named subcommand (class-based). */
    default Object createSubcommand(String name) { return null; }

    /** Return the parser for the named subcommand, or {@code null}. */
    default CommandParser<?> subcommandParser(String name) { return null; }

    /**
     * Invoke a method-subcommand (a {@code @Command}-annotated method) by name.
     * Returns the exit code.
     */
    default int invokeMethodSubcommand(T cmd, String name) throws Exception {
        throw new IllegalStateException("No method subcommand: " + name);
    }

    /**
     * Normalise bare option tokens for agent-mode.
     * E.g. {@code "port=8080"} → {@code "--port=8080"}.
     */
    default void normalizeAgentTokens(Object cmdForErrors, Deque<String> tokens) throws Exception {}

    /**
     * Whether this command supports agent mode (bare option normalisation, comma-separated args).
     * Returns true only when the command is annotated with {@code @Command(agentMode = true)}.
     */
    default boolean supportsAgentMode() { return false; }

    /**
     * Invoke the command ({@code Callable.call()} or {@code Runnable.run()}).
     * Returns the exit code.
     */
    default int invoke(T cmd) throws Exception {
        if (cmd instanceof java.util.concurrent.Callable<?> callable) {
            Object result = callable.call();
            return result instanceof Integer i ? i : 0;
        }
        if (cmd instanceof Runnable runnable) {
            runnable.run();
            return 0;
        }
        throw new IllegalStateException("Command must implement Runnable or Callable<Integer>");
    }
}
