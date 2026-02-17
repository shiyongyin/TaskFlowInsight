package com.syy.taskflowinsight.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Demo07 冒烟测试：验证 {@link Demo07_MapCollectionEntities#main(String[])} 能完整执行。
 *
 * @since 3.0.0
 */
class Demo07OutputTest {

    @Test
    @DisplayName("Demo07_MapCollectionEntities.main() 执行无异常")
    void testDemo07RunsWithoutException() {
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
            assertDoesNotThrow(() -> Demo07_MapCollectionEntities.main(new String[]{}));
        } finally {
            System.setOut(originalOut);
        }
    }
}
