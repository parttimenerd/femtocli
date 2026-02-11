package examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.examples.ArraysAndLists;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArraysAndListsTest {

    @Test
    public void testArraysAndLists() {
        var res = MiniCli.runCaptured(new ArraysAndLists(), new String[]{
                "--xs=a,b",
                "--ys=c,d",
                "rest1",
                "rest2"
        });
        assertEquals(0, res.exitCode());
        assertEquals("""
                xs=[a, b]
                ys=[c, d]
                rest=[rest1, rest2]
                """, res.out());
    }
}