package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.Spec;
import me.bechberger.minicli.VerifierException;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

import java.time.Duration;

@Command(name = "tiny", description = "Tiny demo")
public class VerifierAndConverterMethods implements Runnable {

    @Option(names = {"-i", "--interval"}, defaultValue = "10ms",
            description = "Polling interval (e.g. 10ms, 1s)")
    Duration interval;

    @Option(names = "--port", converterMethod = "com.example.Helpers#parsePort", verifierMethod = "checkPort")
    int port;

    // instance verifier method (called via verifierMethod = "checkPort")
    void checkPort(int p) {
        if (p < 1 || p > 65535) throw new VerifierException("port out of range");
    }

    @Override
    public void run() {
        System.out.println("interval(ms)=" + interval.toMillis());
        System.out.println("port=" + port);
    }

    public static void main(String[] args) {
        MiniCli.run(new VerifierAndConverterMethods(), System.out, System.err, args);
    }
}