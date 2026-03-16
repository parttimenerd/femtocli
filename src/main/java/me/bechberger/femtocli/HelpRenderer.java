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
        String sep = agentMode ? "," : " ";
        List<String> parts = new ArrayList<>();
        parts.add(agentMode ? displayName : "Usage: " + displayName);
        if (showStandardHelpOptions) parts.add(agentMode ? "[hV]" : "[-hV]");

        for (FemtoCli.OptionMeta opt : options) {
            if (opt.opt.hidden()) continue;
            String rawName = opt.opt.names()[opt.opt.names().length - 1];
            String optName = agentMode ? stripLeadingDashes(rawName) : rawName;
            boolean isBoolean = FemtoCli.isBooleanType(opt.field.getType());
            if (!isBoolean) {
                String token = optName + "=" + getOptionParamLabel(opt);
                parts.add(opt.opt.required() ? token : "[" + token + "]");
            } else if (!opt.opt.required()) {
                parts.add("[" + optName + "]");
            }
        }

        for (FemtoCli.ParamInfo param : parameters) parts.add(getLabel(param));
        if (hasSubcommands) parts.add("[COMMAND]");

        if (agentMode) {
            out.println("Usage: " + String.join(sep, parts));
        } else {
            String line = String.join(sep, parts);
            out.println(wrapTextBlock(line, 80, "Usage: ".length() + displayName.length() + 1));
        }
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
        List<HelpEntry> entries = new ArrayList<>();
        for (FemtoCli.ParamInfo param : parameters) {
            entries.add(new HelpEntry(getLabel(param), param.param.description(), false));
        }

        List<HelpEntry> optionEntries = new ArrayList<>();
        if (showStandardHelpOptions) {
            optionEntries.add(new HelpEntry(agentMode ? "h, help" : "-h, --help", "Show this help message and exit.", true));
            optionEntries.add(new HelpEntry(agentMode ? "V, version" : "-V, --version", "Print version information and exit.", true));
        }

        for (FemtoCli.OptionMeta opt : options) {
            if (opt.opt.hidden()) continue;

            String names = formatOptionNames(opt, agentMode);
            String description = expandPlaceholders(opt.opt.description(), opt.opt.defaultValue(), opt.field.getType(), opt.opt);
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

    private static String formatOptionNames(FemtoCli.OptionMeta opt, boolean agentMode) {
        String[] names = opt.opt.names();
        String[] sorted = Arrays.copyOf(names, names.length);
        Arrays.sort(sorted, Comparator.comparingInt(String::length));
        if (agentMode) {
            for (int i = 0; i < sorted.length; i++) sorted[i] = stripLeadingDashes(sorted[i]);
        }
        String joined = String.join(", ", sorted);
        return FemtoCli.isBooleanType(opt.field.getType()) ? joined : joined + "=" + getOptionParamLabel(opt);
    }

    private static void printAlignedEntry(PrintStream out, String label, String description, int labelWidth, boolean hasShortOption) {
        String fullLabel = (hasShortOption ? "  " : "      ") + label;
        int descCol = labelWidth + 6;
        String fmt = "%-" + descCol + "s%s%n";
        List<String> descLines = wrapLines(description, MAX_LINE_WIDTH - descCol);

        if (fullLabel.length() >= descCol || descLines.isEmpty()) {
            out.println(fullLabel);
            for (String line : descLines) out.printf(fmt, "", line);
        } else {
            out.printf(fmt, fullLabel, descLines.get(0));
            for (int i = 1; i < descLines.size(); i++) out.printf(fmt, "", descLines.get(i));
        }
    }

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
        if (text == null || text.isEmpty()) return List.of();

        List<String> result = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) continue;

            if (maxWidth <= 0 || line.length() <= maxWidth) {
                result.add(line);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String word : line.split(" ")) {
                    if (sb.length() == 0) {
                        sb.append(word);
                    } else if (sb.length() + 1 + word.length() <= maxWidth) {
                        sb.append(" ").append(word);
                    } else {
                        result.add(sb.toString());
                        sb.setLength(0);
                        sb.append(word);
                    }
                }
                if (sb.length() > 0) result.add(sb.toString());
            }
        }

        return result.isEmpty() ? List.of(text) : result;
    }

    private static void renderSubcommands(Class<?> cmdClass, boolean hasSubcommandClasses,
                                          boolean hasSubcommandMethods, PrintStream out) {
        if (!hasSubcommandClasses && !hasSubcommandMethods) return;

        List<String[]> entries = new ArrayList<>();
        Command annotation = cmdClass.getAnnotation(Command.class);
        if (hasSubcommandClasses && annotation != null && !annotation.hidden()) {
            for (Class<?> subcommand : annotation.subcommands()) {
                Command sub = subcommand.getAnnotation(Command.class);
                if (sub != null && !sub.hidden()) {
                    entries.add(new String[]{sub.name(), sub.description().length > 0 ? sub.description()[0] : ""});
                }
            }
        }
        for (Method method : cmdClass.getDeclaredMethods()) {
            Command cmdAnnotation = method.getAnnotation(Command.class);
            if (cmdAnnotation != null && !cmdAnnotation.hidden()) {
                entries.add(new String[]{cmdAnnotation.name(), cmdAnnotation.description().length > 0 ? cmdAnnotation.description()[0] : ""});
            }
        }
        if (entries.isEmpty()) return;

        out.println("Commands:");

        int maxNameLength = 0;
        for (String[] entry : entries) maxNameLength = Math.max(maxNameLength, entry[0].length());

        for (String[] entry : entries) {
            if (!entry[0].isEmpty()) out.printf("  %-" + (maxNameLength + 2) + "s%s%n", entry[0], entry[1]);
        }
    }

    static boolean hasSubcommandMethods(Class<?> cmdClass) {
        for (Method method : cmdClass.getDeclaredMethods()) {
            if (method.getAnnotation(Command.class) != null) return true;
        }
        return false;
    }

    private static String expandPlaceholders(String description, String defaultValue, Class<?> type, Option opt) {
        String result = description
                .replace("${DEFAULT-VALUE}", defaultValue.equals(NO_DEFAULT_VALUE) ? "none" : defaultValue);

        // Handle ${COMPLETION-CANDIDATES}
        if (result.contains("${COMPLETION-CANDIDATES")) {
            result = expandCompletionCandidates(result, type, opt);
        }

        return result;
    }

    private static String expandCompletionCandidates(String description, Class<?> type, Option opt) {
        int idx = 0;
        StringBuilder sb = new StringBuilder();

        while (idx < description.length()) {
            int start = description.indexOf("${COMPLETION-CANDIDATES", idx);
            if (start == -1) {
                sb.append(description.substring(idx));
                break;
            }

            sb.append(description, idx, start);

            int end = description.indexOf("}", start);
            if (end == -1) {
                sb.append(description.substring(start));
                break;
            }

            String joiner = ", ";
            int colonIdx = description.indexOf(":", start);
            if (colonIdx != -1 && colonIdx < end) {
                joiner = description.substring(colonIdx + 1, end)
                    .replace("\\n", "\n").replace("\\t", "\t");
            }

            sb.append(FemtoCli.enumCandidates(type, opt, joiner));

            idx = end + 1;
        }

        return sb.toString();
    }


    private static String maybeAppendDefaultValue(String description, Option opt, CommandConfig commandConfig, Command annotation) {
        if (opt == null || !commandConfig.effectiveShowDefaultValuesInHelp(annotation) || !opt.showDefaultValueInHelp())
            return description;
        if (opt.defaultValue() == null || opt.defaultValue().equals(NO_DEFAULT_VALUE))
            return description;
        if (opt.description() != null && opt.description().contains("${DEFAULT-VALUE}"))
            return description;

        String template = opt.defaultValueHelpTemplate();
        if (template == null || template.isBlank()) template = commandConfig.effectiveDefaultValueHelpTemplate();
        String rendered = template.replace("${DEFAULT-VALUE}", opt.defaultValue());
        if (rendered.isBlank()) return description;

        boolean empty = description == null || description.isBlank();
        if (opt.defaultValueOnNewLine() || commandConfig.effectiveDefaultValueOnNewLine()) {
            return empty ? rendered.stripLeading() : description + "\n" + rendered.stripLeading();
        }
        return empty ? rendered.stripLeading() : description + rendered;
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