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
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证所有有界字段取最坏编码形状时，summary 仍能被 Kernel 默认单 Record 预算接纳。 */
class KernelCompareSummaryBudgetContractTest {

    private static final int DEFAULT_SESSION_BYTES = 12_288;
    private static final int DEFAULT_RECORD_BYTES = 2_048;
    private static final int MAX_ISSUES = 256;
    private static final int MAX_CHANGES = 1_000;
    private static final int MAX_APPLIED_ALGORITHMS = 128;

    @Test
    void worstLegalSummaryFitsTheDefaultRecordBudget() {
        List<FlowSession> sessions = new ArrayList<>();
        KernelConfig config = new KernelConfig(
                true,
                List.of(sessions::add),
                Sampler.always(),
                () -> "budget-session",
                MaximumClock.INSTANCE,
                64,
                DEFAULT_SESSION_BYTES,
                DEFAULT_RECORD_BYTES,
                32);
        CompareResult worstShape = worstLegalResult();

        CompareRecordResult result;
        try (KernelRuntime runtime = KernelRuntime.create(config);
             Stage stage = runtime.begin("summary-budget")) {
            result = recorder(worstShape).record(stage, "a".repeat(128), worstShape);
        }

        assertThat(result.status()).isEqualTo(CompareRecordStatus.RECORDED_SUMMARY);
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.truncated()).isFalse();
            assertThat(session.incompleteReasons()).isEmpty();
            assertThat(session.root().records()).singleElement().satisfies(record -> {
                assertThat(record.code()).isEqualTo("KCOMPARE_SUMMARY_V1");
                assertThat(record.data())
                        .containsEntry("availableChangeCount", MAX_CHANGES)
                        .containsEntry("appliedAlgorithmCount", MAX_APPLIED_ALGORITHMS)
                        .containsEntry("similarityValue", BigDecimal.valueOf(Double.MIN_VALUE));
            });
        });
    }

    private static KernelCompareRecorder recorder(CompareResult result) {
        CompareOperations operations = new FixedOperations(result);
        return new KernelCompareRecorder(
                operations,
                new CompareProjectionFactory(),
                MaskingPolicy.safeDefaults(),
                KernelCompareRecordPolicy.defaults());
    }

    private static CompareResult worstLegalResult() {
        AlgorithmId root = maximumAlgorithmId('a', 'x');
        AlgorithmId similarity = maximumAlgorithmId('b', 'y');
        List<AlgorithmId> applied = appliedAlgorithms(root, similarity);
        FieldChange change = FieldChange.at(ChangeKind.MODIFY, ComparePath.root(), "before", "after");
        CompareDiagnostics diagnostics = new CompareDiagnostics(
                Long.MAX_VALUE,
                Optional.of(root),
                applied,
                Optional.of("sha256-v1:" + "f".repeat(64)),
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE);
        return CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                Collections.nCopies(MAX_CHANGES, change),
                maximumProblems(),
                allLimitations(),
                diagnostics,
                Optional.of(new SimilarityScore(similarity, Double.MIN_VALUE)));
    }

    private static List<AlgorithmId> appliedAlgorithms(AlgorithmId root, AlgorithmId similarity) {
        List<AlgorithmId> algorithms = new ArrayList<>(MAX_APPLIED_ALGORITHMS);
        algorithms.add(root);
        algorithms.add(similarity);
        for (int index = 0; algorithms.size() < MAX_APPLIED_ALGORITHMS; index++) {
            algorithms.add(AlgorithmId.of("tfi:a" + index + ":v1"));
        }
        return algorithms;
    }

    private static List<CompareProblem> maximumProblems() {
        List<CompareProblem> problems = new ArrayList<>();
        for (CompareProblemCode code : CompareProblemCode.values()) {
            problems.add(new CompareProblem(code, CompareStage.SNAPSHOT, Optional.empty()));
        }
        int limitationCount = CompareLimitationCode.values().length;
        while (problems.size() + limitationCount < MAX_ISSUES) {
            problems.add(new CompareProblem(
                    CompareProblemCode.INTERNAL_INVARIANT_VIOLATION,
                    CompareStage.SNAPSHOT,
                    Optional.empty()));
        }
        return problems;
    }

    private static List<CompareLimitation> allLimitations() {
        return Arrays.stream(CompareLimitationCode.values())
                .map(code -> new CompareLimitation(code, CompareStage.DIFF, Optional.empty()))
                .toList();
    }

    private static AlgorithmId maximumAlgorithmId(char namespace, char name) {
        return AlgorithmId.of(namespace + ":" + String.valueOf(name).repeat(123) + ":v1");
    }

    private record FixedOperations(CompareResult result) implements CompareOperations {
        @Override
        public CompareResult compare(Object before, Object after) {
            return result;
        }

        @Override
        public CompareResult compare(Object before, Object after, CompareOptions options) {
            return result;
        }
    }

    private enum MaximumClock implements KernelClock {
        INSTANCE;

        @Override
        public long wallTimeMillis() {
            return Long.MAX_VALUE;
        }

        @Override
        public long monotonicNanos() {
            return Long.MAX_VALUE;
        }
    }
}
