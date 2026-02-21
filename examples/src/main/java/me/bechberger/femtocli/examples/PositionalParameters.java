package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Parameters;

import java.util.List;

/**
 * Shows how to use positional parameters.
 * Positional parameters are defined by their index and are not prefixed by an option name.
 */
@Command(name = "positionalparameters")
public class PositionalParameters implements Runnable {
    @Parameters(index = "0", paramLabel = "FILE", description = "Input file")
    String file;

    @Parameters(index = "1..*", paramLabel = "ARGS", description = "Extra arguments")
    List<String> args;

    @Override
    public void run() {
        System.out.println("File: " + file);
        System.out.println("Args: " + args);
    }

    public static void main(String[] args) {
        FemtoCli.run(new PositionalParameters(), args);
    }
}