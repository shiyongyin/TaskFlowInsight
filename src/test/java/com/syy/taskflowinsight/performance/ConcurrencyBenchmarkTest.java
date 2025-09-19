package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实际并发性能基准测试
 * 测试不同并发级别下的真实QPS表现
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("并发性能基准测试")
public class ConcurrencyBenchmarkTest {

    private static final int WARM_UP_DURATION_SECONDS = 2;
    private static final int TEST_DURATION_SECONDS = 10;
    
    @BeforeAll
    static void setup() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        System.out.println("========================================");
        System.out.println("TaskFlowInsight 并发性能基准测试");
        System.out.println("========================================");
        System.out.println("测试配置:");
        System.out.println("- 预热时间: " + WARM_UP_DURATION_SECONDS + "秒");
        System.out.println("- 测试时间: " + TEST_DURATION_SECONDS + "秒");
        System.out.println("========================================\n");
    }

    @Test
    @Order(1)
    @DisplayName("1线程 - 基线性能")
    void testSingleThread() throws InterruptedException {
        runBenchmark(1);
    }

    @Test
    @Order(2)
    @DisplayName("5线程 - 低并发")
    void test5Threads() throws InterruptedException {
        runBenchmark(5);
    }

    @Test
    @Order(3)
    @DisplayName("10线程 - 轻度并发")
    void test10Threads() throws InterruptedException {
        runBenchmark(10);
    }

    @Test
    @Order(4)
    @DisplayName("20线程 - 中低并发")
    void test20Threads() throws InterruptedException {
        runBenchmark(20);
    }

    @Test
    @Order(5)
    @DisplayName("50线程 - 中等并发")
    void test50Threads() throws InterruptedException {
        runBenchmark(50);
    }

    @Test
    @Order(6)
    @DisplayName("100线程 - 高并发")
    void test100Threads() throws InterruptedException {
        runBenchmark(100);
    }

    @Test
    @Order(7)
    @DisplayName("200线程 - 超高并发")
    void test200Threads() throws InterruptedException {
        runBenchmark(200);
    }

    @Test
    @Order(8)
    @DisplayName("500线程 - 极限并发")
    void test500Threads() throws InterruptedException {
        runBenchmark(500);
    }

    @Test
    @Order(9)
    @DisplayName("1000线程 - 超极限并发")
    void test1000Threads() throws InterruptedException {
        runBenchmark(1000);
    }

    @Test
    @Order(10)
    @DisplayName("2000线程 - 大规模并发")
    void test2000Threads() throws InterruptedException {
        runBenchmark(2000);
    }

    @Test
    @Order(11)
    @DisplayName("3500线程 - 超大规模并发")
    void test3500Threads() throws InterruptedException {
        runBenchmark(3500);
    }

    @Test
    @Order(12)
    @DisplayName("5000线程 - 终极压力测试")
    void test5000Threads() throws InterruptedException {
        runBenchmark(5000);
    }

    private void runBenchmark(int threadCount) throws InterruptedException {
        System.out.println("\n【测试 " + threadCount + " 线程并发】");
        System.out.println("----------------------------------------");
        
        // 清理环境
        TFI.clear();
        System.gc();
        Thread.sleep(500);
        
        // 预热
        System.out.print("预热中...");
        warmUp(threadCount);
        System.out.println(" 完成");
        
        // 清理预热数据
        TFI.clear();
        System.gc();
        Thread.sleep(500);
        
        // 记录开始指标
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // 执行性能测试
        BenchmarkResult result = executeBenchmark(threadCount);
        
        // 记录结束指标
        long endTime = System.currentTimeMillis();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        double durationSeconds = (endTime - startTime) / 1000.0;
        
        // 计算性能指标
        double qps = result.successCount.get() / durationSeconds;
        double avgLatency = result.totalLatency.get() / (double) result.successCount.get() / 1_000_000.0; // 转换为毫秒
        double errorRate = result.errorCount.get() * 100.0 / (result.successCount.get() + result.errorCount.get());
        long memoryUsed = (memoryAfter - memoryBefore) / 1024 / 1024; // MB
        
        // 输出结果
        System.out.println("执行时间: " + String.format("%.2f", durationSeconds) + " 秒");
        System.out.println("成功操作: " + result.successCount.get());
        System.out.println("失败操作: " + result.errorCount.get());
        System.out.println("QPS: " + String.format("%.2f", qps) + " ops/sec");
        System.out.println("平均延迟: " + String.format("%.3f", avgLatency) + " ms");
        System.out.println("错误率: " + String.format("%.2f", errorRate) + "%");
        System.out.println("内存使用: " + memoryUsed + " MB");
        System.out.println("每线程QPS: " + String.format("%.2f", qps / threadCount) + " ops/sec");
        
        // 性能评级
        String rating = getPerformanceRating(qps, errorRate);
        System.out.println("性能评级: " + rating);
        System.out.println("----------------------------------------");
    }
    
    private void warmUp(int threadCount) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long endTime = System.currentTimeMillis() + WARM_UP_DURATION_SECONDS * 1000;
                    while (System.currentTimeMillis() < endTime) {
                        performOperation();
                    }
                } catch (Exception e) {
                    // 忽略预热错误
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();
    }
    
    private BenchmarkResult executeBenchmark(int threadCount) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        BenchmarkResult result = new BenchmarkResult();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long endTime = System.currentTimeMillis() + TEST_DURATION_SECONDS * 1000;
                    
                    while (System.currentTimeMillis() < endTime) {
                        long startOp = System.nanoTime();
                        try {
                            performOperation();
                            result.successCount.incrementAndGet();
                        } catch (Exception e) {
                            result.errorCount.incrementAndGet();
                        }
                        long endOp = System.nanoTime();
                        result.totalLatency.addAndGet(endOp - startOp);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();
        
        return result;
    }
    
    private void performOperation() {
        // 模拟真实业务操作
        String sessionId = TFI.startSession("Session-" + Thread.currentThread().getId());
        
        try (TaskContext main = TFI.start("MainTask")) {
            main.message("Processing request");
            main.attribute("timestamp", System.currentTimeMillis());
            
            // 子任务1
            try (TaskContext sub1 = main.subtask("Validation")) {
                sub1.message("Validating input");
                Thread.sleep(0, 100); // 模拟微小延迟
                sub1.success();
            }
            
            // 子任务2
            try (TaskContext sub2 = main.subtask("Processing")) {
                sub2.message("Processing data");
                sub2.attribute("processed", true);
                Thread.sleep(0, 100); // 模拟微小延迟
                sub2.success();
            }
            
            main.success();
        } catch (Exception e) {
            // 忽略
        } finally {
            TFI.endSession();
        }
    }
    
    private String getPerformanceRating(double qps, double errorRate) {
        if (errorRate > 1.0) return "❌ 不稳定";
        if (qps < 1000) return "⚠️ 需优化";
        if (qps < 10000) return "✅ 良好";
        if (qps < 50000) return "🚀 优秀";
        return "🏆 卓越";
    }
    
    private static class BenchmarkResult {
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
    }
    
    @AfterAll
    static void summary() {
        System.out.println("\n========================================");
        System.out.println("测试完成！");
        System.out.println("========================================");
    }
}