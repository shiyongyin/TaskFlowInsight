package com.syy.taskflowinsight.tracking.compare;

import java.lang.reflect.Field;

/**
 * 属性比较器：用于对单个字段值进行等价性判断。
 */
public interface PropertyComparator {
    /**
     * 比较同一字段的两个值是否满足该比较器定义的等价关系。
     *
     * @param left 左侧字段值，可为 null
     * @param right 右侧字段值，可为 null
     * @param field 提供字段声明上下文；实现不得修改其所属对象
     * @return {@code true} 表示两个值等价
     * @throws PropertyComparisonException 比较器无法完成判定时抛出
     */
    boolean areEqual(Object left, Object right, Field field) throws PropertyComparisonException;

    /**
     * 判断比较器是否支持指定字段类型。
     *
     * @param type 待比较字段的声明类型
     * @return 默认返回 {@code true}
     */
    default boolean supports(Class<?> type) { return true; }

    /**
     * 获取用于指标与诊断的比较器名称。
     *
     * @return 默认使用实现类简单名称
     */
    default String getName() { return getClass().getSimpleName(); }
}
