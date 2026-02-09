package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

import java.nio.file.Path;

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
        MiniCli.run(new HelpLabelsAndDefaults(), args);
    }
}