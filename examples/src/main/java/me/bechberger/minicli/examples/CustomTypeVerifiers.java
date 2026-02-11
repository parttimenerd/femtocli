package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.Spec;
import me.bechberger.minicli.VerifierException;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

import java.time.Duration;

class Helpers {
    static void checkPort(int p) {
        if (p < 1 || p > 65535) throw new VerifierException("port out of range");
    }
}

@Command(name = "verifiers")
public class CustomTypeVerifiers implements Runnable {

    @Option(names = "--port", verifierMethod = "Helpers#checkPort")
    int port;

    @Override
    public void run() {
        System.out.println("port=" + port);
    }

    public static void main(String[] args) {
        MiniCli.run(new CustomTypeVerifiers(), args);
    }
}