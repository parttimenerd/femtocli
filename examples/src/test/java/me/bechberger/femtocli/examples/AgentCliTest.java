package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCliTest {

    @Test
    public void testRootHelp() {
        var res = FemtoCli.runAgentCaptured(new AgentCli(), "help");
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: agent-cli,[hV],[COMMAND]
                Options:
                  h, help         Show this help message and exit.
                  V, version      Print version information and exit.
                Commands:
                  start  Start recording
                  stop   Stop recording
                """, res.out());
    }

    @Test
    public void testNonAgentRootHelp() {
        var res = FemtoCli.runCaptured(new AgentCli(), "--help");
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: agent-cli [-hV] [COMMAND]
                Demo CLI for agent args mode
                  -h, --help       Show this help message and exit.
                  -V, --version    Print version information and exit.
                Commands:
                  start  Start recording
                  stop   Stop recording
                """, res.out());
    }

    @Test
    public void testStopHelp() {
        var res = FemtoCli.runCaptured(new AgentCli(), "stop", "--help");
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: agent-cli stop [-hV] --output=<output> [--verbose] <mode>
                Stop recording
                      <mode>
                  -h, --help           Show this help message and exit.
                      --output=<output>
                                       Output file (required)
                  -v, --verbose        Verbose
                  -V, --version        Print version information and exit.
                """, res.out());
    }

    @Test
    public void testStartCommand() {
        var res = FemtoCli.runAgentCaptured(new AgentCli(), "start,interval=1ms");
        assertEquals(0, res.exitCode());
        assertEquals("start: interval=PT0.001S\n", res.out());
    }

    @Test
    public void testStopCommand() {
        var res = FemtoCli.runAgentCaptured(new AgentCli(), "stop,jfr,output=file.jfr,verbose");
        assertEquals(0, res.exitCode());
        assertEquals("stop: mode=jfr, output=file.jfr, verbose=true\n", res.out());
    }
}