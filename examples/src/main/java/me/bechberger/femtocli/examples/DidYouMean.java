package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

/**
 * Demonstrates "did you mean" suggestions for mistyped options.
 *
 * <p>When you mistype an option name, femtocli suggests similar options.
 */
@Command(name = "didyoumean", description = "Example showing helpful error suggestions")
public class DidYouMean implements Runnable {

    @Option(names = "--input-file", description = "Input file to process")
    String inputFile;

    @Option(names = "--output-file", description = "Output file destination")
    String outputFile;

    @Option(names = "--verbose", description = "Enable verbose output")
    boolean verbose;

    @Override
    public void run() {
        System.out.println("Processing " + inputFile + " -> " + outputFile);
        if (verbose) {
            System.out.println("Verbose mode enabled");
        }
    }

    public static void main(String[] args) {
        System.exit(FemtoCli.run(new DidYouMean(), args));
    }
}