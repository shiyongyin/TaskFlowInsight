package com.syy.tfi.kernel.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.RecordType;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证 projection/mapping 故障只降低 detail 可观测性，不改写 Compare 真值。 */
class KernelCompareFailureContractTest {

    private static final String STACK_CLASS_CANARY = "com.example.secret.StackTraceCanary";
    private static final String STACK_METHOD_CANARY = "secretStackMethodCanary";

    @Test
    void ordinaryProjectionFailureRecordsFailedSummaryWithoutThrowableData()
            throws ReflectiveOperationException {
        RuntimeException failure = new IllegalStateException(
                "throwable-message-canary", new IllegalArgumentException("throwable-cause-canary"));
        failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(
                        STACK_CLASS_CANARY, STACK_METHOD_CANARY, "SecretStackFile.java", 73)
        });
        CompareResult result = resultWhoseProjectionThrows(failure);
        List<FlowSession> sessions = new ArrayList<>();
        KernelConfig config = new KernelConfig(
                true, List.of(sessions::add), Sampler.always(), () -> "failure-session",
                FixedClock.INSTANCE, 64, 12_288, 2_048, 32);

        CompareRecordResult recorded;
        try (KernelRuntime runtime = KernelRuntime.create(config);
             Stage stage = runtime.begin("projection-failure")) {
            recorded = recorder(result).record(stage, "order.update", result);
        }

        assertThat(recorded.status()).isEqualTo(CompareRecordStatus.RECORDED_DETAIL_FAILURE);
        assertThat(recorded.compareResult()).containsSame(result);
        assertThat(recorded.availableChanges()).isEqualTo(1);
        assertThat(recorded.recordedChanges()).isZero();
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.root().records()).singleElement().satisfies(summary -> assertThat(summary.data())
                    .containsEntry("plannedDetailCount", 1)
                    .containsEntry("detailState", "FAILED"));
            assertThat(Tfi.toJson(session))
                    .contains("KCOMPARE_SUMMARY_V1", "FAILED")
                    .doesNotContain(
                            "throwable-message-canary",
                            "throwable-cause-canary",
                            STACK_CLASS_CANARY,
                            STACK_METHOD_CANARY);
        });
    }

    @Test
    void nonFatalErrorAlsoDegradesToFailedSummary() throws ReflectiveOperationException {
        AssertionError failure = new AssertionError("non-fatal-error-canary");
        CompareResult result = resultWhoseProjectionThrows(failure);
        RecordingStage stage = new RecordingStage();

        CompareRecordResult recorded = recorder(result).record(stage, "order.update", result);

        assertThat(recorded.status()).isEqualTo(CompareRecordStatus.RECORDED_DETAIL_FAILURE);
        assertThat(stage.records).singleElement().satisfies(summary -> assertThat(summary.data)
                .containsEntry("detailState", "FAILED"));
        assertThat(stage.records.toString()).doesNotContain("non-fatal-error-canary");
    }

    @Test
    @SuppressWarnings("removal")
    void fatalProjectionErrorsPropagateTheSameInstance() throws ReflectiveOperationException {
        for (Error fatal : List.of(new OutOfMemoryError("vm"), new ThreadDeath(), new LinkageError("link"))) {
            CompareResult result = resultWhoseProjectionThrows(fatal);
            RecordingStage stage = new RecordingStage();

            Throwable actual = catchThrowable(() -> recorder(result).record(stage, "order.update", result));

            assertThat(actual).isSameAs(fatal);
            assertThat(stage.records).isEmpty();
        }
    }

    private static KernelCompareRecorder recorder(CompareResult result) {
        return new KernelCompareRecorder(
                new FixedOperations(result), new CompareProjectionFactory(),
                MaskingPolicy.safeDefaults(), new KernelCompareRecordPolicy(1));
    }

    private static CompareResult resultWhoseProjectionThrows(Throwable failure)
            throws ReflectiveOperationException {
        ComparePath path = ComparePath.root().append(new PropertySegment("value"));
        FieldChange change = FieldChange.at(ChangeKind.MODIFY, path, "before", "after");
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT, CompareCompletion.COMPLETE, List.of(change),
                List.of(), List.of(), CompareDiagnostics.empty(), Optional.empty());
        Field field = CompareResult.class.getDeclaredField("changes");
        field.setAccessible(true);
        field.set(result, new FailingChangeList(change, failure));
        return result;
    }

    private record FixedOperations(CompareResult result) implements CompareOperations {
        @Override public CompareResult compare(Object before, Object after) { return result; }
        @Override public CompareResult compare(Object before, Object after, CompareOptions options) { return result; }
    }

    private static final class FailingChangeList extends AbstractList<FieldChange> {
        private final FieldChange change;
        private final Throwable failure;

        private FailingChangeList(FieldChange change, Throwable failure) {
            this.change = change;
            this.failure = failure;
        }

        @Override public FieldChange get(int index) { return change; }
        @Override public int size() { return 1; }
        @Override public Iterator<FieldChange> iterator() {
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw (Error) failure;
        }
    }

    private record CapturedRecord(RecordType type, String code, String text, Map<String, Object> data) { }

    private static final class RecordingStage implements Stage {
        private final List<CapturedRecord> records = new ArrayList<>();

        @Override public Stage attr(String key, Object value) { return this; }
        @Override public void message(String text) { }
        @Override public void change(String path, Object before, Object after) { }
        @Override public void error(String text) { }
        @Override public void error(String text, Throwable error) { }
        @Override public boolean record(RecordType type, String code, String text, Map<String, ?> data) {
            LinkedHashMap<String, Object> copiedData = new LinkedHashMap<>();
            data.forEach(copiedData::put);
            records.add(new CapturedRecord(type, code, text, copiedData));
            return true;
        }
        @Override public int remainingEncodedBytes() { return 65_536; }
        @Override public void close() { }
    }

    private enum FixedClock implements KernelClock {
        INSTANCE;

        @Override public long wallTimeMillis() { return 1_000L; }
        @Override public long monotonicNanos() { return 2_000L; }
    }
}
