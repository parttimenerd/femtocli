package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParseOnlyTest {

    @Command(name = "parse")
    static class ParseCmd {
        @Option(names = "--count")
        int count;

        @Parameters(index = "0")
        String name;
    }

    @Test
    void parsePopulatesFieldsWithoutRunnableCallable() {
        ParseCmd cmd = new ParseCmd();

        Object parsed = FemtoCli.parse(cmd, "--count", "7", "alice");

        assertThat(parsed).isSameAs(cmd);
        assertEquals(7, cmd.count);
        assertEquals("alice", cmd.name);
    }

    @Command(name = "root", subcommands = {Child.class})
    static class Root implements Callable<Integer> {
        boolean invoked;

        @Override
        public Integer call() {
            invoked = true;
            return 0;
        }
    }

    @Command(name = "child")
    static class Child {
        @Parameters(index = "0")
        String value;
    }

    @Test
    void parseReturnsSelectedSubcommandWithoutInvokingParent() {
        Root root = new Root();

        Object parsed = FemtoCli.parse(root, "child", "x");

        assertThat(parsed).isInstanceOf(Child.class);
        assertEquals("x", ((Child) parsed).value);
        assertFalse(root.invoked, "parse() must not invoke command callbacks");
    }

    @Test
    void parseRejectsHelpVersionMode() {
        ParseCmd cmd = new ParseCmd();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FemtoCli.parse(cmd, "--help"));
        assertThat(ex.getMessage()).contains("not supported in parse mode");
    }

    @Test
    void builderParseUsesCustomConverter() {
        ParseCmd cmd = new ParseCmd();

        Object parsed = FemtoCli.builder()
            .registerType(int.class, s -> Integer.parseInt(s) * 2)
                .parse(cmd, "--count", "3", "bob");

        assertThat(parsed).isSameAs(cmd);
        assertEquals(6, cmd.count);
        assertEquals("bob", cmd.name);
    }
}
