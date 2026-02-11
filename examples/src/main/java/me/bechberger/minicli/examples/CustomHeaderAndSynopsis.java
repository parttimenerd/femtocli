package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

/**
 * A command with a custom header and synopsis.
 * The header is printed above the usage message, and the synopsis replaces the default usage line.
 */
@Command(
        name = "mytool",
        header = {"My Tool", "Copyright 2026"},
        customSynopsis = {"Usage: mytool [OPTIONS] <file>"},
        description = "Process files",
        footer = """
                Examples:
                  mytool --flag
                """
)
public class CustomHeaderAndSynopsis implements Runnable {

    @Option(names = "--flag")
    boolean flag = false;

    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.run(new CustomHeaderAndSynopsis(), args);
    }
}