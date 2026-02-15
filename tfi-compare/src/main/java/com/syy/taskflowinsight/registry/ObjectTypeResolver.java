package com.syy.taskflowinsight.registry;

import com.syy.taskflowinsight.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对象类型解析器
 * 按照6级优先级顺序解析对象类型，支持缓存机制
 * 
 * 优先级顺序：
 * 1. @Entity注解
 * 2. @ValueObject注解  
 * 3. JPA注解检测
 * 4. @Key字段检测
 * 5. 程序化注册
 * 6. 类型特征判断
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
public final class ObjectTypeResolver {
    
    private static final Logger log = LoggerFactory.getLogger(ObjectTypeResolver.class);
    
    private static final Map<Class<?>, ObjectType> TYPE_CACHE = new ConcurrentHashMap<>();
    
    private ObjectTypeResolver() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * 解析对象类型，按优先级顺序检查（带缓存）
     */
    public static ObjectType resolveType(Object object) {
        if (object == null) {
            return ObjectType.BASIC_TYPE; // null作为基本类型处理
        }
        
        Class<?> clazz = object.getClass();
        
        // 使用缓存避免重复反射
        return TYPE_CACHE.computeIfAbsent(clazz, ObjectTypeResolver::doResolveType);
    }
    
    /**
     * 根据Class解析类型（主要用于测试）
     */
    public static ObjectType resolveType(Class<?> clazz) {
        if (clazz == null) {
            return ObjectType.BASIC_TYPE;
        }
        
        return TYPE_CACHE.computeIfAbsent(clazz, ObjectTypeResolver::doResolveType);
    }
    
    /**
     * 实际的类型解析逻辑
     */
    private static ObjectType doResolveType(Class<?> clazz) {
        // 优先级1: @Entity注解
        if (clazz.isAnnotationPresent(Entity.class)) {
            log.debug("Resolved {} as ENTITY via @Entity annotation", clazz.getSimpleName());
            return ObjectType.ENTITY;
        }
        
        // 优先级2: @ValueObject注解
        if (clazz.isAnnotationPresent(ValueObject.class)) {
            log.debug("Resolved {} as VALUE_OBJECT via @ValueObject annotation", clazz.getSimpleName());
            return ObjectType.VALUE_OBJECT;
        }
        
        // 优先级3: JPA注解检测
        ObjectType jpaType = checkJpaAnnotations(clazz);
        if (jpaType != null) {
            log.debug("Resolved {} as {} via JPA annotations", clazz.getSimpleName(), jpaType);
            return jpaType;
        }
        
        // 优先级4: @Key字段检测
        if (hasKeyFields(clazz)) {
            log.debug("Resolved {} as ENTITY via @Key field detection", clazz.getSimpleName());
            return ObjectType.ENTITY;
        }
        
        // 优先级5: 程序化注册检查
        ObjectType registeredType = DiffRegistry.getRegisteredType(clazz);
        if (registeredType != null) {
            log.debug("Resolved {} as {} via programmatic registration", clazz.getSimpleName(), registeredType);
            return registeredType;
        }
        
        // 优先级6: 按类型特征判断
        if (isPrimitiveOrWrapper(clazz)) {
            log.debug("Resolved {} as BASIC_TYPE via primitive/wrapper detection", clazz.getSimpleName());
            return ObjectType.BASIC_TYPE;
        }
        
        if (isCollection(clazz)) {
            log.debug("Resolved {} as COLLECTION via collection detection", clazz.getSimpleName());
            return ObjectType.COLLECTION;
        }
        
        // 默认规则：非基本类型默认为ValueObject
        log.debug("Resolved {} as VALUE_OBJECT via default rule", clazz.getSimpleName());
        return ObjectType.VALUE_OBJECT;
    }
    
    /**
     * 检查JPA注解（可通过配置关闭，结果已缓存避免反射成本）
     */
    private static ObjectType checkJpaAnnotations(Class<?> clazz) {
        // 检查 @javax.persistence.Entity
        if (isAnnotationPresent(clazz, "javax.persistence.Entity") ||
            isAnnotationPresent(clazz, "jakarta.persistence.Entity")) {
            return ObjectType.ENTITY;
        }
        
        // 检查 @Embeddable
        if (isAnnotationPresent(clazz, "javax.persistence.Embeddable") ||
            isAnnotationPresent(clazz, "jakarta.persistence.Embeddable")) {
            return ObjectType.VALUE_OBJECT;
        }
        
        return null; // 无JPA注解时返回null，让调用方继续判断
    }
    
    /**
     * 检查是否存在@Key字段
     */
    private static boolean hasKeyFields(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
            .anyMatch(field -> field.isAnnotationPresent(Key.class));
    }
    
    /**
     * 检查是否为基本类型或包装类型
     */
    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() || 
               clazz == Boolean.class || clazz == Byte.class || 
               clazz == Character.class || clazz == Short.class ||
               clazz == Integer.class || clazz == Long.class ||
               clazz == Float.class || clazz == Double.class ||
               clazz == String.class || clazz == BigDecimal.class ||
               clazz == LocalDateTime.class || clazz == LocalDate.class;
    }
    
    /**
     * 检查是否为集合类型
     */
    private static boolean isCollection(Class<?> clazz) {
        return Collection.class.isAssignableFrom(clazz) || 
               Map.class.isAssignableFrom(clazz) ||
               clazz.isArray();
    }
    
    /**
     * 安全检查注解是否存在（避免ClassNotFoundException）
     */
    private static boolean isAnnotationPresent(Class<?> clazz, String annotationName) {
        try {
            Class<?> annotationClass = Class.forName(annotationName);
            if (Annotation.class.isAssignableFrom(annotationClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends Annotation> annClass = (Class<? extends Annotation>) annotationClass;
                return clazz.isAnnotationPresent(annClass);
            }
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 清空缓存（主要用于测试）
     */
    public static void clearCache() {
        TYPE_CACHE.clear();
        log.debug("Cleared type resolution cache");
    }
    
    /**
     * 获取缓存统计
     */
    public static int getCacheSize() {
        return TYPE_CACHE.size();
    }
}