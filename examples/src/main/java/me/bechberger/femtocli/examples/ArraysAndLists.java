package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstrates multi-value options/parameters with both arrays and lists.
 */
@Command(name = "arrays-and-lists", mixinStandardHelpOptions = true)
public class ArraysAndLists implements Runnable {

    @Option(names = "--xs", split = ",", description = "Comma-separated values into a String[]")
    String[] xs;

    @Option(names = "--ys", split = ",", description = "Comma-separated values into a List<String>")
    List<String> ys;

    @Parameters(index = "0..*", paramLabel = "REST", description = "Remaining args")
    String[] rest;

    @Override
    public void run() {
        System.out.println("xs=" + Arrays.toString(xs));
        System.out.println("ys=" + ys);
        System.out.println("rest=" + Arrays.toString(rest));
    }

    public static void main(String[] args) {
        System.exit(FemtoCli.run(new ArraysAndLists(), args));
    }
}