package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for split delimiter handling with regex special characters.
 * Regression tests for Bug #5: Split Delimiter Regex Injection.
 * 
 * Context: String.split(String) uses REGEX patterns, which can cause unexpected
 * behavior when users provide literal delimiters that contain regex special characters.
 * For example, "." (dot) in regex matches ANY character, not just a literal dot.
 * 
 * This is fixed by using Pattern.quote() to escape special characters.
 */
public class SplitDelimiterRegressionTest {

    @Command(name = "dots")
    static class IpAddressCmd implements Runnable {
        @Option(names = "--ips", split = ".")
        String[] ips;

        @Override
        public void run() {}
    }

    @Command(name = "plus")
    static class TagCmd implements Runnable {
        @Option(names = "--tags", split = "+")
        String[] tags;

        @Override
        public void run() {}
    }

    @Command(name = "brackets")
    static class RangeCmd implements Runnable {
        @Option(names = "--ranges", split = "[0-5]")
        String[] ranges;

        @Override
        public void run() {}
    }

    @Command(name = "star")
    static class WildcardCmd implements Runnable {
        @Option(names = "--patterns", split = "*")
        String[] patterns;

        @Override
        public void run() {}
    }

    @Command(name = "pipe")
    static class PipeCmd implements Runnable {
        @Option(names = "--values", split = "|")
        String[] values;

        @Override
        public void run() {}
    }

    @Command(name = "parens")
    static class ParenCmd implements Runnable {
        @Option(names = "--items", split = "()")
        String[] items;

        @Override
        public void run() {}
    }

    @Command(name = "question")
    static class QuestionCmd implements Runnable {
        @Option(names = "--opts", split = "?")
        String[] opts;

        @Override
        public void run() {}
    }

    @Command(name = "backslash")
    static class BackslashCmd implements Runnable {
        @Option(names = "--paths", split = "\\")
        String[] paths;

        @Override
        public void run() {}
    }

    @Command(name = "caret")
    static class CaretCmd implements Runnable {
        @Option(names = "--values", split = "^")
        String[] values;

        @Override
        public void run() {}
    }

    @Command(name = "bracket")
    static class BracketCmd implements Runnable {
        @Option(names = "--items", split = "]")
        String[] items;

        @Override
        public void run() {}
    }

    @Command(name = "brace")
    static class BraceCmd implements Runnable {
        @Option(names = "--data", split = "}")
        String[] data;

        @Override
        public void run() {}
    }

    @Test
    void splitWithDotDelimiterTreatsLiterally() {
        IpAddressCmd cmd = new IpAddressCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--ips=192.168.1.1");
        assertEquals(0, res.exitCode());
        // With the fix, "." is treated as a literal dot, not "any character"
        assertThat(cmd.ips).containsExactly("192", "168", "1", "1");
    }

    @Test
    void splitWithPlusDelimiterWorks() {
        TagCmd cmd = new TagCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--tags=java+spring+boot");
        assertEquals(0, res.exitCode());
        // "+" is a regex quantifier meaning "one or more", but we treat it literally
        assertThat(cmd.tags).containsExactly("java", "spring", "boot");
    }

    @Test
    void splitWithBracketsDelimiterWorks() {
        RangeCmd cmd = new RangeCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--ranges=x[0-5]y[0-5]z");
        assertEquals(0, res.exitCode());
        // "[0-5]" is a character class in regex, but we treat it literally
        assertThat(cmd.ranges).containsExactly("x", "y", "z");
    }

    @Test
    void splitWithStarDelimiterWorks() {
        WildcardCmd cmd = new WildcardCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--patterns=a*b*c");
        assertEquals(0, res.exitCode());
        // "*" is a regex quantifier, but we treat it literally
        assertThat(cmd.patterns).containsExactly("a", "b", "c");
    }

    @Test
    void splitWithPipeDelimiterWorks() {
        PipeCmd cmd = new PipeCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--values=alpha|beta|gamma");
        assertEquals(0, res.exitCode());
        // "|" is alternation in regex, but we treat it literally
        assertThat(cmd.values).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void splitWithParenthesesDelimiterWorks() {
        ParenCmd cmd = new ParenCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--items=first()second()third");
        assertEquals(0, res.exitCode());
        // "()" are grouping in regex, but we treat them literally
        assertThat(cmd.items).containsExactly("first", "second", "third");
    }

    @Test
    void splitWithQuestionMarkDelimiterWorks() {
        QuestionCmd cmd = new QuestionCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--opts=cmd1?cmd2?cmd3");
        assertEquals(0, res.exitCode());
        // "?" is a quantifier in regex, but we treat it literally
        assertThat(cmd.opts).containsExactly("cmd1", "cmd2", "cmd3");
    }

    @Test
    void splitWithBackslashDelimiterWorks() {
        BackslashCmd cmd = new BackslashCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--paths=C:\\Users\\John");
        assertEquals(0, res.exitCode());
        // "\\" is an escape character in regex, but we treat it literally
        assertThat(cmd.paths).containsExactly("C:", "Users", "John");
    }

    @Test
    void splitWithCaretDelimiterWorks() {
        CaretCmd cmd = new CaretCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--values=start^middle^end");
        assertEquals(0, res.exitCode());
        // "^" is anchor in regex, but we treat it literally
        assertThat(cmd.values).containsExactly("start", "middle", "end");
    }

    @Test
    void splitWithCloseBracketDelimiterWorks() {
        BracketCmd cmd = new BracketCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--items=a]b]c");
        assertEquals(0, res.exitCode());
        // "]" can be problematic in character classes, but we treat it literally
        assertThat(cmd.items).containsExactly("a", "b", "c");
    }

    @Test
    void splitWithCloseBraceDelimiterWorks() {
        BraceCmd cmd = new BraceCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--data=x}y}z");
        assertEquals(0, res.exitCode());
        // "}" is quantifier syntax in regex, but we treat it literally
        assertThat(cmd.data).containsExactly("x", "y", "z");
    }

    @Test
    void splitWithDefaultValueContainingRegexChars() {
        @Command(name = "ipdefault")
        class IpDefaultCmd implements Runnable {
            @Option(names = "--ips", split = ".", defaultValue = "127.0.0.1")
            String[] ips;

            @Override
            public void run() {}
        }

        IpDefaultCmd cmd = new IpDefaultCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd);
        assertEquals(0, res.exitCode());
        // Default values should also be split with literal delimiter
        assertThat(cmd.ips).containsExactly("127", "0", "0", "1");
    }

    @Test
    void splitArrayWithRegexDelimiter() {
        @Command(name = "arraysplit")
        class ArraySplitCmd implements Callable<Integer> {
            @Option(names = "--ips", split = ".")
            String[] ips;

            @Override
            public Integer call() {
                return 0;
            }
        }

        ArraySplitCmd cmd = new ArraySplitCmd();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--ips=10.0.0.1", "--ips=192.168.1.1");
        assertEquals(0, res.exitCode());
        // Multiple occurrences should all be split correctly
        assertThat(cmd.ips).containsExactly("10", "0", "0", "1", "192", "168", "1", "1");
    }
}
