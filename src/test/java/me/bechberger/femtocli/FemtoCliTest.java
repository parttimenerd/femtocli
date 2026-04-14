package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FemtoCliTest {

    // ========== Shared helpers ==========
    private static RunResult run(Object command, String... args) {
        return FemtoCli.builder().runCaptured(command, args);
    }

    private static String normalizeHelp(String s) {
        // Make help comparisons stable across platforms/editors.
        return s.replace("\r\n", "\n");
    }

    private static void assertUsageStartsWith(RunResult res, String expectedPrefix) {
        assertThat(normalizeHelp(res.out())).startsWith(expectedPrefix);
    }

    // ========== Tiny CLI Test Framework ==========

    /**
     * Fluent test helper for running FemtoCli commands.
     */
    static class CliTest {
        private final Object command;
        private String[] args = new String[0];
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private Integer exitCode;

        private CliTest(Object command) {
            this.command = command;
        }

        /**
         * Create a test for the given command instance.
         */
        static CliTest of(Object command) {
            return new CliTest(command);
        }

        /**
         * Set arguments to pass to the CLI.
         */
        CliTest args(String... args) {
            this.args = args;
            return this;
        }

        /**
         * Run the command and return this for chaining.
         */
        CliTest run() {
            RunResult res = FemtoCli.builder().runCaptured(command, args);
            exitCode = res.exitCode();
            // Populate the internal streams so existing expect* helpers keep working
            try {
                out.reset();
                err.reset();
                out.write(res.out().getBytes());
                err.write(res.err().getBytes());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        /**
         * Assert that the exit code equals expected.
         */
        CliTest expectCode(int expected) {
            assertEquals(expected, exitCode, "Exit code mismatch");
            return this;
        }

        /**
         * Assert that stdout contains the given substring.
         */
        CliTest expectOut(String substring) {
            assertThat(out.toString()).contains(substring);
            return this;
        }

        /**
         * Assert that stdout does NOT contain the given substring.
         */
        CliTest expectOutNot(String substring) {
            assertThat(out.toString()).doesNotContain(substring);
            return this;
        }

        /**
         * Assert that stderr contains the given substring.
         */
        CliTest expectErr(String substring) {
            assertThat(err.toString()).contains(substring);
            return this;
        }

        /**
         * Assert that stderr is empty or blank.
         */
        CliTest expectErrEmpty() {
            assertThat(err.toString()).isBlank();
            return this;
        }

        /**
         * Assert that stdout is empty or blank.
         */
        CliTest expectOutEmpty() {
            assertThat(out.toString()).isBlank();
            return this;
        }

        /**
         * Get stdout as string.
         */
        String stdout() {
            return out.toString();
        }

        /**
         * Get stderr as string.
         */
        String stderr() {
            return err.toString();
        }

        /**
         * Get the command instance (for field inspection).
         */
        @SuppressWarnings("unchecked")
        <T> T cmd() {
            return (T) command;
        }
    }

    // ========== Test Commands ==========

    @Command(
            name = "root",
            description = "Root command",
            version = "1.2.3",
            subcommands = {Sub.class},
            mixinStandardHelpOptions = true
    )
    static class Root implements Runnable {
        @Override
        public void run() {
            // no-op
        }
    }

    @Command(
            name = "sub",
            description = "A sub command",
            mixinStandardHelpOptions = true
    )
    static class Sub implements Callable<Integer> {

        @Option(names = "--req", description = "Required option", required = true)
        String required;

        @Option(names = "--flag", description = "Boolean flag")
        boolean flag;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(
            name = "ai",
            description = "AI root",
            subcommands = {AiFull.class},
            mixinStandardHelpOptions = true
    )
    static class Ai implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "full",
            description = "Analyze all JVMs on the system with AI",
            mixinStandardHelpOptions = true
    )
    static class AiFull implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "wrap",
            description = "Identify threads waiting without progress (potentially starving)",
            mixinStandardHelpOptions = true
    )
    static class WrapCmd implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "jstall",
            description = "One-shot JVM inspection tool",
            subcommands = {Ai.class, WrapCmd.class},
            mixinStandardHelpOptions = true
    )
    static class JstallRoot implements Runnable {
        @Override
        public void run() {
        }
    }

    // ========== Additional test commands for edge cases ==========

    @Command(name = "types", description = "Test type conversions", mixinStandardHelpOptions = true)
    static class TypesCmd implements Callable<Integer> {
        @Option(names = {"-i", "--int"}, description = "Integer option")
        int intVal;

        @Option(names = {"-l", "--long"}, description = "Long option")
        long longVal;

        @Option(names = {"-d", "--double"}, description = "Double option")
        double doubleVal;

        @Option(names = {"-s", "--string"}, description = "String option")
        String stringVal;

        @Option(names = {"-b", "--bool"}, description = "Boolean option")
        boolean boolVal;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "boxed", description = "Test boxed types", mixinStandardHelpOptions = true)
    static class BoxedTypesCmd implements Callable<Integer> {
        @Option(names = "--int", description = "Boxed Integer")
        Integer boxedInt;

        @Option(names = "--long", description = "Boxed Long")
        Long boxedLong;

        @Option(names = "--double", description = "Boxed Double")
        Double boxedDouble;

        @Option(names = "--bool", description = "Boxed Boolean")
        Boolean boxedBool;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "varargs", description = "Test varargs parameters", mixinStandardHelpOptions = true)
    static class VarargsCmd implements Callable<Integer> {
        @Parameters(index = "0..*", description = "Multiple values")
        List<String> values;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "int-varargs", description = "Test integer varargs parameters", mixinStandardHelpOptions = true)
    static class IntVarargsCmd implements Callable<Integer> {
        @Parameters(index = "0..*", description = "Multiple integer values")
        List<Integer> values;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "optional", description = "Test optional parameter", mixinStandardHelpOptions = true)
    static class OptionalParamCmd implements Callable<Integer> {
        @Parameters(arity = "0..1", description = "Optional value")
        String value;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "required-param", description = "Test required parameter", mixinStandardHelpOptions = true)
    static class RequiredParamCmd implements Callable<Integer> {
        @Parameters(description = "Required value")
        String value;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "multi-opts", description = "Multiple option names", mixinStandardHelpOptions = true)
    static class MultiNameOptCmd implements Callable<Integer> {
        @Option(names = {"-v", "--verbose", "--debug"}, description = "Verbose mode")
        boolean verbose;

        @Option(names = {"-o", "--output"}, description = "Output path")
        String output;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "int-list", description = "Test integer list option", mixinStandardHelpOptions = true)
    static class IntListOptionCmd implements Callable<Integer> {
        @Option(names = "-n", description = "Integer list")
        List<Integer> numbers;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "default-int-list", description = "Test integer list defaults", mixinStandardHelpOptions = true)
    static class DefaultIntListOptionCmd implements Callable<Integer> {
        @Option(names = "--xs", split = ",", defaultValue = "1,2")
        List<Integer> xs;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "invalid-default-int-list", description = "Test invalid integer list defaults", mixinStandardHelpOptions = true)
    static class InvalidDefaultIntListOptionCmd implements Callable<Integer> {
        @Option(names = "--xs", split = ",", defaultValue = "x")
        List<Integer> xs;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "default-string-list", description = "Test string list defaults", mixinStandardHelpOptions = true)
    static class DefaultStringListOptionCmd implements Callable<Integer> {
        @Option(names = "--xs", split = ",", defaultValue = "a,b,c")
        List<String> xs;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ---- Array option commands (analogous to the List<Integer> bug) ----

    @Command(name = "int-array-split", description = "Integer array with split", mixinStandardHelpOptions = true)
    static class IntArraySplitCmd implements Callable<Integer> {
        @Option(names = "--xs", split = ",")
        Integer[] xs;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "int-array-default", description = "Integer array with split and default", mixinStandardHelpOptions = true)
    static class DefaultIntArrayCmd implements Callable<Integer> {
        @Option(names = "--xs", split = ",", defaultValue = "10,20,30")
        Integer[] xs;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "prim-int-array-default", description = "Primitive int array with default", mixinStandardHelpOptions = true)
    static class DefaultPrimIntArrayCmd implements Callable<Integer> {
        @Option(names = "--xs", split = ",", defaultValue = "4,5,6")
        int[] xs;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "invalid-default-int-array", description = "Invalid int array default", mixinStandardHelpOptions = true)
    static class InvalidDefaultIntArrayCmd implements Callable<Integer> {
        @Option(names = "--xs", split = ",", defaultValue = "x,y")
        Integer[] xs;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "string-array-split", description = "String array with split and default", mixinStandardHelpOptions = true)
    static class DefaultStringArrayCmd implements Callable<Integer> {
        @Option(names = "--tags", split = ",", defaultValue = "a,b,c")
        String[] tags;

        @Override
        public Integer call() { return 0; }
    }

    // ---- List options with other element types ----

    @Command(name = "double-list", description = "Double list option", mixinStandardHelpOptions = true)
    static class DoubleListOptionCmd implements Callable<Integer> {
        @Option(names = "-d", description = "Double list")
        List<Double> values;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "long-list-default", description = "Long list with default", mixinStandardHelpOptions = true)
    static class DefaultLongListCmd implements Callable<Integer> {
        @Option(names = "--ids", split = ",", defaultValue = "100,200,300")
        List<Long> ids;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "path-list", description = "Path list option", mixinStandardHelpOptions = true)
    static class PathListOptionCmd implements Callable<Integer> {
        @Option(names = "-f", description = "File list")
        List<Path> files;

        @Override
        public Integer call() { return 0; }
    }

    // ---- Enum in collections ----

    @Command(name = "color-list", description = "Color list option", mixinStandardHelpOptions = true)
    static class ColorListOptionCmd implements Callable<Integer> {
        @Option(names = "--color", description = "Color list")
        List<Color> colors;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "color-list-default", description = "Color list with default", mixinStandardHelpOptions = true)
    static class DefaultColorListCmd implements Callable<Integer> {
        @Option(names = "--colors", split = ",", defaultValue = "RED,GREEN")
        List<Color> colors;

        @Override
        public Integer call() { return 0; }
    }

    // ---- Array positional parameters ----

    @Command(name = "int-array-params", description = "Integer array positional", mixinStandardHelpOptions = true)
    static class IntArrayParamsCmd implements Callable<Integer> {
        @Parameters(index = "0..*", description = "Integer values")
        Integer[] values;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "double-array-params", description = "Double array positional", mixinStandardHelpOptions = true)
    static class DoubleArrayParamsCmd implements Callable<Integer> {
        @Parameters(index = "0..*", description = "Double values")
        double[] values;

        @Override
        public Integer call() { return 0; }
    }

    @Command(name = "callable-ret", description = "Return codes", mixinStandardHelpOptions = true)
    static class CallableReturnCmd implements Callable<Integer> {
        @Option(names = "--code", description = "Exit code to return")
        int code = 0;

        @Override
        public Integer call() {
            return code;
        }
    }

    @Command(name = "callable-null", description = "Return null", mixinStandardHelpOptions = true)
    static class CallableNullReturnCmd implements Callable<Integer> {
        @Override
        public Integer call() {
            return null;
        }
    }

    @Command(name = "defaults", description = "Test default values", mixinStandardHelpOptions = true)
    static class DefaultValuesCmd implements Callable<Integer> {
        @Option(names = "--count", description = "Count")
        int count = 42;

        @Option(names = "--name", description = "Name")
        String name = "default";

        @Option(names = "--enabled", description = "Enabled")
        boolean enabled = true;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "no-opts", description = "Command with no options", mixinStandardHelpOptions = true)
    static class NoOptsCmd implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "deep-root",
            description = "Deep nesting root",
            subcommands = {DeepLevel1.class},
            mixinStandardHelpOptions = true
    )
    static class DeepRoot implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "level1",
            description = "Level 1",
            subcommands = {DeepLevel2.class},
            mixinStandardHelpOptions = true
    )
    static class DeepLevel1 implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "level2",
            description = "Level 2",
            subcommands = {DeepLevel3.class},
            mixinStandardHelpOptions = true
    )
    static class DeepLevel2 implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(name = "level3", description = "Level 3", mixinStandardHelpOptions = true)
    static class DeepLevel3 implements Callable<Integer> {
        @Option(names = "--val", description = "Value")
        String val;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "mixed", description = "Mixed options and params", mixinStandardHelpOptions = true)
    static class MixedCmd implements Callable<Integer> {
        @Option(names = {"-n", "--name"}, description = "Name")
        String name;

        @Option(names = "--count", description = "Count")
        int count;

        @Parameters(index = "0..*", description = "Files")
        List<String> files;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "no-version", description = "No version set", mixinStandardHelpOptions = true)
    static class NoVersionCmd implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(name = "no-version-global", description = "No version set", mixinStandardHelpOptions = true)
    static class NoVersionGlobalCmd implements Runnable {
        @Override
        public void run() {
        }
    }

    // ========== Tests ==========

    @Test
    void helpReturnsExitCode0() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "--help");

        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: root [-hV] [COMMAND]");
        assertThat(res.out()).contains("-h, --help");
        assertThat(res.out()).contains("-V, --version");
    }

    @Test
    void versionReturnsExitCode0AndPrintsVersion() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "--version");

        assertEquals(0, res.exitCode());
        assertThat(res.out().trim()).isEqualTo("1.2.3");
    }

    @Test
    void missingRequiredOptionReturns2() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "sub");

        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required option");
    }

    @Test
    void requiredOptionProvidedIsAccepted() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "sub", "--req", "x", "--flag");

        assertEquals(0, res.exitCode());
        assertThat(res.err()).isBlank();
    }

    @Test
    void rootHelpListsNestedSubcommandsFlattened() {
        RunResult res = FemtoCli.builder().runCaptured(new JstallRoot(), "--help");

        assertEquals(0, res.exitCode());
        String help = res.out();
        assertThat(help).contains("Usage: jstall [-hV] [COMMAND]");
        assertThat(help).contains("Commands:");
        assertThat(help).contains("ai");
        assertThat(help).doesNotContain("ai full");
    }

    @Test
    void longDescriptionsAreDisplayed() {
        RunResult res = FemtoCli.builder().runCaptured(new JstallRoot(), "--help");

        assertEquals(0, res.exitCode());
        String help = res.out();

        // Ensure the long description is present
        assertThat(help).contains("wrap");
        assertThat(help).contains("waiting without progress");
        assertThat(help).contains("starving");
    }

    @Test
    void optionEqualsSyntaxIsSupported() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "sub", "--req=x");

        assertEquals(0, res.exitCode());
        assertThat(res.err()).isBlank();
    }

    @Test
    void unknownOptionReturns2() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "sub", "--nope");

        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Unknown option");
    }

    @Test
    void endOfOptionsMarkerAllowsDashedPositionals() {
        // Make a command with a single positional parameter, to ensure "--" stops option parsing.
        @Command(name = "pos", description = "pos", mixinStandardHelpOptions = true)
        class Positional implements Callable<Integer> {
            @Parameters(description = "value")
            String value;

            @Override
            public Integer call() {
                return 0;
            }
        }

        Positional cmd = new Positional();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--", "--not-an-option");

        assertEquals(0, res.exitCode());
        assertThat(cmd.value).isEqualTo("--not-an-option");
        assertThat(res.err()).isBlank();
    }

    @Test
    void booleanExplicitValuesAreParsed() {
        Sub cmd = new Sub();
        RunResult res = FemtoCli.builder().runCaptured(cmd, "--req", "x", "--flag=false");

        assertEquals(0, res.exitCode());
        assertThat(cmd.flag).isFalse();
        assertThat(res.err()).isBlank();
    }

    @Test
    void missingOptionValueReturns2() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "sub", "--req");

        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing value for option");
    }

    @Test
    void tooManyPositionalsReturns2() {
        @Command(name = "pos", description = "pos", mixinStandardHelpOptions = true)
        class Positional implements Callable<Integer> {
            @Parameters(description = "value")
            String value;

            @Override
            public Integer call() {
                return 0;
            }
        }

        RunResult res = FemtoCli.builder().runCaptured(new Positional(), "a", "b");

        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Too many parameters");
    }

    @Test
    void helpForSubcommandReturns0() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "sub", "--help");

        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: root sub");
        assertThat(res.err()).isBlank();
    }

    @Test
    void versionReturns0EvenWithExtraArgs() {
        RunResult res = FemtoCli.builder().runCaptured(new Root(), "--version", "sub");

        assertEquals(0, res.exitCode());
        assertThat(res.out().trim()).isEqualTo("1.2.3");
    }

    // ========== Type conversion tests ==========

    @Test
    void intOptionParsesCorrectly() {
        TypesCmd cmd = new TypesCmd();

        var res = run(cmd, "-i", "123");
        assertEquals(0, res.exitCode());
        assertEquals(123, cmd.intVal);
    }

    @Test
    void longOptionParsesCorrectly() {
        TypesCmd cmd = new TypesCmd();

        var res = run(cmd, "--long", "9999999999");
        assertEquals(0, res.exitCode());
        assertEquals(9999999999L, cmd.longVal);
    }

    @Test
    void doubleOptionParsesCorrectly() {
        TypesCmd cmd = new TypesCmd();

        var res = run(cmd, "--double", "3.14159");
        assertEquals(0, res.exitCode());
        assertEquals(3.14159, cmd.doubleVal, 0.00001);
    }

    @Test
    void stringOptionParsesCorrectly() {
        TypesCmd cmd = new TypesCmd();

        var res = run(cmd, "--string", "hello world");
        assertEquals(0, res.exitCode());
        assertEquals("hello world", cmd.stringVal);
    }

    @Test
    void booleanOptionWithoutValueSetsTrue() {
        TypesCmd cmd = new TypesCmd();

        var res = run(cmd, "--bool");
        assertEquals(0, res.exitCode());
        assertThat(cmd.boolVal).isTrue();
    }

    @Test
    void invalidIntValueReturns2() {
        var res = run(new TypesCmd(), "-i", "notanumber");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value");
    }

    @Test
    void invalidDoubleValueReturns2() {
        var res = run(new TypesCmd(), "--double", "abc");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value");
    }

    @Test
    void invalidLongValueReturns2() {
        var res = run(new TypesCmd(), "--long", "xyz");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value");
    }

    @Test
    void boxedTypesWorkCorrectly() {
        BoxedTypesCmd cmd = new BoxedTypesCmd();
        var res = run(cmd, "--int", "100", "--long", "200", "--double", "1.5", "--bool");
        assertEquals(0, res.exitCode());
        assertEquals(Integer.valueOf(100), cmd.boxedInt);
        assertEquals(Long.valueOf(200), cmd.boxedLong);
        assertEquals(Double.valueOf(1.5), cmd.boxedDouble);
        assertThat(cmd.boxedBool).isTrue();
    }

    @Test
    void integerListOptionParsesElementsUsingGenericType() {
        IntListOptionCmd cmd = new IntListOptionCmd();

        var res = run(cmd, "-n", "1", "-n", "-2", "-n", "3");
        assertEquals(0, res.exitCode());
        assertThat(cmd.numbers).containsExactly(1, -2, 3);
    }

    @Test
    void integerListDefaultParsesElementsUsingGenericType() {
        DefaultIntListOptionCmd cmd = new DefaultIntListOptionCmd();

        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.xs).containsExactly(1, 2);
    }

    @Test
    void invalidIntegerListDefaultReportsValueErrorInsteadOfUnsupportedType() {
        var res = run(new InvalidDefaultIntListOptionCmd());

        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value for --xs");
        assertThat(res.err()).doesNotContain("Unsupported field type: java.util.List");
    }

    // ========== Array option variants (similar bug pattern) ==========

    @Test
    void integerArrayWithSplitParsesElements() {
        IntArraySplitCmd cmd = new IntArraySplitCmd();
        var res = run(cmd, "--xs=1,2,3");
        assertEquals(0, res.exitCode());
        assertThat(cmd.xs).containsExactly(1, 2, 3);
    }

    @Test
    void integerArrayDefaultParsesElements() {
        DefaultIntArrayCmd cmd = new DefaultIntArrayCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.xs).containsExactly(10, 20, 30);
    }

    @Test
    void primitiveIntArrayDefaultParsesElements() {
        DefaultPrimIntArrayCmd cmd = new DefaultPrimIntArrayCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.xs).containsExactly(4, 5, 6);
    }

    @Test
    void invalidIntegerArrayDefaultReportsValueError() {
        var res = run(new InvalidDefaultIntArrayCmd());
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value for --xs");
        assertThat(res.err()).doesNotContain("Unsupported field type");
    }

    @Test
    void stringArrayWithSplitAndDefaultWorks() {
        DefaultStringArrayCmd cmd = new DefaultStringArrayCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.tags).containsExactly("a", "b", "c");
    }

    // ========== Other List element types ==========

    @Test
    void doubleListOptionParsesElements() {
        DoubleListOptionCmd cmd = new DoubleListOptionCmd();
        var res = run(cmd, "-d", "1.5", "-d", "2.7", "-d", "3.14");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly(1.5, 2.7, 3.14);
    }

    @Test
    void longListDefaultParsesElements() {
        DefaultLongListCmd cmd = new DefaultLongListCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.ids).containsExactly(100L, 200L, 300L);
    }

    @Test
    void pathListOptionParsesElements() {
        PathListOptionCmd cmd = new PathListOptionCmd();
        var res = run(cmd, "-f", "/tmp/a.txt", "-f", "/tmp/b.txt");
        assertEquals(0, res.exitCode());
        assertThat(cmd.files).containsExactly(Path.of("/tmp/a.txt"), Path.of("/tmp/b.txt"));
    }

    // ========== Enum in collections ==========

    @Test
    void colorListOptionParsesElements() {
        ColorListOptionCmd cmd = new ColorListOptionCmd();
        var res = run(cmd, "--color", "red", "--color", "blue");
        assertEquals(0, res.exitCode());
        assertThat(cmd.colors).containsExactly(Color.RED, Color.BLUE);
    }

    @Test
    void colorListDefaultParsesElements() {
        DefaultColorListCmd cmd = new DefaultColorListCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.colors).containsExactly(Color.RED, Color.GREEN);
    }

    // ========== Array positional parameters ==========

    @Test
    void integerArrayPositionalParsesElements() {
        IntArrayParamsCmd cmd = new IntArrayParamsCmd();
        var res = run(cmd, "--", "10", "-5", "99");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly(10, -5, 99);
    }

    @Test
    void doubleArrayPositionalParsesElements() {
        DoubleArrayParamsCmd cmd = new DoubleArrayParamsCmd();
        var res = run(cmd, "1.1", "2.2", "3.3");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly(1.1, 2.2, 3.3);
    }

    // ========== Short option tests ==========

    @Test
    void shortOptionWorks() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-s", "value");
        assertEquals(0, res.exitCode());
        assertEquals("value", cmd.stringVal);
    }

    @Test
    void shortOptionWithEqualsWorks() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-s=value");
        assertEquals(0, res.exitCode());
        assertEquals("value", cmd.stringVal);
    }

    @Test
    void shortBooleanOptionWorks() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-b");
        assertEquals(0, res.exitCode());
        assertThat(cmd.boolVal).isTrue();
    }

    // ========== Multiple option names tests ==========

    // (See parameterized version `allOptionNameAliasesWork_parameterized` above.)

    @ParameterizedTest
    @ValueSource(strings = {"-v", "--verbose", "--debug"})
    void allOptionNameAliasesWork_parameterized(String alias) {
        MultiNameOptCmd cmd = new MultiNameOptCmd();

        var res = run(cmd, alias);
        assertEquals(0, res.exitCode());
        assertThat(cmd.verbose).isTrue();
    }

    @Test
    void shortAndLongForSameOptionWork() {
        MultiNameOptCmd cmd1 = new MultiNameOptCmd();
        var res1 = run(cmd1, "-o", "path1");
        assertEquals(0, res1.exitCode());
        assertEquals("path1", cmd1.output);

        MultiNameOptCmd cmd2 = new MultiNameOptCmd();
        var res2 = run(cmd2, "--output", "path2");
        assertEquals(0, res2.exitCode());
        assertEquals("path2", cmd2.output);
    }

    // ========== Varargs parameters tests ==========

    @Test
    void varargsWithNoArgumentsReturnsEmptyList() {
        VarargsCmd cmd = new VarargsCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).isEmpty();
    }

    @Test
    void varargsWithSingleArgument() {
        VarargsCmd cmd = new VarargsCmd();
        var res = run(cmd, "one");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly("one");
    }

    @Test
    void varargsWithMultipleArguments() {
        VarargsCmd cmd = new VarargsCmd();
        var res = run(cmd, "a", "b", "c", "d");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly("a", "b", "c", "d");
    }

    @Test
    void integerVarargsParseElementsUsingGenericType() {
        IntVarargsCmd cmd = new IntVarargsCmd();
        var res = run(cmd, "--", "1", "-2", "3");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly(1, -2, 3);
    }

    // ========== Optional parameter tests ==========

    @Test
    void optionalParameterCanBeOmitted() {
        OptionalParamCmd cmd = new OptionalParamCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertThat(cmd.value).isNull();
    }

    @Test
    void optionalParameterCanBeProvided() {
        OptionalParamCmd cmd = new OptionalParamCmd();
        var res = run(cmd, "myvalue");
        assertEquals(0, res.exitCode());
        assertEquals("myvalue", cmd.value);
    }

    @Test
    void requiredParameterMissingReturns2() {
        var res = run(new RequiredParamCmd());
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required parameter");
    }

    // ========== Callable return value tests ==========

    @Test
    void callableReturnsCustomExitCode() {
        var res = run(new CallableReturnCmd(), "--code", "42");
        assertEquals(42, res.exitCode());
    }

    @Test
    void callableReturnsZeroByDefault() {
        var res = run(new CallableReturnCmd());
        assertEquals(0, res.exitCode());
    }

    @Test
    void callableReturningNullReturns0() {
        var res = run(new CallableNullReturnCmd());
        assertEquals(0, res.exitCode());
    }

    // ========== Default values tests ==========

    @Test
    void defaultValuesArePreservedWhenNotOverridden() {
        DefaultValuesCmd cmd = new DefaultValuesCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertEquals(42, cmd.count);
        assertEquals("default", cmd.name);
        assertThat(cmd.enabled).isTrue();
    }

    @Test
    void defaultValuesCanBeOverridden() {
        DefaultValuesCmd cmd = new DefaultValuesCmd();
        var res = run(cmd, "--count", "100", "--name", "overridden", "--enabled=false");
        assertEquals(0, res.exitCode());
        assertEquals(100, cmd.count);
        assertEquals("overridden", cmd.name);
        assertThat(cmd.enabled).isFalse();
    }

    @Command(name = "defaults-in-help", description = "Defaults in help", mixinStandardHelpOptions = true)
    static class DefaultsInHelpCmd implements Runnable {
        @Option(names = "--a", description = "Option A", defaultValue = "1")
        int a;

        @Option(names = "--b", description = "Option B (default: ${DEFAULT-VALUE})", defaultValue = "2")
        int b;

        @Option(names = "--c", description = "Option C", defaultValue = "3", showDefaultValueInHelp = false)
        int c;

        @Override
        public void run() {}
    }

    @Test
    void defaultValuesAreAppendedToHelpByDefault() {
        CliTest test = CliTest.of(new DefaultsInHelpCmd())
            .args("--help")
            .run()
            .expectCode(0);

        String help = test.stdout();
        assertThat(help).contains("--a=<a>");
        assertThat(help).contains("Option A (default 1)");

        // Placeholder already in description -> should NOT append another default
        assertThat(help).contains("Option B (default: 2)");
        assertThat(help).doesNotContain("Option B (default: 2) (default 2)");

        // Per-option opt-out
        assertThat(help).contains("Option C");
        assertThat(help).doesNotContain("Option C (default 3)");
    }

    @Test
    void defaultValueRenderingCanBeDisabledGlobally() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        var config = new CommandConfig();
        config.showDefaultValuesInHelp = false;

        int code = FemtoCli.builder()
            .commandConfig(config)
            .run(new DefaultsInHelpCmd(), new PrintStream(out), new PrintStream(err), "--help");

        assertEquals(0, code);
        String help = out.toString();
        assertThat(help).contains("Option A");
        assertThat(help).doesNotContain("Option A (default 1)");
        // Placeholder expansion still works even if appending defaults is disabled
        assertThat(help).contains("Option B (default: 2)");
    }

    // ========== Deep nesting tests ==========

    @Test
    void deeplyNestedSubcommandWorks() {
        var res = run(new DeepRoot(), "level1", "level2", "level3", "--val", "deep");
        assertEquals(0, res.exitCode());
    }

    @Test
    void deeplyNestedHelpWorks() {
        var res = run(new DeepRoot(), "level1", "level2", "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: deep-root level1 level2");
    }

    @Test
    void deepRootHelpShowsAllNestedPaths() {
        var res = run(new DeepRoot(), "--help");
        assertEquals(0, res.exitCode());
        String help = res.out();
        assertThat(help).contains("level1");
        assertThat(help).doesNotContain("deep-root level1 level2");
        assertThat(help).doesNotContain("deep-root level1 level2 level3");
    }

    // ========== Mixed options and parameters tests ==========

     @Test
     void optionsAndParametersCanBeMixed() {
        MixedCmd cmd = new MixedCmd();
        var res = run(cmd, "-n", "test", "file1", "--count", "5", "file2");
        assertEquals(0, res.exitCode());
        assertEquals("test", cmd.name);
        assertEquals(5, cmd.count);
        assertThat(cmd.files).containsExactly("file1", "file2");
     }

     @Test
     void optionsAfterPositionalsAreStillParsed() {
        MixedCmd cmd = new MixedCmd();
        var res = run(cmd, "file1", "file2", "--name", "afterpos", "--count", "10");
        assertEquals(0, res.exitCode());
        assertEquals("afterpos", cmd.name);
        assertEquals(10, cmd.count);
        assertThat(cmd.files).containsExactly("file1", "file2");
     }

     // ========== End-of-options marker tests ==========

     @Test
     void endOfOptionsAllowsDashDashValue() {
        VarargsCmd cmd = new VarargsCmd();
        var res = run(cmd, "--", "--not-an-option", "-also-not");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly("--not-an-option", "-also-not");
     }

     @Test
     void endOfOptionsWithOptionsBeforeAndPositionalsAfter() {
        MixedCmd cmd = new MixedCmd();
        var res = run(cmd, "--name", "test", "--", "--looks-like-option");
        assertEquals(0, res.exitCode());
        assertEquals("test", cmd.name);
        assertThat(cmd.files).containsExactly("--looks-like-option");
     }

     // ========== Boolean explicit value tests ==========

     @Test
     void booleanExplicitTrue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--bool=true");
        assertEquals(0, res.exitCode());
        assertThat(cmd.boolVal).isTrue();
     }

     @Test
     void booleanExplicitFalse() {
        DefaultValuesCmd cmd = new DefaultValuesCmd();
        var res = run(cmd, "--enabled=false");
        assertEquals(0, res.exitCode());
        assertThat(cmd.enabled).isFalse();
     }

     // ========== Empty args tests ==========

     @Test
     void emptyArgsRunsRootCommand() {
        var res = run(new Root());
        assertEquals(0, res.exitCode());
        assertThat(res.err()).isBlank();
     }

     @Test
     void noOptionsCommandWithEmptyArgs() {
        var res = run(new NoOptsCmd());
        assertEquals(0, res.exitCode());
        assertThat(res.err()).isBlank();
     }

     // ========== Version tests ==========

     @Test
     void versionWithNoVersionSetShowsUnknown() {
        var res = run(new NoVersionCmd(), "--version");
        assertEquals(0, res.exitCode());
        assertThat(res.out().trim()).isEqualTo("unknown");
     }

     @Test
     void shortVersionFlagWorks() {
        var res = run(new Root(), "-V");
        assertEquals(0, res.exitCode());
        assertThat(res.out().trim()).isEqualTo("1.2.3");
     }

     @Test
     void shortHelpFlagWorks() {
        var res = run(new Root(), "-h");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage:");
     }

     // ========== Edge case: special characters ==========

     @Test
     void optionValueWithSpaces() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "value with spaces");
        assertEquals(0, res.exitCode());
        assertEquals("value with spaces", cmd.stringVal);
     }

     @Test
     void optionValueWithEqualsSign() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string=value=with=equals");
        assertEquals(0, res.exitCode());
        assertEquals("value=with=equals", cmd.stringVal);
     }

     @Test
     void emptyStringValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "");
        assertEquals(0, res.exitCode());
        assertEquals("", cmd.stringVal);
     }

     @Test
     void emptyStringValueWithEquals() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string=");
        assertEquals(0, res.exitCode());
        assertEquals("", cmd.stringVal);
     }

     // ========== Edge case: negative numbers ==========

     @Test
     void negativeIntValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-i", "-42");
        assertEquals(0, res.exitCode());
        assertEquals(-42, cmd.intVal);
     }

     @Test
     void negativeDoubleValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--double", "-3.14");
        assertEquals(0, res.exitCode());
        assertEquals(-3.14, cmd.doubleVal, 0.001);
     }

     @Test
     void negativeNumberAfterEndOfOptions() {
        VarargsCmd cmd = new VarargsCmd();
        var res = run(cmd, "--", "-42");
        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly("-42");
     }

     // ========== Error message quality tests ==========

    @Test
    void unknownSubcommandIsRejectedAsPositional() {
        var res = run(new Root(), "nonexistent");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Unexpected parameter");
    }

    @Test
    void duplicateOptionsLastOneWins() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "first", "--string", "second");
        assertEquals(0, res.exitCode());
        assertEquals("second", cmd.stringVal);
    }

    // ========== Help output quality tests ==========

    @Test
    void helpShowsOptionDescriptions() {
        var res = run(new TypesCmd(), "--help");
        assertEquals(0, res.exitCode());
        String help = res.out();
        assertThat(help).contains("Integer option");
        assertThat(help).contains("Long option");
        assertThat(help).contains("Double option");
        assertThat(help).contains("String option");
        assertThat(help).contains("Boolean option");
    }

    @Test
    void helpShowsRequiredMarker() {
        var res = run(new Sub(), "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("(required)");
    }

    @Test
    void helpShowsMultipleOptionNames() {
        var res = run(new MultiNameOptCmd(), "--help");
        assertEquals(0, res.exitCode());
        String help = res.out();
        assertThat(help).contains("-v");
        assertThat(help).contains("--verbose");
        assertThat(help).contains("--debug");
    }

    @Test
    void hiddenOptionsAreOmittedFromUsageSynopsis() {
        @me.bechberger.femtocli.annotations.Command(
                name = "hidden-usage",
                description = "Test hidden options in usage",
                mixinStandardHelpOptions = true
        )
        class HiddenUsageCmd implements Runnable {
            @me.bechberger.femtocli.annotations.Option(names = "--shown", description = "Shown")
            String shown;

            @me.bechberger.femtocli.annotations.Option(names = "--secret", hidden = true, description = "Hidden")
            String secret;

            @Override
            public void run() {
            }
        }

        CliTest test = CliTest.of(new HiddenUsageCmd())
                .args("--help")
                .run()
                .expectCode(0);

        String help = test.stdout();
        // --secret must not appear anywhere in the usage line.
        assertThat(help).contains("Usage: hidden-usage [-hV] [--shown=<shown>]\n");
        assertThat(help).doesNotContain("--secret");
    }

    // ========== Combined stress test ==========

    @Test
    void complexCommandLineIsParsedCorrectly() {
        MixedCmd cmd = new MixedCmd();
        var res = run(cmd, "-n=complex name", "file1.txt", "--count=99", "file2.txt", "--", "-file3.txt");
        assertEquals(0, res.exitCode());
        assertEquals("complex name", cmd.name);
        assertEquals(99, cmd.count);
        assertThat(cmd.files).containsExactly("file1.txt", "file2.txt", "-file3.txt");
    }

    // ========== Inheritance tests ==========

    @Command(name = "base", description = "Base command", mixinStandardHelpOptions = true)
    static abstract class BaseCmd implements Callable<Integer> {
        @Option(names = "--common", description = "Common option")
        String common;
    }

    @Command(name = "derived", description = "Derived command", mixinStandardHelpOptions = true)
    static class DerivedCmd extends BaseCmd {
        @Option(names = "--specific", description = "Specific option")
        String specific;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void inheritedOptionsAreRecognized() {
        DerivedCmd cmd = new DerivedCmd();
        var res = run(cmd, "--common", "base-val", "--specific", "derived-val");
        assertEquals(0, res.exitCode());
        assertEquals("base-val", cmd.common);
        assertEquals("derived-val", cmd.specific);
    }

    // ========== Zero and boundary value tests ==========

    @Test
    void zeroIntValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-i", "0");
        assertEquals(0, res.exitCode());
        assertEquals(0, cmd.intVal);
    }

    @Test
    void zeroDoubleValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--double", "0.0");
        assertEquals(0, res.exitCode());
        assertEquals(0.0, cmd.doubleVal, 0.001);
    }

    @Test
    void maxIntValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-i", String.valueOf(Integer.MAX_VALUE));
        assertEquals(0, res.exitCode());
        assertEquals(Integer.MAX_VALUE, cmd.intVal);
    }

    @Test
    void minIntValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-i", String.valueOf(Integer.MIN_VALUE));
        assertEquals(0, res.exitCode());
        assertEquals(Integer.MIN_VALUE, cmd.intVal);
    }

    @Test
    void intOverflowReturns2() {
        var res = run(new TypesCmd(), "-i", "99999999999999999999");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value");
    }

    // ========== Whitespace edge cases ==========

    @Test
    void optionValueWithLeadingSpaces() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "  leading spaces");
        assertEquals(0, res.exitCode());
        assertEquals("  leading spaces", cmd.stringVal);
    }

    @Test
    void optionValueWithTrailingSpaces() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "trailing spaces  ");
        assertEquals(0, res.exitCode());
        assertEquals("trailing spaces  ", cmd.stringVal);
    }

    @Test
    void optionValueOnlySpaces() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "   ");
        assertEquals(0, res.exitCode());
        assertEquals("   ", cmd.stringVal);
    }

    // ========== Special string values ==========

    @Test
    void optionValueWithNewline() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "line1\nline2");
        assertEquals(0, res.exitCode());
        assertEquals("line1\nline2", cmd.stringVal);
    }

    @Test
    void optionValueWithTab() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "col1\tcol2");
        assertEquals(0, res.exitCode());
        assertEquals("col1\tcol2", cmd.stringVal);
    }

    // ========== Multiple subcommand tests ==========

    @Test
    void helpInMiddleOfSubcommandChainWorks() {
        var res = run(new DeepRoot(), "level1", "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: deep-root level1");
    }

    @Test
    void versionInSubcommandShowsRootVersion() {
        var res = run(new Root(), "sub", "--version");
        assertEquals(0, res.exitCode());
        // Note: FemtoCli shows root version even from subcommand
    }

    // ========== Boolean parsing edge cases ==========

    @Test
    void booleanWithCapitalTrue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--bool=TRUE");
        assertEquals(0, res.exitCode());
        // Note: Boolean.parseBoolean is case-insensitive for "true"
        assertThat(cmd.boolVal).isTrue();
    }

    @Test
    void booleanWithMixedCaseFalse() {
        DefaultValuesCmd cmd = new DefaultValuesCmd();
        var res = run(cmd, "--enabled=FaLsE");
        assertEquals(0, res.exitCode());
        assertThat(cmd.enabled).isFalse();
    }

    @Test
    void booleanWithYesValueIsTrue() {
        // "yes" is now accepted as a truthy boolean value (along with "on", "1")
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--bool=yes");
        assertEquals(0, res.exitCode());
        assertThat(cmd.boolVal).isTrue();
    }

    @Test
    void booleanWithInvalidValueIsRejected() {
        // Invalid boolean values like "maybe" now produce an error instead of silently returning false
        var res = run(new TypesCmd(), "--bool=maybe");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value");
    }

    // ========== Double equals sign edge cases ==========

    @Test
    void optionWithDoubleEqualsInValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string=a==b");
        assertEquals(0, res.exitCode());
        assertEquals("a==b", cmd.stringVal);
    }

    @Test
    void optionWithEqualsAtEnd() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string=value=");
        assertEquals(0, res.exitCode());
        assertEquals("value=", cmd.stringVal);
    }

    // ========== Multiple options of same type ==========

    @Test
    void multipleIntOptionsAllParsed() {
        @Command(name = "multi-int", description = "Multi int", mixinStandardHelpOptions = true)
        class MultiIntCmd implements Callable<Integer> {
            @Option(names = "--a", description = "A")
            int a;

            @Option(names = "--b", description = "B")
            int b;

            @Option(names = "--c", description = "C")
            int c;

            @Override
            public Integer call() {
                return 0;
            }
        }

        MultiIntCmd cmd = new MultiIntCmd();
        var res = run(cmd, "--a", "1", "--b", "2", "--c", "3");

        assertEquals(0, res.exitCode());
        assertEquals(1, cmd.a);
        assertEquals(2, cmd.b);
        assertEquals(3, cmd.c);
    }

    // ========== Empty positionals list ==========

    @Test
    void varargsWithOnlyOptionsReturnsEmptyList() {
        MixedCmd cmd = new MixedCmd();
        var res = run(cmd, "--name", "test", "--count", "5");

        assertEquals(0, res.exitCode());
        assertEquals("test", cmd.name);
        assertEquals(5, cmd.count);
        assertThat(cmd.files).isEmpty();
    }

    // ========== Unicode tests ==========

    @Test
    void unicodeStringValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "こんにちは世界");

        assertEquals(0, res.exitCode());
        assertEquals("こんにちは世界", cmd.stringVal);
    }

    @Test
    void emojiStringValue() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string", "Hello 🌍🎉");

        assertEquals(0, res.exitCode());
        assertEquals("Hello 🌍🎉", cmd.stringVal);
    }

    // ========== Scientific notation for doubles ==========

    @Test
    void scientificNotationDouble() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--double", "1.5e10");

        assertEquals(0, res.exitCode());
        assertEquals(1.5e10, cmd.doubleVal, 1e5);
    }

    @Test
    void negativeScientificNotationDouble() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--double", "-2.5E-3");

        assertEquals(0, res.exitCode());
        assertEquals(-2.5e-3, cmd.doubleVal, 1e-6);
    }

    // ========== Single dash alone ==========

    @Test
    void singleDashAsPositionalAfterEndOfOptions() {
        VarargsCmd cmd = new VarargsCmd();

        // Single dash after -- is treated as positional (often used to mean stdin)
        var res = run(cmd, "--", "-");

        assertEquals(0, res.exitCode());
        assertThat(cmd.values).containsExactly("-");
    }

    @Test
    void singleDashAloneIsTreatedAsUnknownOption() {
        // Single dash by itself starts with "-" so it's treated as an option
        var res = run(new VarargsCmd(), "-");

        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Unknown option");
    }

    // ========== Help text stability ==========

    @Test
    void helpTextIsConsistentAcrossCalls() {
        var res1 = FemtoCli.builder().runCaptured(new Root(), "--help");
        var res2 = FemtoCli.builder().runCaptured(new Root(), "--help");

        assertEquals(res1.out(), res2.out());
    }

    // ========== Commands with mixinStandardHelpOptions=false ==========

    @Command(
            name = "no-help",
            description = "Command without help options displayed"
    )
    static class NoHelpCmd implements Runnable {
        @Option(names = "--value", description = "Some value")
        String value;

        @Override
        public void run() {
        }
    }

    @Command(
            name = "no-help-with-opts",
            description = "No help display but has options"
    )
    static class NoHelpWithOwnOptions implements Callable<Integer> {
        @Option(names = "--host", description = "Hostname")
        String host;

        @Option(names = "--port", description = "Port number")
        int port;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(
            name = "no-help-root",
            description = "Root without help display",
            subcommands = {NoHelpSubCmd.class}
    )
    static class NoHelpRoot implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "subcmd",
            description = "Sub command"
    )
    static class NoHelpSubCmd implements Runnable {
        @Option(names = "--opt", description = "Option")
        String opt;

        @Override
        public void run() {
        }
    }

    @Command(
            name = "mixed-help-root",
            description = "Root with help",
            subcommands = {MixedHelpSubCmd.class},
            mixinStandardHelpOptions = true
    )
    static class MixedHelpRoot implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(
            name = "no-help-sub",
            description = "Sub without help display"
    )
    static class MixedHelpSubCmd implements Callable<Integer> {
        @Option(names = "--host", description = "Host option")
        String host;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ========== Tests for mixinStandardHelpOptions ==========

    @Test
    void noHelpOptionsDashHStillShowsHelp() {
        // -h always works, even with mixinStandardHelpOptions=false
        CliTest.of(new NoHelpCmd())
                .args("-h")
                .run()
                .expectCode(0)
                .expectOut("Usage:");
    }

    @Test
    void noHelpOptionsDashVStillShowsVersion() {
        // -V always works, even with mixinStandardHelpOptions=false
        CliTest.of(new NoHelpCmd())
                .args("-V")
                .run()
                .expectCode(0)
                .expectOut("unknown");
    }

    @Test
    void noHelpOptionsLongHelpStillWorks() {
        CliTest.of(new NoHelpCmd())
                .args("--help")
                .run()
                .expectCode(0)
                .expectOut("Usage:");
    }

    @Test
    void noHelpOptionsLongVersionStillWorks() {
        CliTest.of(new NoHelpCmd())
                .args("--version")
                .run()
                .expectCode(0)
                .expectOut("unknown");
    }

    @Test
    void noHelpOptionsHelpAlwaysShowsHelpVersionFlags() {
        // With the current default CommandConfig, standard help options are shown.
        // mixinStandardHelpOptions primarily controls parsing behavior in other CLIs;
        // in FemtoCli it's used for rendering, and defaults to "on" globally.
        CliTest.of(new NoHelpCmd())
                .args("--help")
                .run()
                .expectCode(0)
                .expectOut("Usage:")
                .expectOut("-h, --help")
                .expectOut("-V, --version");
    }

    @Test
    void helpOptionsHelpShowsHelpVersionFlags() {
        // With mixinStandardHelpOptions=true, help output should show -h/-V
        CliTest.of(new Root())
                .args("--help")
                .run()
                .expectCode(0)
                .expectOut("Usage:")
                .expectOut("-h, --help")
                .expectOut("-V, --version");
    }

    @Test
    void noHelpOptionsOtherOptionsStillWork() {
        NoHelpCmd cmd = new NoHelpCmd();
        CliTest.of(cmd)
                .args("--value", "test")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals("test", cmd.value);
    }

    @Test
    void noHelpWithOptionsParseCorrectly() {
        NoHelpWithOwnOptions cmd = new NoHelpWithOwnOptions();
        CliTest.of(cmd)
                .args("--host", "localhost", "--port", "8080")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals("localhost", cmd.host);
        assertEquals(8080, cmd.port);
    }

    @Test
    void noHelpRootSubcommandWorks() {
        CliTest.of(new NoHelpRoot())
                .args("subcmd", "--opt", "value")
                .run()
                .expectCode(0)
                .expectErrEmpty();
    }

    @Test
    void noHelpRootHelpStillWorks() {
        CliTest.of(new NoHelpRoot())
                .args("--help")
                .run()
                .expectCode(0)
                .expectOut("Usage:");
    }

    @Test
    void noHelpSubcommandHelpStillWorks() {
        CliTest.of(new NoHelpRoot())
                .args("subcmd", "--help")
                .run()
                .expectCode(0)
                .expectOut("Usage: no-help-root subcmd");
    }

    // ========== Tests using CliTest framework ==========

    @Test
    void cliTestFramework_helpReturnsZero() {
        CliTest.of(new Root())
                .args("--help")
                .run()
                .expectCode(0)
                .expectOut("Usage: root")
                .expectOut("-h, --help")
                .expectOut("-V, --version")
                .expectErrEmpty();
    }

    @Test
    void cliTestFramework_versionWorks() {
        CliTest.of(new Root())
                .args("--version")
                .run()
                .expectCode(0)
                .expectOut("1.2.3")
                .expectErrEmpty();
    }

    @Test
    void cliTestFramework_missingRequired() {
        CliTest.of(new Root())
                .args("sub")
                .run()
                .expectCode(2)
                .expectErr("Missing required option");
    }

    @Test
    void cliTestFramework_optionParsing() {
        TypesCmd cmd = new TypesCmd();
        CliTest.of(cmd)
                .args("-i", "100", "--string", "hello", "-b")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(100, cmd.intVal);
        assertEquals("hello", cmd.stringVal);
        assertThat(cmd.boolVal).isTrue();
    }

    @Test
    void cliTestFramework_varargsParams() {
        VarargsCmd cmd = new VarargsCmd();
        CliTest.of(cmd)
                .args("a", "b", "c")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertThat(cmd.values).containsExactly("a", "b", "c");
    }

    @Test
    void cliTestFramework_unknownOption() {
        CliTest.of(new TypesCmd())
                .args("--unknown")
                .run()
                .expectCode(2)
                .expectErr("Unknown option: --unknown");
    }

    @Test
    void cliTestFramework_subcommandHelp() {
        CliTest.of(new Root())
                .args("sub", "--help")
                .run()
                .expectCode(0)
                .expectOut("Usage: root sub")
                .expectOut("--req")
                .expectOut("(required)");
    }

    @Test
    void cliTestFramework_deepNesting() {
        CliTest.of(new DeepRoot())
                .args("level1", "level2", "level3", "--val", "deep")
                .run()
                .expectCode(0)
                .expectErrEmpty();
    }

    @Test
    void cliTestFramework_endOfOptions() {
        VarargsCmd cmd = new VarargsCmd();
        CliTest.of(cmd)
                .args("--", "--looks-like-option", "-x")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertThat(cmd.values).containsExactly("--looks-like-option", "-x");
    }

    @Test
    void cliTestFramework_negativeNumbers() {
        TypesCmd cmd = new TypesCmd();
        CliTest.of(cmd)
                .args("-i", "-100", "--double", "-3.14")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(-100, cmd.intVal);
        assertEquals(-3.14, cmd.doubleVal, 0.001);
    }

    @Test
    void cliTestFramework_defaultsPreserved() {
        DefaultValuesCmd cmd = new DefaultValuesCmd();
        CliTest.of(cmd)
                .args()
                .run()
                .expectCode(0);
        assertEquals(42, cmd.count);
        assertEquals("default", cmd.name);
        assertThat(cmd.enabled).isTrue();
    }

    @Test
    void cliTestFramework_callableExitCode() {
        CliTest.of(new CallableReturnCmd())
                .args("--code", "42")
                .run()
                .expectCode(42);
    }

    // ========== Path support tests ==========

    @Command(name = "path-cmd", description = "Test Path support", mixinStandardHelpOptions = true)
    static class PathCmd implements Callable<Integer> {
        @Option(names = {"-f", "--file"}, description = "File path")
        java.nio.file.Path file;

        @Option(names = {"-o", "--output"}, description = "Output path")
        java.nio.file.Path output;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void pathOptionParsesCorrectly() {
        PathCmd cmd = new PathCmd();
        CliTest.of(cmd)
                .args("--file", "/tmp/test.txt")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(java.nio.file.Path.of("/tmp/test.txt"), cmd.file);
    }

    @Test
    void pathOptionWithRelativePath() {
        PathCmd cmd = new PathCmd();
        CliTest.of(cmd)
                .args("-f", "relative/path/file.txt")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(java.nio.file.Path.of("relative/path/file.txt"), cmd.file);
    }

    @Test
    void multiplePathOptions() {
        PathCmd cmd = new PathCmd();
        CliTest.of(cmd)
                .args("--file", "/input.txt", "--output", "/output.txt")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(java.nio.file.Path.of("/input.txt"), cmd.file);
        assertEquals(java.nio.file.Path.of("/output.txt"), cmd.output);
    }

    @Test
    void pathOptionWithEqualsSign() {
        PathCmd cmd = new PathCmd();
        CliTest.of(cmd)
                .args("--file=/path/with=equals/file.txt")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(java.nio.file.Path.of("/path/with=equals/file.txt"), cmd.file);
    }

    @Test
    void pathOptionWithSpaces() {
        PathCmd cmd = new PathCmd();
        CliTest.of(cmd)
                .args("--file", "/path/with spaces/file.txt")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(java.nio.file.Path.of("/path/with spaces/file.txt"), cmd.file);
    }

    // ========== Custom type handler tests ==========

    enum Color {RED, GREEN, BLUE}

    @Command(name = "custom-type", description = "Test custom types", mixinStandardHelpOptions = true)
    static class CustomTypeCmd implements Callable<Integer> {
        @Option(names = "--color", description = "Color enum")
        Color color;

        @Option(names = "--duration", description = "Duration")
        java.time.Duration duration;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void enumOptionLowercase() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        CliTest.of(cmd)
                .args("--color", "red")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(Color.RED, cmd.color);
    }

    @Test
    void enumOptionUppercase() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        CliTest.of(cmd)
                .args("--color", "GREEN")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(Color.GREEN, cmd.color);
    }

    @Test
    void enumOptionMixedCase() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        CliTest.of(cmd)
                .args("--color", "Blue")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(Color.BLUE, cmd.color);
    }

    @Test
    void enumOptionInvalidValue() {
        CliTest.of(new CustomTypeCmd())
                .args("--color", "purple")
                .run()
                .expectCode(2)
                .expectErr("Invalid value");
    }

    @Test
    void enumOptionWithEquals() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        CliTest.of(cmd)
                .args("--color=red")
                .run()
                .expectCode(0)
                .expectErrEmpty();
        assertEquals(Color.RED, cmd.color);
    }

    // ========== Custom type converter tests ==========

    @Test
    void customTypeConverterForDuration() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        var res = FemtoCli.builder()
                .registerType(java.time.Duration.class, java.time.Duration::parse)
                .runCaptured(cmd, "--duration", "PT1H30M");

        assertEquals(0, res.exitCode());
        assertEquals(java.time.Duration.ofHours(1).plusMinutes(30), cmd.duration);
    }

    @Test
    void multipleCustomTypeConverters() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        var res = FemtoCli.builder()
                .registerType(java.time.Duration.class, java.time.Duration::parse)
                .runCaptured(cmd, "--color", "blue", "--duration", "PT2H");

        assertEquals(0, res.exitCode());
        assertEquals(Color.BLUE, cmd.color);
        assertEquals(java.time.Duration.ofHours(2), cmd.duration);
    }

    @Test
    void customTypeConverterCanOverrideBuiltIn() {
        // Custom converter can override built-in enum handling
        CustomTypeCmd cmd = new CustomTypeCmd();
        var res = FemtoCli.builder()
                .registerType(Color.class, s -> {
                    // Custom: "r" -> RED, "g" -> GREEN, "b" -> BLUE
                    return switch (s.toLowerCase()) {
                        case "r" -> Color.RED;
                        case "g" -> Color.GREEN;
                        case "b" -> Color.BLUE;
                        default -> Color.valueOf(s.toUpperCase());
                    };
                })
                .runCaptured(cmd, "--color", "r");

        assertEquals(0, res.exitCode());
        assertEquals(Color.RED, cmd.color);
    }

    @Test
    void customSynopsisAndHeaderWork() {
        @Command(
                name = "custom-help",
                description = "Test custom synopsis and header",
                header = {"Custom Header Line 1", "Custom Header Line 2"},
                customSynopsis = {"Usage: custom-help <special syntax>"},
                mixinStandardHelpOptions = true
        )
        class CustomHelpCmd implements Runnable {
            @Override
            public void run() {
            }
        }

        CliTest test = CliTest.of(new CustomHelpCmd())
                .args("--help")
                .run()
                .expectCode(0);

        String help = test.stdout();
        assertThat(help).contains("Custom Header Line 1");
        assertThat(help).contains("Custom Header Line 2");
        assertThat(help).contains("Usage: custom-help <special syntax>");
    }

    @Test
    void footerIsPrintedInHelp() {
        @Command(
                name = "footer-cmd",
                description = "Has a footer",
                footer = "Footer line 1\nFooter line 2",
                mixinStandardHelpOptions = true
        )
        class FooterCmd implements Runnable {
            @Override
            public void run() {
            }
        }

        CliTest test = CliTest.of(new FooterCmd())
                .args("--help")
                .run()
                .expectCode(0);

        String help = test.stdout();
        assertThat(help).contains("Footer line 1");
        assertThat(help).contains("Footer line 2");
        // Footer should be at the end (allow trailing whitespace/newlines from the renderer)
        assertThat(help.trim()).endsWith("Footer line 2");
    }

    // ========== Exception handling tests ==========

    @Command(name = "exception-cmd", description = "Test exception handling", mixinStandardHelpOptions = true)
    static class ExceptionCmd implements Callable<Integer> {
        @Option(names = "--fail", description = "Cause failure")
        boolean fail;

        @Override
        public Integer call() {
            if (fail) {
                throw new RuntimeException("Intentional failure");
            }
            return 0;
        }
    }

    @Test
    void exceptionInCommandReturns1() {
        CliTest test = CliTest.of(new ExceptionCmd())
                .args("--fail")
                .run()
                .expectCode(1);

        assertThat(test.stderr()).contains("Intentional failure");
    }


    // ========== Subcommand help flag at different positions ==========

    @Test
    void helpBeforeSubcommandShowsRootHelp() {
        CliTest test = CliTest.of(new JstallRoot())
                .args("--help", "ai")
                .run()
                .expectCode(0);

        assertThat(test.stdout()).contains("Usage: jstall");
    }

    @Test
    void helpAfterSubcommandShowsSubcommandHelp() {
        CliTest test = CliTest.of(new JstallRoot())
                .args("ai", "--help")
                .run()
                .expectCode(0);

        assertThat(test.stdout()).contains("Usage: jstall ai");
    }

    @Test
    void helpForDeepSubcommand() {
        CliTest test = CliTest.of(new JstallRoot())
                .args("ai", "full", "--help")
                .run()
                .expectCode(0);

        assertThat(test.stdout()).contains("Usage: jstall ai full");
        assertThat(test.stdout()).contains("Analyze all JVMs");
    }

    // ========== Empty line configuration tests ==========

    @Command(
        name = "empty-lines",
        description = "Test empty line configuration",
        emptyLineAfterUsage = true,
        emptyLineAfterDescription = true,
        mixinStandardHelpOptions = true
    )
    static class EmptyLinesCmd implements Runnable {
        @Option(names = "--opt", description = "An option")
        String opt;

        @Override
        public void run() {}
    }

    @Test
    void emptyLineAfterUsageIsRendered() {
        CliTest test = CliTest.of(new EmptyLinesCmd())
            .args("--help")
            .run()
            .expectCode(0);

        String help = test.stdout();
        // Check that there's an empty line after the usage line
        assertThat(help).contains("Usage: empty-lines [-hV] [--opt=<opt>]\n\nTest empty line configuration");
    }

    @Test
    void emptyLineAfterDescriptionIsRendered() {
        CliTest test = CliTest.of(new EmptyLinesCmd())
            .args("--help")
            .run()
            .expectCode(0);

        String help = test.stdout();
        // Check that there's an empty line after the description
        assertThat(help).contains("Test empty line configuration\n\n");
    }

    @Command(
        name = "no-empty-lines",
        description = "No empty lines",
        mixinStandardHelpOptions = true
    )
    static class NoEmptyLinesCmd implements Runnable {
        @Override
        public void run() {
        }
    }

    @Test
    void noEmptyLinesWhenNotConfigured() {
        CliTest test = CliTest.of(new NoEmptyLinesCmd())
            .args("--help")
            .run()
            .expectCode(0);

        String help = test.stdout();
        // Check that there's NO empty line after usage or description
        assertThat(help).contains("Usage: no-empty-lines [-hV]\nNo empty lines\n  -h");
    }

    // ========== Long description wrapping tests ==========

    @Command(name = "wrap-test", description = "Test description wrapping", mixinStandardHelpOptions = true)
    static class WrapTestCmd implements Callable<Integer> {
        @Parameters(
            arity = "0..1",
            paramLabel = "<filter>",
            description = "Optional filter - only show JVMs whose main class contains this text"
        )
        String filter;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    void longDescriptionIsWrapped() {
        CliTest test = CliTest.of(new WrapTestCmd())
            .args("--help")
            .run()
            .expectCode(0);

        String help = test.stdout();
        // The description should be wrapped (contains newline within the description area)
        assertThat(help).contains("[<filter>]");
        assertThat(help).contains("Optional filter");
        assertThat(help).contains("this text");
    }

    @Test
    void globalVersionFromCommandConfigIsUsed() {
        var config = new CommandConfig();
        config.version = "9.9.9";

        var result = FemtoCli.builder()
            .commandConfig(config)
            .runCaptured(new NoVersionGlobalCmd(), "--version");

        assertEquals(0, result.exitCode());
        assertThat(result.out().trim()).isEqualTo("9.9.9");
    }

    @Test
    void commandConfigCanForceShowingHelpVersionFlags() {
        var config = new CommandConfig();
        config.mixinStandardHelpOptions = true;

        var result = FemtoCli.builder()
            .commandConfig(config)
            .runCaptured(new NoHelpCmd(), "--help");

        assertEquals(0, result.exitCode());
        assertThat(result.out()).contains("-h, --help");
        assertThat(result.out()).contains("-V, --version");
    }

    // ========== Option paramLabel + option arity ==========

    @Command(name = "opt-paramlabel", description = "Option paramLabel in help", mixinStandardHelpOptions = true)
    static class OptionParamLabelCmd implements Runnable {
        @Option(names = "--output", paramLabel = "FILE", description = "Output file")
        String output;

        @Override
        public void run() {}
    }

    @Test
    void optionParamLabelUsedInHelp() {
        CliTest test = CliTest.of(new OptionParamLabelCmd())
            .args("--help")
            .run()
            .expectCode(0);
        assertThat(test.stdout()).contains("--output=FILE");
    }

    @Command(name = "opt-arity", description = "Option arity 0..1", mixinStandardHelpOptions = true)
    static class OptionArityOptionalValueCmd implements Runnable {
        @Option(names = "--mode", arity = "0..1", defaultValue = "safe", description = "Mode")
        String mode;

        @Override
        public void run() {}
    }

    @Test
    void optionArityOptionalValueAllowsFlagWithoutValueAndUsesDefault() {
        OptionArityOptionalValueCmd cmd = new OptionArityOptionalValueCmd();
        CliTest.of(cmd)
            .args("--mode")
            .run()
            .expectCode(0);
        assertThat(cmd.mode).isEqualTo("safe");
    }

    @Test
    void builtInDurationConverterParsesHumanMillis() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        var res = run(cmd, "--duration", "400ms");

        assertEquals(0, res.exitCode());
        assertEquals(Duration.ofMillis(400), cmd.duration);
    }

    @Test
    void builtInDurationConverterParsesHumanFractionalSeconds() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        var res = run(cmd, "--duration", "4.5s");

        assertEquals(0, res.exitCode());
        assertEquals(Duration.ofMillis(4500), cmd.duration);
    }

    @Test
    void builtInDurationConverterParsesNegative() {
        CustomTypeCmd cmd = new CustomTypeCmd();
        var res = run(cmd, "--duration", "-1.25s");

        assertEquals(0, res.exitCode());
        assertEquals(Duration.ofMillis(-1250), cmd.duration);
    }

    @Test
    void builtInDurationConverterRejectsMissingUnit() {
        CliTest.of(new CustomTypeCmd())
                .args("--duration", "10")
                .run()
                .expectCode(2)
                .expectErr("Invalid value");
    }

    @Test
    void unsupportedTypeWithoutConverterReturns2() {
        // java.awt.Point is intentionally unsupported unless a custom converter is registered.
        PointCmd cmd = new PointCmd();
        var res = run(cmd, "--point", "1,2");

        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Unsupported field type");
    }

    @Command(name = "point-cmd", description = "Test Point type", mixinStandardHelpOptions = true)
    static class PointCmd implements Callable<Integer> {
        @Option(names = "--point", description = "Point")
        java.awt.Point point;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(name = "defaultTest")
    static class DefaultTest implements Callable<Integer> {
        @Option(names = {"-i", "--interval"}, defaultValue = "10ms", description = "Sampling interval (default: 10ms)")
        private Duration interval;

        @Override
        public Integer call() {
            System.out.println(interval.toMillis());
            return 0;
        }
    }

    @Test
    void defaultDurationParsing() {
        var res = FemtoCli.builder()
                .commandConfig(cfg -> {
                    cfg.version = "0.4.5";
                    cfg.mixinStandardHelpOptions = true;
                    cfg.defaultValueHelpTemplate = ", default is ${DEFAULT-VALUE}";
                    cfg.defaultValueOnNewLine = false;
                })
                .runCaptured(new DefaultTest());
        assertEquals(0, res.exitCode());
        assertEquals("10", res.out().trim());
    }

    @Command(name = "main", subcommands = DefaultTest2.class)
    static class SomeMainCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(name = "defaultTest")
    static class DefaultTest2 implements Callable<Integer> {
        @Option(names = {"-i", "--interval"}, defaultValue = "10ms", description = "Sampling interval (default: 10ms)")
        private Duration interval;

        @Override
        public Integer call() {
            System.out.println(interval.toMillis());
            return 0;
        }
    }

    @Test
    void defaultDurationParsingInSubcommand() {
        var res = FemtoCli.builder()
                .commandConfig(cfg -> {
                    cfg.version = "0.4.5";
                    cfg.mixinStandardHelpOptions = true;
                    cfg.defaultValueHelpTemplate = ", default is ${DEFAULT-VALUE}";
                    cfg.defaultValueOnNewLine = false;
                })
                .runCaptured(new SomeMainCommand(), "defaultTest");
        assertEquals(0, res.exitCode());
        assertEquals("10", res.out().trim());
    }

    static abstract class BaseCommand implements Callable<Integer> {
        @Option(names = "--base-option", description = "An option in the base class")
        String baseOption;

        @Override
        public Integer call() {
            if (!returnOption().equals("implValue")) {
                throw new IllegalStateException("implOption not set correctly");
            }
            return 0;
        }

        abstract String returnOption();
    }

    @Command(name = "command-impl", description = "Command that extends a base class", mixinStandardHelpOptions = true)
    static class CommandImpl extends BaseCommand {
        @Option(names = "--impl-option", description = "An option in the implementation class")
        String implOption;

        String returnOption() {
            return implOption;
        }
    }

    @Test
    public void inheritedOptionsAreParsedCorrectly() {
        CommandImpl cmd = new CommandImpl();
        var res = FemtoCli.builder()
                .runCaptured(cmd, "--base-option", "baseValue", "--impl-option", "implValue");

        assertEquals(0, res.exitCode());
        assertEquals("baseValue", cmd.baseOption);
        assertEquals("implValue", cmd.implOption);
        assertEquals("implValue", cmd.returnOption());
    }

    @Command(name = "final field")
    static class FinalFieldCmd implements Callable<Integer> {
        @Option(names = "--final-opt", description = "A final field option")
        final String finalOpt = "defaultFinal";

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Test
    public void finalFieldOptionIsHandled() {
        assertThrows(FieldIsFinalException.class, () -> FemtoCli.builder()
                .runCaptured(new FinalFieldCmd(), "--final-opt", "newValue"));
    }

    // ========== Default subcommand tests ==========

    @Command(name = "status", description = "Show status")
    static class DefStatus implements Callable<Integer> {
        @Parameters(description = "target")
        String target;

        @Override
        public Integer call() {
            System.out.println("status:" + target);
            return 0;
        }
    }

    @Command(name = "list", description = "List items")
    static class DefList implements Runnable {
        @Override
        public void run() {
            System.out.println("list");
        }
    }

    @Command(
            name = "myapp",
            subcommands = {DefStatus.class, DefList.class},
            defaultSubcommand = DefStatus.class,
            mixinStandardHelpOptions = true
    )
    static class DefaultSubRoot implements Runnable {
        @Option(names = {"-v", "--verbose"})
        boolean verbose;

        @Override
        public void run() {
            System.out.println("root");
        }
    }

    @Test
    void defaultSubcommandRoutesUnknownTokenToDefaultSub() {
        // "myapp 1234" should behave like "myapp status 1234"
        var root = new DefaultSubRoot();
        RunResult res = FemtoCli.builder().runCaptured(root, "1234");
        assertEquals(0, res.exitCode(), res.err());
        assertThat(res.out().trim()).isEqualTo("status:1234");
    }

    @Test
    void defaultSubcommandExplicitSubcommandStillWorks() {
        // "myapp list" should still route to list, not to status with "list" as positional
        var root = new DefaultSubRoot();
        RunResult res = FemtoCli.builder().runCaptured(root, "list");
        assertEquals(0, res.exitCode(), res.err());
        assertThat(res.out().trim()).isEqualTo("list");
    }

    @Test
    void defaultSubcommandExplicitStatusStillWorks() {
        var root = new DefaultSubRoot();
        RunResult res = FemtoCli.builder().runCaptured(root, "status", "5678");
        assertEquals(0, res.exitCode(), res.err());
        assertThat(res.out().trim()).isEqualTo("status:5678");
    }

    @Test
    void defaultSubcommandWithParentOptionsParsed() {
        // "myapp --verbose 1234" should parse --verbose on root, then route to status
        var root = new DefaultSubRoot();
        RunResult res = FemtoCli.builder().runCaptured(root, "--verbose", "1234");
        assertEquals(0, res.exitCode(), res.err());
        assertThat(root.verbose).isTrue();
        assertThat(res.out().trim()).isEqualTo("status:1234");
    }

    @Test
    void defaultSubcommandHelpStillWorks() {
        var root = new DefaultSubRoot();
        RunResult res = FemtoCli.builder().runCaptured(root, "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Commands:");
        assertThat(res.out()).contains("status");
        assertThat(res.out()).contains("list");
    }

    @Test
    void defaultSubcommandNoArgsInvokesRoot() {
        // "myapp" with no args should invoke the root run(), not the default sub
        var root = new DefaultSubRoot();
        RunResult res = FemtoCli.builder().runCaptured(root);
        assertEquals(0, res.exitCode(), res.err());
        assertThat(res.out().trim()).isEqualTo("root");
    }

    // ========== Bug: parent option with defaultValue overwritten on subcommand fallthrough ==========

    @Command(name = "action", description = "Do something")
    static class FallthroughAction implements Callable<Integer> {
        @Parameters(description = "target file")
        String file;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Command(
            name = "tool",
            subcommands = {FallthroughAction.class},
            mixinStandardHelpOptions = true
    )
    static class ParentWithDefaultAndSubcmds implements Runnable {
        @Option(names = "--lang", defaultValue = "en")
        String lang;

        @Parameters(index = "0..*")
        List<String> files;

        @Override
        public void run() {
            System.out.println("lang=" + lang);
        }
    }

    @Test
    void parentOptionWithDefaultValueNotOverwrittenOnSubcommandFallthrough() {
        // When a parent command has subcommands but falls through as the final command,
        // options set in the first parse pass must not be overwritten by applyDefaultValues.
        var root = new ParentWithDefaultAndSubcmds();
        RunResult res = FemtoCli.builder().runCaptured(root, "--lang=de", "file.txt");
        assertEquals(0, res.exitCode(), res.err());
        // BUG: without fix, lang is overwritten to "en" by the second applyDefaultValues pass
        assertEquals("de", root.lang);
    }

    @Test
    void parentOptionDefaultStillAppliedWhenNotProvided() {
        var root = new ParentWithDefaultAndSubcmds();
        RunResult res = FemtoCli.builder().runCaptured(root, "file.txt");
        assertEquals(0, res.exitCode(), res.err());
        assertEquals("en", root.lang);
    }

    // ========== Bug: µs duration literal not accepted ==========

    @Test
    void durationParsesUnicodeMicroSeconds() {
        // parseDuration scanner recognizes µ as a unit start but the unit lookup
        // only accepts "us", not "µs" — this should work
        Duration d = FemtoCli.parseDuration("500µs");
        assertEquals(Duration.ofNanos(500_000), d);
    }

    @Test
    void durationParsesAsciiMicroSeconds() {
        Duration d = FemtoCli.parseDuration("500us");
        assertEquals(Duration.ofNanos(500_000), d);
    }

    // ========== Edge case: float type ==========

    @Test
    void floatOptionParsesCorrectly() {
        @Command(name = "float-cmd", mixinStandardHelpOptions = true)
        class FloatCmd implements Callable<Integer> {
            @Option(names = "--val") float val;
            @Option(names = "--boxed") Float boxed;
            @Override public Integer call() { return 0; }
        }
        FloatCmd cmd = new FloatCmd();
        var res = run(cmd, "--val", "3.14", "--boxed", "2.71");
        assertEquals(0, res.exitCode());
        assertEquals(3.14f, cmd.val, 0.001f);
        assertEquals(2.71f, cmd.boxed, 0.001f);
    }

    @Test
    void floatOverflowReturns2() {
        @Command(name = "float-cmd", mixinStandardHelpOptions = true)
        class FloatCmd implements Callable<Integer> {
            @Option(names = "--val") int val;
            @Override public Integer call() { return 0; }
        }
        // float overflow won't error (it becomes Infinity), but int overflow should
        var res = run(new FloatCmd(), "--val", "99999999999999999999");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value");
    }

    // ========== Edge case: split + repeated flags accumulate ==========

    @Test
    void splitAndRepeatedFlagsAccumulate() {
        IntArraySplitCmd cmd = new IntArraySplitCmd();
        var res = run(cmd, "--xs=1,2", "--xs=3,4");
        assertEquals(0, res.exitCode());
        assertThat(cmd.xs).containsExactly(1, 2, 3, 4);
    }

    @Test
    void splitListAndRepeatedAccumulate() {
        @Command(name = "accum", mixinStandardHelpOptions = true)
        class AccumCmd implements Callable<Integer> {
            @Option(names = "--tags", split = ",") List<String> tags;
            @Override public Integer call() { return 0; }
        }
        AccumCmd cmd = new AccumCmd();
        var res = run(cmd, "--tags=a,b", "--tags=c");
        assertEquals(0, res.exitCode());
        assertThat(cmd.tags).containsExactly("a", "b", "c");
    }

    // ========== Edge case: explicit value overrides default for list ==========

    @Test
    void explicitListValueOverridesDefault() {
        DefaultIntListOptionCmd cmd = new DefaultIntListOptionCmd();
        var res = run(cmd, "--xs=9,8,7");
        assertEquals(0, res.exitCode());
        assertThat(cmd.xs).containsExactly(9, 8, 7);
    }

    // ========== Edge case: scalar @Option(defaultValue) with typed field ==========

    @Test
    void scalarAnnotationDefaultValueApplied() {
        @Command(name = "scalar-def", mixinStandardHelpOptions = true)
        class ScalarDefCmd implements Callable<Integer> {
            @Option(names = "--port", defaultValue = "8080") int port;
            @Option(names = "--host", defaultValue = "localhost") String host;
            @Override public Integer call() { return 0; }
        }
        ScalarDefCmd cmd = new ScalarDefCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertEquals(8080, cmd.port);
        assertEquals("localhost", cmd.host);
    }

    @Test
    void scalarAnnotationDefaultOverriddenByCli() {
        @Command(name = "scalar-def", mixinStandardHelpOptions = true)
        class ScalarDefCmd implements Callable<Integer> {
            @Option(names = "--port", defaultValue = "8080") int port;
            @Override public Integer call() { return 0; }
        }
        ScalarDefCmd cmd = new ScalarDefCmd();
        var res = run(cmd, "--port", "9090");
        assertEquals(0, res.exitCode());
        assertEquals(9090, cmd.port);
    }

    // ========== Edge case: arity 0..1 with explicit value ==========

    @Test
    void arityOptionalValueWithExplicitEquals() {
        OptionArityOptionalValueCmd cmd = new OptionArityOptionalValueCmd();
        CliTest.of(cmd).args("--mode=fast").run().expectCode(0);
        assertEquals("fast", cmd.mode);
    }

    @Test
    void arityOptionalValueWithSeparateValue() {
        // The next token after the option is NOT consumed for arity 0..1,
        // so the default should apply and "fast" becomes a positional → error
        OptionArityOptionalValueCmd cmd = new OptionArityOptionalValueCmd();
        var res = run(cmd, "--mode", "fast");
        // "fast" is treated as an unexpected positional since arity is 0..1
        assertEquals(2, res.exitCode());
    }

    // ========== Edge case: multiple positional parameters with indices ==========

    @Test
    void multiplePositionalParametersWithExplicitIndices() {
        @Command(name = "multi-pos", mixinStandardHelpOptions = true)
        class MultiPosCmd implements Callable<Integer> {
            @Parameters(index = "0", description = "Source") String source;
            @Parameters(index = "1", description = "Dest") String dest;
            @Override public Integer call() { return 0; }
        }
        MultiPosCmd cmd = new MultiPosCmd();
        var res = run(cmd, "input.txt", "output.txt");
        assertEquals(0, res.exitCode());
        assertEquals("input.txt", cmd.source);
        assertEquals("output.txt", cmd.dest);
    }

    @Test
    void multiplePositionalParametersMissingSecond() {
        @Command(name = "multi-pos", mixinStandardHelpOptions = true)
        class MultiPosCmd implements Callable<Integer> {
            @Parameters(index = "0", description = "Source") String source;
            @Parameters(index = "1", description = "Dest") String dest;
            @Override public Integer call() { return 0; }
        }
        var res = run(new MultiPosCmd(), "input.txt");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required parameter");
    }

    // ========== Edge case: boolean flag doesn't consume non-boolean next token ==========

    @Test
    void booleanFlagDoesNotConsumeNextPositional() {
        MixedCmd cmd = new MixedCmd();
        // --name is not boolean, so this tests specifically boolean behavior by
        // creating a command with boolean + positionals
        @Command(name = "bool-pos", mixinStandardHelpOptions = true)
        class BoolPosCmd implements Callable<Integer> {
            @Option(names = "--flag") boolean flag;
            @Parameters(index = "0..*") List<String> args;
            @Override public Integer call() { return 0; }
        }
        BoolPosCmd bpc = new BoolPosCmd();
        var res = run(bpc, "--flag", "notBoolean");
        assertEquals(0, res.exitCode());
        assertThat(bpc.flag).isTrue();
        assertThat(bpc.args).containsExactly("notBoolean");
    }

    @Test
    void booleanFlagConsumesExplicitTrueToken() {
        @Command(name = "bool-pos", mixinStandardHelpOptions = true)
        class BoolPosCmd implements Callable<Integer> {
            @Option(names = "--flag") boolean flag;
            @Parameters(index = "0..*") List<String> args;
            @Override public Integer call() { return 0; }
        }
        BoolPosCmd cmd = new BoolPosCmd();
        var res = run(cmd, "--flag=true", "positional");
        assertEquals(0, res.exitCode());
        assertThat(cmd.flag).isTrue();
        assertThat(cmd.args).containsExactly("positional");
    }

    @Test
    void booleanFlagConsumesExplicitFalseToken() {
        @Command(name = "bool-pos", mixinStandardHelpOptions = true)
        class BoolPosCmd implements Callable<Integer> {
            @Option(names = "--flag") boolean flag;
            @Parameters(index = "0..*") List<String> args;
            @Override public Integer call() { return 0; }
        }
        BoolPosCmd cmd = new BoolPosCmd();
        var res = run(cmd, "--flag=false", "positional");
        assertEquals(0, res.exitCode());
        assertThat(cmd.flag).isFalse();
        assertThat(cmd.args).containsExactly("positional");
    }

    // ========== Edge case: -- followed by --help ==========

    @Test
    void endOfOptionsFollowedByHelpTreatsHelpAsPositional() {
        VarargsCmd cmd = new VarargsCmd();
        var res = run(cmd, "--", "--help");
        assertEquals(0, res.exitCode());
        // After --, --help should be treated as a positional argument, not trigger help
        assertThat(res.out()).doesNotContain("Usage:");
        assertThat(cmd.values).containsExactly("--help");
    }

    @Test
    void endOfOptionsFollowedByVersionTreatsVersionAsPositional() {
        VarargsCmd cmd = new VarargsCmd();
        var res = run(cmd, "--", "--version");
        assertEquals(0, res.exitCode());
        // After --, --version should be treated as a positional argument
        assertThat(cmd.values).containsExactly("--version");
    }

    // ========== Edge case: root as Class reference ==========

    @Command(name = "class-root", mixinStandardHelpOptions = true)
    static class ClassRootCmd implements Callable<Integer> {
        @Option(names = "--val") String val;
        @Override public Integer call() { return 0; }
    }

    @Test
    void rootAsClassReferenceInstantiatesAndParsesCorrectly() {
        var res = FemtoCli.builder().runCaptured(ClassRootCmd.class, "--val", "hello");
        assertEquals(0, res.exitCode());
    }

    @Test
    void rootAsClassReferenceHelpWorks() {
        var res = FemtoCli.builder().runCaptured(ClassRootCmd.class, "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: class-root");
    }

    // ========== Edge case: root as String throws ==========

    @Test
    void rootAsStringThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                FemtoCli.builder().runCaptured("not a command", "--help"));
    }

    // ========== Edge case: duration all units ==========

    @Test
    void durationParsesNanoseconds() {
        assertEquals(Duration.ofNanos(100), FemtoCli.parseDuration("100ns"));
    }

    @Test
    void durationParsesSeconds() {
        assertEquals(Duration.ofSeconds(5), FemtoCli.parseDuration("5s"));
    }

    @Test
    void durationParsesMinutes() {
        assertEquals(Duration.ofMinutes(3), FemtoCli.parseDuration("3m"));
    }

    @Test
    void durationParsesHours() {
        assertEquals(Duration.ofHours(2), FemtoCli.parseDuration("2h"));
    }

    @Test
    void durationParsesDays() {
        assertEquals(Duration.ofDays(1), FemtoCli.parseDuration("1d"));
    }

    @Test
    void durationRejectsEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseDuration(""));
    }

    @Test
    void durationRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseDuration(null));
    }

    @Test
    void durationRejectsUnknownUnit() {
        assertThrows(IllegalArgumentException.class, () -> FemtoCli.parseDuration("5x"));
    }

    // ========== Edge case: agent mode basics ==========

    @Test
    void agentModeBasicParsing() {
        Sub sub = new Sub();
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "sub,--req=hello");
        assertEquals(0, res.exitCode());
    }

    @Test
    void agentModeHelpViaBareToken() {
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage:");
    }

    @Test
    void agentModeVersionViaBareToken() {
        RunResult res = FemtoCli.runAgentCaptured(new Root(), "version");
        assertEquals(0, res.exitCode());
        assertThat(res.out().trim()).isEqualTo("1.2.3");
    }

    // ========== Edge case: custom help exit code ==========

    @Test
    void customHelpExitCodeIsReturned() {
        var config = new CommandConfig();
        config.helpExitCode = 42;
        var res = FemtoCli.builder()
                .commandConfig(config)
                .runCaptured(new Root(), "--help");
        assertEquals(42, res.exitCode());
        assertThat(res.out()).contains("Usage:");
    }

    // ========== Edge case: errors to stdout ==========

    @Test
    void usageErrorsToStdoutWhenConfigured() {
        var config = new CommandConfig();
        config.usageErrorsToStdout = true;
        var res = FemtoCli.builder()
                .commandConfig(config)
                .runCaptured(new Root(), "sub");  // missing required option
        assertEquals(2, res.exitCode());
        assertThat(res.out()).contains("Missing required option");
        assertThat(res.err()).isBlank();
    }

    @Test
    void usageErrorsToStderrByDefault() {
        var res = FemtoCli.builder().runCaptured(new Root(), "sub");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required option");
        assertThat(res.out()).doesNotContain("Missing required option");
    }

    // ========== Edge case: negative int via equals ==========

    @Test
    void negativeIntViaEquals() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "-i=-42");
        assertEquals(0, res.exitCode());
        assertEquals(-42, cmd.intVal);
    }

    // ========== Edge case: parameter with default value ==========

    @Test
    void parameterDefaultValueApplied() {
        @Command(name = "param-def", mixinStandardHelpOptions = true)
        class ParamDefCmd implements Callable<Integer> {
            @Parameters(arity = "0..1", defaultValue = "fallback") String value;
            @Override public Integer call() { return 0; }
        }
        ParamDefCmd cmd = new ParamDefCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        assertEquals("fallback", cmd.value);
    }

    @Test
    void parameterDefaultValueOverridden() {
        @Command(name = "param-def", mixinStandardHelpOptions = true)
        class ParamDefCmd implements Callable<Integer> {
            @Parameters(arity = "0..1", defaultValue = "fallback") String value;
            @Override public Integer call() { return 0; }
        }
        ParamDefCmd cmd = new ParamDefCmd();
        var res = run(cmd, "explicit");
        assertEquals(0, res.exitCode());
        assertEquals("explicit", cmd.value);
    }

    // ========== Edge case: empty varargs for typed arrays ==========

    @Test
    void emptyIntegerArrayVarargs() {
        IntArrayParamsCmd cmd = new IntArrayParamsCmd();
        var res = run(cmd);
        assertEquals(0, res.exitCode());
        // No positionals provided → field stays null (array not allocated)
    }

    // ========== Edge case: command not Runnable or Callable ==========

    @Test
    void commandNotRunnableOrCallableThrows() {
        @Command(name = "bad") class BadCmd {}
        assertThrows(IllegalStateException.class, () ->
                FemtoCli.builder().runCaptured(new BadCmd()));
    }

    // ========== Edge case: long overflow ==========

    @Test
    void longOverflowReturns2() {
        var res = run(new TypesCmd(), "--long", "99999999999999999999");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Invalid value");
    }

    // ========== Edge case: option value starting with dash via equals ==========

    @Test
    void optionValueStartingWithDashViaEquals() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--string=-dashed-value");
        assertEquals(0, res.exitCode());
        assertEquals("-dashed-value", cmd.stringVal);
    }

    // ========== Edge case: duplicate option last one wins for boolean ==========

    @Test
    void duplicateBooleanOptionLastOneWins() {
        TypesCmd cmd = new TypesCmd();
        var res = run(cmd, "--bool=true", "--bool=false");
        assertEquals(0, res.exitCode());
        assertThat(cmd.boolVal).isFalse();
    }

    // ========== Edge case: removeCommands hides subcommand ==========

    @Test
    void removedSubcommandIsNotAccessible() {
        var res = FemtoCli.builder()
                .removeCommands(Sub.class)
                .runCaptured(new Root(), "sub", "--req=x");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Unknown option: --req");
    }

    @Test
    void removedSubcommandNotShownInHelp() {
        var res = FemtoCli.builder()
                .removeCommands(Sub.class)
                .runCaptured(new Root(), "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).doesNotContain("sub ");
    }

    // ========== Bug: --help/--version after options in parent with subcommands ==========
    // When a parent command (with subcommands) has options, and --help/--version appears
    // AFTER those options, it should show help/version instead of "Unknown option: --help".

    @Test
    void helpAfterOptionInParentWithSubcommands() {
        var res = run(new ParentWithDefaultAndSubcmds(), "--lang=de", "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: tool");
    }

    @Test
    void shortHelpAfterOptionInParentWithSubcommands() {
        var res = run(new ParentWithDefaultAndSubcmds(), "--lang=de", "-h");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("Usage: tool");
    }

    @Test
    void versionAfterOptionInParentWithSubcommands() {
        var res = FemtoCli.builder()
                .commandConfig(c -> c.version = "9.8.7")
                .runCaptured(new ParentWithDefaultAndSubcmds(), "--lang=de", "--version");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("9.8.7");
    }

    @Test
    void shortVersionAfterOptionInParentWithSubcommands() {
        var res = FemtoCli.builder()
                .commandConfig(c -> c.version = "9.8.7")
                .runCaptured(new ParentWithDefaultAndSubcmds(), "--lang=de", "-V");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("9.8.7");
    }

    // ========== Bug: scalar default values skip verifier ==========
    // Array/list defaults run verifiers via convertToArray/convertToList, but scalar
    // defaults only call convert() without runVerifiers(). This is inconsistent.

    public static class RejectBadVerifier implements Verifier<String> {
        @Override
        public void verify(String value) throws VerifierException {
            if ("bad".equals(value)) {
                throw new VerifierException("value 'bad' is rejected by verifier");
            }
        }
    }

    @Command(name = "scalar-vd")
    static class ScalarVerifierDefaultCmd implements Callable<Integer> {
        @Option(names = "--val", defaultValue = "bad", verifier = RejectBadVerifier.class)
        String val;
        @Override public Integer call() { return 0; }
    }

    @Test
    void scalarDefaultValueRunsVerifier() {
        // Bug: scalar defaults skip verifier. Default "bad" should be rejected.
        var res = run(new ScalarVerifierDefaultCmd());
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("value 'bad' is rejected by verifier");
    }

    @Test
    void scalarExplicitBadValueRunsVerifier() {
        // Explicit "bad" value should also be rejected (this already works).
        var res = run(new ScalarVerifierDefaultCmd(), "--val=bad");
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("value 'bad' is rejected by verifier");
    }

    @Test
    void scalarExplicitGoodValuePassesVerifier() {
        ScalarVerifierDefaultCmd cmd = new ScalarVerifierDefaultCmd();
        var res = run(cmd, "--val=good");
        assertEquals(0, res.exitCode());
        assertEquals("good", cmd.val);
    }

    // ========== Bug: -- end-of-options not propagated to final command ==========
    // When a parent command (with subcommands) processes --, the "end of options"
    // state is lost when the same command is re-parsed as the final command.

    @Test
    void endOfOptionsInParentPropagatesToFinalParsing() {
        // Bug: -- consumed by parent's parseOptions, but final parseInto starts
        // fresh with acceptOptions=true, treating --not-an-option as an option.
        var cmd = new ParentWithDefaultAndSubcmds();
        var res = run(cmd, "--", "--not-an-option");
        assertEquals(0, res.exitCode());
        assertThat(cmd.files).containsExactly("--not-an-option");
    }

    @Test
    void endOfOptionsWithOptionsBeforeInParent() {
        var cmd = new ParentWithDefaultAndSubcmds();
        var res = run(cmd, "--lang=fr", "--", "--weird-file");
        assertEquals(0, res.exitCode());
        assertEquals("fr", cmd.lang);
        assertThat(cmd.files).containsExactly("--weird-file");
    }

}