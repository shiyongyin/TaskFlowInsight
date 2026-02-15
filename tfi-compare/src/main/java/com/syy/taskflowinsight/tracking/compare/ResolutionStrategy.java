package com.syy.taskflowinsight.tracking.compare;

/**
 * 冲突解决策略枚举
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public enum ResolutionStrategy {
    /**
     * 使用左侧值
     */
    USE_LEFT,
    
    /**
     * 使用右侧值
     */
    USE_RIGHT,
    
    /**
     * 使用基础值
     */
    USE_BASE,
    
    /**
     * 合并两个值
     */
    MERGE_VALUES,
    
    /**
     * 手动解决
     */
    MANUAL,
    
    /**
     * 跳过此字段
     */
    SKIP
}