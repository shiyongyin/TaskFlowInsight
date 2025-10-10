package com.syy.taskflowinsight.tracking.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 反射元数据缓存（基于Caffeine）
 * <p>
 * 缓存类的字段列表、注解元数据等反射信息，减少重复反射开销。
 * 通过 tfi.diff.cache.reflection.* 配置控制。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
public final class ReflectionMetaCache {

    private static final Logger logger = LoggerFactory.getLogger(ReflectionMetaCache.class);

    private final Cache<Class<?>, List<Field>> fieldCache;
    private final boolean enabled;

    /**
     * 创建反射元数据缓存
     *
     * @param enabled 是否启用缓存
     * @param maxSize 最大缓存条目数
     * @param ttlMs 过期时间（毫秒）
     */
    public ReflectionMetaCache(boolean enabled, long maxSize, long ttlMs) {
        this.enabled = enabled;
        if (enabled) {
            this.fieldCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
                .recordStats()
                .build();
            logger.debug("ReflectionMetaCache initialized: maxSize={}, ttlMs={}", maxSize, ttlMs);
        } else {
            this.fieldCache = null;
            logger.debug("ReflectionMetaCache disabled (pass-through mode)");
        }
    }

    /**
     * 获取类的所有字段（包括继承的字段）
     *
     * @param clazz 类
     * @param resolver 解析函数（缓存未命中时调用）
     * @return 字段列表
     */
    public List<Field> getFieldsOrResolve(
        Class<?> clazz,
        Function<Class<?>, List<Field>> resolver
    ) {
        if (!enabled || fieldCache == null) {
            // 缓存禁用，直通
            return resolver.apply(clazz);
        }

        List<Field> fields = fieldCache.getIfPresent(clazz);
        if (fields != null) {
            logger.trace("ReflectionMetaCache hit: {}", clazz.getSimpleName());
            return fields;
        }

        // 缓存未命中，调用解析器
        logger.trace("ReflectionMetaCache miss: {}", clazz.getSimpleName());
        fields = resolver.apply(clazz);

        if (fields != null) {
            fieldCache.put(clazz, fields);
        }

        return fields;
    }

    /**
     * 默认的字段解析器（包括继承的字段）
     *
     * @param clazz 类
     * @return 字段列表
     */
    public static List<Field> defaultFieldResolver(Class<?> clazz) {
        return Arrays.asList(clazz.getDeclaredFields());
    }

    /**
     * 获取缓存统计信息
     *
     * @return 命中率，缓存禁用时返回 -1.0
     */
    public double getHitRate() {
        if (!enabled || fieldCache == null) {
            return -1.0;
        }
        return fieldCache.stats().hitRate();
    }

    /**
     * 获取缓存统计信息（请求总数）
     */
    public long getRequestCount() {
        if (!enabled || fieldCache == null) {
            return 0;
        }
        return fieldCache.stats().requestCount();
    }

    /**
     * 获取当前缓存大小
     *
     * @return 缓存中的条目数量，缓存禁用时返回0
     */
    public long getCacheSize() {
        if (!enabled || fieldCache == null) {
            return 0;
        }
        return fieldCache.estimatedSize();
    }

    /**
     * 清空缓存
     */
    public void invalidateAll() {
        if (enabled && fieldCache != null) {
            fieldCache.invalidateAll();
            logger.debug("ReflectionMetaCache invalidated");
        }
    }
}
