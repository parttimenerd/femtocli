package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParentCommandAccessTest {

    @Test
    public void testChildCanAccessParentCommand() {
        var res = FemtoCli.runCaptured(new ParentCommandAccess(), "child", "--name", "test");

        String out = res.out();
        assertTrue(out.contains("Child 'test' executed"), "Expected child execution message");
        assertTrue(out.contains("Parent verbose: false"), "Expected verbose from parent");
        assertTrue(out.contains("Parent debug: false"), "Expected debug from parent");
        assertTrue(out.contains("Parent command verified via typed access"), "Expected typed access");
    }

    @Test
    public void testChildWithDefaultName() {
        var res = FemtoCli.runCaptured(new ParentCommandAccess(), "child");

        String out = res.out();
        assertTrue(out.contains("Child 'child' executed"), "Expected default child name");
        assertTrue(out.contains("Parent verbose: false"), "Expected verbose flag");
        assertTrue(out.contains("Parent debug: false"), "Expected debug flag");
    }
}