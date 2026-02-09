package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Parameters;

import java.util.List;

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
        MiniCli.run(new MultiValueOptions(), args);
    }
}