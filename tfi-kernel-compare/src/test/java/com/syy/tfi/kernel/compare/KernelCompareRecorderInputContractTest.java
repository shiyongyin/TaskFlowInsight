package com.syy.tfi.kernel.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.model.RecordType;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 验证 bridge 在任何 Compare 或 Kernel 副作用前完成输入校验和容量短路。 */
class KernelCompareRecorderInputContractTest {

    @Test
    void zeroCapacitySkipsCompareAndRecord() {
        CountingOperations operations = new CountingOperations(CompareResult.identical());
        TestStage stage = new TestStage(0, true);

        CompareRecordResult result = recorder(operations, KernelCompareRecordPolicy.defaults())
                .compareAndRecord(stage, "order.update", null, null);

        assertThat(result).isEqualTo(new CompareRecordResult(
                CompareRecordStatus.SKIPPED_NO_RECORDING_CAPACITY,
                Optional.empty(), 0, 0));
        assertThat(operations.calls).hasValue(0);
        assertThat(stage.recordCalls).hasValue(0);
    }

    @Test
    void compareExecutesOnceAndRejectedSummaryReturnsExecutedNotRecorded() {
        CompareResult comparison = CompareResult.ofNullDiff(null, "after");
        CountingOperations operations = new CountingOperations(comparison);
        TestStage stage = new TestStage(4_096, false);

        CompareRecordResult result = recorder(operations, KernelCompareRecordPolicy.defaults())
                .compareAndRecord(stage, "order.update", null, "after");

        assertThat(operations.calls).hasValue(1);
        assertThat(stage.recordCalls).hasValue(1);
        assertThat(result.status()).isEqualTo(CompareRecordStatus.EXECUTED_NOT_RECORDED);
        assertThat(result.compareResult()).containsSame(comparison);
        assertThat(result.availableChanges()).isEqualTo(1);
        assertThat(result.recordedChanges()).isZero();
    }

    @Test
    void recordWithExistingResultNeverReturnsSkipped() {
        CompareResult comparison = CompareResult.identical();
        TestStage stage = new TestStage(0, false);

        CompareRecordResult result = recorder(
                new CountingOperations(comparison), KernelCompareRecordPolicy.defaults())
                .record(stage, "order.update", comparison);

        assertThat(result.status()).isEqualTo(CompareRecordStatus.EXECUTED_NOT_RECORDED);
        assertThat(result.compareResult()).containsSame(comparison);
        assertThat(stage.recordCalls).hasValue(1);
    }

