package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DiffDetector性能测试
 * 验证P50 < 50μs, P95 < 200μs的性能指标
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-12
 */
class DiffDetectorPerformanceTest {
    
    private Map<String, Object> smallBefore;
    private Map<String, Object> smallAfter;
    private Map<String, Object> mediumBefore;
    private Map<String, Object> mediumAfter;
    private Map<String, Object> largeBefore;
    private Map<String, Object> largeAfter;
    
    @BeforeEach
    void setUp() {
        // 准备小规模数据（2个字段）
        smallBefore = new HashMap<>();
        smallAfter = new HashMap<>();
        smallBefore.put("field1", "value1");
        smallBefore.put("field2", 100);
        smallAfter.put("field1", "value1_changed");
        smallAfter.put("field2", 200);
        
        // 准备中等规模数据（20个字段）
        mediumBefore = new HashMap<>();
        mediumAfter = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            mediumBefore.put("field_" + i, "value_" + i);
            mediumAfter.put("field_" + i, "changed_" + i);
        }
        
        // 准备大规模数据（100个字段）
        largeBefore = new HashMap<>();
        largeAfter = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            largeBefore.put("field_" + i, generateValue(i));
            largeAfter.put("field_" + i, generateValue(i + 1000));
        }
    }
    
    private Object generateValue(int index) {
        // 生成不同类型的值，确保index和index+1000永远不相等
        switch (index % 5) {
            case 0: return "string_" + index;
            case 1: return index;
            case 2: return index * 1.5;
            case 3: return index < 500;  // 确保index(0-99)和index+1000(1000-1099)的boolean值不同
            case 4: return new Date(1000000000L + index * 1000L);
            default: return "default_" + index;
        }
    }
    
    @Test
    @DisplayName("性能基准测试 - 2字段对比P50应小于50μs")
    void testSmallScalePerformanceP50() {
        List<Long> durations = new ArrayList<>();
        int iterations = 10000;
        
        // 预热
        for (int i = 0; i < 1000; i++) {
            DiffDetector.diff("TestObject", smallBefore, smallAfter);
        }
        
        // 正式测试
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            DiffDetector.diff("TestObject", smallBefore, smallAfter);
            long end = System.nanoTime();
            durations.add(end - start);
        }
        
        // 计算P50
        Collections.sort(durations);
        long p50 = durations.get(iterations / 2);
        long p50Micros = TimeUnit.NANOSECONDS.toMicros(p50);
        
        System.out.println("Small scale (2 fields) P50: " + p50Micros + " μs");
        
        // 验证P50 < 50μs
        assertTrue(p50Micros < 50, 
            String.format("P50 performance not met: %d μs (expected < 50 μs)", p50Micros));
    }
    
    @Test
    @DisplayName("性能基准测试 - 2字段对比P95应小于200μs")
    void testSmallScalePerformanceP95() {
        List<Long> durations = new ArrayList<>();
        int iterations = 10000;
        
        // 预热
        for (int i = 0; i < 1000; i++) {
            DiffDetector.diff("TestObject", smallBefore, smallAfter);
        }
        
        // 正式测试
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            DiffDetector.diff("TestObject", smallBefore, smallAfter);
            long end = System.nanoTime();
            durations.add(end - start);
        }
        
        // 计算P95
        Collections.sort(durations);
        int p95Index = (int) (iterations * 0.95);
        long p95 = durations.get(p95Index);
        long p95Micros = TimeUnit.NANOSECONDS.toMicros(p95);
        
        System.out.println("Small scale (2 fields) P95: " + p95Micros + " μs");
        
        // 验证P95 < 200μs
        assertTrue(p95Micros < 200, 
            String.format("P95 performance not met: %d μs (expected < 200 μs)", p95Micros));
    }
    
    @Test
    @DisplayName("性能基准测试 - 20字段对比")
    void testMediumScalePerformance() {
        List<Long> durations = new ArrayList<>();
        int iterations = 5000;
        
        // 预热
        for (int i = 0; i < 500; i++) {
            DiffDetector.diff("TestObject", mediumBefore, mediumAfter);
        }
        
        // 正式测试
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", mediumBefore, mediumAfter);
            long end = System.nanoTime();
            durations.add(end - start);
            
            // 验证结果正确性
            assertEquals(20, changes.size());
        }
        
        // 计算统计数据
        Collections.sort(durations);
        long p50 = durations.get(iterations / 2);
        long p95 = durations.get((int) (iterations * 0.95));
        long p99 = durations.get((int) (iterations * 0.99));
        
        System.out.println("Medium scale (20 fields) performance:");
        System.out.println("  P50: " + TimeUnit.NANOSECONDS.toMicros(p50) + " μs");
        System.out.println("  P95: " + TimeUnit.NANOSECONDS.toMicros(p95) + " μs");
        System.out.println("  P99: " + TimeUnit.NANOSECONDS.toMicros(p99) + " μs");
        
        // 20字段的合理期望：P95 < 500μs
        assertTrue(TimeUnit.NANOSECONDS.toMicros(p95) < 500, 
            "Medium scale P95 should be under 500μs");
    }
    
    @Test
    @DisplayName("性能基准测试 - 100字段对比P95应小于2ms")
    void testLargeScalePerformance() {
        List<Long> durations = new ArrayList<>();
        int iterations = 1000;
        
        // 预热
        for (int i = 0; i < 100; i++) {
            DiffDetector.diff("TestObject", largeBefore, largeAfter);
        }
        
        // 正式测试
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", largeBefore, largeAfter);
            long end = System.nanoTime();
            durations.add(end - start);
            
            // 验证结果正确性
            assertEquals(100, changes.size());
        }
        
        // 计算统计数据
        Collections.sort(durations);
        long p50 = durations.get(iterations / 2);
        long p95 = durations.get((int) (iterations * 0.95));
        long p99 = durations.get((int) (iterations * 0.99));
        
        long p50Millis = TimeUnit.NANOSECONDS.toMillis(p50);
        long p95Millis = TimeUnit.NANOSECONDS.toMillis(p95);
        long p99Millis = TimeUnit.NANOSECONDS.toMillis(p99);
        
        System.out.println("Large scale (100 fields) performance:");
        System.out.println("  P50: " + p50Millis + " ms (" + TimeUnit.NANOSECONDS.toMicros(p50) + " μs)");
        System.out.println("  P95: " + p95Millis + " ms (" + TimeUnit.NANOSECONDS.toMicros(p95) + " μs)");
        System.out.println("  P99: " + p99Millis + " ms (" + TimeUnit.NANOSECONDS.toMicros(p99) + " μs)");
        
        // 验证P95 < 2ms
        assertTrue(p95Millis <= 2, 
            String.format("P95 performance not met: %d ms (expected <= 2 ms)", p95Millis));
    }
    
    @Test
    @DisplayName("性能测试 - 空快照对比")
    void testEmptySnapshotPerformance() {
        List<Long> durations = new ArrayList<>();
        int iterations = 10000;
        
        Map<String, Object> empty = new HashMap<>();
        
        // 测试空对比的性能
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", empty, empty);
            long end = System.nanoTime();
            durations.add(end - start);
            
            assertTrue(changes.isEmpty());
        }
        
        Collections.sort(durations);
        long p50 = durations.get(iterations / 2);
        long p95 = durations.get((int) (iterations * 0.95));
        
        System.out.println("Empty snapshot performance:");
        System.out.println("  P50: " + TimeUnit.NANOSECONDS.toMicros(p50) + " μs");
        System.out.println("  P95: " + TimeUnit.NANOSECONDS.toMicros(p95) + " μs");
        
        // 空快照对比应该非常快
        assertTrue(TimeUnit.NANOSECONDS.toMicros(p95) < 10, 
            "Empty snapshot comparison should be under 10μs");
    }
    
    @Test
    @DisplayName("性能测试 - 大量CREATE场景")
    void testMassCreatePerformance() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        // 创建100个新字段
        for (int i = 0; i < 100; i++) {
            after.put("new_field_" + i, "value_" + i);
        }
        
        List<Long> durations = new ArrayList<>();
        int iterations = 1000;
        
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
            long end = System.nanoTime();
            durations.add(end - start);
            
            assertEquals(100, changes.size());
        }
        
        Collections.sort(durations);
        long p95 = durations.get((int) (iterations * 0.95));
        
        System.out.println("Mass CREATE (100 fields) P95: " + 
            TimeUnit.NANOSECONDS.toMillis(p95) + " ms");
        
        assertTrue(TimeUnit.NANOSECONDS.toMillis(p95) <= 2, 
            "Mass CREATE P95 should be under 2ms");
    }
    
    @Test
    @DisplayName("性能测试 - 大量DELETE场景")
    void testMassDeletePerformance() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        // 删除100个字段
        for (int i = 0; i < 100; i++) {
            before.put("old_field_" + i, "value_" + i);
        }
        
        List<Long> durations = new ArrayList<>();
        int iterations = 1000;
        
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
            long end = System.nanoTime();
            durations.add(end - start);
            
            assertEquals(100, changes.size());
        }
        
        Collections.sort(durations);
        long p95 = durations.get((int) (iterations * 0.95));
        
        System.out.println("Mass DELETE (100 fields) P95: " + 
            TimeUnit.NANOSECONDS.toMillis(p95) + " ms");
        
        assertTrue(TimeUnit.NANOSECONDS.toMillis(p95) <= 2, 
            "Mass DELETE P95 should be under 2ms");
    }
    
    @Test
    @DisplayName("吞吐量测试")
    void testThroughput() {
        long testDurationMs = 1000; // 测试1秒
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDurationMs;
        int operations = 0;
        
        // 使用中等规模数据测试吞吐量
        while (System.currentTimeMillis() < endTime) {
            DiffDetector.diff("TestObject", mediumBefore, mediumAfter);
            operations++;
        }
        
        double throughput = operations * 1000.0 / testDurationMs;
        System.out.println("Throughput (20 fields): " + 
            String.format("%.0f", throughput) + " ops/sec");
        
        // 期望吞吐量：至少2000 ops/sec for 20字段
        assertTrue(throughput > 2000, 
            String.format("Throughput too low: %.0f ops/sec (expected > 2000)", throughput));
    }
    
    @Test
    @DisplayName("内存占用测试")
    void testMemoryFootprint() {
        // 获取初始内存
        System.gc();
        long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // 执行大量对比操作
        List<List<ChangeRecord>> allChanges = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            List<ChangeRecord> changes = DiffDetector.diff("TestObject", largeBefore, largeAfter);
            allChanges.add(changes);
        }
        
        // 获取最终内存
        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = afterMemory - beforeMemory;
        long memoryPerOperation = memoryUsed / 1000;
        
        System.out.println("Memory usage for 1000 operations (100 fields each):");
        System.out.println("  Total: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println("  Per operation: " + (memoryPerOperation / 1024) + " KB");
        
        // 验证内存使用合理（每个操作不应超过100KB）
        assertTrue(memoryPerOperation < 100 * 1024, 
            "Memory usage per operation should be under 100KB");
        
        // 清理
        allChanges.clear();
    }
}