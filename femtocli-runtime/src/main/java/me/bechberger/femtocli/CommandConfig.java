package me.bechberger.femtocli;

/**
 * Default settings that complement command annotations.
 *
 * <p>This lets callers set global defaults without repeating annotation flags on every command.
 */
public class CommandConfig {
    public static final String DEFAULT_TEMPLATE = " (default ${DEFAULT-VALUE})";
    public static final String DEFAULT_SUGGESTION_TEMPLATE = "\n  tip: a similar argument exists: '${SUGGESTION}'";

    public boolean emptyLineAfterUsage;
    public boolean emptyLineAfterDescription;
    public boolean mixinStandardHelpOptions = true;
    public boolean showDefaultValuesInHelp = true;
    public String defaultValueHelpTemplate = DEFAULT_TEMPLATE;
    public boolean defaultValueOnNewLine;
    public String version = "";
    public boolean suggestSimilarOptions = true;
    public String similarOptionsSuggestionTemplate = DEFAULT_SUGGESTION_TEMPLATE;
    /** Exit code to return when --help is requested (default: 0) */
    public int helpExitCode = 0;
    /** Print usage to stdout instead of stderr when there's a CLI usage error (default: false, prints to stderr) */
    public boolean usageErrorsToStdout = false;

    public CommandConfig() {
    }

    /**
     * Create a defensive copy
     */
    public CommandConfig copy() {
        var c = new CommandConfig();
        c.emptyLineAfterUsage = emptyLineAfterUsage;
        c.emptyLineAfterDescription = emptyLineAfterDescription;
        c.mixinStandardHelpOptions = mixinStandardHelpOptions;
        c.showDefaultValuesInHelp = showDefaultValuesInHelp;
        c.defaultValueHelpTemplate = defaultValueHelpTemplate;
        c.defaultValueOnNewLine = defaultValueOnNewLine;
        c.version = version;
        c.suggestSimilarOptions = suggestSimilarOptions;
        c.similarOptionsSuggestionTemplate = similarOptionsSuggestionTemplate;
        c.helpExitCode = helpExitCode;
        c.usageErrorsToStdout = usageErrorsToStdout;
        return c;
    }

    public String effectiveDefaultValueHelpTemplate() {
        return (defaultValueHelpTemplate == null || defaultValueHelpTemplate.isBlank())
                ? DEFAULT_TEMPLATE : defaultValueHelpTemplate;
    }

    public boolean effectiveDefaultValueOnNewLine() {
        return defaultValueOnNewLine;
    }
}