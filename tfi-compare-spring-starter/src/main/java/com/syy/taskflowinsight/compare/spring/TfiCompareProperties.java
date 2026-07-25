package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * 将 {@code tfi.compare.*} 一次绑定为当前上下文的完整不可变策略输入。
 *
 * <p>所有缺省值都从 {@link ComparePolicy#defaults()} 取得；该对象只存在于启动期，不在请求期间读取
 * Environment，从而保持纯 Java 与 Spring 入口使用同一语义 owner。</p>
 *
 * @since 4.0.0
 */
@ConfigurationProperties("tfi.compare")
public record TfiCompareProperties(
        /** 当前上下文是否执行比较。 */ Boolean enabled,
        /** 未显式覆盖时是否计算相似度。 */ Boolean computeSimilarity,
        /** 是否比较容器成员。 */ Boolean includeCollectionContents,
        /** root 直接成员按零计算的逻辑深度上限。 */ Integer maxDepth,
        /** 单次请求共享的节点预算。 */ Integer maxComparedNodes,
        /** 两侧容器成员合计预算。 */ Integer maxElements,
        /** 不包含业务 action 时间的协作式时限。 */ Duration deadline,
        /** 可发布的 canonical change 数量上限。 */ Integer maxChangeDetails,
        /** problem 与 limitation 的合计上限。 */ Integer maxIssues,
        /** 单个 scalar 事实的字符预算。 */ Integer maxResultValueChars,
        /** 单条 canonical path 的字符预算。 */ Integer maxPathEncodedChars,
        /** 整个结果文本事实的累计字符预算。 */ Integer maxResultTotalChars,
        /** 单个 entity key 的有序 component 上限。 */ Integer maxEntityKeyComponents,
        /** 单个 entity key canonical wire 的字节预算。 */ Integer maxEntityKeyEncodedBytes,
        /** strategy 与 comparator 注册总量上限。 */ Integer maxRegisteredExtensions,
        /** include 或 exclude 每类规则数量上限。 */ Integer maxPathRules,
        /** 单个 path pattern 的 segment 上限。 */ Integer maxPatternSegments,
        /** 单个 pattern token 的字符预算。 */ Integer maxPatternTokenChars,
        /** 同类 pattern 的累计字符预算。 */ Integer maxPatternTotalChars,
        /** 构造期编译的 source 白名单。 */ List<String> includePathRules,
        /** 构造期编译的路径黑名单。 */ List<String> excludePathRules,
        /** 单次 tracking action 的 target 上限。 */ Integer maxTrackingTargets,
        /** process-local tracking name 的字符预算。 */ Integer maxTrackingNameChars,
        /** numeric equality 的非负绝对容差。 */ BigDecimal numericAbsoluteTolerance,
        /** numeric equality 的有限相对容差。 */ Double numericRelativeTolerance,
        /** 同类 temporal 值允许的最大时间差。 */ Duration temporalTolerance,
        /** 只能扩大安全范围的投影脱敏配置。 */ Masking masking,
        /** 与普通比较开关独立的 TfiTask 集成配置。 */ Tracking tracking) {

    /**
     * 使用唯一 Policy 默认值补齐未绑定字段，并防御复制集合。
     */
    public TfiCompareProperties {
        ComparePolicy defaults = ComparePolicy.defaults();
        enabled = valueOrDefault(enabled, defaults.enabled());
        computeSimilarity = valueOrDefault(computeSimilarity, defaults.computeSimilarity());
        includeCollectionContents = valueOrDefault(
                includeCollectionContents, defaults.includeCollectionContents());
        maxDepth = valueOrDefault(maxDepth, defaults.maxDepth());
        maxComparedNodes = valueOrDefault(maxComparedNodes, defaults.maxComparedNodes());
        maxElements = valueOrDefault(maxElements, defaults.maxElements());
        deadline = valueOrDefault(deadline, defaults.deadline());
        maxChangeDetails = valueOrDefault(maxChangeDetails, defaults.maxChangeDetails());
        maxIssues = valueOrDefault(maxIssues, defaults.maxIssues());
        maxResultValueChars = valueOrDefault(maxResultValueChars, defaults.maxResultValueChars());
        maxPathEncodedChars = valueOrDefault(maxPathEncodedChars, defaults.maxPathEncodedChars());
        maxResultTotalChars = valueOrDefault(maxResultTotalChars, defaults.maxResultTotalChars());
        maxEntityKeyComponents = valueOrDefault(maxEntityKeyComponents, defaults.maxEntityKeyComponents());
        maxEntityKeyEncodedBytes = valueOrDefault(
                maxEntityKeyEncodedBytes, defaults.maxEntityKeyEncodedBytes());
        maxRegisteredExtensions = valueOrDefault(
                maxRegisteredExtensions, defaults.maxRegisteredExtensions());
        maxPathRules = valueOrDefault(maxPathRules, defaults.maxPathRules());
        maxPatternSegments = valueOrDefault(maxPatternSegments, defaults.maxPatternSegments());
        maxPatternTokenChars = valueOrDefault(maxPatternTokenChars, defaults.maxPatternTokenChars());
        maxPatternTotalChars = valueOrDefault(maxPatternTotalChars, defaults.maxPatternTotalChars());
        includePathRules = copyOrDefault(includePathRules, List.of());
        excludePathRules = copyOrDefault(excludePathRules, List.of());
        maxTrackingTargets = valueOrDefault(maxTrackingTargets, defaults.maxTrackingTargets());
        maxTrackingNameChars = valueOrDefault(maxTrackingNameChars, defaults.maxTrackingNameChars());
        numericAbsoluteTolerance = valueOrDefault(
                numericAbsoluteTolerance, defaults.numericAbsoluteTolerance());
        numericRelativeTolerance = valueOrDefault(
                numericRelativeTolerance, defaults.numericRelativeTolerance());
        temporalTolerance = valueOrDefault(temporalTolerance, defaults.temporalTolerance());
        masking = masking != null ? masking : new Masking(null);
        tracking = tracking != null ? tracking : new Tracking(null);
    }

    /** 只在自动配置边界创建尚未冻结的完整 Policy builder，供有限 alias 覆盖。 */
    ComparePolicy.Builder policyBuilder() {
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

    private static <T> T valueOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private static <T> List<T> copyOrDefault(List<T> values, List<T> defaultValues) {
        return values != null ? List.copyOf(values) : List.copyOf(defaultValues);
    }

    /** 脱敏配置不提供 include-sensitive 或关闭内置规则的字段。 */
    public record Masking(/** 在安全 floor 上追加的 typed path 规则。 */ List<String> additionalRules) {
        /** 防御复制绑定输入，避免配置集合在启动后变化。 */
        public Masking {
            additionalRules = additionalRules != null ? List.copyOf(additionalRules) : List.of();
        }
    }

    /** 追踪集成默认关闭，避免引入 Flow 后隐式启用 deep tracking。 */
    public record Tracking(/** 是否装配可选 TfiTask delegate。 */ Boolean enabled) {
        /** 将缺省值固定为关闭。 */
        public Tracking {
            enabled = enabled != null ? enabled : false;
        }
    }
}
