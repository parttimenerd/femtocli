package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import java.nio.file.Path;

@Command(name = "help-labels")
public class HelpLabelsAndDefaults implements Runnable {
    @Option(
            names = "--output",
            paramLabel = "FILE",
            defaultValue = "out.txt",
            description = "Write result to FILE (default: ${DEFAULT-VALUE})"
    )
    Path output;

    @Parameters(
            index = "0",
            paramLabel = "INPUT",
            description = "Input file"
    )
    Path input;

    @Parameters(
            index = "1",
            arity = "0..1",
            paramLabel = "LEVEL",
            defaultValue = "info",
            description = "Log level (default: ${DEFAULT-VALUE})"
    )
    String level;

    @Override
    public void run() {
        System.out.println("Input: " + input);
        System.out.println("Output: " + output);
        System.out.println("Level: " + level);
    }

    public static void main(String[] args) {
        FemtoCli.run(new HelpLabelsAndDefaults(), args);
    }
}