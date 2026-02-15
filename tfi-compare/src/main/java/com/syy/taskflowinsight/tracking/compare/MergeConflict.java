package com.syy.taskflowinsight.tracking.compare;

import lombok.Builder;
import lombok.Data;

/**
 * 合并冲突
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder
public class MergeConflict {
    
    /**
     * 冲突字段名
     */
    private String fieldName;
    
    /**
     * 基础值
     */
    private Object baseValue;
    
    /**
     * 左侧值
     */
    private Object leftValue;
    
    /**
     * 右侧值
     */
    private Object rightValue;
    
    /**
     * 冲突类型
     */
    private ConflictType conflictType;
    
    /**
     * 冲突描述
     */
    private String description;
    
    /**
     * 建议的解决方案
     */
    private ResolutionStrategy suggestedResolution;
    
    /**
     * 是否可自动解决
     */
    private boolean autoResolvable;
    
    /**
     * 获取冲突摘要
     */
    public String getSummary() {
        return String.format("Conflict in field '%s': left=%s, right=%s (type=%s)",
            fieldName, leftValue, rightValue, conflictType);
    }
}