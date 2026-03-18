package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Minimal reflection-based CLI runner with Java 21 features.
 */
public final class FemtoCli {

    static final String NO_DEFAULT_VALUE = "__NO_DEFAULT_VALUE__";

    /** Builder for configuring FemtoCli with custom type handlers. */
    public static class Builder {
        private final Map<Class<?>, TypeConverter<?>> converters = new HashMap<>();
        private final Set<Class<?>> removedCommands = new HashSet<>();
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

        /**
         * Remove the given command classes so that they are ignored everywhere,
         * including as transitive subcommands, and never show up in help.
         *
         * <pre>{@code
         * FemtoCli.builder()
         *         .removeCommands(Experimental.class, Internal.class)
         *         .run(root, args);
         * }</pre>
         */
        public Builder removeCommands(Class<?>... classes) {
            Collections.addAll(removedCommands, classes);
            return this;
        }

        public int run(Object root, String... args) {
            return run(root, System.out, System.err, args);
        }

        public int run(Object root, PrintStream out, PrintStream err, String... args) {
            return FemtoCli.execute(root, out, err, args, converters, commandConfig, false, removedCommands);
        }

        public RunResult runCaptured(Object root, String... args) {
            return captureRun((out, err) -> FemtoCli.execute(root, out, err, args, converters, commandConfig, false, removedCommands));
        }
    }

    public static Builder builder() { return new Builder(); }
    private FemtoCli() {}

    /**
     * Run the CLI with the given root command object and arguments,
     * and use the passed output and error streams for FemtoCli output.
     */
    public static int run(Object root, PrintStream out, PrintStream err, String... args) {
        return execute(root, out, err, args, Map.of(), new CommandConfig(), false, Set.of());
    }

    public static RunResult runCaptured(Object root, String... args) {
        return builder().runCaptured(root, args);
    }

    public static int run(Object root, String... args) {
        return builder().run(root, args);
    }

    /**
     * Run the CLI with agent args (a single comma-separated string), using {@link System#out} and {@link System#err}.
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
     */
    public static int runAgent(Object root, PrintStream out, PrintStream err, String agentArgs) {
        String[] argv = AgentArgs.toArgv(agentArgs);
        return execute(root, out, err, argv, Map.of(), new CommandConfig(), true, Set.of());
    }

    public static RunResult runAgentCaptured(Object root, String agentArgs) {
        return captureRun((out, err) -> runAgent(root, out, err, agentArgs));
    }

