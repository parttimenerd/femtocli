package me.bechberger.femtocli.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Filters which {@link Option} annotated fields are considered for a command or mixin class.
 *
 * <p>By default, FemtoCli collects all {@code @Option} annotated fields from a command's class hierarchy
 * (including inherited fields) as well as from any {@code @Mixin} instances.</p>
 *
 * <p>Add this annotation to a class to filter the options collected from that class' hierarchy.
 * Note: the annotation is evaluated per "option holder" (the command object itself and each mixin object).
 * So to ignore options defined in a mixin class, put {@code @IgnoreOptions} on the mixin class.
 * To ignore options defined on the command class (or inherited by it), put it on the command class.</p>
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li>If {@code ignoreAll=true}: start with zero options.</li>
 *   <li>{@code include}: always (re-)include matching options.</li>
 *   <li>{@code exclude} (and deprecated alias {@code options}): exclude matching options.</li>
 * </ul>
 *
 * <p>Matching: an entry can be an option name like {@code "--port"} or {@code "-p"},
 * or a field name prefixed with {@code "field:"} (e.g. {@code "field:port"}).</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IgnoreOptions {

    /**
     * Alias for {@link #exclude()}.
     *
     * @deprecated Use {@link #exclude()}.
     */
    @Deprecated
    String[] options() default {};

    /** Exclude matching options. */
    String[] exclude() default {};

    /** Include matching options (useful with {@link #ignoreAll()}). */
    String[] include() default {};

    /** If true, include no options unless explicitly {@link #include() included}. */
    boolean ignoreAll() default false;
}