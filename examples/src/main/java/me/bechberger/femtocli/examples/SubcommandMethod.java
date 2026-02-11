package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;

@Command(name = "myapp")
public class SubcommandMethod implements Runnable {
    @Command(name = "status", description = "Show status")
    int status() {
        System.out.println("OK");
        return 0;
    }

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        FemtoCli.run(new SubcommandMethod(), args);
    }
}