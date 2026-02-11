package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static me.bechberger.femtocli.FemtoCli.NO_DEFAULT_VALUE;

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
        render(cmd, commandPath, commandConfig, out, false);
    }

    static void render(Object cmd, String commandPath, CommandConfig commandConfig, PrintStream out, boolean agentMode) {
        Command annotation = cmd.getClass().getAnnotation(Command.class);
        boolean hasSubcommandClasses = annotation != null && annotation.subcommands().length > 0;
        boolean hasSubcommandMethods = hasSubcommandMethods(cmd.getClass());
        boolean hasSubcommands = hasSubcommandClasses || hasSubcommandMethods;

        boolean showStandardHelpOptions = commandConfig.effectiveMixinStandardHelpOptions(annotation);

        CommandModel model;
        try {
            model = CommandModel.of(cmd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        printLines(out, annotation == null ? null : annotation.header());

        if (!printLines(out, annotation == null ? null : annotation.customSynopsis())) {
            renderSynopsis(commandPath, showStandardHelpOptions, hasSubcommands, model.options, model.parameters, out, agentMode);
        }

        if (commandConfig.effectiveEmptyLineAfterUsage(annotation)) out.println();

        if (!agentMode && annotation != null && annotation.description().length > 0) {
            out.println(annotation.description()[0]);
        }

        if (commandConfig.effectiveEmptyLineAfterDescription(annotation)) out.println();

        renderParametersAndOptions(model.parameters, model.options, showStandardHelpOptions, commandConfig, annotation, out, agentMode);
        renderSubcommands(cmd.getClass(), hasSubcommandClasses, hasSubcommandMethods, out);
        renderFooter(annotation, out);
    }

    /** Print all lines; returns true if at least one line was printed. */
    private static boolean printLines(PrintStream out, String[] lines) {
        if (lines == null || lines.length == 0) return false;
        for (String line : lines) out.println(line);
        return true;
    }

    static String stripLeadingDashes(String name) {
        if (name == null) return "";
        if (name.startsWith("--")) return name.substring(2);
        if (name.startsWith("-")) return name.substring(1);
        return name;
    }

    private static void renderSynopsis(String displayName, boolean showStandardHelpOptions,
                                       boolean hasSubcommands,
                                       List<FemtoCli.OptionMeta> options, List<FemtoCli.ParamInfo> parameters, PrintStream out,
                                       boolean agentMode) {
        if (agentMode) {
            List<String> parts = new ArrayList<>();
            parts.add(displayName);
            if (showStandardHelpOptions) parts.add("[hV]");

            for (FemtoCli.OptionMeta opt : options) {
                if (opt.opt.hidden()) continue;
                String optName = stripLeadingDashes(last(opt.opt.names()));
                boolean isBoolean = FemtoCli.isBooleanType(opt.field.getType());
                if (!isBoolean) {
                    String paramLabel = getOptionParamLabel(opt);
                    String token = optName + "=" + paramLabel;
                    parts.add(opt.opt.required() ? token : "[" + token + "]");
                } else if (!opt.opt.required()) {
                    parts.add("[" + optName + "]");
                }
            }

            if (hasSubcommands) parts.add("[COMMAND]");
            for (FemtoCli.ParamInfo param : parameters) parts.add(getLabel(param));

            out.println("Usage: " + String.join(",", parts));
            return;
        }

        // non-agent mode (classic)
        StringBuilder synopsis = new StringBuilder("Usage: ").append(displayName);
        if (showStandardHelpOptions) synopsis.append(" [-hV]");

        for (FemtoCli.OptionMeta opt : options) {
            if (opt.opt.hidden()) continue;
            String optName = last(opt.opt.names());
            boolean isBoolean = FemtoCli.isBooleanType(opt.field.getType());

            if (!isBoolean) {
                String paramLabel = getOptionParamLabel(opt);
                synopsis.append(opt.opt.required() ? " " : " [")
                        .append(optName).append("=").append(paramLabel)
                        .append(opt.opt.required() ? "" : "]");
            } else if (!opt.opt.required()) {
                synopsis.append(" [").append(optName).append("]");
            }
        }

        if (hasSubcommands) synopsis.append(" [COMMAND]");
        for (FemtoCli.ParamInfo param : parameters) synopsis.append(" ").append(getLabel(param));

        int usageIndent = "Usage: ".length() + displayName.length() + 1;
        out.println(wrapTextBlock(synopsis.toString(), 80, usageIndent));
    }

    private static <T> T last(T[] a) {
        return a[a.length - 1];
    }

    private static String getLabel(FemtoCli.ParamInfo param) {
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

    private static String getOptionParamLabel(FemtoCli.OptionMeta opt) {
        String configured = opt.opt.paramLabel();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "<" + opt.field.getName() + ">";
    }

    private static void renderParametersAndOptions(List<FemtoCli.ParamInfo> parameters, List<FemtoCli.OptionMeta> options,
                                                   boolean showStandardHelpOptions,
                                                   CommandConfig commandConfig,
                                                   Command annotation,
                                                   PrintStream out,
                                                   boolean agentMode) {
        List<HelpEntry> entries = new ArrayList<>(getHelpEntries(parameters));

        List<HelpEntry> optionEntries = new ArrayList<>();
        if (showStandardHelpOptions) {
            optionEntries.add(new HelpEntry(agentMode ? "h, help" : "-h, --help", "Show this help message and exit.", true));
            optionEntries.add(new HelpEntry(agentMode ? "V, version" : "-V, --version", "Print version information and exit.", true));
        }

        for (FemtoCli.OptionMeta opt : options) {
            if (opt.opt.hidden()) continue;

            String names = formatOptionNames(opt, agentMode);
            String description = expandPlaceholders(opt.opt.description(), opt.opt.defaultValue(), opt.field.getType());
            description = maybeAppendDefaultValue(description, opt.opt, commandConfig, annotation);
            if (opt.opt.required()) description += " (required)";

            optionEntries.add(new HelpEntry(names, description, hasShortOption(opt.opt.names())));
        }

        if (agentMode && !optionEntries.isEmpty()) {
            out.println("Options:");
        }

        optionEntries.sort(Comparator.comparing(e -> e.label.replaceFirst("^\\s*-+", "").toLowerCase()));
        entries.addAll(optionEntries);

        int labelColumnWidth = Math.max(MIN_LABEL_WIDTH, entries.stream().mapToInt(e -> e.label.length()).max().orElse(MIN_LABEL_WIDTH));
        for (HelpEntry entry : entries) {
            printAlignedEntry(out, entry.label, entry.description, labelColumnWidth, entry.hasShortOption);
        }
    }

    private static List<HelpEntry> getHelpEntries(List<FemtoCli.ParamInfo> parameters) {
        List<HelpEntry> entries = new ArrayList<>();

        for (FemtoCli.ParamInfo param : parameters) {
            String label = getLabel(param);
            entries.add(new HelpEntry(label, param.param.description(), false));
        }
        return entries;
    }

    private static String formatOptionNames(FemtoCli.OptionMeta opt, boolean agentMode) {
        StringBuilder sb = new StringBuilder();
        String[] names = opt.opt.names();

        String[] sortedNames = Arrays.copyOf(names, names.length);
        Arrays.sort(sortedNames, Comparator.comparingInt(String::length));

        if (agentMode) {
            for (int i = 0; i < sortedNames.length; i++) {
                sortedNames[i] = stripLeadingDashes(sortedNames[i]);
            }
        }

        sb.append(String.join(", ", sortedNames));

        if (!FemtoCli.isBooleanType(opt.field.getType())) {
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
            if (currentLine.isEmpty()) {
                currentLine.append(word);
            } else if (currentLine.length() + 1 + word.length() <= maxWidth) {
                currentLine.append(" ").append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private static void renderSubcommands(Class<?> cmdClass, boolean hasSubcommandClasses,
                                          boolean hasSubcommandMethods, PrintStream out) {
        if (!hasSubcommandClasses && !hasSubcommandMethods) return;

        List<String[]> entries = new ArrayList<>();
        if (hasSubcommandClasses) collectDirectSubcommands(cmdClass, entries);
        collectMethodSubs(cmdClass, entries);
        if (entries.isEmpty()) return;

        out.println("Commands:");

        int maxNameLength = 0;
        for (String[] entry : entries) maxNameLength = Math.max(maxNameLength, entry[0].length());

        for (String[] entry : entries) {
            if (!entry[0].isEmpty()) out.printf("  %-" + (maxNameLength + 2) + "s%s%n", entry[0], entry[1]);
        }
    }

    private static void collectDirectSubcommands(Class<?> cmdClass, List<String[]> entries) {
        Command annotation = cmdClass.getAnnotation(Command.class);
        if (annotation == null || annotation.hidden()) {
            return;
        }

        for (Class<?> subcommand : annotation.subcommands()) {
            Command sub = subcommand.getAnnotation(Command.class);
            if (sub == null || sub.hidden()) {
                continue;
            }
            String description = sub.description().length > 0 ? sub.description()[0] : "";
            entries.add(new String[]{sub.name(), description});
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
            if (cmdAnnotation != null && !cmdAnnotation.hidden()) {
                String description = cmdAnnotation.description().length > 0 ? cmdAnnotation.description()[0] : "";
                entries.add(new String[]{cmdAnnotation.name(), description});
            }
        }
    }

    private static String expandPlaceholders(String description, String defaultValue, Class<?> type) {
        return description
                .replace("${DEFAULT-VALUE}", defaultValue.equals(NO_DEFAULT_VALUE) ? "none" : defaultValue)
                .replace("${COMPLETION-CANDIDATES}", FemtoCli.enumCandidates(type));
    }

    // (kept for backwards compatibility; no longer used for help output)
    @Deprecated
    @SuppressWarnings("unused")
    private static void collectSubs(Class<?> cmdClass, String prefix, List<String[]> entries) {
        Command annotation = cmdClass.getAnnotation(Command.class);
        if (annotation == null) {
            return;
        }

        if (annotation.hidden()) {
            return;
        }

        String name = annotation.name();
        String description = annotation.description().length > 0 ? annotation.description()[0] : "";

        // Full command path that should show up in the *current* help output.
        // Example: when collecting for root help, "ai full" should be shown, not "full".
        String fullPath = prefix.isEmpty() ? name : (prefix + " " + name);

        // Add entry (skip the command that owns this help screen; i.e. the "root" of this traversal)
        if (!prefix.isEmpty()) {
            entries.add(new String[]{fullPath, description});
        }

        // Recursively collect subcommands
        for (Class<?> subcommand : annotation.subcommands()) {
            collectSubs(subcommand, fullPath, entries);
        }
    }

    private static String maybeAppendDefaultValue(String description, Option opt, CommandConfig commandConfig, Command annotation) {
        if (opt == null) {
            return description;
        }
        if (!commandConfig.effectiveShowDefaultValuesInHelp(annotation) || !opt.showDefaultValueInHelp()) {
            return description;
        }
        if (opt.defaultValue() == null || opt.defaultValue().equals(NO_DEFAULT_VALUE)) {
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

    private static void renderFooter(Command annotation, PrintStream out) {
        if (annotation == null) return;
        String footer = annotation.footer();
        if (footer == null || footer.isBlank()) return;

        out.println();
        out.print(footer);
        if (!footer.endsWith("\n") && !footer.endsWith("\r")) {
            out.println();
        }
    }

    private static boolean hasShortOption(String[] names) {
        for (String n : names)
            if (n.length() == 2 && n.charAt(0) == '-' && Character.isLetter(n.charAt(1))) return true;
        return false;
    }
}