package com.syy.taskflowinsight.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 分层缓存存储实现
 * @param <K> 键类型
 * @param <V> 值类型
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tfi.store.caffeine.tiered.enabled", havingValue = "true", matchIfMissing = false)
public class TieredCaffeineStore<K, V> implements Store<K, V> {
    
    private final CaffeineStore<K, V> l1Cache;
    private final CaffeineStore<K, V> l2Cache;
    
    /**
     * 默认构造函数
     */
    public TieredCaffeineStore() {
        this(StoreConfig.l1Config(), StoreConfig.l2Config());
    }
    
    /**
     * 配置构造函数
     * @param l1Config L1缓存配置
     * @param l2Config L2缓存配置
     */
    public TieredCaffeineStore(@Qualifier("l1") StoreConfig l1Config,
                               @Qualifier("l2") StoreConfig l2Config) {
        this.l1Cache = new CaffeineStore<>(l1Config);
        this.l2Cache = new CaffeineStore<>(l2Config);
        log.info("TieredCaffeineStore initialized with L1 size={}, L2 size={}", 
            l1Config.getMaxSize(), l2Config.getMaxSize());
    }
    
    @Override
    public void put(K key, V value) {
        // 同时写入L1和L2
        l1Cache.put(key, value);
        l2Cache.put(key, value);
    }
    
    @Override
    public Optional<V> get(K key) {
        // 先从L1查找
        Optional<V> value = l1Cache.get(key);
        if (value.isPresent()) {
            log.trace("L1 cache hit for key: {}", key);
            return value;
        }
        
        // L1未命中，从L2查找
        value = l2Cache.get(key);
        if (value.isPresent()) {
            log.trace("L2 cache hit for key: {}", key);
            // 提升到L1
            l1Cache.put(key, value.get());
        } else {
            log.trace("Cache miss for key: {}", key);
        }
        
        return value;
    }
    
    @Override
    public void remove(K key) {
        l1Cache.remove(key);
        l2Cache.remove(key);
    }
    
    @Override
    public void clear() {
        l1Cache.clear();
        l2Cache.clear();
        log.info("Tiered cache cleared");
    }
    
    @Override
    public long size() {
        // 返回去重后的总大小（近似值）
        return l1Cache.size() + l2Cache.size();
    }
    
    @Override
    public StoreStats getStats() {
        StoreStats l1Stats = l1Cache.getStats();
        StoreStats l2Stats = l2Cache.getStats();
        
        // 合并统计信息
        return StoreStats.builder()
            .hitCount(l1Stats.getHitCount() + l2Stats.getHitCount())
            .missCount(l2Stats.getMissCount()) // 只有L2未命中才是真正的未命中
            .loadSuccessCount(l1Stats.getLoadSuccessCount() + l2Stats.getLoadSuccessCount())
            .loadFailureCount(l1Stats.getLoadFailureCount() + l2Stats.getLoadFailureCount())
            .evictionCount(l1Stats.getEvictionCount() + l2Stats.getEvictionCount())
            .totalLoadTime(l1Stats.getTotalLoadTime() + l2Stats.getTotalLoadTime())
            .estimatedSize(size())
            .hitRate(calculateOverallHitRate(l1Stats, l2Stats))
            .missRate(calculateOverallMissRate(l1Stats, l2Stats))
            .build();
    }
    
    /**
     * 计算总体命中率
     * @param l1Stats L1统计
     * @param l2Stats L2统计
     * @return 总体命中率
     */
    private double calculateOverallHitRate(StoreStats l1Stats, StoreStats l2Stats) {
        long totalRequests = l1Stats.getHitCount() + l1Stats.getMissCount();
        if (totalRequests == 0) {
            return 0.0;
        }
        long totalHits = l1Stats.getHitCount() + l2Stats.getHitCount();
        return (double) totalHits / totalRequests;
    }
    
    /**
     * 计算总体未命中率
     * @param l1Stats L1统计
     * @param l2Stats L2统计
     * @return 总体未命中率
     */
    private double calculateOverallMissRate(StoreStats l1Stats, StoreStats l2Stats) {
        return 1.0 - calculateOverallHitRate(l1Stats, l2Stats);
    }
    
    /**
     * 获取L1缓存统计
     * @return L1统计信息
     */
    public StoreStats getL1Stats() {
        return l1Cache.getStats();
    }
    
    /**
     * 获取L2缓存统计
     * @return L2统计信息
     */
    public StoreStats getL2Stats() {
        return l2Cache.getStats();
    }
    
    /**
     * 手动触发清理
     */
    public void cleanUp() {
        l1Cache.cleanUp();
        l2Cache.cleanUp();
    }
}