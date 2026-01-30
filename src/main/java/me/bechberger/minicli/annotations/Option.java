package me.bechberger.minicli.annotations;

import me.bechberger.minicli.CommandConfig;
import me.bechberger.minicli.TypeConverter;
import me.bechberger.minicli.Verifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as an option (named argument).
 *
 * <p>Supports placeholders in description:
 * <ul>
 *   <li>{@code ${DEFAULT-VALUE}} - replaced with the default value</li>
 *   <li>{@code ${COMPLETION-CANDIDATES}} - replaced with valid enum values (for enum types)</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {

    /** Supported option names, e.g. {"-o", "--output"}. */
    String[] names();

    /** Description (supports ${DEFAULT-VALUE} and ${COMPLETION-CANDIDATES} placeholders). */
    String description() default "";

    /**
     * Label for this option's value in help output (e.g., "FILE", "COUNT").
     * If empty, a default based on the field name is used.
     */
    String paramLabel() default "";

    /** Whether this option must be provided on the command line. */
    boolean required() default false;

    /** Default value as string (used for ${DEFAULT-VALUE} placeholder and to set field if not provided). */
    String defaultValue() default "__NO_DEFAULT_VALUE__";

    /**
     * Delimiter for splitting values into array/list elements.
     * E.g., "," to split "a,b,c" into ["a", "b", "c"].
     * If empty, multiple occurrences of the option are collected instead.
     */
    String split() default "";

    /**
     * Arity (number of values). Supported: "0..1" (optional), "1" (required), "0..*" (varargs), or empty.
     */
    String arity() default "";

    /**
     * Custom converter class for this option.
     * Must implement {@link TypeConverter} and have a no-arg constructor.
     */
    Class<? extends TypeConverter<?>> converter() default TypeConverter.NullTypeConverter.class;

    /**
     * Custom converter method.
     *
     * <p>Supported forms:</p>
     * <ul>
     *   <li>{@code "methodName"} - instance method on the command class</li>
     *   <li>{@code "ClassName#methodName"} - static method on another class (must be resolvable via reflection)</li>
     * </ul>
     *
     * <p>The method must accept a single {@code String} and return the target type.</p>
     */
    String converterMethod() default "";

    /**
     * Whether MiniCli should automatically append the default value to the rendered help text
     * when {@link #defaultValue()} is set.
     *
     * <p>This is useful to avoid repeating "default is ..." in every description.
     */
    boolean showDefaultValueInHelp() default true;

    /**
     * Optional per-option template for rendering default values in help.
     *
     * <p>If empty, MiniCli uses the global template from {@link CommandConfig}.
     * The token {@code ${DEFAULT-VALUE}} is replaced with {@link #defaultValue()}.
     */
    String defaultValueHelpTemplate() default "";

    /**
     * If true, the default value text is forced onto a separate wrapped line in the help output.
     *
     * <p>This is useful for long option descriptions where the default should stand out.
     */
    boolean defaultValueOnNewLine() default false;

    /**
     * Custom verifier class for this option.
     * Must implement {@link Verifier} and have a no-arg constructor.
     */
    Class<? extends Verifier<?>> verifier() default Verifier.NullVerifier.class;

    /**
     * Custom verifier method.
     *
     * <p>Supported forms:</p>
     * <ul>
     *   <li>{@code "methodName"} - instance method on the command class</li>
     *   <li>{@code "ClassName#methodName"} - static method on another class</li>
     * </ul>
     *
     * <p>The method must accept a single argument of the option's target type and return void.</p>
     */
    String verifierMethod() default "";

    /**
     * Whether this option should be hidden from the help output.
     * Useful for advanced or deprecated options.
     */
    boolean hidden() default false;
}