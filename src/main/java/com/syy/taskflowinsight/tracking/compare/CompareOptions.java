package com.syy.taskflowinsight.tracking.compare;

import lombok.Builder;
import lombok.Data;
import com.syy.taskflowinsight.annotation.ObjectType;
import com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy;
import com.syy.taskflowinsight.tracking.perf.PerfGuard;

import java.util.List;

/**
 * 比较选项
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder
public class CompareOptions {
    
    /**
     * 默认选项
     */
    public static final CompareOptions DEFAULT = CompareOptions.builder().build();
    
    /**
     * 深度比较选项
     */
    public static final CompareOptions DEEP = CompareOptions.builder()
        .enableDeepCompare(true)
        .maxDepth(10)
        .build();
    
    /**
     * 报告选项
     */
    public static final CompareOptions WITH_REPORT = CompareOptions.builder()
        .generateReport(true)
        .reportFormat(ReportFormat.MARKDOWN)
        .calculateSimilarity(true)
        .build();
    
    /**
     * 是否启用深度比较
     */
    @Builder.Default
    private boolean enableDeepCompare = false;
    
    /**
     * 最大深度
     */
    @Builder.Default
    private int maxDepth = 3;
    
    /**
     * 是否计算相似度
     */
    @Builder.Default
    private boolean calculateSimilarity = false;
    
    /**
     * 是否生成报告
     */
    @Builder.Default
    private boolean generateReport = false;
    
    /**
     * 报告格式
     */
    @Builder.Default
    private ReportFormat reportFormat = ReportFormat.TEXT;
    
    /**
     * 是否生成补丁
     */
    @Builder.Default
    private boolean generatePatch = false;
    
    /**
     * 补丁格式
     */
    @Builder.Default
    private PatchFormat patchFormat = PatchFormat.JSON_PATCH;
    
    /**
     * 是否包含null变更
     */
    @Builder.Default
    private boolean includeNullChanges = false;
    
    /**
     * 忽略的字段
     */
    private List<String> ignoreFields;
    
    /**
     * 排除的字段模式
     */
    private List<String> excludeFields;
    
    /**
     * 并行处理阈值
     */
    @Builder.Default
    private int parallelThreshold = 10;
    
    /**
     * 策略名称
     */
    private String strategyName;
    
    /**
     * 是否尝试自动合并
     */
    @Builder.Default
    private boolean attemptAutoMerge = false;
    
    /**
     * 是否启用类型感知
     */
    @Builder.Default
    private boolean typeAwareEnabled = false;
    
    /**
     * 强制的对象类型（覆盖自动检测）
     */
    private ObjectType forcedObjectType;
    
    /**
     * 强制的比较策略（仅对ValueObject有效）
     */
    private ValueObjectCompareStrategy forcedStrategy;

    /**
     * 性能选项（规模阈值/时间预算/lazySnapshot 等）
     * 默认值来源于 PerfGuard.PerfOptions.defaults()
     */
    @Builder.Default
    private PerfGuard.PerfOptions perf = PerfGuard.PerfOptions.defaults();
    
    /**
     * 是否检测移动操作（仅对List比较有效）
     */
    @Builder.Default
    private boolean detectMoves = false;

    /**
     * 是否追踪 Entity key 的非 @Key 属性变化（仅对 Map<Entity, ?> 有效）
     * <p>
     * 当 Map 的 key 为 Entity 且 @Key 字段相同时，是否追踪非 @Key 属性的变化。
     * 默认 false，避免噪音。
     * </p>
     */
    @Builder.Default
    private boolean trackEntityKeyAttributes = false;

    /**
     * 严格模式：Set<Entity> 检测到重复 @Key 时抛异常
     * <p>
     * 当 Set 的元素为 Entity 且检测到重复 @Key 时：
     * - strictDuplicateKey=false（默认）：DiagnosticLogger 记录 SET-002 警告
     * - strictDuplicateKey=true：抛出 IllegalArgumentException
     * </p>
     * <p>
     * 重复 @Key 通常意味着 equals/hashCode 实现与 @Key 不一致，建议修复数据模型。
     * </p>
     */
    @Builder.Default
    private boolean strictDuplicateKey = false;

    /**
     * 创建深度比较选项
     */
    public static CompareOptions deep(int maxDepth) {
        return CompareOptions.builder()
            .enableDeepCompare(true)
            .maxDepth(maxDepth)
            .build();
    }
    
    /**
     * 创建类型感知的深度比较选项
     */
    public static CompareOptions typeAware() {
        return CompareOptions.builder()
            .enableDeepCompare(true)
            .typeAwareEnabled(true)
            .maxDepth(5)
            .build();
    }
    
    /**
     * 创建带报告的选项
     */
    public static CompareOptions withReport(ReportFormat format) {
        return CompareOptions.builder()
            .generateReport(true)
            .reportFormat(format)
            .calculateSimilarity(true)
            .build();
    }

    // ========== M2: PerfOptions 扩展（性能预算与降级控制） ==========

    /**
     * 性能预算超时阈值（毫秒），超过则触发降级
     * <p>
     * 可通过 YAML 绑定：tfi.diff.perf.timeout-ms
     * </p>
     */
    @Builder.Default
    private int perfTimeoutMs = 5000;

    /**
     * 性能预算元素数量阈值，超过则触发降级
     * <p>
     * 可通过 YAML 绑定：tfi.diff.perf.max-elements
     * </p>
     */
    @Builder.Default
    private int perfMaxElements = 10000;

    /**
     * 是否启用严格性能预算（超预算时抛异常而非降级）
     * <p>
     * 可通过 YAML 绑定：tfi.diff.perf.strict-mode
     * </p>
     */
    @Builder.Default
    private boolean perfStrictMode = false;

    /**
     * 性能降级策略
     * <p>
     * 可通过 YAML 绑定：tfi.diff.perf.degradation-strategy
     * </p>
     */
    @Builder.Default
    private String perfDegradationStrategy = "FALLBACK_TO_SIMPLE";

    /**
     * 创建带性能预算的选项
     */
    public static CompareOptions withPerfBudget(int timeoutMs, int maxElements) {
        return CompareOptions.builder()
            .perfTimeoutMs(timeoutMs)
            .perfMaxElements(maxElements)
            .build();
    }
}
