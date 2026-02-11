package me.bechberger.femtocli.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a mixin, allowing reusable option groups.
 *
 * <p>The mixin class should contain {@link Option} annotated fields
 * that will be included in the parent command's options.
 *
 * <p>Example:
 * <pre>{@code
 * class CommonOptions {
 *     @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
 *     boolean verbose;
 *
 *     @Option(names = {"-q", "--quiet"}, description = "Suppress output")
 *     boolean quiet;
 * }
 *
 * @Command(name = "myapp", description = "My application")
 * class MyApp implements Runnable {
 *     @Mixin
 *     CommonOptions common;
 *
 *     @Override
 *     public void run() {
 *         if (common.verbose) { ... }
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Mixin {
}