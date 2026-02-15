package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 比较缓存配置（M3新增）
 * <p>
 * 根据 tfi.diff.cache.* 配置创建缓存Bean。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
@Configuration
public class CompareCacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CompareCacheConfig.class);

    /**
     * 策略缓存Bean（基于配置启用）
     */
    @Bean
    @ConditionalOnProperty(prefix = "tfi.diff.cache.strategy", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StrategyCache strategyCache(CompareCacheProperties cacheProps) {
        CompareCacheProperties.CacheConfig strategyCfg = cacheProps.getStrategy();
        logger.info("Creating StrategyCache: enabled={}, maxSize={}, ttlMs={}",
            strategyCfg.isEnabled(), strategyCfg.getMaxSize(), strategyCfg.getTtlMs());
        return new StrategyCache(strategyCfg.isEnabled(), strategyCfg.getMaxSize(), strategyCfg.getTtlMs());
    }

    /**
     * 反射元数据缓存Bean（基于配置启用）
     */
    @Bean
    @ConditionalOnProperty(prefix = "tfi.diff.cache.reflection", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ReflectionMetaCache reflectionMetaCache(CompareCacheProperties cacheProps) {
        CompareCacheProperties.CacheConfig reflectionCfg = cacheProps.getReflection();
        logger.info("Creating ReflectionMetaCache: enabled={}, maxSize={}, ttlMs={}",
            reflectionCfg.isEnabled(), reflectionCfg.getMaxSize(), reflectionCfg.getTtlMs());
        ReflectionMetaCache cache = new ReflectionMetaCache(reflectionCfg.isEnabled(), reflectionCfg.getMaxSize(), reflectionCfg.getTtlMs());
        // 将缓存注入到 SSOT/快照组件，减少反射开销
        try {
            EntityKeyUtils.setReflectionMetaCache(cache);
            com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep.setReflectionMetaCache(cache);
            com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.setReflectionMetaCache(cache);
        } catch (Throwable ignored) {
            // 注入失败不影响 Bean 创建
        }
        return cache;
    }
}
