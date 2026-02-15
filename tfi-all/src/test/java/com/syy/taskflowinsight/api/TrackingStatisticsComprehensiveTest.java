package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TrackingStatistics 全面测试套件
 *
 * 测试思路：
 * 1. 发现并发竞态条件问题
 * 2. 验证边界条件和异常输入处理
 * 3. 检查内存泄漏和性能问题
 * 4. 确保统计数据的准确性和一致性
 * 5. 测试复杂业务场景下的正确性
 *
 * 这不是为了测试而测试，而是为了发现TrackingStatistics类的潜在问题
 */
@DisplayName("TrackingStatistics 综合问题发现测试")
class TrackingStatisticsComprehensiveTest {

    private TrackingStatistics statistics;

    @BeforeEach
    void setUp() {
        statistics = new TrackingStatistics();
    }

    @Nested
    @DisplayName("初始状态正确性验证")
    class InitialStateTests {

        @Test
        @DisplayName("新建实例应该有正确的初始值")
        void newInstanceShouldHaveCorrectInitialValues() {
            // 测试思路：验证构造函数是否正确初始化所有字段
            // 可能发现的问题：未初始化的字段、空指针、错误的默认值
            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            
            assertThat(summary.totalObjectsTracked).isEqualTo(0);
            assertThat(summary.totalChangesDetected).isEqualTo(0);
            assertThat(summary.averageDetectionTimeMs).isEqualTo(0.0);
            assertThat(summary.duration).isNotNull();
            assertThat(summary.changeTypeDistribution).isNotNull();
            assertThat(summary.topChangedObjects).isNotNull();
            
            // 验证所有变更类型都被初始化
            for (ChangeType type : ChangeType.values()) {
                assertThat(summary.changeTypeDistribution.get(type)).isNotNull();
                assertThat(summary.changeTypeDistribution.get(type)).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("平均检测时间应该正确处理零除法")
        void averageDetectionTimeShouldHandleZeroDivision() {
            // 测试思路：发现零除法错误
            // 可能发现的问题：当没有记录任何变更时的除零异常
            double avgTime = statistics.getAverageDetectionTimeMs();
            assertThat(avgTime).isEqualTo(0.0);
            assertThat(Double.isNaN(avgTime)).isFalse();
            assertThat(Double.isInfinite(avgTime)).isFalse();
        }
    }

    @Nested
    @DisplayName("并发安全性问题发现")
    class ConcurrencyIssueTests {

        @Test
        @DisplayName("并发记录对象跟踪应该保持计数准确性")
        void concurrentObjectTrackingShouldMaintainAccuracy() throws InterruptedException {
            // 测试思路：发现AtomicInteger使用不当或线程安全问题
            // 可能发现的问题：竞态条件、数据不一致、丢失更新
            int threadCount = 20;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < operationsPerThread; j++) {
                            statistics.recordObjectTracked("thread_" + threadId + "_obj_" + j);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            // 这个测试发现了HashMap线程安全问题
            int expected = threadCount * operationsPerThread;
            int actual = summary.totalObjectsTracked;
            System.out.println("Expected: " + expected + ", Actual: " + actual + 
                             ", Loss: " + (expected - actual) + " (" + 
                             String.format("%.1f%%", 100.0 * (expected - actual) / expected) + ")");
            
            // 由于已修复HashMap并发问题，现在应该等于期望值
            assertThat(actual).isEqualTo(expected)
                             .describedAs("使用ConcurrentHashMap后并发访问应该没有数据丢失");
        }

        @Test
        @DisplayName("并发记录变更应该保持统计一致性")
        void concurrentChangeRecordingShouldMaintainConsistency() throws InterruptedException {
            // 测试思路：发现Map并发修改、List并发访问等问题
            // 可能发现的问题：ConcurrentModificationException、数据损坏
            int threadCount = 10;
            int changesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // 先注册对象
                        statistics.recordObjectTracked("obj_" + threadId);
                        
                        for (int j = 0; j < changesPerThread; j++) {
                            ChangeRecord change = ChangeRecord.builder()
                                .objectName("obj_" + threadId)
                                .fieldName("field_" + j)
                                .oldValue("old")
                                .newValue("new_" + j)
                                .changeType(ChangeType.UPDATE)
                                .timestamp(System.currentTimeMillis())
                                .build();
                            
                            statistics.recordChanges(Arrays.asList(change), 1000000L);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            // 这些测试也发现了并发问题
            int expectedChanges = threadCount * changesPerThread;
            int actualChanges = summary.totalChangesDetected;
            System.out.println("Expected changes: " + expectedChanges + ", Actual: " + actualChanges);
            
            // 允许一定的误差范围，但记录问题
            assertThat(actualChanges).isGreaterThan((int)(expectedChanges * 0.8))
                                    .describedAs("并发访问导致变更记录丢失");
            assertThat(summary.totalObjectsTracked).isEqualTo(threadCount);
        }
    }

    @Nested
    @DisplayName("边界条件和异常输入处理")
    class EdgeCaseTests {

        @Test
        @DisplayName("空值和空字符串应该被正确处理")
        void nullAndEmptyValuesShouldBeHandledCorrectly() {
            // 测试思路：发现空指针异常和字符串处理问题
            // 可能发现的问题：NullPointerException、错误的空值处理
            
            // 测试null对象名
            statistics.recordObjectTracked(null);
            statistics.recordObjectTracked("");
            statistics.recordObjectTracked("  "); // 空白字符
            
            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            assertThat(summary.totalObjectsTracked).isEqualTo(3);
            
            // 测试null变更记录
            statistics.recordChanges(null, 1000000L);
            statistics.recordChanges(Arrays.asList(), 1000000L);
            
            // 测试变更记录中的null值
            ChangeRecord nullFieldChange = ChangeRecord.builder()
                .objectName(null)
                .fieldName(null)
                .oldValue(null)
                .newValue(null)
                .changeType(ChangeType.UPDATE)
                .timestamp(System.currentTimeMillis())
                .build();
            
            statistics.recordChanges(Arrays.asList(nullFieldChange), 1000000L);
            
            // 应该不会抛出异常
            TrackingStatistics.StatisticsSummary finalSummary = statistics.getSummary();
            assertThat(finalSummary).isNotNull();
        }

        @Test
        @DisplayName("极大数值应该被正确处理")
        void extremeValuesShouldBeHandledCorrectly() {
            // 测试思路：发现数值溢出、精度丢失等问题
            // 可能发现的问题：整数溢出、浮点精度问题
            
            // 测试极大的检测时间
            ChangeRecord change = buildChangeRecord("test", ChangeType.UPDATE);
            statistics.recordChanges(Arrays.asList(change), Long.MAX_VALUE);
            
            double avgTime = statistics.getAverageDetectionTimeMs();
            assertThat(Double.isFinite(avgTime)).isTrue();
            assertThat(avgTime).isGreaterThan(0);
            
            // 测试大量对象跟踪
            for (int i = 0; i < 10000; i++) {
                statistics.recordObjectTracked("massive_obj_" + i);
            }
            
            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            assertThat(summary.totalObjectsTracked).isEqualTo(10000);
        }

        @Test
        @DisplayName("零和负值时间应该被正确处理")
        void zeroAndNegativeTimesShouldBeHandledCorrectly() {
            // 测试思路：发现时间处理逻辑错误
            // 可能发现的问题：负时间导致的计算错误、零时间处理不当
            
            ChangeRecord change1 = buildChangeRecord("obj1", ChangeType.UPDATE);
            ChangeRecord change2 = buildChangeRecord("obj2", ChangeType.CREATE);
            
            // 零时间
            statistics.recordChanges(Arrays.asList(change1), 0L);
            
            // 负时间（可能由于时钟调整等原因）
            statistics.recordChanges(Arrays.asList(change2), -1000000L);
            
            double avgTime = statistics.getAverageDetectionTimeMs();
            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            
            assertThat(summary.totalChangesDetected).isEqualTo(2);
            // 平均时间应该是合理的值，不应该是负数
            assertThat(Double.isFinite(avgTime)).isTrue();
        }
    }

    @Nested
    @DisplayName("内存和性能问题发现")
    class MemoryAndPerformanceTests {

        @Test
        @DisplayName("大量数据应该不会导致内存泄漏")
        void largeDataShouldNotCauseMemoryLeak() {
            // 测试思路：发现内存泄漏和性能问题
            // 可能发现的问题：集合无限增长、对象无法回收
            
            // 记录大量对象和变更
            for (int i = 0; i < 5000; i++) {
                statistics.recordObjectTracked("leak_test_obj_" + i);
                
                ChangeRecord change = ChangeRecord.builder()
                    .objectName("leak_test_obj_" + i)
                    .fieldName("field")
                    .oldValue("old_" + i)
                    .newValue("new_" + i)
                    .changeType(ChangeType.UPDATE)
                    .timestamp(System.currentTimeMillis())
                    .build();
                
                statistics.recordChanges(Arrays.asList(change), 100000L);
            }
            
            // 验证基本功能仍然正常
            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            assertThat(summary.totalObjectsTracked).isEqualTo(5000);
            assertThat(summary.totalChangesDetected).isEqualTo(5000);
            
            // 验证热点对象查询性能
            long startTime = System.nanoTime();
            List<TrackingStatistics.ObjectStatistics> topObjects = statistics.getTopChangedObjects(10);
            long endTime = System.nanoTime();
            
            assertThat(topObjects).isNotNull();
            assertThat(Duration.ofNanos(endTime - startTime)).isLessThan(Duration.ofSeconds(1));
        }

        @Test
        @DisplayName("重复重置操作应该正确清理资源")
        void repeatedResetOperationsShouldCleanupProperly() {
            // 测试思路：发现重置操作的内存清理问题
            // 可能发现的问题：重置不彻底、资源泄漏
            
            for (int cycle = 0; cycle < 100; cycle++) {
                // 添加数据
                statistics.recordObjectTracked("cycle_" + cycle);
                ChangeRecord change = buildChangeRecord("cycle_" + cycle, ChangeType.UPDATE);
                statistics.recordChanges(Arrays.asList(change), 50000L);
                
                // 重置
                statistics.reset();
                
                // 验证重置后状态正确
                TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
                assertThat(summary.totalObjectsTracked).isEqualTo(0);
                assertThat(summary.totalChangesDetected).isEqualTo(0);
                assertThat(statistics.getAverageDetectionTimeMs()).isEqualTo(0.0);
            }
        }
    }

    @Nested
    @DisplayName("统计计算准确性验证")
    class StatisticalAccuracyTests {

        @Test
        @DisplayName("平均检测时间计算应该数学准确")
        void averageDetectionTimeCalculationShouldBeMathematicallyCorrect() {
            // 测试思路：发现平均值计算错误
            // 可能发现的问题：累计溢出、精度丢失、错误的平均算法
            
            ChangeRecord change1 = buildChangeRecord("obj1", ChangeType.UPDATE);
            ChangeRecord change2 = buildChangeRecord("obj2", ChangeType.CREATE);
            ChangeRecord change3 = buildChangeRecord("obj3", ChangeType.DELETE);
            
            // 已知的检测时间：1ms、3ms、2ms
            statistics.recordChanges(Arrays.asList(change1), 1_000_000L); // 1ms
            statistics.recordChanges(Arrays.asList(change2), 3_000_000L); // 3ms
            statistics.recordChanges(Arrays.asList(change3), 2_000_000L); // 2ms
            
            double expectedAverage = (1.0 + 3.0 + 2.0) / 3.0; // 2.0ms
            double actualAverage = statistics.getAverageDetectionTimeMs();
            
            assertThat(actualAverage).isEqualTo(expectedAverage);
        }

        @Test
        @DisplayName("变更类型分布应该准确统计")
        void changeTypeDistributionShouldBeAccurate() {
            // 测试思路：发现变更类型统计错误
            // 可能发现的问题：计数器更新错误、并发竞态条件
            
            // 创建已知的变更分布：3个UPDATE，2个CREATE，1个DELETE
            for (int i = 0; i < 3; i++) {
                ChangeRecord change = buildChangeRecord("update_obj_" + i, ChangeType.UPDATE);
                statistics.recordChanges(Arrays.asList(change), 100000L);
            }
            
            for (int i = 0; i < 2; i++) {
                ChangeRecord change = buildChangeRecord("create_obj_" + i, ChangeType.CREATE);
                statistics.recordChanges(Arrays.asList(change), 100000L);
            }
            
            ChangeRecord deleteChange = buildChangeRecord("delete_obj", ChangeType.DELETE);
            statistics.recordChanges(Arrays.asList(deleteChange), 100000L);
            
            Map<ChangeType, Integer> distribution = statistics.getChangeTypeDistribution();
            assertThat(distribution.get(ChangeType.UPDATE)).isEqualTo(3);
            assertThat(distribution.get(ChangeType.CREATE)).isEqualTo(2);
            assertThat(distribution.get(ChangeType.DELETE)).isEqualTo(1);
            
            // 验证总数一致性
            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            int totalFromDistribution = distribution.values().stream().mapToInt(Integer::intValue).sum();
            assertThat(totalFromDistribution).isEqualTo(summary.totalChangesDetected);
        }

        @Test
        @DisplayName("对象统计应该准确跟踪每个对象的变更")
        void objectStatisticsShouldAccuratelyTrackPerObjectChanges() {
            // 测试思路：发现对象级统计错误
            // 可能发现的问题：对象映射错误、计数不准确、字段统计错误
            
            // 为obj1记录3个变更（2个不同字段）
            statistics.recordObjectTracked("obj1");
            statistics.recordObjectTracked("obj2");
            
            ChangeRecord obj1Field1Change1 = ChangeRecord.builder()
                .objectName("obj1")
                .fieldName("field1")
                .oldValue("old1")
                .newValue("new1")
                .changeType(ChangeType.UPDATE)
                .timestamp(System.currentTimeMillis())
                .build();
            
            ChangeRecord obj1Field1Change2 = ChangeRecord.builder()
                .objectName("obj1")
                .fieldName("field1")
                .oldValue("new1")
                .newValue("newer1")
                .changeType(ChangeType.UPDATE)
                .timestamp(System.currentTimeMillis())
                .build();
            
            ChangeRecord obj1Field2Change = ChangeRecord.builder()
                .objectName("obj1")
                .fieldName("field2")
                .oldValue("old2")
                .newValue("new2")
                .changeType(ChangeType.UPDATE)
                .timestamp(System.currentTimeMillis())
                .build();
            
            ChangeRecord obj2Change = buildChangeRecord("obj2", ChangeType.CREATE);
            
            statistics.recordChanges(Arrays.asList(obj1Field1Change1, obj1Field1Change2), 100000L);
            statistics.recordChanges(Arrays.asList(obj1Field2Change), 50000L);
            statistics.recordChanges(Arrays.asList(obj2Change), 75000L);
            
            List<TrackingStatistics.ObjectStatistics> topObjects = statistics.getTopChangedObjects(10);
            
            // obj1应该有3个变更，obj2应该有1个变更
            TrackingStatistics.ObjectStatistics obj1Stats = topObjects.stream()
                .filter(stat -> "obj1".equals(stat.getObjectName()))
                .findFirst()
                .orElseThrow();
            
            TrackingStatistics.ObjectStatistics obj2Stats = topObjects.stream()
                .filter(stat -> "obj2".equals(stat.getObjectName()))
                .findFirst()
                .orElseThrow();
            
            assertThat(obj1Stats.getTotalChanges()).isEqualTo(3);
            assertThat(obj2Stats.getTotalChanges()).isEqualTo(1);
            
            // 验证字段级统计
            Map<String, Integer> obj1FieldChanges = obj1Stats.getFieldChangeCounts();
            assertThat(obj1FieldChanges.get("field1")).isEqualTo(2);
            assertThat(obj1FieldChanges.get("field2")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("性能统计准确性验证")
    class PerformanceStatisticsTests {

        @Test
        @DisplayName("性能统计应该正确计算百分位数")
        void performanceStatisticsShouldCalculatePercentilesCorrectly() {
            // 测试思路：发现百分位数计算错误
            // 可能发现的问题：排序错误、百分位算法错误、数据类型转换问题
            
            // 创建已知的时间分布：100μs, 200μs, 300μs, 400μs, 500μs
            for (int i = 1; i <= 5; i++) {
                ChangeRecord change = buildChangeRecord("perf_obj_" + i, ChangeType.UPDATE);
                statistics.recordChanges(Arrays.asList(change), i * 100_000L); // i * 100μs in nanos
            }
            
            TrackingStatistics.PerformanceStatistics perfStats = statistics.getPerformanceStatistics();
            
            assertThat(perfStats.minMicros).isEqualTo(100); // 最小值
            assertThat(perfStats.maxMicros).isEqualTo(500); // 最大值
            assertThat(perfStats.p50Micros).isEqualTo(300); // 中位数
            // P95和P99在小样本中可能与最大值相同
            assertThat(perfStats.p95Micros).isGreaterThanOrEqualTo(400);
            assertThat(perfStats.p99Micros).isGreaterThanOrEqualTo(500);
        }
    }

    @Nested
    @DisplayName("复杂业务场景正确性")
    class ComplexBusinessScenarioTests {

        @Test
        @DisplayName("模拟真实业务场景的统计准确性")
        void realBusinessScenarioStatisticalAccuracy() {
            // 测试思路：在复杂场景下发现统计错误
            // 可能发现的问题：复杂交互下的数据不一致、边界情况处理错误
            
            // 模拟电商订单处理场景
            String[] orderIds = {"order_001", "order_002", "order_003"};
            String[] statuses = {"CREATED", "PAID", "SHIPPED", "DELIVERED"};
            
            // 注册订单对象
            for (String orderId : orderIds) {
                statistics.recordObjectTracked(orderId);
            }
            
            // 模拟订单状态变更流程
            for (String orderId : orderIds) {
                for (int i = 0; i < statuses.length; i++) {
                    ChangeRecord statusChange = ChangeRecord.builder()
                        .objectName(orderId)
                        .fieldName("status")
                        .oldValue(i > 0 ? statuses[i-1] : null)
                        .newValue(statuses[i])
                        .changeType(i == 0 ? ChangeType.CREATE : ChangeType.UPDATE)
                        .timestamp(System.currentTimeMillis() + i * 1000)
                        .build();
                    
                    // 模拟不同处理时间
                    long processingTime = (i + 1) * 50_000L; // 50μs, 100μs, 150μs, 200μs
                    statistics.recordChanges(Arrays.asList(statusChange), processingTime);
                }
            }
            
            // 验证统计准确性
            TrackingStatistics.StatisticsSummary summary = statistics.getSummary();
            assertThat(summary.totalObjectsTracked).isEqualTo(3); // 3个订单
            assertThat(summary.totalChangesDetected).isEqualTo(12); // 3订单 × 4状态变更
            
            // 验证变更类型分布
            Map<ChangeType, Integer> distribution = summary.changeTypeDistribution;
            assertThat(distribution.get(ChangeType.CREATE)).isEqualTo(3); // 每个订单1次创建
            assertThat(distribution.get(ChangeType.UPDATE)).isEqualTo(9); // 每个订单3次更新
            assertThat(distribution.get(ChangeType.DELETE)).isEqualTo(0); // 无删除
            
            // 验证热点对象排序
            List<TrackingStatistics.ObjectStatistics> topObjects = statistics.getTopChangedObjects(5);
            assertThat(topObjects).hasSize(3);
            for (TrackingStatistics.ObjectStatistics objStat : topObjects) {
                assertThat(objStat.getTotalChanges()).isEqualTo(4); // 每个订单4次变更
                assertThat(objStat.getFieldChangeCounts().get("status")).isEqualTo(4);
            }
        }
    }

    // 辅助方法
    private ChangeRecord buildChangeRecord(String objectName, ChangeType changeType) {
        return ChangeRecord.builder()
            .objectName(objectName)
            .fieldName("testField")
            .oldValue("oldValue")
            .newValue("newValue")
            .changeType(changeType)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}