package com.syy.taskflowinsight.tracking.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CollectionSummary性能测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("CollectionSummary性能测试")
public class CollectionSummaryPerformanceTest {
    
    private CollectionSummary summary;
    
    @BeforeEach
    void setUp() {
        summary = new CollectionSummary();
    }
    
    @Test
    @DisplayName("大集合(1000+元素)性能测试")
    void testLargeCollectionPerformance() {
        // 准备测试数据
        List<Integer> largeList = new ArrayList<>(10000);
        for (int i = 0; i < 10000; i++) {
            largeList.add(i);
        }
        
        // 预热
        for (int i = 0; i < 100; i++) {
            summary.summarize(largeList);
        }
        
        // 性能测试
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            long start = System.nanoTime();
            SummaryInfo info = summary.summarize(largeList);
            long duration = System.nanoTime() - start;
            durations.add(duration / 1000); // 转换为微秒
            
            assertThat(info.getSize()).isEqualTo(10000);
        }
        
        // 计算P95
        Collections.sort(durations);
        long p95 = durations.get((int) (durations.size() * 0.95));
        long p50 = durations.get(durations.size() / 2);
        long p99 = durations.get((int) (durations.size() * 0.99));
        
        System.out.printf("Large collection (10000 elements) performance:%n");
        System.out.printf("  P50: %d μs%n", p50);
        System.out.printf("  P95: %d μs%n", p95);
        System.out.printf("  P99: %d μs%n", p99);
        
        // 验证P95 < 500μs
        assertThat(p95).isLessThan(500);
    }
    
    @Test
    @DisplayName("超大集合(100K+元素)降级性能")
    void testHugeCollectionDegradation() {
        // 准备100K元素
        List<String> hugeList = new ArrayList<>(100000);
        for (int i = 0; i < 100000; i++) {
            hugeList.add("item-" + i);
        }
        
        long start = System.currentTimeMillis();
        SummaryInfo info = summary.summarize(hugeList);
        long duration = System.currentTimeMillis() - start;
        
        System.out.printf("Huge collection (100K elements) summarized in %d ms%n", duration);
        
        assertThat(duration).isLessThan(100); // 应该在100ms内完成
        assertThat(info.isTruncated()).isTrue(); // 应该被截断
        assertThat(info.getSize()).isEqualTo(100000);
    }
    
    @Test
    @DisplayName("Map性能测试")
    void testMapPerformance() {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < 5000; i++) {
            map.put("key" + i, i);
        }
        
        // 预热
        for (int i = 0; i < 50; i++) {
            summary.summarize(map);
        }
        
        // 测试
        long totalTime = 0;
        int iterations = 500;
        
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            summary.summarize(map);
            totalTime += (System.nanoTime() - start);
        }
        
        long avgMicros = (totalTime / 1000) / iterations;
        System.out.printf("Map (5000 entries) average summary time: %d μs%n", avgMicros);
        
        assertThat(avgMicros).isLessThan(500);
    }
    
    @Test
    @DisplayName("并发集合性能")
    void testConcurrentCollectionPerformance() {
        ConcurrentHashMap<String, Integer> concurrentMap = new ConcurrentHashMap<>();
        for (int i = 0; i < 2000; i++) {
            concurrentMap.put("key" + i, i);
        }
        
        List<Long> durations = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            SummaryInfo info = summary.summarize(concurrentMap);
            long duration = System.nanoTime() - start;
            durations.add(duration / 1000);
            
            assertThat(info.getSize()).isEqualTo(2000);
        }
        
        long avg = durations.stream().mapToLong(Long::longValue).sum() / durations.size();
        System.out.printf("ConcurrentHashMap (2000 entries) average: %d μs%n", avg);
        
        assertThat(avg).isLessThan(300);
    }
    
    @Test
    @DisplayName("数组性能测试")
    void testArrayPerformance() {
        int[] array = IntStream.range(0, 10000).toArray();
        
        // 预热
        for (int i = 0; i < 100; i++) {
            summary.summarize(array);
        }
        
        // 测试
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            long start = System.nanoTime();
            SummaryInfo info = summary.summarize(array);
            durations.add((System.nanoTime() - start) / 1000);
            
            assertThat(info.getStatistics()).isNotNull();
        }
        
        Collections.sort(durations);
        long p95 = durations.get((int) (durations.size() * 0.95));
        
        System.out.printf("Array (10000 elements) P95: %d μs%n", p95);
        assertThat(p95).isLessThan(500);
    }
    
    @Test
    @DisplayName("混合类型集合性能")
    void testMixedTypePerformance() {
        List<Object> mixedList = new ArrayList<>(5000);
        for (int i = 0; i < 5000; i++) {
            switch (i % 4) {
                case 0:
                    mixedList.add("string" + i);
                    break;
                case 1:
                    mixedList.add(i);
                    break;
                case 2:
                    mixedList.add(i * 1.5);
                    break;
                case 3:
                    mixedList.add(i % 2 == 0);
                    break;
            }
        }
        
        long start = System.nanoTime();
        SummaryInfo info = summary.summarize(mixedList);
        long duration = (System.nanoTime() - start) / 1000;
        
        System.out.printf("Mixed type collection (5000 elements): %d μs%n", duration);
        
        assertThat(info.getTypeDistribution()).hasSize(4);
        assertThat(duration).isLessThan(1000);
    }
    
    @Test
    @DisplayName("内存使用测试")
    void testMemoryUsage() {
        // 创建大集合
        List<String> largeList = new ArrayList<>(50000);
        for (int i = 0; i < 50000; i++) {
            largeList.add("This is a relatively long string for testing memory usage - " + i);
        }
        
        // 记录初始内存
        System.gc();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // 生成摘要
        SummaryInfo info = summary.summarize(largeList);
        
        // 记录摘要后内存
        long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        System.out.printf("Memory used for 50K string summary: %d KB%n", memoryUsed / 1024);
        
        // 验证摘要信息正确
        assertThat(info.getSize()).isEqualTo(50000);
        assertThat(info.isTruncated()).isTrue();
        
        // 验证内存使用合理（摘要应该远小于原集合）
        // 摘要不应该使用超过5MB内存（考虑JVM内存波动）
        assertThat(memoryUsed).isLessThan(5 * 1024 * 1024);
    }
    
    @Test
    @DisplayName("嵌套集合性能")
    void testNestedCollectionPerformance() {
        // 创建嵌套结构
        Map<String, List<Integer>> nestedMap = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            List<Integer> innerList = new ArrayList<>();
            for (int j = 0; j < 100; j++) {
                innerList.add(i * 100 + j);
            }
            nestedMap.put("key" + i, innerList);
        }
        
        long start = System.nanoTime();
        SummaryInfo info = summary.summarize(nestedMap);
        long duration = (System.nanoTime() - start) / 1000;
        
        System.out.printf("Nested collection (100x100): %d μs%n", duration);
        
        assertThat(info.getSize()).isEqualTo(100);
        assertThat(duration).isLessThan(1000);
    }
}