package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * DiffInclude注解
 * 标记字段在变更检测中包含
 * 
 * 适用场景：
 * - 明确指定需要追踪的字段
 * - 与@DiffIgnore形成互补
 * - 精确控制变更检测范围
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DiffInclude {
}