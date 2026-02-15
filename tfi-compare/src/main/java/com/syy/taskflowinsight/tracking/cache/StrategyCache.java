package com.syy.taskflowinsight.tracking.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.syy.taskflowinsight.tracking.compare.CompareStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 策略解析结果缓存（基于Caffeine）
 * <p>
 * 缓存 StrategyResolver 的类型 -> 策略映射，减少重复解析开销。
 * 通过 tfi.diff.cache.strategy.* 配置控制。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
public final class StrategyCache {

    private static final Logger logger = LoggerFactory.getLogger(StrategyCache.class);

    private final Cache<Class<?>, CompareStrategy<?>> cache;
    private final boolean enabled;

    /**
     * 创建策略缓存
     *
     * @param enabled 是否启用缓存
     * @param maxSize 最大缓存条目数
     * @param ttlMs 过期时间（毫秒）
     */
    public StrategyCache(boolean enabled, long maxSize, long ttlMs) {
        this.enabled = enabled;
        if (enabled) {
            this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
                .recordStats()
                .build();
            logger.debug("StrategyCache initialized: maxSize={}, ttlMs={}", maxSize, ttlMs);
        } else {
            this.cache = null;
            logger.debug("StrategyCache disabled (pass-through mode)");
        }
    }

    /**
     * 获取或解析策略
     *
     * @param type 类型
     * @param resolver 解析函数（缓存未命中时调用）
     * @return 策略实例，可能为null
     */
    public CompareStrategy<?> getOrResolve(
        Class<?> type,
        Function<Class<?>, CompareStrategy<?>> resolver
    ) {
        if (!enabled || cache == null) {
            // 缓存禁用，直通
            return resolver.apply(type);
        }

        CompareStrategy<?> strategy = cache.getIfPresent(type);
        if (strategy != null) {
            logger.trace("StrategyCache hit: {}", type.getSimpleName());
            return strategy;
        }

        // 缓存未命中，调用解析器
        logger.trace("StrategyCache miss: {}", type.getSimpleName());
        strategy = resolver.apply(type);

        if (strategy != null) {
            cache.put(type, strategy);
        }

        return strategy;
    }

    /**
     * 获取缓存统计信息
     *
     * @return 命中率，缓存禁用时返回 -1.0
     */
    public double getHitRate() {
        if (!enabled || cache == null) {
            return -1.0;
        }
        return cache.stats().hitRate();
    }

    /**
     * 获取缓存统计信息（请求总数）
     */
    public long getRequestCount() {
        if (!enabled || cache == null) {
            return 0;
        }
        return cache.stats().requestCount();
    }

    /**
     * 清空缓存
     */
    public void invalidateAll() {
        if (enabled && cache != null) {
            cache.invalidateAll();
            logger.debug("StrategyCache invalidated");
        }
    }
}
