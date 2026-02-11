package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.VerifierException;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

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
        FemtoCli.run(new CustomTypeVerifiers(), args);
    }
}