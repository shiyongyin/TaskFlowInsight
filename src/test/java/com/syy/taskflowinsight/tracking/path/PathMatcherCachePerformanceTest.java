package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PathMatcherCache性能测试
 * 验证P95 < 10μs的性能要求
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("PathMatcherCache性能测试")
public class PathMatcherCachePerformanceTest {
    
    private PathMatcherCache matcher;
    
    @BeforeEach
    void setUp() {
        matcher = new PathMatcherCache();
        matcher.setCacheSize(1000);
        matcher.setPatternMaxLength(256);
        matcher.setMaxWildcards(10);
    }
    
    @Test
    @DisplayName("缓存命中性能P95<10μs")
    void testCacheHitPerformance() {
        String path = "user.profile.settings.privacy";
        String pattern = "user.**.privacy";
        
        // 预热缓存
        matcher.matches(path, pattern);
        
        // 预热JIT
        for (int i = 0; i < 10000; i++) {
            matcher.matches(path, pattern);
        }
        
        // 测试缓存命中性能
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            long start = System.nanoTime();
            matcher.matches(path, pattern);
            long duration = System.nanoTime() - start;
            durations.add(duration);
        }
        
        // 计算P95
        Collections.sort(durations);
        long p50 = durations.get(durations.size() / 2);
        long p95 = durations.get((int) (durations.size() * 0.95));
        long p99 = durations.get((int) (durations.size() * 0.99));
        
        System.out.printf("Cache hit performance (nanoseconds):%n");
        System.out.printf("  P50: %d ns (%.2f μs)%n", p50, p50 / 1000.0);
        System.out.printf("  P95: %d ns (%.2f μs)%n", p95, p95 / 1000.0);
        System.out.printf("  P99: %d ns (%.2f μs)%n", p99, p99 / 1000.0);
        
