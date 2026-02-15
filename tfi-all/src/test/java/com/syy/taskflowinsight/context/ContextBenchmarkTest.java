package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 上下文管理性能基准测试
 * 提供简化的性能测试，无需JMH依赖
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "tfi.perf.enabled", matches = "(?i)true")
class ContextBenchmarkTest {
    
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10000;
    
    @BeforeEach
    void cleanup() {
        ManagedThreadContext current = ManagedThreadContext.current();
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("上下文创建性能基准")
    void benchmarkContextCreation() {
        // 预热JVM
        warmupContextCreation();
        
        // 基准测试
        long startNanos = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            try (ManagedThreadContext context = ManagedThreadContext.create("benchmark-" + i)) {
                // 仅创建和关闭
            }
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        
        double avgMicros = (elapsedNanos / 1000.0) / BENCHMARK_ITERATIONS;
        double throughput = (BENCHMARK_ITERATIONS * 1_000_000_000.0) / elapsedNanos;
        
        System.out.printf("Context Creation Performance:%n");
        System.out.printf("  Average time: %.2f μs%n", avgMicros);
        System.out.printf("  Throughput: %.0f ops/sec%n", throughput);
        System.out.printf("  Total time: %.2f ms%n", elapsedNanos / 1_000_000.0);
        
        // 性能断言（宽松的目标）
        assertTrue(avgMicros < 200, "Context creation too slow: " + avgMicros + " μs");
        assertTrue(throughput > 5000, "Throughput too low: " + throughput + " ops/sec");
    }
    
    @Test
    @Order(2)
    @DisplayName("任务操作性能基准")
    void benchmarkTaskOperations() {
        warmupTaskOperations();
        
        try (ManagedThreadContext context = ManagedThreadContext.create("taskBench")) {
            long startNanos = System.nanoTime();
            
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                context.startTask("task-" + i);
                context.endTask();
            }
            
            long elapsedNanos = System.nanoTime() - startNanos;
            double avgNanos = (double) elapsedNanos / BENCHMARK_ITERATIONS;
            double throughput = (BENCHMARK_ITERATIONS * 1_000_000_000.0) / elapsedNanos;
            
            System.out.printf("Task Operations Performance:%n");
            System.out.printf("  Average time: %.2f ns%n", avgNanos);
            System.out.printf("  Throughput: %.0f ops/sec%n", throughput);
            System.out.printf("  Total time: %.2f ms%n", elapsedNanos / 1_000_000.0);
            
            // 性能断言（调整为更现实的目标）
            assertTrue(avgNanos < 50000, "Task operation too slow: " + avgNanos + " ns");
            assertTrue(throughput > 20000, "Throughput too low: " + throughput + " ops/sec");
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("快照创建性能基准")
    void benchmarkSnapshotCreation() {
        try (ManagedThreadContext context = ManagedThreadContext.create("snapBench")) {
            context.startTask("nestedTask");
            
            // 预热
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                ContextSnapshot snapshot = context.createSnapshot();
            }
            
            // 基准测试
            long startNanos = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                ContextSnapshot snapshot = context.createSnapshot();
            }
            long elapsedNanos = System.nanoTime() - startNanos;
            
            double avgNanos = (double) elapsedNanos / BENCHMARK_ITERATIONS;
            double throughput = (BENCHMARK_ITERATIONS * 1_000_000_000.0) / elapsedNanos;
            
            System.out.printf("Snapshot Creation Performance:%n");
            System.out.printf("  Average time: %.2f ns%n", avgNanos);
            System.out.printf("  Throughput: %.0f ops/sec%n", throughput);
            
            assertTrue(avgNanos < 1000, "Snapshot creation too slow: " + avgNanos + " ns");
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("内存分配性能测试")
    void benchmarkMemoryAllocation() {
        Runtime runtime = Runtime.getRuntime();
        
        // 强制GC
        System.gc();
        Thread.yield();
        System.gc();
        
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 创建大量上下文
        List<ManagedThreadContext> contexts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            contexts.add(ManagedThreadContext.create("memory-test-" + i));
        }
        
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryPerContext = (afterMemory - beforeMemory) / 1000;
        
        System.out.printf("Memory Allocation:%n");
        System.out.printf("  Memory per context: %d bytes%n", memoryPerContext);
        System.out.printf("  Total allocated: %.2f KB%n", (afterMemory - beforeMemory) / 1024.0);
        
        // 清理
        for (ManagedThreadContext context : contexts) {
            context.close();
        }
        
        // 验证内存使用合理（调整为更现实的期望）
        assertTrue(memoryPerContext < 50000, "Memory usage too high: " + memoryPerContext + " bytes per context");
    }
    
    @Test
    @Order(5)
    @DisplayName("并发性能测试")
    void benchmarkConcurrentPerformance() throws Exception {
        int threadCount = Runtime.getRuntime().availableProcessors();
        int operationsPerThread = 1000;
        
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(threadCount);
        
        try {
            long startTime = System.nanoTime();
            
            List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();
            
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    int operations = 0;
                    for (int i = 0; i < operationsPerThread; i++) {
                        try (ManagedThreadContext context = ManagedThreadContext.create("concurrent")) {
                            context.startTask("task");
                            context.endTask();
                            operations++;
                        }
                    }
                    return operations;
                }));
            }
            
            int totalOperations = 0;
            for (java.util.concurrent.Future<Integer> future : futures) {
                totalOperations += future.get(30, TimeUnit.SECONDS);
            }
            
            long elapsedNanos = System.nanoTime() - startTime;
            double throughput = (totalOperations * 1_000_000_000.0) / elapsedNanos;
            
            System.out.printf("Concurrent Performance (%d threads):%n", threadCount);
            System.out.printf("  Total operations: %d%n", totalOperations);
            System.out.printf("  Throughput: %.0f ops/sec%n", throughput);
            System.out.printf("  Time: %.2f ms%n", elapsedNanos / 1_000_000.0);
            
            assertEquals(threadCount * operationsPerThread, totalOperations);
            assertTrue(throughput > 10000, "Concurrent throughput too low: " + throughput);
            
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
    
    // 预热方法
    private void warmupContextCreation() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try (ManagedThreadContext context = ManagedThreadContext.create("warmup")) {
                // 预热JIT编译
            }
        }
    }
    
    private void warmupTaskOperations() {
        try (ManagedThreadContext context = ManagedThreadContext.create("warmup")) {
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                context.startTask("warmup");
                context.endTask();
            }
        }
    }
}
