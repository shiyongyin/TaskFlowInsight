package com.syy.tfi.kernel.compare;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.model.RecordType;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证 detail 只消费一次 canonical projection，并保留确定的接纳前缀。 */
class KernelCompareDetailContractTest {

    @Test
    void projectionCallCountFollowsDetailDemand() throws ReflectiveOperationException {
        CompareResult summaryOnly = different(List.of(change("only", "before", "after")));
        CountingChangeList summaryChanges = replaceWithCountingList(summaryOnly);
        RecordingStage summaryStage = new RecordingStage(-1);

        CompareRecordResult summary = recorder(summaryOnly, 0).record(summaryStage, "order.update", summaryOnly);

        assertThat(summary.status()).isEqualTo(CompareRecordStatus.RECORDED_SUMMARY);
        assertThat(summaryChanges.iterations).isZero();

        CompareResult withDetails = different(List.of(change("only", "before", "after")));
        CountingChangeList detailChanges = replaceWithCountingList(withDetails);
        CompareRecordResult details = recorder(withDetails, 1)
                .record(new RecordingStage(-1), "order.update", withDetails);

        assertThat(details.status()).isEqualTo(CompareRecordStatus.RECORDED_DETAILS);
        assertThat(detailChanges.iterations).isEqualTo(1);
        assertThat(recorder(CompareResult.identical(), 1)
                .record(new RecordingStage(-1), "order.read", CompareResult.identical()).status())
                .isEqualTo(CompareRecordStatus.RECORDED_SUMMARY);
    }

    @Test
    void detailsUseCanonicalOrderAndFrozenRecordSchema() {
        CompareResult result = different(List.of(
                change("zeta", "z-before", "z-after"),
                change("alpha", false, true),
                change("middle", 1, 2)));
        RecordingStage stage = new RecordingStage(-1);

        CompareRecordResult recorded = recorder(result, 3).record(stage, "order.update", result);

        assertThat(recorded.status()).isEqualTo(CompareRecordStatus.RECORDED_DETAILS);
        assertThat(recorded.recordedChanges()).isEqualTo(3);
        assertThat(stage.attempts).hasSize(4);
        CapturedRecord summary = stage.attempts.getFirst();
        assertThat(summary.type).isEqualTo(RecordType.MESSAGE);
        assertThat(summary.code).isEqualTo("KCOMPARE_SUMMARY_V1");
        assertThat(summary.text).isNull();
        assertThat(summary.data)
                .containsEntry("plannedDetailCount", 3)
                .containsEntry("detailState", "READY");
        assertThat(stage.attempts.subList(1, 4))
                .extracting(record -> propertyName(record.data))
                .containsExactly("alpha", "middle", "zeta");
        for (int index = 0; index < 3; index++) {
            CapturedRecord detail = stage.attempts.get(index + 1);
            assertThat(detail.type).isEqualTo(RecordType.CHANGE);
            assertThat(detail.code).isEqualTo("KCOMPARE_CHANGE_V1");
            assertThat(detail.text).isNull();
            assertThat(detail.data.keySet())
                    .containsExactly("schemaVersion", "operation", "changeIndex", "change");
            assertThat(detail.data).containsEntry("changeIndex", index);
        }
    }

    @Test
    void limitsAndFirstKernelRejectionKeepOnlyTheCanonicalPrefix() {
        CompareResult result = different(List.of(
                change("a", 0, 1), change("b", 1, 2), change("c", 2, 3)));

        RecordingStage limitedStage = new RecordingStage(-1);
        CompareRecordResult limited = recorder(result, 2).record(limitedStage, "order.update", result);
        assertThat(limited.status()).isEqualTo(CompareRecordStatus.RECORDED_PARTIAL_DETAILS);
        assertThat(limited.recordedChanges()).isEqualTo(2);
        assertThat(limitedStage.attempts).hasSize(3);

        RecordingStage rejectedStage = new RecordingStage(3);
        CompareRecordResult rejected = recorder(result, 3).record(rejectedStage, "order.update", result);
        assertThat(rejected.status()).isEqualTo(CompareRecordStatus.RECORDED_PARTIAL_DETAILS);
        assertThat(rejected.recordedChanges()).isEqualTo(1);
        assertThat(rejectedStage.attempts).hasSize(3);
    }

