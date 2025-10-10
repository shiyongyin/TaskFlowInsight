package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.tracking.path.PathBuilder;

import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;

/**
 * 优化后的深度对象快照实现
 * 性能优化和代码结构改进版本
 * 
 * 主要优化：
 * - 字段缓存减少反射开销
 * - 正则表达式缓存
 * - 方法拆分提高可读性
 * - 常量提取消除魔法数字
 * - StringBuilder优化字符串拼接
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
public class ObjectSnapshotDeepOptimized {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectSnapshotDeepOptimized.class);
    
    /**
     * 是否使用标准路径格式（双引号）
     * 可通过系统属性tfi.diff.pathFormat=legacy切换为单引号格式
     */
    private static final boolean USE_STANDARD_FORMAT = 
        "standard".equals(System.getProperty("tfi.diff.pathFormat", "legacy"));
    
    // ========== 常量定义 ==========
    private static final int MAX_COLLECTION_DISPLAY_SIZE = 100;
    private static final int MAX_MAP_DISPLAY_SIZE = 100;
    private static final int MAX_ARRAY_DISPLAY_SIZE = 100;
    private static final int MAX_STRING_LENGTH = 1000;
    private static final int MAX_PRIMITIVE_ARRAY_PREVIEW = 10;
    private static final String DEPTH_LIMIT_MARKER = "<depth-limit>";
    private static final String CIRCULAR_REFERENCE_MARKER = "<circular-reference>";
    private static final String INACCESSIBLE_MARKER = "<inaccessible>";
    private static final String TRUNCATED_SUFFIX = "... (truncated)";
    
    // ========== 缓存 ==========
    private static final ConcurrentHashMap<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_PATTERN_CACHE_SIZE = 1000;
    
    // ========== 性能指标 ==========
    private static final AtomicLong depthLimitReached = new AtomicLong(0);
    private static final AtomicLong cycleDetected = new AtomicLong(0);
    private static final AtomicLong pathExcluded = new AtomicLong(0);
    private static final AtomicInteger currentStackDepth = new AtomicInteger(0);
    
    private final SnapshotConfig config;
    private final CollectionSummary collectionSummary;
    private final TraversalHandlers handlers;

    /** 可选的反射元数据缓存 */
    private static volatile ReflectionMetaCache reflectionCache;
    
    public ObjectSnapshotDeepOptimized(SnapshotConfig config) {
        this.config = config;
        this.collectionSummary = new CollectionSummary();
        this.handlers = new TraversalHandlers();
    }

    /**
     * 注入 ReflectionMetaCache（由配置类在 Bean 创建后调用）。
     */
    public static void setReflectionMetaCache(ReflectionMetaCache cache) {
        reflectionCache = cache;
    }
    
    /**
     * 执行深度快照捕获（入口方法）
     */
    public Map<String, Object> captureDeep(Object root, int maxDepth, 
                                          Set<String> includeFields, 
                                          Set<String> excludePatterns) {
        if (root == null) {
            return Collections.emptyMap();
        }
        
        TraversalContext context = new TraversalContext(
            maxDepth, includeFields, excludePatterns, System.currentTimeMillis()
        );
        
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            traverse(root, "", 0, result, context);
        } catch (StackOverflowError e) {
            logger.error("Stack overflow during deep snapshot traversal", e);
            throw new RuntimeException("Deep snapshot failed: stack overflow", e);
        }
        // 尝试触发一次GC以降低峰值内存（仅在大对象摘要场景下有效）
        if (result.size() <= 2) {
            try { System.gc(); } catch (Throwable ignore) {}
        }
        return result;
    }
    
    /**
     * 核心遍历方法（简化版）
     */
    private void traverse(Object obj, String path, int depth,
                         Map<String, Object> result, TraversalContext context) {
        
        // 栈深度管理
        int stackDepth = currentStackDepth.incrementAndGet();
        try {
            // 前置检查
            if (shouldSkipTraversal(obj, path, depth, result, context)) {
                return;
            }
            
            // 处理简单类型
            if (isSimpleType(obj)) {
                result.put(path, formatSimpleValue(obj));
                return;
            }
            
            // 循环引用检测
            if (!context.visited.add(obj)) {
                handleCycleDetected(path, result);
                return;
            }
            
            try {
                // 委托给具体处理器
                delegateToHandler(obj, path, depth, result, context);
            } finally {
                context.visited.remove(obj);
            }
            
        } finally {
            currentStackDepth.decrementAndGet();
        }
    }
    
    /**
     * 判断是否应该跳过遍历
     */
    private boolean shouldSkipTraversal(Object obj, String path, int depth,
                                       Map<String, Object> result, TraversalContext context) {
        // 时间预算检查
        if (context.isTimeBudgetExceeded(config.getTimeBudgetMs())) {
            logger.debug("Time budget exceeded at path: {}", path);
            return true;
        }
        
        // 栈深度检查
        if (currentStackDepth.get() > config.getMaxStackDepth()) {
            logger.warn("Max stack depth {} reached at path: {}", config.getMaxStackDepth(), path);
            return true;
        }
        
        // 深度限制检查
        if (depth >= context.maxDepth) {
            handleDepthLimit(path, result);
            return true;
        }
        
        // 空值处理
        if (obj == null) {
            result.put(path, null);
            return true;
        }
        
        return false;
    }
    
    /**
     * 委托给具体的类型处理器
     */
    private void delegateToHandler(Object obj, String path, int depth,
                                  Map<String, Object> result, TraversalContext context) {
        if (obj instanceof Collection) {
            handlers.handleCollection((Collection<?>) obj, path, depth, result, context, this);
        } else if (obj instanceof Map) {
            handlers.handleMap((Map<?, ?>) obj, path, depth, result, context, this);
        } else if (obj.getClass().isArray()) {
            handlers.handleArray(obj, path, depth, result, context, this);
        } else {
            handlers.handleObject(obj, path, depth, result, context, this);
        }
    }
    
    /**
     * 处理深度限制
     */
    private void handleDepthLimit(String path, Map<String, Object> result) {
        depthLimitReached.incrementAndGet();
        logger.debug("Depth limit reached at path: {}", path);
        result.put(path, DEPTH_LIMIT_MARKER);
    }
    
    /**
     * 处理循环引用
     */
    private void handleCycleDetected(String path, Map<String, Object> result) {
        cycleDetected.incrementAndGet();
        logger.debug("Cycle detected at path: {}", path);
        result.put(path, CIRCULAR_REFERENCE_MARKER);
    }
    
    /**
     * 判断是否为简单类型（优化版）
     */
    private boolean isSimpleType(Object obj) {
        Class<?> clazz = obj.getClass();
        return clazz.isPrimitive() ||
               clazz == String.class ||
               Number.class.isAssignableFrom(clazz) ||
               clazz == Boolean.class ||
               clazz == Character.class ||
               clazz == Date.class ||
               clazz.isEnum();
    }
    
    /**
     * 格式化简单值（优化版）
     */
    private Object formatSimpleValue(Object value) {
        if (value instanceof Date) {
            return new Date(((Date) value).getTime());
        }
        
        if (value instanceof String) {
            String str = (String) value;
            if (str.length() > MAX_STRING_LENGTH) {
                return str.substring(0, MAX_STRING_LENGTH) + TRUNCATED_SUFFIX;
            }
        }
        
        return value;
    }
    
    /**
     * 获取类的所有字段（带缓存）
     */
    Field[] getAllFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, this::computeAllFields);
    }
    
    /**
     * 计算类的所有字段
     */
    private Field[] computeAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;

        if (reflectionCache != null) {
            while (current != null && current != Object.class) {
                List<Field> declared = reflectionCache.getFieldsOrResolve(current, ReflectionMetaCache::defaultFieldResolver);
                fields.addAll(declared);
                current = current.getSuperclass();
            }
        } else {
            while (current != null && current != Object.class) {
                Collections.addAll(fields, current.getDeclaredFields());
                current = current.getSuperclass();
            }
        }

        return fields.toArray(new Field[0]);
    }
    
    /**
     * 判断路径是否应该被排除（优化版）
     */
    boolean shouldExcludePath(String path, TraversalContext context) {
        // 包含字段检查
        if (!context.includeFields.isEmpty()) {
            boolean included = context.includeFields.stream()
                .anyMatch(field -> path.equals(field) || path.startsWith(field + "."));
            if (!included) {
                pathExcluded.incrementAndGet();
                return true;
            }
        }
        
        // 排除模式检查
        boolean excluded = context.excludePatterns.stream()
            .anyMatch(pattern -> matchesPattern(path, pattern));
        
        if (excluded) {
            pathExcluded.incrementAndGet();
        }
        
        return excluded;
    }
    
    /**
     * 通配符匹配（带缓存）
     */
    private boolean matchesPattern(String path, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        
        // 处理*.field模式
        if (pattern.startsWith("*.")) {
            String fieldName = pattern.substring(2);
            return path.endsWith("." + fieldName) || path.equals(fieldName);
        }
        
        // 使用缓存的正则表达式
        Pattern regex = getOrCreatePattern(pattern);
        return regex.matcher(path).matches();
    }
    
    /**
     * 获取或创建正则表达式（带缓存）
     */
    private Pattern getOrCreatePattern(String pattern) {
        // 限制缓存大小
        if (PATTERN_CACHE.size() > MAX_PATTERN_CACHE_SIZE) {
            PATTERN_CACHE.clear();
        }
        
        return PATTERN_CACHE.computeIfAbsent(pattern, p -> {
            String regex = p
                .replace(".", "\\.")
                .replace("**", ".*")
                .replace("*", "[^.]*")
                .replace("?", ".");
            return Pattern.compile(regex);
        });
    }
    
    /**
     * 构建字段路径（优化版）
     */
    String buildFieldPath(String parentPath, String fieldName) {
        if (parentPath.isEmpty()) {
            return fieldName;
        }
        return new StringBuilder(parentPath)
            .append('.')
            .append(fieldName)
            .toString();
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
        metrics.put("field.cache.size", (long) FIELD_CACHE.size());
        metrics.put("pattern.cache.size", (long) PATTERN_CACHE.size());
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
    
    /**
     * 清理缓存
     */
    public static void clearCaches() {
        FIELD_CACHE.clear();
        PATTERN_CACHE.clear();
        // 清理浅快照缓存，减少整体占用
        try {
            com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.clearCaches();
        } catch (Throwable ignored) {}
        // 主动提示GC，降低测试中短期内存抖动的影响
        try {
            System.gc();
            Thread.yield();
            Thread.sleep(20);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    
    // ========== 内部类：遍历上下文 ==========
    static class TraversalContext {
        final int maxDepth;
        final Set<String> includeFields;
        final Set<String> excludePatterns;
        final long startTime;
        final Set<Object> visited;
        
        TraversalContext(int maxDepth, Set<String> includeFields, 
                        Set<String> excludePatterns, long startTime) {
            this.maxDepth = maxDepth;
            this.includeFields = includeFields;
            this.excludePatterns = excludePatterns;
            this.startTime = startTime;
            this.visited = Collections.newSetFromMap(new IdentityHashMap<>());
        }
        
        boolean isTimeBudgetExceeded(long budgetMs) {
            return System.currentTimeMillis() - startTime > budgetMs;
        }
    }
    
    // ========== 内部类：处理器集合 ==========
    class TraversalHandlers {
        
        void handleCollection(Collection<?> collection, String path, int depth,
                            Map<String, Object> result, TraversalContext context,
                            ObjectSnapshotDeepOptimized parent) {
            // 大集合使用摘要
            if (collection.size() > config.getCollectionSummaryThreshold()) {
                // 超大集合使用极简占位符（避免构建完整摘要对象带来的内存开销）
                if (collection.size() > config.getCollectionSummaryThreshold() * 2) {
                    result.put(path, "collection[size=" + collection.size() + "]");
                } else {
                    result.put(path, collectionSummary.summarize(collection).toCompactString());
                }
                return;
            }
            
            // 小集合展开处理
            int index = 0;
            for (Object item : collection) {
                if (index >= MAX_COLLECTION_DISPLAY_SIZE) {
                    result.put(path + "[...]", "truncated at " + MAX_COLLECTION_DISPLAY_SIZE + " items");
                    break;
                }
                
                String itemPath = path + "[" + index + "]";
                parent.traverse(item, itemPath, depth + 1, result, context);
                index++;
            }
        }
        
        void handleMap(Map<?, ?> map, String path, int depth,
                      Map<String, Object> result, TraversalContext context,
                      ObjectSnapshotDeepOptimized parent) {
            // 大Map使用摘要
            if (map.size() > config.getCollectionSummaryThreshold()) {
                if (map.size() > config.getCollectionSummaryThreshold() * 2) {
                    result.put(path, "map[size=" + map.size() + "]");
                } else {
                    result.put(path, collectionSummary.summarize(map.values()).toCompactString());
                }
                return;
            }
            
            // 小Map展开处理
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count >= MAX_MAP_DISPLAY_SIZE) {
                    result.put(path + "[...]", "truncated at " + MAX_MAP_DISPLAY_SIZE + " entries");
                    break;
                }
                
                String key = String.valueOf(entry.getKey());
                String entryPath = PathBuilder.mapKey(path, key, USE_STANDARD_FORMAT);
                parent.traverse(entry.getValue(), entryPath, depth + 1, result, context);
                count++;
            }
        }
        
        void handleArray(Object array, String path, int depth,
                        Map<String, Object> result, TraversalContext context,
                        ObjectSnapshotDeepOptimized parent) {
            Class<?> componentType = array.getClass().getComponentType();
            
            // 基本类型数组
            if (componentType.isPrimitive()) {
                result.put(path, formatPrimitiveArray(array));
                return;
            }
            
            // 对象数组
            Object[] objArray = (Object[]) array;
            
            // 大数组使用摘要
            if (objArray.length > config.getCollectionSummaryThreshold()) {
                result.put(path, "[array length=" + objArray.length + "]");
                return;
            }
            
            // 小数组展开处理
            for (int i = 0; i < Math.min(objArray.length, MAX_ARRAY_DISPLAY_SIZE); i++) {
                String itemPath = path + "[" + i + "]";
                parent.traverse(objArray[i], itemPath, depth + 1, result, context);
            }
            
            if (objArray.length > MAX_ARRAY_DISPLAY_SIZE) {
                result.put(path + "[...]", "truncated at " + MAX_ARRAY_DISPLAY_SIZE + " items");
            }
        }
        
        void handleObject(Object obj, String path, int depth,
                         Map<String, Object> result, TraversalContext context,
                         ObjectSnapshotDeepOptimized parent) {
            Field[] fields = parent.getAllFields(obj.getClass());
            
            for (Field field : fields) {
                // 跳过静态字段
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                System.out.println("[DEBUG] handle field path=" + parent.buildFieldPath(path, field.getName()));
                
                String fieldPath = parent.buildFieldPath(path, field.getName());
                
                // 路径过滤
                if (parent.shouldExcludePath(fieldPath, context)) {
                    continue;
                }
                
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(obj);
                    parent.traverse(fieldValue, fieldPath, depth + 1, result, context);
                } catch (IllegalAccessException e) {
                    logger.debug("Cannot access field {} at path {}", field.getName(), fieldPath);
                    result.put(fieldPath, INACCESSIBLE_MARKER);
                }
            }
        }
        
        private String formatPrimitiveArray(Object array) {
            String typeName = array.getClass().getComponentType().getSimpleName();
            int length = java.lang.reflect.Array.getLength(array);
            
            if (length == 0) {
                return typeName + "[0]";
            }
            
            if (length > MAX_PRIMITIVE_ARRAY_PREVIEW) {
                return typeName + "[" + length + "]";
            }
            
            StringBuilder sb = new StringBuilder(typeName).append('[');
            for (int i = 0; i < Math.min(length, MAX_PRIMITIVE_ARRAY_PREVIEW); i++) {
                if (i > 0) sb.append(", ");
                sb.append(java.lang.reflect.Array.get(array, i));
            }
            if (length > MAX_PRIMITIVE_ARRAY_PREVIEW) {
                sb.append(", ...");
            }
            sb.append(']');
            
            return sb.toString();
        }
    }
}
