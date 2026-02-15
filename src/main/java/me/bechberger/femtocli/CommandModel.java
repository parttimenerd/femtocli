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
        // Initialize mixins (same behavior as before)
        for (Field field : FemtoCli.allFields(cmd.getClass())) {
            if (field.getAnnotation(Mixin.class) != null) {
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new FieldIsFinalException("@Mixin field must not be static: " + field);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new FieldIsFinalException("@Mixin field must not be final: " + field);
                }
                field.setAccessible(true);
                Constructor<?> ctor = field.getType().getDeclaredConstructor();
                ctor.setAccessible(true);
                Object mixin = ctor.newInstance();
                field.set(cmd, mixin);
            }
        }
    }

    private static void registerOptionMeta(FemtoCli.OptionMeta meta,
                                           Map<String, FemtoCli.OptionMeta> optionsByName,
                                           Map<Field, FemtoCli.OptionMeta> optionByField,
                                           List<FemtoCli.OptionMeta> options,
                                           Map<String, Map<Field, List<String>>> namesByBareAndField) {
        options.add(meta);
        optionByField.put(meta.field, meta);

        for (String name : meta.opt.names()) {
            optionsByName.put(name, meta);

            if (namesByBareAndField != null) {
                String bare = HelpRenderer.stripLeadingDashes(name);
                if (bare.isBlank()) continue;
                namesByBareAndField
                        .computeIfAbsent(bare, k -> new LinkedHashMap<>())
                        .computeIfAbsent(meta.field, k -> new ArrayList<>())
                        .add(name);
            }
        }
    }

    private static boolean matchesIgnoreRule(String rule, Field field, Option opt) {
        if (rule == null || rule.isBlank()) return false;
        if (rule.startsWith("field:")) {
            return field.getName().equals(rule.substring("field:".length()));
        }
        for (String n : opt.names()) {
            if (n.equals(rule)) return true;
        }
        return false;
    }

    private static boolean matchesAnyRule(String[] rules, Field field, Option opt) {
        if (rules == null) return false;
        for (String rule : rules) {
            if (matchesIgnoreRule(rule, field, opt)) return true;
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
                                          List<FemtoCli.OptionMeta> options,
                                          Map<String, Map<Field, List<String>>> namesByBareAndField) {
        for (Field field : declaredIn.getDeclaredFields()) {
            field.setAccessible(true);
            Option opt = field.getAnnotation(Option.class);
            if (opt == null) continue;
            if (Modifier.isFinal(field.getModifiers())) {
                throw new FieldIsFinalException("@Option field must not be final: " + field);
            }
            if (!shouldIncludeOption(ignore, field, opt)) {
                continue;
            }
            registerOptionMeta(new FemtoCli.OptionMeta(field, holder, opt), optionsByName, optionByField, options, namesByBareAndField);
        }
    }

    private static void collectOptionsFrom(Object holder,
                                          Map<String, FemtoCli.OptionMeta> optionsByName,
                                          Map<Field, FemtoCli.OptionMeta> optionByField,
                                          List<FemtoCli.OptionMeta> options,
                                          Map<String, Map<Field, List<String>>> namesByBareAndField) {
        IgnoreOptions ignore = holder.getClass().getAnnotation(IgnoreOptions.class);

        // inherited first (older classes first), then declared: declared overrides inherited
        Class<?> type = holder.getClass();
        for (Class<?> current = type.getSuperclass(); current != null && current != Object.class; current = current.getSuperclass()) {
            addDeclaredOptions(holder, current, ignore, optionsByName, optionByField, options, namesByBareAndField);
        }
        addDeclaredOptions(holder, type, ignore, optionsByName, optionByField, options, namesByBareAndField);
    }


    private static void validateNoAmbiguousBareOptionNames(Map<String, Map<Field, List<String>>> namesByBareAndField) {
        for (var e : namesByBareAndField.entrySet()) {
            String bare = e.getKey();
            Map<Field, List<String>> byField = e.getValue();
            if (byField.size() > 1) {
                List<String> names = new ArrayList<>();
                for (List<String> ns : byField.values()) {
                    names.addAll(ns);
                }
                throw new IllegalArgumentException(
                        "Ambiguous option name '" + bare + "' (use distinct names): " + String.join(", ", names));
            }
        }
    }

    static CommandModel of(Object cmd) throws Exception {
        initializeMixins(cmd);

        // Spec fields are initialized by FemtoCli once it knows the runtime streams.

        Map<String, FemtoCli.OptionMeta> optionsByName = new LinkedHashMap<>();
        Map<Field, FemtoCli.OptionMeta> optionByField = new LinkedHashMap<>();
        List<FemtoCli.OptionMeta> options = new ArrayList<>();
        Map<String, Map<Field, List<String>>> namesByBareAndField = new LinkedHashMap<>();

        // Collect mixin options first, then command options (so command overrides same-name options)
        for (Field field : FemtoCli.allFields(cmd.getClass())) {
            field.setAccessible(true);
            if (field.getAnnotation(Mixin.class) != null && field.get(cmd) != null) {
                collectOptionsFrom(field.get(cmd), optionsByName, optionByField, options, namesByBareAndField);
            }
        }
        collectOptionsFrom(cmd, optionsByName, optionByField, options, namesByBareAndField);

        // Fail early if agent-mode bare option normalization would be ambiguous.
        validateNoAmbiguousBareOptionNames(namesByBareAndField);

        List<FemtoCli.ParamInfo> params = new ArrayList<>();
        for (Field f : FemtoCli.allFields(cmd.getClass())) {
            Parameters p = f.getAnnotation(Parameters.class);
            if (p != null) {
                if (Modifier.isFinal(f.getModifiers())) {
                    throw new FieldIsFinalException("@Parameters field must not be final: " + f);
                }
                f.setAccessible(true);
                params.add(new FemtoCli.ParamInfo(f, p, FemtoCli.parseRange(p.index()), FemtoCli.parseRange(p.arity())));
            }
        }
        params.sort(Comparator.comparingInt(pi -> pi.indexRange[0]));

        return new CommandModel(cmd, optionsByName, optionByField, options, params);
    }
}