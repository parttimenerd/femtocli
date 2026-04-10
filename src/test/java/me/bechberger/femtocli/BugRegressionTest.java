package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for bugs found during code review.
 */
class BugRegressionTest {

    private static RunResult run(Object command, String... args) {
        return FemtoCli.builder().runCaptured(command, args);
    }

    // ====================================================================
    // Bug 1: Required boolean options missing from synopsis/usage line.
    //
    // In HelpRenderer.renderSynopsis the code handles:
    //   if (!isBoolean) { ... parts.add(...) }
    //   else if (!opt.opt.required()) { parts.add("[" + optName + "]"); }
    //
    // When isBoolean && required, nothing is added to the synopsis.
    // ====================================================================

    @Command(name = "reqbool", description = "Test required boolean", mixinStandardHelpOptions = true)
    static class RequiredBoolCmd implements Runnable {
        @Option(names = "--force", description = "Force operation", required = true)
        boolean force;

        @Override
        public void run() {}
    }

    @Test
    void requiredBooleanOptionAppearsInSynopsis() {
        var res = run(new RequiredBoolCmd(), "--help");
        assertEquals(0, res.exitCode());
        // Required boolean should appear in usage line (without brackets)
        assertThat(res.out()).contains("--force");
        // Specifically, the Usage line should include --force
        String usageLine = res.out().lines()
                .filter(l -> l.startsWith("Usage:"))
                .findFirst().orElse("");
        assertThat(usageLine).contains("--force");
    }

    @Test
    void requiredBooleanOptionMissingGivesError() {
        var res = run(new RequiredBoolCmd());
        assertEquals(2, res.exitCode());
        assertThat(res.err()).contains("Missing required option");
        assertThat(res.err()).contains("--force");
    }

    // ====================================================================
    // Bug 2: Multi-line @Command(description={"line1","line2"}) only
    //         renders the first line in help output.
    //
    // In HelpRenderer.render:
    //   out.println(annotation.description()[0]);
    // should iterate all lines.
    // ====================================================================

    @Command(name = "multiline",
            description = {"First line of description.", "Second line of description."},
            mixinStandardHelpOptions = true)
    static class MultiLineDescCmd implements Runnable {
        @Override
        public void run() {}
    }

    @Test
    void multiLineDescriptionShowsAllLines() {
        var res = run(new MultiLineDescCmd(), "--help");
        assertEquals(0, res.exitCode());
        assertThat(res.out()).contains("First line of description.");
        assertThat(res.out()).contains("Second line of description.");
    }

    // ====================================================================
    // Bug 3: Double newline before "did you mean" suggestion.
    //
    // DEFAULT_SUGGESTION_TEMPLATE starts with "\n" but the code in
    // parseOption also prepends "\n", creating a blank line between
    // the error and the suggestion.
    // ====================================================================

    @Command(name = "suggest", mixinStandardHelpOptions = true)
    static class SuggestCmd implements Runnable {
        @Option(names = "--verbose", description = "Verbose mode")
        boolean verbose;

        @Override
        public void run() {}
    }

    @Test
    void didYouMeanSuggestionHasNoDoubleNewline() {
        // Use default template (which used to produce double newline)
        var res = FemtoCli.builder().runCaptured(new SuggestCmd(), "--verbse");
        assertEquals(2, res.exitCode());
        String err = res.err();
        assertThat(err).contains("Unknown option: --verbse");
        assertThat(err).contains("--verbose");
        // There should NOT be a blank line between the error and the suggestion
        // (i.e., no "\n\n" between "Unknown option" line and "tip:" line)
        String errorBlock = err.substring(0, err.indexOf("\n\nUsage:") >= 0 ? err.indexOf("\n\nUsage:") : err.length());
        assertThat(errorBlock).doesNotContain("\n\n");
    }

    // ====================================================================
    // Bug 4: Boolean.parseBoolean silently returns false for invalid values.
    //
    // --flag=yes, --flag=1, --flag=on all silently become false because
    // Boolean.parseBoolean only recognizes "true" (case-insensitive).
    // Any other string silently returns false. This is a data corruption
    // bug: users reasonably expect "yes"/"on"/"1" to mean true.
    // ====================================================================

    @Command(name = "boolparse", mixinStandardHelpOptions = true)
    static class BoolParseCmd implements Runnable {
        @Option(names = "--flag")
        boolean flag;

        @Override
        public void run() {}
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "on", "1", "Yes", "ON", "TRUE", "True"})
    void booleanTruthyValuesAreAccepted(String val) {
        BoolParseCmd cmd = new BoolParseCmd();
        var res = run(cmd, "--flag=" + val);
        assertEquals(0, res.exitCode(), "should accept '" + val + "' as boolean true");
        assertThat(cmd.flag).as("'%s' should parse to true", val).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "no", "off", "0", "False", "NO", "OFF"})
    void booleanFalsyValuesAreAccepted(String val) {
        BoolParseCmd cmd = new BoolParseCmd();
        var res = run(cmd, "--flag=" + val);
        assertEquals(0, res.exitCode(), "should accept '" + val + "' as boolean false");
        assertThat(cmd.flag).as("'%s' should parse to false", val).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"maybe", "2", "yep", "nope", "truthy"})
    void booleanInvalidValuesAreRejected(String val) {
        var res = run(new BoolParseCmd(), "--flag=" + val);
        assertEquals(2, res.exitCode(), "should reject '" + val + "' as invalid boolean");
        assertThat(res.err()).contains("Invalid value");
    }

    @Test
    void booleanBoxedTruthyValues() {
        @Command(name = "boxedbool", mixinStandardHelpOptions = true)
        class BoxedBoolCmd implements Runnable {
            @Option(names = "--flag")
            Boolean flag;

            @Override
            public void run() {}
        }

        BoxedBoolCmd cmd = new BoxedBoolCmd();
        var res = run(cmd, "--flag=yes");
        assertEquals(0, res.exitCode());
        assertThat(cmd.flag).isTrue();
    }

    // ====================================================================
    // Bug 5: wrapLines drops intentional empty lines in descriptions.
    //
    // In HelpRenderer.wrapLines:
    //   if (line.isEmpty()) continue;
    // This drops blank lines that might be intentional paragraph breaks
    // in multi-line option descriptions.
    // ====================================================================

    @Command(name = "blanklines", mixinStandardHelpOptions = true)
    static class BlankLineDescCmd implements Runnable {
        @Option(names = "--opt", description = "Paragraph one.\n\nParagraph two.")
        String opt;

        @Override
        public void run() {}
    }

    @Test
    void blankLinesInOptionDescriptionArePreserved() {
        var res = run(new BlankLineDescCmd(), "--help");
        assertEquals(0, res.exitCode());
        String help = res.out();
        assertThat(help).contains("Paragraph one.");
        assertThat(help).contains("Paragraph two.");
        // The blank line between paragraphs should produce an empty line in the output
        // (the help renderer aligns descriptions with padding, so we check for an
        //  empty-ish line between the two paragraphs)
        int p1 = help.indexOf("Paragraph one.");
        int p2 = help.indexOf("Paragraph two.");
        assertThat(p1).isGreaterThanOrEqualTo(0);
        assertThat(p2).isGreaterThan(p1);
        String between = help.substring(p1 + "Paragraph one.".length(), p2);
        // There should be at least two newlines (i.e. a blank line) between paragraphs
        long newlines = between.chars().filter(c -> c == '\n').count();
        assertThat(newlines).as("Expected blank line between paragraphs").isGreaterThanOrEqualTo(2);
    }
}
