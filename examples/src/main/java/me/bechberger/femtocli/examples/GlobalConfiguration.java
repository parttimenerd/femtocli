package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;

public class GlobalConfiguration implements Runnable {

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        FemtoCli.builder()
                .commandConfig(c -> {
                    c.version = "1.2.3";
                })
                .run(new GlobalConfiguration(), args);
    }
}