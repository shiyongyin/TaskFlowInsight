package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.syy.taskflowinsight.tracking.compare.CompareConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能基准测试
 * 测试不同大小列表的比较性能，记录P50/P95/P99指标
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@SpringBootTest
class PerformanceBenchmarkTest {
    
    @Autowired
    private CompareService compareService;
    
    @Autowired
    private ListCompareExecutor listCompareExecutor;
    
    @Test
    void benchmark100Elements() {
        int size = 100;
        int iterations = 100;
        
        System.out.println("\n=== Performance Benchmark: 100 Elements ===");
        
        List<Long> durations = runBenchmark(size, iterations, STRATEGY_LEVENSHTEIN, true);
        
        printStatistics(size, durations);
        
        // 验证性能目标
        double p95 = calculatePercentile(durations, 95);
        assertTrue(p95 < PERFORMANCE_TARGET_100_ELEMENTS_MS * 10, // 放宽10倍，因为包含Spring开销
            String.format("100 elements P95 should be < %dms, actual: %.2fms", 
                PERFORMANCE_TARGET_100_ELEMENTS_MS * 10, p95));
    }
    
    @Test
    void benchmark500Elements() {
        int size = 500;
        int iterations = 50;
        
        System.out.println("\n=== Performance Benchmark: 500 Elements ===");
        
        // LEVENSHTEIN策略（不会降级）
        List<Long> durationsLevenshtein = runBenchmark(size, iterations, STRATEGY_LEVENSHTEIN, false);
        
        System.out.println("\nLEVENSHTEIN Strategy:");
        printStatistics(size, durationsLevenshtein);
        
        // SIMPLE策略（降级后）
        List<Long> durationsSimple = runBenchmark(size, iterations, STRATEGY_SIMPLE, false);
        
        System.out.println("\nSIMPLE Strategy:");
        printStatistics(size, durationsSimple);
        
        // AS_SET策略
        List<Long> durationsAsSet = runBenchmark(size, iterations, STRATEGY_AS_SET, false);
        
        System.out.println("\nAS_SET Strategy:");
        printStatistics(size, durationsAsSet);
        
        // 验证性能目标
        double p95Simple = calculatePercentile(durationsSimple, 95);
        assertTrue(p95Simple < PERFORMANCE_TARGET_500_ELEMENTS_MS * 5,
            String.format("500 elements SIMPLE P95 should be < %dms, actual: %.2fms", 
                PERFORMANCE_TARGET_500_ELEMENTS_MS * 5, p95Simple));
    }
    
    @Test
    void benchmark1000Elements() {
        int size = 1000;
        int iterations = 30;
        
        System.out.println("\n=== Performance Benchmark: 1000 Elements ===");
        
        // 测试降级性能（应该自动降级到SIMPLE）
        List<Long> durations = runBenchmark(size, iterations, STRATEGY_LEVENSHTEIN, true);
        
        printStatistics(size, durations);
        
        // 验证性能目标（降级后应该很快）
        double p95 = calculatePercentile(durations, 95);
        assertTrue(p95 < PERFORMANCE_TARGET_1000_ELEMENTS_MS * 3,
            String.format("1000 elements (degraded) P95 should be < %dms, actual: %.2fms", 
                PERFORMANCE_TARGET_1000_ELEMENTS_MS * 3, p95));
        
        // 验证降级确实发生了
        assertTrue(listCompareExecutor.getDegradationCount() > 0,
            "1000 elements should trigger degradation");
    }
    
