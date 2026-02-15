package com.syy.taskflowinsight.registry;

import com.syy.taskflowinsight.annotation.ObjectType;
import com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 差异检测注册表
 * 提供程序化注册对象类型和比较策略的机制
 * 
 * 功能：
 * - 类型注册（Entity/ValueObject）
 * - 策略注册（ValueObject比较策略）
 * - 批量操作
 * - 统计查询
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
public final class DiffRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(DiffRegistry.class);
    
    private static final Map<Class<?>, ObjectType> TYPE_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ValueObjectCompareStrategy> STRATEGY_REGISTRY = new ConcurrentHashMap<>();
    
    private DiffRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * 注册类型为Entity
     */
    public static void registerEntity(Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        TYPE_REGISTRY.put(clazz, ObjectType.ENTITY);
        log.debug("Registered Entity: {}", clazz.getSimpleName());
    }
    
    /**
     * 注册类型为ValueObject，指定比较策略
     */
    public static void registerValueObject(Class<?> clazz, ValueObjectCompareStrategy strategy) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        
        TYPE_REGISTRY.put(clazz, ObjectType.VALUE_OBJECT);
        STRATEGY_REGISTRY.put(clazz, strategy);
        
        log.debug("Registered ValueObject: {} with strategy: {}", 
            clazz.getSimpleName(), strategy);
    }
    
    /**
     * 注册类型为ValueObject（默认FIELDS策略）
     */
    public static void registerValueObject(Class<?> clazz) {
        registerValueObject(clazz, ValueObjectCompareStrategy.FIELDS);
    }
    
    /**
     * 获取已注册的类型
     * 
     * @param clazz 目标类
     * @return 已注册的 {@link ObjectType}，如果未注册则返回 {@code null}
     */
    public static ObjectType getRegisteredType(Class<?> clazz) {
        return TYPE_REGISTRY.get(clazz); // 返回null表示未注册
    }
    
    /**
     * 获取已注册的比较策略
     * 
     * @param clazz 目标类
     * @return 已注册的 {@link ValueObjectCompareStrategy}；如果未显式注册，
     *         返回 {@link ValueObjectCompareStrategy#AUTO} 作为默认策略（never null）
     */
    public static ValueObjectCompareStrategy getRegisteredStrategy(Class<?> clazz) {
        return STRATEGY_REGISTRY.getOrDefault(clazz, ValueObjectCompareStrategy.AUTO);
    }
    
    /**
     * 批量注册配置
     */
    public static void registerAll(Map<Class<?>, ObjectType> types, 
                                  Map<Class<?>, ValueObjectCompareStrategy> strategies) {
        
        types.forEach((clazz, type) -> {
            TYPE_REGISTRY.put(clazz, type);
            ValueObjectCompareStrategy strategy = strategies.getOrDefault(clazz, ValueObjectCompareStrategy.AUTO);
            STRATEGY_REGISTRY.put(clazz, strategy);
        });
        
        log.info("Batch registered {} types and {} strategies", 
            types.size(), strategies.size());
    }
    
    /**
     * 清除指定类的注册信息
     */
    public static void unregister(Class<?> clazz) {
        ObjectType removedType = TYPE_REGISTRY.remove(clazz);
        ValueObjectCompareStrategy removedStrategy = STRATEGY_REGISTRY.remove(clazz);
        
        if (removedType != null) {
            log.debug("Unregistered class: {} (type: {}, strategy: {})", 
                clazz.getSimpleName(), removedType, removedStrategy);
        }
    }
    
    /**
     * 获取所有已注册的类型统计
     */
    public static Map<ObjectType, Long> getRegistrationStats() {
        return TYPE_REGISTRY.values().stream()
            .collect(Collectors.groupingBy(
                Function.identity(), 
                Collectors.counting()
            ));
    }
    
    /**
     * 清空所有注册信息（主要用于测试）
     */
    public static void clear() {
        TYPE_REGISTRY.clear();
        STRATEGY_REGISTRY.clear();
        log.debug("Cleared all registrations");
    }
    
    /**
     * 获取注册表大小
     */
    public static int size() {
        return TYPE_REGISTRY.size();
    }
}