        // 验证P95 < 10μs (10000ns)
        assertThat(p95).isLessThan(10000);
    }
    
    @Test
    @DisplayName("高并发缓存性能")
    void testHighConcurrencyCachePerformance() {
        // 准备测试数据
        List<String> paths = IntStream.range(0, 100)
            .mapToObj(i -> "user.profile" + i + ".settings")
            .collect(Collectors.toList());
        
        List<String> patterns = Arrays.asList(
            "user.**",
            "*.settings",
            "user.profile*.settings",
            "**.settings"
        );
        
        // 预热
        for (String path : paths) {
            for (String pattern : patterns) {
                matcher.matches(path, pattern);
            }
        }
        
        // 测试高并发访问
        long totalOperations = 100000;
        Random random = new Random(42);
        
        long start = System.nanoTime();
        for (int i = 0; i < totalOperations; i++) {
            String path = paths.get(random.nextInt(paths.size()));
            String pattern = patterns.get(random.nextInt(patterns.size()));
            matcher.matches(path, pattern);
        }
        long totalTime = System.nanoTime() - start;
        
        double avgTimeNanos = (double) totalTime / totalOperations;
        double opsPerSecond = 1_000_000_000.0 / avgTimeNanos;
        
        System.out.printf("High concurrency performance:%n");
        System.out.printf("  Total operations: %d%n", totalOperations);
        System.out.printf("  Average time: %.2f ns (%.3f μs)%n", avgTimeNanos, avgTimeNanos / 1000);
        System.out.printf("  Throughput: %.0f ops/sec%n", opsPerSecond);
        
        // 验证平均时间 < 1μs
        assertThat(avgTimeNanos).isLessThan(1000);
        
        // 验证缓存命中率 > 85%
        PathMatcherCache.CacheStats stats = matcher.getStats();
        assertThat(stats.getHitRate()).isGreaterThan(0.85);
    }
    
    @Test
    @DisplayName("不同模式类型性能对比")
    void testDifferentPatternPerformance() {
        Map<String, String> testCases = new LinkedHashMap<>();
        testCases.put("exact", "user.profile.settings");
        testCases.put("single_wildcard", "user.*.settings");
        testCases.put("double_wildcard", "user.**");
        testCases.put("question_mark", "user?");
        testCases.put("complex", "user.*.profile.**.settings");
        
        String testPath = "user.profile.settings";
        
        System.out.println("\nPattern type performance comparison:");
        System.out.println("=====================================");
        
        for (Map.Entry<String, String> entry : testCases.entrySet()) {
            String type = entry.getKey();
            String pattern = entry.getValue();
            
            // 预热
            for (int i = 0; i < 1000; i++) {
                matcher.matches(testPath, pattern);
            }
            
            // 测试
            List<Long> durations = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                long start = System.nanoTime();
                matcher.matches(testPath, pattern);
                long duration = System.nanoTime() - start;
                durations.add(duration);
            }
            
            Collections.sort(durations);
            long p50 = durations.get(durations.size() / 2);
            long p95 = durations.get((int) (durations.size() * 0.95));
            
            System.out.printf("%-20s P50: %6.2f μs, P95: %6.2f μs%n", 
                type + ":", p50 / 1000.0, p95 / 1000.0);
            
            // 所有模式P95应该 < 20μs
            assertThat(p95).isLessThan(20000);
        }
    }
    
    @Test
    @DisplayName("缓存大小对性能的影响")
    void testCacheSizeImpact() {
        int[] cacheSizes = {10, 100, 1000, 10000};
        
        System.out.println("\nCache size impact on performance:");
        System.out.println("===================================");
        
        for (int cacheSize : cacheSizes) {
            PathMatcherCache testMatcher = new PathMatcherCache();
            testMatcher.setCacheSize(cacheSize);
            
            // 生成测试数据（2倍缓存大小）
            List<String> patterns = IntStream.range(0, cacheSize * 2)
                .mapToObj(i -> "pattern." + i + ".**")
                .collect(Collectors.toList());
            
            // 执行匹配
            long startTime = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                String pattern = patterns.get(i % patterns.size());
                testMatcher.matches("test.path.value", pattern);
            }
            long totalTime = System.nanoTime() - startTime;
            
            PathMatcherCache.CacheStats stats = testMatcher.getStats();
            double avgTime = totalTime / 10000.0 / 1000; // 转换为μs
            
            System.out.printf("Cache size: %5d, Hit rate: %.2f%%, Avg time: %.2f μs%n",
                cacheSize, stats.getHitRate() * 100, avgTime);
        }
    }
    
    @Test
    @DisplayName("批量匹配性能")
    void testBatchMatchingPerformance() {
        // 准备测试数据
        List<String> paths = IntStream.range(0, 1000)
            .mapToObj(i -> "user.dept" + (i % 10) + ".employee" + i)
            .collect(Collectors.toList());
        
        String pattern = "user.**.employee*";
        
        // 预热
        matcher.matchBatch(paths.subList(0, 10), pattern);
        
        // 测试批量匹配
        long start = System.nanoTime();
        Map<String, Boolean> results = matcher.matchBatch(paths, pattern);
        long duration = System.nanoTime() - start;
        
        double avgTimePerPath = duration / (double) paths.size() / 1000; // μs
        
        System.out.printf("\nBatch matching performance:%n");
        System.out.printf("  Paths: %d%n", paths.size());
        System.out.printf("  Total time: %.2f ms%n", duration / 1_000_000.0);
        System.out.printf("  Avg per path: %.2f μs%n", avgTimePerPath);
        
        assertThat(results).hasSize(1000);
        assertThat(avgTimePerPath).isLessThan(10); // 平均每个路径<10μs
    }
    
    @Test
    @DisplayName("内存占用测试")
    void testMemoryFootprint() {
        // 记录初始内存
        System.gc();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // 创建大量缓存项
        for (int i = 0; i < 1000; i++) {
            String pattern = "user.dept" + i + ".**.settings";
            for (int j = 0; j < 10; j++) {
                String path = "user.dept" + i + ".team" + j + ".member.settings";
                matcher.matches(path, pattern);
            }
        }
        
        // 记录缓存后内存
        System.gc();
        long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        PathMatcherCache.CacheStats stats = matcher.getStats();
        
        System.out.printf("\nMemory footprint:%n");
        System.out.printf("  Pattern cache size: %d%n", stats.getPatternCacheSize());
        System.out.printf("  Result cache size: %d%n", stats.getResultCacheSize());
        System.out.printf("  Memory used: %.2f KB%n", memoryUsed / 1024.0);
        System.out.printf("  Avg per entry: %.2f bytes%n", 
            (double) memoryUsed / (stats.getPatternCacheSize() + stats.getResultCacheSize()));
        
        // 验证内存使用合理（<2MB for 1000 patterns + 5000 results）
        assertThat(memoryUsed).isLessThan(2 * 1024 * 1024);
    }
    
    @Test
    @DisplayName("缓存淘汰策略性能")
    void testCacheEvictionPerformance() {
        matcher.setCacheSize(100); // 小缓存
        
        // 访问超过缓存大小的模式
        List<Long> durations = new ArrayList<>();
        
        for (int round = 0; round < 10; round++) {
            for (int i = 0; i < 200; i++) {
                String pattern = "pattern" + i;
                String path = "test.path";
                
                long start = System.nanoTime();
                matcher.matches(path, pattern);
                long duration = System.nanoTime() - start;
                
                if (round > 5) { // 跳过预热轮次
                    durations.add(duration);
                }
            }
        }
        
        Collections.sort(durations);
        long p50 = durations.get(durations.size() / 2);
        long p95 = durations.get((int) (durations.size() * 0.95));
        
        System.out.printf("\nCache eviction performance:%n");
        System.out.printf("  P50: %.2f μs%n", p50 / 1000.0);
        System.out.printf("  P95: %.2f μs%n", p95 / 1000.0);
        
        PathMatcherCache.CacheStats stats = matcher.getStats();
        System.out.printf("  Final cache hit rate: %.2f%%%n", stats.getHitRate() * 100);
        
        // 即使有缓存淘汰，P95也应该<50μs
        assertThat(p95).isLessThan(50000);
    }
    
    @Test
    @DisplayName("实际场景性能测试")
    void testRealWorldScenarios() {
        // 模拟实际使用场景
        List<String> commonPatterns = Arrays.asList(
            "*.password",
            "*.secret",
            "**.token",
            "**.apiKey",
            "user.**.personal",
            "admin.**",
            "api.v?.users.*",
            "logs.*.debug",
            "metrics.**.count",
            "config.**.enabled"
        );
        
        // 预加载常用模式
        matcher.preload(commonPatterns);
        
        // 生成实际路径
        List<String> realPaths = new ArrayList<>();
        realPaths.add("user.john.password");
        realPaths.add("admin.settings.apiKey");
        realPaths.add("api.v1.users.123");
        realPaths.add("logs.application.debug");
        realPaths.add("metrics.http.requests.count");
        realPaths.add("config.cache.redis.enabled");
        realPaths.add("user.profile.personal");
        realPaths.add("system.secret");
        
        // 性能测试
        long iterations = 100000;
        Random random = new Random(42);
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String path = realPaths.get(random.nextInt(realPaths.size()));
            String pattern = commonPatterns.get(random.nextInt(commonPatterns.size()));
            matcher.matches(path, pattern);
        }
        long totalTime = System.nanoTime() - startTime;
        
        double avgTimeNanos = (double) totalTime / iterations;
        double opsPerSecond = 1_000_000_000.0 / avgTimeNanos;
        
        PathMatcherCache.CacheStats stats = matcher.getStats();
        
        System.out.printf("\nReal-world scenario performance:%n");
        System.out.printf("  Operations: %d%n", iterations);
        System.out.printf("  Avg time: %.2f ns (%.3f μs)%n", avgTimeNanos, avgTimeNanos / 1000);
        System.out.printf("  Throughput: %.0f ops/sec%n", opsPerSecond);
        System.out.printf("  Cache hit rate: %.2f%%%n", stats.getHitRate() * 100);
        
        // 实际场景平均时间应该<1μs
        assertThat(avgTimeNanos).isLessThan(1000);
        // 缓存命中率应该>90%
        assertThat(stats.getHitRate()).isGreaterThan(0.90);
    }
}