package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

/**
 * A command with a custom header, synopsis and footer.
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
        FemtoCli.run(new CustomHeaderAndSynopsis(), args);
    }
}