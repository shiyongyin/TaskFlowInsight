package com.syy.taskflowinsight.tracking.compare;

/**
 * 比较策略接口
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public interface CompareStrategy<T> {
    
    /**
     * 比较两个对象
     * 
     * @param obj1 第一个对象
     * @param obj2 第二个对象
     * @param options 比较选项
     * @return 比较结果
     */
    CompareResult compare(T obj1, T obj2, CompareOptions options);
    
    /**
     * 获取策略名称
     */
    String getName();
    
    /**
     * 是否支持该类型
     */
    boolean supports(Class<?> type);
}