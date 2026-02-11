package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;

public class GlobalConfiguration implements Runnable {

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.builder()
                .commandConfig(c -> {
                    c.version = "1.2.3";
                })
                .run(new GlobalConfiguration(), args);
    }
}