package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * ValueObject注解
 * 标记值对象，无唯一标识的不可变对象
 * 
 * ValueObject对象特征：
 * - 无唯一身份标识
 * - 通过字段值确定相等性
 * - 变更追踪时进行深度字段比较
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValueObject {
    
    /**
     * 比较策略
     * 
     * @return 值对象比较策略
     */
    ValueObjectCompareStrategy strategy() default ValueObjectCompareStrategy.AUTO;
}