package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 将 {@code tfi.compare.*} 一次绑定为当前 context 的完整 Compare Runtime 输入。
 *
 * <p>该记录只做启动期映射；请求路径不会再次读取 Environment 或 BeanFactory。</p>
 *
 * @param enabled 是否执行比较
 * @param computeSimilarity 未显式覆盖时是否计算相似度
 * @param includeCollectionContents 是否进入容器成员
 * @param maxDepth root 直接成员按 0 计算的逻辑深度上限
 * @param maxComparedNodes 单次请求可消费的节点总量，单位为个
 * @param maxElements 两侧容器成员合计消费上限，单位为个
 * @param deadline 同线程协作式比较时限
 * @param maxChangeDetails 可发布 canonical change 明细上限，单位为条
 * @param maxIssues problem 与 limitation 合计上限，单位为条
 * @param maxResultValueChars 单个 scalar 事实的 UTF-16 字符预算
 * @param maxPathEncodedChars 单条 canonical path 的 UTF-16 字符预算
 * @param maxResultTotalChars 单个结果文本事实的累计 UTF-16 字符预算
 * @param maxEntityKeyComponents 单个 entity key 的有序 component 上限，单位为个
 * @param maxEntityKeyEncodedBytes 单个 entity key canonical wire 的字节预算
 * @param maxRegisteredExtensions strategy 与 comparator 注册总量上限，单位为个
 * @param maxPathRules include 或 exclude 每类 path rule 上限，单位为条
 * @param maxPatternSegments 单个 path pattern 的 segment 上限，单位为个
 * @param maxPatternTokenChars 单个 pattern token 的 UTF-16 字符预算
 * @param maxPatternTotalChars 同类 pattern 的累计 UTF-16 字符预算
 * @param includePathRules 构造期编译的 source 白名单
 * @param excludePathRules 构造期编译的 source 黑名单
 * @param maxTrackingTargets 单次 tracking action 的 target 上限，单位为个
 * @param maxTrackingNameChars process-local tracking name 的 UTF-16 字符预算
 * @param numericAbsoluteTolerance numeric equality 使用的非负绝对容差
 * @param numericRelativeTolerance numeric equality 使用的有限归一化相对容差
 * @param temporalTolerance 同类 temporal equality 允许的最大时间差
 * @param masking 只能扩大安全 floor 的 projection 脱敏配置
 * @since 4.0.0
 */
