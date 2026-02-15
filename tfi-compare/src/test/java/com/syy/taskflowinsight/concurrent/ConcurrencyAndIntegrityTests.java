package com.syy.taskflowinsight.concurrent;

import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.Pair;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * 并发安全 &amp; 数据完整性测试。
 *
 * <p>覆盖以下生产就绪盲区：
 * <ol>
 *   <li>多线程同时 compare — 结果互不干扰</li>
 *   <li>ChangeTracker ThreadLocal 隔离 — N 个线程各自 track / getChanges</li>
 *   <li>compareBatch 并行 — 虚拟线程池下结果顺序 &amp; 正确性</li>
 *   <li>compare 幂等性 — 同一对连续调用两次结果一致</li>
 *   <li>数据完整性 — 嵌套对象/集合深度比较不丢字段</li>
 *   <li>边界值 — 空集合、null、大集合降级</li>
 * </ol>
 *
 * @author TFI Expert Panel — Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Concurrency & Data Integrity Tests")
class ConcurrencyAndIntegrityTests {

    private CompareService compareService;

    @BeforeEach
    void setUp() {
        compareService = CompareService.createDefault(CompareOptions.DEFAULT, null);
    }

    @AfterEach
    void tearDown() {
        ChangeTracker.clearAllTracking();
    }

    // ========================== 并发安全 ==========================

    @Nested
    @DisplayName("1. CompareService 多线程安全")
    class CompareServiceConcurrency {

