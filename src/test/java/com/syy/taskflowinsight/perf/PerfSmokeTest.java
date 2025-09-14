package com.syy.taskflowinsight.perf;

import com.syy.taskflowinsight.api.TFI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CI 轻量性能冒烟测试（非严苛基准）。
 * 目的：为 CI 提供“性能不退化”的栅栏，避免明显回归。
 * 说明：阈值刻意放宽，避免环境抖动导致误报；重场景放在 perf profile。
 */
class PerfSmokeTest {

    static class Order {
        String status;
        double amount;
    }

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    @Test
    @DisplayName("2字段轻量性能冒烟：平均<5ms/次（CI栅栏）")
    void twoFieldSmoke() {
        // 迭代次数与阈值保持温和，避免 CI 抖动误报
        final int iterations = 200;

        Order o = new Order();
        o.status = "PENDING";
        o.amount = 100.0;

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            // 只测核心热路径：track→修改→getChanges→清理
            TFI.track("order", o, "status", "amount");
            o.status = (i % 2 == 0) ? "PAID" : "PENDING";
            o.amount = 100.0 + i;
            TFI.getChanges();
            TFI.clearAllTracking();
        }
        long elapsedNs = System.nanoTime() - start;
        double avgMs = (elapsedNs / 1_000_000.0) / iterations;

        // 阈值：平均 < 5ms/次（环境友好），仅作为退化栅栏
        Assertions.assertTrue(avgMs < 5.0,
                String.format("2字段轻量性能冒烟失败：avg=%.3fms/次（阈值<5ms）", avgMs));
    }
}

