package com.syy.taskflowinsight.tracking.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DegradationLevel 枚举单元测试
 * 
 * 验证降级级别的行为和顺序
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
class DegradationLevelTest {
    
    @Test
    @DisplayName("降级级别顺序应该正确")
    void shouldHaveCorrectDegradationOrder() {
        DegradationLevel[] levels = DegradationLevel.values();
        
        // 验证5个级别
        assertThat(levels).hasSize(5);
        
        // 验证顺序：从最宽松到最严格
        assertThat(levels[0]).isEqualTo(DegradationLevel.FULL_TRACKING);
        assertThat(levels[1]).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
        assertThat(levels[2]).isEqualTo(DegradationLevel.SIMPLE_COMPARISON);
        assertThat(levels[3]).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        assertThat(levels[4]).isEqualTo(DegradationLevel.DISABLED);
    }
    
    @Test
    @DisplayName("FULL_TRACKING应该允许所有功能")
    void fullTrackingShouldAllowAllFeatures() {
        DegradationLevel level = DegradationLevel.FULL_TRACKING;
        
        assertThat(level.allowsDeepAnalysis()).isTrue();
        assertThat(level.allowsMoveDetection()).isTrue();
        assertThat(level.allowsPathOptimization()).isTrue();
        assertThat(level.onlySummaryInfo()).isFalse();
        assertThat(level.isDisabled()).isFalse();
        
        assertThat(level.getMaxDepth()).isEqualTo(10);
        assertThat(level.getMaxElements()).isEqualTo(Integer.MAX_VALUE);
    }
    
    @Test
    @DisplayName("SKIP_DEEP_ANALYSIS应该跳过深度分析")
    void skipDeepAnalysisShouldDisableDeepAnalysis() {
        DegradationLevel level = DegradationLevel.SKIP_DEEP_ANALYSIS;
        
        assertThat(level.allowsDeepAnalysis()).isFalse();
        assertThat(level.allowsMoveDetection()).isTrue();
        assertThat(level.allowsPathOptimization()).isTrue();
        assertThat(level.onlySummaryInfo()).isFalse();
        assertThat(level.isDisabled()).isFalse();
        
        assertThat(level.getMaxDepth()).isEqualTo(8);
        assertThat(level.getMaxElements()).isEqualTo(10000);
    }
    
    @Test
    @DisplayName("SIMPLE_COMPARISON应该仅允许基础比较")
    void simpleComparisonShouldOnlyAllowBasicComparison() {
        DegradationLevel level = DegradationLevel.SIMPLE_COMPARISON;
        
        assertThat(level.allowsDeepAnalysis()).isFalse();
        assertThat(level.allowsMoveDetection()).isFalse();
        assertThat(level.allowsPathOptimization()).isFalse();
        assertThat(level.onlySummaryInfo()).isFalse();
        assertThat(level.isDisabled()).isFalse();
        
        assertThat(level.getMaxDepth()).isEqualTo(5);
        assertThat(level.getMaxElements()).isEqualTo(5000);
    }
    
    @Test
    @DisplayName("SUMMARY_ONLY应该仅允许摘要信息")
    void summaryOnlyShouldOnlyAllowSummaryInfo() {
        DegradationLevel level = DegradationLevel.SUMMARY_ONLY;
        
        assertThat(level.allowsDeepAnalysis()).isFalse();
        assertThat(level.allowsMoveDetection()).isFalse();
        assertThat(level.allowsPathOptimization()).isFalse();
        assertThat(level.onlySummaryInfo()).isTrue();
        assertThat(level.isDisabled()).isFalse();
        
        assertThat(level.getMaxDepth()).isEqualTo(3);
        assertThat(level.getMaxElements()).isEqualTo(1000);
    }
    
