package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArraysAndListsTest {

    @Test
    public void testArraysAndLists() {
        var res = FemtoCli.runCaptured(new ArraysAndLists(), "--xs=a,b",
                "--ys=c,d",
                "rest1",
                "rest2");
        assertEquals(0, res.exitCode());
        assertEquals("""
                xs=[a, b]
                ys=[c, d]
                rest=[rest1, rest2]
                """, res.out());
    }
}