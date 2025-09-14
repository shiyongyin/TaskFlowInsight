package com.syy.taskflowinsight.tracking.compare;

import lombok.Builder;
import lombok.Data;

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
     * 创建深度比较选项
     */
    public static CompareOptions deep(int maxDepth) {
        return CompareOptions.builder()
            .enableDeepCompare(true)
            .maxDepth(maxDepth)
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
}