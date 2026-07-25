package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无序容器遍历、暂存和结果真值的请求容量合同。
 */
class CompareContainerCapacityContractTests {

    /** 单次比较两侧共享的容器成员消费预算。 */
    private static final int MAX_ELEMENTS = 4;

    /** 剩余预算加一个 overflow sentinel 后的单侧最大读取次数。 */
    private static final int PENDING_LIMIT = MAX_ELEMENTS + 1;

    @Test
    void oversizedSetStopsAtOverflowSentinelWithoutPublishingArbitraryChanges() {
        GuardedSet<Integer> before = GuardedSet.range(0, 100, PENDING_LIMIT);
        GuardedSet<Integer> after = GuardedSet.range(1_000, 101, PENDING_LIMIT);

        assertSetOverflow(before, after);
    }

    @Test
    void oversizedEntitySetIsBoundedBeforeIdentityPlanning() {
        GuardedSet<CapacityEntity> before = GuardedSet.entities(0, 100, false, PENDING_LIMIT);
        GuardedSet<CapacityEntity> after = GuardedSet.entities(1_000, 100, false, PENDING_LIMIT);

        assertSetOverflow(before, after);
    }

    @Test
    void oversizedAmbiguousEntitySetIsBoundedBeforeDuplicateGrouping() {
        GuardedSet<CapacityEntity> before = GuardedSet.entities(0, 100, true, PENDING_LIMIT);
        GuardedSet<CapacityEntity> after = GuardedSet.entities(1_000, 100, true, PENDING_LIMIT);

        assertSetOverflow(before, after);
    }

    @Test
    void oversizedMapStopsAtOverflowSentinelWithoutPublishingArbitraryChanges() {
        GuardedMap before = GuardedMap.range(0, 100, PENDING_LIMIT);
        GuardedMap after = GuardedMap.range(1_000, 101, PENDING_LIMIT);
        ComparePolicy policy = policy();

        CompareResult result = CompareRuntime.builder().policy(policy).build()
                .engine().compare(before, after, CompareOptions.defaults(policy));

        assertThat(before.nextCalls()).isEqualTo(PENDING_LIMIT);
        assertThat(after.nextCalls()).isEqualTo(PENDING_LIMIT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.getLimitations())
                .extracting(limitation -> limitation.code())
                .containsOnly(CompareLimitationCode.COLLECTION_LIMIT_REACHED);
        assertThat(result.getDiagnostics().consumedElements()).isLessThanOrEqualTo(MAX_ELEMENTS);
    }

