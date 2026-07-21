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
 * The annotation on a mixin class filters that mixin's own options.
 * The annotation on a command class filters options defined on (or inherited by) the command class
 * <em>and</em> options contributed by the command's {@code @Mixin} fields. This lets a command hide
 * an option it inherits from a shared mixin without modifying the mixin.</p>
 *
 * <p>When both a command and one of its mixins carry {@code @IgnoreOptions}, an option is kept only
 * if it survives both: either annotation may exclude it.</p>
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