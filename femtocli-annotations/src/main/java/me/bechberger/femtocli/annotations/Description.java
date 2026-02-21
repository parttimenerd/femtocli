package me.bechberger.femtocli.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides a description for an enum constant, used by the annotation processor
 * to expand {@code ${COMPLETION-CANDIDATES}} placeholders with descriptions.
 *
 * <p>Example:
 * <pre>{@code
 * enum Color {
 *     @Description("A warm, bright color")
 *     RED,
 *     @Description("The color of leaves")
 *     GREEN,
 *     @Description("The color of the sky")
 *     BLUE
 * }
 * }</pre>
 *
 * <p>When used with {@code @Option(showEnumDescriptions = true)}, the
 * {@code ${COMPLETION-CANDIDATES}} placeholder expands to
 * {@code "RED (A warm, bright color), GREEN (The color of leaves), BLUE (The color of the sky)"}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface Description {
    /** The description text for this enum constant. */
    String value();
}
