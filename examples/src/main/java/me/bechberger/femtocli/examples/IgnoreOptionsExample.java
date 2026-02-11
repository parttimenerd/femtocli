package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.IgnoreOptions;
import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;

/**
 * Example for {@code @IgnoreOptions}:
 * <ul>
 *   <li>remove inherited options from a base command</li>
 *   <li>remove options contributed by a {@code @Mixin}</li>
 * </ul>
 */
public class IgnoreOptionsExample {

    @IgnoreOptions(exclude = "--m")
    static class MixinOpts {
        @Option(names = "--m", description = "Mixin option")
        int m;
    }

    static class Base implements Runnable {
        @Option(names = "--a", description = "Inherited option A")
        int a;

        @Option(names = "--b", description = "Inherited option B")
        int b;

        @Mixin
        MixinOpts mixin;

        @Override
        public void run() {
        }
    }

    /**
     * One command that extends a base command and has a mixin.
     * <p>
     * - {@code --a} is inherited from {@link Base} but ignored here
     * - {@code --m} comes from the mixin but is ignored on the mixin class
     */
    @IgnoreOptions(exclude = "--a")
    static class Cmd extends Base {
        @Override
        public void run() {
            System.out.println("b=" + b);
            System.out.println("(mixin is present, but its option is ignored)");
        }
    }

    public static void main(String[] args) {
        // try:
        //   --a 1      (unknown option, ignored from base)
        //   --m 2      (unknown option, ignored from mixin)
        //   --b 3
        FemtoCli.run(new Cmd(), args);
    }
}