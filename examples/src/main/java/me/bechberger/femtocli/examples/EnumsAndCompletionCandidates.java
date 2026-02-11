package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

@Command(name = "enums")
public class EnumsAndCompletionCandidates implements Runnable {
    enum Mode { fast, safe }

    @Option(names = "--mode",
            defaultValue = "safe",
            description = "Mode (${COMPLETION-CANDIDATES}), default: ${DEFAULT-VALUE}")
    Mode mode;

    public void run() {
        System.out.println("Mode: " + mode);
    }

    public static void main(String[] args) {
        FemtoCli.run(new EnumsAndCompletionCandidates(), args);
    }
}