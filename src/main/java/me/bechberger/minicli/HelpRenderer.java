package me.bechberger.minicli;

import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class HelpRenderer {
    private static final int MIN_LABEL_WIDTH = 12;  // Minimum width for labels
    private static final int MAX_LINE_WIDTH = 80;  // Maximum line width for wrapping

    static final class HelpEntry {
        final String label;
        final String description;
        final boolean hasShortOption;

        HelpEntry(String label, String description, boolean hasShortOption) {
            this.label = label;
            this.description = description;
            this.hasShortOption = hasShortOption;
        }
    }

    static void render(Object cmd, String commandPath, CommandConfig commandConfig, PrintStream out) {
        Command annotation = cmd.getClass().getAnnotation(Command.class);
        boolean hasSubcommandClasses = annotation != null && annotation.subcommands().length > 0;
        boolean hasSubcommandMethods = hasSubcommandMethods(cmd.getClass());

        boolean showStandardHelpOptions = commandConfig.effectiveMixinStandardHelpOptions(annotation);

        CommandModel model;
        try {
            model = CommandModel.of(cmd);
        } catch (Exception e) {
            // Fall back to previous behavior if model creation fails for some reason.
            throw new RuntimeException(e);
        }

        renderHeader(annotation, out);
        renderSynopsis(annotation, commandPath, showStandardHelpOptions,
                hasSubcommandClasses || hasSubcommandMethods, model.options, model.parameters, out);

        if (commandConfig.effectiveEmptyLineAfterUsage(annotation)) {
            out.println();
        }

        renderDescription(annotation, out);

        if (commandConfig.effectiveEmptyLineAfterDescription(annotation)) {
            out.println();
        }

        renderParametersAndOptions(model.parameters, model.options, showStandardHelpOptions, commandConfig, annotation, out);
        renderSubcommands(cmd.getClass(), hasSubcommandClasses, hasSubcommandMethods, out);
    }

    private static void renderHeader(Command annotation, PrintStream out) {
        if (annotation != null) {
            for (String line : annotation.header()) out.println(line);
        }
    }

    private static void renderDescription(Command annotation, PrintStream out) {
        if (annotation != null && annotation.description().length > 0) {
            out.println(annotation.description()[0]);
        }
    }

    private static void renderSynopsis(Command annotation, String displayName, boolean showStandardHelpOptions,
                                       boolean hasSubcommands,
                                       List<MiniCli.OptionMeta> options, List<MiniCli.ParamInfo> parameters, PrintStream out) {
        if (annotation != null && annotation.customSynopsis().length > 0) {
            for (String s : annotation.customSynopsis()) out.println(s);
            return;
        }

        StringBuilder synopsis = new StringBuilder();
        synopsis.append("Usage: ").append(displayName);
        if (showStandardHelpOptions) {
            synopsis.append(" [-hV]");
        }

        for (MiniCli.OptionMeta opt : options) {
            String optName = opt.opt.names()[opt.opt.names().length - 1];
            if (!MiniCli.isBooleanType(opt.field.getType())) {
                String paramLabel = getOptionParamLabel(opt);
                if (opt.opt.required()) {
                    synopsis.append(" ").append(optName).append("=").append(paramLabel);
                } else {
                    synopsis.append(" [").append(optName).append("=").append(paramLabel).append("]");
                }
            } else if (!opt.opt.required()) {
                synopsis.append(" [").append(optName).append("]");
            }
        }

        if (hasSubcommands) {
            synopsis.append(" [COMMAND]");
        }

        for (MiniCli.ParamInfo param : parameters) {
            String label = getLabel(param);
            synopsis.append(" ").append(label);
        }

        String synopsisStr = synopsis.toString();
        int usageIndent = "Usage: ".length() + displayName.length() + 1;
        out.println(wrapTextBlock(synopsisStr, 80, usageIndent));
    }

    private static String getLabel(MiniCli.ParamInfo param) {
        String label = param.param.paramLabel().isEmpty()
                ? "<" + param.field.getName() + ">"
                : param.param.paramLabel();
        boolean isVarargs = param.indexRange[1] == -1
                            || List.class.isAssignableFrom(param.field.getType())
                            || param.field.getType().isArray();
        if (isVarargs) {
            label = "[" + label + "...]";
        } else if (param.param.arity().startsWith("0")) {
            label = "[" + label + "]";
        }
        return label;
    }

    private static String getOptionParamLabel(MiniCli.OptionMeta opt) {
        String configured = opt.opt.paramLabel();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "<" + opt.field.getName() + ">";
    }

    private static void renderParametersAndOptions(List<MiniCli.ParamInfo> parameters, List<MiniCli.OptionMeta> options,
                                                   boolean showStandardHelpOptions,
                                                   CommandConfig commandConfig,
                                                   Command annotation,
                                                   PrintStream out) {
        List<HelpEntry> entries = getHelpEntries(parameters);

        List<MiniCli.OptionMeta> sortedOptions = new ArrayList<>(options);
        sortedOptions.sort(Comparator.comparing(o -> {
            String[] names = o.opt.names();
            return names[names.length - 1].replaceFirst("^-+", "").toLowerCase();
        }));

        List<HelpEntry> optionEntries = new ArrayList<>();
        if (showStandardHelpOptions) {
            optionEntries.add(new HelpEntry("-h, --help", "Show this help message and exit.", true));
            optionEntries.add(new HelpEntry("-V, --version", "Print version information and exit.", true));
        }

        for (MiniCli.OptionMeta opt : sortedOptions) {
            String names = formatOptionNames(opt);

            String description = expandPlaceholders(
                    opt.opt.description(),
                    opt.opt.defaultValue(),
                    opt.field.getType()
            );

            description = maybeAppendDefaultValue(description, opt.opt, commandConfig, annotation);

            if (opt.opt.required()) {
                description += " (required)";
            }
            boolean hasShortOption = hasShortOption(opt.opt.names());
            optionEntries.add(new HelpEntry(names, description, hasShortOption));
        }

        optionEntries.sort(Comparator.comparing(e -> e.label.replaceFirst("^\\s*-+", "").toLowerCase()));
        entries.addAll(optionEntries);

        int maxLabelWidth = MIN_LABEL_WIDTH;
        for (HelpEntry e : entries) {
            if (e.label.length() > maxLabelWidth) maxLabelWidth = e.label.length();
        }
        int labelColumnWidth = Math.max(maxLabelWidth, MIN_LABEL_WIDTH);

        for (HelpEntry entry : entries) {
            printAlignedEntry(out, entry.label, entry.description, labelColumnWidth, entry.hasShortOption);
        }
    }

    private static List<HelpEntry> getHelpEntries(List<MiniCli.ParamInfo> parameters) {
        List<HelpEntry> entries = new ArrayList<>();

        for (MiniCli.ParamInfo param : parameters) {
            String label = getLabel(param);
            entries.add(new HelpEntry(label, param.param.description(), false));
        }
        return entries;
    }

    private static String formatOptionNames(MiniCli.OptionMeta opt) {
        StringBuilder sb = new StringBuilder();
        String[] names = opt.opt.names();

        String[] sortedNames = Arrays.copyOf(names, names.length);
        Arrays.sort(sortedNames, Comparator.comparingInt(String::length));

        sb.append(String.join(", ", sortedNames));

        if (!MiniCli.isBooleanType(opt.field.getType())) {
            sb.append("=").append(getOptionParamLabel(opt));
        }

        return sb.toString();
    }

    private static void printAlignedEntry(PrintStream out, String label, String description, int labelWidth, boolean hasShortOption) {
        String prefix = hasShortOption ? "  " : "      ";
        String fullLabel = prefix + label;
        int descriptionColumn = labelWidth + 6;
        int maxDescriptionWidth = MAX_LINE_WIDTH - descriptionColumn;

        List<String> descriptionLines = wrapLines(description, maxDescriptionWidth);

        if (fullLabel.length() < descriptionColumn) {
            if (descriptionLines.isEmpty()) {
                out.println(fullLabel);
            } else {
                out.printf("%-" + descriptionColumn + "s%s%n", fullLabel, descriptionLines.get(0));
                for (int i = 1; i < descriptionLines.size(); i++) {
                    out.printf("%-" + descriptionColumn + "s%s%n", "", descriptionLines.get(i));
                }
            }
        } else {
            out.println(fullLabel);
            for (String line : descriptionLines) {
                out.printf("%-" + descriptionColumn + "s%s%n", "", line);
            }
        }
    }

    // Merged word-wrap utilities:
    private static String wrapTextBlock(String text, int maxWidth, int subsequentIndent) {
        if (text == null || text.isEmpty() || maxWidth <= 0 || text.length() <= maxWidth) {
            return text == null ? "" : text;
        }
        String indent = " ".repeat(Math.max(0, subsequentIndent));
        List<String> lines = wrapLines(text, maxWidth);
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            sb.append("\n").append(indent).append(lines.get(i));
        }
        return sb.toString();
    }

    private static List<String> wrapLines(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (maxWidth <= 0 || text.length() <= maxWidth) {
            return List.of(text);
        }

        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
            } else if (currentLine.length() + 1 + word.length() <= maxWidth) {
                currentLine.append(" ").append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private static void renderSubcommands(Class<?> cmdClass, boolean hasSubcommandClasses,
                                          boolean hasSubcommandMethods, PrintStream out) {
        if (!hasSubcommandClasses && !hasSubcommandMethods) {
            return;
        }

        out.println("Commands:");
        List<String[]> entries = new ArrayList<>();

        if (hasSubcommandClasses) {
            collectSubs(cmdClass, "", entries);
        }
        collectMethodSubs(cmdClass, entries);

        int maxNameLength = 0;
        for (String[] entry : entries) {
            if (entry[0].length() > maxNameLength) maxNameLength = entry[0].length();
        }

        for (String[] entry : entries) {
            if (!entry[0].isEmpty()) {
                out.printf("  %-" + (maxNameLength + 2) + "s%s%n", entry[0], entry[1]);
            }
        }
    }

    private static boolean hasSubcommandMethods(Class<?> cmdClass) {
        for (Method method : cmdClass.getDeclaredMethods()) {
            if (method.getAnnotation(Command.class) != null) return true;
        }
        return false;
    }

    private static void collectMethodSubs(Class<?> cmdClass, List<String[]> entries) {
        for (Method method : cmdClass.getDeclaredMethods()) {
            Command cmdAnnotation = method.getAnnotation(Command.class);
            if (cmdAnnotation != null) {
                String description = cmdAnnotation.description().length > 0 ? cmdAnnotation.description()[0] : "";
                entries.add(new String[]{cmdAnnotation.name(), description});
            }
        }
    }

    private static String expandPlaceholders(String description, String defaultValue, Class<?> type) {
        return description
                .replace("${DEFAULT-VALUE}", defaultValue.isEmpty() ? "none" : defaultValue)
                .replace("${COMPLETION-CANDIDATES}", MiniCli.enumCandidates(type));
    }

    private static void collectSubs(Class<?> cmdClass, String prefix, List<String[]> entries) {
        Command annotation = cmdClass.getAnnotation(Command.class);
        if (annotation == null) {
            return;
        }

        String path = prefix.isEmpty() ? annotation.name() : prefix + " " + annotation.name();
        String description = annotation.description().length > 0 ? annotation.description()[0] : "";

        // Add entry (skip root command)
        if (!prefix.isEmpty()) {
            entries.add(new String[]{path, description});
        }

        // Recursively collect subcommands
        String newPrefix = prefix.isEmpty() ? annotation.name() : path;
        for (Class<?> subcommand : annotation.subcommands()) {
            collectSubs(subcommand, newPrefix, entries);
        }
    }

    private static String maybeAppendDefaultValue(String description, Option opt, CommandConfig commandConfig, Command annotation) {
        if (opt == null) {
            return description;
        }
        if (!commandConfig.effectiveShowDefaultValuesInHelp(annotation) || !opt.showDefaultValueInHelp()) {
            return description;
        }
        if (opt.defaultValue() == null || opt.defaultValue().isEmpty()) {
            return description;
        }
        // If the description already uses the placeholder, don't auto-append a template too.
        if (opt.description() != null && opt.description().contains("${DEFAULT-VALUE}")) {
            return description;
        }

        String template = opt.defaultValueHelpTemplate();
        if (template == null || template.isBlank()) {
            template = commandConfig.effectiveDefaultValueHelpTemplate();
        }
        String rendered = template.replace("${DEFAULT-VALUE}", opt.defaultValue());
        if (rendered.isBlank()) {
            return description;
        }

        boolean forceNewLine = opt.defaultValueOnNewLine() || commandConfig.effectiveDefaultValueOnNewLine();
        if (forceNewLine) {
            return (description == null || description.isBlank())
                    ? rendered.stripLeading()
                    : description + "\n" + rendered.stripLeading();
        }

        return (description == null || description.isBlank())
                ? rendered.stripLeading()
                : description + rendered;
    }

    private static boolean hasShortOption(String[] names) {
        for (String n : names)
            if (n.length() == 2 && n.charAt(0) == '-' && Character.isLetter(n.charAt(1))) return true;
        return false;
    }
}