    @Test
    @DisplayName("DISABLED应该完全禁用")
    void disabledShouldDisableAllFeatures() {
        DegradationLevel level = DegradationLevel.DISABLED;
        
        assertThat(level.allowsDeepAnalysis()).isFalse();
        assertThat(level.allowsMoveDetection()).isFalse();
        assertThat(level.allowsPathOptimization()).isFalse();
        assertThat(level.onlySummaryInfo()).isFalse();
        assertThat(level.isDisabled()).isTrue();
        
        assertThat(level.getMaxDepth()).isEqualTo(0);
        assertThat(level.getMaxElements()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("isMoreRestrictiveThan应该正确判断严格程度")
    void isMoreRestrictiveThanShouldWorkCorrectly() {
        // DISABLED 比其他所有级别都严格
        assertThat(DegradationLevel.DISABLED.isMoreRestrictiveThan(DegradationLevel.SUMMARY_ONLY)).isTrue();
        assertThat(DegradationLevel.DISABLED.isMoreRestrictiveThan(DegradationLevel.FULL_TRACKING)).isTrue();
        
        // SUMMARY_ONLY 比 SIMPLE_COMPARISON 严格
        assertThat(DegradationLevel.SUMMARY_ONLY.isMoreRestrictiveThan(DegradationLevel.SIMPLE_COMPARISON)).isTrue();
        assertThat(DegradationLevel.SIMPLE_COMPARISON.isMoreRestrictiveThan(DegradationLevel.SUMMARY_ONLY)).isFalse();
        
        // 相同级别不严格
        assertThat(DegradationLevel.FULL_TRACKING.isMoreRestrictiveThan(DegradationLevel.FULL_TRACKING)).isFalse();
    }
    
    @Test
    @DisplayName("getNextMoreRestrictive应该返回下一个更严格的级别")
    void getNextMoreRestrictiveShouldReturnNextLevel() {
        assertThat(DegradationLevel.FULL_TRACKING.getNextMoreRestrictive())
            .isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
        
        assertThat(DegradationLevel.SKIP_DEEP_ANALYSIS.getNextMoreRestrictive())
            .isEqualTo(DegradationLevel.SIMPLE_COMPARISON);
        
        assertThat(DegradationLevel.SIMPLE_COMPARISON.getNextMoreRestrictive())
            .isEqualTo(DegradationLevel.SUMMARY_ONLY);
        
        assertThat(DegradationLevel.SUMMARY_ONLY.getNextMoreRestrictive())
            .isEqualTo(DegradationLevel.DISABLED);
        
        // 最严格级别返回自身
        assertThat(DegradationLevel.DISABLED.getNextMoreRestrictive())
            .isEqualTo(DegradationLevel.DISABLED);
    }
    
    @Test
    @DisplayName("getNextLessRestrictive应该返回下一个更宽松的级别")
    void getNextLessRestrictiveShouldReturnPrevLevel() {
        assertThat(DegradationLevel.DISABLED.getNextLessRestrictive())
            .isEqualTo(DegradationLevel.SUMMARY_ONLY);
        
        assertThat(DegradationLevel.SUMMARY_ONLY.getNextLessRestrictive())
            .isEqualTo(DegradationLevel.SIMPLE_COMPARISON);
        
        assertThat(DegradationLevel.SIMPLE_COMPARISON.getNextLessRestrictive())
            .isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
        
        assertThat(DegradationLevel.SKIP_DEEP_ANALYSIS.getNextLessRestrictive())
            .isEqualTo(DegradationLevel.FULL_TRACKING);
        
        // 最宽松级别返回自身
        assertThat(DegradationLevel.FULL_TRACKING.getNextLessRestrictive())
            .isEqualTo(DegradationLevel.FULL_TRACKING);
    }
    
    @Test
    @DisplayName("toString应该包含描述信息")
    void toStringShouldIncludeDescription() {
        assertThat(DegradationLevel.FULL_TRACKING.toString())
            .contains("FULL_TRACKING")
            .contains("完整追踪");
        
        assertThat(DegradationLevel.DISABLED.toString())
            .contains("DISABLED")
            .contains("完全禁用");
    }
}