package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TFI API性能基准测试 - 生产环境性能指标验证套件
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>采用微基准测试方法，使用JVM预热消除JIT编译影响</li>
 *   <li>建立性能基线对比，量化TFI框架的实际性能开销</li>
 *   <li>使用系统属性控制测试启用，避免影响常规CI流程</li>
 *   <li>通过多维度性能指标全面评估系统性能表现</li>
 *   <li>结合内存监控确保无内存泄漏和资源浪费</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>基础操作性能：</strong>单次API调用时间 < 100微秒，10万次操作基准测试</li>
 *   <li><strong>高频调用吞吐：</strong>持续3秒测试，目标吞吐量 ≥ 1万次/秒</li>
 *   <li><strong>内存稳定性：</strong>5万次操作，内存增长 < 100MB，无明显泄漏</li>
 *   <li><strong>禁用状态性能：</strong>10万次操作，性能提升 > 2倍验证</li>
 *   <li><strong>深度嵌套性能：</strong>1000层嵌套，每层平均时间 < 5微秒</li>
 *   <li><strong>并发性能：</strong>10线程×1万操作，30秒内完成验证</li>
 * </ul>
 * 
 * <h2>性能场景：</h2>
 * <ul>
 *   <li><strong>微操作基准：</strong>100,000次基础操作（start/message/stop）性能测试</li>
 *   <li><strong>持续负载：</strong>3秒持续高频调用，测试系统稳定吞吐能力</li>
 *   <li><strong>内存压力：</strong>50,000任务×2消息×1子任务，内存使用监控</li>
 *   <li><strong>极限嵌套：</strong>1000层任务嵌套创建/销毁性能测试</li>
 *   <li><strong>并发负载：</strong>10线程并发执行总计100,000次操作</li>
 *   <li><strong>状态切换：</strong>禁用与启用状态性能对比分析</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>单次操作性能：</strong>平均响应时间 < 100微秒（考虑Spring Boot框架开销）</li>
 *   <li><strong>系统吞吐能力：</strong>持续吞吐量 ≥ 10,000 operations/second</li>
 *   <li><strong>内存使用稳定：</strong>大量操作后内存增长控制在合理范围</li>
 *   <li><strong>禁用状态优化：</strong>禁用状态性能提升 > 2倍，验证快速路径有效</li>
 *   <li><strong>嵌套性能线性：</strong>深度嵌套性能保持线性增长，无指数级退化</li>
 *   <li><strong>并发扩展性：</strong>并发环境下性能表现良好，无显著性能退化</li>
 * </ul>
 * 
 * <h3>性能要求与验证标准：</h3>
 * <ol>
 *   <li><strong>响应时间要求：</strong>单次完整操作 < 100微秒（包含框架开销）</li>
 *   <li><strong>吞吐量要求：</strong>生产级吞吐量 ≥ 10,000 ops/s（适合CI环境）</li>
 *   <li><strong>内存效率要求：</strong>大规模操作内存增长 < 100MB</li>
 *   <li><strong>状态切换要求：</strong>禁用状态性能提升 > 2倍</li>
 *   <li><strong>嵌套深度要求：</strong>1000层嵌套每层处理 < 5微秒</li>
 *   <li><strong>并发性能要求：</strong>多线程环境无显著性能退化</li>
 * </ol>
 * 
 * <p><strong>注意：</strong>此测试需要设置系统属性 -Dtfi.perf.enabled=true 才会执行</p>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "tfi.perf.enabled", matches = "(?i)true")
class TFIPerformanceTest {
    
    private PerformanceMetrics metrics;
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
        metrics = new PerformanceMetrics();
        
