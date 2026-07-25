package com.syy.taskflowinsight.ops.compare;

import com.syy.taskflowinsight.tracking.compare.AlgorithmId;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 将 canonical 比较结果投影为宿主 Micrometer 聚合事实。
 *
 * <p>该类型不缓存 meter，也不接受调用方自定义名称或 tag。Registry 按完整 name+tags 负责去重，
 * 从而避免 Ops 再建立一套有生命周期和容量语义的本地缓存。</p>
 */
final class CompareMetrics {

    /** 每次 direct operations 调用发布一次的固定请求计数器。 */
    static final String REQUEST_METER = "tfi.compare.request";
    /** 直接复用结果诊断耗时的固定计时器。 */
    static final String DURATION_METER = "tfi.compare.duration";
    /** problem 与 limitation 共用、由 kind 区分的固定问题计数器。 */
    static final String ISSUE_METER = "tfi.compare.issue";
    /** 有界结果未保留事实的固定省略计数器。 */
    static final String OMITTED_METER = "tfi.compare.omitted";
    /** 宿主提供的唯一 meter 所有者；Ops 不创建私有 Registry。 */
    private final MeterRegistry registry;

    CompareMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * 只从不可变结果派生低基数事实，不读取业务对象或异常消息。
     *
     * @param result Engine 返回的原始 canonical 结果
     */
    void record(CompareResult result) {
        Objects.requireNonNull(result, "result");
        Tags commonTags = commonTags(result);
        registry.counter(REQUEST_METER, commonTags).increment();
        registry.timer(DURATION_METER, commonTags).record(
                result.getDiagnostics().durationNanos(), TimeUnit.NANOSECONDS);

        for (CompareProblem problem : result.getProblems()) {
            registry.counter(ISSUE_METER, commonTags.and(
                    "kind", "problem",
                    "code", problem.code().wireCode(),
                    "stage", token(problem.stage().name()))).increment();
        }
        for (CompareLimitation limitation : result.getLimitations()) {
            registry.counter(ISSUE_METER, commonTags.and(
                    "kind", "limitation",
                    "code", limitation.code().wireCode(),
                    "stage", token(limitation.stage().name()))).increment();
        }

        CompareDiagnostics diagnostics = result.getDiagnostics();
        recordOmitted(commonTags, "path", diagnostics.omittedPaths());
        recordOmitted(commonTags, "change", diagnostics.omittedChanges());
        recordOmitted(commonTags, "problem", diagnostics.omittedProblems());
        recordOmitted(commonTags, "limitation", diagnostics.omittedLimitations());
    }

    private void recordOmitted(Tags commonTags, String kind, long count) {
        if (count > 0) {
            registry.counter(OMITTED_METER, commonTags.and("kind", kind)).increment(count);
        }
    }

    private static Tags commonTags(CompareResult result) {
        String rootAlgorithmId = result.getDiagnostics().rootAlgorithmId()
                .map(AlgorithmId::value)
                .orElse("none");
        return Tags.of(
                "rootAlgorithmId", rootAlgorithmId,
                "outcome", token(result.getOutcome().name()),
                "completion", token(result.getCompletion().name()));
    }

    private static String token(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
