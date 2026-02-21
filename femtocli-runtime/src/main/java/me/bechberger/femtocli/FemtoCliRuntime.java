package me.bechberger.femtocli;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Shared runtime helpers called by generated parser code.
 * <p>
 * Keeps the generated parsers small by centralising common logic
 * such as type conversion, "did you mean?" suggestions, and error factories.
 * <p>
 * <strong>Not part of the public API</strong> — generated code only.
 */
public final class FemtoCliRuntime {

    public static final String NO_DEFAULT_VALUE = "__NO_DEFAULT_VALUE__";

    private FemtoCliRuntime() {}

    // ---- Built-in type converters ----

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
        m.put(Duration.class, (TypeConverter<Duration>) FemtoCliRuntime::parseDuration);
        BUILTIN_CONVERTERS = Collections.unmodifiableMap(m);
    }

    // ---- Public conversion API ----

    /**
     * Convert a raw string to the target type using:
     * <ol>
     *   <li>User-registered converters (from Builder)</li>
     *   <li>Built-in converters</li>
     *   <li>Enum matching (case-insensitive)</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    public static Object convert(String value, Class<?> type,
                                 Map<Class<?>, TypeConverter<?>> customConverters) {
        // 1) custom converter
        if (customConverters != null) {
            TypeConverter<?> custom = customConverters.get(type);
            if (custom != null) return custom.convert(value);
        }
        // 2) built-in
        TypeConverter<?> builtin = BUILTIN_CONVERTERS.get(type);
        if (builtin != null) return builtin.convert(value);
        // 3) enum (case-insensitive)
        if (type.isEnum()) {
            return convertEnum(value, (Class<? extends Enum<?>>) type);
        }
        throw new IllegalArgumentException("Unsupported field type: " + type.getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object convertEnum(String value, Class<? extends Enum<?>> enumType) {
        for (Enum<?> c : enumType.getEnumConstants()) {
            if (c.name().equalsIgnoreCase(value)) return c;
        }
        return Enum.valueOf((Class) enumType, value.toUpperCase(Locale.ROOT));
    }

    // ---- Duration parsing ----

    /**
     * Parse a short human-readable duration, e.g. "400ms", "4.5s", "2m", "1h", "1d".
     */
    public static Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be null or empty");
        }
        String s = raw.trim();
        int i = 0;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || c == '\u00b5') break;
        }
        String num = s.substring(0, i).trim();
        String unit = s.substring(i).trim();
        long nanosPerUnit = switch (unit) {
            case "ns" -> 1L;
            case "us" -> 1_000L;
            case "ms" -> 1_000_000L;
            case "s"  -> 1_000_000_000L;
            case "m"  -> 60_000_000_000L;
            case "h"  -> 3_600_000_000_000L;
            case "d"  -> 86_400_000_000_000L;
            default -> throw new IllegalArgumentException("Invalid duration unit: " + unit);
        };
        return Duration.ofNanos(Math.round(Double.parseDouble(num) * nanosPerUnit));
    }

    // ---- Boolean helpers ----

    public static boolean looksLikeExplicitBooleanValue(String s) {
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
    }

    public static boolean isBooleanType(Class<?> c) {
        return c == boolean.class || c == Boolean.class;
    }

    // ---- Help/version token detection ----

    public static boolean isHelp(String t) {
        return "--help".equals(t) || "-h".equals(t);
    }

    public static boolean isVersion(String t) {
        return "--version".equals(t) || "-V".equals(t);
    }

    // ---- "Did you mean?" suggestions ----

    /**
     * Build an error message for an unknown option, optionally including a
     * "did you mean?" suggestion.
     */
    public static String unknownOptionMessage(String name, Set<String> validNames,
                                              CommandConfig config) {
        String msg = "Unknown option: " + name;
        if (config.suggestSimilarOptions) {
            String suggestion = findSimilarOption(name, validNames);
            if (suggestion != null) {
                msg += "\n" + config.similarOptionsSuggestionTemplate
                        .replace("${SUGGESTION}", suggestion);
            }
        }
        return msg;
    }

    public static String findSimilarOption(String invalidOption, Set<String> validOptions) {
        if (validOptions.isEmpty()) return null;
        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String valid : validOptions) {
            int d = levenshteinDistance(invalidOption, valid);
            if (d < bestDistance) { bestDistance = d; bestMatch = valid; }
        }
        return bestDistance <= Math.max(2, invalidOption.length() / 2) ? bestMatch : null;
    }

    public static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length(), len2 = s2.length();
        int[] prev = new int[len2 + 1], curr = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) prev[j] = j;
        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                curr[j] = s1.charAt(i - 1) == s2.charAt(j - 1)
                        ? prev[j - 1]
                        : 1 + Math.min(prev[j], Math.min(curr[j - 1], prev[j - 1]));
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[len2];
    }

    // ---- Error factories ----

    /**
     * Create a {@link UsageEx} for errors during parsing.
     * Public so generated code can throw usage errors.
     */
    public static UsageEx usageError(Object cmd, String message) {
        return new UsageEx(cmd, message);
    }

    public static UsageEx helpRequest(Object cmd) {
        return UsageEx.help(cmd);
    }

    public static UsageEx versionRequest() {
        return UsageEx.version();
    }

    /**
     * Throw if the token is --help/-h or --version/-V.
     */
    public static void throwIfHelpOrVersion(Object cmd, String token) throws UsageEx {
        if (isHelp(token)) throw UsageEx.help(cmd);
        if (isVersion(token)) throw UsageEx.version();
    }

    // ---- Agent-mode bare option normalisation helpers ----

    public static String normalizeBareHelpOrVersionToken(String token) {
        if ("help".equals(token)) return "--help";
        if ("version".equals(token)) return "--version";
        return token;
    }

    // ---- Range parsing ----

    /** Parse "0", "0..1", "0..*", "2..*" into [start, end] where -1 means unbounded. */
    public static int[] parseRange(String range) {
        if (range == null || range.isEmpty()) return new int[]{-2, -2};
        if (range.contains("..")) {
            String[] parts = range.split("\\.\\.");
            int start = Integer.parseInt(parts[0]);
            int end = "*".equals(parts[1]) ? -1 : Integer.parseInt(parts[1]);
            return new int[]{start, end};
        }
        int idx = Integer.parseInt(range);
        return new int[]{idx, idx};
    }

    // ---- Parser loading (single remaining reflective call) ----

    @SuppressWarnings("unchecked")
    public static <T> CommandParser<T> loadParser(Class<T> commandClass) {
        String parserName = commandClass.getName() + "CommandParser";
        try {
            Class<?> parserClass = Class.forName(parserName, true, commandClass.getClassLoader());
            Object instance = parserClass.getDeclaredConstructor().newInstance();
            return (CommandParser<T>) instance;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing generated parser: " + parserName +
                    ". Ensure the femtocli-processor annotation processor ran during compilation.", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate parser: " + parserName, e);
        }
    }
}
