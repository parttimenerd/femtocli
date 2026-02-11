package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Example showcasing FemtoCli agent args mode (comma-separated arguments).
 * <p>
 * Example invocations:
 * <ul>
 *   <li>{@code start,interval=1ms}</li>
 *   <li>{@code stop,output=file.jfr,verbose}</li>
 *   <li>{@code help}</li>
 *   <li>{@code version}</li>
 * </ul>
 */
@Command(
        name = "agent-cli",
        description = "Demo CLI for agent args mode",
        version = "1.0.0",
        subcommands = {AgentCli.Start.class, AgentCli.Stop.class},
        mixinStandardHelpOptions = true
)
public class AgentCli implements Runnable {

    @Override
    public void run() {
        // default action
        System.out.println("Try: start,interval=1ms or stop,output=file.jfr,verbose");
    }

    @Command(name = "start", description = "Start recording", mixinStandardHelpOptions = true)
    public static class Start implements Callable<Integer> {

        @Option(names = "--interval", defaultValue = "1ms", description = "Sampling interval")
        Duration interval;

        @Override
        public Integer call() {
            System.out.println("start: interval=" + interval);
            return 0;
        }
    }

    @Command(name = "stop", description = "Stop recording", mixinStandardHelpOptions = true)
    public static class Stop implements Callable<Integer> {
        @Parameters
        String mode;

        @Option(names = "--output", required = true, description = "Output file")
        String output;

        @Option(names = {"-v", "--verbose"}, description = "Verbose")
        boolean verbose;

        @Override
        public Integer call() {
            System.out.println("stop: mode=" + mode + ", output=" + output + ", verbose=" + verbose);
            return 0;
        }
    }

    public static void main(String[] args) {
        // Demonstrate agent mode if a single agent-args string is passed,
        // otherwise fall back to normal argv parsing.
        if (args.length == 1) {
            System.exit(FemtoCli.runAgent(new AgentCli(), args[0]));
        }
        System.exit(FemtoCli.run(new AgentCli(), args));
    }
}