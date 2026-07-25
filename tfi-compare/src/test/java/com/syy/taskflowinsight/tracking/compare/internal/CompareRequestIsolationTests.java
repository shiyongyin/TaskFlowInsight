package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.CompareStrategy;
import com.syy.taskflowinsight.tracking.compare.AlgorithmId;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.PropertySelector;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 单次比较状态隔离合同，防止预算与cycle标记跨请求或跨兄弟路径泄漏。 */
class CompareRequestIsolationTests {

    private static final String OVERLONG_FIELD =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void eachRequestOwnsItsBudgetAndActivePathIdentity() {
        CompareOptions options = CompareOptions.builder()
                .maxComparedNodes(1)
                .maxElements(1)
                .build();
        CompareRequestState first = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        CompareRequestState second = CompareRequestState.create(
                options, new CompareResultAccumulator(8, 4));
        AtomicInteger callbacks = new AtomicInteger();

        assertThat(first.admit(BudgetEvent.SNAPSHOT_NODE, callbacks::incrementAndGet)).isTrue();
        assertThat(first.admit(BudgetEvent.SNAPSHOT_NODE, callbacks::incrementAndGet)).isFalse();
        assertThat(second.admit(BudgetEvent.SNAPSHOT_NODE, callbacks::incrementAndGet)).isTrue();

        Object shared = new Object();
        assertThat(first.enterActivePath(shared)).isTrue();
        assertThat(first.enterActivePath(shared)).isFalse();
        first.leaveActivePath(shared);
        assertThat(first.enterActivePath(shared)).isTrue();
        assertThat(second.enterActivePath(shared)).isTrue();
        assertThat(callbacks).hasValue(2);
    }

    @Test
    void deadlineUsesMonotonicElapsedTimeAndExpiresAtTheExactBoundary() {
        CompareOptions options = CompareOptions.builder()
                .deadline(Duration.ofNanos(10))
                .build();
        AtomicLong nanoTime = new AtomicLong(100);
        CompareRequestState state = CompareRequestState.create(
                options,
                new CompareResultAccumulator(8, 4),
                nanoTime::get);

        assertThat(state.deadlineReached()).isFalse();
        nanoTime.set(109);
        assertThat(state.deadlineReached()).isFalse();
        nanoTime.set(110);
        assertThat(state.deadlineReached()).isTrue();
    }

    @Test
    void requestStatePublishesElapsedAndLedgerCounters() {
        CompareOptions options = CompareOptions.builder().build();
        AtomicLong nanoTime = new AtomicLong(100);
        CompareRequestState state = CompareRequestState.create(
                options,
                new CompareResultAccumulator(8, 4),
                nanoTime::get);

        state.admit(BudgetEvent.SNAPSHOT_NODE, () -> { });
        state.admit(BudgetEvent.SNAPSHOT_NODE, () -> { });
        state.admit(BudgetEvent.CONTAINER_MEMBER, () -> { });
        nanoTime.set(107);
        state.publishDiagnostics();
        CompareResult result = CompareResultReducer.reduce(state.accumulator());

        assertThat(result.getDiagnostics().durationNanos()).isEqualTo(7);
        assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(2);
        assertThat(result.getDiagnostics().consumedElements()).isEqualTo(1);
    }

    @Test
    void engineSharesOneLedgerAcrossBothSnapshotsAndDiff() {
        CompareRuntime runtime = CompareRuntime.builder().build();

        CompareResult result = runtime.engine().compare(new ScalarRoot(1), new ScalarRoot(2));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(6);
        assertThat(result.getDiagnostics().consumedElements()).isZero();
    }

    @Test
    void rootIdentityConsumesNoBudget() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        ScalarRoot shared = new ScalarRoot(1);

        CompareResult result = runtime.engine().compare(shared, shared);

