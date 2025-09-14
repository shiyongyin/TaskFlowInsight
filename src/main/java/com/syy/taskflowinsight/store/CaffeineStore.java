package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine缓存存储实现
 * @param <K> 键类型
 * @param <V> 值类型
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tfi.store.enabled", havingValue = "true", matchIfMissing = false)
public class CaffeineStore<K, V> implements Store<K, V> {
    
    private final Cache<K, V> cache;
    private final StoreConfig config;
    
    /**
     * 默认构造函数
     */
    public CaffeineStore() {
        this(StoreConfig.defaultConfig());
    }
    
    /**
     * 配置构造函数
     * @param config 存储配置
     */
    public CaffeineStore(StoreConfig config) {
        this.config = config;
        this.cache = buildCache(config);
        log.info("CaffeineStore initialized with maxSize={}, ttl={}, idleTimeout={}", 
            config.getMaxSize(), config.getDefaultTtl(), config.getIdleTimeout());
    }
    
    /**
     * 构建缓存
     * @param config 配置
     * @return 缓存实例
     */
    private Cache<K, V> buildCache(StoreConfig config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(config.getMaxSize());
        
        // 配置TTL
        if (config.getDefaultTtl() != null) {
            builder.expireAfterWrite(config.getDefaultTtl());
        }
        
        // 配置空闲超时
        if (config.getIdleTimeout() != null) {
            builder.expireAfterAccess(config.getIdleTimeout());
        }
        
        // 配置刷新
        if (config.getRefreshAfterWrite() != null && config.getLoader() != null) {
            builder.refreshAfterWrite(config.getRefreshAfterWrite());
        }
        
        // 配置软引用
        if (config.isUseSoftValues()) {
            builder.softValues();
        }
        
        // 配置统计
        if (config.isRecordStats()) {
            builder.recordStats();
        }
        
        // 配置驱逐监听
        if (config.isLogEvictions()) {
            builder.removalListener((key, value, cause) -> 
                log.debug("Cache eviction: key={}, cause={}", key, cause));
        }
        
        // 构建缓存
        if (config.getLoader() != null) {
            @SuppressWarnings("unchecked")
            LoadingCache<K, V> loadingCache = (LoadingCache<K, V>) builder.build(config.getLoader());
            return loadingCache;
        } else {
            return builder.build();
        }
    }
    
    @Override
    public void put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        cache.put(key, value);
    }
    
    @Override
    public Optional<V> get(K key) {
        if (key == null) {
            return Optional.empty();
        }
        V value = cache.getIfPresent(key);
        return Optional.ofNullable(value);
    }
    
    @Override
    public void remove(K key) {
        if (key != null) {
            cache.invalidate(key);
        }
    }
    
    @Override
    public void clear() {
        cache.invalidateAll();
        log.info("Cache cleared");
    }
    
    @Override
    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
    
    @Override
    public StoreStats getStats() {
        if (!config.isRecordStats()) {
            return StoreStats.builder()
                .estimatedSize(size())
                .build();
        }
        
        CacheStats stats = cache.stats();
        return StoreStats.builder()
            .hitCount(stats.hitCount())
            .missCount(stats.missCount())
            .loadSuccessCount(stats.loadSuccessCount())
            .loadFailureCount(stats.loadFailureCount())
            .evictionCount(stats.evictionCount())
            .totalLoadTime(stats.totalLoadTime())
            .estimatedSize(cache.estimatedSize())
            .hitRate(stats.hitRate())
            .missRate(stats.missRate())
            .build();
    }
    
    /**
     * 手动触发缓存清理
     */
    public void cleanUp() {
        cache.cleanUp();
    }
    
    /**
     * 批量存储
     * @param entries 键值对
     */
    public void putAll(java.util.Map<? extends K, ? extends V> entries) {
        cache.putAll(entries);
    }
    
    /**
     * 批量获取
     * @param keys 键集合
     * @return 键值对映射
     */
    public java.util.Map<K, V> getAll(Iterable<? extends K> keys) {
        return cache.getAllPresent(keys);
    }
    
    /**
     * 批量移除
     * @param keys 键集合
     */
    public void removeAll(Iterable<? extends K> keys) {
        cache.invalidateAll(keys);
    }
}