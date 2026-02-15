package com.syy.taskflowinsight.tracking.compare;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 三方比较合并结果
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder
public class MergeResult {
    
    /**
     * 基础版本
     */
    private Object base;
    
    /**
     * 左侧版本
     */
    private Object left;
    
    /**
     * 右侧版本
     */
    private Object right;
    
    /**
     * 左侧变更
     */
    @Builder.Default
    private List<FieldChange> leftChanges = Collections.emptyList();
    
    /**
     * 右侧变更
     */
    @Builder.Default
    private List<FieldChange> rightChanges = Collections.emptyList();
    
    /**
     * 冲突列表
     */
    @Builder.Default
    private List<MergeConflict> conflicts = Collections.emptyList();
    
    /**
     * 合并后的对象（如果自动合并成功）
     */
    private Object merged;
    
    /**
     * 自动合并是否成功
     */
    private boolean autoMergeSuccessful;
    
    /**
     * 合并策略
     */
    private MergeStrategy strategy;
    
    /**
     * 是否有冲突
     */
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
    
    /**
     * 是否可以自动合并
     */
    public boolean canAutoMerge() {
        return !hasConflicts() || (strategy != null && strategy.canResolveConflicts());
    }
    
    /**
     * 获取所有变更数量
     */
    public int getTotalChanges() {
        return leftChanges.size() + rightChanges.size();
    }
    
    /**
     * 获取冲突数量
     */
    public int getConflictCount() {
        return conflicts.size();
    }
}