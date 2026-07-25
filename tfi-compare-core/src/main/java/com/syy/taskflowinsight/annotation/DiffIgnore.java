package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * DiffIgnore注解
 * 标记字段在变更检测中忽略
 * 
 * 适用场景：
 * - 内部时间戳字段
 * - 计算字段
 * - 缓存字段
 * - 其他不关心变更的字段
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DiffIgnore {
}