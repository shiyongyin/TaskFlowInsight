package com.syy.taskflowinsight.annotation;

import java.lang.annotation.*;

/**
 * 数值精度控制注解
 * 用于字段级别的精度配置，覆盖全局配置
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NumericPrecision {
    
    /**
     * 绝对容差，默认1e-12
     */
    double absoluteTolerance() default 1e-12;
    
    /**
     * 相对容差，默认1e-9
     */
    double relativeTolerance() default 1e-9;
    
    /**
     * BigDecimal比较方法
     * COMPARE_TO: 忽略scale差异（默认）
     * EQUALS: 严格比较scale
     * WITH_TOLERANCE: 基于容差比较
     */
    String compareMethod() default "COMPARE_TO";
    
    /**
     * 小数位数，-1表示不设置
     */
    int scale() default -1;
    
    /**
     * 舍入模式
     */
    String roundingMode() default "HALF_UP";
}