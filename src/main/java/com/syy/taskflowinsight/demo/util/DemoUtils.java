package com.syy.taskflowinsight.demo.util;

/**
 * 演示通用工具方法。
 */
public final class DemoUtils {
    private DemoUtils() {}

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

