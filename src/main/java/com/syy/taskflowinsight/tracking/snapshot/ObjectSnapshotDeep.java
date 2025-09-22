package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 深度对象快照实现
 * 支持对象图的深度遍历和循环引用检测
 * 
 * 核心功能：
 * - 可控深度遍历（DFS）
 * - 循环引用检测
 * - 路径匹配过滤
 * - 集合摘要化
 * - 性能监控
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class ObjectSnapshotDeep {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectSnapshotDeep.class);
    
    // 性能指标
    private static final AtomicLong depthLimitReached = new AtomicLong(0);
    private static final AtomicLong cycleDetected = new AtomicLong(0);
    private static final AtomicLong pathExcluded = new AtomicLong(0);
    private static final AtomicInteger currentStackDepth = new AtomicInteger(0);
    
    private final SnapshotConfig config;
    private final CollectionSummary collectionSummary;
    private TrackingOptions.CollectionStrategy collectionStrategy;
    
    public ObjectSnapshotDeep(SnapshotConfig config) {
        this.config = config;
        this.collectionSummary = new CollectionSummary();
        this.collectionStrategy = TrackingOptions.CollectionStrategy.SUMMARY; // 默认策略
    }
    
    /**
     * 设置集合处理策略
     */
    public void setCollectionStrategy(TrackingOptions.CollectionStrategy strategy) {
        this.collectionStrategy = strategy != null ? strategy : TrackingOptions.CollectionStrategy.SUMMARY;
    }
    
    /**
     * 执行深度快照捕获
     * 
     * @param root 根对象
     * @param maxDepth 最大深度
     * @param includeFields 包含字段集合
     * @param excludePatterns 排除模式集合
     * @return 扁平化的路径-值映射
     */
    public Map<String, Object> captureDeep(Object root, int maxDepth, 
                                          Set<String> includeFields, 
                                          Set<String> excludePatterns) {
        if (root == null) {
            return Collections.emptyMap();
        }
        
        // 使用IdentityHashMap进行循环引用检测
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        try {
            traverseDFS(root, "", 0, maxDepth, result, visited, includeFields, excludePatterns, startTime);
        } catch (StackOverflowError e) {
            logger.error("Stack overflow during deep snapshot traversal", e);
            throw new RuntimeException("Deep snapshot failed: stack overflow", e);
        }
        
        return result;
    }
    
    /**
     * DFS深度优先遍历
     */
    private void traverseDFS(Object obj, String path, int depth, int maxDepth,
                            Map<String, Object> result, Set<Object> visited,
                            Set<String> includeFields, Set<String> excludePatterns,
                            long startTime) {
        
        // 检查时间预算
        if (System.currentTimeMillis() - startTime > config.getTimeBudgetMs()) {
            logger.debug("Time budget exceeded at path: {}", path);
            return;
        }
        
        // 检查栈深度
        int stackDepth = currentStackDepth.incrementAndGet();
        try {
            if (stackDepth > config.getMaxStackDepth()) {
                logger.warn("Max stack depth {} reached at path: {}", config.getMaxStackDepth(), path);
                return;
            }
            
            // 深度检查
            if (depth >= maxDepth) {
                depthLimitReached.incrementAndGet();
                logger.debug("Depth limit {} reached at path: {}", maxDepth, path);
                result.put(path, "<depth-limit>");
                return;
            }
            
            // 空值处理
            if (obj == null) {
                result.put(path, null);
                return;
            }
            
            // 基本类型和字符串处理
            if (isSimpleType(obj)) {
                result.put(path, formatSimpleValue(obj));
                return;
            }
            
            // 循环引用检测
            if (!visited.add(obj)) {
                cycleDetected.incrementAndGet();
                logger.debug("Cycle detected at path: {}", path);
                result.put(path, "<circular-reference>");
                return;
            }
            
            try {
                // 集合处理
                if (obj instanceof Collection) {
                    handleCollection(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                    return;
                }
                
                // Map处理
                if (obj instanceof Map) {
                    handleMap(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                    return;
                }
                
                // 数组处理
                if (obj.getClass().isArray()) {
                    handleArray(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                    return;
                }
                
                // 普通对象处理
                handleObject(obj, path, depth, maxDepth, result, visited, includeFields, excludePatterns, startTime);
                
            } finally {
                // 移除访问标记，允许其他路径访问
                visited.remove(obj);
            }
            
        } finally {
            currentStackDepth.decrementAndGet();
        }
    }
    
    /**
     * 处理普通对象
     */
    private void handleObject(Object obj, String path, int depth, int maxDepth,
                            Map<String, Object> result, Set<Object> visited,
                            Set<String> includeFields, Set<String> excludePatterns,
                            long startTime) {
        
        Class<?> clazz = obj.getClass();
        Field[] fields = getAllFields(clazz);
        
        for (Field field : fields) {
            // 跳过静态字段
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            
            String fieldName = field.getName();
            String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;
            
            // 路径过滤
            if (shouldExcludePath(fieldPath, includeFields, excludePatterns)) {
                pathExcluded.incrementAndGet();
                continue;
            }
            
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                
                if (fieldValue == null) {
                    result.put(fieldPath, null);
                } else if (isSimpleType(fieldValue)) {
                    result.put(fieldPath, formatSimpleValue(fieldValue));
                } else {
                    // 递归处理复杂类型
                    traverseDFS(fieldValue, fieldPath, depth + 1, maxDepth, 
                              result, visited, includeFields, excludePatterns, startTime);
                }
                
            } catch (IllegalAccessException e) {
                logger.debug("Cannot access field {} at path {}", fieldName, fieldPath);
                result.put(fieldPath, "<inaccessible>");
            }
        }
    }
    
    /**
     * 处理集合
     */
    private void handleCollection(Object obj, String path, int depth, int maxDepth,
                                Map<String, Object> result, Set<Object> visited,
                                Set<String> includeFields, Set<String> excludePatterns,
                                long startTime) {
        
        Collection<?> collection = (Collection<?>) obj;
        
        // 根据策略处理集合
        switch (collectionStrategy) {
            case IGNORE:
                // 忽略集合内容，只记录类型和大小
                result.put(path, collection.getClass().getSimpleName() + "[" + collection.size() + "]");
                return;
                
            case SUMMARY:
                // 使用摘要策略
                if (collection.size() > config.getCollectionSummaryThreshold()) {
                    result.put(path, collectionSummary.summarize(collection));
                    return;
                }
                // 小集合仍然展开处理
                break;
                
            case ELEMENT:
                // 逐元素处理，不管大小
                break;
        }
        
        // 展开处理集合元素
        int index = 0;
        int maxElements = (collectionStrategy == TrackingOptions.CollectionStrategy.ELEMENT) ? 
            Integer.MAX_VALUE : 100;
            
        for (Object item : collection) {
            String itemPath = path + "[" + index + "]";
            
            if (item == null) {
                result.put(itemPath, null);
            } else if (isSimpleType(item)) {
                result.put(itemPath, formatSimpleValue(item));
            } else {
                traverseDFS(item, itemPath, depth + 1, maxDepth, 
                          result, visited, includeFields, excludePatterns, startTime);
            }
            
            index++;
            
            // 限制处理数量（除非是ELEMENT策略）
            if (index >= maxElements) {
                if (collectionStrategy != TrackingOptions.CollectionStrategy.ELEMENT) {
                    result.put(path + "[...]", "truncated at " + maxElements + " items");
                }
                break;
            }
        }
        
        // 为ELEMENT策略添加元数据
        if (collectionStrategy == TrackingOptions.CollectionStrategy.ELEMENT) {
            result.put(path + ".size", collection.size());
            result.put(path + ".type", collection.getClass().getSimpleName());
        }
    }
    
    /**
     * 处理Map
     */
    private void handleMap(Object obj, String path, int depth, int maxDepth,
                         Map<String, Object> result, Set<Object> visited,
                         Set<String> includeFields, Set<String> excludePatterns,
                         long startTime) {
        
        Map<?, ?> map = (Map<?, ?>) obj;
        
        // 大Map使用摘要
        if (map.size() > config.getCollectionSummaryThreshold()) {
            result.put(path, collectionSummary.summarize(map.values()));
            return;
        }
        
        // 小Map展开处理
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String entryPath = path + "['" + key + "']";
            Object value = entry.getValue();
            
            if (value == null) {
                result.put(entryPath, null);
            } else if (isSimpleType(value)) {
                result.put(entryPath, formatSimpleValue(value));
            } else {
                traverseDFS(value, entryPath, depth + 1, maxDepth, 
                          result, visited, includeFields, excludePatterns, startTime);
            }
            
            count++;
            
            // 限制处理数量
            if (count >= 100) {
                result.put(path + "[...]", "truncated at 100 entries");
                break;
            }
        }
    }
    
    /**
     * 处理数组
     */
    private void handleArray(Object obj, String path, int depth, int maxDepth,
                           Map<String, Object> result, Set<Object> visited,
                           Set<String> includeFields, Set<String> excludePatterns,
                           long startTime) {
        
        Class<?> componentType = obj.getClass().getComponentType();
        
        // 基本类型数组
        if (componentType.isPrimitive()) {
            result.put(path, formatPrimitiveArray(obj));
            return;
        }
        
        // 对象数组
        Object[] array = (Object[]) obj;
        
        // 大数组使用摘要
        if (array.length > config.getCollectionSummaryThreshold()) {
            result.put(path, "[array length=" + array.length + "]");
            return;
        }
        
        // 小数组展开处理
        for (int i = 0; i < Math.min(array.length, 100); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = array[i];
            
            if (item == null) {
                result.put(itemPath, null);
            } else if (isSimpleType(item)) {
                result.put(itemPath, formatSimpleValue(item));
            } else {
                traverseDFS(item, itemPath, depth + 1, maxDepth, 
                          result, visited, includeFields, excludePatterns, startTime);
            }
        }
        
        if (array.length > 100) {
            result.put(path + "[...]", "truncated at 100 items");
        }
    }
    
    /**
     * 判断是否为简单类型
     */
    private boolean isSimpleType(Object obj) {
        return obj instanceof String ||
               obj instanceof Number ||
               obj instanceof Boolean ||
               obj instanceof Character ||
               obj instanceof Date ||
               obj instanceof Enum ||
               obj.getClass().isPrimitive();
    }
    
    /**
     * 格式化简单值
     */
    private Object formatSimpleValue(Object value) {
        if (value instanceof Date) {
            // Date深拷贝
            return new Date(((Date) value).getTime());
        }
        
        if (value instanceof String) {
            String str = (String) value;
            // 截断超长字符串
            if (str.length() > 1000) {
                return str.substring(0, 1000) + "... (truncated)";
            }
        }
        
        return value;
    }
    
    /**
     * 格式化基本类型数组
     */
    private String formatPrimitiveArray(Object array) {
        String className = array.getClass().getComponentType().getSimpleName();
        int length = java.lang.reflect.Array.getLength(array);
        
        if (length == 0) {
            return className + "[0]";
        }
        
        if (length > 10) {
            return className + "[" + length + "]";
        }
        
        // 显示前10个元素
        StringBuilder sb = new StringBuilder(className).append("[");
        for (int i = 0; i < Math.min(length, 10); i++) {
            if (i > 0) sb.append(", ");
            sb.append(java.lang.reflect.Array.get(array, i));
        }
        if (length > 10) {
            sb.append(", ...");
        }
        sb.append("]");
        
        return sb.toString();
    }
    
    /**
     * 获取类的所有字段（包括父类）
     */
    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        
        return fields.toArray(new Field[0]);
    }
    
    /**
     * 判断路径是否应该被排除
     */
    private boolean shouldExcludePath(String path, Set<String> includeFields, Set<String> excludePatterns) {
        // 如果指定了包含字段，只处理这些字段
        if (!includeFields.isEmpty()) {
            boolean included = includeFields.stream()
                .anyMatch(field -> path.equals(field) || path.startsWith(field + "."));
            if (!included) {
                return true;
            }
        }
        
        // 检查排除模式
        return excludePatterns.stream()
            .anyMatch(pattern -> matchesPattern(path, pattern));
    }
    
    /**
     * 简单的通配符匹配
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        
        // 处理*.field模式（匹配任何以.field结尾的路径）
        if (pattern.startsWith("*.")) {
            String fieldName = pattern.substring(2);
            return path.endsWith("." + fieldName) || path.equals(fieldName);
        }
        
        // 转换通配符为正则表达式
        String regex = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^.]*")
            .replace("?", ".");
            
        return path.matches(regex);
    }
    
    /**
     * 获取性能指标
     */
    public static Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("depth.limit.reached", depthLimitReached.get());
        metrics.put("cycle.detected", cycleDetected.get());
        metrics.put("path.excluded", pathExcluded.get());
        metrics.put("current.stack.depth", (long) currentStackDepth.get());
        return metrics;
    }
    
    /**
     * 重置性能指标
     */
    public static void resetMetrics() {
        depthLimitReached.set(0);
        cycleDetected.set(0);
        pathExcluded.set(0);
        currentStackDepth.set(0);
    }
}