package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;
import me.bechberger.minicli.annotations.IgnoreOptions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

final class CommandModel {

    final Object cmd;
    final Map<String, MiniCli.OptionMeta> optionsByName;
    final Map<Field, MiniCli.OptionMeta> optionByField;
    final List<MiniCli.OptionMeta> options;
    final List<MiniCli.ParamInfo> parameters;

    private CommandModel(Object cmd,
                         Map<String, MiniCli.OptionMeta> optionsByName,
                         Map<Field, MiniCli.OptionMeta> optionByField,
                         List<MiniCli.OptionMeta> options,
                         List<MiniCli.ParamInfo> parameters) {
        this.cmd = cmd;
        this.optionsByName = optionsByName;
        this.optionByField = optionByField;
        this.options = options;
        this.parameters = parameters;
    }

    private static void initializeMixins(Object cmd) throws Exception {
        // Initialize mixins (same behavior as before)
        for (Field field : MiniCli.allFields(cmd.getClass())) {
            if (field.getAnnotation(Mixin.class) != null) {
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new FieldIsFinalException("@Mixin field must not be static: " + field);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new FieldIsFinalException("@Mixin field must not be final: " + field);
                }
                field.setAccessible(true);
                Object mixin = field.getType().getDeclaredConstructor().newInstance();
                field.set(cmd, mixin);
            }
        }
    }

    private static void registerOptionMeta(MiniCli.OptionMeta meta,
                                          Map<String, MiniCli.OptionMeta> optionsByName,
                                          Map<Field, MiniCli.OptionMeta> optionByField,
                                          List<MiniCli.OptionMeta> options) {
        options.add(meta);
        optionByField.put(meta.field, meta);
        for (String name : meta.opt.names()) {
            optionsByName.put(name, meta);
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
                                          Map<String, MiniCli.OptionMeta> optionsByName,
                                          Map<Field, MiniCli.OptionMeta> optionByField,
                                          List<MiniCli.OptionMeta> options) {
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
            registerOptionMeta(new MiniCli.OptionMeta(field, holder, opt), optionsByName, optionByField, options);
        }
    }

    private static void collectOptionsFrom(Object holder,
                                          Map<String, MiniCli.OptionMeta> optionsByName,
                                          Map<Field, MiniCli.OptionMeta> optionByField,
                                          List<MiniCli.OptionMeta> options) {
        IgnoreOptions ignore = holder.getClass().getAnnotation(IgnoreOptions.class);

        // inherited first (older classes first), then declared: declared overrides inherited
        Class<?> type = holder.getClass();
        for (Class<?> current = type.getSuperclass(); current != null && current != Object.class; current = current.getSuperclass()) {
            addDeclaredOptions(holder, current, ignore, optionsByName, optionByField, options);
        }
        addDeclaredOptions(holder, type, ignore, optionsByName, optionByField, options);
    }

    static CommandModel of(Object cmd) throws Exception {
        initializeMixins(cmd);

        // Spec fields are initialized by MiniCli once it knows the runtime streams.

        Map<String, MiniCli.OptionMeta> optionsByName = new LinkedHashMap<>();
        Map<Field, MiniCli.OptionMeta> optionByField = new LinkedHashMap<>();
        List<MiniCli.OptionMeta> options = new ArrayList<>();

        // Collect mixin options first, then command options (so command overrides same-name options)
        for (Field field : MiniCli.allFields(cmd.getClass())) {
            field.setAccessible(true);
            if (field.getAnnotation(Mixin.class) != null && field.get(cmd) instanceof Object mixin) {
                collectOptionsFrom(mixin, optionsByName, optionByField, options);
            }
        }
        collectOptionsFrom(cmd, optionsByName, optionByField, options);

        List<MiniCli.ParamInfo> params = new ArrayList<>();
        for (Field f : MiniCli.allFields(cmd.getClass())) {
            Parameters p = f.getAnnotation(Parameters.class);
            if (p != null) {
                if (Modifier.isFinal(f.getModifiers())) {
                    throw new FieldIsFinalException("@Parameters field must not be final: " + f);
                }
                f.setAccessible(true);
                params.add(new MiniCli.ParamInfo(f, p, MiniCli.parseRange(p.index()), MiniCli.parseRange(p.arity())));
            }
        }
        params.sort(Comparator.comparingInt(pi -> pi.indexRange[0]));

        return new CommandModel(cmd, optionsByName, optionByField, options, params);
    }
}