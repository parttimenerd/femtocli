package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

/**
 * Demonstrates boolean options as flags and with explicit values.
 *
 * <p>Supported forms:</p>
 * <ul>
 *   <li>{@code --prim} (flag style, sets to true)</li>
 *   <li>{@code --prim false} (explicit value as separate token)</li>
 *   <li>{@code --boxed=false} (explicit value with equals)</li>
 * </ul>
 */
@Command(name = "bools", description = "Boolean option parsing example")
public class BooleanExplicitValues implements Runnable {

    @Option(names = "--boxed", description = "Boxed boolean (Boolean)")
    Boolean boxed;

    @Option(names = "--prim", description = "Primitive boolean (boolean)")
    boolean prim;

    @Override
    public void run() {
        System.out.println("boxed=" + boxed);
        System.out.println("prim=" + prim);
    }

    public static void main(String[] args) {
        System.exit(MiniCli.run(new BooleanExplicitValues(), args));
    }
}