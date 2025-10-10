package com.syy.taskflowinsight.registry;

import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ValueObject策略解析器
 * 解析ValueObject的比较策略（仅用于VALUE_OBJECT类型）
 * 
 * 策略解析优先级：
 * 1. @ValueObject注解的strategy配置
 * 2. 程序化注册的策略
 * 3. 默认FIELDS策略
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
public final class ValueObjectStrategyResolver {
    
    private static final Logger log = LoggerFactory.getLogger(ValueObjectStrategyResolver.class);
    
    private ValueObjectStrategyResolver() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * 解析ValueObject的比较策略（仅用于VALUE_OBJECT类型）
     */
    public static ValueObjectCompareStrategy resolveStrategy(Object object) {
        if (object == null) {
            return ValueObjectCompareStrategy.FIELDS;
        }
        
        Class<?> clazz = object.getClass();
        return resolveStrategy(clazz);
    }
    
    /**
     * 根据Class解析策略（主要用于测试）
     */
    public static ValueObjectCompareStrategy resolveStrategy(Class<?> clazz) {
        if (clazz == null) {
            return ValueObjectCompareStrategy.FIELDS;
        }
        
        // 检查ValueObject注解的策略配置
        if (clazz.isAnnotationPresent(ValueObject.class)) {
            ValueObject annotation = clazz.getAnnotation(ValueObject.class);
            ValueObjectCompareStrategy strategy = annotation.strategy();
            
            // AUTO策略解析为FIELDS
            if (strategy == ValueObjectCompareStrategy.AUTO) {
                log.debug("Resolved AUTO strategy as FIELDS for {}", clazz.getSimpleName());
                return ValueObjectCompareStrategy.FIELDS;
            }
            
            log.debug("Resolved strategy {} via @ValueObject annotation for {}", 
                strategy, clazz.getSimpleName());
            return strategy;
        }
        
        // 检查程序化注册的策略
        ValueObjectCompareStrategy registeredStrategy = DiffRegistry.getRegisteredStrategy(clazz);
        if (registeredStrategy != ValueObjectCompareStrategy.AUTO) {
            log.debug("Resolved strategy {} via programmatic registration for {}", 
                registeredStrategy, clazz.getSimpleName());
            return registeredStrategy;
        }
        
        // 默认使用FIELDS策略
        log.debug("Resolved default FIELDS strategy for {}", clazz.getSimpleName());
        return ValueObjectCompareStrategy.FIELDS;
    }
}