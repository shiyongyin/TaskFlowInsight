package com.syy.taskflowinsight.tracking.path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路径缓存管理器
 * 使用WeakHashMap实现自动内存管理，结合LRU策略提升性能
 * 支持统计监控，满足>90%命中率要求
 * 
 * 特性：
 * - WeakHashMap自动清理无引用对象
 * - 线程安全的统计计数
 * - 内存使用估算
 * - 缓存容量限制
 * 
 * @author TaskFlow Insight Team  
 * @since v3.0.0
 */
public class PathCache {
    
    private static final Logger logger = LoggerFactory.getLogger(PathCache.class);
    
    // 默认配置
    private static final int DEFAULT_MAX_SIZE = 10000;
    private static final boolean DEFAULT_ENABLED = true;
    private static final String POLICY_LRU = "LRU";
    private static final String POLICY_FIFO = "FIFO";
    private static final String POLICY_SIZE = "SIZE_BASED";
    
    // 缓存存储（WeakHashMap确保内存安全）
    private final WeakHashMap<Object, String> cache;
    private final int maxSize;
    private final boolean enabled;
    private final String evictionPolicy;
    
    // LRU 索引（强引用，容量受限；仅用于次序管理）
    private final java.util.LinkedHashMap<Object, Boolean> lruIndex = new java.util.LinkedHashMap<>(16, 0.75f, true);
    // FIFO 队列（强引用，容量受限；仅用于次序管理）
    private final java.util.ArrayDeque<Object> fifoQueue = new java.util.ArrayDeque<>();
    
    // 统计信息（线程安全）
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    
    /**
     * 默认构造器
     */
    public PathCache() {
        this(DEFAULT_ENABLED, DEFAULT_MAX_SIZE, POLICY_SIZE);
    }
    
    /**
     * 带配置的构造器
     * 
     * @param enabled 是否启用缓存
     * @param maxSize 最大缓存容量
     */
    public PathCache(boolean enabled, int maxSize) {
        this(enabled, maxSize, POLICY_SIZE);
    }

    /**
     * 带驱逐策略的构造器
     *
     * @param enabled 是否启用
     * @param maxSize 最大容量
     * @param evictionPolicy 驱逐策略：LRU/FIFO/SIZE_BASED
     */
    public PathCache(boolean enabled, int maxSize, String evictionPolicy) {
        this.enabled = enabled;
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.evictionPolicy = (evictionPolicy != null ? evictionPolicy : POLICY_SIZE).toUpperCase();
        this.cache = new WeakHashMap<>(Math.min(this.maxSize, 1000));
        
        logger.info("PathCache initialized: enabled={}, maxSize={}, policy={}", enabled, this.maxSize, this.evictionPolicy);
    }
    
    /**
     * 从缓存获取路径
     * 
     * @param object 目标对象
     * @return 缓存的路径，未找到返回null
     */
    public synchronized String get(Object object) {
        if (!enabled || object == null) {
            return null;
        }

        String cachedPath = cache.get(object);
        if (cachedPath != null) {
            hits.incrementAndGet();
            logger.trace("Cache hit for object: {}", System.identityHashCode(object));
            if (POLICY_LRU.equals(evictionPolicy)) {
                lruIndex.put(object, Boolean.TRUE); // 触发访问顺序更新
            }
            return cachedPath;
        } else {
            misses.incrementAndGet();
            return null;
        }
    }
    
    /**
     * 将路径存入缓存（优化版，预热友好）
     *
     * @param object 目标对象
     * @param path 路径字符串
     */
    public synchronized void put(Object object, String path) {
        if (!enabled || object == null || path == null) {
            return;
        }

        // 检查是否已存在相同路径，避免重复存储
        String existing = cache.get(object);
        if (path.equals(existing)) {
            return;
        }

        // 检查容量限制，按策略驱逐
        if (cache.size() >= maxSize) {
            evictAccordingToPolicy(Math.max(1, maxSize / 10));
        }

        cache.put(object, path);
        puts.incrementAndGet();

        if (POLICY_LRU.equals(evictionPolicy)) {
            lruIndex.put(object, Boolean.TRUE);
        } else if (POLICY_FIFO.equals(evictionPolicy)) {
            fifoQueue.addLast(object);
        }

        logger.trace("Cached path '{}' for object: {}", path, System.identityHashCode(object));
    }

