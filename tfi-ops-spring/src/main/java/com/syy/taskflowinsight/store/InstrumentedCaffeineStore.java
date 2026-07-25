package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 带加载器和刷新能力的缓存存储
 * @param <K> 键类型
 * @param <V> 值类型
 */
@Slf4j
public class InstrumentedCaffeineStore<K, V> implements Store<K, V> {
    
    private final LoadingCache<K, V> cache;
    private final StoreConfig config;
    /** 同时承接 Caffeine 脱敏和显式 refresh 原始结果桥接的唯一 loader。 */
    private final ConfidentialCacheLoader<K, V> loader;
    
    /**
     * 构造函数
     * @param loader 缓存加载器
     */
    public InstrumentedCaffeineStore(CacheLoader<K, V> loader) {
        this(StoreConfig.defaultConfig(), loader);
    }
    
    /**
     * 配置构造函数
     * @param config 存储配置
     * @param loader 缓存加载器
     */
    public InstrumentedCaffeineStore(StoreConfig config, CacheLoader<K, V> loader) {
        this.config = config;
        this.loader = new ConfidentialCacheLoader<>(Objects.requireNonNull(loader, "loader"));
        this.cache = buildLoadingCache(config, this.loader);
        log.info("InstrumentedCaffeineStore initialized with loader, maxSize={}", config.getMaxSize());
    }
    
    /**
     * 构建加载缓存
     * @param config 配置
     * @param loader 加载器
     * @return 加载缓存实例
     */
    private LoadingCache<K, V> buildLoadingCache(StoreConfig config, CacheLoader<K, V> loader) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(config.getMaxSize())
            .recordStats();
        
        // 配置TTL
        if (config.getDefaultTtl() != null) {
            builder.expireAfterWrite(config.getDefaultTtl());
        }
        
        // 配置空闲超时
        if (config.getIdleTimeout() != null) {
            builder.expireAfterAccess(config.getIdleTimeout());
        }
        
        // 配置刷新
        if (config.getRefreshAfterWrite() != null) {
            builder.refreshAfterWrite(config.getRefreshAfterWrite());
        }
        
        // 配置软引用
        if (config.isUseSoftValues()) {
            builder.softValues();
        }
        
        // 配置驱逐监听
        if (config.isLogEvictions()) {
            builder.removalListener((key, value, cause) ->
                log.debug("Cache eviction: cause={}", cause));
        }
        
        return builder.build(loader);
    }
    
    @Override
    public void put(K key, V value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }
        cache.put(key, value);
    }
    
    @Override
    public Optional<V> get(K key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            // 使用加载器获取值
            V value = cache.get(key);
            return Optional.ofNullable(value);
        } catch (Exception exception) {
            log.error("Failed to load value: exceptionType={}", exception.getClass().getName());
            return Optional.empty();
        }
    }
    
    /**
     * 异步获取值
     * @param key 键
     * @return 异步结果
     */
    public CompletableFuture<V> getAsync(K key) {
        return CompletableFuture.supplyAsync(() -> get(key).orElse(null));
    }
    
    /**
     * 异步获取值（指定执行器）
     * @param key 键
     * @param executor 执行器
     * @return 异步结果
     */
    public CompletableFuture<V> getAsync(K key, Executor executor) {
        return CompletableFuture.supplyAsync(() -> get(key).orElse(null), executor);
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
        log.info("Instrumented cache cleared");
    }
    
    @Override
    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
    
    @Override
    public StoreStats getStats() {
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
     * 刷新指定键的值
     * @param key 键
     */
    public void refresh(K key) {
        refreshAsync(key);
    }
    
    /**
     * 异步刷新指定键的值
     * @param key 键
     * @return 异步结果
     */
    public CompletableFuture<V> refreshAsync(K key) {
        return loader.refresh(cache, key);
    }
    
    /**
     * 手动触发清理
     */
    public void cleanUp() {
        cache.cleanUp();
    }
}
