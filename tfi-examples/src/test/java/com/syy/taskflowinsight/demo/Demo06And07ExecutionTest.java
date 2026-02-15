package com.syy.taskflowinsight.demo;

import org.junit.jupiter.api.Test;

/**
 * Execution test for Demo06 and Demo07 to verify they run without errors
 */
class jDemo06And07ExecutionTest {

    @Test
    void testDemo06Execution() {
        // Redirect System.out to avoid cluttering test output
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));

            // Run Demo06
            Demo06_SetCollectionEntities.main(new String[0]);

            // If we get here, the demo ran successfully
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testDemo07Execution() {
        // Redirect System.out to avoid cluttering test output
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));

            // Run Demo07
            Demo07_MapCollectionEntities.main(new String[0]);

            // If we get here, the demo ran successfully
        } finally {
            System.setOut(originalOut);
        }
    }
}
