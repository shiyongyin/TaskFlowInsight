package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 快照并发测试
 * 验证多线程环境下的线程安全性和性能
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@DisplayName("快照并发测试")
public class SnapshotConcurrencyTest {
    
    private SnapshotConfig config;
    private ObjectSnapshotDeepOptimized optimizedSnapshot;
    private SnapshotFacadeOptimized facade;
    private static final int THREAD_COUNT = 10;
    private static final int OPERATIONS_PER_THREAD = 100;
    
    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(3);
        optimizedSnapshot = new ObjectSnapshotDeepOptimized(config);
        
        ShallowSnapshotStrategy shallowStrategy = new ShallowSnapshotStrategy();
        DeepSnapshotStrategy deepStrategy = new DeepSnapshotStrategy(config);
        facade = new SnapshotFacadeOptimized(config, shallowStrategy, deepStrategy);
    }
    
    @Test
    @DisplayName("多线程并发快照测试")
    void testConcurrentSnapshots() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        TestObject obj = createTestObject(threadId, j);
                        
                        Map<String, Object> result = optimizedSnapshot.captureDeep(
                            obj, 3, Collections.emptySet(), Collections.emptySet()
                        );
                        
                        if (!result.isEmpty()) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // 同时启动所有线程
        startLatch.countDown();
        
        // 等待所有线程完成
        boolean completed = completeLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        executor.shutdown();
        
        // 验证结果
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT * OPERATIONS_PER_THREAD);
        assertThat(errorCount.get()).isZero();
        
        // 验证缓存工作正常
        Map<String, Long> metrics = ObjectSnapshotDeepOptimized.getMetrics();
        assertThat(metrics.get("field.cache.size")).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("并发循环引用检测")
    void testConcurrentCycleDetection() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            Future<Map<String, Object>> future = executor.submit(() -> {
                try {
                    // 创建循环引用对象
                    CyclicObject obj1 = new CyclicObject("obj1");
                    CyclicObject obj2 = new CyclicObject("obj2");
                    obj1.reference = obj2;
                    obj2.reference = obj1;
                    
                    return optimizedSnapshot.captureDeep(
                        obj1, 5, Collections.emptySet(), Collections.emptySet()
                    );
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 验证所有线程都正确检测到循环引用
        for (Future<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> result = future.get();
                assertThat(result.toString()).contains("circular-reference");
            } catch (Exception e) {
                throw new RuntimeException("Failed to get future result", e);
            }
        }
        
        Map<String, Long> metrics = ObjectSnapshotDeepOptimized.getMetrics();
        assertThat(metrics.get("cycle.detected")).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("异步快照测试")
    void testAsyncSnapshots() throws Exception {
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        
        for (int i = 0; i < 50; i++) {
            TestObject obj = createTestObject(i, i);
            CompletableFuture<Map<String, Object>> future = facade.captureAsync("test-" + i, obj);
            futures.add(future);
        }
        
        // 等待所有异步操作完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        allFutures.get(5, TimeUnit.SECONDS);
        
        // 验证所有结果
        for (CompletableFuture<Map<String, Object>> future : futures) {
            Map<String, Object> result = future.get();
            assertThat(result).isNotEmpty();
        }
    }
    
    @Test
    @DisplayName("缓存一致性测试")
    void testCacheConcurrency() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        // 使用相同的类进行并发访问，测试字段缓存的线程安全性
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        TestObject obj = new TestObject();
                        optimizedSnapshot.getAllFields(obj.getClass());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 验证缓存正常工作
        Map<String, Long> metrics = ObjectSnapshotDeepOptimized.getMetrics();
        assertThat(metrics.get("field.cache.size")).isPositive();
        
        // 清理缓存后再次测试
        ObjectSnapshotDeepOptimized.clearCaches();
        assertThat(ObjectSnapshotDeepOptimized.getMetrics().get("field.cache.size")).isZero();
    }
    
    @Test
    @DisplayName("并发性能基准测试")
    void testConcurrentPerformance() throws InterruptedException {
        int warmupRounds = 100;
        int measurementRounds = 1000;
        
        // 预热
        for (int i = 0; i < warmupRounds; i++) {
            TestObject obj = createTestObject(i, i);
            optimizedSnapshot.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
        }
        
        // 性能测试
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(THREAD_COUNT);
        
        List<Long> durations = new CopyOnWriteArrayList<>();
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < measurementRounds / THREAD_COUNT; j++) {
                        TestObject obj = createTestObject(j, j);
                        
                        long start = System.nanoTime();
                        optimizedSnapshot.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
                        long duration = System.nanoTime() - start;
                        
                        durations.add(duration);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        completeLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 计算性能指标
        Collections.sort(durations);
        long p50 = durations.get(durations.size() / 2);
        long p95 = durations.get((int) (durations.size() * 0.95));
        long p99 = durations.get((int) (durations.size() * 0.99));
        
        System.out.println("Concurrent Performance Metrics:");
        System.out.printf("  P50: %.2f μs\n", p50 / 1000.0);
        System.out.printf("  P95: %.2f μs\n", p95 / 1000.0);
        System.out.printf("  P99: %.2f μs\n", p99 / 1000.0);
        
        // 验证性能达标
        assertThat(p95).isLessThan(TimeUnit.MILLISECONDS.toNanos(10)); // P95 < 10ms
    }
    
    @Test
    @DisplayName("动态策略切换并发测试")
    void testConcurrentStrategySwitch() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        // 动态切换策略
                        if (j % 2 == 0) {
                            facade.setEnableDeep(true);
                        } else {
                            facade.setEnableDeep(false);
                        }
                        
                        TestObject obj = createTestObject(threadId, j);
                        Map<String, Object> result = facade.capture("test", obj);
                        
                        if (!result.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT * 50);
    }
    
    // ========== 辅助类和方法 ==========
    
    private TestObject createTestObject(int id, int value) {
        TestObject obj = new TestObject();
        obj.id = id;
        obj.value = value;
        obj.name = "test-" + id;
        obj.nested = new NestedObject();
        obj.nested.data = "nested-" + value;
        obj.list = IntStream.range(0, 10).boxed().toList();
        return obj;
    }
    
    static class TestObject {
        int id;
        int value;
        String name;
        NestedObject nested;
        List<Integer> list;
    }
    
    static class NestedObject {
        String data;
    }
    
    static class CyclicObject {
        String name;
        CyclicObject reference;
        
        CyclicObject(String name) {
            this.name = name;
        }
    }
}