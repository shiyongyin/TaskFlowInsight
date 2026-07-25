package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.tracking.path.PathBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路径语法性能基准测试
 * 验证P50<1ms、P99<5ms的性能目标
 */
@EnabledIfSystemProperty(named = "tfi.perf.enabled", matches = "true")
class PathSyntaxPerformanceTest {

    private static final int ITERATIONS = 10000;

    @BeforeEach
    void setUp() {
        // 预热JVM
        for (int i = 0; i < 1000; i++) {
            PathBuilder.mapKey("parent", "key" + i);
        }
    }

    @Test
    @DisplayName("路径构建性能：P50<1ms，P99<5ms")
    void testPathBuildingPerformance() {
        long[] durations = new long[ITERATIONS];

        // 测试路径构建性能
        for (int i = 0; i < ITERATIONS; i++) {
            long startTime = System.nanoTime();

            // 构建不同类型的路径
            PathBuilder.mapKey("parent", "key with spaces " + i);
            PathBuilder.mapKey("parent", "key\"with\"quotes" + i);
            PathBuilder.arrayIndex("parent", i);
            PathBuilder.fieldPath("parent", "field" + i);
            PathBuilder.setElement("parent", "element" + i);

            long endTime = System.nanoTime();
            durations[i] = endTime - startTime;
        }

        // 计算统计数据
        java.util.Arrays.sort(durations);
        long p50 = durations[ITERATIONS / 2];
        long p99 = durations[(int) (ITERATIONS * 0.99)];

        // 转换为毫秒
        double p50Ms = p50 / 1_000_000.0;
        double p99Ms = p99 / 1_000_000.0;

        System.out.printf("路径构建性能 - P50: %.3fms, P99: %.3fms%n", p50Ms, p99Ms);

        // 验证性能目标
        assertTrue(p50Ms < 1.0, "P50应该<1ms，实际: " + p50Ms + "ms");
        assertTrue(p99Ms < 5.0, "P99应该<5ms，实际: " + p99Ms + "ms");
    }

    @Test
    @DisplayName("字符串转义性能：特殊字符处理")
    void testStringEscapingPerformance() {
        String[] testStrings = {
            "simple_key",
            "key with spaces",
            "key\"with\"quotes",
            "key\\with\\backslashes",
            "key\nwith\nnewlines",
            "key\twith\ttabs",
            "key\rwith\rreturns",
            "complex\"key\\with\nmultiple\tspecial\rchars"
        };

        long totalDuration = 0;
        int totalOperations = 0;

        for (String testString : testStrings) {
            long startTime = System.nanoTime();

            for (int i = 0; i < 1000; i++) {
                PathBuilder.mapKey("parent", testString + i);
                totalOperations++;
            }

            long endTime = System.nanoTime();
            totalDuration += (endTime - startTime);
        }

        double avgDurationNs = (double) totalDuration / totalOperations;
        double avgDurationMs = avgDurationNs / 1_000_000.0;

        System.out.printf("字符串转义平均耗时: %.6fms (%d operations)%n", avgDurationMs, totalOperations);

        // 验证每个转义操作<0.1ms
        assertTrue(avgDurationMs < 0.1, "转义操作平均耗时应<0.1ms，实际: " + avgDurationMs + "ms");
    }

}