    @Test
    void equalComplexSetDoesNotCompareCompleteFactsToOverflowedEmptyFacts() {
        GuardedSet<CapacityValue> before = GuardedSet.complexValues(MAX_ELEMENTS, PENDING_LIMIT);
        GuardedSet<CapacityValue> after = GuardedSet.complexValues(MAX_ELEMENTS, PENDING_LIMIT);
        ComparePolicy policy = policy();

        CompareResult result = CompareRuntime.builder().policy(policy).build()
                .engine().compare(before, after, CompareOptions.defaults(policy));

        assertThat(before.nextCalls()).isEqualTo(MAX_ELEMENTS);
        assertThat(after.nextCalls()).isEqualTo(1);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.getLimitations())
                .extracting(limitation -> limitation.code())
                .containsOnly(CompareLimitationCode.COLLECTION_LIMIT_REACHED);
    }

    @Test
    void deadlineDuringSetStagingPublishesNoContainerFacts() {
        AtomicLong nanoTime = new AtomicLong();
        CompareOptions options = CompareOptions.builder()
                .maxElements(10)
                .deadline(Duration.ofNanos(2))
                .build();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4), nanoTime::get);
        ClockAdvancingSet values = new ClockAdvancingSet(nanoTime);

        SnapshotResult snapshot = RequestLocalSnapshot.capture(values, options, state);

        assertThat(values.nextCalls()).isEqualTo(2);
        assertThat(snapshot.values()).isEmpty();
        assertThat(snapshot.setSnapshots()).isEmpty();
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(snapshot.limitations())
                .extracting(limitation -> limitation.code())
                .containsOnly(CompareLimitationCode.DEADLINE_REACHED);
    }

    @Test
    void deadlineAfterSetPlanningDiscardsTheIncompleteContainerFacts() {
        PostStagingDeadlineClock nanoTime = new PostStagingDeadlineClock();
        CompareOptions options = deadlineOptions();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4), nanoTime);
        Set<CapacityValue> values = deadlineAfterStagingSet(nanoTime);

        SnapshotResult snapshot = RequestLocalSnapshot.capture(values, options, state);

        assertDeadlineDiscarded(snapshot);
        assertThat(snapshot.setSnapshots()).isEmpty();
    }

    @Test
    void deadlineAfterMapPlanningDiscardsTheIncompleteContainerFacts() {
        PostStagingDeadlineClock nanoTime = new PostStagingDeadlineClock();
        CompareOptions options = deadlineOptions();
        CompareRequestState state = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4), nanoTime);
        Map<Integer, Integer> values = deadlineAfterStagingMap(nanoTime);

        SnapshotResult snapshot = RequestLocalSnapshot.capture(values, options, state);

        assertDeadlineDiscarded(snapshot);
    }

    private static CompareOptions deadlineOptions() {
        return CompareOptions.builder()
                .maxElements(MAX_ELEMENTS)
                .deadline(Duration.ofNanos(10))
                .build();
    }

    private static void assertDeadlineDiscarded(SnapshotResult snapshot) {
        assertThat(snapshot.values()).isEmpty();
        assertThat(snapshot.completion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(snapshot.limitations())
                .extracting(limitation -> limitation.code())
                .containsOnly(CompareLimitationCode.DEADLINE_REACHED);
    }

    private static Set<CapacityValue> deadlineAfterStagingSet(
            PostStagingDeadlineClock nanoTime) {
        return new AbstractSet<>() {
            @Override
            public Iterator<CapacityValue> iterator() {
                return armAfterExhaustion(
                        Set.of(new CapacityValue(1)).iterator(), nanoTime);
            }

            @Override
            public int size() {
                return 1;
            }
        };
    }

    private static Map<Integer, Integer> deadlineAfterStagingMap(
            PostStagingDeadlineClock nanoTime) {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<Integer, Integer>> entrySet() {
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<Integer, Integer>> iterator() {
                        return armAfterExhaustion(
                                Map.of(1, 1).entrySet().iterator(), nanoTime);
                    }

                    @Override
                    public int size() {
                        return 1;
                    }
                };
            }
        };
    }

    private static <T> Iterator<T> armAfterExhaustion(
            Iterator<T> delegate,
            PostStagingDeadlineClock nanoTime) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                boolean hasNext = delegate.hasNext();
                if (!hasNext) {
                    nanoTime.arm();
                }
                return hasNext;
            }

            @Override
            public T next() {
                return delegate.next();
            }
        };
    }

    private static ComparePolicy policy() {
        return ComparePolicy.builder()
                .maxElements(MAX_ELEMENTS)
                .build();
    }

    private static <T> void assertSetOverflow(GuardedSet<T> before, GuardedSet<T> after) {
        ComparePolicy policy = policy();
        CompareResult result = CompareRuntime.builder().policy(policy).build()
                .engine().compare(before, after, CompareOptions.defaults(policy));

        assertThat(before.nextCalls()).isEqualTo(PENDING_LIMIT);
        assertThat(after.nextCalls()).isEqualTo(PENDING_LIMIT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.getLimitations())
                .extracting(limitation -> limitation.code())
                .containsOnly(CompareLimitationCode.COLLECTION_LIMIT_REACHED);
        assertThat(result.getDiagnostics().consumedElements()).isLessThanOrEqualTo(MAX_ELEMENTS);
    }

    /** Set 实际读取超过上界时立即失败，避免测试被全量遍历的实现假绿。 */
    private static final class GuardedSet<T> extends AbstractSet<T> {

        /** 受控迭代的数据；顺序只用于复现，不参与超限结果语义。 */
        private final Set<T> values;

        /** 本实例允许的 iterator next 调用次数。 */
        private final int maxNextCalls;

        /** 已成功完成的 iterator next 调用次数。 */
        private int nextCalls;

        private GuardedSet(Set<T> values, int maxNextCalls) {
            this.values = Set.copyOf(values);
            this.maxNextCalls = maxNextCalls;
        }

        private static GuardedSet<Integer> range(int start, int count, int maxNextCalls) {
            Set<Integer> values = new LinkedHashSet<>();
            for (int offset = 0; offset < count; offset++) {
                values.add(start + offset);
            }
            return new GuardedSet<>(values, maxNextCalls);
        }

        private static GuardedSet<CapacityEntity> entities(
                int start,
                int count,
                boolean duplicateIdentities,
                int maxNextCalls) {
            Set<CapacityEntity> values = new LinkedHashSet<>();
            for (int offset = 0; offset < count; offset++) {
                int id = duplicateIdentities ? start + offset / 2 : start + offset;
                values.add(new CapacityEntity(id, "member-" + offset));
            }
            return new GuardedSet<>(values, maxNextCalls);
        }

        private static GuardedSet<CapacityValue> complexValues(int count, int maxNextCalls) {
            Set<CapacityValue> values = new LinkedHashSet<>();
            for (int offset = 0; offset < count; offset++) {
                values.add(new CapacityValue(offset));
            }
            return new GuardedSet<>(values, maxNextCalls);
        }

        @Override
        public Iterator<T> iterator() {
            Iterator<T> delegate = values.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public T next() {
                    if (nextCalls == maxNextCalls) {
                        throw new IllegalStateException("guarded Set exceeded iterator budget");
                    }
                    nextCalls++;
                    return delegate.next();
                }
            };
        }

        @Override
        public int size() {
            return values.size();
        }

        private int nextCalls() {
            return nextCalls;
        }
    }

    /** 容量测试中的 Entity；重复 id 必须在完整 staging 后才能解释为 ambiguity。 */
    @Entity
    private static final class CapacityEntity {

        /** Set 成员的 canonical identity。 */
        @Key
        private final int id;

        /** identity 配对后才允许读取的业务内容。 */
        private final String name;

        private CapacityEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** 无 Entity 标注的复杂 Set 成员，按字段事实而非对象 identity 比较。 */
    private static final class CapacityValue {

        /** 两侧成员应形成相同 canonical signature 的业务值。 */
        private final int value;

        private CapacityValue(int value) {
            this.value = value;
        }
    }

    /** 每次读取后推进测试时钟，用于稳定触发 staging 中途 deadline。 */
    private static final class ClockAdvancingSet extends AbstractSet<Integer> {

        /** staging 与请求状态共享的可控单调时钟，单位为纳秒。 */
        private final AtomicLong nanoTime;

        /** 已完成的成员读取次数。 */
        private int nextCalls;

        private ClockAdvancingSet(AtomicLong nanoTime) {
            this.nanoTime = nanoTime;
        }

        @Override
        public Iterator<Integer> iterator() {
            Iterator<Integer> delegate = Set.of(1, 2, 3, 4).iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public Integer next() {
                    Integer value = delegate.next();
                    nextCalls++;
                    nanoTime.incrementAndGet();
                    return value;
                }
            };
        }

        @Override
        public int size() {
            return 4;
        }

        private int nextCalls() {
            return nextCalls;
        }
    }

    /** staging 完成后的首次检查仍有效，下一次检查稳定跨过 deadline。 */
    private static final class PostStagingDeadlineClock implements LongSupplier {

        /** false 表示 iterator 尚未证明 staging 已完整读取。 */
        private boolean armed;

        /** staging 完成后已发生的 deadline 检查次数。 */
        private int readsAfterStaging;

        private void arm() {
            armed = true;
        }

        @Override
        public long getAsLong() {
            return armed && readsAfterStaging++ > 0 ? 10L : 0L;
        }
    }

    /** Map entry 实际读取超过上界时立即失败，验证 entry 暂存受同一预算约束。 */
    private static final class GuardedMap extends AbstractMap<Integer, Integer> {

        /** 受控迭代的 entry 数据；key/value 都只用于触发超限路径。 */
        private final Map<Integer, Integer> values;

        /** 本实例允许的 entry iterator next 调用次数。 */
        private final int maxNextCalls;

        /** 已成功完成的 entry iterator next 调用次数。 */
        private int nextCalls;

        private GuardedMap(Map<Integer, Integer> values, int maxNextCalls) {
            this.values = Map.copyOf(values);
            this.maxNextCalls = maxNextCalls;
        }

        private static GuardedMap range(int start, int count, int maxNextCalls) {
            Map<Integer, Integer> values = new LinkedHashMap<>();
            for (int offset = 0; offset < count; offset++) {
                values.put(start + offset, offset);
            }
            return new GuardedMap(values, maxNextCalls);
        }

        @Override
        public Set<Entry<Integer, Integer>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<Integer, Integer>> iterator() {
                    Iterator<Entry<Integer, Integer>> delegate = values.entrySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return delegate.hasNext();
                        }

                        @Override
                        public Entry<Integer, Integer> next() {
                            if (nextCalls == maxNextCalls) {
                                throw new IllegalStateException("guarded Map exceeded iterator budget");
                            }
                            nextCalls++;
                            return delegate.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return values.size();
                }
            };
        }

        private int nextCalls() {
            return nextCalls;
        }
    }
}