        assertThat(result.getDiagnostics().comparedNodes()).isZero();
        assertThat(result.getDiagnostics().consumedElements()).isZero();
    }

    @Test
    void propertyComparatorRunsInsideTheSharedDiffLedger() {
        CompareRuntime runtime = CompareRuntime.builder()
                .registerComparator(
                        PropertySelector.of(ScalarRoot.class, "value"),
                        AlgorithmId.of("test:scalar-value:v1"),
                        (left, right, field) -> true)
                .build();

        CompareResult result = runtime.engine().compare(new ScalarRoot(1), new ScalarRoot(2));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(6);
    }

    @Test
    void propertyComparatorFailureDoesNotDiscardLaterSiblingDifference() {
        CompareRuntime runtime = CompareRuntime.builder()
                .registerComparator(
                        PropertySelector.of(ComparatorFailureRoot.class, "aCompared"),
                        AlgorithmId.of("test:failing-property:v1"),
                        (left, right, field) -> {
                            throw new IllegalStateException("expected comparator failure");
                        })
                .build();

        CompareResult result = runtime.engine().compare(
                new ComparatorFailureRoot(1, 1),
                new ComparatorFailureRoot(2, 2));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getProblems())
                .extracting(CompareProblem::code)
                .containsExactly(CompareProblemCode.DIFF_FAILED);
        assertThat(result.getChanges())
                .extracting(change -> change.before().orElseThrow().path())
                .containsExactly(ComparePath.root().append(new PropertySegment("zChanged")));
    }

    @Test
    void engineStopsBeforeTheFirstDiffNodeBeyondTheSharedLimit() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxComparedNodes(5)
                .build();

        CompareResult result = runtime.engine().compare(
                new ScalarRoot(1), new ScalarRoot(2), options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(5);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.NODE_BUDGET_REACHED);
    }

    @Test
    void equalLengthStringSummariesCannotProveEquality() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxResultValueChars(64)
                .build();

        CompareResult result = runtime.engine().compare(
                "a".repeat(65),
                "b".repeat(65),
                options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
    }

    @Test
    void differentStringSummariesStillProveACompleteDifference() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxResultValueChars(64)
                .build();

        CompareResult result = runtime.engine().compare(
                "a".repeat(65),
                "b".repeat(66),
                options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges()).hasSize(1);
        assertThat(result.getLimitations()).isEmpty();
    }

    @Test
    void overlongResultPathFallsBackToABoundedDifferenceAnchor() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxPathEncodedChars(64)
                .build();
        String longKey = "k".repeat(80);

        CompareResult result = runtime.engine().compare(
                Map.of(longKey, 1),
                Map.of(longKey, 2),
                options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(ChangeKind.MODIFY);
            assertThat(change.before().orElseThrow().path()).isEqualTo(ComparePath.root());
            assertThat(change.after().orElseThrow().path()).isEqualTo(ComparePath.root());
            assertThat(change.beforeValue().orElseThrow().representation())
                    .isEqualTo(ValueSnapshot.Representation.OMITTED);
            assertThat(change.afterValue().orElseThrow().representation())
                    .isEqualTo(ValueSnapshot.Representation.OMITTED);
        });
        assertThat(result.getDiagnostics().omittedPaths()).isEqualTo(1);
        assertThat(result.getDiagnostics().omittedChanges()).isEqualTo(1);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
    }

    @Test
    void requestResultFactsNeverExceedTheTotalCharacterBudget() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxResultTotalChars(65_536)
                .build();
        Map<String, String> before = new LinkedHashMap<>();
        Map<String, String> after = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            String key = "key-" + index;
            before.put(key, "a".repeat(4_000));
            after.put(key, "b".repeat(4_000));
        }

        CompareResult result = runtime.engine().compare(before, after, options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getChanges()).isNotEmpty().hasSizeLessThan(20);
        assertThat(result.getDiagnostics().retainedResultChars())
                .isPositive()
                .isLessThanOrEqualTo(options.maxResultTotalChars());
        assertThat(result.getDiagnostics().omittedChanges()).isPositive();
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
    }

    @Test
    void overlongProblemPathFallsBackToTheNearestBoundedAncestor() {
        CompareRuntime runtime = CompareRuntime.builder()
                .registerComparator(
                        PropertySelector.of(OverlongIssuePathRoot.class, OVERLONG_FIELD),
                        AlgorithmId.of("test:overlong-problem:v1"),
                        (left, right, field) -> {
                            throw new IllegalStateException("expected comparator failure");
                        })
                .build();
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxPathEncodedChars(64)
                .build();

        CompareResult result = runtime.engine().compare(
                new OverlongIssuePathRoot(1),
                new OverlongIssuePathRoot(2),
                options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.FAILED);
        assertThat(result.getProblems()).singleElement().satisfies(problem ->
                assertThat(problem.path()).contains(ComparePath.root()));
        assertThat(result.getDiagnostics().omittedPaths()).isEqualTo(1);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
    }

    @Test
    void delegatedResultDiagnosticsReflectTheFinalBoundedProjection() {
        CompareOptions options = CompareOptions.builder()
                .maxPathEncodedChars(64)
                .build();
        ComparePath overlongPath = ComparePath.root()
                .append(new PropertySegment("p".repeat(80)));

        CompareResult result = RequestLocalCompareKernel.executeDiff(
                options,
                () -> CompareResultReducer.complete(List.of(
                        FieldChange.at(ChangeKind.MODIFY, overlongPath, 1, 2))));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getDiagnostics().retainedResultChars()).isPositive();
        assertThat(result.getDiagnostics().omittedPaths()).isEqualTo(1);
        assertThat(result.getDiagnostics().omittedChanges()).isEqualTo(1);
    }

    @Test
    void incompleteAfterSnapshotCannotTurnMissingEvidenceIntoRemoval() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxElements(3)
                .build();

        CompareResult result = runtime.engine().compare(
                new ContainerRoot(List.of("a", "b")),
                new ContainerRoot(List.of("a", "b")),
                options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.getDiagnostics().consumedElements()).isEqualTo(3);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.COLLECTION_LIMIT_REACHED);
    }

    @Test
    void selectedCustomStrategyBelongsToOneDiffNode() {
        AtomicInteger callbacks = new AtomicInteger();
        CompareRuntime runtime = CompareRuntime.builder()
                .registerStrategy(
                        CustomValue.class,
                        AlgorithmId.of("test:custom-value:v1"),
                        new CustomValueStrategy(callbacks))
                .build();

        CompareResult result = runtime.engine().compare(new CustomValue(1), new CustomValue(2));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(1);
        assertThat(callbacks).hasValue(1);
    }

    @Test
    void builtInListSharesSnapshotAndElementBudgets() {
        CompareRuntime runtime = CompareRuntime.builder().build();

        CompareResult result = runtime.engine().compare(
                List.of("a", "b"),
                List.of("a", "c"));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(9);
        assertThat(result.getDiagnostics().consumedElements()).isEqualTo(4);
    }

    @Test
    void builtInMapSharesSnapshotAndEntryBudgets() {
        CompareRuntime runtime = CompareRuntime.builder().build();

        CompareResult result = runtime.engine().compare(
                Map.of("key", 1),
                Map.of("key", 2));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(6);
        assertThat(result.getDiagnostics().consumedElements()).isEqualTo(2);
    }

    @Test
    void concurrentCallsOnOneRuntimeDoNotShareRequestState() throws Exception {
        CompareRuntime runtime = CompareRuntime.builder().build();
        List<Callable<CompareResult>> tasks = IntStream.range(0, 32)
                .mapToObj(index -> (Callable<CompareResult>) () -> runtime.engine().compare(
                        new ScalarRoot(index), new ScalarRoot(index + 1)))
                .toList();

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<CompareResult>> futures = executor.invokeAll(tasks);
            for (Future<CompareResult> future : futures) {
                CompareResult result = future.get();
                assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
                assertThat(result.getChanges()).hasSize(1);
                assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(6);
                assertThat(result.getDiagnostics().consumedElements()).isZero();
            }
        }
    }

    /** 单字段对象让两侧snapshot和typed diff的消费点可精确计数。 */
    private static final class ScalarRoot {

        /** 唯一业务值；不同输入应在对应property diff node产生change。 */
        private final int value;

        private ScalarRoot(int value) {
            this.value = value;
        }
    }

    /** 容器包裹对象用于验证两侧snapshot共享同一element预算。 */
    private static final class ContainerRoot {

        /** 被比较的有序成员；预算耗尽后缺失事实不能解释成删除。 */
        private final List<String> values;

        private ContainerRoot(List<String> values) {
            this.values = values;
        }
    }

    /** comparator故障字段排在普通差异之前，用于证明单个扩展失败不会中止兄弟DIFF_NODE。 */
    private static final class ComparatorFailureRoot {

        /** 由注册comparator处理且确定抛错，禁止回退默认值比较。 */
        private final int aCompared;

        /** comparator故障后仍应被遍历并保留的确定差异。 */
        private final int zChanged;

        private ComparatorFailureRoot(int aCompared, int zChanged) {
            this.aCompared = aCompared;
            this.zChanged = zChanged;
        }
    }

    /** 字段名自身超过最小path预算，用于验证issue不能携带越界exact path。 */
    private static final class OverlongIssuePathRoot {

        /** 唯一业务值；其字段名与OVERLONG_FIELD保持一致。 */
        private final int aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;

        private OverlongIssuePathRoot(int value) {
            this.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa = value;
        }
    }

    /** exact custom strategy目标；其字段不应由built-in snapshot重复计费。 */
    private static final class CustomValue {

        /** 仅用于区分两个输入引用，custom strategy有权定义终局语义。 */
        private final int value;

        private CustomValue(int value) {
            this.value = value;
        }
    }

    /** 记录调用次数，证明Engine不会在预算探测或fallback中重复执行扩展。 */
    private static final class CustomValueStrategy implements CompareStrategy<CustomValue> {

        /** 当前测试请求累计的扩展回调次数。 */
        private final AtomicInteger callbacks;

        private CustomValueStrategy(AtomicInteger callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public CompareResult compare(
                CustomValue first,
                CustomValue second,
                CompareOptions options) {
            callbacks.incrementAndGet();
            return CompareResult.identical();
        }

        @Override
        public String getName() {
            return "custom-value";
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == CustomValue.class;
        }
    }
}
