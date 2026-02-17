package com.syy.taskflowinsight.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Demo06 / Demo07 冒烟测试：验证 main() 能完整执行不抛异常。
 *
 * @since 3.0.0
 */
class Demo06And07ExecutionTest {

    @Test
    @DisplayName("Demo06_SetCollectionEntities.main() 执行无异常")
    void testDemo06Execution() {
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
            assertDoesNotThrow(() -> Demo06_SetCollectionEntities.main(new String[0]));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Demo07_MapCollectionEntities.main() 执行无异常")
    void testDemo07Execution() {
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
            assertDoesNotThrow(() -> Demo07_MapCollectionEntities.main(new String[0]));
        } finally {
            System.setOut(originalOut);
        }
    }
}
