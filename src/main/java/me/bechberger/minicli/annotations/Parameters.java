package me.bechberger.minicli.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as positional parameters.
 *
 * <p>Index formats:
 * <ul>
 *   <li>{@code "0"} - first positional parameter</li>
 *   <li>{@code "1"} - second positional parameter</li>
 *   <li>{@code "0..1"} - first two positional parameters</li>
 *   <li>{@code "2..*"} - third and all remaining positional parameters</li>
 *   <li>{@code "0..*"} - all positional parameters (varargs)</li>
 * </ul>
 *
 * <p>Arity formats:
 * <ul>
 *   <li>{@code ""} (empty) - required (exactly 1 value expected for single index)</li>
 *   <li>{@code "0..1"} - optional (0 or 1 value)</li>
 *   <li>{@code "1..*"} - at least 1 value</li>
 *   <li>{@code "0..*"} - any number of values</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Parameters {

    /**
     * Index or index range for this parameter.
     * E.g., "0", "1", "0..1", "0..*", "2..*".
     */
    String index() default "0";

    /**
     * Arity (number of values). E.g., "", "0..1", "1..*", "0..*".
     */
    String arity() default "";

    /** Description for help output. */
    String description() default "";

    /**
     * Label for this parameter in help output (e.g., "FILE", "DIR").
     * If empty, the field name is used.
     */
    String paramLabel() default "";

    /** Default value as string (applied if parameter is optional and not provided). */
    String defaultValue() default "";
}