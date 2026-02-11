package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Option;

import java.nio.file.Path;
import java.util.List;

/**
 * Demonstrates how to use multi-value options. The option value is split by a separator and stored in a list.
 */
public class MultiValueOptions implements Runnable {
    @Option(names = "-I", description = "Include dirs")
    List<Path> includeDirs; // -I a -I b

    @Option(names = "--tags", split = ",", description = "Tags")
    List<String> tags; // --tags=a,b,c

    public void run() {
        System.out.println("Include Dirs: " + includeDirs);
        System.out.println("Tags: " + tags);
    }

    public static void main(String[] args) {
        MiniCli.run(new MultiValueOptions(), args);
    }
}