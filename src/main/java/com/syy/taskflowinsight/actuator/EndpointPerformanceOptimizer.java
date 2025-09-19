package com.syy.taskflowinsight.actuator;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 端点性能优化器
 * 
 * 提供缓存、限流、性能监控等优化功能
 * 
 * @since 3.0.0
 */
@Component
public class EndpointPerformanceOptimizer {
    
    private final Map<String, CachedData> cache = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    
    private static final long CACHE_TTL_MS = 5000; // 5秒缓存
    private static final int MAX_CACHE_SIZE = 50;
    
    /**
     * 获取缓存的概览数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedOverview(Supplier<T> supplier) {
        return (T) getCachedData("overview", (Supplier<Object>) supplier);
    }
    
    /**
     * 获取缓存数据
     */
    public Object getCachedData(String key, Supplier<Object> supplier) {
        requestCount.incrementAndGet();
        
        CachedData cached = cache.get(key);
        long now = System.currentTimeMillis();
        
        if (cached != null && (now - cached.timestamp) <= CACHE_TTL_MS) {
            cacheHitCount.incrementAndGet();
            return cached.data;
        }
        
        // 缓存未命中，重新生成数据
        Object data = supplier.get();
        cache.put(key, new CachedData(data, now));
        
        // 清理过期缓存
        cleanupExpiredCache(now);
        
        return data;
    }
    
    /**
     * 获取性能统计
     */
    public PerformanceStats getStats() {
        long total = requestCount.get();
        long hits = cacheHitCount.get();
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        
        return new PerformanceStats(total, hits, hitRate, cache.size());
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        cache.clear();
    }
    
    /**
     * 清理过期缓存
     */
    private void cleanupExpiredCache(long now) {
        if (cache.size() > MAX_CACHE_SIZE) {
            cache.entrySet().removeIf(entry -> 
                (now - entry.getValue().timestamp) > CACHE_TTL_MS);
        }
    }
    
    /**
     * 缓存数据结构
     */
    private static class CachedData {
        final Object data;
        final long timestamp;
        
        CachedData(Object data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * 性能统计
     */
    public static class PerformanceStats {
        private final long totalRequests;
        private final long cacheHits;
        private final double hitRate;
        private final int cacheSize;
        
        public PerformanceStats(long totalRequests, long cacheHits, double hitRate, int cacheSize) {
            this.totalRequests = totalRequests;
            this.cacheHits = cacheHits;
            this.hitRate = hitRate;
            this.cacheSize = cacheSize;
        }
        
        public long getTotalRequests() { return totalRequests; }
        public long getCacheHits() { return cacheHits; }
        public double getHitRate() { return hitRate; }
        public int getCacheSize() { return cacheSize; }
    }
}