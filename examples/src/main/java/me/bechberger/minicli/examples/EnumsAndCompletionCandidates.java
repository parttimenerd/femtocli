package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Option;

public class EnumsAndCompletionCandidates implements Runnable {
    enum Mode { fast, safe }

    @Option(names = "--mode",
            defaultValue = "safe",
            description = "Mode (${COMPLETION-CANDIDATES}), default: ${DEFAULT-VALUE}")
    Mode mode;

    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.run(new EnumsAndCompletionCandidates(), args);
    }
}