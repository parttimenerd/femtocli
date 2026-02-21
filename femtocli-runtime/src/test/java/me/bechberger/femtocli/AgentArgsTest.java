package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentArgsTest {

    @Command(name = "types", description = "Test type conversions", mixinStandardHelpOptions = true, agentMode = true)
    static class TypesCmd implements Callable<Integer> {
        @Option(names = {"-i", "--int"}, description = "Integer option")
        int intVal;

        @Option(names = {"-s", "--string"}, description = "String option")
        String stringVal;

        @Option(names = {"-b", "--bool"}, description = "Boolean option")
        boolean boolVal;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(
            name = "root",
            description = "Root command",
            subcommands = {Sub.class},
            mixinStandardHelpOptions = true,
            agentMode = true
    )
    static class Root implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "sub",
            description = "Sub command",
            mixinStandardHelpOptions = true,
            agentMode = true
    )
    static class Sub implements Callable<Integer> {
        @Option(names = "--req", required = true, description = "Required")
        String req;

        @Option(names = "--flag", description = "flag")
        boolean flag;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "pos", description = "Positional", mixinStandardHelpOptions = true, agentMode = true)
    static class Positional implements Callable<Integer> {
        @me.bechberger.femtocli.annotations.Parameters(description = "value")
        String value;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(
            name = "deep-root",
            description = "Deep root",
            subcommands = {Level1.class},
            version = "1.2.3",
            mixinStandardHelpOptions = true,
            agentMode = true
    )
    static class DeepRoot implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "l1",
            description = "Level1",
            subcommands = {Level2.class},
            mixinStandardHelpOptions = true,
            agentMode = true
    )
    static class Level1 implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "l2",
            description = "Level2",
            mixinStandardHelpOptions = true,
            agentMode = true
    )
    static class Level2 implements Callable<Integer> {
        @Option(names = "--req", required = true, description = "Required")
        String req;

        @Option(names = {"-x"}, description = "X")
        String x;

        @Option(names = "--flag", description = "flag")
        boolean flag;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "amb", mixinStandardHelpOptions = true, agentMode = true)
    static class Amb implements Callable<Integer> {
        @Option(names = "-x")
        String shortX;

        @Option(names = "--x")
        String longX;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void runAgent_basicOptionAndFlag() {
        TypesCmd cmd = new TypesCmd();
        RunResult res = FemtoCli.runAgentCaptured(cmd, "--string=hello,-b,-i,34");
        assertEquals(0, res.exitCode());
        assertEquals("hello", cmd.stringVal);
        assertThat(cmd.boolVal).isTrue();
        assertEquals(34, cmd.intVal);
    }

    @Test
    void runAgent_escapingCommaAndEquals() {
        TypesCmd cmd = new TypesCmd();
        RunResult res = FemtoCli.runAgentCaptured(cmd, "--string=a\\,b\\=c");
        assertEquals(0, res.exitCode());
        assertEquals("a,b=c", cmd.stringVal);
    }

    @Test
    void toArgv_trimsWhitespaceAroundTokens() {
        String[] argv = FemtoCli.agentArgsToArgv("  a  ,  -x=1  ,b");
        assertThat(argv).containsExactly("a", "-x=1", "b");
    }

    @Test
    void toArgv_rejectsEmptyToken() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FemtoCli.agentArgsToArgv("--string=hello,,--bool=true"));
        assertThat(ex.getMessage()).contains("Empty token");
    }

    @Test
    void toArgv_rejectsDanglingEscape() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FemtoCli.agentArgsToArgv("--string=hello\\"));
        assertThat(ex.getMessage()).contains("Dangling escape");
    }

    @Test
    void toArgv_rejectsUnknownEscape() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FemtoCli.agentArgsToArgv("--string=hello\\n"));
        assertThat(ex.getMessage()).contains("Invalid escape sequence");
    }

    @Test
    void runAgent_subcommandChainAndOptions() {
        Root root = new Root();
        // subcommand token + option/value + boolean flag
        int code = FemtoCli.runAgent(root, "sub,req=x,--flag");
        assertEquals(0, code);
    }

    @Test
    void runAgent_subcommandHelpWorks() {
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "sub,--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: root,sub");
        assertThat(res.out()).contains("req");
    }

    @Test
    void agentArgs_endOfOptionsAllowsDashedPositional() {
        Positional cmd = new Positional();
        RunResult res = FemtoCli.runAgentCaptured(cmd, "--,--not-an-option");
        assertEquals(0, res.exitCode());
        assertEquals("--not-an-option", cmd.value);
    }

    @Test
    void agentArgs_negativePositionalRequiresEndOfOptions() {
        Positional cmd = new Positional();
        RunResult res = FemtoCli.runAgentCaptured(cmd, "--,-42");
        assertEquals(0, res.exitCode());
        assertEquals("-42", cmd.value);
    }

    @Test
    void agentArgs_emptyValueViaEqualsIsAllowed() {
        TypesCmd cmd = new TypesCmd();
        RunResult res = FemtoCli.runAgentCaptured(cmd, "--string=");
        assertEquals(0, res.exitCode());
        assertEquals("", cmd.stringVal);
    }

    @Test
    void toArgv_singleQuotesAllowCommaInsideToken() {
        String[] argv = FemtoCli.agentArgsToArgv("a,'b,c',d");
        assertThat(argv).containsExactly("a", "b,c", "d");
    }

    @Test
    void toArgv_singleQuotesAllowSpacesButWhitespaceStillTrimmed() {
        String[] argv = FemtoCli.agentArgsToArgv("'  hello  '");
        assertThat(argv).containsExactly("hello");
    }

    @Test
    void toArgv_trimsWhitespaceOutsideQuotes() {
        String[] argv = FemtoCli.agentArgsToArgv("  'x'  ,  y  ");
        assertThat(argv).containsExactly("x", "y");
    }

    @Test
    void toArgv_canUseEscapedCommaAndEqualsOutsideQuotes() {
        String[] argv = FemtoCli.agentArgsToArgv("a\\,b,c\\=d");
        assertThat(argv).containsExactly("a,b", "c=d");
    }

    @Test
    void toArgv_backslashEscapeWorksInsideSingleQuotesToo() {
        String[] argv = FemtoCli.agentArgsToArgv("'a\\,b'");
        assertThat(argv).containsExactly("a,b");
    }

    @Test
    void toArgv_unterminatedSingleQuoteIsRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FemtoCli.agentArgsToArgv("a,'b"));
        assertThat(ex.getMessage()).contains("Unterminated");
    }

    @Test
    void toArgv_rejectsEmptyTokenEvenWithQuotes() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FemtoCli.agentArgsToArgv("a,,''"));
        assertThat(ex.getMessage()).contains("Empty token");
    }

    @Test
    void runAgent_singleQuotedOptionWithCommaInValue() {
        TypesCmd cmd = new TypesCmd();
        RunResult res = FemtoCli.runAgentCaptured(cmd, "'--string=hello,world',-i,1");
        assertEquals(0, res.exitCode());
        assertEquals("hello,world", cmd.stringVal);
        assertEquals(1, cmd.intVal);
    }

    @Test
    void runAgent_singleQuotedOptionWithCommaInValue2() {
        TypesCmd cmd = new TypesCmd();
        RunResult res = FemtoCli.runAgentCaptured(cmd, "'string=hello,world',-i,1");
        assertEquals(0, res.exitCode());
        assertEquals("hello,world", cmd.stringVal);
        assertEquals(1, cmd.intVal);
    }

    @Test
    void toArgv_escapedBackslashIsSupported() {
        String[] argv = FemtoCli.agentArgsToArgv("a\\\\b");
        assertThat(argv).containsExactly("a\\b");
    }

    @Test
    void toArgv_escapeOfEqualsInsideQuotesWorks() {
        String[] argv = FemtoCli.agentArgsToArgv("'a\\=b'");
        assertThat(argv).containsExactly("a=b");
    }

    @Test
    void toArgv_invalidEscapeInsideQuotesIsRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FemtoCli.agentArgsToArgv("'a\\n'")
        );
        assertThat(ex.getMessage()).contains("Invalid escape sequence");
    }

    @Test
    void toArgv_quoteTogglingWorksAcrossTokens() {
        String[] argv = FemtoCli.agentArgsToArgv("'a',b,'c'");
        assertThat(argv).containsExactly("a", "b", "c");
    }

    @Test
    void toArgv_allowsSingleQuotedTokenContainingEquals() {
        String[] argv = FemtoCli.agentArgsToArgv("'-x=a=b'");
        assertThat(argv).containsExactly("-x=a=b");
    }

    @Test
    void toArgv_allowsCommasInsideQuotesWithoutEscapingButNotOutside() {
        String[] argv = FemtoCli.agentArgsToArgv("'a,b,c',d");
        assertThat(argv).containsExactly("a,b,c", "d");
    }

    @Test
    void runAgent_helpOnRoot() {
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: root");
    }

    @Test
    void runAgent_helpBareTokenOnRoot() {
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: root");
    }

    @Test
    void runAgent_helpOnSubcommand() {
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "sub,help");
        assertEquals(0, res.exitCode());
        assertEquals("""
                Usage: root,sub,[hV],req=<req>,[flag]
                Options:
                      flag        flag
                  h, help         Show this help message and exit.
                      req=<req>   Required (required)
                  V, version      Print version information and exit.
                """, res.out());
    }

    @Test
    void runAgent_helpOnSubcommand_withQuotedHelpToken() {
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "sub,'help'");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).startsWith("Usage: root,sub,");
    }

    @Test
    void runAgent_helpOnSubcommand_withEscapedTokenStillWorks() {
        // agent args escaping shouldn't break token recognition
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "sub,--\\=help");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Unknown option");
    }

    @Test
    void runAgent_nestedSubcommand_chainAndBareHelpAtDepth() {
        RunResult res = FemtoCli.runAgentCaptured(new DeepRoot(), "l1,l2,help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).startsWith("Usage: deep-root,l1,l2,");
        assertThat(res.out()).contains("Options:");
        assertThat(res.out()).contains("req=<req>");
    }

    @Test
    void runAgent_nestedSubcommand_missingRequiredOptionGivesUsage() {
        RunResult res = FemtoCli.runAgentCaptured(new DeepRoot(), "l1,l2");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required option");
    }

    @Test
    void runAgent_nestedSubcommand_bareOptionNormalization_longAndShort() {
        // Assert real parsed state by invoking the concrete subcommand object directly.
        // This isolates agent token normalization from subcommand instantiation.

        // bare long option name
        Level2 l2_1 = new Level2();
        RunResult res1 = FemtoCli.runAgentCaptured(l2_1, "req=ok");
        assertEquals(0, res1.exitCode(), () -> "stderr was: " + res1.err() + "\nstdout was: " + res1.out());
        assertEquals("ok", l2_1.req);

        // bare name matching -x and field name x
        Level2 l2_2 = new Level2();
        RunResult res2 = FemtoCli.runAgentCaptured(l2_2, "req=ok,x=42");
        assertEquals(0, res2.exitCode(), () -> "stderr was: " + res2.err() + "\nstdout was: " + res2.out());
        assertEquals("ok", l2_2.req);
        assertEquals("42", l2_2.x);

        // bare boolean flag token
        Level2 l2_3 = new Level2();
        RunResult res3 = FemtoCli.runAgentCaptured(l2_3, "req=ok,flag");
        assertEquals(0, res3.exitCode(), () -> "stderr was: " + res3.err() + "\nstdout was: " + res3.out());
        assertEquals("ok", l2_3.req);
        assertThat(l2_3.flag).isTrue();
    }

    @Test
    void runAgent_bareVersionTokenWorksAtDepth() {
        RunResult res = FemtoCli.runAgentCaptured(new DeepRoot(), "l1,version");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("1.2.3");
    }

    @Test
    void runAgent_quotedTokenContainingCommaAndEquals_combinedWithEscapes() {
        TypesCmd cmd = new TypesCmd();
        // one token contains comma; another uses escapes
        RunResult res = FemtoCli.runAgentCaptured(cmd, "'--string=hello, world',--string=a\\,b\\=c");
        assertEquals(0, res.exitCode());
        // last value wins
        assertEquals("a,b=c", cmd.stringVal);
    }

    @Test
    void runAgent_unterminatedQuoteIsRejectedEvenInComplexInput() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FemtoCli.runAgentCaptured(new Root(), "sub,'help"));
        assertThat(ex.getMessage()).contains("Unterminated");
    }

    // Ambiguity detection for bare option names is now a compile-time check
    // in the annotation processor, so the runtime test was removed.

    @Test
    void runAgent_helpOnCommandWithSubcommands_showsCommandsSection() {
        RunResult res = FemtoCli.runAgentCaptured(new DeepRoot(), "help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).isEqualTo("""
                Usage: deep-root,[hV],[COMMAND]
                Options:
                  h, help         Show this help message and exit.
                  V, version      Print version information and exit.
                Commands:
                  l1  Level1
                """);
    }

    @Test
    void runAgent_helpOnNestedSubcommand_showsOnlyImmediateSubcommand() {
        RunResult res = FemtoCli.runAgentCaptured(new DeepRoot(), "l1,help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).isEqualTo("""
                Usage: deep-root,l1,[hV],[COMMAND]
                Options:
                  h, help         Show this help message and exit.
                  V, version      Print version information and exit.
                Commands:
                  l2  Level2
                """);
    }

    @Test
    void runAgent_helpOnLeafCommand_showsOptionsButNoCommands() {
        RunResult res = FemtoCli.runAgentCaptured(new DeepRoot(), "l1,l2,help");
        assertEquals(0, res.exitCode());
        assertThat(res.out().trim()).isEqualTo("""
                Usage: deep-root,l1,l2,[hV],req=<req>,[x=<x>],[flag]
                Options:
                      flag        flag
                  h, help         Show this help message and exit.
                      req=<req>   Required (required)
                  V, version      Print version information and exit.
                  x=<x>           X
                """.trim());
    }
}