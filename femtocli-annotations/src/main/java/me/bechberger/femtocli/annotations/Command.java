package me.bechberger.femtocli.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as a command.
 *
 * <p>When applied to a class, defines a command that can have options and subcommands.
 * When applied to a method, defines a subcommand that will invoke that method.
 *
 * <p>Example of method-based subcommand:
 * <pre>{@code
 * @Command(name = "myapp", description = "My application")
 * class MyApp implements Runnable {
 *
 *     @Command(name = "greet", description = "Greet someone")
 *     int greet() {
 *         System.out.println("Hello!");
 *         return 0;
 *     }
 *
 *     @Override
 *     public void run() { }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Command {

    String name();

    /** Command description, shown after the usage line. */
    String[] description() default {};

    /**
     * Header text, shown before the usage line.
     * Each array element is printed on a separate line.
     */
    String[] header() default {};

    /**
     * Custom synopsis (usage) line(s). If empty, a default synopsis is generated.
     * Each array element is printed on a separate line.
     */
    String[] customSynopsis() default {};

    String version() default "";

    Class<?>[] subcommands() default {};

    boolean mixinStandardHelpOptions() default false;

    /**
     * Whether to add an empty line after the usage/synopsis line.
     */
    boolean emptyLineAfterUsage() default false;

    /**
     * Whether to add an empty line after the description.
     */
    boolean emptyLineAfterDescription() default false;

    /**
     * Per-command override for whether default values should be appended to help for options
     * that have {@link Option#defaultValue()} set.
     *
     * <p>By default this is {@link ShowDefaultValuesInHelp#INHERIT}, which means the global
     * setting from {@link CommandConfig} is used.
     */
    ShowDefaultValuesInHelp showDefaultValuesInHelp() default ShowDefaultValuesInHelp.INHERIT;

    enum ShowDefaultValuesInHelp {
        /** Use the global setting from {@link CommandConfig}. */
        INHERIT,
        /** Force showing default values in help for this command. */
        ENABLE,
        /** Force hiding default values in help for this command. */
        DISABLE
    }

    /**
     * Whether this command should be hidden from the help output of the parent command.
     */
    boolean hidden() default false;

    /**
     * Enable agent mode for this command.
     * <p>
     * When true, the generated parser will include agent-mode token normalisation
     * (bare option names like {@code "port=8080"} → {@code "--port=8080"}) and
     * the runtime will support {@link me.bechberger.femtocli.FemtoCli#runAgent(Object, String)}.
     * <p>
     * Default is false — agent-mode code is omitted to keep generated parsers small.
     */
    boolean agentMode() default false;

    /**
     * Footer text, shown after the options and subcommands in help output.
     * Each array element is printed on a separate line.
     */
    String footer() default "";
}