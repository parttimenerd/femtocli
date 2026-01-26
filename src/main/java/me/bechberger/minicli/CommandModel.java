package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

import java.lang.reflect.Field;
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

    static CommandModel of(Object cmd) throws Exception {
        // Initialize mixins (same behavior as before)
        for (Field field : MiniCli.allFields(cmd.getClass())) {
            if (field.getAnnotation(Mixin.class) != null) {
                field.setAccessible(true);
                Object mixin = field.getType().getDeclaredConstructor().newInstance();
                field.set(cmd, mixin);
            }
        }

        Map<String, MiniCli.OptionMeta> optionsByName = new LinkedHashMap<>();
        Map<Field, MiniCli.OptionMeta> optionByField = new LinkedHashMap<>();
        List<MiniCli.OptionMeta> options = new ArrayList<>();

        // Collect command options and mixin options
        for (Field field : MiniCli.allFields(cmd.getClass())) {
            field.setAccessible(true);

            if (field.getAnnotation(Mixin.class) != null && field.get(cmd) instanceof Object mixin) {
                for (Field mixinField : MiniCli.allFields(mixin.getClass())) {
                    mixinField.setAccessible(true);
                    Option opt = mixinField.getAnnotation(Option.class);
                    if (opt != null) {
                        var meta = new MiniCli.OptionMeta(mixinField, mixin, opt);
                        options.add(meta);
                        optionByField.put(mixinField, meta);
                        for (String name : opt.names()) {
                            optionsByName.put(name, meta);
                        }
                    }
                }
            }

            Option opt = field.getAnnotation(Option.class);
            if (opt != null) {
                var meta = new MiniCli.OptionMeta(field, cmd, opt);
                options.add(meta);
                optionByField.put(field, meta);
                for (String name : opt.names()) {
                    optionsByName.put(name, meta);
                }
            }
        }

        List<MiniCli.ParamInfo> params = new ArrayList<>();
        for (Field f : MiniCli.allFields(cmd.getClass())) {
            Parameters p = f.getAnnotation(Parameters.class);
            if (p != null) {
                f.setAccessible(true);
                params.add(new MiniCli.ParamInfo(f, p, MiniCli.parseRange(p.index()), MiniCli.parseRange(p.arity())));
            }
        }
        params.sort(Comparator.comparingInt(pi -> pi.indexRange[0]));

        return new CommandModel(cmd, optionsByName, optionByField, options, params);
    }
}