package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

/**
 * Demonstrates parse-only mode, where fields are populated without invoking Runnable/Callable callbacks.
 */
@Command(name = "parse-only", description = "Populate fields without executing commands", subcommands = {ParseOnly.Server.class})
public class ParseOnly {

    @Option(names = "--profile", defaultValue = "dev", description = "Selected profile (default: ${DEFAULT-VALUE})")
    String profile;

    @Option(names = "--verbose", description = "Enable verbose mode")
    boolean verbose;

    @Command(name = "server", description = "Server configuration")
    public static class Server {
        @Option(names = "--port", defaultValue = "8080", description = "Port to bind (default: ${DEFAULT-VALUE})")
        int port;
    }

    public static Object parse(String... args) {
        return FemtoCli.parse(new ParseOnly(), args);
    }

    public static void main(String[] args) {
        Object parsed = parse(args);
        if (parsed instanceof Server server) {
            System.out.println("selected=server");
            System.out.println("port=" + server.port);
            return;
        }
        ParseOnly root = (ParseOnly) parsed;
        System.out.println("selected=root");
        System.out.println("profile=" + root.profile);
        System.out.println("verbose=" + root.verbose);
    }
}