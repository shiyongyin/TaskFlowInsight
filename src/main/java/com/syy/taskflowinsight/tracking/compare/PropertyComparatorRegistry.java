package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.CustomComparator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段级比较器注册表：负责注册、查找与实例缓存。
 * 解析顺序：Options.path > @CustomComparator(field) > 其余策略。
 */
public class PropertyComparatorRegistry {

    /** propertyPath -> comparator 实例（路径级注册）。 */
    private final Map<String, PropertyComparator> pathComparators = new ConcurrentHashMap<>();

    /** CustomComparator.value() -> comparator 实例（注解实例缓存）。 */
    private final Map<Class<? extends PropertyComparator>, PropertyComparator> instanceCache = new ConcurrentHashMap<>();

    // 简易指标占位（P0）：命中/未命中计数
    private final java.util.concurrent.atomic.AtomicLong pathHits = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong annotationHits = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong misses = new java.util.concurrent.atomic.AtomicLong();

    /** 注册路径级比较器。 */
    public void register(String propertyPath, PropertyComparator comparator) {
        if (propertyPath == null || propertyPath.isEmpty() || propertyPath.startsWith(".") || propertyPath.endsWith(".")) {
            throw new IllegalArgumentException("Invalid property path: " + propertyPath);
        }
        pathComparators.put(propertyPath, comparator);
    }

    /** 查找比较器（按解析顺序）。 */
    public PropertyComparator findComparator(String fullPath, Field field) {
        // 1) 路径注册优先（如 order.items[0].price）
        PropertyComparator byPath = pathComparators.get(fullPath);
        if (byPath != null) {
            pathHits.incrementAndGet();
            // 指标占位
            try {
                io.micrometer.core.instrument.Metrics.counter("tfi.compare.property.hits", "source", "path").increment();
            } catch (Throwable ignored) {}
            return byPath;
        }
        // 2) 注解注册
        if (field != null && field.isAnnotationPresent(CustomComparator.class)) {
            CustomComparator cc = field.getAnnotation(CustomComparator.class);
            Class<? extends PropertyComparator> clazz = cc.value();
            PropertyComparator comp = cc.cached()
                ? instanceCache.computeIfAbsent(clazz, this::instantiate)
                : instantiate(clazz);
            annotationHits.incrementAndGet();
            try {
                io.micrometer.core.instrument.Metrics.counter("tfi.compare.property.hits", "source", "annotation").increment();
            } catch (Throwable ignored) {}
            return comp;
        }
        // 3) 未找到
        misses.incrementAndGet();
        try {
            io.micrometer.core.instrument.Metrics.counter("tfi.compare.property.misses").increment();
        } catch (Throwable ignored) {}
        return null;
    }

    private PropertyComparator instantiate(Class<? extends PropertyComparator> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (PropertyComparator) ctor.newInstance();
        } catch (Exception e) {
            throw new PropertyComparisonException("Failed to instantiate comparator: " + clazz.getName(), e);
        }
    }

    /** 简易指标快照（占位）：用于外部观测命中/未命中 */
    public MetricsSnapshot getMetricsSnapshot() {
        return new MetricsSnapshot(pathHits.get(), annotationHits.get(), misses.get());
    }

    /** 重置计数器（测试/运维用途） */
    public void resetMetrics() {
        pathHits.set(0);
        annotationHits.set(0);
        misses.set(0);
    }

    public static final class MetricsSnapshot {
        private final long pathHits;
        private final long annotationHits;
        private final long misses;

        public MetricsSnapshot(long pathHits, long annotationHits, long misses) {
            this.pathHits = pathHits;
            this.annotationHits = annotationHits;
            this.misses = misses;
        }

        public long getPathHits() { return pathHits; }
        public long getAnnotationHits() { return annotationHits; }
        public long getMisses() { return misses; }
        public long getTotalHits() { return pathHits + annotationHits; }
    }
}
