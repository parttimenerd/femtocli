package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoveCommandsTest {

    @Command(name = "root", mixinStandardHelpOptions = true, subcommands = {Status.class, Admin.class})
    static class Root implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(name = "status", description = "Show status")
    static class Status implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("status");
            return 0;
        }
    }

    @Command(name = "admin", description = "Admin tasks", mixinStandardHelpOptions = true,
            subcommands = {Danger.class})
    static class Admin implements Runnable {
        @Override
        public void run() {
            System.out.println("admin");
        }
    }

    @Command(name = "danger", description = "Dangerous admin task")
    static class Danger implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("danger");
            return 0;
        }
    }

    @Test
    void removingParentCommandHidesItAndItsTransitiveCommands() {
        RunResult rootHelp = FemtoCli.builder()
                .removeCommands(Admin.class)
                .runCaptured(new Root(), "--help");

        assertEquals(0, rootHelp.exitCode());
        assertThat(rootHelp.out()).contains("status");
        assertThat(rootHelp.out()).doesNotContain("admin");
        assertThat(rootHelp.out()).doesNotContain("danger");

        RunResult removedPath = FemtoCli.builder()
                .removeCommands(Admin.class)
                .runCaptured(new Root(), "admin", "danger");

        assertEquals(2, removedPath.exitCode());
        assertThat(removedPath.err()).contains("Unexpected parameter: admin");

        RunResult visiblePath = FemtoCli.builder()
                .removeCommands(Admin.class)
                .runCaptured(new Root(), "status");

        assertEquals(0, visiblePath.exitCode(), visiblePath.err());
        assertThat(visiblePath.out().trim()).isEqualTo("status");
    }

    @Test
    void removingLeafCommandKeepsParentButHidesLeafFromHelpAndRouting() {
        RunResult rootHelp = FemtoCli.builder()
                .removeCommands(Danger.class)
                .runCaptured(new Root(), "--help");

        assertEquals(0, rootHelp.exitCode());
        assertThat(rootHelp.out()).contains("admin");
        assertThat(rootHelp.out()).doesNotContain("danger");

        RunResult adminHelp = FemtoCli.builder()
                .removeCommands(Danger.class)
                .runCaptured(new Root(), "admin", "--help");

        assertEquals(0, adminHelp.exitCode());
        assertThat(adminHelp.out()).doesNotContain("danger");
        assertThat(adminHelp.out()).doesNotContain("[COMMAND]");

        RunResult removedLeafPath = FemtoCli.builder()
                .removeCommands(Danger.class)
                .runCaptured(new Root(), "admin", "danger");

        assertEquals(2, removedLeafPath.exitCode());
        assertThat(removedLeafPath.err()).contains("Unexpected parameter: danger");
    }

    @Test
    void removingMultipleCommandsWorks() {
        RunResult rootHelp = FemtoCli.builder()
                .removeCommands(Admin.class, Status.class)
                .runCaptured(new Root(), "--help");

        assertEquals(0, rootHelp.exitCode());
        assertThat(rootHelp.out()).doesNotContain("admin");
        assertThat(rootHelp.out()).doesNotContain("status");
        assertThat(rootHelp.out()).doesNotContain("[COMMAND]");
    }
}