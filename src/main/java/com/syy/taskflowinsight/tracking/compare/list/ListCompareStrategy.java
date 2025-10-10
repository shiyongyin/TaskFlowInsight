package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.util.List;

/**
 * 列表比较策略接口
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public interface ListCompareStrategy {
    
    /**
     * 比较两个列表
     * 
     * @param list1 第一个列表
     * @param list2 第二个列表
     * @param options 比较选项
     * @return 比较结果
     */
    CompareResult compare(List<?> list1, List<?> list2, CompareOptions options);
    
    /**
     * 检查是否支持移动检测
     * 
     * @return true表示支持移动检测
     */
    boolean supportsMoveDetection();
    
    /**
     * 获取策略名称
     * 
     * @return 策略名称
     */
    String getStrategyName();
    
    /**
     * 获取推荐的最大列表大小
     * 
     * @return 最大推荐大小，超过此大小可能触发降级
     */
    int getMaxRecommendedSize();
}