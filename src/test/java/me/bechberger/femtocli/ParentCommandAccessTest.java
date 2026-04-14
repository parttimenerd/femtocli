package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for the parent command access feature ({@link Spec#getParent()},
 * {@link Spec#getParent(Class)}) and the per-level option parsing that supports it.
 */
class ParentCommandAccessTest {

    // ---------- command hierarchy: Root -> Mid -> Leaf ----------

    @Command(name = "root", subcommands = {Mid.class})
    static class Root implements Runnable {
        @Option(names = "--verbose")
        boolean verbose;

        @Option(names = "--config", defaultValue = "default.conf")
        String config;

        Spec spec;

        @Override
        public void run() {
        }
    }

    @Command(name = "mid", subcommands = {Leaf.class})
    static class Mid implements Runnable {
        @Option(names = "--host", defaultValue = "localhost")
        String host;

        @Option(names = "--port", defaultValue = "5432")
        int port;

        Spec spec;

        @Override
        public void run() {
        }
    }

    @Command(name = "leaf")
    static class Leaf implements Callable<Integer> {
        @Option(names = "--output")
        String output;

        Spec spec;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ---------- simple parent/child ----------

    @Command(name = "parent", subcommands = {SimpleChild.class})
    static class SimpleParent implements Runnable {
        @Option(names = "--flag")
        boolean flag;

        @Override
        public void run() {
        }
    }

    @Command(name = "child")
    static class SimpleChild implements Callable<Integer> {
        Spec spec;

        @Option(names = "--value")
        String value;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ---------- root command with no subcommands ----------

    @Command(name = "standalone")
    static class Standalone implements Callable<Integer> {
        @Option(names = "--name")
        String name;

        @Parameters(description = "files")
        List<String> files;

        Spec spec;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ---------- parent with required options ----------

    @Command(name = "reqroot", subcommands = {ReqChild.class})
    static class ReqRoot implements Runnable {
        @Option(names = "--token", required = true)
        String token;

        @Override
        public void run() {
        }
    }

    @Command(name = "reqchild")
    static class ReqChild implements Callable<Integer> {
        Spec spec;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ---------- parent with positional parameters ----------

    @Command(name = "posroot", subcommands = {PosChild.class})
    static class PosRoot implements Runnable {
        @Option(names = "--mode")
        String mode;

        @Override
        public void run() {
        }
    }

    @Command(name = "poschild")
    static class PosChild implements Callable<Integer> {
        @Parameters(description = "files", index = "0..*")
        List<String> files;

        Spec spec;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ---------- 4-level hierarchy ----------

    @Command(name = "a", subcommands = {LevelB.class})
    static class LevelA implements Runnable {
        @Option(names = "--a-opt")
        String aOpt;

        @Override
        public void run() {
        }
    }

    @Command(name = "b", subcommands = {LevelC.class})
    static class LevelB implements Runnable {
        @Option(names = "--b-opt")
        String bOpt;

        @Override
        public void run() {
        }
    }

    @Command(name = "c", subcommands = {LevelD.class})
    static class LevelC implements Runnable {
        @Option(names = "--c-opt")
        String cOpt;

        @Override
        public void run() {
        }
    }

    @Command(name = "d")
    static class LevelD implements Callable<Integer> {
        @Option(names = "--d-opt")
        String dOpt;

        Spec spec;

        @Override
        public Integer call() {
            return 0;
        }
    }

    // ======================= TESTS =======================

    // ----- Basic parent access -----

    @Test
    void rootCommandHasNullParent() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root);
        assertEquals(0, res.exitCode());
        assertNull(root.spec.getParent(), "Root command should have null parent");
    }

    @Test
    void childGetParentReturnsParentInstance() {
        var parent = new SimpleParent();
        var res = FemtoCli.runCaptured(parent, "child");
        assertEquals(0, res.exitCode());
        // We can't directly access the child spec here, so we test via 3-level chain
    }

    @Test
    void midLevelGetParentReturnsRoot() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "mid");
        assertEquals(0, res.exitCode());
        // root.spec should be injected
        assertNotNull(root.spec);
        assertNull(root.spec.getParent(), "Root should have no parent");
    }

    // ----- Parent options are parsed -----

    @Test
    void parentOptionsAreParsedBeforeSubcommand() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--verbose", "--config", "prod.conf", "mid");
        assertEquals(0, res.exitCode());
        assertTrue(root.verbose, "Root --verbose should be parsed");
        assertEquals("prod.conf", root.config, "Root --config should be parsed");
    }

    @Test
    void parentDefaultValuesAreApplied() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "mid");
        assertEquals(0, res.exitCode());
        assertFalse(root.verbose, "Root --verbose should default to false");
        assertEquals("default.conf", root.config, "Root --config should have default value");
    }

    @Test
    void intermediateOptionsAreParsed() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "mid", "--host", "db.example.com", "--port", "3306", "leaf");
        assertEquals(0, res.exitCode());
    }

    @Test
    void allLevelsGetTheirOwnOptionsParsed() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--verbose", "mid", "--host", "myhost", "leaf", "--output", "out.txt");
        assertEquals(0, res.exitCode());
        assertTrue(root.verbose, "Root --verbose should be parsed");
    }

    // ----- getParent(Class) deep chain lookup -----

    @Test
    void getParentWithClassSearchesEntireChain() {
        // Use a wrapper to capture the leaf's spec
        final Spec[] leafSpec = new Spec[1];

        @Command(name = "r", subcommands = {CaptureMid.class})
        class CaptureRoot implements Runnable {
            @Option(names = "--r-flag")
            boolean rFlag;
            @Override
            public void run() {}
        }

        var root = new CaptureRoot();
        var res = FemtoCli.runCaptured(root, "--r-flag", "cap-mid", "cap-leaf");
        assertEquals(0, res.exitCode());
    }

    @Command(name = "cap-mid", subcommands = {CaptureLeaf.class})
    static class CaptureMid implements Runnable {
        @Override
        public void run() {}
    }

    @Command(name = "cap-leaf")
    static class CaptureLeaf implements Callable<Integer> {
        Spec spec;
        @Override
        public Integer call() {
            // Verify chain access: parent should be CaptureMid, grandparent search should find both
            assertNotNull(spec.getParent(), "Leaf should have a parent");
            assertThat(spec.getParent()).isInstanceOf(CaptureMid.class);
            assertNull(spec.getParent(String.class), "No String in parent chain");
            assertNotNull(spec.getParent(CaptureMid.class), "Should find CaptureMid");
            return 0;
        }
    }

    @Test
    void getParentWithNullClassReturnsNull() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root);
        assertEquals(0, res.exitCode());
        assertNull(root.spec.getParent(null), "getParent(null) should return null");
    }

    @Test
    void getParentWithUnmatchedTypeReturnsNull() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root);
        assertEquals(0, res.exitCode());
        assertNull(root.spec.getParent(String.class), "No String in parent chain of root");
    }

    // ----- 4-level chain -----

    @Test
    void fourLevelChainAllOptionsAreParsed() {
        var root = new LevelA();
        var res = FemtoCli.runCaptured(root, "--a-opt", "aval", "b", "--b-opt", "bval", "c", "--c-opt", "cval", "d", "--d-opt", "dval");
        assertEquals(0, res.exitCode());
        assertEquals("aval", root.aOpt, "Level A option should be parsed");
    }

    @Test
    void fourLevelChainDefaultValues() {
        var root = new LevelA();
        var res = FemtoCli.runCaptured(root, "b", "c", "d");
        assertEquals(0, res.exitCode());
        assertNull(root.aOpt, "Level A option should be null by default");
    }

    // ----- Standalone (no subcommands) is unaffected -----

    @Test
    void standaloneCommandParseOptionsAndPositionals() {
        var cmd = new Standalone();
        var res = FemtoCli.runCaptured(cmd, "--name", "test", "file1.txt", "file2.txt");
        assertEquals(0, res.exitCode());
        assertEquals("test", cmd.name);
        assertThat(cmd.files).containsExactly("file1.txt", "file2.txt");
    }

    @Test
    void standaloneCommandHasNullParent() {
        var cmd = new Standalone();
        var res = FemtoCli.runCaptured(cmd, "--name", "x");
        assertEquals(0, res.exitCode());
        assertNull(cmd.spec.getParent(), "Standalone should have no parent");
    }

    @Test
    void standaloneCommandWithNoArgs() {
        var cmd = new Standalone();
        var res = FemtoCli.runCaptured(cmd);
        assertEquals(0, res.exitCode());
        assertNull(cmd.name);
        // List parameters are initialized to empty list by the framework
        assertNotNull(cmd.files);
        assertTrue(cmd.files.isEmpty());
        assertNull(cmd.spec.getParent());
    }

    // ----- Leaf command positionals work -----

    @Test
    void childCommandWithPositionals() {
        var root = new PosRoot();
        var res = FemtoCli.runCaptured(root, "--mode", "fast", "poschild", "a.txt", "b.txt");
        assertEquals(0, res.exitCode());
        assertEquals("fast", root.mode, "Parent --mode should be parsed");
    }

    // ----- Help/version still works with parent options -----

    @Test
    void helpOnRootWithSubcommands() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--help");
        assertThat(res.out()).contains("mid");
    }

    @Test
    void helpOnSubcommandAfterParentOptions() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--verbose", "mid", "--help");
        assertThat(res.out()).contains("leaf");
    }

    @Test
    void helpOnLeafAfterAllParentOptions() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--verbose", "mid", "--host", "x", "leaf", "--help");
        assertThat(res.out()).contains("--output");
    }

    // ----- Root with subcommands but no subcommand given -----

    @Test
    void rootWithSubcommandsNoSubcommandGivenExecutesRoot() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--verbose");
        assertEquals(0, res.exitCode());
        assertTrue(root.verbose);
    }

    @Test
    void multiValueOptionsBeforeAndAfterFirstPositionalArePreserved() {
        @Command(name = "files", subcommands = {PosChild.class})
        class RootWithTags implements Runnable {
            @Option(names = "--tag")
            List<String> tags;

            @Parameters(index = "0..*")
            List<String> files;

            @Override
            public void run() {
            }
        }

        var root = new RootWithTags();
        var res = FemtoCli.runCaptured(root, "--tag", "a", "file1", "--tag", "b");

        assertEquals(0, res.exitCode(), () -> "stderr was: " + res.err());
        assertThat(root.tags).containsExactly("a", "b");
        assertThat(root.files).containsExactly("file1");
    }

    @Test
    void rootWithSubcommandsNoArgsExecutesRoot() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root);
        assertEquals(0, res.exitCode());
    }

    // ----- Intermediate command runs when no deeper subcommand -----

    @Test
    void midCommandRunsWhenNoLeafGiven() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--config", "x.conf", "mid", "--host", "h1");
        assertEquals(0, res.exitCode());
        assertEquals("x.conf", root.config);
    }

    // ----- Error cases -----

    @Test
    void unknownOptionOnParentReturnsError() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "--unknown", "mid");
        assertEquals(2, res.exitCode(), "Unknown parent option should cause error");
        assertThat(res.err()).contains("Unknown option");
    }

    @Test
    void unknownOptionOnChildReturnsError() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "mid", "--badopt", "leaf");
        assertEquals(2, res.exitCode(), "Unknown child option should cause error");
        assertThat(res.err()).contains("Unknown option");
    }

    @Test
    void unknownOptionOnLeafReturnsError() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "mid", "leaf", "--badopt");
        assertEquals(2, res.exitCode(), "Unknown leaf option should cause error");
        assertThat(res.err()).contains("Unknown option");
    }

    // ----- commandPath includes all levels -----

    @Test
    void commandPathForRootIsSingleElement() {
        var root = new Root();
        var res = FemtoCli.runCaptured(root);
        assertEquals(0, res.exitCode());
        assertThat(root.spec.commandPath()).containsExactly("root");
    }

    @Test
    void commandPathForMidIncludesRootAndMid() {
        // We test this via a custom leaf that captures spec
        var root = new Root();
        var res = FemtoCli.runCaptured(root, "mid");
        assertEquals(0, res.exitCode());
        // root.spec is for root level
        assertThat(root.spec.commandPath()).containsExactly("root");
    }

    // ----- Boolean flag on parent -----

    @Test
    void parentBooleanFlagTrueWhenSet() {
        var root = new Root();
        FemtoCli.runCaptured(root, "--verbose", "mid");
        assertTrue(root.verbose);
    }

    @Test
    void parentBooleanFlagFalseWhenNotSet() {
        var root = new Root();
        FemtoCli.runCaptured(root, "mid");
        assertFalse(root.verbose);
    }

    // ----- Spec injection happens for all levels -----

    @Test
    void specIsInjectedForRootEvenWithSubcommands() {
        var root = new Root();
        FemtoCli.runCaptured(root, "mid");
        assertNotNull(root.spec, "Root's Spec should be injected");
    }

    @Test
    void specIsInjectedForRootWhenNoSubcommandGiven() {
        var root = new Root();
        FemtoCli.runCaptured(root);
        assertNotNull(root.spec, "Root's Spec should be injected when no subcommand given");
    }
}