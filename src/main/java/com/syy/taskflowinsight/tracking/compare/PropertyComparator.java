package com.syy.taskflowinsight.tracking.compare;

import java.lang.reflect.Field;

/**
 * 属性比较器：用于对单个字段值进行等价性判断。
 */
public interface PropertyComparator {
    /** 比较两个值是否等价。 */
    boolean areEqual(Object left, Object right, Field field) throws PropertyComparisonException;

    /** 是否支持指定类型（默认 true）。 */
    default boolean supports(Class<?> type) { return true; }

    /** 比较器名称（用于指标与日志）。 */
    default String getName() { return getClass().getSimpleName(); }
}

