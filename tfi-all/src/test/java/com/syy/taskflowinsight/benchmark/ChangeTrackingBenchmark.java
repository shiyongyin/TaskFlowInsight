package com.syy.taskflowinsight.benchmark;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 变更追踪性能基准测试
 * 测试目标：2字段对比 P95 ≤ 200μs
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
@EnabledIfSystemProperty(named = "tfi.runPerfTests", matches = "true")
class ChangeTrackingBenchmark {
    
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int MEASUREMENT_ITERATIONS = 10000;
    private static final int PERCENTILE_95 = 95;
    
    // 测试实体
    static class BenchmarkEntity {
        private String field1 = "initial1";
        private String field2 = "initial2";
        private Integer field3 = 100;
        private Integer field4 = 200;
        private Boolean field5 = true;
        private Date field6 = new Date();
        private Double field7 = 3.14;
        private Long field8 = 999L;
        private String field9 = "test9";
        private String field10 = "test10";
        private String field11 = "test11";
        private String field12 = "test12";
        private String field13 = "test13";
        private String field14 = "test14";
        private String field15 = "test15";
        private String field16 = "test16";
        private String field17 = "test17";
        private String field18 = "test18";
        private String field19 = "test19";
        private String field20 = "test20";
        
        public void updateFields(int index) {
            field1 = "updated1_" + index;
            field2 = "updated2_" + index;
            field3 = 100 + index;
            field4 = 200 + index;
            field5 = index % 2 == 0;
            field6 = new Date(System.currentTimeMillis() + index);
            field7 = 3.14 + index;
            field8 = 999L + index;
            field9 = "test9_" + index;
            field10 = "test10_" + index;
            field11 = "test11_" + index;
            field12 = "test12_" + index;
            field13 = "test13_" + index;
            field14 = "test14_" + index;
            field15 = "test15_" + index;
            field16 = "test16_" + index;
            field17 = "test17_" + index;
            field18 = "test18_" + index;
            field19 = "test19_" + index;
            field20 = "test20_" + index;
        }
    }
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
    }
    
    @Test
    void benchmark2FieldTracking() {
        System.out.println("\n========================================");
        System.out.println("2-Field Change Tracking Benchmark");
        System.out.println("========================================");
        
        TFI.startSession("BenchmarkSession");
        TFI.start("Benchmark2Fields");
        
        // Warmup
        BenchmarkEntity warmupEntity = new BenchmarkEntity();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            TFI.track("WarmupEntity", warmupEntity, "field1", "field2");
            warmupEntity.updateFields(i);
            TFI.getChanges();
            TFI.clearAllTracking();
        }
        
        // Measurement
        List<Long> latencies = new ArrayList<>();
        BenchmarkEntity entity = new BenchmarkEntity();
        
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            
            // Track 2 fields
            TFI.track("Entity", entity, "field1", "field2");
            
            // Modify fields
            entity.updateFields(i);
            
            // Get changes (triggers diff detection)
            List<ChangeRecord> changes = TFI.getChanges();
            
            // Clear tracking
            TFI.clearAllTracking();
            
            long endTime = System.nanoTime();
            latencies.add(endTime - startTime);
        }
        
        TFI.stop();
        
        // Calculate and report metrics
        reportMetrics("2-Field Tracking", latencies);
    }
    
    @Test
    void benchmark20FieldTracking() {
        System.out.println("\n========================================");
        System.out.println("20-Field Change Tracking Benchmark");
        System.out.println("========================================");
        
        TFI.startSession("BenchmarkSession");
        TFI.start("Benchmark20Fields");
        
        // Warmup
        BenchmarkEntity warmupEntity = new BenchmarkEntity();
        String[] allFields = {
            "field1", "field2", "field3", "field4", "field5",
            "field6", "field7", "field8", "field9", "field10",
            "field11", "field12", "field13", "field14", "field15",
            "field16", "field17", "field18", "field19", "field20"
        };
        
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            TFI.track("WarmupEntity", warmupEntity, allFields);
            warmupEntity.updateFields(i);
            TFI.getChanges();
            TFI.clearAllTracking();
        }
        
        // Measurement
        List<Long> latencies = new ArrayList<>();
        BenchmarkEntity entity = new BenchmarkEntity();
        
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            
            // Track 20 fields
            TFI.track("Entity", entity, allFields);
            
            // Modify fields
            entity.updateFields(i);
            
            // Get changes (triggers diff detection)
            List<ChangeRecord> changes = TFI.getChanges();
            
            // Clear tracking
            TFI.clearAllTracking();
            
            long endTime = System.nanoTime();
            latencies.add(endTime - startTime);
        }
        
        TFI.stop();
        
        // Calculate and report metrics
        reportMetrics("20-Field Tracking", latencies);
    }
    
    @Test
    void benchmarkConcurrent8Threads() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("8-Thread Concurrent Tracking Benchmark");
        System.out.println("========================================");
        
        runConcurrentBenchmark(8);
    }
    
    @Test
    void benchmarkConcurrent16Threads() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("16-Thread Concurrent Tracking Benchmark");
        System.out.println("========================================");
        
        runConcurrentBenchmark(16);
    }
    
    private void runConcurrentBenchmark(int threadCount) throws InterruptedException {
        TFI.startSession("ConcurrentBenchmarkSession");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        List<List<Long>> allLatencies = new CopyOnWriteArrayList<>();
        AtomicLong totalOperations = new AtomicLong(0);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    TFI.start("Thread-" + threadId);
                    
                    // Warmup
                    BenchmarkEntity warmupEntity = new BenchmarkEntity();
                    for (int i = 0; i < WARMUP_ITERATIONS / threadCount; i++) {
                        TFI.track("WarmupEntity" + threadId, warmupEntity, "field1", "field2");
                        warmupEntity.updateFields(i);
                        TFI.getChanges();
                        TFI.clearAllTracking();
                    }
                    
                    // Measurement
                    List<Long> threadLatencies = new ArrayList<>();
                    BenchmarkEntity entity = new BenchmarkEntity();
                    
                    for (int i = 0; i < MEASUREMENT_ITERATIONS / threadCount; i++) {
                        long startTime = System.nanoTime();
                        
                        TFI.track("Entity" + threadId, entity, "field1", "field2");
                        entity.updateFields(i);
                        List<ChangeRecord> changes = TFI.getChanges();
                        TFI.clearAllTracking();
                        
                        long endTime = System.nanoTime();
                        threadLatencies.add(endTime - startTime);
                        totalOperations.incrementAndGet();
                    }
                    
                    allLatencies.add(threadLatencies);
                    TFI.stop();
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // Start all threads
        long testStartTime = System.currentTimeMillis();
        startLatch.countDown();
        
        // Wait for completion
        doneLatch.await(60, TimeUnit.SECONDS);
        long testDuration = System.currentTimeMillis() - testStartTime;
        
        executor.shutdown();
        
        // Aggregate all latencies
        List<Long> aggregatedLatencies = new ArrayList<>();
        allLatencies.forEach(aggregatedLatencies::addAll);
        
        // Report metrics
        reportMetrics(threadCount + "-Thread Concurrent", aggregatedLatencies);
        
        // Report throughput
        double throughput = (totalOperations.get() * 1000.0) / testDuration;
        System.out.printf("Throughput: %.0f ops/sec%n", throughput);
    }
    
    @Test
    void generateBenchmarkReport() {
        System.out.println("\n========================================");
        System.out.println("PERFORMANCE BENCHMARK REPORT");
        System.out.println("========================================");
        System.out.println("Environment:");
        System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("  JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        System.out.println("  Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("  Max Memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println();
        System.out.println("Test Configuration:");
        System.out.println("  Warmup Iterations: " + WARMUP_ITERATIONS);
        System.out.println("  Measurement Iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();
        System.out.println("Target Metrics:");
        System.out.println("  2-field P95 ≤ 200μs (建议目标)");
        System.out.println();
        
        // Run mini benchmark for report
        TFI.startSession("ReportSession");
        TFI.start("ReportBenchmark");
        
        BenchmarkEntity entity = new BenchmarkEntity();
        List<Long> quickLatencies = new ArrayList<>();
        
        // Quick benchmark (1000 iterations)
        for (int i = 0; i < 1000; i++) {
            long startTime = System.nanoTime();
            TFI.track("Entity", entity, "field1", "field2");
            entity.updateFields(i);
            TFI.getChanges();
            TFI.clearAllTracking();
            long endTime = System.nanoTime();
            quickLatencies.add(endTime - startTime);
        }
        
        TFI.stop();
        
        Collections.sort(quickLatencies);
        long p95 = quickLatencies.get((int)(quickLatencies.size() * 0.95));
        long p95Micros = p95 / 1000;
        
        System.out.println("Quick Benchmark Results:");
        System.out.println("  2-field P95: " + p95Micros + "μs");
        
        if (p95Micros <= 200) {
            System.out.println("  ✓ PASS: Meets target (≤200μs)");
        } else {
            System.out.println("  ✗ FAIL: Exceeds target (>200μs)");
            System.out.println();
            System.out.println("Improvement Plan:");
            System.out.println("  1. Optimize reflection cache hit rate");
            System.out.println("  2. Reduce string operations in diff detection");
            System.out.println("  3. Consider lazy evaluation for value representation");
            System.out.println("  4. Profile and optimize hot paths");
        }
        
        System.out.println();
        System.out.println("Note: Run individual benchmark tests for detailed metrics");
        System.out.println("========================================");
    }
    
    private void reportMetrics(String testName, List<Long> latencies) {
        Collections.sort(latencies);
        
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long median = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int)(latencies.size() * 0.95));
        long p99 = latencies.get((int)(latencies.size() * 0.99));
        
        double avg = latencies.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
        
        System.out.println("\nResults for: " + testName);
        System.out.println("----------------------------------------");
        System.out.printf("  Min:     %,d ns (%.2f μs)%n", min, min / 1000.0);
        System.out.printf("  Median:  %,d ns (%.2f μs)%n", median, median / 1000.0);
        System.out.printf("  Average: %,.0f ns (%.2f μs)%n", avg, avg / 1000.0);
        System.out.printf("  P95:     %,d ns (%.2f μs)%n", p95, p95 / 1000.0);
        System.out.printf("  P99:     %,d ns (%.2f μs)%n", p99, p99 / 1000.0);
        System.out.printf("  Max:     %,d ns (%.2f μs)%n", max, max / 1000.0);
        
        // Check against target
        if (testName.contains("2-Field")) {
            long p95Micros = p95 / 1000;
            if (p95Micros <= 200) {
                System.out.println("  ✓ Meets target: P95 ≤ 200μs");
            } else {
                System.out.println("  ✗ Exceeds target: P95 > 200μs");
            }
        }
    }
}
