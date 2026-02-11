package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;

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
        MiniCli.run(new SubcommandMethod(), args);
    }
}