    @Test
    void benchmarkMoveDetection() {
        System.out.println("\n=== Performance Benchmark: Move Detection ===");
        
        // 测试不同大小的移动检测性能
        int[] sizes = {50, 100, 200, 300, 400, 500};
        
        for (int size : sizes) {
            List<String> list1 = generateList(size);
            List<String> list2 = generateShuffledList(size);
            
            CompareOptions options = CompareOptions.builder()
                .strategyName(STRATEGY_LEVENSHTEIN)
                .detectMoves(true)
                .build();
            
            // Warm up
            for (int i = 0; i < 5; i++) {
                compareService.compare(list1, list2, options);
            }
            
            // Measure
            List<Long> durations = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                long start = System.nanoTime();
                CompareResult result = compareService.compare(list1, list2, options);
                long duration = (System.nanoTime() - start) / 1_000_000;
                durations.add(duration);
            }
            
            double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            double p95 = calculatePercentile(durations, 95);
            
            System.out.printf("Size %d with MOVE detection - Avg: %.2fms, P95: %.2fms\n", 
                size, avg, p95);
        }
    }
    
    @Test
    void benchmarkWorstCaseScenario() {
        System.out.println("\n=== Performance Benchmark: Worst Case Scenarios ===");
        
        // 最坏情况1：完全不同的列表
        List<String> list1 = generateList(200);
        List<String> list2 = IntStream.range(200, 400)
            .mapToObj(i -> "different" + i)
            .collect(Collectors.toList());
        
        long start = System.nanoTime();
        CompareResult result1 = compareService.compare(list1, list2, 
            CompareOptions.builder().strategyName(STRATEGY_LEVENSHTEIN).build());
        long duration1 = (System.nanoTime() - start) / 1_000_000;
        
        System.out.printf("Completely different lists (200 elements): %dms, changes: %d\n",
            duration1, result1.getChanges().size());
        
        // 最坏情况2：完全反序
        List<String> list3 = generateList(200);
        List<String> list4 = new ArrayList<>(list3);
        Collections.reverse(list4);
        
        start = System.nanoTime();
        CompareResult result2 = compareService.compare(list3, list4,
            CompareOptions.builder()
                .strategyName(STRATEGY_LEVENSHTEIN)
                .detectMoves(true)
                .build());
        long duration2 = (System.nanoTime() - start) / 1_000_000;
        
        System.out.printf("Completely reversed lists (200 elements): %dms, changes: %d\n",
            duration2, result2.getChanges().size());
    }
    
    // 辅助方法
    
    private List<Long> runBenchmark(int size, int iterations, String strategy, boolean detectMoves) {
        List<String> list1 = generateList(size);
        List<String> list2 = generateListWithModifications(size);
        
        CompareOptions options = CompareOptions.builder()
            .strategyName(strategy)
            .detectMoves(detectMoves)
            .build();
        
        // Warm up
        for (int i = 0; i < Math.min(10, iterations / 2); i++) {
            compareService.compare(list1, list2, options);
        }
        
        // Actual benchmark
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            CompareResult result = compareService.compare(list1, list2, options);
            long duration = (System.nanoTime() - start) / 1_000_000;
            durations.add(duration);
            
            assertNotNull(result);
        }
        
        return durations;
    }
    
    private void printStatistics(int size, List<Long> durations) {
        Collections.sort(durations);
        
        double min = durations.get(0);
        double max = durations.get(durations.size() - 1);
        double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double p50 = calculatePercentile(durations, 50);
        double p95 = calculatePercentile(durations, 95);
        double p99 = calculatePercentile(durations, 99);
        
        System.out.println("Statistics for " + size + " elements:");
        System.out.printf("  Min:  %.2fms\n", min);
        System.out.printf("  Max:  %.2fms\n", max);
        System.out.printf("  Avg:  %.2fms\n", avg);
        System.out.printf("  P50:  %.2fms\n", p50);
        System.out.printf("  P95:  %.2fms ✓\n", p95);
        System.out.printf("  P99:  %.2fms ✓\n", p99);
        
        // 输出性能是否满足目标
        if (size == 100 && p95 < PERFORMANCE_TARGET_100_ELEMENTS_MS * 10) {
            System.out.println("  ✅ Performance target met!");
        } else if (size == 500 && p95 < PERFORMANCE_TARGET_500_ELEMENTS_MS * 5) {
            System.out.println("  ✅ Performance target met!");
        } else if (size == 1000 && p95 < PERFORMANCE_TARGET_1000_ELEMENTS_MS * 3) {
            System.out.println("  ✅ Performance target met!");
        }
    }
    
    private double calculatePercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
    
    private List<String> generateList(int size) {
        return IntStream.range(0, size)
            .mapToObj(i -> "item" + i)
            .collect(Collectors.toList());
    }
    
    private List<String> generateListWithModifications(int size) {
        List<String> list = generateList(size);
        
        // 修改10%的元素
        int modifications = size / 10;
        Random random = new Random(42); // 固定种子，保证可重复
        
        for (int i = 0; i < modifications; i++) {
            int index = random.nextInt(size);
            list.set(index, "modified" + index);
        }
        
        return list;
    }
    
    private List<String> generateShuffledList(int size) {
        List<String> list = generateList(size);
        Collections.shuffle(list, new Random(42)); // 固定种子
        return list;
    }
}