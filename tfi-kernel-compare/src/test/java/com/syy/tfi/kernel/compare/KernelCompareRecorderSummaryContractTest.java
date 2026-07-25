package com.syy.tfi.kernel.compare;

import static org.assertj.core.api.Assertions.assertThat;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.AlgorithmId;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.SimilarityScore;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.model.RecordType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 验证 Compare Core 真值只映射为固定、有序且无业务明细的 summary。 */
class KernelCompareRecorderSummaryContractTest {

    private static final List<String> ALL_FIELDS = List.of(
            "schemaVersion", "operation", "outcome", "completion", "availableChangeCount",
            "problemCodeCounts", "limitationCodeCounts", "rootAlgorithmId", "appliedAlgorithmCount",
            "effectivePolicyFingerprint", "similarityAlgorithmId", "similarityValue", "durationNanos",
            "comparedNodes", "consumedElements", "retainedResultChars", "omittedPaths", "omittedChanges",
            "omittedProblems", "omittedLimitations", "configuredDetailLimit", "plannedDetailCount", "detailState");

    @Test
    void summaryUsesExactOrderedSchemaAndSortedCodeCounts() {
        RecordingStage stage = new RecordingStage(true, 4_096);
        CompareResult result = fullResult();

        CompareRecordResult recorded = recorder(KernelCompareRecordPolicy.defaults())
                .record(stage, " order.update ", result);

        assertThat(recorded.status()).isEqualTo(CompareRecordStatus.RECORDED_SUMMARY);
        assertThat(recorded.compareResult()).containsSame(result);
        assertThat(stage.type).isEqualTo(RecordType.MESSAGE);
        assertThat(stage.code).isEqualTo("KCOMPARE_SUMMARY_V1");
        assertThat(stage.text).isNull();
        assertThat(stage.data.keySet()).containsExactlyElementsOf(ALL_FIELDS);
        assertThat(stage.data)
                .containsEntry("schemaVersion", 1)
                .containsEntry("operation", "order.update")
                .containsEntry("outcome", "DIFFERENT")
                .containsEntry("completion", "COMPLETE")
                .containsEntry("availableChangeCount", 1)
                .containsEntry("rootAlgorithmId", "tfi:root:v1")
                .containsEntry("appliedAlgorithmCount", 2)
                .containsEntry("effectivePolicyFingerprint", "sha256-v1:" + "a".repeat(64))
                .containsEntry("similarityAlgorithmId", "tfi:similarity:v1")
                .containsEntry("similarityValue", 0.75)
                .containsEntry("durationNanos", 11L)
                .containsEntry("comparedNodes", 12L)
                .containsEntry("consumedElements", 13L)
                .containsEntry("retainedResultChars", 14L)
                .containsEntry("omittedPaths", 15L)
                .containsEntry("omittedChanges", 16L)
                .containsEntry("omittedProblems", 17L)
                .containsEntry("omittedLimitations", 18L)
                .containsEntry("configuredDetailLimit", 0)
                .containsEntry("plannedDetailCount", 0)
                .containsEntry("detailState", "NOT_REQUESTED");
        assertThat(countMap(stage.data.get("problemCodeCounts")))
                .containsExactly(
                        Map.entry("CMP_E_1101", 1),
                        Map.entry("CMP_E_2001", 2));
        assertThat(countMap(stage.data.get("limitationCodeCounts")))
                .containsExactly(
                        Map.entry("CMP_W_2102", 1),
                        Map.entry("CMP_W_2201", 2));
        assertThat(stage.data.toString()).doesNotContain("before-secret", "after-secret", "path", "message");
    }