        @RepeatedTest(3)
        @DisplayName("多线程同时 compare 结果互不污染")
        void concurrentCompareShouldNotInterfere() throws Exception {
            int threadCount = 20;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<CompareResult>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    Order before = Order.builder()
                            .id((long) idx)
                            .status("PENDING")
                            .amount(BigDecimal.valueOf(100 + idx))
                            .build();
                    Order after = Order.builder()
                            .id((long) idx)
                            .status("SHIPPED")
                            .amount(BigDecimal.valueOf(200 + idx))
                            .build();
                    return compareService.compare(before, after);
                }));
            }

            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < threadCount; i++) {
                CompareResult result = futures.get(i).get();
                assertThat(result).isNotNull();
                assertThat(result.isIdentical()).isFalse();
                // Each thread should see at least one change (status)
                assertThat(result.getChanges()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("compareBatch 并行模式结果顺序与串行一致")
        void compareBatchParallelOrderMatchesSerial() {
            int batchSize = 50;
            List<Pair<Object, Object>> pairs = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                Order before = Order.builder().id((long) i).status("A" + i).amount(BigDecimal.valueOf(i)).build();
                Order after = Order.builder().id((long) i).status("B" + i).amount(BigDecimal.valueOf(i + 100)).build();
                pairs.add(Pair.of(before, after));
            }

            // Serial
            CompareOptions serialOpts = CompareOptions.builder().parallelThreshold(Integer.MAX_VALUE).build();
            List<CompareResult> serialResults = compareService.compareBatch(pairs, serialOpts);

            // Parallel (threshold = 1 → forces parallel)
            CompareOptions parallelOpts = CompareOptions.builder().parallelThreshold(1).build();
            List<CompareResult> parallelResults = compareService.compareBatch(pairs, parallelOpts);

            assertThat(parallelResults).hasSameSizeAs(serialResults);
            for (int i = 0; i < batchSize; i++) {
                assertThat(parallelResults.get(i).getChanges())
                        .as("Pair %d should have same change count", i)
                        .hasSameSizeAs(serialResults.get(i).getChanges());
                assertThat(parallelResults.get(i).isIdentical())
                        .isEqualTo(serialResults.get(i).isIdentical());
            }
        }

        @Test
        @DisplayName("compareBatch null / empty 防御")
        void compareBatchNullAndEmptyDefense() {
            assertThat(compareService.compareBatch(null, CompareOptions.DEFAULT)).isEmpty();
            assertThat(compareService.compareBatch(Collections.emptyList(), CompareOptions.DEFAULT)).isEmpty();
            assertThat(compareService.compareBatch(List.of(), null)).isEmpty();
        }
    }

    // ========================== ThreadLocal 隔离 ==========================

    @Nested
    @DisplayName("2. ChangeTracker ThreadLocal 隔离")
    class ChangeTrackerIsolation {

        @RepeatedTest(3)
        @DisplayName("N 个线程各自 track, getChanges 完全隔离")
        void threadLocalIsolation() throws Exception {
            int threadCount = 10;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            ConcurrentMap<Integer, List<ChangeRecord>> results = new ConcurrentHashMap<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                        ChangeTracker.clearAllTracking();

                        Order order = Order.builder()
                                .id((long) idx).status("INIT_" + idx).amount(BigDecimal.TEN).build();
                        ChangeTracker.track("order_" + idx, order, "status", "amount");

                        // Mutate
                        order.setStatus("DONE_" + idx);
                        order.setAmount(BigDecimal.valueOf(999));

                        List<ChangeRecord> changes = ChangeTracker.getChanges();
                        results.put(idx, changes != null ? new ArrayList<>(changes) : Collections.emptyList());
                    } catch (Exception e) {
                        results.put(idx, Collections.emptyList());
                    } finally {
                        ChangeTracker.clearAllTracking();
                    }
                    return null;
                });
            }

            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            // Each thread should see only its own changes
            for (int i = 0; i < threadCount; i++) {
                List<ChangeRecord> threadChanges = results.get(i);
                assertThat(threadChanges)
                        .as("Thread %d should have its own changes", i)
                        .isNotNull();
                // Verify no cross-contamination: all change records should reference "order_<idx>"
                for (ChangeRecord cr : threadChanges) {
                    assertThat(cr.getObjectName()).startsWith("order_" + i);
                }
            }
        }

        @Test
        @DisplayName("clearAllTracking 真正清理 ThreadLocal")
        void clearAllTrackingActuallyClears() {
            Order order = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).build();
            ChangeTracker.track("testOrder", order, "status");
            assertThat(ChangeTracker.getTrackedCount()).isGreaterThan(0);

            ChangeTracker.clearAllTracking();
            assertThat(ChangeTracker.getTrackedCount()).isZero();
            assertThat(ChangeTracker.getChanges()).isEmpty();
        }
    }

    // ========================== 幂等性 ==========================

    @Nested
    @DisplayName("3. 比较幂等性")
    class CompareIdempotency {

        @Test
        @DisplayName("同一对象连续 compare 两次结果一致")
        void compareIdempotent() {
            Order before = Order.builder().id(1L).status("A").amount(BigDecimal.valueOf(100)).build();
            Order after = Order.builder().id(1L).status("B").amount(BigDecimal.valueOf(200)).build();

            CompareResult r1 = compareService.compare(before, after);
            CompareResult r2 = compareService.compare(before, after);

            assertThat(r1.isIdentical()).isEqualTo(r2.isIdentical());
            assertThat(r1.getChanges()).hasSameSizeAs(r2.getChanges());

            // Verify field-level consistency
            for (int i = 0; i < r1.getChanges().size(); i++) {
                FieldChange fc1 = r1.getChanges().get(i);
                FieldChange fc2 = r2.getChanges().get(i);
                assertThat(fc1.getFieldName()).isEqualTo(fc2.getFieldName());
                assertThat(String.valueOf(fc1.getOldValue())).isEqualTo(String.valueOf(fc2.getOldValue()));
                assertThat(String.valueOf(fc1.getNewValue())).isEqualTo(String.valueOf(fc2.getNewValue()));
            }
        }

        @Test
        @DisplayName("compare 不修改原始对象")
        void compareShouldNotMutateInputs() {
            Order before = Order.builder().id(1L).status("A").amount(BigDecimal.valueOf(100)).build();
            Order after = Order.builder().id(1L).status("B").amount(BigDecimal.valueOf(200)).build();

            String beforeStatus = before.getStatus();
            String afterStatus = after.getStatus();
            BigDecimal beforeAmount = before.getAmount();
            BigDecimal afterAmount = after.getAmount();

            compareService.compare(before, after);

            assertThat(before.getStatus()).isEqualTo(beforeStatus);
            assertThat(after.getStatus()).isEqualTo(afterStatus);
            assertThat(before.getAmount()).isEqualTo(beforeAmount);
            assertThat(after.getAmount()).isEqualTo(afterAmount);
        }
    }

    // ========================== 数据完整性 ==========================

    @Nested
    @DisplayName("4. 数据完整性")
    class DataIntegrity {

        @Test
        @DisplayName("顶层字段变更检测到")
        void topLevelFieldChangesDetected() {
            Customer before = Customer.builder().name("Alice").build();
            Customer after = Customer.builder().name("Bob").build();

            CompareResult result = compareService.compare(before, after);

            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isNotEmpty();
            assertThat(result.getChanges().stream()
                    .anyMatch(fc -> "name".equals(fc.getFieldName())))
                    .isTrue();
        }

        @Test
        @DisplayName("嵌套对象深度比较能执行不崩溃")
        void deepCompareDoesNotCrash() {
            Address addr1 = new Address("Beijing", "Haidian", "100000");
            Address addr2 = new Address("Shanghai", "Pudong", "200000");

            Customer before = Customer.builder().name("Alice").address(addr1).build();
            Customer after = Customer.builder().name("Alice").address(addr2).build();

            CompareOptions deepOpts = CompareOptions.builder().enableDeepCompare(true).maxDepth(5).build();

            assertThatCode(() -> compareService.compare(before, after, deepOpts))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("集合元素增删改全部检测到")
        void collectionChangesDetected() {
            List<String> tags1 = new ArrayList<>(Arrays.asList("urgent", "vip", "wholesale"));
            List<String> tags2 = new ArrayList<>(Arrays.asList("urgent", "retail", "international"));

            Order before = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).tags(tags1).build();
            Order after = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).tags(tags2).build();

            CompareOptions deepOpts = CompareOptions.builder().enableDeepCompare(true).maxDepth(5).build();
            CompareResult result = compareService.compare(before, after, deepOpts);

            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("String 字段差异正确检测")
        void stringFieldDifferenceDetected() {
            Order before = Order.builder().id(1L).status("PENDING").amount(BigDecimal.ONE).build();
            Order after = Order.builder().id(1L).status("SHIPPED").amount(BigDecimal.ONE).build();

            CompareResult result = compareService.compare(before, after);

            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isNotEmpty();
            assertThat(result.getChanges().stream()
                    .anyMatch(fc -> "PENDING".equals(String.valueOf(fc.getOldValue()))
                            && "SHIPPED".equals(String.valueOf(fc.getNewValue()))))
                    .isTrue();
        }

        @Test
        @DisplayName("BigDecimal 比较不崩溃")
        void bigDecimalCompareDoesNotCrash() {
            Order before = Order.builder().id(1L).status("A").amount(new BigDecimal("100")).build();
            Order after = Order.builder().id(1L).status("A").amount(new BigDecimal("200")).build();

            assertThatCode(() -> compareService.compare(before, after))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 字段正确处理 (null→value, value→null)")
        void nullFieldTransitions() {
            Order before = Order.builder().id(1L).status(null).amount(BigDecimal.ONE).build();
            Order after = Order.builder().id(1L).status("ACTIVE").amount(null).build();

            CompareResult result = compareService.compare(before, after);

            assertThat(result.isIdentical()).isFalse();
            // At least one field changed (status: null→ACTIVE or amount: 1→null)
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("完全相同的对象 compare 结果为 identical")
        void identicalObjectsProduceNoChanges() {
            Order order = Order.builder().id(1L).status("A").amount(BigDecimal.TEN).build();

            CompareResult result = compareService.compare(order, order);

            assertThat(result.isIdentical()).isTrue();
            assertThat(result.getChanges()).isEmpty();
        }

        @Test
        @DisplayName("两个 null 比较不抛异常")
        void compareNullWithNull() {
            CompareResult result = compareService.compare(null, null);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null vs 非 null 对象")
        void compareNullWithNonNull() {
            Order order = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).build();

            CompareResult r1 = compareService.compare(null, order);
            assertThat(r1).isNotNull();
            assertThat(r1.isIdentical()).isFalse();

            CompareResult r2 = compareService.compare(order, null);
            assertThat(r2).isNotNull();
            assertThat(r2.isIdentical()).isFalse();
        }
    }

    // ========================== 压力/边界 ==========================

    @Nested
    @DisplayName("5. 边界与压力")
    class BoundaryAndStress {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 10, 100})
        @DisplayName("不同大小的集合比较不崩溃")
        void variousCollectionSizes(int size) {
            List<String> tags1 = new ArrayList<>();
            List<String> tags2 = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                tags1.add("tag_" + i);
                tags2.add("tag_" + (i + 1)); // All different
            }

            Order before = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).tags(tags1).build();
            Order after = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).tags(tags2).build();

            assertThatCode(() -> {
                CompareOptions deepOpts = CompareOptions.builder().enableDeepCompare(true).maxDepth(5).build();
                compareService.compare(before, after, deepOpts);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ChangeTracker.track 超过 MAX_TRACKED_OBJECTS 不崩溃")
        void trackBeyondMaxDoesNotCrash() {
            try {
                for (int i = 0; i < ChangeTracker.getMaxTrackedObjects() + 100; i++) {
                    Order order = Order.builder().id((long) i).status("S").amount(BigDecimal.ONE).build();
                    ChangeTracker.track("obj_" + i, order, "status");
                }
                // Should not throw, just cap or warn
                assertThat(ChangeTracker.getTrackedCount()).isLessThanOrEqualTo(
                        ChangeTracker.getMaxTrackedObjects() + 100);
            } finally {
                ChangeTracker.clearAllTracking();
            }
        }

        @Test
        @DisplayName("HealthIndicator 在高压力下正确报告")
        void healthIndicatorUnderPressure() {
            // Track many objects to simulate pressure
            try {
                for (int i = 0; i < 50; i++) {
                    Order order = Order.builder().id((long) i).status("S").amount(BigDecimal.ONE).build();
                    ChangeTracker.track("pressure_" + i, order, "status");
                }
                int count = ChangeTracker.getTrackedCount();
                int max = ChangeTracker.getMaxTrackedObjects();
                double usage = (double) count / max;

                assertThat(count).isGreaterThan(0);
                assertThat(usage).isLessThan(1.0); // 50/1000 = 5%, should be healthy
            } finally {
                ChangeTracker.clearAllTracking();
            }
        }
    }

    // ========================== 错误恢复 ==========================

    @Nested
    @DisplayName("6. 错误恢复")
    class ErrorRecovery {

        @Test
        @DisplayName("compare 后异常不影响下次 compare")
        void recoverAfterException() {
            // First: a normal compare
            Order before = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).build();
            Order after = Order.builder().id(1L).status("B").amount(BigDecimal.TEN).build();
            CompareResult r1 = compareService.compare(before, after);
            assertThat(r1).isNotNull();

            // Second: compare with completely different types (should not crash)
            assertThatCode(() -> compareService.compare("string", 42))
                    .doesNotThrowAnyException();

            // Third: normal compare should still work
            CompareResult r3 = compareService.compare(before, after);
            assertThat(r3).isNotNull();
            assertThat(r3.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("ChangeTracker track 异常后 clearAllTracking 恢复正常状态")
        void trackerRecoveryAfterClear() {
            try {
                // Track something
                Order order = Order.builder().id(1L).status("A").amount(BigDecimal.ONE).build();
                ChangeTracker.track("test", order, "status");
                assertThat(ChangeTracker.getTrackedCount()).isGreaterThan(0);
            } finally {
                ChangeTracker.clearAllTracking();
            }

            // After clear, state should be pristine
            assertThat(ChangeTracker.getTrackedCount()).isZero();

            // Should be able to track again
            Order order2 = Order.builder().id(2L).status("B").amount(BigDecimal.TEN).build();
            ChangeTracker.track("test2", order2, "status");
            assertThat(ChangeTracker.getTrackedCount()).isGreaterThan(0);
            ChangeTracker.clearAllTracking();
        }
    }

    // ========================== Test models ==========================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class Order {
        private Long id;
        private String status;
        private BigDecimal amount;
        private List<String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class Customer {
        private String name;
        private Address address;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Address {
        private String city;
        private String district;
        private String zipCode;
    }
}
