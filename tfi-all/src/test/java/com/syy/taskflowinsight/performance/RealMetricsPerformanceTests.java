package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests with real metrics collection and analysis
 * 
 * These tests measure actual performance characteristics:
 * - Throughput (operations per second)
 * - Latency (response times)
 * - Memory usage (heap consumption)
 * - CPU utilization
 * - Scalability under load
 * - Resource leak detection
 * 
 * Enable with: -Dtfi.perf.enabled=true
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "tfi.perf.enabled", matches = "true")
@DisplayName("Real Metrics Performance Tests")
class RealMetricsPerformanceTests {

    private PerformanceMetrics metrics;
    private MemoryMXBean memoryBean;

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
        
        metrics = new PerformanceMetrics();
        memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Warm up JVM
        warmUpJvm();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
        
        // Force garbage collection for clean metrics
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    @DisplayName("Throughput Performance")
    class ThroughputTests {

        @Test
        @DisplayName("Session creation throughput under load")
        void sessionCreationThroughputUnderLoad() {
            final int ITERATIONS = 10000;
            final int WARMUP_ITERATIONS = 1000;
            
            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                String sessionId = TFI.startSession("Warmup Session " + i);
                TFI.endSession();
            }
            
            // Measure throughput
            long startTime = System.nanoTime();
            MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
            
            for (int i = 0; i < ITERATIONS; i++) {
                String sessionId = TFI.startSession("Performance Test Session " + i);
                assertThat(sessionId).isNotNull();
                TFI.endSession();
            }
            
            long endTime = System.nanoTime();
            MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
            
            // Calculate metrics
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = ITERATIONS / durationSeconds;
            long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
            
            metrics.recordThroughput("sessionCreation", throughput);
            metrics.recordMemoryUsage("sessionCreation", memoryUsed);
            
            // Performance assertions
            assertThat(throughput).isGreaterThan(1000); // At least 1K sessions/sec
            assertThat(memoryUsed).isLessThan(100 * 1024 * 1024); // Less than 100MB
            
            System.out.printf("Session Creation Throughput: %.2f sessions/sec, Memory: %d bytes%n", 
                throughput, memoryUsed);
        }

        @Test
        @DisplayName("Task execution throughput with nested operations")
        void taskExecutionThroughputWithNestedOperations() {
            final int ITERATIONS = 5000;
            
            TFI.startSession("Throughput Test Session");
            
            long startTime = System.nanoTime();
            MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
            
            for (int i = 0; i < ITERATIONS; i++) {
                try (TaskContext main = TFI.start("Main Task " + i)) {
                    main.message("Processing item " + i);
                    main.attribute("itemId", i);
                    
                    try (TaskContext sub1 = main.subtask("Subtask 1")) {
                        sub1.message("Validating item");
                        sub1.success();
                    }
                    
                    try (TaskContext sub2 = main.subtask("Subtask 2")) {
                        sub2.message("Processing item");
                        sub2.attribute("processed", true);
                        sub2.success();
                    }
                    
                    main.success();
                }
            }
            
            long endTime = System.nanoTime();
            MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
            
            // Calculate metrics
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = ITERATIONS / durationSeconds;
            long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
            
            metrics.recordThroughput("nestedTaskExecution", throughput);
            metrics.recordMemoryUsage("nestedTaskExecution", memoryUsed);
            
            // Performance assertions
            assertThat(throughput).isGreaterThan(500); // At least 500 complex tasks/sec
            assertThat(memoryUsed).isLessThan(200 * 1024 * 1024); // Less than 200MB
            
            TFI.endSession();
            
            System.out.printf("Nested Task Execution Throughput: %.2f tasks/sec, Memory: %d bytes%n", 
                throughput, memoryUsed);
        }

