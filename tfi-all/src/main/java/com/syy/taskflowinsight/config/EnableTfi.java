package com.syy.taskflowinsight.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用TFI功能的注解
 * 通过该注解可以快速启用TaskFlowInsight的各项功能
 * 
 * 使用示例：
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableTfi(enableChangeTracking = true, enableActuator = true)
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }
 * </pre>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TfiConfigurationImportSelector.class)
public @interface EnableTfi {
    
    /**
     * 是否启用变更追踪功能
     * 
     * @return 默认true
     */
    boolean enableChangeTracking() default true;
    
    /**
     * 是否启用Actuator端点
     * 
     * @return 默认true
     */
    boolean enableActuator() default true;
    
    /**
     * 是否启用异步处理
     * 
     * @return 默认false
     */
    boolean enableAsync() default false;
    
    /**
     * 是否启用调试模式
     * 
     * @return 默认false
     */
    boolean debug() default false;
    
    /**
     * 配置profile
     * 可以指定不同环境的配置
     * 
     * @return 默认为空，使用当前active profile
     */
    String[] profiles() default {};
}