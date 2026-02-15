package com.syy.taskflowinsight.tracking.compare;

/**
 * 合并策略接口
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public interface MergeStrategy {
    
    /**
     * 是否可以解决冲突
     */
    boolean canResolveConflicts();
    
    /**
     * 解决冲突
     * 
     * @param conflict 冲突信息
     * @return 解决后的值
     */
    Object resolveConflict(MergeConflict conflict);
    
    /**
     * 获取策略名称
     */
    String getName();
    
    /**
     * 获取策略描述
     */
    String getDescription();
}