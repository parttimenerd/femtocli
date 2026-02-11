package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import java.util.List;

/**
 * Demonstrates the end-of-options marker ({@code --}).
 * <p>
 * Everything after {@code --} is treated as a positional parameter, even if it starts with {@code -}.
 */
@Command(name = "end-of-options", mixinStandardHelpOptions = true)
public class EndOfOptionsMarker implements Runnable {

    @Option(names = "--name", description = "A normal option")
    String name;

    @Parameters(index = "0..*", paramLabel = "ARGS", description = "Arguments (may start with '-')")
    List<String> args;

    @Override
    public void run() {
        System.out.println("name=" + name);
        System.out.println("args=" + args);
    }

    public static void main(String[] args) {
        System.exit(FemtoCli.run(new EndOfOptionsMarker(), args));
    }
}