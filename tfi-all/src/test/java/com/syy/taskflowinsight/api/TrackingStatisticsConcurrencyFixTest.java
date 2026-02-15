package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TrackingStatistics并发安全修复验证测试
 * 验证修复HashMap并发问题后的线程安全性
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("TrackingStatistics并发安全修复验证测试")
class TrackingStatisticsConcurrencyFixTest {

    private TrackingStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new TrackingStatistics();
    }

    @Test
    @DisplayName("并发对象追踪应该无数据丢失")
    void concurrentObjectTrackingShouldNotLoseData() throws InterruptedException {
        final int threadCount = 20;
        final int objectsPerThread = 100;
        final int totalExpectedObjects = threadCount * objectsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 每个线程追踪不同的对象
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < objectsPerThread; i++) {
                        String objectName = "thread" + threadId + "_object" + i;
                        stats.recordObjectTracked(objectName);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        TrackingStatistics.StatisticsSummary summary = stats.getSummary();
        
        // 验证没有数据丢失 - 这在修复前会失败
        assertThat(summary.totalObjectsTracked)
            .withFailMessage("Expected %d objects but got %d - data loss detected!", 
                totalExpectedObjects, summary.totalObjectsTracked)
            .isEqualTo(totalExpectedObjects);
    }

    @Test
    @DisplayName("并发变更记录应该无数据丢失且保持一致性")
    void concurrentChangeRecordingShouldNotLoseDataAndMaintainConsistency() throws InterruptedException {
        final int threadCount = 15;
        final int changesPerThread = 50;
        final int totalExpectedChanges = threadCount * changesPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 先注册一些对象用于变更追踪
        for (int i = 0; i < threadCount; i++) {
            stats.recordObjectTracked("object" + i);
        }
        
        // 每个线程记录变更
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    List<ChangeRecord> changes = new ArrayList<>();
                    for (int i = 0; i < changesPerThread; i++) {
                        String objectName = "object" + threadId;
                        String fieldName = "field" + i;
                        ChangeType changeType = ChangeType.values()[i % ChangeType.values().length];
                        
                        changes.add(ChangeRecord.of(objectName, fieldName, "oldValue" + i, "newValue" + i, changeType));
                    }
                    stats.recordChanges(changes, 1000000L); // 1ms in nanos
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        TrackingStatistics.StatisticsSummary summary = stats.getSummary();
        
        // 验证变更总数
        assertThat(summary.totalChangesDetected)
            .withFailMessage("Expected %d changes but got %d - data loss detected!", 
                totalExpectedChanges, summary.totalChangesDetected)
            .isEqualTo(totalExpectedChanges);
        
        // 验证对象统计数据的一致性
        List<TrackingStatistics.ObjectStatistics> topObjects = stats.getTopChangedObjects(threadCount);
        assertThat(topObjects).hasSize(threadCount);
        
        // 每个对象应该有正确的变更数量
        for (TrackingStatistics.ObjectStatistics objStats : topObjects) {
            assertThat(objStats.getTotalChanges())
                .withFailMessage("Object %s should have %d changes but has %d", 
                    objStats.getObjectName(), changesPerThread, objStats.getTotalChanges())
                .isEqualTo(changesPerThread);
        }
    }

    @Test
    @DisplayName("并发重置操作应该线程安全")
    void concurrentResetShouldBeThreadSafe() throws InterruptedException {
        final int threadCount = 10;
        final int iterations = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 一半线程添加数据，一半线程重置
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    if (threadId % 2 == 0) {
                        // 添加数据的线程
                        for (int i = 0; i < iterations; i++) {
                            stats.recordObjectTracked("resetTest" + threadId + "_" + i);
                            List<ChangeRecord> changes = List.of(
                                ChangeRecord.of("resetTest" + threadId, "field" + i, "old", "new", ChangeType.UPDATE)
                            );
                            stats.recordChanges(changes, 500000L);
                        }
                    } else {
                        // 重置的线程
                        for (int i = 0; i < iterations; i++) {
                            stats.reset();
                            Thread.sleep(1); // 短暂等待
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 验证没有出现并发修改异常或死锁
        // 最终状态可能为空（因为重置）或有数据，但不应该有异常
        TrackingStatistics.StatisticsSummary summary = stats.getSummary();
        assertThat(summary).isNotNull();
        assertThat(summary.totalObjectsTracked).isGreaterThanOrEqualTo(0);
        assertThat(summary.totalChangesDetected).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("并发性能快照记录应该保持一致性")
    void concurrentPerformanceSnapshotsShouldMaintainConsistency() throws InterruptedException {
        final int threadCount = 12;
        final int snapshotsPerThread = 25;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 每个线程记录性能快照
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < snapshotsPerThread; i++) {
                        List<ChangeRecord> changes = List.of(
                            ChangeRecord.of("perfObject" + threadId, "field" + i, "old", "new", ChangeType.UPDATE)
                        );
                        stats.recordChanges(changes, (threadId * 1000L + i) * 1000000L); // 不同的时间
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 验证性能统计的一致性
        TrackingStatistics.PerformanceStatistics perfStats = stats.getPerformanceStatistics();
        assertThat(perfStats).isNotNull();
        
        // 验证平均检测时间是合理的（不是NaN或负数）
        double avgTime = stats.getAverageDetectionTimeMs();
        assertThat(avgTime).isGreaterThanOrEqualTo(0);
        assertThat(avgTime).isNotNaN();
    }

    @Test
    @DisplayName("格式化输出在并发环境下应该稳定")
    void formatOutputShouldBeStableUnderConcurrency() throws InterruptedException {
        final int threadCount = 8;
        final int iterations = 30;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 添加一些初始数据
        stats.recordObjectTracked("formatTest");
        List<ChangeRecord> initialChanges = List.of(
            ChangeRecord.of("formatTest", "field", "old", "new", ChangeType.UPDATE)
        );
        stats.recordChanges(initialChanges, 1000000L);
        
        // 一些线程添加数据，一些线程调用格式化
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    if (threadId % 2 == 0) {
                        // 添加数据
                        for (int i = 0; i < iterations; i++) {
                            stats.recordObjectTracked("formatObject" + threadId + "_" + i);
                        }
                    } else {
                        // 调用格式化方法
                        for (int i = 0; i < iterations; i++) {
                            String formatted = stats.format();
                            assertThat(formatted).isNotNull();
                            assertThat(formatted).contains("=== Tracking Statistics ===");
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 最终格式化不应该抛出异常
        String finalFormat = stats.format();
        assertThat(finalFormat).isNotNull();
        assertThat(finalFormat).isNotEmpty();
    }
}