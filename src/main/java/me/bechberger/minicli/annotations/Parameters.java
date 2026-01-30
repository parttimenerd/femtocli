package me.bechberger.minicli.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import me.bechberger.minicli.TypeConverter;
import me.bechberger.minicli.Verifier;

/**
 * Marks a field as positional parameters.
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
    String defaultValue() default "__NO_DEFAULT_VALUE__";

    /**
     * Custom converter method.
     *
     * <p>Supported forms:</p>
     * <ul>
     *   <li>{@code "methodName"} - instance method on the command class</li>
     *   <li>{@code "ClassName#methodName"} - static method on another class</li>
     * </ul>
     *
     * <p>The method must accept a single {@code String} and return the target type.</p>
     */
    String converterMethod() default "";

    /**
     * Custom converter class for this parameter.
     * Must implement {@link TypeConverter} and have a no-arg constructor.
     */
    Class<? extends TypeConverter<?>> converter() default TypeConverter.NullTypeConverter.class;

    /**
     * Custom verifier class for this parameter.
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
     * <p>The method must accept a single argument of the parameter's target type and return void.</p>
     */
    String verifierMethod() default "";
}