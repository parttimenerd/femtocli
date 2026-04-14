package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import me.bechberger.femtocli.annotations.IgnoreOptions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.util.*;

final class CommandModel {

    final Object cmd;
    final Map<String, FemtoCli.OptionMeta> optionsByName;
    final Map<Field, FemtoCli.OptionMeta> optionByField;
    final List<FemtoCli.OptionMeta> options;
    final List<FemtoCli.ParamInfo> parameters;
    /** Populated by FemtoCli.parseOptions() for later required-option validation. */
    Set<Field> seenFields;
    /** Fields explicitly provided by the user (before default values are applied). */
    Set<Field> userProvidedFields;
    /** Set to true by FemtoCli.parseOptions() when a '--' end-of-options marker is consumed. */
    boolean endOfOptionsSeen;

    private CommandModel(Object cmd,
                         Map<String, FemtoCli.OptionMeta> optionsByName,
                         Map<Field, FemtoCli.OptionMeta> optionByField,
                         List<FemtoCli.OptionMeta> options,
                         List<FemtoCli.ParamInfo> parameters) {
        this.cmd = cmd;
        this.optionsByName = optionsByName;
        this.optionByField = optionByField;
        this.options = options;
        this.parameters = parameters;
    }

    private static void initializeMixins(Object cmd) throws Exception {
        for (Field field : FemtoCli.allFields(cmd.getClass())) {
            if (field.getAnnotation(Mixin.class) != null) {
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new FieldIsFinalException("@Mixin field must not be static: " + field);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new FieldIsFinalException("@Mixin field must not be final: " + field);
                }
                field.setAccessible(true);
                if (field.get(cmd) == null) {
                    Constructor<?> ctor = field.getType().getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object mixin = ctor.newInstance();
                    field.set(cmd, mixin);
                }
            }
        }
    }

    private static void registerOptionMeta(FemtoCli.OptionMeta meta,
                                           Map<String, FemtoCli.OptionMeta> optionsByName,
                                           Map<Field, FemtoCli.OptionMeta> optionByField,
                                           List<FemtoCli.OptionMeta> options) {
        List<FemtoCli.OptionMeta> overridden = new ArrayList<>();
        for (String name : meta.opt.names()) {
            FemtoCli.OptionMeta previous = optionsByName.get(name);
            if (previous != null && previous.field != meta.field && !overridden.contains(previous)) {
                overridden.add(previous);
            }
        }

        for (FemtoCli.OptionMeta previous : overridden) {
            optionByField.remove(previous.field);
            options.remove(previous);
            for (Iterator<Map.Entry<String, FemtoCli.OptionMeta>> it = optionsByName.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue() == previous) {
                    it.remove();
                }
            }
        }

        options.add(meta);
        optionByField.put(meta.field, meta);

        for (String name : meta.opt.names()) {
            optionsByName.put(name, meta);
        }
    }

    private static boolean matchesAnyRule(String[] rules, Field field, Option opt) {
        if (rules == null) return false;
        for (String rule : rules) {
            if (rule == null || rule.isBlank()) continue;
            if (rule.startsWith("field:")) {
                if (field.getName().equals(rule.substring(6))) return true;
            } else {
                for (String n : opt.names()) {
                    if (n.equals(rule)) return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldIncludeOption(IgnoreOptions ignore, Field field, Option opt) {
        if (ignore == null) return true;
        if (ignore.ignoreAll()) {
            // ignoreAll means: only include if explicitly included
            return matchesAnyRule(ignore.include(), field, opt);
        }

        // include wins over exclude
        if (matchesAnyRule(ignore.include(), field, opt)) return true;

        if (matchesAnyRule(ignore.exclude(), field, opt)) return false;
        //noinspection deprecation
        return !matchesAnyRule(ignore.options(), field, opt);
    }


    private static void addDeclaredOptions(Object holder,
                                          Class<?> declaredIn,
                                          IgnoreOptions ignore,
                                          Map<String, FemtoCli.OptionMeta> optionsByName,
                                          Map<Field, FemtoCli.OptionMeta> optionByField,
                                          List<FemtoCli.OptionMeta> options) {
        for (Field field : declaredIn.getDeclaredFields()) {
            Option opt = field.getAnnotation(Option.class);
            if (opt == null) continue;
            field.setAccessible(true);
            if (Modifier.isFinal(field.getModifiers())) {
                throw new FieldIsFinalException("@Option field must not be final: " + field);
            }
            if (!shouldIncludeOption(ignore, field, opt)) {
                continue;
            }
            registerOptionMeta(new FemtoCli.OptionMeta(field, holder, opt), optionsByName, optionByField, options);
        }
    }

    private static void collectOptionsFrom(Object holder,
                                          Map<String, FemtoCli.OptionMeta> optionsByName,
                                          Map<Field, FemtoCli.OptionMeta> optionByField,
                                          List<FemtoCli.OptionMeta> options) {
        IgnoreOptions ignore = holder.getClass().getAnnotation(IgnoreOptions.class);

        // inherited first (older classes first), then declared: declared overrides inherited
        Class<?> type = holder.getClass();
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type.getSuperclass(); current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(current);
        }
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            addDeclaredOptions(holder, hierarchy.get(i), ignore, optionsByName, optionByField, options);
        }
        addDeclaredOptions(holder, type, ignore, optionsByName, optionByField, options);
    }


    static CommandModel of(Object cmd) throws Exception {
        initializeMixins(cmd);


        Map<String, FemtoCli.OptionMeta> optionsByName = new LinkedHashMap<>();
        Map<Field, FemtoCli.OptionMeta> optionByField = new LinkedHashMap<>();
        List<FemtoCli.OptionMeta> options = new ArrayList<>();

        // Collect mixin options first, then command options (so command overrides same-name options)
        for (Field field : FemtoCli.allFields(cmd.getClass())) {
            if (field.getAnnotation(Mixin.class) != null) {
                field.setAccessible(true);
                if (field.get(cmd) != null) {
                    collectOptionsFrom(field.get(cmd), optionsByName, optionByField, options);
                }
            }
        }
        collectOptionsFrom(cmd, optionsByName, optionByField, options);

        List<FemtoCli.ParamInfo> params = new ArrayList<>();
        // Collect @Parameters from mixin objects first
        for (Field field : FemtoCli.allFields(cmd.getClass())) {
            if (field.getAnnotation(Mixin.class) != null) {
                field.setAccessible(true);
                Object mixin = field.get(cmd);
                if (mixin != null) {
                    collectParameters(mixin, mixin, params);
                }
            }
        }
        // Then collect @Parameters from command itself
        collectParameters(cmd, cmd, params);
        params.sort((a, b) -> Integer.compare(a.indexRange[0], b.indexRange[0]));

        // Detect duplicate/overlapping scalar @Parameters indices
        validateParameterIndices(params);

        return new CommandModel(cmd, optionsByName, optionByField, options, params);
    }

    private static void collectParameters(Object holder, Object target, List<FemtoCli.ParamInfo> params) {
        for (Field f : FemtoCli.allFields(holder.getClass())) {
            Parameters p = f.getAnnotation(Parameters.class);
            if (p != null) {
                if (Modifier.isFinal(f.getModifiers())) {
                    throw new FieldIsFinalException("@Parameters field must not be final: " + f);
                }
                f.setAccessible(true);
                params.add(new FemtoCli.ParamInfo(f, target, p, FemtoCli.parseRange(p.index()), FemtoCli.parseRange(p.arity())));
            }
        }
    }

    /**
     * Detects duplicate or overlapping @Parameters index declarations.
     * Two scalar parameters with the same fixed index, or two parameters whose index
     * ranges overlap, are a configuration error and should fail fast.
     */
    private static void validateParameterIndices(List<FemtoCli.ParamInfo> params) {
        for (int i = 0; i < params.size(); i++) {
            for (int j = i + 1; j < params.size(); j++) {
                FemtoCli.ParamInfo a = params.get(i);
                FemtoCli.ParamInfo b = params.get(j);
                // Skip if either has unspecified index (marker -2)
                if (a.indexRange[0] < 0 || b.indexRange[0] < 0) continue;
                // Skip if they refer to the same field (e.g. inherited)
                if (a.field.equals(b.field)) continue;
                if (rangesOverlap(a.indexRange, b.indexRange)) {
                    String labelA = a.param.paramLabel().isEmpty() ? a.field.getName() : a.param.paramLabel();
                    String labelB = b.param.paramLabel().isEmpty() ? b.field.getName() : b.param.paramLabel();
                    throw new IllegalArgumentException(
                            "Overlapping @Parameters index: fields '" + labelA + "' and '" + labelB
                                    + "' both claim index " + formatRange(a.indexRange) + " / " + formatRange(b.indexRange));
                }
            }
        }
    }

    private static boolean rangesOverlap(int[] a, int[] b) {
        int aStart = a[0];
        int aEnd = a[1] < 0 ? Integer.MAX_VALUE : a[1]; // -1 means unbounded
        int bStart = b[0];
        int bEnd = b[1] < 0 ? Integer.MAX_VALUE : b[1];
        return aStart <= bEnd && bStart <= aEnd;
    }

    private static String formatRange(int[] range) {
        if (range[0] == range[1]) return String.valueOf(range[0]);
        String end = range[1] < 0 ? "*" : String.valueOf(range[1]);
        return range[0] + ".." + end;
    }
}