    @Test
    void operationIsTrimmedButNeverCaseNormalized() {
        CountingOperations operations = new CountingOperations(CompareResult.identical());
        TestStage stage = new TestStage(4_096, true);
        KernelCompareRecorder recorder = recorder(operations, KernelCompareRecordPolicy.defaults());

        recorder.compareAndRecord(stage, "  order.update  ", null, null);

        assertThat(stage.lastData.get("operation")).isEqualTo("order.update");
        for (String invalid : new String[]{"", " ", "Order.update", "order/update", "a".repeat(129)}) {
            int comparisonsBefore = operations.calls.get();
            int recordsBefore = stage.recordCalls.get();
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> recorder.compareAndRecord(stage, invalid, null, null))
                    .withMessageStartingWith("KCS_E_1201");
            assertThat(operations.calls).hasValue(comparisonsBefore);
            assertThat(stage.recordCalls).hasValue(recordsBefore);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> recorder.record(stage, invalid, CompareResult.identical()))
                    .withMessageStartingWith("KCS_E_1201");
            assertThat(operations.calls).hasValue(comparisonsBefore);
            assertThat(stage.recordCalls).hasValue(recordsBefore);
        }
    }

    @Test
    void requiredMethodArgumentsUseFixedNullNames() {
        KernelCompareRecorder recorder = recorder(
                new CountingOperations(CompareResult.identical()), KernelCompareRecordPolicy.defaults());
        TestStage stage = new TestStage(4_096, true);

        assertThatNullPointerException()
                .isThrownBy(() -> recorder.compareAndRecord(null, "order.update", null, null))
                .withMessage("stage");
        assertThatNullPointerException()
                .isThrownBy(() -> recorder.compareAndRecord(stage, null, null, null))
                .withMessage("operation");
        assertThatNullPointerException()
                .isThrownBy(() -> recorder.record(null, "order.update", CompareResult.identical()))
                .withMessage("stage");
        assertThatNullPointerException()
                .isThrownBy(() -> recorder.record(stage, null, CompareResult.identical()))
                .withMessage("operation");
        assertThatNullPointerException()
                .isThrownBy(() -> recorder.record(stage, "order.update", null))
                .withMessage("result");
    }

    @Test
    void constructorDependenciesUseFixedNullNames() {
        CompareOperations operations = new CountingOperations(CompareResult.identical());
        CompareProjectionFactory projections = new CompareProjectionFactory();
        MaskingPolicy masking = MaskingPolicy.safeDefaults();
        KernelCompareRecordPolicy policy = KernelCompareRecordPolicy.defaults();

        assertThatNullPointerException()
                .isThrownBy(() -> new KernelCompareRecorder(null, projections, masking, policy))
                .withMessage("compareOperations");
        assertThatNullPointerException()
                .isThrownBy(() -> new KernelCompareRecorder(operations, null, masking, policy))
                .withMessage("projectionFactory");
        assertThatNullPointerException()
                .isThrownBy(() -> new KernelCompareRecorder(operations, projections, null, policy))
                .withMessage("maskingPolicy");
        assertThatNullPointerException()
                .isThrownBy(() -> new KernelCompareRecorder(operations, projections, masking, null))
                .withMessage("recordPolicy");
    }

    @Test
    void recordPolicyAcceptsOnlyTheClosedDetailRange() {
        assertThat(KernelCompareRecordPolicy.defaults().maxRecordedChanges()).isZero();
        assertThat(new KernelCompareRecordPolicy(0).maxRecordedChanges()).isZero();
        assertThat(new KernelCompareRecordPolicy(32).maxRecordedChanges()).isEqualTo(32);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KernelCompareRecordPolicy(-1))
                .withMessageStartingWith("KCS_E_1201");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KernelCompareRecordPolicy(33))
                .withMessageStartingWith("KCS_E_1201");
    }

    @Test
    void recordResultEnforcesSkippedAndExecutedInvariants() {
        CompareResult comparison = CompareResult.identical();
        CompareResult different = CompareResult.ofNullDiff(null, "after");

        assertThatNullPointerException().isThrownBy(() -> new CompareRecordResult(
                null, Optional.of(comparison), 0, 0)).withMessage("status");
        assertThatNullPointerException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.RECORDED_SUMMARY, null, 0, 0)).withMessage("compareResult");
        assertThatIllegalArgumentException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.SKIPPED_NO_RECORDING_CAPACITY,
                Optional.of(comparison), 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.SKIPPED_NO_RECORDING_CAPACITY,
                Optional.empty(), 1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.RECORDED_SUMMARY,
                Optional.empty(), 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.RECORDED_SUMMARY,
                Optional.of(comparison), 1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.RECORDED_SUMMARY,
                Optional.of(comparison), 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.RECORDED_SUMMARY,
                Optional.of(comparison), 0, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompareRecordResult(
                CompareRecordStatus.RECORDED_DETAILS,
                Optional.of(different), 1, 0));

        assertThat(new CompareRecordResult(
                CompareRecordStatus.RECORDED_SUMMARY,
                Optional.of(different), 1, 0)).isNotNull();
        assertThat(new CompareRecordResult(
                CompareRecordStatus.RECORDED_DETAILS,
                Optional.of(different), 1, 1)).isNotNull();
    }

    private static KernelCompareRecorder recorder(
            CompareOperations operations, KernelCompareRecordPolicy policy) {
        return new KernelCompareRecorder(
                operations, new CompareProjectionFactory(), MaskingPolicy.safeDefaults(), policy);
    }

    private static final class CountingOperations implements CompareOperations {
        private final AtomicInteger calls = new AtomicInteger();
        private final CompareResult result;

        private CountingOperations(CompareResult result) {
            this.result = result;
        }

        @Override
        public CompareResult compare(Object before, Object after) {
            calls.incrementAndGet();
            return result;
        }

        @Override
        public CompareResult compare(Object before, Object after, CompareOptions options) {
            calls.incrementAndGet();
            return result;
        }
    }

    private static final class TestStage implements Stage {
        private final int remaining;
        private final boolean accepts;
        private final AtomicInteger recordCalls = new AtomicInteger();
        private Map<String, ?> lastData = Map.of();

        private TestStage(int remaining, boolean accepts) {
            this.remaining = remaining;
            this.accepts = accepts;
        }

        @Override public Stage attr(String key, Object value) { return this; }
        @Override public void message(String text) { }
        @Override public void change(String path, Object before, Object after) { }
        @Override public void error(String text) { }
        @Override public void error(String text, Throwable error) { }

        @Override
        public boolean record(RecordType type, String code, String text, Map<String, ?> data) {
            recordCalls.incrementAndGet();
            lastData = data;
            return accepts;
        }

        @Override public int remainingEncodedBytes() { return remaining; }
        @Override public void close() { }
    }
}
