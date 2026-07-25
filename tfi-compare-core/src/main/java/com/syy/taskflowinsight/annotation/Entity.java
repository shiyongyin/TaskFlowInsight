package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * Entity注解
 * 标记实体对象，有唯一标识的业务对象
 * 
 * Entity对象特征：
 * - 具有唯一身份标识（通过@Key字段）
 * - 生命周期独立管理
 * - 变更追踪时进行浅层处理以避免深度遍历
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Entity {
    
    /**
     * 实体显示名称，默认使用类名
     * 
     * @return 实体名称
     */
    String name() default "";
}