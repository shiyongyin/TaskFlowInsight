package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TFI变更追踪注解
 * 
 * 基于现有TFI变更追踪API的声明式包装，自动追踪对象变更
 * 
 * <p>使用示例：
 * <pre>{@code
 * @TfiTrack(objects = {"order", "user"})
 * public void updateOrderStatus(Order order, User user, String status) {
 *     // 自动追踪order和user对象的变更
 *     order.setStatus(status);
 *     user.setLastActivity(new Date());
 * }
 * 
 * @TfiTrack(objects = "#order", condition = "#enableTracking")
 * public void processOrder(Order order, boolean enableTracking) {
 *     // 条件性追踪，仅当enableTracking为true时启用
 *     order.process();
 * }
 * }</pre>
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TfiTrack {
    
    /**
     * 要追踪的对象表达式（SpEL）
     * 
     * <p>支持以下格式：
     * <ul>
     * <li>参数名: "order"</li>
     * <li>SpEL表达式: "#order.user"</li>
     * <li>多个对象: {"order", "user"}</li>
     * </ul>
     * 
     * @return 对象表达式数组
     */
    String[] objects() default {};
    
    /**
     * 追踪对象（objects的别名）
     * 
     * @return 对象表达式数组
     */
    String[] value() default {};
    
    /**
     * 启用条件（SpEL表达式）
     * 
     * @return SpEL条件表达式
     */
    String condition() default "";
    
    /**
     * 追踪深度
     * 
     * @return 对象遍历深度，0表示仅追踪顶层属性
     */
    int depth() default 1;
    
    /**
     * 包含的字段模式
     * 
     * @return 字段名模式数组，支持通配符
     */
    String[] includes() default {};
    
    /**
     * 排除的字段模式
     * 
     * @return 字段名模式数组，支持通配符
     */
    String[] excludes() default {};
    
    /**
     * 是否追踪嵌套对象
     * 
     * @return true表示追踪嵌套对象
     */
    boolean trackNested() default true;
    
    /**
     * 是否对敏感字段脱敏
     * 
     * @return true表示自动脱敏
     */
    boolean maskSensitive() default true;
}