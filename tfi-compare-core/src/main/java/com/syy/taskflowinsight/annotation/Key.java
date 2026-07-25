package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * Key注解
 * 标记实体的唯一标识字段
 * 
 * 用途：
 * - 标识Entity的主键字段
 * - 浅层引用处理时的标识依据
 * - 类型推断时的Entity判定条件
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Key {
}