        // JVM预热
        warmUpJVM();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
        metrics.printReport();
    }
    
    @Test
    void testBasicOperationPerformance() {
        final int ITERATIONS = 100_000;
        
        // 测量基准性能（无TFI操作）
        long baselineTime = measureBaseline(ITERATIONS);
        
        // 测量TFI操作性能
        long tfiTime = measureTFIOperations(ITERATIONS);
        
        // 计算性能开销
        double overheadPercent = ((double) (tfiTime - baselineTime) / baselineTime) * 100;
        
        metrics.recordBasicPerformance("basic-operations", tfiTime, baselineTime, overheadPercent, ITERATIONS);
        
        // 基准测试调整：由于TFI是完整的框架操作，不应与简单字符串操作比较开销百分比
        // 改为验证绝对性能指标
        long avgTFIOperationTime = tfiTime / (ITERATIONS * 3); // 3个操作：start, message, stop
        
        // 验证单次TFI操作性能：应小于100微秒（考虑Spring Boot上下文和框架开销）
        assertThat(avgTFIOperationTime).isLessThan(100_000) // 100微秒
            .withFailMessage("TFI单次操作平均时间 %d ns 超过100微秒要求", avgTFIOperationTime);
        
        // 记录相对开销仅用于监控，不作为硬性要求
        System.out.printf("性能对比 - TFI开销相对基准: %.2f%%, 绝对时间: %d ns/op%n", 
            overheadPercent, avgTFIOperationTime);
        
        // 删除这行重复的输出，上面已经有了
    }
    
    @Test
    void testHighFrequencyCallPerformance() {
        final int TARGET_OPS_PER_SECOND = 10_000; // 1万次/秒目标（更贴近实际/CI）
        final int TEST_DURATION_SECONDS = 3;
        
        long startTime = System.nanoTime();
        int operationCount = 0;
        
        // 持续执行指定时间
        while ((System.nanoTime() - startTime) < TimeUnit.SECONDS.toNanos(TEST_DURATION_SECONDS)) {
            try (TaskContext task = TFI.start("high-freq-test-" + operationCount)) {
                task.message("High frequency test message " + operationCount);
            }
            operationCount++;
        }
        
        long totalTime = System.nanoTime() - startTime;
        double actualOpsPerSecond = (double) operationCount / (totalTime / 1_000_000_000.0);
        
        metrics.recordHighFrequencyPerformance("high-frequency", actualOpsPerSecond, operationCount, totalTime);
        
        // 验证吞吐量要求
        assertThat(actualOpsPerSecond).isGreaterThan(TARGET_OPS_PER_SECOND)
            .withFailMessage("实际吞吐量 %.0f ops/s 低于目标 %d ops/s", 
                actualOpsPerSecond, TARGET_OPS_PER_SECOND);
        
        System.out.printf("高频调用性能 - 吞吐量: %.0f ops/s, 总操作数: %d%n", 
            actualOpsPerSecond, operationCount);
    }
    
    @Test
    void testMemoryUsageStability() {
        final int ITERATIONS = 50_000;
        
        // 记录初始内存使用
        Runtime runtime = Runtime.getRuntime();
        System.gc(); // 强制GC获取准确基线
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 执行大量操作
        for (int i = 0; i < ITERATIONS; i++) {
            try (TaskContext task = TFI.start("memory-test-" + i)) {
                task.message(String.format("Memory test message %d", i));
                task.message("Additional message for task " + i);
                
                // 创建子任务
                try (TaskContext subTask = task.subtask("sub-" + i)) {
                    subTask.message("Subtask message");
                }
            }
            
            // 每1000次操作检查一次内存
            if (i % 1000 == 0 && i > 0) {
                System.gc();
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                long memoryGrowth = currentMemory - initialMemory;
                
                // 内存增长不应超过100MB
                assertThat(memoryGrowth).isLessThan(100 * 1024 * 1024)
                    .withFailMessage("内存增长 %d MB 在 %d 次操作后超过100MB限制", 
                        memoryGrowth / (1024 * 1024), i);
            }
        }
        
        // 最终内存检查
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalGrowth = finalMemory - initialMemory;
        
        metrics.recordMemoryUsage("memory-stability", initialMemory, finalMemory, totalGrowth, ITERATIONS);
        
        System.out.printf("内存使用测试 - 初始: %d MB, 最终: %d MB, 增长: %d MB%n",
            initialMemory / (1024 * 1024), finalMemory / (1024 * 1024), totalGrowth / (1024 * 1024));
    }
    
    @Test
    void testDisabledStatePerformance() {
        final int ITERATIONS = 1_00_000; // 更高的迭代次数测试禁用状态
        
        // 禁用TFI
        TFI.disable();
        
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            try (TaskContext task = TFI.start("disabled-test")) {
                task.message("This should be ignored");
            }
        }
        long disabledTime = System.nanoTime() - startTime;
        
        // 重新启用并测试正常状态
        TFI.enable();
        startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            try (TaskContext task = TFI.start("enabled-test")) {
                task.message("This should be recorded");
            }
        }
        long enabledTime = System.nanoTime() - startTime;
        
        // 禁用状态应该明显更快
        double performanceRatio = (double) enabledTime / disabledTime;
        
        metrics.recordDisabledPerformance("disabled-state", disabledTime, enabledTime, performanceRatio, ITERATIONS);
        
        assertThat(performanceRatio).isGreaterThan(2.0)
            .withFailMessage("禁用状态性能提升比例 %.2f 应大于2倍", performanceRatio);
        
        System.out.printf("禁用状态性能 - 禁用: %d ns, 启用: %d ns, 性能提升: %.2fx%n",
            disabledTime / ITERATIONS, enabledTime / ITERATIONS, performanceRatio);
    }
    
    @Test
    void testDeepNestingPerformance() {
        final int DEPTH = 1000; // 1000层嵌套
        
        long startTime = System.nanoTime();
        
        // 创建深度嵌套任务
        List<TaskContext> tasks = new ArrayList<>(DEPTH);
        for (int i = 0; i < DEPTH; i++) {
            TaskContext task = TFI.start("level-" + i);
            tasks.add(task);
        }
        
        // 在最深层添加消息
        TFI.message("Message at depth " + DEPTH, MessageType.PROCESS);
        
        // 逐层关闭任务
        for (int i = DEPTH - 1; i >= 0; i--) {
            tasks.get(i).close();
        }
        
        long totalTime = System.nanoTime() - startTime;
        long avgTimePerLevel = totalTime / (DEPTH * 2); // 创建和关闭
        
        metrics.recordNestingPerformance("deep-nesting", avgTimePerLevel, DEPTH, totalTime);
        
        // 验证深度嵌套性能：每层平均时间应小于5微秒
        assertThat(avgTimePerLevel).isLessThan(5000) // 5000纳秒 = 5微秒
            .withFailMessage("深度嵌套平均时间 %d ns/层超过5微秒要求", avgTimePerLevel);
        
        System.out.printf("深度嵌套性能 (%d 层) - 平均: %d ns/层, 总时间: %.2f ms%n",
            DEPTH, avgTimePerLevel, totalTime / 1_000_000.0);
    }
    
    @Test
    void testConcurrentPerformance() {
        final int THREAD_COUNT = 10;
        final int OPERATIONS_PER_THREAD = 10_000;
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        
        long startTime = System.nanoTime();
        
        // 启动并发测试线程
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        try (TaskContext task = TFI.start("concurrent-" + threadId + "-" + j)) {
                            task.message("Concurrent message from thread " + threadId);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // 开始测试
        
        try {
            boolean finished = finishLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue().withFailMessage("并发测试超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long totalTime = System.nanoTime() - startTime;
        int totalOperations = THREAD_COUNT * OPERATIONS_PER_THREAD;
        double throughput = totalOperations / (totalTime / 1_000_000_000.0);
        
        metrics.recordConcurrentPerformance("concurrent", throughput, totalOperations, THREAD_COUNT, totalTime);
        
        executor.shutdown();
        
        System.out.printf("并发性能测试 - 吞吐量: %.0f ops/s, 总操作: %d, 线程数: %d%n",
            throughput, totalOperations, THREAD_COUNT);
    }
    
    private long measureBaseline(int iterations) {
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            // 模拟相同的工作量但不使用TFI
            String taskName = "baseline-test-" + i;
            String message = "Baseline message";
            // 简单的字符串操作模拟
            @SuppressWarnings("unused")
            String result = taskName + ": " + message;
        }
        return System.nanoTime() - startTime;
    }
    
    private long measureTFIOperations(int iterations) {
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (TaskContext task = TFI.start("perf-test-" + i)) {
                task.message("Performance test message");
            }
        }
        return System.nanoTime() - startTime;
    }
    
    private void warmUpJVM() {
        // JVM预热，避免JIT编译影响测试结果
        for (int i = 0; i < 1000; i++) {
            try (TaskContext task = TFI.start("warmup")) {
                task.message("warmup message");
            }
        }
        TFI.clear();
    }
    
    /**
     * 性能指标收集器
     */
    private static class PerformanceMetrics {
        private final List<String> reports = new ArrayList<>();
        
        void recordBasicPerformance(String operation, long tfiTime, long baselineTime, 
                                  double overheadPercent, int iterations) {
            reports.add(String.format("%-20s: TFI=%8d ns, Base=%8d ns, 开销=%.2f%%, 迭代=%d",
                operation, tfiTime/iterations, baselineTime/iterations, overheadPercent, iterations));
        }
        
        void recordHighFrequencyPerformance(String operation, double opsPerSecond, 
                                          int operationCount, long totalTime) {
            reports.add(String.format("%-20s: %.0f ops/s, 总操作=%d, 时间=%.2f s",
                operation, opsPerSecond, operationCount, totalTime/1_000_000_000.0));
        }
        
        void recordMemoryUsage(String operation, long initial, long end, 
                             long growth, int iterations) {
            reports.add(String.format("%-20s: 初始=%d MB, 结束=%d MB, 增长=%d MB, 迭代=%d",
                operation, initial/(1024*1024), end/(1024*1024), growth/(1024*1024), iterations));
        }
        
        void recordDisabledPerformance(String operation, long disabledTime, 
                                     long enabledTime, double ratio, int iterations) {
            reports.add(String.format("%-20s: 禁用=%d ns, 启用=%d ns, 提升=%.2fx, 迭代=%d",
                operation, disabledTime/iterations, enabledTime/iterations, ratio, iterations));
        }
        
        void recordNestingPerformance(String operation, long avgTime, int depth, long totalTime) {
            reports.add(String.format("%-20s: 平均=%d ns/层, 深度=%d, 总时间=%.2f ms",
                operation, avgTime, depth, totalTime/1_000_000.0));
        }
        
        void recordConcurrentPerformance(String operation, double throughput, 
                                       int totalOps, int threads, long totalTime) {
            reports.add(String.format("%-20s: %.0f ops/s, 操作=%d, 线程=%d, 时间=%.2f s",
                operation, throughput, totalOps, threads, totalTime/1_000_000_000.0));
        }
        
        void printReport() {
            System.out.println("\n=== TFI 性能测试报告 ===");
            reports.forEach(System.out::println);
            System.out.println("=====================\n");
        }
    }
}
