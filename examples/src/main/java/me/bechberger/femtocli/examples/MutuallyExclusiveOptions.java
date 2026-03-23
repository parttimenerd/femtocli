package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

/**
 * Example showing how to define mutually exclusive options using {@link Option#prevents()}.
 *
 * <p>The {@link Option#prevents()} field allows you to specify which other options cannot be used
 * together with a given option. When the user provides conflicting options, FemtoCli will
 * display an error and exit with code 2.
 *
 * <p>Note: The constraint is bidirectional. If option A prevents B, you don't need to
 * specify that B prevents A - the constraint is automatically enforced when both are present.
 * However, you may want to specify it both ways for clarity and to catch errors on both sides.
 */
@Command(name = "fileutil", description = "File utility with mutually exclusive options")
public class MutuallyExclusiveOptions implements Runnable {

    @Option(
            names = {"-e", "--encrypt"},
            description = "Encrypt the file (conflicts with --decrypt and --view)",
            prevents = {"--decrypt", "-d", "--view", "-v"}
    )
    boolean encrypt;

    @Option(
            names = {"-d", "--decrypt"},
            description = "Decrypt the file (conflicts with --encrypt and --view)",
            prevents = {"--encrypt", "-e", "--view", "-v"}
    )
    boolean decrypt;

    @Option(
            names = {"-v", "--view"},
            description = "View the file (conflicts with --encrypt and --decrypt)",
            prevents = {"--encrypt", "-e", "--decrypt", "-d"}
    )
    boolean view;

    @Option(
            names = {"-q", "--quiet"},
            description = "Suppress output",
            prevents = {"-v", "--verbose"}
    )
    boolean quiet;

    @Option(
            names = "--verbose",
            description = "Show verbose output (conflicts with --quiet)",
            prevents = {"-q", "--quiet"}
    )
    boolean verbose;

    @Option(
            names = {"-f", "--file"},
            description = "Path to the file to process",
            required = true
    )
    String file;

    @Override
    public void run() {
        if (encrypt) {
            System.out.println("Encrypting " + file);
        } else if (decrypt) {
            System.out.println("Decrypting " + file);
        } else if (view) {
            System.out.println("Viewing " + file);
        }

        if (verbose) {
            System.out.println("Verbose mode enabled");
        }
        if (quiet) {
            System.out.println("Running in quiet mode");
        }
    }

    public static void main(String[] args) {
        FemtoCli.run(new MutuallyExclusiveOptions(), args);
    }
}