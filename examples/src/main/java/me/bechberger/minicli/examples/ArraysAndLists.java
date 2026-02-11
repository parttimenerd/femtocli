package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

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
        System.exit(MiniCli.run(new ArraysAndLists(), args));
    }
}