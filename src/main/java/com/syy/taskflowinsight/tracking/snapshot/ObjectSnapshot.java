package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.tracking.format.ValueReprFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;

/**
 * 对象快照工具
 * 捕获对象的标量字段快照，仅支持基本类型、字符串和日期
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
public final class ObjectSnapshot {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectSnapshot.class);
    
    /** 最大缓存类数量 */
    private static final int MAX_CLASSES = 1024;
    
    /** 反射元数据缓存：类 -> (字段名 -> Field对象) */
    private static final ConcurrentHashMap<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    
    /** 默认值表示的最大长度 */
    public static final int DEFAULT_MAX_VALUE_LENGTH = 8192;
    /** 可配置的值表示最大长度（从配置注入） */
    private static volatile int maxValueLength = DEFAULT_MAX_VALUE_LENGTH;
    
    /** 截断后缀 */
    private static final String TRUNCATION_SUFFIX = "... (truncated)";
    
    private ObjectSnapshot() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 可选的反射元数据缓存 */
    private static volatile ReflectionMetaCache reflectionCache;

    /** 提供外部注入的缓存 */
    public static void setReflectionMetaCache(ReflectionMetaCache cache) {
        reflectionCache = cache;
    }
    
    /**
     * 捕获对象的字段快照
     * 
     * @param name 对象名称（用于日志和标识）
     * @param target 目标对象
     * @param fields 要捕获的字段名，如果为空则捕获所有标量字段
     * @return 字段名到值的映射，仅包含标量值
     */
    public static Map<String, Object> capture(String name, Object target, String... fields) {
        if (target == null) {
            logger.debug("Skip capturing null object: {}", name);
            return Collections.emptyMap();
        }
        
        try {
            Map<String, Field> fieldMap = getFieldMap(target.getClass());
            Map<String, Object> snapshot = new HashMap<>();
            
            // 确定要捕获的字段
            Collection<String> fieldsToCapture = (fields == null || fields.length == 0) 
                ? fieldMap.keySet() 
                : Arrays.asList(fields);
            
            for (String fieldName : fieldsToCapture) {
                Field field = fieldMap.get(fieldName);
                if (field == null) {
                    logger.debug("Field not found or not scalar: {}.{}", name, fieldName);
                    continue;
                }
                
                try {
                    Object value = field.get(target);
                    // 标量值处理和深拷贝
                    Object normalizedValue = normalizeValue(value);
                    if (normalizedValue != null || value == null) {
                        snapshot.put(fieldName, normalizedValue);
                    }
                } catch (IllegalAccessException e) {
                    logger.debug("Cannot access field {}.{}: {}", name, fieldName, e.getMessage());
                }
            }
            
            return snapshot;
            
        } catch (Exception e) {
            logger.debug("Failed to capture snapshot for {}: {}", name, e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    /**
     * 获取类的字段映射（带缓存）
     */
    private static Map<String, Field> getFieldMap(Class<?> clazz) {
        // 检查缓存上限
        if (FIELD_CACHE.size() >= MAX_CLASSES) {
            logger.warn("Field cache reached limit ({}), creating temporary field map", MAX_CLASSES);
            return buildFieldMap(clazz);
        }
        
        return FIELD_CACHE.computeIfAbsent(clazz, ObjectSnapshot::buildFieldMap);
    }

    /**
     * 清理ObjectSnapshot内部缓存（用于测试/诊断）
     */
    public static void clearCaches() {
        FIELD_CACHE.clear();
    }
    
    /**
     * 构建类的字段映射（仅包含标量字段）
     */
    private static Map<String, Field> buildFieldMap(Class<?> clazz) {
        Map<String, Field> fieldMap = new HashMap<>();
        
        // 遍历类及其父类的所有字段（最大深度2层）
        Class<?> current = clazz;
        int depth = 0;
        
        while (current != null && current != Object.class && depth < 2) {
            List<Field> declared = getDeclaredFieldsCached(current);
            for (Field field : declared) {
                if (isScalarType(field.getType())) {
                    field.setAccessible(true);
                    fieldMap.putIfAbsent(field.getName(), field);
                }
            }
            current = current.getSuperclass();
            depth++;
        }
        
        return fieldMap;
    }

    private static List<Field> getDeclaredFieldsCached(Class<?> c) {
        ReflectionMetaCache cache = reflectionCache;
        if (cache != null) {
            return cache.getFieldsOrResolve(c, ReflectionMetaCache::defaultFieldResolver);
        }
        return Arrays.asList(c.getDeclaredFields());
    }
    
    /**
     * 判断是否为标量类型
     * 修改：现在也包括Map和Collection类型，以支持集合比较
     */
    private static boolean isScalarType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Boolean.class ||
               type == Byte.class ||
               type == Short.class ||
               type == Character.class ||
               type == Date.class ||
               type.isEnum() ||
               Map.class.isAssignableFrom(type) ||     // 支持Map类型
               Collection.class.isAssignableFrom(type); // 支持Collection类型（包括List和Set）
    }
    
    /**
     * 归一化值（深拷贝Date，Map和Collection浅拷贝，其他标量直接返回）
     */
    private static Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }

        // Date深拷贝
        if (value instanceof Date) {
            return new Date(((Date) value).getTime());
        }

        // Map浅拷贝（保持引用以便DiffDetector进行比较）
        if (value instanceof Map) {
            return new HashMap<>((Map<?, ?>) value);
        }

        // Collection浅拷贝（保持引用以便DiffDetector进行比较）
        if (value instanceof Set) {
            return new HashSet<>((Set<?>) value);
        }
        if (value instanceof List) {
            return new ArrayList<>((List<?>) value);
        }
        if (value instanceof Collection) {
            return new ArrayList<>((Collection<?>) value);
        }

        // 标量类型直接返回
        if (isScalarType(value.getClass())) {
            return value;
        }

        // 非标量类型返回null（不采集）
        logger.debug("Skipping non-scalar value of type: {}", value.getClass().getName());
        return null;
    }
    
    /**
     * 生成值的字符串表示（转义和截断）
     * 
     * @param value 原始值
     * @return 转义和截断后的字符串表示
     */
    public static String repr(Object value) {
        return ValueReprFormatter.format(value);
    }

    /**
     * 设置全局的值表示最大长度（供配置注入调用）
     */
    public static void setMaxValueLength(int length) {
        if (length > 0) {
            maxValueLength = length;
        }
    }

    /**
     * 获取当前全局的值表示最大长度
     */
    public static int getMaxValueLength() {
        return maxValueLength;
    }
    
    /**
     * 生成值的字符串表示（转义和截断）
     * 
     * @param value 原始值
     * @param maxLength 最大长度
     * @return 转义和截断后的字符串表示
     */
    public static String repr(Object value, int maxLength) {
        if (value == null) {
            return "null";
        }
        
        String str;
        if (value instanceof Date) {
            // Date转为时间戳表示
            str = String.valueOf(((Date) value).getTime());
        } else {
            str = value.toString();
        }
        
        // 转义特殊字符
        str = escapeString(str);
        
        // 截断处理
        if (str.length() > maxLength) {
            return str.substring(0, maxLength - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
        }
        
        return str;
    }
    
    /**
     * 转义字符串中的特殊字符
     */
    private static String escapeString(String input) {
        if (input == null) {
            return "null";
        }
        
        return input.replace("\\", "\\\\")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                   .replace("\"", "\\\"");
    }
}
