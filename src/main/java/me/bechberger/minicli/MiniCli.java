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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Minimal reflection-based CLI runner with Java 21 features.
 */
public final class MiniCli {

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
            int exitCode = MiniCli.execute(root, out, err, args, converters, commandConfig);
            return new RunResult(outStream.toString(), errStream.toString(), exitCode);
        }
    }

    public static Builder builder() { return new Builder(); }
    private MiniCli() {}

    public static int run(Object root, PrintStream out, PrintStream err, String[] args) {
        return execute(root, out, err, args, Map.of(), new CommandConfig());
    }

    public static int run(Object root, PrintStream out, PrintStream err, String[] args,
                          Map<Class<?>, TypeConverter<?>> converters) {
        return execute(root, out, err, args, converters, new CommandConfig());
    }

    private static int execute(Object root, PrintStream out, PrintStream err, String[] args,
                               Map<Class<?>, TypeConverter<?>> converters,
                               CommandConfig commandConfig) {
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

                if (isHelp(next)) {
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig));
                    usage(cmd, out);
                    return 0;
                }
                if (isVersion(next)) {
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig));
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
                    cmd = sub.getDeclaredConstructor().newInstance();
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
                    USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig));
                    return invokeSubcommandMethod(cmd, method, tokens, converters);
                }
                break;
            }

            USAGE_CONTEXT.set(new UsageContext(List.copyOf(commandPath), commandConfig));
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

    public static void usage(Object cmd, PrintStream out) {
        UsageContext ctx = USAGE_CONTEXT.get();
        if (ctx != null) {
            // Always show the full command path (root + subcommands) in the usage line.
            usage(cmd, ctx.commandPath, ctx.commandConfig, out);
            return;
        }
        usage(cmd, List.of(commandName(cmd)), new CommandConfig(), out);
    }

    public static void usage(Object cmd, List<String> commandPath, CommandConfig commandConfig, PrintStream out) {
        HelpRenderer.render(cmd, String.join(" ", commandPath), commandConfig, out);
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

        // Parse tokens
        Set<Field> seenFields = new HashSet<>();
        Set<Field> seenFieldsWithoutValue = new HashSet<>();
        Map<Field, List<String>> multiValueFields = new HashMap<>();
        List<String> positionals = new ArrayList<>();
        boolean acceptOptions = true;

        while (!tokens.isEmpty()) {
            String token = tokens.removeFirst();

            if (isHelp(token)) {
                throw UsageEx.help(cmd);
            }
            if (isVersion(token)) {
                throw UsageEx.version();
            }
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
            // Boolean flags: presence means true
            if (isBoolean) {
                optMeta.field.set(optMeta.target, true);
                return;
            }

            // Optional-value option: allow "--opt" without a value
            if (opt != null && "0..1".equals(opt.arity())) {
                // Mark as seen, but remember that no explicit value was provided.
                seenFieldsWithoutValue.add(optMeta.field);
                return;
            }

            // Otherwise a value is required
            if (tokens.isEmpty()) {
                throw new UsageEx(cmd, "Missing value for option: " + name);
            }
            value = tokens.removeFirst();
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
            optMeta.field.set(optMeta.target, convert(value, type, optMeta.field.getName(), opt, converters));
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
                    Array.set(array, i, convert(values.get(i), componentType, field.getName(), opt, converters));
                }
                field.set(target, array);
            } else {
                field.set(target, new ArrayList<>(values));
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

            boolean shouldApply = (!seenFields.contains(field) && !opt.defaultValue().isEmpty())
                                  || (seenFieldsWithoutValue.contains(field) && !opt.defaultValue().isEmpty());

            if (shouldApply) {
                field.set(optMeta.target, convert(opt.defaultValue(), field.getType(), field.getName(), opt, converters));
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
                Array.set(array, i, convert(values.get(i), componentType, field.getName(), null, converters));
            }
            field.set(cmd, array);
        } else if (List.class.isAssignableFrom(field.getType())) {
            field.set(cmd, new ArrayList<>(values));
        } else if (!values.isEmpty()) {
            field.set(cmd, convert(values.getFirst(), field.getType(), field.getName(), null, converters));
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
            field.set(cmd, convert(value, field.getType(), field.getName(), null, converters));
            return currentIndex + 1;
        }

        if (!isOptional && param.defaultValue().isEmpty()) {
            throw new UsageEx(cmd, "Missing required parameter: " + getParamLabel(paramInfo));
        }

        if (!param.defaultValue().isEmpty()) {
            field.set(cmd, convert(param.defaultValue(), field.getType(), field.getName(), null, converters));
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
        BUILTIN_CONVERTERS = Collections.unmodifiableMap(m);
    }

    private static Object convert(String raw, Class<?> type, String fieldName, Option opt,
                                  Map<Class<?>, TypeConverter<?>> converters) throws UsageEx {
        try {
            // Option-specific converter
            if (opt != null && opt.converter() != TypeConverter.class) {
                @SuppressWarnings("unchecked")
                var converter = (TypeConverter<Object>) opt.converter().getDeclaredConstructor().newInstance();
                return converter.convert(raw);
            }

            // Global converters
            if (converters.get(type) instanceof TypeConverter<?> custom) {
                return custom.convert(raw);
            }

            // Built-in types
            if (BUILTIN_CONVERTERS.get(type) instanceof TypeConverter<?> builtin) {
                return builtin.convert(raw);
            }

            // Enum types
            if (type.isEnum()) {
                return convertEnum(type, raw);
            }
        } catch (Exception e) {
            throw new UsageEx(null, "Invalid value for " + fieldName + ": " + raw);
        }

        throw new UsageEx(null, "Unsupported field type: " + type.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertEnum(Class<?> enumType, String raw) {
        Class<? extends Enum> enumClass = (Class<? extends Enum>) enumType;
        Object[] constants = enumClass.getEnumConstants();
        for (Object c : constants) {
            Enum<?> e = (Enum<?>) c;
            if (e.name().equalsIgnoreCase(raw)) return e;
        }
        throw new IllegalArgumentException("Invalid enum value: " + raw);
    }

    static String enumCandidates(Class<?> type) {
        if (!type.isEnum()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Object[] constants = type.getEnumConstants();
        for (int i = 0; i < constants.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(((Enum<?>) constants[i]).name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
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

    private static final class UsageEx extends Exception {
        final Object cmd;
        final boolean help;
        final boolean version;

        UsageEx(Object cmd, String message) {
            super(message);
            this.cmd = cmd;
            this.help = false;
            this.version = false;
        }

        private UsageEx(Object cmd, boolean help, boolean version) {
            super("");
            this.cmd = cmd;
            this.help = help;
            this.version = version;
        }

        static UsageEx help(Object cmd) {
            return new UsageEx(cmd, true, false);
        }

        static UsageEx version() {
            return new UsageEx(null, false, true);
        }
    }

    private static final class UsageContext {
        final List<String> commandPath;
        final CommandConfig commandConfig;

        UsageContext(List<String> commandPath, CommandConfig commandConfig) {
            this.commandPath = commandPath;
            this.commandConfig = commandConfig;
        }
    }

    private static final ThreadLocal<UsageContext> USAGE_CONTEXT = new ThreadLocal<>();
}