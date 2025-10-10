package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 支持FIFO策略的Caffeine缓存存储实现
 * 
 * @param <K> 键类型
 * @param <V> 值类型
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tfi.store.fifo.enabled", havingValue = "true", matchIfMissing = false)
public class FifoCaffeineStore<K, V> implements Store<K, V> {
    
    private final Cache<K, V> underlyingCache;
    private final StoreConfig config;
    
    // FIFO特有字段
    private final ConcurrentLinkedQueue<K> insertionOrder = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<K, Long> insertionTimes = new ConcurrentHashMap<>();
    private final AtomicLong insertionCounter = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);
    private final int maxSize;
    
    @Autowired(required = false)
    private TfiMetrics tfiMetrics;
    
    /**
     * 默认构造函数
     */
    public FifoCaffeineStore() {
        this(StoreConfig.fifoConfig());
    }
    
    /**
     * 配置构造函数
     * @param config 存储配置
     */
    public FifoCaffeineStore(StoreConfig config) {
        this.config = config;
        this.maxSize = (int) config.getMaxSize();
        this.underlyingCache = buildCaffeineCache(config);
        
        log.info("FifoCaffeineStore initialized with maxSize={}, strategy={}", 
            config.getMaxSize(), config.getEvictionStrategy());
    }
    
    /**
     * 构建底层Caffeine缓存（不带自动驱逐，由FIFO逻辑控制）
     */
    private Cache<K, V> buildCaffeineCache(StoreConfig config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        
        // FIFO模式下不使用LRU的maximumSize，而是手动控制
        // 设置一个较大的值防止Caffeine内部驱逐
        builder.maximumSize(config.getMaxSize() * 2);
        
        // 配置TTL
        if (config.getDefaultTtl() != null) {
            builder.expireAfterWrite(config.getDefaultTtl());
        }
        
        // 配置统计
        if (config.isRecordStats()) {
            builder.recordStats();
        }
        
        // 配置驱逐监听（记录被Caffeine驱逐的条目）
        builder.removalListener((key, value, cause) -> {
            if (key != null) {
                insertionOrder.remove(key);
                insertionTimes.remove(key);
                log.debug("Cache entry removed: key={}, cause={}", key, cause);
            }
        });
        
        return builder.build();
    }
    
    @Override
    public void put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        
        // 检查是否为新键
        boolean isNewKey = !insertionTimes.containsKey(key);
        
        if (isNewKey) {
            // FIFO驱逐检查
            enforceFifoEviction();
            
            // 记录插入顺序
            insertionOrder.offer(key);
            insertionTimes.put(key, insertionCounter.incrementAndGet());
        }
        
        // 放入底层缓存
        underlyingCache.put(key, value);
        
        if (log.isDebugEnabled()) {
            log.debug("Put key={}, isNew={}, queueSize={}", key, isNewKey, insertionOrder.size());
        }
    }
    
    @Override
    public Optional<V> get(K key) {
        if (key == null) {
            return Optional.empty();
        }
        
        V value = underlyingCache.getIfPresent(key);
        return Optional.ofNullable(value);
    }
    
    @Override
    public void remove(K key) {
        if (key != null) {
            underlyingCache.invalidate(key);
            insertionOrder.remove(key);
            insertionTimes.remove(key);
        }
    }
    
    @Override
    public void clear() {
        underlyingCache.invalidateAll();
        insertionOrder.clear();
        insertionTimes.clear();
        insertionCounter.set(0);
        log.info("FIFO cache cleared");
    }
    
    @Override
    public long size() {
        underlyingCache.cleanUp();
        return underlyingCache.estimatedSize();
    }
    
    @Override
    public StoreStats getStats() {
        if (!config.isRecordStats()) {
            return StoreStats.builder()
                .estimatedSize(size())
                .build();
        }
        
        CacheStats stats = underlyingCache.stats();
        return StoreStats.builder()
            .hitCount(stats.hitCount())
            .missCount(stats.missCount())
            .loadSuccessCount(stats.loadSuccessCount())
            .loadFailureCount(stats.loadFailureCount())
            .evictionCount(stats.evictionCount())
            .totalLoadTime(stats.totalLoadTime())
            .estimatedSize(underlyingCache.estimatedSize())
            .hitRate(stats.hitRate())
            .build();
    }
    
    /**
     * 执行FIFO驱逐策略
     */
    private void enforceFifoEviction() {
        long evicted = 0;
        while (insertionOrder.size() >= maxSize) {
            K oldestKey = insertionOrder.poll();
            if (oldestKey != null) {
                insertionTimes.remove(oldestKey);
                underlyingCache.invalidate(oldestKey);
                evicted++;
                totalEvictions.incrementAndGet();
                
                if (log.isDebugEnabled()) {
                    log.debug("FIFO evicted oldest key: {}", oldestKey);
                }
            } else {
                break; // 队列为空
            }
        }
        
        // 上报FIFO驱逐指标
        if (evicted > 0 && tfiMetrics != null) {
            tfiMetrics.recordFifoEviction("fifo-store", evicted);
            
            // 同时调用标准缓存指标接口
            CacheStats stats = underlyingCache.stats();
            tfiMetrics.recordCacheMetrics("fifo-store",
                stats.hitRate(),
                totalEvictions.get(),  // 总驱逐数
                underlyingCache.estimatedSize(),
                null  // 无加载时间
            );
        }
    }
    
    /**
     * 获取FIFO特有统计信息
     */
    public FifoStats getFifoStats() {
        return new FifoStats(
            insertionOrder.size(),
            maxSize,
            insertionCounter.get(),
            getInsertionOrderIntegrity()
        );
    }
    
    /**
     * 检查插入顺序完整性
     */
    private boolean getInsertionOrderIntegrity() {
        return insertionOrder.size() == insertionTimes.size();
    }
    
    /**
     * FIFO统计信息
     */
    public static class FifoStats {
        private final int queueSize;
        private final int maxSize;
        private final long totalInsertions;
        private final boolean integrityCheck;
        
        public FifoStats(int queueSize, int maxSize, long totalInsertions, boolean integrityCheck) {
            this.queueSize = queueSize;
            this.maxSize = maxSize;
            this.totalInsertions = totalInsertions;
            this.integrityCheck = integrityCheck;
        }
        
        public int getQueueSize() { return queueSize; }
        public int getMaxSize() { return maxSize; }
        public long getTotalInsertions() { return totalInsertions; }
        public boolean isIntegrityCheck() { return integrityCheck; }
        
        public double getCapacityRatio() {
            return maxSize == 0 ? 0.0 : (double) queueSize / maxSize;
        }
        
        @Override
        public String toString() {
            return String.format("FifoStats{queue=%d/%d(%.1f%%), total=%d, integrity=%s}",
                queueSize, maxSize, getCapacityRatio() * 100, totalInsertions, integrityCheck);
        }
    }
}