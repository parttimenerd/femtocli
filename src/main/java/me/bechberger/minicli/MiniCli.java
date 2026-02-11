package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

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
public final class MiniCli {

    static final String NO_DEFAULT_VALUE = "__NO_DEFAULT_VALUE__";

    /** Builder for configuring MiniCli with custom type handlers. */
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
            return MiniCli.execute(root, out, err, args, converters, commandConfig);
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
            int exitCode = MiniCli.execute(root, out, err, args, converters, commandConfig);
            System.setOut(oldOut);
            System.setErr(oldErr);
            return new RunResult(outStream.toString(), errStream.toString(), exitCode);
        }
    }

    public static Builder builder() { return new Builder(); }
    private MiniCli() {}

    /**
     * Run the CLI with the given root command object and arguments,
     * and use the passed output and error streams for MiniCli output.
     */
    public static int run(Object root, PrintStream out, PrintStream err, String[] args) {
        return execute(root, out, err, args, Map.of(), new CommandConfig(), false);
    }

    public static RunResult runCaptured(Object root, String[] args) {
        return builder().runCaptured(root, args);
    }

    public static int run(Object root, String[] args) {
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

    private static int execute(Object root, PrintStream out, PrintStream err, String[] args,
                               Map<Class<?>, TypeConverter<?>> converters,
                               CommandConfig commandConfig,
                               boolean agentMode) {
        UsageContext previous = USAGE_CONTEXT.get();
        try {
            var tokens = new ArrayDeque<>(Arrays.asList(args));
            Object cmd = root;

            // Track fully-qualified command path for help output
            List<String> commandPath = new ArrayList<>();
            commandPath.add(commandName(root));

            // Resolve subcommand chain
            while (!tokens.isEmpty()) {
                String next = tokens.peekFirst();

                // In agent mode, allow bare help/version tokens at any depth.
                if (agentMode && ("help".equals(next) || "version".equals(next))) {
                    next = "help".equals(next) ? "--help" : "--version";
                }

                if (isHelp(next)) {
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode));
                    usage(cmd, out);
                    return 0;
                }
                if (isVersion(next)) {
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode));
                    version(root, out);
                    return 0;
                }
                if (next.startsWith("-")) {
                    break;
                }

                // Check for subcommand class
                Class<?> sub = findSubcommand(cmd.getClass(), next);
                if (sub != null) {
                    tokens.removeFirst();
                    var ctor = sub.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    cmd = ctor.newInstance();
                    commandPath.add(commandName(cmd));
                    continue;
                }

                // Check for @Command method
                Method method = findSubcommandMethod(cmd.getClass(), next);
                if (method != null) {
                    tokens.removeFirst();
                    Command mc = method.getAnnotation(Command.class);
                    if (mc != null && !mc.name().isBlank()) {
                        commandPath.add(mc.name());
                    }
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode));
                    return invokeSubcommandMethod(cmd, method, tokens, converters);
                }
                break;
            }

            USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig, agentMode));

            CommandModel model = CommandModel.of(cmd);
            injectSpec(model, out, err, List.copyOf(commandPath), commandConfig);

            if (agentMode) {
                normalizeBareOptionTokens(cmd, tokens, model);
            }

            parseInto(cmd, tokens, converters);
            return invoke(cmd);

        } catch (UsageEx e) {
            Object target = e.cmd != null ? e.cmd : root;
            if (e.help) {
                usage(target, out);
                return 0;
            }
            if (e.version) {
                version(root, out);
                return 0;
            }
            err.println("Error: " + e.getMessage());
            usage(target, out);
            return 2;
        } catch (FieldIsFinalException e) {
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
        }
    }

    /**
     * Allow "bare" option names without leading dashes (useful for agent-args or restricted environments).
     * <p>
     * Example: "req=x" is normalized to "--req=x" if "--req" is a known option for the current command.
     * If multiple options match, we fail early with a helpful error.
     */
    private static void normalizeBareOptionTokens(Object cmdForErrors, Deque<String> tokens, CommandModel model) throws UsageEx {
        if (tokens.isEmpty() || model == null) {
            return;
        }
        // Copy to allow in-place update
        List<String> normalized = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            String nt = normalizeBareOptionToken(cmdForErrors, t, model);
            normalized.add(nt);
        }
        tokens.clear();
        tokens.addAll(normalized);
    }

    private static String normalizeBareOptionToken(Object cmdForErrors, String token, CommandModel model) throws UsageEx {
        if (token == null || token.isEmpty()) {
            return token;
        }
        if (token.startsWith("-")) {
            return token;
        }
        // help/version as bare tokens (besides regular --help/-h and --version/-V)
        if ("help".equals(token)) {
            return "--help";
        }
        if ("version".equals(token)) {
            return "--version";
        }

        // Support bare boolean flags without '=' in agent mode, if unambiguous and boolean.
        // Example: "flag" is normalized to "--flag" if --flag is a known boolean option.
        if (!token.contains("=")) {
            List<String> candidates = new ArrayList<>();
            for (var e : model.optionsByName.entrySet()) {
                String optName = e.getKey();
                MiniCli.OptionMeta meta = e.getValue();
                if (!MiniCli.isBooleanType(meta.field.getType())) {
                    continue;
                }
                if (optName.startsWith("--") && optName.substring(2).equals(token)) {
                    candidates.add(optName);
                }
                if (optName.startsWith("-") && !optName.startsWith("--") && optName.substring(1).equals(token)) {
                    candidates.add(optName);
                }
            }
            if (candidates.size() == 1) {
                return candidates.getFirst();
            }
            if (candidates.size() > 1) {
                throw new UsageEx(cmdForErrors,
                        "Ambiguous bare option '" + token + "' (use full name): " + String.join(", ", candidates));
            }
            return token;
        }

        // Only treat tokens containing '=' as potential bare options to avoid interfering with positionals/subcommands.
        int eq = token.indexOf('=');
        if (eq < 0) {
            return token;
        }
        String bareName = token.substring(0, eq);
        if (bareName.isBlank()) {
            return token;
        }

        List<String> candidates = new ArrayList<>();
        MiniCli.OptionMeta firstMeta = null;
        for (var e : model.optionsByName.entrySet()) {
            String optName = e.getKey();
            if (!(optName.startsWith("--") || (optName.startsWith("-") && !optName.startsWith("--")))) {
                continue;
            }
            String stripped = optName.startsWith("--") ? optName.substring(2) : optName.substring(1);
            if (!stripped.equals(bareName)) {
                continue;
            }
            MiniCli.OptionMeta meta = e.getValue();
            if (firstMeta == null) {
                firstMeta = meta;
                candidates.add(optName);
            } else {
                // If this candidate points to the same underlying option field, accept it as an alias.
                if (meta.field.equals(firstMeta.field)) {
                    candidates.add(optName);
                } else {
                    candidates.add(optName);
                }
            }
        }

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
                return candidates.getFirst() + token.substring(eq);
            }

            throw new UsageEx(cmdForErrors,
                    "Ambiguous bare option '" + bareName + "' (use full name): " + String.join(", ", candidates));
        }
        return candidates.getFirst() + token.substring(eq);
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

    private static boolean looksLikeExplicitBooleanValue(String s) {
        return ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s));
    }

    private static boolean isHelp(String t) { return "--help".equals(t) || "-h".equals(t); }
    private static boolean isVersion(String t) { return "--version".equals(t) || "-V".equals(t); }

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

    public static void usage(Object cmd, List<String> commandPath, CommandConfig commandConfig, PrintStream out) {
        usage(cmd, commandPath, commandConfig, out, false);
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
        injectSpecFromContext(model);

        // Parse tokens
        Set<Field> seenFields = new HashSet<>();
        Set<Field> seenFieldsWithoutValue = new HashSet<>();
        Map<Field, List<String>> multiValueFields = new HashMap<>();
        List<String> positionals = new ArrayList<>();
        boolean acceptOptions = true;

        while (!tokens.isEmpty()) {
            String token = tokens.removeFirst();
            throwIfHelpOrVersion(cmd, token);
            if (acceptOptions && "--".equals(token)) {
                acceptOptions = false;
                continue;
            }

            if (acceptOptions && token.startsWith("-")) {
                parseOption(model, cmd, token, tokens, seenFields, seenFieldsWithoutValue, multiValueFields, converters);
            } else {
                positionals.add(token);
            }
        }

        // Apply multi-value fields
        applyMultiValueFields(model, multiValueFields, converters);

        // Apply default values for unseen options
        applyDefaultValues(model, seenFields, seenFieldsWithoutValue, converters);

        // Bind positionals based on index/arity
        bindPositionals(cmd, positionals, model.parameters, converters);

        // Validate required options
        validateRequiredOptions(cmd, model.options, seenFields);
    }

    private static void throwIfHelpOrVersion(Object cmd, String token) throws UsageEx {
        if (isHelp(token)) {
            throw UsageEx.help(cmd);
        }
        if (isVersion(token)) {
            throw UsageEx.version();
        }
    }

    private static void injectSpecFromContext(CommandModel model) throws Exception {
        UsageContext ctx = USAGE_CONTEXT.get();
        if (ctx != null) {
            injectSpec(model, System.out, System.err, ctx.commandPath, ctx.commandConfig);
        } else {
            injectSpec(model, System.out, System.err, List.of(commandName(model.cmd)), new CommandConfig());
        }
    }

    private static Object invokeConverterMethod(String spec, Object cmd, String raw, Class<?> targetType) throws Exception {
        Method m = resolveMethod(spec, cmd != null ? cmd.getClass() : null);
        Object receiver = methodReceiverOrThrow(m, cmd, "Converter", spec);

        // Expect single String parameter
        if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != String.class) {
            throw new IllegalArgumentException("Converter method must take a single String argument: " + spec);
        }
        m.setAccessible(true);
        Object result = m.invoke(receiver, raw);
        if (result == null) return null;
        if (targetType.isInstance(result)) return result;
        if (isPrimitiveWrapperAssignable(targetType, result.getClass())) return result;
        throw new IllegalArgumentException("Converter method returned incompatible type for " + spec);
    }

    private static Object methodReceiverOrThrow(Method m, Object instance, String kind, String spec) {
        boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
        if (isStatic) {
            return null;
        }
        if (instance == null) {
            throw new IllegalArgumentException(kind + " method requires a command instance: " + spec);
        }
        return instance;
    }

    private static void invokeSingleArgMethod(String spec, Object cmdForErrors, Object value) throws Exception {
        Method m = resolveMethod(spec, cmdForErrors != null ? cmdForErrors.getClass() : null);
        Object receiver = methodReceiverOrThrow(m, cmdForErrors, "Verifier", spec);
        if (m.getParameterCount() != 1) {
            throw new IllegalArgumentException("Verifier method must take a single argument: " + spec);
        }
        m.setAccessible(true);
        m.invoke(receiver, value);
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
                                    Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        int eqIndex = token.indexOf('=');
        String name = eqIndex >= 0 ? token.substring(0, eqIndex) : token;
        String value = eqIndex >= 0 ? token.substring(eqIndex + 1) : null;

        OptionMeta optMeta = model.optionsByName.get(name);
        if (optMeta == null) {
            throw new UsageEx(cmd, "Unknown option: " + name);
        }
        seenFields.add(optMeta.field);

        Option opt = optMeta.opt;

        Class<?> type = optMeta.field.getType();
        boolean isBoolean = isBooleanType(type);

        // If no explicit value is provided (no "=...")
        if (value == null) {
            // Boolean flags: presence means true, but allow an explicit boolean value as the next token.
            if (isBoolean) {
                if (!tokens.isEmpty() && looksLikeExplicitBooleanValue(tokens.peekFirst())) {
                    value = tokens.removeFirst();
                } else {
                    optMeta.field.set(optMeta.target, true);
                    return;
                }
            }

            // Optional-value option: allow "--opt" without a value
            if (value == null && opt != null && "0..1".equals(opt.arity())) {
                // Mark as seen, but remember that no explicit value was provided.
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
                Class<?> componentType = type.getComponentType();
                Object array = Array.newInstance(componentType, values.size());
                for (int i = 0; i < values.size(); i++) {
                    Object converted = convert(values.get(i), componentType, field.getName(), opt, converters, model.cmd);
                    runVerifiers(model.cmd, converted, opt, null);
                    Array.set(array, i, converted);
                }
                field.set(target, array);
            } else {
                // For lists we keep raw strings converted by caller's registered converters when binding positionals or options
                List<Object> list = new ArrayList<>();
                for (String v : values) {
                    Object converted = convert(v, String.class, field.getName(), opt, converters, model.cmd);
                    runVerifiers(model.cmd, converted, opt, null);
                    list.add(converted);
                }
                field.set(target, list);
            }
        }
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

    /** Parse range like "0", "0..1", "0..*", "2..*" into [start, end] where -1 means unbounded. */
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

    /* internal structs (single definitions) */

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

    private static final class ArityBounds {
        final int min;
        final int max;

        ArityBounds(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    private static void bindPositionals(Object cmd, List<String> positionals, List<ParamInfo> paramInfos,
                                        Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        if (paramInfos.isEmpty()) {
            if (!positionals.isEmpty()) {
                throw new UsageEx(cmd, "Unexpected parameter: " + positionals.getFirst());
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

            ArityBounds arity = determineArity(paramInfo, isVarargs);

            if (isVarargs) {
                currentIndex = bindVarargsParam(cmd, field, positionals, currentIndex, arity, paramInfo, converters);
            } else {
                currentIndex = bindSingleParam(cmd, field, param, positionals, currentIndex, arity, paramInfo, converters);
            }
        }

        // Check for extra positionals
        if (currentIndex < positionals.size()) {
            ParamInfo lastParam = paramInfos.getLast();
            boolean lastIsVarargs = lastParam.indexRange[1] == -1
                                    || lastParam.field.getType().isArray()
                                    || List.class.isAssignableFrom(lastParam.field.getType());

            if (!lastIsVarargs) {
                throw new UsageEx(cmd, "Too many parameters");
            }
        }
    }

    private static ArityBounds determineArity(ParamInfo paramInfo, boolean isVarargs) {
        int[] arityRange = paramInfo.arityRange;

        if (arityRange[0] == -2) {
            // Arity not specified - infer from type
            return isVarargs
                    ? new ArityBounds(0, Integer.MAX_VALUE)
                    : new ArityBounds(1, 1);
        }

        int minArity = arityRange[0];
        int maxArity = arityRange[1] == -1 ? Integer.MAX_VALUE : arityRange[1];
        return new ArityBounds(minArity, maxArity);
    }

    private static int bindVarargsParam(Object cmd, Field field, List<String> positionals,
                                        int startIndex, ArityBounds arity, ParamInfo paramInfo,
                                        Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        int available = positionals.size() - startIndex;
        int toConsume = Math.min(available, arity.max);
        List<String> values = positionals.subList(startIndex, startIndex + toConsume);

        if (values.size() < arity.min) {
            throw new UsageEx(cmd, "Missing required parameter: " + getParamLabel(paramInfo));
        }

        if (field.getType().isArray()) {
            Class<?> componentType = field.getType().getComponentType();
            Object array = Array.newInstance(componentType, values.size());
            for (int i = 0; i < values.size(); i++) {
                Object converted = convert(values.get(i), componentType, field.getName(), null, converters, cmd);
                runVerifiers(cmd, converted, null, paramInfo.param);
                Array.set(array, i, converted);
            }
            field.set(cmd, array);
        } else if (List.class.isAssignableFrom(field.getType())) {
            List<Object> list = new ArrayList<>();
            for (String v : values) {
                Object converted = convert(v, String.class, field.getName(), null, converters, cmd);
                runVerifiers(cmd, converted, null, paramInfo.param);
                list.add(converted);
            }
            field.set(cmd, list);
        } else if (!values.isEmpty()) {
            Object converted = convert(values.getFirst(), field.getType(), field.getName(), null, converters, cmd);
            runVerifiers(cmd, converted, null, paramInfo.param);
            field.set(cmd, converted);
        }

        return startIndex + toConsume;
    }

    private static int bindSingleParam(Object cmd, Field field, Parameters param,
                                       List<String> positionals, int currentIndex,
                                       ArityBounds arity, ParamInfo paramInfo,
                                       Map<Class<?>, TypeConverter<?>> converters) throws Exception {
        boolean isOptional = arity.min == 0 || "0..1".equals(param.arity());

        if (currentIndex < positionals.size()) {
            String value = positionals.get(currentIndex);
            Object converted = convert(value, field.getType(), field.getName(), null, converters, cmd);
            runVerifiers(cmd, converted, null, param);
            field.set(cmd, converted);
            return currentIndex + 1;
        }

        if (!isOptional && param.defaultValue().equals(NO_DEFAULT_VALUE)) {
            throw new UsageEx(cmd, "Missing required parameter: " + getParamLabel(paramInfo));
        }

        if (!param.defaultValue().equals(NO_DEFAULT_VALUE)) {
            Object converted = convert(param.defaultValue(), field.getType(), field.getName(), null, converters, cmd);
            runVerifiers(cmd, converted, null, param);
            field.set(cmd, converted);
        }

        return currentIndex;
    }

    private static String getParamLabel(ParamInfo pi) {
        return pi.param.paramLabel().isEmpty() ? "<" + pi.field.getName() + ">" : pi.param.paramLabel();
    }

    // Collecting fields/params is now centralized in CommandModel

    /* Builtins (static initializer to avoid Map.ofEntries bootstrap) */
    private static final Map<Class<?>, TypeConverter<?>> BUILTIN_CONVERTERS;
    static {
        Map<Class<?>, TypeConverter<?>> m = new HashMap<>();
        m.put(String.class, (TypeConverter<String>) s -> s);
        m.put(int.class, (TypeConverter<Integer>) Integer::parseInt);
        m.put(Integer.class, (TypeConverter<Integer>) Integer::parseInt);
        m.put(long.class, (TypeConverter<Long>) Long::parseLong);
        m.put(Long.class, (TypeConverter<Long>) Long::parseLong);
        m.put(double.class, (TypeConverter<Double>) Double::parseDouble);
        m.put(Double.class, (TypeConverter<Double>) Double::parseDouble);
        m.put(float.class, (TypeConverter<Float>) Float::parseFloat);
        m.put(Float.class, (TypeConverter<Float>) Float::parseFloat);
        m.put(boolean.class, (TypeConverter<Boolean>) Boolean::parseBoolean);
        m.put(Boolean.class, (TypeConverter<Boolean>) Boolean::parseBoolean);
        m.put(Path.class, (TypeConverter<Path>) Path::of);
        m.put(Duration.class, (TypeConverter<Duration>) MiniCli::parseDuration);
        BUILTIN_CONVERTERS = Collections.unmodifiableMap(m);
    }

    /**
     * Parse duration a short human format like
     * "400ms", "4.5s", "2m", "1h", "1d"
     */
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

    private static Class<?> findSubcommand(Class<?> cmdClass, String name) {
        Command ann = cmdClass.getAnnotation(Command.class);
        if (ann == null) return null;
        for (Class<?> sub : ann.subcommands()) {
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
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
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

    /**
     * Converts a single string value to the requested Java type.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Custom converters provided by the Builder / run overload</li>
     *   <li>Built-in converters (primitives, boxed types, Path, Duration, ...)</li>
     *   <li>Enum types (case-insensitive)</li>
     * </ol>
     */
    private static Object convert(String value,
                                  Class<?> type,
                                  String fieldName,
                                  Option opt,
                                  Map<Class<?>, TypeConverter<?>> converters,
                                  Object cmdForErrors) throws UsageEx {
        try {
            // 0) Per-option converter method
            if (opt != null && !opt.converterMethod().isBlank()) {
                return invokeConverterMethod(opt.converterMethod(), cmdForErrors, value, type);
            }

            // 1) Per-option converter class
            if (opt != null && opt.converter() != TypeConverter.NullTypeConverter.class) {
                TypeConverter<?> perOpt = opt.converter().getDeclaredConstructor().newInstance();
                return perOpt.convert(value);
            }

            // 2) Custom converter
            TypeConverter<?> custom = converters.get(type);
            if (custom != null) {
                return custom.convert(value);
            }

            // 3) Built-in converter
            TypeConverter<?> builtin = BUILTIN_CONVERTERS.get(type);
            if (builtin != null) {
                return builtin.convert(value);
            }

            // 4) Enums (case-insensitive)
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
            if (opt == null) {
                displayName = "<" + fieldName + ">";
            }
            throw new UsageEx(cmdForErrors, "Invalid value for " + displayName + ": " + value);
        }
    }

    /**
     * Used by help placeholder ${COMPLETION-CANDIDATES}.
     */
    static String enumCandidates(Class<?> type) {
        if (type == null || !type.isEnum()) {
            return "";
        }
        Object[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Object c : constants) {
            joiner.add(String.valueOf(c));
        }
        return joiner.toString();
    }

    private static void injectSpec(CommandModel model,
                                   PrintStream out,
                                   PrintStream err,
                                   List<String> commandPath,
                                   CommandConfig commandConfig) throws Exception {
        me.bechberger.minicli.Spec spec = new me.bechberger.minicli.Spec(model.cmd, out, err, commandPath, commandConfig);
        for (Field f : allFields(model.cmd.getClass())) {
            f.setAccessible(true);
            if (!me.bechberger.minicli.Spec.class.isAssignableFrom(f.getType())) {
                continue;
            }
            if (f.get(model.cmd) != null) {
                continue;
            }
            f.set(model.cmd, spec);
        }
    }

    private static Method resolveMethod(String spec, Class<?> defaultClass) throws ClassNotFoundException, NoSuchMethodException {
        String classPart;
        String methodPart;
        if (spec.contains("#")) {
            String[] parts = spec.split("#", 2);
            classPart = parts[0];
            methodPart = parts[1];
        } else {
            classPart = null;
            methodPart = spec;
        }

        Class<?> owner = (classPart == null || classPart.isBlank())
                ? Objects.requireNonNull(defaultClass, "No default class available for method: " + spec)
                : Class.forName(classPart);

        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(methodPart)) {
                return m;
            }
        }
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(methodPart)) {
                return m;
            }
        }
        throw new NoSuchMethodException("Cannot find method " + methodPart + " on " + owner.getName());
    }

    private static boolean isPrimitiveWrapperAssignable(Class<?> targetType, Class<?> actualType) {
        if (targetType == null || actualType == null) return false;
        if (!targetType.isPrimitive()) return false;
        return (targetType == int.class && actualType == Integer.class)
                || (targetType == long.class && actualType == Long.class)
                || (targetType == double.class && actualType == Double.class)
                || (targetType == float.class && actualType == Float.class)
                || (targetType == boolean.class && actualType == Boolean.class)
                || (targetType == short.class && actualType == Short.class)
                || (targetType == byte.class && actualType == Byte.class)
                || (targetType == char.class && actualType == Character.class);
    }
}