    /**
     * 批量预热缓存
     */
    public synchronized void warmUp(Map<Object, String> entries) {
        if (!enabled || entries == null || entries.isEmpty()) {
            return;
        }

        for (Map.Entry<Object, String> entry : entries.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                cache.put(entry.getKey(), entry.getValue());
                puts.incrementAndGet();
            }
        }

        logger.debug("Warmed up cache with {} entries, total size: {}", entries.size(), cache.size());
    }
    
    /**
     * 清空缓存
     */
    public synchronized void clear() {
        int oldSize = cache.size();
        cache.clear();
        logger.info("PathCache cleared, removed {} entries", oldSize);
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 统计数据对象
     */
    public CacheStatistics getStatistics() {
        long totalRequests = hits.get() + misses.get();
        double hitRate = totalRequests == 0 ? 0.0 : (double) hits.get() / totalRequests * 100.0;
        
        return new CacheStatistics(
            cache.size(),           // 当前大小
            maxSize,               // 最大容量
            hits.get(),            // 命中次数
            misses.get(),          // 未命中次数
            puts.get(),            // 存入次数
            evictions.get(),       // 清理次数
            hitRate,               // 命中率
            estimateMemoryUsage()   // 内存使用估算
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
        puts.set(0);
        logger.info("PathCache statistics reset");
    }
    
    /**
     * 估算内存使用量
     * 
     * @return 估算的字节数
     */
    private long estimateMemoryUsage() {
        // 粗略估算：每个条目约100字节（对象引用+字符串）
        return cache.size() * 100L;
    }
    
    /**
     * 获取缓存是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 获取最大容量
     */
    public int getMaxSize() {
        return maxSize;
    }
    
    /**
     * 当前驱逐策略
     */
    public String getEvictionPolicy() {
        return evictionPolicy;
    }

    /**
     * 移除指定对象的缓存条目
     */
    public synchronized void remove(Object object) {
        if (!enabled || object == null) return;
        cache.remove(object);
        lruIndex.remove(object);
        fifoQueue.remove(object);
    }
    
    /**
     * 缓存统计数据
     */
    public static class CacheStatistics {
        private final int currentSize;
        private final int maxSize;
        private final long hits;
        private final long misses;
        private final long puts;
        private final long evictions;
        private final double hitRate;
        private final long memoryUsage;
        
        public CacheStatistics(int currentSize, int maxSize, long hits, long misses, 
                             long puts, long evictions, double hitRate, long memoryUsage) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.puts = puts;
            this.evictions = evictions;
            this.hitRate = hitRate;
            this.memoryUsage = memoryUsage;
        }
        
        // Getters
        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getPuts() { return puts; }
        public long getEvictions() { return evictions; }
        public double getHitRate() { return hitRate; }
        public long getMemoryUsage() { return memoryUsage; }
        public long getTotalRequests() { return hits + misses; }
        
        @Override
        public String toString() {
            return String.format("CacheStats{size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%, memory=%dB}",
                currentSize, maxSize, hits, misses, hitRate, memoryUsage);
        }
    }

    // ============== 内部辅助 ==============

    private void evictAccordingToPolicy(int toRemove) {
        int removed = 0;
        if (POLICY_LRU.equals(evictionPolicy)) {
            // 使用LinkedHashMap的访问顺序：迭代器从最久未使用到最新
            java.util.Iterator<Object> it = lruIndex.keySet().iterator();
            while (it.hasNext() && removed < toRemove) {
                Object o = it.next();
                it.remove();
                if (cache.remove(o) != null) {
                    removed++;
                }
            }
        } else if (POLICY_FIFO.equals(evictionPolicy)) {
            while (removed < toRemove && !fifoQueue.isEmpty()) {
                Object o = fifoQueue.pollFirst();
                if (o != null && cache.remove(o) != null) {
                    removed++;
                }
                // 同时从LRU索引移除（如果存在）
                if (o != null) {
                    lruIndex.remove(o);
                }
            }
        }

        // 回退：如果仍未移除够或策略队列为空，则按SIZE_BASED移除
        if (removed < toRemove) {
            int stillNeed = toRemove - removed;
            Iterator<Map.Entry<Object, String>> iterator = cache.entrySet().iterator();
            int s = 0;
            while (iterator.hasNext() && s < stillNeed) {
                iterator.next();
                iterator.remove();
                s++;
            }
            evictions.addAndGet(stillNeed);
        } else {
            evictions.addAndGet(removed);
        }
    }

    // 无需队列驱逐辅助方法（使用lruIndex/fifoQueue代替）
}
