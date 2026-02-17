package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.tracking.path.PathBuilder;
import com.syy.taskflowinsight.tracking.detector.ChangeRecordComparator;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.TestChangeRecordFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Test
    @DisplayName("三级排序性能：1000条记录P99<5ms")
    void testThreeLevelSortingPerformance() {
        // 创建大数据集
        List<ChangeRecord> records = createLargeChangeRecordDataset(1000);

        long[] durations = new long[100]; // 执行100次测试

        for (int i = 0; i < durations.length; i++) {
            // 创建副本以避免影响
            List<ChangeRecord> testRecords = new ArrayList<>(records);

            long startTime = System.nanoTime();
            testRecords.sort(ChangeRecordComparator.INSTANCE);
            long endTime = System.nanoTime();

            durations[i] = endTime - startTime;
        }

        // 计算统计数据
        java.util.Arrays.sort(durations);
        long p50 = durations[durations.length / 2];
        long p99 = durations[(int) (durations.length * 0.99)];

        // 转换为毫秒
        double p50Ms = p50 / 1_000_000.0;
        double p99Ms = p99 / 1_000_000.0;

        System.out.printf("排序性能（1000条记录）- P50: %.3fms, P99: %.3fms%n", p50Ms, p99Ms);

        // 验证性能目标
        assertTrue(p99Ms < 5.0, "1000条记录排序P99应<5ms，实际: " + p99Ms + "ms");
        assertTrue(p50Ms < 2.0, "1000条记录排序P50应<2ms，实际: " + p50Ms + "ms");
    }

    @Test
    @DisplayName("缓存性能：验证命中率提升效果")
    void testCachePerformance() {
        // 清理缓存
        PathBuilder.clearCache();

        String[] keys = new String[100];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "key\"with\\special" + (i % 20) + "chars"; // 20种不同的键，重复使用
        }

        // 首次执行（缓存miss）
        long uncachedStartTime = System.nanoTime();
        for (String key : keys) {
            PathBuilder.mapKey("parent", key);
        }
        long uncachedDuration = System.nanoTime() - uncachedStartTime;

        // 二次执行（缓存hit）
        long cachedStartTime = System.nanoTime();
        for (String key : keys) {
            PathBuilder.mapKey("parent", key);
        }
        long cachedDuration = System.nanoTime() - cachedStartTime;

        double speedupRatio = (double) uncachedDuration / cachedDuration;

        System.out.printf("缓存性能 - 无缓存: %dns, 有缓存: %dns, 加速比: %.2fx%n",
                         uncachedDuration, cachedDuration, speedupRatio);

        // 验证缓存大小
        int cacheSize = PathBuilder.getCacheSize();
        assertTrue(cacheSize > 0, "缓存应该包含转义字符串");
        assertTrue(cacheSize <= 20, "缓存大小应该合理：" + cacheSize);

        // 验证缓存带来的性能提升
        assertTrue(speedupRatio > 1.2, "缓存应该带来至少20%的性能提升，实际加速比: " + speedupRatio);
    }

    @Test
    @DisplayName("内存使用验证：缓存不应导致内存泄漏")
    void testMemoryUsage() {
        // 获取初始内存
        System.gc();
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // 生成大量不同的键进行测试
        for (int i = 0; i < 10000; i++) {
            PathBuilder.mapKey("parent", "unique_key_with_special\"chars\\" + i);
        }

        // 获取使用后内存
        System.gc();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long memoryIncrease = usedMemory - initialMemory;
        double memoryIncreaseMB = memoryIncrease / (1024.0 * 1024.0);

        System.out.printf("内存增量: %.2fMB (初始: %dB, 使用后: %dB)%n",
                         memoryIncreaseMB, initialMemory, usedMemory);

        // 验证内存增量<5MB（保守估计）
        assertTrue(memoryIncreaseMB < 5.0, "内存增量应<5MB，实际: " + memoryIncreaseMB + "MB");

        // 清理缓存并验证
        PathBuilder.clearCache();
        System.gc();

        long cleanedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        assertTrue(cleanedMemory < usedMemory, "清理缓存后内存使用应降低");
    }

    /**
     * 创建大数据集用于排序测试
     */
    private List<ChangeRecord> createLargeChangeRecordDataset(int size) {
        List<ChangeRecord> records = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            String path = "path" + (i % 100); // 100种不同路径
            ChangeType type = ChangeType.values()[i % ChangeType.values().length];
            String oldValue = "oldValue" + i;
            String newValue = "newValue" + i;

            records.add(TestChangeRecordFactory.create(path, oldValue, newValue, type));
        }

        return records;
    }
}