    @Test
    void rejectedSummaryStopsBeforeAnyDetailRecord() {
        CompareResult result = different(List.of(change("value", "before", "after")));
        RecordingStage stage = new RecordingStage(1);

        CompareRecordResult recorded = recorder(result, 1).record(stage, "order.update", result);

        assertThat(recorded.status()).isEqualTo(CompareRecordStatus.EXECUTED_NOT_RECORDED);
        assertThat(recorded.recordedChanges()).isZero();
        assertThat(stage.attempts).singleElement().satisfies(summary -> assertThat(summary.data)
                .containsEntry("plannedDetailCount", 1)
                .containsEntry("detailState", "READY"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void converterPreservesEveryProjectionKindAsJdkStructuredData() {
        CompareResult result = different(List.of(change("enabled", false, true)));
        CompareProjection projection = new CompareProjectionFactory().create(
                result, ProjectionMetadata.empty(), MaskingPolicy.safeDefaults(), ProjectionOptions.defaults());

        Map<String, Object> root = (Map<String, Object>) ProjectionNodeDataConverter.convert(projection.root());

        assertThat(root).isInstanceOf(LinkedHashMap.class).containsEntry("schemaVersion", 1);
        assertThat(root.get("changes")).isInstanceOf(ArrayList.class);
        Map<String, Object> diagnostics = (Map<String, Object>) root.get("diagnostics");
        assertThat(diagnostics).containsEntry("rootAlgorithmId", null);
        List<Map<String, Object>> changes = (List<Map<String, Object>>) root.get("changes");
        Map<String, Object> after = (Map<String, Object>) changes.getFirst().get("after");
        Map<String, Object> value = (Map<String, Object>) after.get("value");
        assertThat(value).containsEntry("value", true);
    }

    private static KernelCompareRecorder recorder(CompareResult result, int limit) {
        return new KernelCompareRecorder(
                new FixedOperations(result), new CompareProjectionFactory(),
                MaskingPolicy.safeDefaults(), new KernelCompareRecordPolicy(limit));
    }

    private static CompareResult different(List<FieldChange> changes) {
        return CompareResult.canonical(
                CompareOutcome.DIFFERENT, CompareCompletion.COMPLETE, changes,
                List.of(), List.of(), CompareDiagnostics.empty(), Optional.empty());
    }

    private static FieldChange change(String property, Object before, Object after) {
        ComparePath path = ComparePath.root().append(new PropertySegment(property));
        return FieldChange.at(ChangeKind.MODIFY, path, before, after);
    }

    private static CountingChangeList replaceWithCountingList(CompareResult result)
            throws ReflectiveOperationException {
        CountingChangeList changes = new CountingChangeList(result.getChanges());
        Field field = CompareResult.class.getDeclaredField("changes");
        field.setAccessible(true);
        field.set(result, changes);
        return changes;
    }

    @SuppressWarnings("unchecked")
    private static String propertyName(Map<String, Object> detail) {
        Map<String, Object> change = (Map<String, Object>) detail.get("change");
        Map<String, Object> after = (Map<String, Object>) change.get("after");
        List<Map<String, Object>> path = (List<Map<String, Object>>) after.get("path");
        return (String) path.getFirst().get("name");
    }

    private record FixedOperations(CompareResult result) implements CompareOperations {
        @Override public CompareResult compare(Object before, Object after) { return result; }
        @Override public CompareResult compare(Object before, Object after, CompareOptions options) { return result; }
    }

    private static final class CountingChangeList extends AbstractList<FieldChange> {
        private final List<FieldChange> delegate;
        /** 真实 factory 复制源列表的次数；size 查询不计入。 */
        private int iterations;

        private CountingChangeList(List<FieldChange> delegate) { this.delegate = delegate; }
        @Override public FieldChange get(int index) { return delegate.get(index); }
        @Override public int size() { return delegate.size(); }
        @Override public Iterator<FieldChange> iterator() {
            iterations++;
            return delegate.iterator();
        }
    }

    private record CapturedRecord(RecordType type, String code, String text, Map<String, Object> data) { }

    private static final class RecordingStage implements Stage {
        private final int rejectAtAttempt;
        private final List<CapturedRecord> attempts = new ArrayList<>();

        private RecordingStage(int rejectAtAttempt) { this.rejectAtAttempt = rejectAtAttempt; }
        @Override public Stage attr(String key, Object value) { return this; }
        @Override public void message(String text) { }
        @Override public void change(String path, Object before, Object after) { }
        @Override public void error(String text) { }
        @Override public void error(String text, Throwable error) { }
        @Override public boolean record(RecordType type, String code, String text, Map<String, ?> data) {
            LinkedHashMap<String, Object> copiedData = new LinkedHashMap<>();
            data.forEach(copiedData::put);
            attempts.add(new CapturedRecord(type, code, text, copiedData));
            return attempts.size() != rejectAtAttempt;
        }
        @Override public int remainingEncodedBytes() { return 65_536; }
        @Override public void close() { }
    }
}
