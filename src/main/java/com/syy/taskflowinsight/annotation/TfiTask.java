package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TFI任务追踪注解
 * 
 * 基于现有TFI.stage()API的声明式包装，自动管理TaskContext生命周期
 * 
 * <p>使用示例：
 * <pre>{@code
 * @TfiTask("订单处理")
 * public Order processOrder(String orderId) {
 *     // 方法执行时自动创建stage("订单处理")
 *     // 自动记录方法参数和返回值
 *     return orderService.process(orderId);
 * }
 * 
 * @TfiTask(value = "用户验证", condition = "#userId != null")
 * public boolean validateUser(String userId) {
 *     // 仅当userId不为null时才启用追踪
 *     return userService.validate(userId);
 * }
 * }</pre>
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TfiTask {
    
    /**
     * 任务名称（支持SpEL表达式）
     * 
     * @return 任务名称，默认使用方法名
     */
    String value() default "";
    
    /**
     * 任务名称（value的别名）
     * 
     * @return 任务名称
     */
    String name() default "";
    
    /**
     * 启用条件（SpEL表达式）
     * 
     * <p>仅在条件为true时启用追踪，用于动态采样
     * 
     * @return SpEL条件表达式，默认总是启用
     */
    String condition() default "";
    
    /**
     * 采样率（0.0-1.0）
     * 
     * @return 采样率，0.0表示禁用，1.0表示全量采样
     */
    double samplingRate() default 1.0;
    
    /**
     * 是否记录方法参数
     * 
     * @return true表示记录参数
     */
    boolean logArgs() default true;
    
    /**
     * 是否记录返回值
     * 
     * @return true表示记录返回值
     */
    boolean logResult() default true;
    
    /**
     * 是否记录异常信息
     * 
     * @return true表示记录异常
     */
    boolean logException() default true;
    
    /**
     * 参数脱敏表达式（SpEL）
     * 
     * <p>对敏感参数进行脱敏处理
     * 
     * @return SpEL表达式，用于处理参数脱敏
     */
    String argsMask() default "";
    
    /**
     * 返回值脱敏表达式（SpEL）
     * 
     * @return SpEL表达式，用于处理返回值脱敏
     */
    String resultMask() default "";
    
    /**
     * 自定义标签
     * 
     * @return 标签数组，用于分类和过滤
     */
    String[] tags() default {};
}