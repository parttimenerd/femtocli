package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

import java.util.concurrent.Callable;

@Command(name = "greet", description = "Greet a person")
class GreetCommand implements Callable<Integer> {
    @Option(names = {"-n", "--name"}, description = "Name to greet", required = true)
    String name;

    @Option(names = {"-c", "--count"}, description = "Count (default: ${DEFAULT-VALUE})", defaultValue = "1")
    int count;

    @Override
    public Integer call() {
        for (int i = 0; i < count; i++) System.out.println("Hello, " + name + "!");
        return 0;
    }
}

@Command(name = "myapp", description = "My CLI application", version = "1.0.0",
        subcommands = {GreetCommand.class})
public class QuickStart implements Runnable {
    public void run() {
        System.out.println("Use 'myapp greet --help'");
    }

    public static void main(String[] args) {
        FemtoCli.run(new QuickStart(), args);
    }
}