package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression tests for index-based positional parameter binding.
 * Verifies that @Parameters(index = "N") binds to the Nth positional argument,
 * not just the next available one sequentially.
 */
class PositionalIndexBindingTest {

    private static RunResult run(Object command, String... args) {
        return FemtoCli.builder().runCaptured(command, args);
    }

    // ========== Basic index-based binding ==========

    @Command(name = "two-params")
    static class TwoParamsCmd implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "SRC") String src;
        @Parameters(index = "1", paramLabel = "DST") String dst;
        @Override public Integer call() { return 0; }
    }

    @Test
    void twoExplicitIndicesBindCorrectly() {
        TwoParamsCmd cmd = new TwoParamsCmd();
        var res = run(cmd, "input.txt", "output.txt");
        assertEquals(0, res.exitCode());
        assertEquals("input.txt", cmd.src);
        assertEquals("output.txt", cmd.dst);
    }

    @Test
    void missingSecondIndexGivesError() {
        var res = run(new TwoParamsCmd(), "input.txt");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required parameter");
        assertThat(res.err()).contains("DST");
    }

    // ========== Index gap: only index 2 declared, position 0 provided ==========

    @Command(name = "gap-cmd")
    static class IndexGapCmd implements Callable<Integer> {
        @Parameters(index = "2", paramLabel = "THIRD") String third;
        @Override public Integer call() { return 0; }
    }

    @Test
    void singleArgDoesNotBindToHigherIndex() {
        // Before fix: "a" would silently bind to index-2 field. Now properly errors.
        var res = run(new IndexGapCmd(), "a");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).containsAnyOf("Missing required parameter", "Too many parameters");
    }

    @Test
    void correctIndexBindsWhenEnoughArgs() {
        // With 3 args: position 2 = "c" should bind to index-2 field.
        // But positions 0 and 1 are unconsumed → "Too many parameters"
        var res = run(new IndexGapCmd(), "a", "b", "c");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Too many parameters");
    }

    // ========== Fixed + varargs: index 0 fixed, index 1..* varargs ==========

    @Command(name = "fixed-varargs")
    static class FixedPlusVarargsCmd implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "FILE") String file;
        @Parameters(index = "1..*", paramLabel = "ARGS") List<String> args;
        @Override public Integer call() { return 0; }
    }

    @Test
    void fixedPlusVarargsBindsCorrectly() {
        FixedPlusVarargsCmd cmd = new FixedPlusVarargsCmd();
        var res = run(cmd, "main.txt", "arg1", "arg2", "arg3");
        assertEquals(0, res.exitCode());
        assertEquals("main.txt", cmd.file);
        assertThat(cmd.args).containsExactly("arg1", "arg2", "arg3");
    }

    @Test
    void fixedPlusVarargsOnlyFixed() {
        FixedPlusVarargsCmd cmd = new FixedPlusVarargsCmd();
        var res = run(cmd, "main.txt");
        assertEquals(0, res.exitCode());
        assertEquals("main.txt", cmd.file);
        assertThat(cmd.args).isNullOrEmpty();
    }

    @Test
    void fixedPlusVarargsNoArgs() {
        var res = run(new FixedPlusVarargsCmd());
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required parameter");
    }

    // ========== Varargs with single index (List + index="0") ==========

    @Command(name = "list-default")
    static class ListDefaultIndexCmd implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "FILES") List<String> files;
        @Override public Integer call() { return 0; }
    }

    @Test
    void listWithSingleIndexConsumesAllArgs() {
        // index="0" on a List field should mean "start from 0, consume all"
        ListDefaultIndexCmd cmd = new ListDefaultIndexCmd();
        var res = run(cmd, "a", "b", "c");
        assertEquals(0, res.exitCode());
        assertThat(cmd.files).containsExactly("a", "b", "c");
    }

    // ========== Varargs with bounded index range ==========

    @Command(name = "bounded-varargs")
    static class BoundedVarargsCmd implements Callable<Integer> {
        @Parameters(index = "0..2", paramLabel = "ITEMS") List<String> items;
        @Override public Integer call() { return 0; }
    }

    @Test
    void boundedVarargsConsumesOnlyInRange() {
        // index="0..2" means positions 0, 1, 2 only
        BoundedVarargsCmd cmd = new BoundedVarargsCmd();
        var res = run(cmd, "a", "b", "c");
        assertEquals(0, res.exitCode());
        assertThat(cmd.items).containsExactly("a", "b", "c");
    }

    @Test
    void boundedVarargsRejectsExtraArgs() {
        // Position 3 is out of the declared range [0..2] → "Too many parameters"
        var res = run(new BoundedVarargsCmd(), "a", "b", "c", "d");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Too many parameters");
    }

    // ========== Three explicit indices ==========

    @Command(name = "three-params")
    static class ThreeParamsCmd implements Callable<Integer> {
        @Parameters(index = "0") String first;
        @Parameters(index = "1") String second;
        @Parameters(index = "2") String third;
        @Override public Integer call() { return 0; }
    }

    @Test
    void threeExplicitIndicesBindCorrectly() {
        ThreeParamsCmd cmd = new ThreeParamsCmd();
        var res = run(cmd, "a", "b", "c");
        assertEquals(0, res.exitCode());
        assertEquals("a", cmd.first);
        assertEquals("b", cmd.second);
        assertEquals("c", cmd.third);
    }

    @Test
    void threeExplicitIndicesTooFewArgs() {
        var res = run(new ThreeParamsCmd(), "a", "b");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required parameter");
    }

    @Test
    void threeExplicitIndicesTooManyArgs() {
        var res = run(new ThreeParamsCmd(), "a", "b", "c", "d");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Too many parameters");
    }

    // ========== Optional parameter with index ==========

    @Command(name = "opt-idx")
    static class OptionalWithIndexCmd implements Callable<Integer> {
        @Parameters(index = "0") String required;
        @Parameters(index = "1", arity = "0..1", defaultValue = "default") String optional;
        @Override public Integer call() { return 0; }
    }

    @Test
    void optionalIndexParamUsesDefault() {
        OptionalWithIndexCmd cmd = new OptionalWithIndexCmd();
        var res = run(cmd, "req");
        assertEquals(0, res.exitCode());
        assertEquals("req", cmd.required);
        assertEquals("default", cmd.optional);
    }

    @Test
    void optionalIndexParamUsesProvided() {
        OptionalWithIndexCmd cmd = new OptionalWithIndexCmd();
        var res = run(cmd, "req", "val");
        assertEquals(0, res.exitCode());
        assertEquals("req", cmd.required);
        assertEquals("val", cmd.optional);
    }
}
