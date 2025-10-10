package com.syy.taskflowinsight.annotation;

import java.lang.annotation.*;

/**
 * 日期格式控制注解
 * 用于字段级别的日期格式和容差配置
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DateFormat {
    
    /**
     * 日期格式模式
     * 默认：yyyy-MM-dd HH:mm:ss
     */
    String pattern() default "yyyy-MM-dd HH:mm:ss";
    
    /**
     * 时区设置
     * SYSTEM: 系统默认时区
     * UTC: 协调世界时
     * 或具体时区ID如：Asia/Shanghai
     */
    String timezone() default "SYSTEM";
    
    /**
     * 时间容差（毫秒）
     * 默认0表示精确比较
     */
    long toleranceMs() default 0L;
}