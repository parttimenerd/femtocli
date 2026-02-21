package me.bechberger.femtocli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * CLI entry point. Delegates all parsing to generated {@link CommandParser} implementations
 * produced by the annotation processor at compile time.
 * <p>
 * No reflection is used for option / parameter discovery; the only reflective call
 * is {@link FemtoCliRuntime#loadParser(Class)} which locates the generated parser by name.
 */
public final class FemtoCli {

    /** Builder for configuring FemtoCli with custom type handlers. */
    public static class Builder {
        private final Map<Class<?>, TypeConverter<?>> converters = new HashMap<>();
        private CommandConfig commandConfig = new CommandConfig();

        public <T> Builder registerType(Class<T> type, TypeConverter<T> converter) {
            converters.put(Objects.requireNonNull(type), Objects.requireNonNull(converter));
            return this;
        }

        /** Set global default command/help settings (takes a defensive copy). */
        public Builder commandConfig(CommandConfig commandConfig) {
            this.commandConfig = Objects.requireNonNull(commandConfig).copy();
            return this;
        }

        public Builder commandConfig(Consumer<CommandConfig> configurer) {
            Objects.requireNonNull(configurer).accept(this.commandConfig);
            this.commandConfig = this.commandConfig.copy();
            return this;
        }

        public int run(Object root, String... args) {
            return run(root, System.out, System.err, args);
        }

        public int run(Object root, PrintStream out, PrintStream err, String... args) {
            return FemtoCli.execute(root, out, err, args, converters, commandConfig);
        }

        public RunResult runCaptured(Object root, String... args) {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(outStream);
            PrintStream err = new PrintStream(errStream);
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            System.setOut(out);
            System.setErr(err);
            int exitCode = FemtoCli.execute(root, out, err, args, converters, commandConfig);
            System.setOut(oldOut);
            System.setErr(oldErr);
            return new RunResult(outStream.toString(), errStream.toString(), exitCode);
        }
    }

    public static Builder builder() { return new Builder(); }
    private FemtoCli() {}

    /**
     * Run the CLI with the given root command object and arguments,
     * and use the passed output and error streams for FemtoCli output.
     */
    public static int run(Object root, PrintStream out, PrintStream err, String... args) {
        return execute(root, out, err, args, Map.of(), new CommandConfig(), false);
    }

    public static RunResult runCaptured(Object root, String... args) {
        return builder().runCaptured(root, args);
    }

    public static int run(Object root, String... args) {
        return builder().run(root, args);
    }

    /**
     * Run the CLI with agent args (a single comma-separated string), using {@link System#out} and {@link System#err}.
     * <p>
     * Requires the root command to be annotated with {@code @Command(agentMode = true)}.
     */
    public static int runAgent(Object root, String agentArgs) {
        return runAgent(root, System.out, System.err, agentArgs);
    }

    /**
     * Run the CLI with the given root command object and a single "agent args" string.
     * <p>
     * Agent args are a comma-separated list of tokens that will be translated into the normal {@code String[]} argv.
     * Escapes: {@code \\} (backslash), {@code \,} (comma), {@code \=} (equals). Whitespace around tokens is trimmed.
     * Empty tokens (from ",," or a trailing comma) are rejected; use {@code --opt=} to pass an empty value.
     * <p>
     * Agent mode also supports single-quoted tokens and bare {@code help}/{@code version} and bare option names
     * (like {@code req=x}) when unambiguous.
     * <p>
     * Requires the root command to be annotated with {@code @Command(agentMode = true)}.
     */
    public static int runAgent(Object root, PrintStream out, PrintStream err, String agentArgs) {
        String[] argv = agentArgsToArgv(agentArgs);
        return execute(root, out, err, argv, Map.of(), new CommandConfig(), true);
    }

    public static RunResult runAgentCaptured(Object root, String agentArgs) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outStream);
        PrintStream err = new PrintStream(errStream);
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(out);
        System.setErr(err);
        int exitCode = runAgent(root, out, err, agentArgs);
        System.setOut(oldOut);
        System.setErr(oldErr);
        return new RunResult(outStream.toString(), errStream.toString(), exitCode);
    }

    private static int execute(Object root, PrintStream out, PrintStream err, String[] args,
                               Map<Class<?>, TypeConverter<?>> converters,
                               CommandConfig commandConfig) {
        return execute(root, out, err, args, converters, commandConfig, false);
    }

    @SuppressWarnings("unchecked")
    private static int execute(Object root, PrintStream out, PrintStream err, String[] args,
                               Map<Class<?>, TypeConverter<?>> converters,
                               CommandConfig commandConfig,
                               boolean agentMode) {
        if (root instanceof String) {
            throw new IllegalArgumentException("Root command cannot be a String (got " + root + ").");
        }
        UsageContext previous = USAGE_CONTEXT.get();
        try {
            var tokens = new ArrayDeque<>(Arrays.asList(args));
            Object cmd = root;
            CommandParser<Object> parser = (CommandParser<Object>) FemtoCliRuntime.loadParser(cmd.getClass());
            CommandParser<Object> rootParser = parser;
            String methodSubName = null;

            // Track fully-qualified command path for help output
            List<String> commandPath = new ArrayList<>();
            commandPath.add(parser.name().isEmpty()
                    ? cmd.getClass().getSimpleName().toLowerCase(Locale.ROOT) : parser.name());

            // Initialise (mixin creation, Spec injection, etc.)
            parser.init(cmd, out, err, commandPath, commandConfig);

            // Resolve subcommand chain
            while (!tokens.isEmpty()) {
                String next = tokens.peekFirst();

                // In agent mode, allow bare help/version tokens at any depth.
                if (agentMode) {
                    next = FemtoCliRuntime.normalizeBareHelpOrVersionToken(next);
                }

                if (FemtoCliRuntime.isHelp(next)) {
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode, parser, rootParser));
                    renderHelp(parser, commandPath, commandConfig, out, agentMode);
                    return commandConfig.helpExitCode;
                }
                if (FemtoCliRuntime.isVersion(next)) {
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode, parser, rootParser));
                    printVersion(parser, rootParser, commandConfig, out);
                    return 0;
                }
                if (next.startsWith("-")) {
                    break;
                }

                // Ask the generated parser whether this token names a subcommand
                String subName = parser.resolveSubcommand(next);
                if (subName != null) {
                    tokens.removeFirst();
                    Object subCmd = parser.createSubcommand(subName);
                    CommandParser<Object> subParser = (CommandParser<Object>) parser.subcommandParser(subName);
                    if (subCmd != null && subParser != null) {
                        cmd = subCmd;
                        parser = subParser;
                        commandPath.add(parser.name().isEmpty() ? subName : parser.name());
                        parser.init(cmd, out, err, commandPath, commandConfig);
                        continue;
                    }
                    // Method-subcommand: add to command path, parse parent, invoke method
                    commandPath.add(subName);
                    methodSubName = subName;
                    break;
                }
                break;
            }

            USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode, parser, rootParser));

            if (methodSubName != null) {
                // Method subcommand: check remaining tokens for --help/--version,
                // then invoke the method directly (no parent parse()).
                for (String t : tokens) {
                    String nt = agentMode ? FemtoCliRuntime.normalizeBareHelpOrVersionToken(t) : t;
                    if (FemtoCliRuntime.isHelp(nt)) {
                        renderHelp(parser, commandPath, commandConfig, out, agentMode);
                        return commandConfig.helpExitCode;
                    }
                    if (FemtoCliRuntime.isVersion(nt)) {
                        printVersion(parser, rootParser, commandConfig, out);
                        return 0;
                    }
                }
                return parser.invokeMethodSubcommand(cmd, methodSubName);
            }

            // In agent mode, normalise bare option tokens (e.g. "port=8080" → "--port=8080")
            if (agentMode) {
                parser.normalizeAgentTokens(cmd, tokens);
            }

            try {
                parser.parse(cmd, tokens, converters, commandConfig);
            } catch (UsageEx ue) {
                throw ue;
            } catch (IllegalArgumentException e) {
                throw FemtoCliRuntime.usageError(cmd, "Invalid value: " + e.getMessage());
            }
            return parser.invoke(cmd);

        } catch (UsageEx e) {
            UsageContext ctx = USAGE_CONTEXT.get();
            CommandParser<?> activeParser = ctx != null ? ctx.parser : null;
            if (activeParser == null) {
                try { activeParser = FemtoCliRuntime.loadParser(root.getClass()); } catch (Exception ignored) {}
            }
            if (e.help && activeParser != null) {
                renderHelp(activeParser,
                        ctx != null ? ctx.commandPath : List.of(""),
                        commandConfig, out, ctx != null && ctx.agentMode);
                return commandConfig.helpExitCode;
            }
            if (e.version) {
                CommandParser<?> rootP = ctx != null ? ctx.rootParser : activeParser;
                if (activeParser != null) printVersion(activeParser, rootP, commandConfig, out);
                else out.println("unknown");
                return 0;
            }
            PrintStream errorStream = commandConfig.usageErrorsToStdout ? out : err;
            errorStream.println("Error: " + e.getMessage());
            errorStream.println();
            if (activeParser != null) {
                renderHelp(activeParser,
                        ctx != null ? ctx.commandPath : List.of(""),
                        commandConfig, errorStream, ctx != null && ctx.agentMode);
            }
            return 2;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            return 1;
        } finally {
            if (previous == null) {
                USAGE_CONTEXT.remove();
            } else {
                USAGE_CONTEXT.set(previous);
            }
        }
    }

    // ---- Help / version rendering ----

    private static void renderHelp(CommandParser<?> parser, List<String> commandPath,
                                   CommandConfig config, PrintStream out, boolean agentMode) {
        String displayPath = String.join(agentMode ? "," : " ", commandPath);
        parser.renderHelp(out, displayPath, config, agentMode);
    }

    private static void printVersion(CommandParser<?> parser, CommandParser<?> rootParser,
                                     CommandConfig config, PrintStream out) {
        String v = parser.version();
        if ((v == null || v.isBlank()) && rootParser != null) v = rootParser.version();
        if (v == null || v.isBlank()) v = config.version;
        out.println((v != null && !v.isBlank()) ? v : "unknown");
    }

    /**
     * Print usage for the given command object. Looks up the generated parser.
     */
    public static void usage(Object cmd, PrintStream out) {
        UsageContext ctx = USAGE_CONTEXT.get();
        if (ctx != null && ctx.parser != null) {
            renderHelp(ctx.parser, ctx.commandPath, ctx.commandConfig, out, ctx.agentMode);
            return;
        }
        @SuppressWarnings("unchecked")
        CommandParser<Object> parser = (CommandParser<Object>) FemtoCliRuntime.loadParser(cmd.getClass());
        renderHelp(parser, List.of(parser.name()), new CommandConfig(), out, false);
    }

    public static void version(Object root, PrintStream out) {
        UsageContext ctx = USAGE_CONTEXT.get();
        if (ctx != null && ctx.parser != null) {
            printVersion(ctx.parser, ctx.rootParser, ctx.commandConfig, out);
            return;
        }
        @SuppressWarnings("unchecked")
        CommandParser<Object> parser = (CommandParser<Object>) FemtoCliRuntime.loadParser(root.getClass());
        printVersion(parser, parser, new CommandConfig(), out);
    }

    // ---- Internal context (thread-local for nested help/version calls from Spec) ----

    private static final class UsageContext {
        final List<String> commandPath;
        final CommandConfig commandConfig;
        final boolean agentMode;
        final CommandParser<?> parser;
        final CommandParser<?> rootParser;

        UsageContext(List<String> commandPath, CommandConfig commandConfig,
                     boolean agentMode, CommandParser<?> parser, CommandParser<?> rootParser) {
            this.commandPath = commandPath;
            this.commandConfig = commandConfig;
            this.agentMode = agentMode;
            this.parser = parser;
            this.rootParser = rootParser;
        }
    }

    static final ThreadLocal<UsageContext> USAGE_CONTEXT = new ThreadLocal<>();

    // ---- Agent args parsing (inlined from former AgentArgs class) ----

    static String[] agentArgsToArgv(String agentArgs) {
        if (agentArgs == null) {
            throw new IllegalArgumentException("agentArgs must not be null");
        }
        if (agentArgs.isBlank()) {
            return new String[0];
        }
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean escaping = false;
        boolean inSingleQuotes = false;

        for (int i = 0; i < agentArgs.length(); i++) {
            char c = agentArgs.charAt(i);
            if (escaping) {
                if (c == '\\' || c == ',' || c == '=') { cur.append(c); }
                else { throw new IllegalArgumentException("Invalid escape sequence: \\" + c + " at index " + i); }
                escaping = false;
                continue;
            }
            if (c == '\\') { escaping = true; continue; }
            if (c == '\'') { inSingleQuotes = !inSingleQuotes; continue; }
            if (!inSingleQuotes && c == ',') {
                addAgentToken(out, cur);
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (escaping) throw new IllegalArgumentException("Dangling escape at end of agent args");
        if (inSingleQuotes) throw new IllegalArgumentException("Unterminated single quote in agent args");
        addAgentToken(out, cur);
        return out.toArray(String[]::new);
    }

    private static void addAgentToken(List<String> out, StringBuilder cur) {
        String token = cur.toString().trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Empty token in agent args (did you use ',,' or a trailing comma?)");
        }
        out.add(token);
    }
}