    @Test
    void optionalFieldsAreAbsentTogetherWhenCoreDidNotProduceThem() {
        RecordingStage stage = new RecordingStage(true, 4_096);
        CompareResult withoutOptionals = CompareResult.canonical(
                CompareOutcome.EQUAL,
                CompareCompletion.COMPLETE,
                List.of(),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());

        recorder(KernelCompareRecordPolicy.defaults())
                .record(stage, "order.read", withoutOptionals);

        assertThat(stage.data.keySet()).containsExactly(
                "schemaVersion", "operation", "outcome", "completion", "availableChangeCount",
                "problemCodeCounts", "limitationCodeCounts", "appliedAlgorithmCount", "durationNanos",
                "comparedNodes", "consumedElements", "retainedResultChars", "omittedPaths", "omittedChanges",
                "omittedProblems", "omittedLimitations", "configuredDetailLimit", "plannedDetailCount",
                "detailState");
        assertThat(stage.data).doesNotContainKeys(
                "rootAlgorithmId",
                "effectivePolicyFingerprint",
                "similarityAlgorithmId",
                "similarityValue");
        assertThat(countMap(stage.data.get("problemCodeCounts"))).isEmpty();
        assertThat(countMap(stage.data.get("limitationCodeCounts"))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("legalTruths")
    void summaryPreservesEveryLegalOutcomeAndCompletion(
            CompareResult result, CompareOutcome outcome, CompareCompletion completion) {
        RecordingStage stage = new RecordingStage(true, 4_096);

        CompareRecordResult recorded = recorder(KernelCompareRecordPolicy.defaults())
                .record(stage, "truth.matrix", result);

        assertThat(recorded.status()).isEqualTo(CompareRecordStatus.RECORDED_SUMMARY);
        assertThat(stage.data)
                .containsEntry("outcome", outcome.name())
                .containsEntry("completion", completion.name());
    }

    private static Stream<Arguments> legalTruths() {
        FieldChange change = FieldChange.at(ChangeKind.MODIFY, ComparePath.root(), "a", "b");
        CompareLimitation limitation = limit(CompareLimitationCode.DEPTH_LIMIT_REACHED);
        CompareProblem problem = issue(CompareProblemCode.DIFF_FAILED);
        return Stream.of(
                Arguments.of(
                        CompareResult.identical(), CompareOutcome.EQUAL, CompareCompletion.COMPLETE),
                Arguments.of(
                        CompareResult.ofNullDiff(null, "value"),
                        CompareOutcome.DIFFERENT, CompareCompletion.COMPLETE),
                Arguments.of(
                        CompareResult.canonical(
                                CompareOutcome.DIFFERENT, CompareCompletion.PARTIAL,
                                List.of(change), List.of(), List.of(limitation),
                                CompareDiagnostics.empty(), Optional.empty()),
                        CompareOutcome.DIFFERENT, CompareCompletion.PARTIAL),
                Arguments.of(
                        CompareResult.canonical(
                                CompareOutcome.INDETERMINATE, CompareCompletion.PARTIAL,
                                List.of(), List.of(), List.of(limitation),
                                CompareDiagnostics.empty(), Optional.empty()),
                        CompareOutcome.INDETERMINATE, CompareCompletion.PARTIAL),
                Arguments.of(
                        CompareResult.canonical(
                                CompareOutcome.INDETERMINATE, CompareCompletion.FAILED,
                                List.of(), List.of(problem), List.of(),
                                CompareDiagnostics.empty(), Optional.empty()),
                        CompareOutcome.INDETERMINATE, CompareCompletion.FAILED),
                Arguments.of(
                        CompareResult.canonical(
                                CompareOutcome.INDETERMINATE, CompareCompletion.DISABLED,
                                List.of(), List.of(),
                                List.of(limit(CompareLimitationCode.POLICY_DISABLED)),
                                CompareDiagnostics.empty(), Optional.empty()),
                        CompareOutcome.INDETERMINATE, CompareCompletion.DISABLED));
    }

    private static KernelCompareRecorder recorder(KernelCompareRecordPolicy policy) {
        CompareOperations unused = new FixedOperations(CompareResult.identical());
        return new KernelCompareRecorder(
                unused, new CompareProjectionFactory(), MaskingPolicy.safeDefaults(), policy);
    }

    private static CompareResult fullResult() {
        AlgorithmId root = AlgorithmId.of("tfi:root:v1");
        AlgorithmId similarity = AlgorithmId.of("tfi:similarity:v1");
        FieldChange change = FieldChange.at(
                ChangeKind.MODIFY, ComparePath.root(), "before-secret", "after-secret");
        List<CompareProblem> problems = List.of(
                issue(CompareProblemCode.SNAPSHOT_FAILED),
                issue(CompareProblemCode.ENTITY_KEY_INVALID),
                issue(CompareProblemCode.SNAPSHOT_FAILED));
        List<CompareLimitation> limitations = List.of(
                limit(CompareLimitationCode.KEY_AMBIGUOUS),
                limit(CompareLimitationCode.DEPTH_LIMIT_REACHED),
                limit(CompareLimitationCode.KEY_AMBIGUOUS));
        CompareDiagnostics diagnostics = new CompareDiagnostics(
                11, Optional.of(root), List.of(root, similarity),
                Optional.of("sha256-v1:" + "a".repeat(64)),
                12, 13, 14, 15, 16, 17, 18);
        return CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                problems,
                limitations,
                diagnostics,
                Optional.of(new SimilarityScore(similarity, 0.75)));
    }

    private static CompareProblem issue(CompareProblemCode code) {
        return new CompareProblem(code, CompareStage.SNAPSHOT, Optional.empty());
    }

    private static CompareLimitation limit(CompareLimitationCode code) {
        return new CompareLimitation(code, CompareStage.DIFF, Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> countMap(Object value) {
        return (Map<String, Integer>) value;
    }

    private static final class FixedOperations implements CompareOperations {
        private final CompareResult result;

        private FixedOperations(CompareResult result) {
            this.result = result;
        }

        @Override
        public CompareResult compare(Object before, Object after) {
            return result;
        }

        @Override
        public CompareResult compare(Object before, Object after, CompareOptions options) {
            return result;
        }
    }

    private static final class RecordingStage implements Stage {
        private final boolean accepted;
        private final int remaining;
        private RecordType type;
        private String code;
        private String text;
        private Map<String, Object> data = new LinkedHashMap<>();

        private RecordingStage(boolean accepted, int remaining) {
            this.accepted = accepted;
            this.remaining = remaining;
        }

        @Override public Stage attr(String key, Object value) { return this; }
        @Override public void message(String text) { }
        @Override public void change(String path, Object before, Object after) { }
        @Override public void error(String text) { }
        @Override public void error(String text, Throwable error) { }

        @Override
        public boolean record(
                RecordType recordType, String recordCode, String recordText, Map<String, ?> recordData) {
            type = recordType;
            code = recordCode;
            text = recordText;
            data = new LinkedHashMap<>(recordData);
            return accepted;
        }

        @Override public int remainingEncodedBytes() { return remaining; }
        @Override public void close() { }
    }
}