    private static RunResult captureRun(java.util.function.BiFunction<PrintStream, PrintStream, Integer> runner) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outStream);
        PrintStream err = new PrintStream(errStream);
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(out);
        System.setErr(err);
        int exitCode = runner.apply(out, err);
        System.setOut(oldOut);
        System.setErr(oldErr);
        return new RunResult(outStream.toString(), errStream.toString(), exitCode);
    }

    private static int execute(Object root, PrintStream out, PrintStream err, String[] args,
                               Map<Class<?>, TypeConverter<?>> converters,
                               CommandConfig commandConfig,
                               boolean agentMode,
                               Set<Class<?>> removedCommands) {
        if (root instanceof String) {
            throw new IllegalArgumentException("Root command cannot be a String (got " + root + ").");
        }
        UsageContext previous = USAGE_CONTEXT.get();
        Set<Class<?>> previousRemoved = REMOVED_COMMANDS.get();
        try {
            REMOVED_COMMANDS.set(removedCommands);
            var tokens = new ArrayDeque<>(Arrays.asList(args));
            Object cmd = root;
            List<Object> commandChain = new ArrayList<>();
            List<String> commandPath = new ArrayList<>();
            commandPath.add(commandName(root));

            // Process commands and their options in sequence
            while (true) {
                // Check for help/version at current level
                int hvResult = checkHelpVersion(tokens, agentMode, cmd, root, out, commandPath, commandConfig);
                if (hvResult >= 0) return hvResult;

                // If the current command has no subcommands, it's the final command
                if (!hasSubcommands(cmd.getClass())) {
                    break;
                }

                // This command has subcommands: parse its options, then look for subcommand
                CommandModel model = CommandModel.of(cmd);
                injectSpec(model, out, err, List.copyOf(commandPath), commandConfig, List.copyOf(commandChain));
                if (agentMode) {
                    normalizeBareOptionTokens(cmd, tokens, model);
                }
                parseOptions(model, cmd, tokens, converters,
                        (USAGE_CONTEXT.get() != null ? USAGE_CONTEXT.get().commandConfig : commandConfig), true);

                // Consume leading positional parameter values for this command so that
                // the subcommand lookup below sees the actual subcommand name.
                // E.g. for "agent <PID> start ...", consume <PID> then find "start".
                List<String> parentPositionals = consumeLeadingPositionalTokens(cmd, tokens, model, agentMode);

                // After parsing options, check if there's a subcommand
                if (tokens.isEmpty()) {
                    if (!parentPositionals.isEmpty()) {
                        bindPositionals(cmd, parentPositionals, model.parameters, converters);
                    }
                    // No subcommand given - fall through to invoke this command
                    setUsageCtx(commandPath, commandConfig, agentMode);
                    return invoke(cmd);
                }

                // Check for help/version again after parsing options
                hvResult = checkHelpVersion(tokens, agentMode, cmd, root, out, commandPath, commandConfig);
                if (hvResult >= 0) {
                    if (!parentPositionals.isEmpty()) {
                        bindPositionals(cmd, parentPositionals, model.parameters, converters);
                    }
                    return hvResult;
                }

                String next = peekNormalized(tokens, agentMode);

                // Check for subcommand class
                Class<?> sub = findSubcommand(cmd.getClass(), next);
                if (sub != null) {
                    tokens.removeFirst();
                    if (!parentPositionals.isEmpty()) {
                        bindPositionals(cmd, parentPositionals, model.parameters, converters);
                    }
                    commandChain.add(cmd);
                    cmd = instantiateSubcommand(sub);
                    commandPath.add(commandName(cmd));
                    continue; // Process next level
                }

                // Check for @Command method
                Method method = findSubcommandMethod(cmd.getClass(), next);
                if (method != null) {
                    tokens.removeFirst();
                    if (!parentPositionals.isEmpty()) {
                        bindPositionals(cmd, parentPositionals, model.parameters, converters);
                    }
                    Command mc = method.getAnnotation(Command.class);
                    if (mc != null && !mc.name().isBlank()) {
                        commandPath.add(mc.name());
                    }
                    setUsageCtx(commandPath, commandConfig, agentMode);
                    return invokeSubcommandMethod(cmd, method, tokens, converters);
                }

                // Bare "help" token in subcommand position → treat as --help
                if ("help".equals(next)) {
                    if (!parentPositionals.isEmpty()) {
                        bindPositionals(cmd, parentPositionals, model.parameters, converters);
                    }
                    setUsageCtx(commandPath, commandConfig, agentMode);
                    usage(cmd, out);
                    return commandConfig.helpExitCode;
                }

                // No subcommand found – check for a default subcommand
                Command cmdAnn = cmd.getClass().getAnnotation(Command.class);
                Class<?> defaultSub = cmdAnn != null ? cmdAnn.defaultSubcommand() : void.class;
                if (defaultSub != void.class && !isCommandRemoved(defaultSub)) {
                    // Put positionals back – they become positionals for the default subcommand
                    for (int i = parentPositionals.size() - 1; i >= 0; i--) {
                        tokens.addFirst(parentPositionals.get(i));
                    }
                    commandChain.add(cmd);
                    cmd = instantiateSubcommand(defaultSub);
                    commandPath.add(commandName(cmd));
                    continue;
                }
                // Put positionals back for parseInto
                for (int i = parentPositionals.size() - 1; i >= 0; i--) {
                    tokens.addFirst(parentPositionals.get(i));
                }
                break;
            }

            // Final command: full parsing (options + positionals + required validation)
            setUsageCtx(commandPath, commandConfig, agentMode);

            CommandModel model = CommandModel.of(cmd);
            injectSpec(model, out, err, List.copyOf(commandPath), commandConfig, List.copyOf(commandChain));

            if (agentMode) {
                normalizeBareOptionTokens(cmd, tokens, model);
            }

            parseInto(cmd, tokens, converters);
            return invoke(cmd);

        } catch (UsageEx e) {
            Object target = e.cmd != null ? e.cmd : root;
            if (e.help) {
                usage(target, out);
                return commandConfig.helpExitCode;
            }
            if (e.version) {
                version(root, out);
                return 0;
            }
            // Print error and usage to stderr by default, or stdout if configured
            PrintStream errorStream = commandConfig.usageErrorsToStdout ? out : err;
            errorStream.println("Error: " + e.getMessage());
            errorStream.println();
            usage(target, errorStream);
            return 2;
        } catch (FieldIsFinalException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            return 1;
        } finally {
            // restore previous context if any
            if (previous == null) {
                USAGE_CONTEXT.remove();
            } else {
                USAGE_CONTEXT.set(previous);
            }
            if (previousRemoved == null) {
                REMOVED_COMMANDS.remove();
            } else {
                REMOVED_COMMANDS.set(previousRemoved);
            }
        }
    }

    private static void normalizeBareOptionTokens(Object cmdForErrors, Deque<String> tokens, CommandModel model) throws UsageEx {
        if (tokens.isEmpty() || model == null) return;
        List<String> normalized = new ArrayList<>(tokens.size());
        for (String t : tokens) normalized.add(normalizeBareOptionToken(cmdForErrors, t, model));
        tokens.clear();
        tokens.addAll(normalized);
    }

    private static String normalizeBareHelpOrVersionToken(String token) {
        if ("help".equals(token)) return "--help";
        if ("version".equals(token)) return "--version";
        return token;
    }

    private static String normalizeBareOptionToken(Object cmdForErrors, String token, CommandModel model) throws UsageEx {
        if (token == null || token.isEmpty()) {
            return token;
        }
        if (token.startsWith("-")) {
            return token;
        }
        // help/version as bare tokens (besides regular --help/-h and --version/-V)
        token = normalizeBareHelpOrVersionToken(token);
        if ("--help".equals(token) || "--version".equals(token)) {
            return token;
        }

        // Support bare boolean flags without '=' in agent mode, if unambiguous and boolean.
        // Example: "flag" is normalized to "--flag" if --flag is a known boolean option.
        if (!token.contains("=")) {
            List<String> candidates = getCandidates(model, token, true);
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
            if (candidates.size() > 1) {
                throw new UsageEx(cmdForErrors,
                        "Ambiguous bare option '" + token + "' (use full name): " + String.join(", ", candidates));
            }
            return token;
        }

        // Only treat tokens containing '=' as potential bare options to avoid interfering with positionals/subcommands.
        int eq = token.indexOf('=');
        String bareName = token.substring(0, eq);
        if (bareName.isBlank()) {
            return token;
        }

        List<String> candidates = getCandidates(model, bareName, false);

        if (candidates.isEmpty()) {
            return token;
        }
        // If multiple candidates exist but they all map to the same field, pick the long form if present.
        if (candidates.size() > 1) {
            // Check whether there are multiple distinct target fields
            Set<Field> fields = new HashSet<>();
            for (String c : candidates) {
                fields.add(model.optionsByName.get(c).field);
            }
            if (fields.size() == 1) {
                for (String c : candidates) {
                    if (c.startsWith("--")) {
                        return c + token.substring(eq);
                    }
                }
                return candidates.get(0) + token.substring(eq);
            }

            throw new UsageEx(cmdForErrors,
                    "Ambiguous bare option '" + bareName + "' (use full name): " + String.join(", ", candidates));
        }
        return candidates.get(0) + token.substring(eq);
    }

    private static List<String> getCandidates(CommandModel model, String bareName, boolean requireBoolean) {
        List<String> candidates = new ArrayList<>();
        for (var e : model.optionsByName.entrySet()) {
            String optName = e.getKey();
            if (!optName.startsWith("-")) {
                continue;
            }

            OptionMeta meta = e.getValue();
            if (requireBoolean && !FemtoCli.isBooleanType(meta.field.getType())) {
                continue;
            }

            String stripped = optName.startsWith("--") ? optName.substring(2) : optName.substring(1);
            if (stripped.equals(bareName)) {
                candidates.add(optName);
            }
        }
        return candidates;
    }

    private static String commandName(Object cmd) {
        Command c = cmd.getClass().getAnnotation(Command.class);
        return c != null && !c.name().isBlank()
                ? c.name()
                : cmd.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    static boolean isBooleanType(Class<?> c) {
        return c == boolean.class || c == Boolean.class;
    }

    private static boolean isHelp(String t) { return "--help".equals(t) || "-h".equals(t); }
    private static boolean isVersion(String t) { return "--version".equals(t) || "-V".equals(t); }

    private static String peekNormalized(Deque<String> tokens, boolean agentMode) {
        if (tokens.isEmpty()) return null;
        String next = tokens.peekFirst();
        return agentMode ? normalizeBareHelpOrVersionToken(next) : next;
    }

    private static int checkHelpVersion(Deque<String> tokens, boolean agentMode,
                                            Object cmd, Object root, PrintStream out,
                                            List<String> commandPath, CommandConfig commandConfig) {
        String next = peekNormalized(tokens, agentMode);
        if (next == null) return -1;
        if (isHelp(next)) {
            setUsageCtx(commandPath, commandConfig, agentMode);
            usage(cmd, out);
            return commandConfig.helpExitCode;
        }
        if (isVersion(next)) {
            setUsageCtx(commandPath, commandConfig, agentMode);
            version(root, out);
            return 0;
        }
        return -1;
    }

    private static Object instantiateSubcommand(Class<?> sub) throws Exception {
        var ctor = sub.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    /**
     * Use {@link Spec#usage()} instead
     */
    public static void usage(Object cmd, PrintStream out) {
        UsageContext ctx = USAGE_CONTEXT.get();
        if (ctx != null) {
            // Always show the full command path (root + subcommands) in the usage line.
            usage(cmd, ctx.commandPath, ctx.commandConfig, out, ctx.agentMode);
            return;
        }
        usage(cmd, List.of(commandName(cmd)), new CommandConfig(), out, false);
    }

    static void usage(Object cmd, List<String> commandPath, CommandConfig commandConfig, PrintStream out, boolean agentMode) {
        HelpRenderer.render(cmd, String.join(agentMode ? "," : " ", commandPath), commandConfig, out, agentMode);
    }

    public static void version(Object root, PrintStream out) {
        Command c = root.getClass().getAnnotation(Command.class);
        UsageContext ctx = USAGE_CONTEXT.get();
        String version = ctx != null ? ctx.commandConfig.effectiveVersion(c) : (c != null ? c.version() : "");
        out.println(!version.isBlank() ? version : "unknown");
    }

    private static void parseInto(Object cmd, Deque<String> tokens,
                                  Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        var model = CommandModel.of(cmd);
        UsageContext ctx = USAGE_CONTEXT.get();
        if (ctx != null) {
            injectSpec(model, System.out, System.err, ctx.commandPath, ctx.commandConfig, List.of());
        } else {
            injectSpec(model, System.out, System.err, List.of(commandName(model.cmd)), new CommandConfig(), List.of());
        }
        CommandConfig config = ctx != null ? ctx.commandConfig : new CommandConfig();

        List<String> positionals = parseOptions(model, cmd, tokens, converters, config, false);

        // Bind positionals based on index/arity
        bindPositionals(cmd, positionals, model.parameters, converters);

        // Validate required options
        validateRequiredOptions(cmd, model.options, model.seenFields);
    }

    /**
     * Shared option-parsing loop. When stopAtNonOption is true, stops at first non-option
     * token and leaves it in the queue.
     */
    private static List<String> parseOptions(CommandModel model, Object cmd, Deque<String> tokens,
                                             Map<Class<?>, TypeConverter<?>> converters,
                                             CommandConfig config, boolean stopAtNonOption) throws Exception {
        Set<Field> seenFields = new HashSet<>();
        Set<Field> seenFieldsWithoutValue = new HashSet<>();
        Map<Field, List<String>> multiValueFields = new HashMap<>();
        List<String> positionals = new ArrayList<>();
        boolean acceptOptions = true;

        while (!tokens.isEmpty()) {
            String token = stopAtNonOption ? tokens.peekFirst() : tokens.removeFirst();

            if (!stopAtNonOption) {
                if (isHelp(token)) throw UsageEx.help(cmd);
                if (isVersion(token)) throw UsageEx.version();
            }

            if (acceptOptions && "--".equals(token)) {
                if (stopAtNonOption) tokens.removeFirst();
                acceptOptions = false;
                continue;
            }

            if (acceptOptions && token.startsWith("-")) {
                if (stopAtNonOption) tokens.removeFirst();
                parseOption(model, cmd, token, tokens, seenFields, seenFieldsWithoutValue, multiValueFields, converters, config);
            } else if (stopAtNonOption) {
                break;
            } else {
                positionals.add(token);
            }
        }

        // Apply multi-value fields
        applyMultiValueFields(model, multiValueFields, converters);

        // Apply default values for unseen options
        applyDefaultValues(model, seenFields, seenFieldsWithoutValue, converters);

        // Store seenFields on model for later required-option validation
        model.seenFields = seenFields;

        return positionals;
    }

    private static Object invokeConverterMethod(String spec, Object cmd, String raw, Class<?> targetType) throws Exception {
        Method m = resolveMethod(spec, cmd != null ? cmd.getClass() : null);
        if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != String.class) {
            throw new IllegalArgumentException("Converter method must take a single String argument: " + spec);
        }
        Object result = invokeResolvedMethod(m, cmd, "Converter", spec, raw);
        if (result == null) return null;
        if (targetType.isInstance(result) || isPrimitiveWrapperAssignable(targetType, result.getClass())) return result;
        throw new IllegalArgumentException("Converter method returned incompatible type for " + spec);
    }

    private static void invokeSingleArgMethod(String spec, Object cmdForErrors, Object value) throws Exception {
        Method m = resolveMethod(spec, cmdForErrors != null ? cmdForErrors.getClass() : null);
        if (m.getParameterCount() != 1) {
            throw new IllegalArgumentException("Verifier method must take a single argument: " + spec);
        }
        invokeResolvedMethod(m, cmdForErrors, "Verifier", spec, value);
    }

    private static Object invokeResolvedMethod(Method m, Object instance, String kind, String spec, Object arg) throws Exception {
        boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
        Object receiver = isStatic ? null : instance;
        if (!isStatic && instance == null) {
            throw new IllegalArgumentException(kind + " method requires a command instance: " + spec);
        }
        m.setAccessible(true);
        return m.invoke(receiver, arg);
    }

    private static void runVerifiers(Object cmdForErrors, Object value, Option opt, Parameters param) throws UsageEx {
        try {
            if (opt != null) {
                runVerifier(cmdForErrors, value, opt.verifier(), opt.verifierMethod());
            }
            if (param != null) {
                runVerifier(cmdForErrors, value, param.verifier(), param.verifierMethod());
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof VerifierException ve) {
                throw new UsageEx(cmdForErrors, ve.getMessage());
            }
            throw new UsageEx(cmdForErrors, cause != null ? cause.getMessage() : ite.getMessage());
        } catch (Exception e) {
            throw new UsageEx(cmdForErrors, e.getMessage());
        }
    }

    private static void runVerifier(Object cmdForErrors, Object value,
                                    @SuppressWarnings("rawtypes") Class<? extends Verifier> verifierClass,
                                    String verifierMethod) throws Exception {
        if (verifierClass != null && verifierClass != Verifier.NullVerifier.class) {
            //noinspection unchecked
            verifierClass.getDeclaredConstructor().newInstance().verify(value);
        }
        if (!verifierMethod.isBlank()) {
            invokeSingleArgMethod(verifierMethod, cmdForErrors, value);
        }
    }

    private static void parseOption(CommandModel model, Object cmd, String token, Deque<String> tokens,
                                    Set<Field> seenFields,
                                    Set<Field> seenFieldsWithoutValue,
                                    Map<Field, List<String>> multiValueFields,
                                    Map<Class<?>, TypeConverter<?>> converters,
                                    CommandConfig config) throws Exception {
        int eqIndex = token.indexOf('=');
        String name = eqIndex >= 0 ? token.substring(0, eqIndex) : token;
        String value = eqIndex >= 0 ? token.substring(eqIndex + 1) : null;

        OptionMeta optMeta = model.optionsByName.get(name);
        if (optMeta == null) {
            String errorMsg = "Unknown option: " + name;
            if (config.suggestSimilarOptions) {
                String suggestion = findSimilarOption(name, model.optionsByName.keySet());
                if (suggestion != null) {
                    String template = "\n" + config.similarOptionsSuggestionTemplate;
                    errorMsg += template.replace("${SUGGESTION}", suggestion);
                }
            }
            throw new UsageEx(cmd, errorMsg);
        }
        seenFields.add(optMeta.field);

        Option opt = optMeta.opt;

        Class<?> type = optMeta.field.getType();
        boolean isBoolean = isBooleanType(type);

        // If no explicit value is provided (no "=...")
        if (value == null) {
            // Check whether boolean should be flag vs requiring explicit value (when converter is present)
            boolean hasPerOptionConverter = opt != null && (!opt.converterMethod().isBlank() || opt.converter() != TypeConverter.NullTypeConverter.class);
            boolean hasRegisteredConverter = converters != null && converters.containsKey(type);
            boolean treatBooleanAsFlag = isBoolean && !hasPerOptionConverter && !hasRegisteredConverter;

            // Boolean flags: presence means true, allow explicit boolean value as next token.
            if (treatBooleanAsFlag) {
                if (!tokens.isEmpty() && ("true".equalsIgnoreCase(tokens.peekFirst()) || "false".equalsIgnoreCase(tokens.peekFirst()))) {
                    value = tokens.removeFirst();
                } else {
                    optMeta.field.set(optMeta.target, true);
                    return;
                }
            }

            // Optional-value option
            if (value == null && opt != null && "0..1".equals(opt.arity())) {
                seenFieldsWithoutValue.add(optMeta.field);
                return;
            }

            // Otherwise a value is required
            if (value == null) {
                if (tokens.isEmpty()) {
                    throw new UsageEx(cmd, "Missing value for option: " + name);
                }
                value = tokens.removeFirst();
            }
        }

        if (type.isArray() || List.class.isAssignableFrom(type)) {
            // Handle multi-value options
            List<String> values = multiValueFields.computeIfAbsent(optMeta.field, k -> new ArrayList<>());
            String delimiter = opt != null ? opt.split() : "";
            if (!delimiter.isEmpty()) {
                Collections.addAll(values, value.split(delimiter));
            } else {
                values.add(value);
            }
        } else {
            Object converted = convert(value, type, optMeta.field.getName(), opt, converters, cmd);
            runVerifiers(cmd, converted, opt, null);
            optMeta.field.set(optMeta.target, converted);
        }
    }

    private static void applyMultiValueFields(CommandModel model, Map<Field, List<String>> multiValueFields,
                                              Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        for (var entry : multiValueFields.entrySet()) {
            Field field = entry.getKey();
            List<String> values = entry.getValue();

            OptionMeta optMeta = model.optionByField.get(field);
            Option opt = optMeta != null ? optMeta.opt : field.getAnnotation(Option.class);
            Object target = optMeta != null ? optMeta.target : model.cmd;

            Class<?> type = field.getType();

            if (type.isArray()) {
                field.set(target, convertToArray(values, type.getComponentType(), field.getName(), opt, null, converters, model.cmd));
            } else {
                field.set(target, convertToList(values, field.getName(), opt, null, converters, model.cmd));
            }
        }
    }

    private static Object convertToArray(List<String> values, Class<?> componentType, String fieldName,
                                         Option opt, Parameters param,
                                         Map<Class<?>, TypeConverter<?>> converters, Object cmd) throws Exception {
        Object array = Array.newInstance(componentType, values.size());
        for (int i = 0; i < values.size(); i++) {
            Object converted = convert(values.get(i), componentType, fieldName, opt, param, converters, cmd);
            runVerifiers(cmd, converted, opt, param);
            Array.set(array, i, converted);
        }
        return array;
    }

    private static List<Object> convertToList(List<String> values, String fieldName,
                                              Option opt, Parameters param,
                                              Map<Class<?>, TypeConverter<?>> converters, Object cmd) throws Exception {
        List<Object> list = new ArrayList<>();
        for (String v : values) {
            Object converted = convert(v, String.class, fieldName, opt, param, converters, cmd);
            runVerifiers(cmd, converted, opt, param);
            list.add(converted);
        }
        return list;
    }

    private static void applyDefaultValues(CommandModel model,
                                           Set<Field> seenFields,
                                           Set<Field> seenFieldsWithoutValue,
                                           Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        for (OptionMeta optMeta : model.options) {
            Field field = optMeta.field;
            Option opt = optMeta.opt;

            boolean shouldApply = !opt.defaultValue().equals(NO_DEFAULT_VALUE) && (!seenFields.contains(field) || seenFieldsWithoutValue.contains(field));

            if (shouldApply) {
                optMeta.field.set(optMeta.target,
                        convert(opt.defaultValue(), field.getType(), field.getName(), opt, converters, model.cmd));
                seenFields.add(field);
            }
        }
    }

    private static void validateRequiredOptions(Object cmd, List<OptionMeta> options,
                                                Set<Field> seenFields) throws UsageEx {
        for (OptionMeta optMeta : options) {
            Option opt = optMeta.opt;
            if (opt.required() && !seenFields.contains(optMeta.field)) {
                throw new UsageEx(cmd, "Missing required option: " + preferredOptionName(opt));
            }
        }
    }

    private static String preferredOptionName(Option opt) {
        if (opt == null || opt.names().length == 0) return "<option>";
        for (String n : opt.names()) if (n.startsWith("--")) return n;
        return opt.names()[0];
    }

    static int[] parseRange(String range) {
        if (range == null || range.isEmpty()) return new int[]{-2, -2}; // marker for "not specified"
        if (range.contains("..")) {
            String[] parts = range.split("\\.\\.");
            int start = Integer.parseInt(parts[0]);
            int end = "*".equals(parts[1]) ? -1 : Integer.parseInt(parts[1]);
            return new int[]{start, end};
        }
        int idx = Integer.parseInt(range);
        return new int[]{idx, idx};
    }

    static final class ParamInfo {
        final Field field;
        final Parameters param;
        final int[] indexRange;
        final int[] arityRange;

        ParamInfo(Field field, Parameters param, int[] indexRange, int[] arityRange) {
            this.field = field;
            this.param = param;
            this.indexRange = indexRange;
            this.arityRange = arityRange;
        }
    }

    static final class OptionMeta {
        final Field field;
        final Object target;
        final Option opt;

        OptionMeta(Field field, Object target, Option opt) {
            this.field = field;
            this.target = target;
            this.opt = opt;
        }
    }

    private static void bindPositionals(Object cmd, List<String> positionals, List<ParamInfo> paramInfos,
                                        Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        if (paramInfos.isEmpty()) {
            if (!positionals.isEmpty()) {
                throw new UsageEx(cmd, "Unexpected parameter: " + positionals.get(0));
            }
            return;
        }

        int currentIndex = 0;

        for (ParamInfo paramInfo : paramInfos) {
            Field field = paramInfo.field;
            Parameters param = paramInfo.param;
            int endIndex = paramInfo.indexRange[1]; // -1 = unbounded

            boolean isVarargs = endIndex == -1
                                || List.class.isAssignableFrom(field.getType())
                                || field.getType().isArray();

            int[] arity = determineArity(paramInfo, isVarargs);

            if (isVarargs) {
                currentIndex = bindVarargsParam(cmd, field, positionals, currentIndex, arity, paramInfo, converters);
            } else {
                currentIndex = bindSingleParam(cmd, field, param, positionals, currentIndex, arity, paramInfo, converters);
            }
        }

        // Check for extra positionals
        if (currentIndex < positionals.size()) {
            ParamInfo lastParam = paramInfos.get(paramInfos.size() - 1);
            boolean lastIsVarargs = lastParam.indexRange[1] == -1
                                    || lastParam.field.getType().isArray()
                                    || List.class.isAssignableFrom(lastParam.field.getType());

            if (!lastIsVarargs) {
                throw new UsageEx(cmd, "Too many parameters");
            }
        }
    }

    private static int[] determineArity(ParamInfo paramInfo, boolean isVarargs) {
        int[] arityRange = paramInfo.arityRange;

        if (arityRange[0] == -2) {
            return isVarargs ? new int[]{0, Integer.MAX_VALUE} : new int[]{1, 1};
        }

        return new int[]{arityRange[0], arityRange[1] == -1 ? Integer.MAX_VALUE : arityRange[1]};
    }

    private static int bindVarargsParam(Object cmd, Field field, List<String> positionals,
                                        int startIndex, int[] arity, ParamInfo paramInfo,
                                        Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        int available = positionals.size() - startIndex;
        int toConsume = Math.min(available, arity[1]);
        List<String> values = positionals.subList(startIndex, startIndex + toConsume);

        if (values.size() < arity[0]) {
            throw new UsageEx(cmd, "Missing required parameter: " + getParamLabel(paramInfo));
        }

        if (field.getType().isArray()) {
            field.set(cmd, convertToArray(values, field.getType().getComponentType(), field.getName(), null, paramInfo.param, converters, cmd));
        } else if (List.class.isAssignableFrom(field.getType())) {
            field.set(cmd, convertToList(values, field.getName(), null, paramInfo.param, converters, cmd));
        } else if (!values.isEmpty()) {
            Object converted = convert(values.get(0), field.getType(), field.getName(), null, paramInfo.param, converters, cmd);
            runVerifiers(cmd, converted, null, paramInfo.param);
            field.set(cmd, converted);
        }

        return startIndex + toConsume;
    }

    private static int bindSingleParam(Object cmd, Field field, Parameters param,
                                       List<String> positionals, int currentIndex,
                                       int[] arity, ParamInfo paramInfo,
                                       Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        boolean isOptional = arity[0] == 0 || "0..1".equals(param.arity());

        if (currentIndex < positionals.size()) {
            String value = positionals.get(currentIndex);
            Object converted = convert(value, field.getType(), field.getName(), null, param, converters, cmd);
            runVerifiers(cmd, converted, null, param);
            field.set(cmd, converted);
            return currentIndex + 1;
        }

        if (!isOptional && param.defaultValue().equals(NO_DEFAULT_VALUE)) {
            throw new UsageEx(cmd, "Missing required parameter: " + getParamLabel(paramInfo));
        }

        if (!param.defaultValue().equals(NO_DEFAULT_VALUE)) {
            Object converted = convert(param.defaultValue(), field.getType(), field.getName(), null, param, converters, cmd);
            runVerifiers(cmd, converted, null, param);
            field.set(cmd, converted);
        }

        return currentIndex;
    }

    private static String getParamLabel(ParamInfo pi) {
        return pi.param.paramLabel().isEmpty() ? "<" + pi.field.getName() + ">" : pi.param.paramLabel();
    }

    /* Builtins */
    private static final Map<Class<?>, TypeConverter<?>> BUILTIN_CONVERTERS;
    static {
        Map<Class<?>, TypeConverter<?>> m = new HashMap<>();
        m.put(String.class, (TypeConverter<String>) s -> s);
        addPair(m, int.class, Integer.class, Integer::parseInt);
        addPair(m, long.class, Long.class, Long::parseLong);
        addPair(m, double.class, Double.class, Double::parseDouble);
        addPair(m, float.class, Float.class, Float::parseFloat);
        addPair(m, boolean.class, Boolean.class, Boolean::parseBoolean);
        m.put(Path.class, (TypeConverter<Path>) Path::of);
        m.put(Duration.class, (TypeConverter<Duration>) FemtoCli::parseDuration);
        BUILTIN_CONVERTERS = Collections.unmodifiableMap(m);
    }

    private static <T> void addPair(Map<Class<?>, TypeConverter<?>> m, Class<?> prim, Class<T> boxed, TypeConverter<T> c) {
        m.put(prim, c); m.put(boxed, c);
    }

    static Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be null or empty");
        }
        String s = raw.trim();
        int i = 0;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || c == 'µ') break;
        }

        String num = s.substring(0, i).trim();
        String unit = s.substring(i).trim();

        long nanosPerUnit = switch (unit) {
            case "ns" -> 1L;
            case "us" -> 1_000L;
            case "ms" -> 1_000_000L;
            case "s" -> 1_000_000_000L;
            case "m" -> 60_000_000_000L;
            case "h" -> 3_600_000_000_000L;
            case "d" -> 86_400_000_000_000L;
            default -> throw new IllegalArgumentException("Invalid duration unit: " + unit);
        };

        return Duration.ofNanos(Math.round(Double.parseDouble(num) * nanosPerUnit));
    }

    private static int invoke(Object cmd) throws Exception {
        if (cmd instanceof Callable<?> callable) {
            Object result = callable.call();
            return result instanceof Integer i ? i : 0;
        }
        if (cmd instanceof Runnable runnable) {
            runnable.run();
            return 0;
        }
        throw new IllegalStateException("Command must implement Runnable or Callable<Integer>");
    }

    public static boolean hasSubcommands(Class<?> cmdClass) {
        Command ann = cmdClass.getAnnotation(Command.class);
        if (ann != null && ann.subcommands().length > 0) {
            for (Class<?> sub : ann.subcommands()) {
                if (!isCommandRemoved(sub)) return true;
            }
        }
        // Also check for @Command methods
        for (Method m : cmdClass.getDeclaredMethods()) {
            if (m.getAnnotation(Command.class) != null) return true;
        }
        return false;
    }

    /**
     * Consume leading non-option tokens that are not subcommand names,
     * up to the number of fixed (non-varargs) positional parameters defined on the command.
     */
    private static List<String> consumeLeadingPositionalTokens(Object cmd, Deque<String> tokens,
                                                               CommandModel model, boolean agentMode) {
        List<String> consumed = new ArrayList<>();
        int fixedCount = 0;
        for (ParamInfo p : model.parameters) {
            if (p.indexRange[1] != -1
                    && !List.class.isAssignableFrom(p.field.getType())
                    && !p.field.getType().isArray()) {
                fixedCount++;
            }
        }
        for (int i = 0; i < fixedCount && !tokens.isEmpty(); i++) {
            String tok = tokens.peekFirst();
            if (tok.startsWith("-")) break;
            String normalized = agentMode && tok.startsWith("'") && tok.endsWith("'")
                    ? tok.substring(1, tok.length() - 1) : tok;
            if (findSubcommand(cmd.getClass(), normalized) != null) break;
            if (findSubcommandMethod(cmd.getClass(), normalized) != null) break;
            if ("help".equals(normalized) || "version".equals(normalized)) break;
            tokens.removeFirst();
            consumed.add(tok);
        }
        return consumed;
    }

    private static Class<?> findSubcommand(Class<?> cmdClass, String name) {
        Command ann = cmdClass.getAnnotation(Command.class);
        if (ann == null) return null;
        for (Class<?> sub : ann.subcommands()) {
            if (isCommandRemoved(sub)) continue;
            Command s = sub.getAnnotation(Command.class);
            if (s != null && s.name().equals(name)) return sub;
        }
        return null;
    }

    private static Method findSubcommandMethod(Class<?> cmdClass, String name) {
        for (Method m : cmdClass.getDeclaredMethods()) {
            Command ann = m.getAnnotation(Command.class);
            if (ann != null && ann.name().equals(name)) return m;
        }
        return null;
    }

    private static int invokeSubcommandMethod(Object parent, Method method, Deque<String> tokens,
                                              Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        method.setAccessible(true);
        var wrapper = new SubcommandMethodWrapper(parent, method);
        parseInto(wrapper, tokens, converters);
        return wrapper.call();
    }

    static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Collections.addAll(fields, c.getDeclaredFields());
        }
        return fields;
    }

    private static final class UsageContext {
        final List<String> commandPath;
        final CommandConfig commandConfig;
        final boolean agentMode;

        UsageContext(List<String> commandPath, CommandConfig commandConfig, boolean agentMode) {
            this.commandPath = commandPath;
            this.commandConfig = commandConfig;
            this.agentMode = agentMode;
        }
    }

    private static final ThreadLocal<UsageContext> USAGE_CONTEXT = new ThreadLocal<>();

    static final ThreadLocal<Set<Class<?>>> REMOVED_COMMANDS = new ThreadLocal<>();

    /** Returns the current set of removed command classes (never null). */
    static boolean isCommandRemoved(Class<?> commandClass) {
        Set<Class<?>> removed = REMOVED_COMMANDS.get();
        return removed != null && removed.contains(commandClass);
    }

    private static void setUsageCtx(List<String> commandPath, CommandConfig commandConfig, boolean agentMode) {
        USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode));
    }

    private static Object convert(String value,
                                  Class<?> type,
                                  String fieldName,
                                  Option opt,
                                  Map<Class<?>, TypeConverter<?>> converters,
                                  Object cmdForErrors) throws UsageEx {
        return convert(value, type, fieldName, opt, null, converters, cmdForErrors);
    }

    private static Object convert(String value,
                                  Class<?> type,
                                  String fieldName,
                                  Option opt,
                                  Parameters param,
                                  Map<Class<?>, TypeConverter<?>> converters,
                                  Object cmdForErrors) throws UsageEx {
        try {
            // 0) Per-option converter method
            if (opt != null && !opt.converterMethod().isBlank()) {
                return invokeConverterMethod(opt.converterMethod(), cmdForErrors, value, type);
            }

            // 0b) Per-parameter converter method
            if (param != null && !param.converterMethod().isBlank()) {
                return invokeConverterMethod(param.converterMethod(), cmdForErrors, value, type);
            }

            // 1) Per-option converter class
            if (opt != null && opt.converter() != TypeConverter.NullTypeConverter.class) {
                TypeConverter<?> perOpt = opt.converter().getDeclaredConstructor().newInstance();
                return perOpt.convert(value);
            }

            // 1b) Per-parameter converter class
            if (param != null && param.converter() != TypeConverter.NullTypeConverter.class) {
                TypeConverter<?> perParam = param.converter().getDeclaredConstructor().newInstance();
                return perParam.convert(value);
            }

            // 2) Custom or built-in converter
            TypeConverter<?> tc = converters.get(type);
            if (tc == null) tc = BUILTIN_CONVERTERS.get(type);
            if (tc != null) return tc.convert(value);

            // Enums (case-insensitive)
            if (type.isEnum()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Class<? extends Enum> e = (Class<? extends Enum>) type;
                for (Enum<?> c : e.getEnumConstants()) {
                    if (c.name().equalsIgnoreCase(value)) {
                        return c;
                    }
                }
                @SuppressWarnings({"rawtypes", "unchecked"})
                Enum<?> parsed = Enum.valueOf((Class) e, value.toUpperCase(Locale.ROOT));
                return parsed;
            }

            throw new UsageEx(cmdForErrors, "Unsupported field type: " + type.getName());
        } catch (UsageEx e) {
            throw e;
        } catch (Exception e) {
            String displayName = preferredOptionName(opt);
            if (opt == null && param != null) {
                displayName = param.paramLabel().isEmpty() ? "<" + fieldName + ">" : param.paramLabel();
            } else if (opt == null) {
                displayName = "<" + fieldName + ">";
            }
            throw new UsageEx(cmdForErrors, "Invalid value for " + displayName + ": " + e.getMessage());
        }
    }


    static String enumCandidates(Class<?> type, me.bechberger.femtocli.annotations.Option opt, String joiner) {
        if (type == null || !type.isEnum()) return "";
        Object[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) return "";

        boolean showDescriptions = opt != null && opt.showEnumDescriptions();
        Method descMethod = null;

        if (showDescriptions) {
            try {
                descMethod = type.getDeclaredMethod("getDescription");
                descMethod.setAccessible(true);
                if (!String.class.isAssignableFrom(descMethod.getReturnType())) {
                    throw new IllegalStateException("Enum " + type.getName() +
                        " getDescription() must return String");
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Enum " + type.getName() +
                    " missing getDescription() method with showEnumDescriptions=true", e);
            }
        }

        StringJoiner sj = new StringJoiner(joiner);
        for (Object c : constants) {
            if (showDescriptions) {
                try {
                    String desc = (String) descMethod.invoke(c);
                    sj.add((desc != null && !desc.isBlank()) ? c + " (" + desc + ")" : String.valueOf(c));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke getDescription() on " + c, e);
                }
            } else {
                sj.add(String.valueOf(c));
            }
        }
        return sj.toString();
    }


    private static void injectSpec(CommandModel model,
                                   PrintStream out, PrintStream err,
                                   List<String> commandPath, CommandConfig commandConfig,
                                   List<Object> commandChain) throws Exception {
        Spec spec = new Spec(model.cmd, out, err, commandPath, commandConfig, commandChain);
        for (Field f : allFields(model.cmd.getClass())) {
            f.setAccessible(true);
            if (Spec.class.isAssignableFrom(f.getType()) && f.get(model.cmd) == null) {
                f.set(model.cmd, spec);
            }
        }
    }

    private static Method resolveMethod(String spec, Class<?> defaultClass) throws ClassNotFoundException, NoSuchMethodException {
        int hash = spec.indexOf('#');
        String classPart = hash >= 0 ? spec.substring(0, hash) : null;
        String methodPart = hash >= 0 ? spec.substring(hash + 1) : spec;

        Class<?> owner;
        if (classPart == null || classPart.isBlank()) {
            owner = Objects.requireNonNull(defaultClass, "No default class available for method: " + spec);
        } else {
            owner = resolveClass(classPart, defaultClass);
        }

        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(methodPart)) return m;
        }
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(methodPart)) return m;
        }
        throw new NoSuchMethodException("Cannot find method " + methodPart + " on " + owner.getName());
    }

    private static Class<?> resolveClass(String classPart, Class<?> defaultClass) throws ClassNotFoundException {
        if (defaultClass != null) {
            for (Class<?> c = defaultClass; c != null; c = c.getEnclosingClass()) {
                try { return Class.forName(c.getName() + "$" + classPart); }
                catch (ClassNotFoundException ignored) {}
            }
            Package p = defaultClass.getPackage();
            if (p != null && !p.getName().isBlank()) {
                try { return Class.forName(p.getName() + "." + classPart); }
                catch (ClassNotFoundException ignored) {}
            }
        }
        return Class.forName(classPart);
    }

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = Map.of(
            int.class, Integer.class, long.class, Long.class,
            double.class, Double.class, float.class, Float.class,
            boolean.class, Boolean.class, short.class, Short.class,
            byte.class, Byte.class, char.class, Character.class);

    private static boolean isPrimitiveWrapperAssignable(Class<?> targetType, Class<?> actualType) {
        return targetType != null && actualType != null
                && actualType.equals(PRIMITIVE_TO_WRAPPER.get(targetType));
    }

    private static String findSimilarOption(String invalid, Set<String> validOptions) {
        if (validOptions.isEmpty()) return null;
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String valid : validOptions) {
            // Inline Levenshtein distance
            int len1 = invalid.length(), len2 = valid.length();
            int[] prev = new int[len2 + 1], curr = new int[len2 + 1];
            for (int j = 0; j <= len2; j++) prev[j] = j;
            for (int i = 1; i <= len1; i++) {
                curr[0] = i;
                for (int j = 1; j <= len2; j++) {
                    curr[j] = invalid.charAt(i - 1) == valid.charAt(j - 1) ? prev[j - 1] :
                        1 + Math.min(prev[j], Math.min(curr[j - 1], prev[j - 1]));
                }
                int[] tmp = prev; prev = curr; curr = tmp;
            }
            if (prev[len2] < bestDist) { bestDist = prev[len2]; best = valid; }
        }
        return bestDist <= Math.max(2, invalid.length() / 2) ? best : null;
    }
}