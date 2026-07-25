package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Queue<K> insertionOrder = new ConcurrentLinkedQueue<>();
    private final Map<K, Long> insertionTimes = new ConcurrentHashMap<>();
    /** 缓存与两份 FIFO 元数据的复合变更必须在同一实例内线性化。 */
    private final Object fifoLock = new Object();
    /** listener 只置脏；多次移除合并为调用线程上的一次有界校准。 */
    private final AtomicBoolean metadataDirty = new AtomicBoolean();
    private final AtomicLong insertionCounter = new AtomicLong(0);
    /** 当前实例已执行的 FIFO 驱逐总数。 */
    private final AtomicLong totalEvictions = new AtomicLong(0);
    /** 手动 FIFO 队列允许保留的最大键数量。 */
    private final int maxSize;
    
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
        // direct executor 保证移除返回前已置脏，listener 自身不触碰业务 key。
        builder.executor(Runnable::run);
        
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
            if (cause.wasEvicted()) {
                metadataDirty.set(true);
            }
            if (key != null) {
                log.debug("Cache entry removed: cause={}", cause);
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
        synchronized (fifoLock) {
            underlyingCache.cleanUp();
            drainRemovalMetadata();
            final V currentValue = underlyingCache.policy().getIfPresentQuietly(key);
            final boolean isNewKey = currentValue == null || !insertionTimes.containsKey(key);

            if (isNewKey) {
                // 清掉过期残留后再按当前容量登记，重插入必须获得新的 FIFO 位置。
                insertionOrder.remove(key);
                insertionTimes.remove(key);
                enforceFifoEviction();
                insertionOrder.offer(key);
                insertionTimes.put(key, insertionCounter.incrementAndGet());
            }

            underlyingCache.put(key, value);
            if (log.isDebugEnabled()) {
                log.debug("Cache put: isNew={}, queueSize={}", isNewKey, insertionOrder.size());
            }
        }
    }
    
    @Override
    public Optional<V> get(K key) {
        if (key == null) {
            return Optional.empty();
        }
        drainRemovalMetadataIfNeeded();
        final V value = underlyingCache.getIfPresent(key);
        if (value == null) {
            synchronized (fifoLock) {
                drainRemovalMetadata();
                if (underlyingCache.policy().getIfPresentQuietly(key) == null) {
                    insertionOrder.remove(key);
                    insertionTimes.remove(key);
                }
            }
        }
        return Optional.ofNullable(value);
    }
    
    @Override
    public void remove(K key) {
        if (key != null) {
            synchronized (fifoLock) {
                drainRemovalMetadata();
                underlyingCache.invalidate(key);
                insertionOrder.remove(key);
                insertionTimes.remove(key);
            }
        }
    }
    
    @Override
    public void clear() {
        synchronized (fifoLock) {
            underlyingCache.invalidateAll();
            insertionOrder.clear();
            insertionTimes.clear();
            insertionCounter.set(0);
            log.info("FIFO cache cleared");
        }
    }
    
    @Override
    public long size() {
        synchronized (fifoLock) {
            drainRemovalMetadata();
            underlyingCache.cleanUp();
            drainRemovalMetadata();
            return underlyingCache.estimatedSize();
        }
    }
    
    @Override
    public StoreStats getStats() {
        synchronized (fifoLock) {
            drainRemovalMetadata();
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
    }
    
    /**
     * 执行FIFO驱逐策略
     */
    private void enforceFifoEviction() {
        while (insertionOrder.size() >= maxSize) {
            K oldestKey = insertionOrder.poll();
            if (oldestKey != null) {
                insertionTimes.remove(oldestKey);
                underlyingCache.invalidate(oldestKey);
                totalEvictions.incrementAndGet();
                
                if (log.isDebugEnabled()) {
                    log.debug("FIFO evicted oldest entry");
                }
            } else {
                break; // 队列为空
            }
        }
        
    }

    private void drainRemovalMetadata() {
        if (!metadataDirty.getAndSet(false)) {
            return;
        }
        // insertionOrder 受 maxSize 约束；在调用线程校准可避免 listener 触碰业务 equality。
        final Iterator<K> trackedKeys = insertionOrder.iterator();
        while (trackedKeys.hasNext()) {
            final K trackedKey = trackedKeys.next();
            if (underlyingCache.policy().getIfPresentQuietly(trackedKey) == null) {
                trackedKeys.remove();
                insertionTimes.remove(trackedKey);
            }
        }
    }

    private void drainRemovalMetadataIfNeeded() {
        if (metadataDirty.get()) {
            synchronized (fifoLock) {
                drainRemovalMetadata();
            }
        }
    }
    
    /**
     * 获取FIFO特有统计信息
     */
    public FifoStats getFifoStats() {
        synchronized (fifoLock) {
            drainRemovalMetadata();
            return new FifoStats(
                insertionOrder.size(),
                maxSize,
                insertionCounter.get(),
                getInsertionOrderIntegrity()
            );
        }
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