@ConfigurationProperties("tfi.compare")
public record TfiCompareCoreProperties(
        /** 是否执行比较。 */ Boolean enabled,
        /** 未显式覆盖时是否计算相似度。 */ Boolean computeSimilarity,
        /** 是否进入容器成员。 */ Boolean includeCollectionContents,
        /** root 直接成员按 0 计算的逻辑深度上限。 */ Integer maxDepth,
        /** 单次请求可消费的节点总量，单位为个。 */ Integer maxComparedNodes,
        /** 两侧容器成员合计消费上限，单位为个。 */ Integer maxElements,
        /** 同线程协作式比较时限。 */ Duration deadline,
        /** 可发布 canonical change 明细上限，单位为条。 */ Integer maxChangeDetails,
        /** problem 与 limitation 合计上限，单位为条。 */ Integer maxIssues,
        /** 单个 scalar 事实的 UTF-16 字符预算。 */ Integer maxResultValueChars,
        /** 单条 canonical path 的 UTF-16 字符预算。 */ Integer maxPathEncodedChars,
        /** 单个结果文本事实的累计 UTF-16 字符预算。 */ Integer maxResultTotalChars,
        /** 单个 entity key 的有序 component 上限，单位为个。 */ Integer maxEntityKeyComponents,
        /** 单个 entity key canonical wire 的字节预算。 */ Integer maxEntityKeyEncodedBytes,
        /** strategy 与 comparator 注册总量上限，单位为个。 */ Integer maxRegisteredExtensions,
        /** include 或 exclude 每类 path rule 上限，单位为条。 */ Integer maxPathRules,
        /** 单个 path pattern 的 segment 上限，单位为个。 */ Integer maxPatternSegments,
        /** 单个 pattern token 的 UTF-16 字符预算。 */ Integer maxPatternTokenChars,
        /** 同类 pattern 的累计 UTF-16 字符预算。 */ Integer maxPatternTotalChars,
        /** 构造期编译的 source 白名单。 */ List<String> includePathRules,
        /** 构造期编译的 source 黑名单。 */ List<String> excludePathRules,
        /** 单次 tracking action 的 target 上限，单位为个。 */ Integer maxTrackingTargets,
        /** process-local tracking name 的 UTF-16 字符预算。 */ Integer maxTrackingNameChars,
        /** numeric equality 使用的非负绝对容差。 */ BigDecimal numericAbsoluteTolerance,
        /** numeric equality 使用的有限归一化相对容差。 */ Double numericRelativeTolerance,
        /** 同类 temporal equality 允许的最大时间差。 */ Duration temporalTolerance,
        /** 只能扩大安全 floor 的 projection 脱敏配置。 */ Masking masking) {

    /** 从 Core owner 补齐全部缺省值，随后让 Core 完成最终语义校验。 */
    public TfiCompareCoreProperties(
            Boolean enabled,
            Boolean computeSimilarity,
            Boolean includeCollectionContents,
            Integer maxDepth,
            Integer maxComparedNodes,
            Integer maxElements,
            Duration deadline,
            Integer maxChangeDetails,
            Integer maxIssues,
            Integer maxResultValueChars,
            Integer maxPathEncodedChars,
            Integer maxResultTotalChars,
            Integer maxEntityKeyComponents,
            Integer maxEntityKeyEncodedBytes,
            Integer maxRegisteredExtensions,
            Integer maxPathRules,
            Integer maxPatternSegments,
            Integer maxPatternTokenChars,
            Integer maxPatternTotalChars,
            List<String> includePathRules,
            List<String> excludePathRules,
            Integer maxTrackingTargets,
            Integer maxTrackingNameChars,
            BigDecimal numericAbsoluteTolerance,
            Double numericRelativeTolerance,
            Duration temporalTolerance,
            Masking masking) {
        ComparePolicy defaults = ComparePolicy.defaults();
        this.enabled = valueOrDefault(enabled, defaults.enabled());
        this.computeSimilarity = valueOrDefault(computeSimilarity, defaults.computeSimilarity());
        this.includeCollectionContents = valueOrDefault(
                includeCollectionContents, defaults.includeCollectionContents());
        this.maxDepth = valueOrDefault(maxDepth, defaults.maxDepth());
        this.maxComparedNodes = valueOrDefault(maxComparedNodes, defaults.maxComparedNodes());
        this.maxElements = valueOrDefault(maxElements, defaults.maxElements());
        this.deadline = valueOrDefault(deadline, defaults.deadline());
        this.maxChangeDetails = valueOrDefault(maxChangeDetails, defaults.maxChangeDetails());
        this.maxIssues = valueOrDefault(maxIssues, defaults.maxIssues());
        this.maxResultValueChars = valueOrDefault(maxResultValueChars, defaults.maxResultValueChars());
        this.maxPathEncodedChars = valueOrDefault(maxPathEncodedChars, defaults.maxPathEncodedChars());
        this.maxResultTotalChars = valueOrDefault(maxResultTotalChars, defaults.maxResultTotalChars());
        this.maxEntityKeyComponents = valueOrDefault(maxEntityKeyComponents, defaults.maxEntityKeyComponents());
        this.maxEntityKeyEncodedBytes = valueOrDefault(
                maxEntityKeyEncodedBytes, defaults.maxEntityKeyEncodedBytes());
        this.maxRegisteredExtensions = valueOrDefault(
                maxRegisteredExtensions, defaults.maxRegisteredExtensions());
        this.maxPathRules = valueOrDefault(maxPathRules, defaults.maxPathRules());
        this.maxPatternSegments = valueOrDefault(maxPatternSegments, defaults.maxPatternSegments());
        this.maxPatternTokenChars = valueOrDefault(maxPatternTokenChars, defaults.maxPatternTokenChars());
        this.maxPatternTotalChars = valueOrDefault(maxPatternTotalChars, defaults.maxPatternTotalChars());
        this.includePathRules = copyOrDefault(includePathRules, "tfi.compare.include-path-rules");
        this.excludePathRules = copyOrDefault(excludePathRules, "tfi.compare.exclude-path-rules");
        this.maxTrackingTargets = valueOrDefault(maxTrackingTargets, defaults.maxTrackingTargets());
        this.maxTrackingNameChars = valueOrDefault(maxTrackingNameChars, defaults.maxTrackingNameChars());
        this.numericAbsoluteTolerance = valueOrDefault(
                numericAbsoluteTolerance, defaults.numericAbsoluteTolerance());
        this.numericRelativeTolerance = valueOrDefault(
                numericRelativeTolerance, defaults.numericRelativeTolerance());
        this.temporalTolerance = valueOrDefault(temporalTolerance, defaults.temporalTolerance());
        this.masking = masking != null ? masking : new Masking(null);
        validateWithCore();
    }

    /** 将全部 26 个 Core 字段一次映射到尚未冻结的 Policy builder。 */
    ComparePolicy.Builder toPolicyBuilder() {
        return ComparePolicy.builder()
                .enabled(enabled).computeSimilarity(computeSimilarity)
                .includeCollectionContents(includeCollectionContents)
                .maxDepth(maxDepth).maxComparedNodes(maxComparedNodes).maxElements(maxElements)
                .deadline(deadline).maxChangeDetails(maxChangeDetails).maxIssues(maxIssues)
                .maxResultValueChars(maxResultValueChars).maxPathEncodedChars(maxPathEncodedChars)
                .maxResultTotalChars(maxResultTotalChars)
                .maxEntityKeyComponents(maxEntityKeyComponents)
                .maxEntityKeyEncodedBytes(maxEntityKeyEncodedBytes)
                .maxRegisteredExtensions(maxRegisteredExtensions).maxPathRules(maxPathRules)
                .maxPatternSegments(maxPatternSegments).maxPatternTokenChars(maxPatternTokenChars)
                .maxPatternTotalChars(maxPatternTotalChars)
                .includePathRules(includePathRules).excludePathRules(excludePathRules)
                .maxTrackingTargets(maxTrackingTargets).maxTrackingNameChars(maxTrackingNameChars)
                .numericAbsoluteTolerance(numericAbsoluteTolerance)
                .numericRelativeTolerance(numericRelativeTolerance)
                .temporalTolerance(temporalTolerance);
    }

    /** 使用不可弱化的安全 floor 编译附加脱敏规则。 */
    MaskingPolicy toMaskingPolicy() {
        try {
            return MaskingPolicy.safeDefaultsWithAdditionalRules(masking.additionalRules());
        } catch (RuntimeException exception) {
            throw invalid("tfi.compare.masking.additional-rules", "cannot compile rules", exception);
        }
    }

    private void validateWithCore() {
        try {
            toPolicyBuilder().build();
        } catch (RuntimeException exception) {
            throw invalid(firstInvalidPolicyPath(), "cannot construct ComparePolicy", exception);
        }
        toMaskingPolicy();
    }

    private String firstInvalidPolicyPath() {
        ComparePolicy.Builder builder = ComparePolicy.builder();
        for (PolicyValidationStep step : policyValidationSteps()) {
            try {
                step.apply().accept(builder);
                builder.build();
            } catch (RuntimeException exception) {
                return step.path();
            }
        }
        return "tfi.compare";
    }

    private List<PolicyValidationStep> policyValidationSteps() {
        return List.of(
                new PolicyValidationStep("tfi.compare.max-depth", builder -> builder.maxDepth(maxDepth)),
                new PolicyValidationStep(
                        "tfi.compare.max-compared-nodes", builder -> builder.maxComparedNodes(maxComparedNodes)),
                new PolicyValidationStep("tfi.compare.max-elements", builder -> builder.maxElements(maxElements)),
                new PolicyValidationStep("tfi.compare.deadline", builder -> builder.deadline(deadline)),
                new PolicyValidationStep(
                        "tfi.compare.max-change-details", builder -> builder.maxChangeDetails(maxChangeDetails)),
                new PolicyValidationStep("tfi.compare.max-issues", builder -> builder.maxIssues(maxIssues)),
                new PolicyValidationStep(
                        "tfi.compare.max-result-value-chars",
                        builder -> builder.maxResultValueChars(maxResultValueChars)),
                new PolicyValidationStep(
                        "tfi.compare.max-path-encoded-chars",
                        builder -> builder.maxPathEncodedChars(maxPathEncodedChars)),
                new PolicyValidationStep(
                        "tfi.compare.max-result-total-chars",
                        builder -> builder.maxResultTotalChars(maxResultTotalChars)),
                new PolicyValidationStep(
                        "tfi.compare.max-entity-key-components",
                        builder -> builder.maxEntityKeyComponents(maxEntityKeyComponents)),
                new PolicyValidationStep(
                        "tfi.compare.max-entity-key-encoded-bytes",
                        builder -> builder.maxEntityKeyEncodedBytes(maxEntityKeyEncodedBytes)),
                new PolicyValidationStep(
                        "tfi.compare.max-registered-extensions",
                        builder -> builder.maxRegisteredExtensions(maxRegisteredExtensions)),
                new PolicyValidationStep(
                        "tfi.compare.max-path-rules", builder -> builder.maxPathRules(maxPathRules)),
                new PolicyValidationStep(
                        "tfi.compare.max-pattern-segments",
                        builder -> builder.maxPatternSegments(maxPatternSegments)),
                new PolicyValidationStep(
                        "tfi.compare.max-pattern-token-chars",
                        builder -> builder.maxPatternTokenChars(maxPatternTokenChars)),
                new PolicyValidationStep(
                        "tfi.compare.max-pattern-total-chars",
                        builder -> builder.maxPatternTotalChars(maxPatternTotalChars)),
                new PolicyValidationStep(
                        "tfi.compare.max-tracking-targets",
                        builder -> builder.maxTrackingTargets(maxTrackingTargets)),
                new PolicyValidationStep(
                        "tfi.compare.max-tracking-name-chars",
                        builder -> builder.maxTrackingNameChars(maxTrackingNameChars)),
                new PolicyValidationStep(
                        "tfi.compare.numeric-absolute-tolerance",
                        builder -> builder.numericAbsoluteTolerance(numericAbsoluteTolerance)),
                new PolicyValidationStep(
                        "tfi.compare.numeric-relative-tolerance",
                        builder -> builder.numericRelativeTolerance(numericRelativeTolerance)),
                new PolicyValidationStep(
                        "tfi.compare.temporal-tolerance",
                        builder -> builder.temporalTolerance(temporalTolerance)),
                new PolicyValidationStep(
                        "tfi.compare.include-path-rules",
                        builder -> builder.includePathRules(includePathRules)),
                new PolicyValidationStep(
                        "tfi.compare.exclude-path-rules",
                        builder -> builder.excludePathRules(excludePathRules)));
    }

    private static <T> T valueOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private static List<String> copyOrDefault(List<String> values, String path) {
        try {
            return values != null ? List.copyOf(values) : List.of();
        } catch (RuntimeException exception) {
            throw invalid(path, "must not contain null", exception);
        }
    }

    private static IllegalArgumentException invalid(String path, String reason, Throwable cause) {
        return new IllegalArgumentException("KCS_E_1003: " + path + " " + reason, cause);
    }

    /** Core 拒绝后才执行的诊断步骤，不在正常启动路径复制或解释资源边界。 */
    private record PolicyValidationStep(
            /** 对应当前 Core builder 输入的 canonical Spring property path。 */ String path,
            /** 将单个实际绑定值施加到持续累积的 Core builder。 */ Consumer<ComparePolicy.Builder> apply) {
    }

    /**
     * 不允许关闭安全 floor 或开启敏感值，只接收附加 typed path 规则。
     *
     * @param additionalRules 在默认安全规则之上追加的规则文本
     */
    public record Masking(List<String> additionalRules) {

        /** 防御复制绑定集合，避免 context ready 后规则被外部改写。 */
        public Masking {
            additionalRules = copyOrDefault(
                    additionalRules,
                    "tfi.compare.masking.additional-rules");
        }
    }
}
