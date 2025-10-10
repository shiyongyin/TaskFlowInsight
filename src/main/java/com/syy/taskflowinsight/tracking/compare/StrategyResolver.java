package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.cache.StrategyCache;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略解析器
 * <p>
 * 根据目标类型和优先级规则解析最合适的比较策略。
 * 优先级规则：精确匹配(100) > 泛化/接口(50) > 通用Object(0)
 * </p>
 * <p>
 * 解析结果会被缓存以提升性能。M3增强：支持可选的 Caffeine 缓存。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-04
 */
public class StrategyResolver {

    private final Map<Class<?>, CompareStrategy<?>> fallbackCache = new ConcurrentHashMap<>();
    private final StrategyCache strategyCache; // M3新增：可选的Caffeine缓存

    /**
     * 默认构造器（向后兼容）
     */
    public StrategyResolver() {
        this(null);
    }

    /**
     * 带缓存的构造器（M3新增）
     *
     * @param strategyCache Caffeine缓存，可为null
     */
    public StrategyResolver(StrategyCache strategyCache) {
        this.strategyCache = strategyCache;
    }

    /**
     * 解析目标类型的比较策略
     *
     * @param strategies 注册的策略列表
     * @param targetType 目标类型
     * @return 最合适的策略，如果没有找到返回 null
     */
    public CompareStrategy<?> resolve(List<CompareStrategy<?>> strategies, Class<?> targetType) {
        if (strategies == null || strategies.isEmpty() || targetType == null) {
            return null;
        }

        // M3增强：优先使用 StrategyCache
        if (strategyCache != null) {
            return strategyCache.getOrResolve(targetType, type -> resolveInternal(strategies, type));
        }

        // 向后兼容：使用 fallbackCache
        return fallbackCache.computeIfAbsent(targetType, type -> resolveInternal(strategies, type));
    }

    /**
     * 内部解析逻辑（抽离以供缓存调用）
     */
    private CompareStrategy<?> resolveInternal(List<CompareStrategy<?>> strategies, Class<?> targetType) {
        return strategies.stream()
            .filter(s -> s.supports(targetType))
            .max(Comparator.comparingInt(s -> calculatePriority(s, targetType)))
            .orElse(null);
    }

    /**
     * 计算策略对目标类型的优先级
     *
     * @param strategy 策略
     * @param targetType 目标类型
     * @return 优先级分数：精确(100) > 泛化/接口(50) > 通用(0)
     */
    private int calculatePriority(CompareStrategy<?> strategy, Class<?> targetType) {
        if (strategy == null || targetType == null) {
            return 0;
        }

        // 尝试获取策略支持的精确类型
        Class<?> strategyType = extractSupportedType(strategy, targetType);

        if (strategyType == null) {
            return 0; // 通用策略
        }

        // 精确匹配
        if (strategyType.equals(targetType)) {
            return 100;
        }

        // 泛化/接口匹配：对更专用的容器策略给予更高优先级
        if (strategyType.isAssignableFrom(targetType)) {
            String name = strategy.getName();
            try {
                if (name != null) {
                    if (name.contains("Set") && java.util.Set.class.isAssignableFrom(targetType)) {
                        return 60; // SetCompare 优先于 CollectionCompare
                    }
                    if (name.contains("List") && java.util.List.class.isAssignableFrom(targetType)) {
                        return 60; // List 专用策略（兜底）
                    }
                    if (name.contains("Map") && java.util.Map.class.isAssignableFrom(targetType)) {
                        return 60; // Map 专用策略（兜底）
                    }
                }
            } catch (Throwable ignore) {}
            return 50;
        }

        return 0; // 通用
    }

    /**
     * 提取策略支持的类型
     * <p>
     * 由于 Java 类型擦除，无法直接获取泛型参数，
     * 这里通过启发式方法：尝试常见类型判断
     * </p>
     */
    private Class<?> extractSupportedType(CompareStrategy<?> strategy, Class<?> targetType) {
        // 启发式方法：根据策略名称推断
        String strategyName = strategy.getName();

        // 常见类型映射
        if (strategyName.contains("Date") && strategy.supports(java.util.Date.class)) {
            return java.util.Date.class;
        }
        if (strategyName.contains("Collection") && strategy.supports(java.util.Collection.class)) {
            return java.util.Collection.class;
        }
        if (strategyName.contains("Map") && strategy.supports(java.util.Map.class)) {
            return java.util.Map.class;
        }
        if (strategyName.contains("List") && strategy.supports(java.util.List.class)) {
            return java.util.List.class;
        }
        if (strategyName.contains("Set") && strategy.supports(java.util.Set.class)) {
            return java.util.Set.class;
        }

        // 对于未知策略，直接使用 targetType 测试 supports
        return strategy.supports(targetType) ? targetType : null;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        if (strategyCache != null) {
            strategyCache.invalidateAll();
        }
        fallbackCache.clear();
    }

    /**
     * 获取缓存大小（用于诊断）
     */
    public int getCacheSize() {
        return fallbackCache.size();
    }

    /**
     * 获取缓存命中率（M3新增）
     *
     * @return 命中率，无Caffeine缓存时返回 -1.0
     */
    public double getCacheHitRate() {
        return strategyCache != null ? strategyCache.getHitRate() : -1.0;
    }
}
