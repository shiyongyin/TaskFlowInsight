package com.syy.tfi.kernel.compare;

import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.SimilarityScore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** 只提取 Compare Core 的低敏机器事实，不遍历 change、path、value 或自由文本。 */
final class CompareSummaryMapper {

    /** 当前 summary data schema 的整数版本，与 Record code 的 V1 同步演进。 */
    private static final int SCHEMA_VERSION = 1;
    /** 当前调用未请求 canonical detail，或 CompareResult 没有 change。 */
    static final String DETAIL_NOT_REQUESTED = "NOT_REQUESTED";
    /** canonical detail 已完整投影并转换，可在 summary 接纳后尝试记录。 */
    static final String DETAIL_READY = "READY";
    /** projection 或转换发生普通设施故障，不允许回退读取 raw change。 */
    static final String DETAIL_FAILED = "FAILED";

    private CompareSummaryMapper() {
    }

    static Map<String, Object> map(
            String operation,
            CompareResult result,
            KernelCompareRecordPolicy recordPolicy,
            int plannedDetailCount,
            String detailState) {
        CompareDiagnostics diagnostics = result.getDiagnostics();
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", SCHEMA_VERSION);
        summary.put("operation", operation);
        summary.put("outcome", result.getOutcome().name());
        summary.put("completion", result.getCompletion().name());
        summary.put("availableChangeCount", result.getChanges().size());
        summary.put("problemCodeCounts", sortedCounts(result.getProblems(), problem -> problem.code().wireCode()));
        summary.put(
                "limitationCodeCounts",
                sortedCounts(result.getLimitations(), limitation -> limitation.code().wireCode()));
        diagnostics.rootAlgorithmId().ifPresent(id -> summary.put("rootAlgorithmId", id.value()));
        summary.put("appliedAlgorithmCount", diagnostics.appliedAlgorithmIds().size());
        diagnostics.effectivePolicyFingerprint()
                .ifPresent(fingerprint -> summary.put("effectivePolicyFingerprint", fingerprint));
        result.similarity().ifPresent(similarity -> putSimilarity(summary, similarity));
        summary.put("durationNanos", diagnostics.durationNanos());
        summary.put("comparedNodes", diagnostics.comparedNodes());
        summary.put("consumedElements", diagnostics.consumedElements());
        summary.put("retainedResultChars", diagnostics.retainedResultChars());
        summary.put("omittedPaths", diagnostics.omittedPaths());
        summary.put("omittedChanges", diagnostics.omittedChanges());
        summary.put("omittedProblems", diagnostics.omittedProblems());
        summary.put("omittedLimitations", diagnostics.omittedLimitations());
        summary.put("configuredDetailLimit", recordPolicy.maxRecordedChanges());
        summary.put("plannedDetailCount", plannedDetailCount);
        summary.put("detailState", detailState);
        return Collections.unmodifiableMap(summary);
    }

    private static void putSimilarity(Map<String, Object> summary, SimilarityScore similarity) {
        summary.put("similarityAlgorithmId", similarity.algorithmId().value());
        summary.put("similarityValue", similarity.value());
    }

    private static <T> Map<String, Integer> sortedCounts(List<T> facts, Function<T, String> wireCode) {
        if (facts.isEmpty()) {
            return Map.of();
        }
        TreeMap<String, Integer> sorted = new TreeMap<>();
        for (T fact : facts) {
            sorted.merge(wireCode.apply(fact), 1, Integer::sum);
        }
        // TreeMap 已拥有稳定 wire 顺序，局部实例只需冻结，无需再复制成第二张 Map。
        return Collections.unmodifiableMap(sorted);
    }
}
