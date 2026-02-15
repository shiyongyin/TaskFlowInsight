package com.syy.taskflowinsight.tracking.ssot.path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * PathNavigator: 解析对象路径并执行反射导航，支持集合/数组/Map 索引访问。
 *
 * 路径示例：
 * - Order.customer
 * - Order.items[0].supplier
 * - Order.attributes[region].owner
 * - root.array[2].field
 */
public final class PathNavigator {

    private static final Logger logger = LoggerFactory.getLogger(PathNavigator.class);

    private PathNavigator() {}

    // 简易字段缓存：owner类 + 字段名 -> Field（避免重复反射开销）
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field>> FIELD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 解析路径并返回最终对象（可能为 null）。
     */
    public static Object resolve(Object root, String path) {
        if (root == null || path == null || path.isEmpty()) return null;
        try {
            Object current = root;
            Class<?> currentClass = current.getClass();
            String[] parts = path.split("\\.");
            int startIdx = 0;
            if (parts.length > 0 && parts[0].equals(currentClass.getSimpleName())) {
                startIdx = 1;
            }

            for (int i = startIdx; i < parts.length; i++) {
                String segment = parts[i];
                int lb = segment.indexOf('[');
                String fieldName = (lb > 0) ? segment.substring(0, lb) : segment;

                Field f = getDeclaredFieldRecursive(currentClass, fieldName);
                if (f == null) return null;
                f.setAccessible(true);
                Object value = safeGet(f, current);
                if (lb > 0) {
                    int rb = segment.indexOf(']', lb);
                    if (rb < 0) return null;
                    String keyText = segment.substring(lb + 1, rb);
                    value = navigateIndex(value, keyText);
                }
                if (i == parts.length - 1) {
                    return value;
                }
                if (value == null) return null;
                current = value;
                currentClass = current.getClass();
            }
            return current;
        } catch (Exception e) {
            logger.debug("Path resolution failed for '{}': {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 判断路径对应的字段是否带指定注解（仅检查末段字段）。
     */
    public static boolean isAnnotatedField(Object root, String path, Class<? extends Annotation> ann) {
        if (root == null || path == null || path.isEmpty() || ann == null) return false;
        try {
            Object current = root;
            Class<?> currentClass = current.getClass();
            String[] parts = path.split("\\.");
            int startIdx = 0;
            if (parts.length > 0 && parts[0].equals(currentClass.getSimpleName())) {
                startIdx = 1;
            }

            for (int i = startIdx; i < parts.length; i++) {
                String segment = parts[i];
                int lb = segment.indexOf('[');
                String fieldName = (lb > 0) ? segment.substring(0, lb) : segment;
                Field f = getDeclaredFieldRecursive(currentClass, fieldName);
                if (f == null) return false;
                if (i == parts.length - 1) {
                    return f.isAnnotationPresent(ann);
                }
                f.setAccessible(true);
                Object value = safeGet(f, current);
                if (lb > 0) {
                    int rb = segment.indexOf(']', lb);
                    if (rb < 0) return false;
                    String keyText = segment.substring(lb + 1, rb);
                    value = navigateIndex(value, keyText);
                }
                if (value == null) return false;
                current = value;
                currentClass = current.getClass();
            }
            return false;
        } catch (Exception e) {
            logger.debug("Annotation check failed for path '{}': {}", path, e.getMessage());
            return false;
        }
    }

    private static Object navigateIndex(Object container, String keyText) {
        if (container == null) return null;
        try {
            if (container instanceof java.util.List<?> list) {
                int idx = Integer.parseInt(keyText);
                return (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
            } else if (container instanceof java.util.Map<?,?> map) {
                Object k = keyText;
                if (!map.containsKey(k)) {
                    try { k = Integer.valueOf(keyText); } catch (Exception ignored) {}
                }
                return map.get(k);
            } else if (container.getClass().isArray()) {
                int idx = Integer.parseInt(keyText);
                int len = java.lang.reflect.Array.getLength(container);
                return (idx >= 0 && idx < len) ? java.lang.reflect.Array.get(container, idx) : null;
            }
        } catch (Exception e) {
            logger.debug("Index navigation failed for key '{}': {}", keyText, e.getMessage());
            return null;
        }
        return null;
    }

    private static Field getDeclaredFieldRecursive(Class<?> clazz, String name) {
        if (clazz == null || name == null) return null;
        // 先查缓存
        java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field> byName = FIELD_CACHE.computeIfAbsent(clazz, k -> new java.util.concurrent.ConcurrentHashMap<>());
        java.lang.reflect.Field cached = byName.get(name);
        if (cached != null) {
            return cached;
        }
        // 未命中则递归查找
        java.lang.reflect.Field found = null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                found = c.getDeclaredField(name);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (found != null) {
            // 使用 putIfAbsent 的返回值：如果另一个线程先插入了，使用其缓存值
            Field existing = byName.putIfAbsent(name, found);
            return existing != null ? existing : found;
        }
        return null;
    }

    private static Object safeGet(Field f, Object obj) {
        try { return f.get(obj); } catch (Exception e) { return null; }
    }
}
