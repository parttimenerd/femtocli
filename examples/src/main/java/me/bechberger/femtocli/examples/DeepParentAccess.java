package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.Spec;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

@Command(name = "cli", description = "Deep parent access example", subcommands = {DeepParentAccess.Database.class})
public class DeepParentAccess implements Runnable {
    @Option(names = {"-c", "--config"}, description = "Config file path", defaultValue = "default.conf")
    String config;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @Override
    public void run() {
        System.out.println("Root command executed with config: " + config);
    }

    @Command(name = "db", description = "Database operations", subcommands = {Migrate.class})
    public static class Database implements Runnable {
        Spec spec;

        @Option(names = {"-h", "--host"}, description = "Database host", defaultValue = "localhost")
        String host;

        @Option(names = {"-p", "--port"}, description = "Database port", defaultValue = "5432")
        int port;

        @Override
        public void run() {
            DeepParentAccess root = spec.getParent(DeepParentAccess.class);
            System.out.println("Database: Connecting to " + host + ":" + port);
            System.out.println("  Config: " + root.config);
            System.out.println("  Verbose: " + root.verbose);
        }
    }

    @Command(name = "migrate", description = "Run database migrations")
    public static class Migrate implements Runnable {
        Spec spec;

        @Option(names = {"-d", "--direction"}, description = "Migration direction (up/down)", defaultValue = "up")
        String direction;

        @Override
        public void run() {
            // getParent() returns the direct parent
            Database db = (Database) spec.getParent();
            // getParent(Class) searches the entire ancestor chain
            DeepParentAccess root = spec.getParent(DeepParentAccess.class);

            System.out.println("Migration " + direction);
            System.out.println("  Host: " + db.host + ":" + db.port);
            System.out.println("  Config: " + root.config + ", verbose: " + root.verbose);
        }
    }

    public static void main(String[] args) {
        FemtoCli.run(new DeepParentAccess(), args);
    }
}