        @Test
        @DisplayName("Change tracking throughput with object mutations")
        void changeTrackingThroughputWithObjectMutations() {
            final int ITERATIONS = 2000;
            final int OBJECTS_PER_ITERATION = 10;
            
            TFI.startSession("Change Tracking Throughput Test");
            
            long startTime = System.nanoTime();
            MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
            
            for (int i = 0; i < ITERATIONS; i++) {
                Map<String, Object> objects = new HashMap<>();
                
                // Create objects to track
                for (int j = 0; j < OBJECTS_PER_ITERATION; j++) {
                    TestObject obj = new TestObject("Object-" + i + "-" + j, i * j);
                    objects.put("obj" + j, obj);
                }
                
                // Track objects
                TFI.trackAll(objects);
                
                // Mutate objects
                for (Object obj : objects.values()) {
                    if (obj instanceof TestObject) {
                        TestObject testObj = (TestObject) obj;
                        testObj.setValue(testObj.getValue() + 1);
                        testObj.setName(testObj.getName() + "-updated");
                    }
                }
                
                // Get changes
                List<ChangeRecord> changes = TFI.getChanges();
                assertThat(changes).isNotEmpty();
            }
            
            long endTime = System.nanoTime();
            MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
            
            // Calculate metrics
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = (ITERATIONS * OBJECTS_PER_ITERATION) / durationSeconds;
            long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
            
            metrics.recordThroughput("changeTracking", throughput);
            metrics.recordMemoryUsage("changeTracking", memoryUsed);
            
            // Performance assertions
            assertThat(throughput).isGreaterThan(1000); // At least 1K tracked objects/sec
            
            TFI.endSession();
            
            System.out.printf("Change Tracking Throughput: %.2f tracked objects/sec, Memory: %d bytes%n", 
                throughput, memoryUsed);
        }
    }

    @Nested
    @DisplayName("Latency Performance")
    class LatencyTests {

        @Test
        @DisplayName("Single operation latency distribution")
        void singleOperationLatencyDistribution() {
            final int SAMPLES = 10000;
            List<Long> latencies = new ArrayList<>(SAMPLES);
            
            TFI.startSession("Latency Test Session");
            
            // Collect latency samples
            for (int i = 0; i < SAMPLES; i++) {
                long startTime = System.nanoTime();
                
                try (TaskContext task = TFI.start("Latency Test Task " + i)) {
                    task.message("Test operation");
                    task.attribute("sampleId", i);
                    task.success();
                }
                
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }
            
            // Calculate statistics
            latencies.sort(Long::compareTo);
            long p50 = latencies.get(SAMPLES / 2);
            long p95 = latencies.get((int) (SAMPLES * 0.95));
            long p99 = latencies.get((int) (SAMPLES * 0.99));
            long min = latencies.get(0);
            long max = latencies.get(SAMPLES - 1);
            double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            
            metrics.recordLatency("singleOperation", p50, p95, p99);
            
            // Performance assertions (in microseconds)
            assertThat(p50 / 1000).isLessThan(100); // P50 < 100μs
            assertThat(p95 / 1000).isLessThan(500); // P95 < 500μs
            assertThat(p99 / 1000).isLessThan(1000); // P99 < 1ms
            
            TFI.endSession();
            
            System.out.printf("Single Operation Latency - P50: %dμs, P95: %dμs, P99: %dμs, Avg: %.2fμs%n",
                p50 / 1000, p95 / 1000, p99 / 1000, avg / 1000);
        }

        @Test
        @DisplayName("Export operation latency under different data sizes")
        void exportOperationLatencyUnderDifferentDataSizes() {
            final int[] DATA_SIZES = {10, 100, 1000, 5000};
            
            for (int dataSize : DATA_SIZES) {
                TFI.startSession("Export Latency Test - Size " + dataSize);
                
                // Create data
                for (int i = 0; i < dataSize; i++) {
                    try (TaskContext task = TFI.start("Data Task " + i)) {
                        task.message("Processing data item " + i);
                        task.attribute("itemId", i);
                        task.attribute("dataSize", dataSize);
                        task.success();
                    }
                }
                
                // Measure export latency
                long startTime = System.nanoTime();
                String jsonExport = TFI.exportToJson();
                long jsonLatency = System.nanoTime() - startTime;
                
                startTime = System.nanoTime();
                Map<String, Object> mapExport = TFI.exportToMap();
                long mapLatency = System.nanoTime() - startTime;
                
                assertThat(jsonExport).isNotNull();
                assertThat(mapExport).isNotEmpty();
                
                metrics.recordExportLatency("json", dataSize, jsonLatency);
                metrics.recordExportLatency("map", dataSize, mapLatency);
                
                // Latency should be reasonable even for large datasets
                assertThat(jsonLatency / 1_000_000).isLessThan(dataSize); // < 1ms per task for JSON
                assertThat(mapLatency / 1_000_000).isLessThan(dataSize / 2); // < 0.5ms per task for Map
                
                TFI.endSession();
                
                System.out.printf("Export Latency (size=%d) - JSON: %dms, Map: %dms%n",
                    dataSize, jsonLatency / 1_000_000, mapLatency / 1_000_000);
            }
        }
    }

    @Nested
    @DisplayName("Scalability Performance")
    class ScalabilityTests {

        @Test
        @DisplayName("Concurrent session scalability")
        void concurrentSessionScalability() throws InterruptedException {
            final int THREAD_COUNT = 20;
            final int OPERATIONS_PER_THREAD = 500;
            
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            AtomicLong totalOperations = new AtomicLong(0);
            AtomicLong totalLatency = new AtomicLong(0);
            
            long startTime = System.nanoTime();
            MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
            
            // Submit concurrent tasks
            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        long threadStartTime = System.nanoTime();
                        
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            String sessionId = TFI.startSession("Concurrent Session " + threadId + "-" + i);
                            
                            try (TaskContext task = TFI.start("Concurrent Task " + i)) {
                                task.message("Processing in thread " + threadId);
                                task.attribute("threadId", threadId);
                                task.attribute("operationId", i);
                                task.success();
                            }
                            
                            TFI.endSession();
                            totalOperations.incrementAndGet();
                        }
                        
                        long threadLatency = System.nanoTime() - threadStartTime;
                        totalLatency.addAndGet(threadLatency);
                        
                    } catch (Exception e) {
                        System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            
            long endTime = System.nanoTime();
            MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
            
            // Calculate metrics
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = totalOperations.get() / durationSeconds;
            double avgLatencyPerThread = (totalLatency.get() / THREAD_COUNT) / 1_000_000.0; // ms
            long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
            
            metrics.recordConcurrentThroughput(THREAD_COUNT, throughput);
            metrics.recordMemoryUsage("concurrent", memoryUsed);
            
            // Scalability assertions
            assertThat(throughput).isGreaterThan(1000); // At least 1K ops/sec under concurrency
            assertThat(avgLatencyPerThread).isLessThan(10000); // Avg thread latency < 10s
            assertThat(memoryUsed).isLessThan(500 * 1024 * 1024); // Less than 500MB
            
            System.out.printf("Concurrent Scalability (%d threads) - Throughput: %.2f ops/sec, " +
                "Avg Thread Latency: %.2fms, Memory: %d bytes%n",
                THREAD_COUNT, throughput, avgLatencyPerThread, memoryUsed);
        }

        @Test
        @DisplayName("Deep nesting scalability")
        void deepNestingScalability() {
            final int[] NESTING_DEPTHS = {5, 10, 20, 50};
            
            for (int depth : NESTING_DEPTHS) {
                TFI.startSession("Deep Nesting Test - Depth " + depth);
                
                long startTime = System.nanoTime();
                MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
                
                createNestedTasks(depth, 0);
                
                long endTime = System.nanoTime();
                MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
                
                long latency = endTime - startTime;
                long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
                
                metrics.recordNestingPerformance(depth, latency, memoryUsed);
                
                // Nesting should scale reasonably
                assertThat(latency / 1_000_000).isLessThan(depth * 10); // < 10ms per level
                assertThat(memoryUsed).isLessThan(depth * 1024 * 1024); // < 1MB per level
                
                TFI.endSession();
                
                System.out.printf("Deep Nesting (depth=%d) - Latency: %dms, Memory: %d bytes%n",
                    depth, latency / 1_000_000, memoryUsed);
            }
        }

        private void createNestedTasks(int remainingDepth, int currentLevel) {
            if (remainingDepth <= 0) return;
            
            try (TaskContext task = TFI.start("Nested Task Level " + currentLevel)) {
                task.message("Processing at level " + currentLevel);
                task.attribute("level", currentLevel);
                task.attribute("remainingDepth", remainingDepth);
                
                createNestedTasks(remainingDepth - 1, currentLevel + 1);
                
                task.success();
            }
        }
    }

    @Nested
    @DisplayName("Memory Performance")
    class MemoryTests {

        @Test
        @DisplayName("Memory leak detection under sustained load")
        void memoryLeakDetectionUnderSustainedLoad() {
            final int CYCLES = 10;
            final int OPERATIONS_PER_CYCLE = 1000;
            List<Long> memoryMeasurements = new ArrayList<>();
            
            // Baseline measurement
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            long baselineMemory = memoryBean.getHeapMemoryUsage().getUsed();
            
            for (int cycle = 0; cycle < CYCLES; cycle++) {
                // Perform operations
                for (int i = 0; i < OPERATIONS_PER_CYCLE; i++) {
                    TFI.startSession("Memory Test Session " + cycle + "-" + i);
                    
                    try (TaskContext task = TFI.start("Memory Test Task " + i)) {
                        task.message("Processing item " + i);
                        
                        // Create some tracked objects
                        TestObject obj = new TestObject("Object-" + i, i);
                        TFI.track("testObj" + i, obj, "name", "value");
                        obj.setValue(obj.getValue() + 1);
                        
                        List<ChangeRecord> changes = TFI.getChanges();
                        task.attribute("changeCount", changes.size());
                        task.success();
                    }
                    
                    TFI.endSession();
                }
                
                // Force GC and measure memory
                System.gc();
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                
                long currentMemory = memoryBean.getHeapMemoryUsage().getUsed();
                memoryMeasurements.add(currentMemory);
                
                System.out.printf("Cycle %d: Memory usage = %d bytes (delta from baseline: %+d bytes)%n",
                    cycle, currentMemory, currentMemory - baselineMemory);
            }
            
            // Analyze memory trend
            long finalMemory = memoryMeasurements.get(memoryMeasurements.size() - 1);
            long memoryGrowth = finalMemory - baselineMemory;
            double avgGrowthPerCycle = memoryGrowth / (double) CYCLES;
            
            metrics.recordMemoryGrowth(memoryGrowth, avgGrowthPerCycle);
            
            // Memory leak assertions
            assertThat(memoryGrowth).isLessThan(100 * 1024 * 1024); // Less than 100MB total growth
            assertThat(avgGrowthPerCycle).isLessThan(10 * 1024 * 1024); // Less than 10MB per cycle
            
            System.out.printf("Memory Leak Analysis - Total Growth: %d bytes, Avg per cycle: %.2f bytes%n",
                memoryGrowth, avgGrowthPerCycle);
        }

        @Test
        @DisplayName("Large object tracking memory efficiency")
        void largeObjectTrackingMemoryEfficiency() {
            final int[] OBJECT_COUNTS = {100, 500, 1000, 2000};
            
            for (int objectCount : OBJECT_COUNTS) {
                System.gc();
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
                
                TFI.startSession("Large Object Tracking - Count " + objectCount);
                
                // Create and track large number of objects
                Map<String, Object> objects = new HashMap<>();
                for (int i = 0; i < objectCount; i++) {
                    LargeTestObject obj = new LargeTestObject("LargeObject-" + i);
                    objects.put("large" + i, obj);
                }
                
                long trackingStartTime = System.nanoTime();
                TFI.trackAll(objects);
                long trackingEndTime = System.nanoTime();
                
                // Mutate objects
                for (Object obj : objects.values()) {
                    if (obj instanceof LargeTestObject) {
                        ((LargeTestObject) obj).updateData("updated-" + System.currentTimeMillis());
                    }
                }
                
                // Get changes
                List<ChangeRecord> changes = TFI.getChanges();
                
                TFI.endSession();
                
                MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
                long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
                long trackingLatency = trackingEndTime - trackingStartTime;
                
                metrics.recordLargeObjectTracking(objectCount, memoryUsed, trackingLatency);
                
                // Memory efficiency assertions
                long memoryPerObject = memoryUsed / objectCount;
                assertThat(memoryPerObject).isLessThan(10 * 1024); // Less than 10KB per object overhead
                assertThat(trackingLatency / 1_000_000).isLessThan(objectCount); // < 1ms per object
                
                System.out.printf("Large Object Tracking (%d objects) - Memory: %d bytes (%d bytes/obj), " +
                    "Tracking Latency: %dms%n",
                    objectCount, memoryUsed, memoryPerObject, trackingLatency / 1_000_000);
            }
        }
    }

    @Nested
    @DisplayName("Resource Management")
    class ResourceManagementTests {

        @Test
        @DisplayName("Thread context cleanup efficiency")
        void threadContextCleanupEfficiency() {
            final int ITERATIONS = 1000;
            
            // Measure active contexts before
            ThreadContext.ContextStatistics beforeStats = ThreadContext.getStatistics();
            
            for (int i = 0; i < ITERATIONS; i++) {
                TFI.startSession("Cleanup Test Session " + i);
                
                try (TaskContext task = TFI.start("Cleanup Test Task " + i)) {
                    task.message("Testing cleanup");
                    task.success();
                }
                
                TFI.endSession();
                TFI.clear(); // Explicit cleanup
            }
            
            // Force cleanup and measure
            System.gc();
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            
            ThreadContext.ContextStatistics afterStats = ThreadContext.getStatistics();
            
            // Calculate cleanup efficiency
            int contextLeak = afterStats.activeContexts - beforeStats.activeContexts;
            double cleanupEfficiency = 1.0 - (contextLeak / (double) ITERATIONS);
            
            metrics.recordCleanupEfficiency(cleanupEfficiency);
            
            // Cleanup assertions
            assertThat(contextLeak).isLessThan(ITERATIONS / 10); // Less than 10% leak
            assertThat(cleanupEfficiency).isGreaterThan(0.9); // At least 90% cleanup efficiency
            
            System.out.printf("Thread Context Cleanup - Efficiency: %.2f%%, Leaked: %d/%d%n",
                cleanupEfficiency * 100, contextLeak, ITERATIONS);
        }

        @Test
        @DisplayName("Resource utilization under stress")
        void resourceUtilizationUnderStress() throws InterruptedException {
            final int THREAD_COUNT = 50;
            final int DURATION_SECONDS = 10;
            
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            AtomicInteger operationCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            long startTime = System.nanoTime();
            MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
            
            // Submit stress test tasks
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadId = t;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    long threadEndTime = startTime + (DURATION_SECONDS * 1_000_000_000L);
                    
                    while (System.nanoTime() < threadEndTime) {
                        try {
                            TFI.startSession("Stress Session " + threadId + "-" + operationCount.get());
                            
                            try (TaskContext task = TFI.start("Stress Task")) {
                                task.message("Stress testing");
                                task.attribute("threadId", threadId);
                                
                                // Create some tracked objects
                                TestObject obj = new TestObject("StressObj", operationCount.get());
                                TFI.track("stressObj", obj);
                                obj.setValue(obj.getValue() + 1);
                                
                                task.success();
                            }
                            
                            TFI.endSession();
                            operationCount.incrementAndGet();
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                        
                        // Brief pause to prevent CPU saturation
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // Wait for completion
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
            
            long endTime = System.nanoTime();
            MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
            
            // Calculate stress test metrics
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = operationCount.get() / durationSeconds;
            double errorRate = errorCount.get() / (double) operationCount.get();
            long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
            
            metrics.recordStressTestResults(THREAD_COUNT, throughput, errorRate, memoryUsed);
            
            // Stress test assertions
            assertThat(errorRate).isLessThan(0.01); // Less than 1% error rate
            assertThat(throughput).isGreaterThan(100); // At least 100 ops/sec under stress
            assertThat(memoryUsed).isLessThan(1024 * 1024 * 1024); // Less than 1GB
            
            System.out.printf("Stress Test Results (%d threads, %ds) - Throughput: %.2f ops/sec, " +
                "Error Rate: %.2f%%, Memory: %d bytes%n",
                THREAD_COUNT, DURATION_SECONDS, throughput, errorRate * 100, memoryUsed);
        }
    }

    // Performance metrics collection class
    private static class PerformanceMetrics {
        private final Map<String, Double> throughputMetrics = new HashMap<>();
        private final Map<String, Long> memoryMetrics = new HashMap<>();
        private final Map<String, LatencyMetrics> latencyMetrics = new HashMap<>();
        
        void recordThroughput(String operation, double throughput) {
            throughputMetrics.put(operation, throughput);
        }
        
        void recordMemoryUsage(String operation, long bytes) {
            memoryMetrics.put(operation, bytes);
        }
        
        void recordLatency(String operation, long p50, long p95, long p99) {
            latencyMetrics.put(operation, new LatencyMetrics(p50, p95, p99));
        }
        
        void recordExportLatency(String format, int dataSize, long latency) {
            String key = format + "_size_" + dataSize;
            latencyMetrics.put(key, new LatencyMetrics(latency, latency, latency));
        }
        
        void recordConcurrentThroughput(int threadCount, double throughput) {
            throughputMetrics.put("concurrent_" + threadCount, throughput);
        }
        
        void recordNestingPerformance(int depth, long latency, long memory) {
            latencyMetrics.put("nesting_" + depth, new LatencyMetrics(latency, latency, latency));
            memoryMetrics.put("nesting_" + depth, memory);
        }
        
        void recordMemoryGrowth(long totalGrowth, double avgGrowthPerCycle) {
            memoryMetrics.put("totalGrowth", totalGrowth);
            memoryMetrics.put("avgGrowthPerCycle", (long) avgGrowthPerCycle);
        }
        
        void recordLargeObjectTracking(int objectCount, long memory, long latency) {
            memoryMetrics.put("largeObjects_" + objectCount, memory);
            latencyMetrics.put("largeObjects_" + objectCount, new LatencyMetrics(latency, latency, latency));
        }
        
        void recordCleanupEfficiency(double efficiency) {
            throughputMetrics.put("cleanupEfficiency", efficiency);
        }
        
        void recordStressTestResults(int threadCount, double throughput, double errorRate, long memory) {
            throughputMetrics.put("stress_" + threadCount + "_throughput", throughput);
            throughputMetrics.put("stress_" + threadCount + "_errorRate", errorRate);
            memoryMetrics.put("stress_" + threadCount, memory);
        }
        
        private static class LatencyMetrics {
            final long p50, p95, p99;
            
            LatencyMetrics(long p50, long p95, long p99) {
                this.p50 = p50;
                this.p95 = p95;
                this.p99 = p99;
            }
        }
    }

    // Test object classes for performance testing
    private static class TestObject {
        private String name;
        private int value;
        
        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
    
    private static class LargeTestObject {
        private String id;
        private byte[] data;
        private Map<String, Object> metadata;
        
        public LargeTestObject(String id) {
            this.id = id;
            this.data = new byte[1024]; // 1KB data
            this.metadata = new HashMap<>();
            
            // Fill with test data
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (i % 256);
            }
            
            // Add metadata
            for (int i = 0; i < 10; i++) {
                metadata.put("key" + i, "value" + i);
            }
        }
        
        public void updateData(String suffix) {
            metadata.put("lastUpdate", suffix);
        }
        
        public String getId() { return id; }
        public byte[] getData() { return data; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    private void warmUpJvm() {
        // Warm up JVM for more accurate performance measurements
        for (int i = 0; i < 100; i++) {
            TFI.startSession("Warmup Session " + i);
            try (TaskContext task = TFI.start("Warmup Task")) {
                task.message("Warming up JVM");
                task.success();
            }
            TFI.endSession();
        }
        
        // Force GC to clean up warmup data
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}