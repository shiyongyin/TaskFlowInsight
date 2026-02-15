package com.syy.taskflowinsight.tracking.path;

import java.util.List;
import java.util.Map;

/**
 * 路径匹配缓存接口
 * 
 * <p>提供统一的路径匹配缓存抽象，支持多种实现：
 * <ul>
 *   <li>CaffeinePathMatcherCache - 基于Caffeine的高性能实现</li>
 *   <li>PathMatcherCache - 原有ConcurrentHashMap实现（兼容性）</li>
 * </ul>
 * 
 * @author TaskFlowInsight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
public interface PathMatcherCacheInterface {
    
    /**
     * 匹配路径
     * 
     * @param path 要匹配的路径
     * @param pattern 模式（支持通配符）
     * @return 是否匹配
     */
    boolean matches(String path, String pattern);
    
    /**
     * 批量匹配
     * 
     * @param paths 路径列表
     * @param pattern 模式
     * @return 匹配结果Map
     */
    Map<String, Boolean> matchBatch(List<String> paths, String pattern);
    
    /**
     * 查找匹配的模式
     * 
     * @param path 路径
     * @param patterns 模式列表
     * @return 匹配的模式列表
     */
    List<String> findMatchingPatterns(String path, List<String> patterns);
    
    /**
     * 预加载模式
     * 
     * @param patterns 要预加载的模式列表
     */
    void preload(List<String> patterns);
    
    /**
     * 清空缓存
     */
    void clear();
    
    /**
     * 获取统计信息
     * 
     * @return 缓存统计信息
     */
    CacheStats getStats();
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final long loadCount;
        private final long loadExceptionCount;
        private final double averageLoadTime;
        private final long evictionCount;
        private final long size;
        
        public CacheStats(long hitCount, long missCount, long loadCount,
                         long loadExceptionCount, double averageLoadTime,
                         long evictionCount, long size) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.loadCount = loadCount;
            this.loadExceptionCount = loadExceptionCount;
            this.averageLoadTime = averageLoadTime;
            this.evictionCount = evictionCount;
            this.size = size;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public long getRequestCount() { return hitCount + missCount; }
        public double getHitRate() { 
            long requests = getRequestCount();
            return requests == 0 ? 1.0 : (double) hitCount / requests;
        }
        public long getLoadCount() { return loadCount; }
        public long getLoadExceptionCount() { return loadExceptionCount; }
        public double getAverageLoadTime() { return averageLoadTime; }
        public long getEvictionCount() { return evictionCount; }
        public long getSize() { return size; }
        
        public static class Builder {
            private long hitCount;
            private long missCount;
            private long loadCount;
            private long loadExceptionCount;
            private double averageLoadTime;
            private long evictionCount;
            private long size;
            
            public Builder hitCount(long hitCount) { this.hitCount = hitCount; return this; }
            public Builder missCount(long missCount) { this.missCount = missCount; return this; }
            public Builder loadCount(long loadCount) { this.loadCount = loadCount; return this; }
            public Builder loadExceptionCount(long loadExceptionCount) { this.loadExceptionCount = loadExceptionCount; return this; }
            public Builder averageLoadTime(double averageLoadTime) { this.averageLoadTime = averageLoadTime; return this; }
            public Builder evictionCount(long evictionCount) { this.evictionCount = evictionCount; return this; }
            public Builder size(long size) { this.size = size; return this; }
            
            public CacheStats build() {
                return new CacheStats(hitCount, missCount, loadCount, loadExceptionCount,
                                    averageLoadTime, evictionCount, size);
            }
        }
    }
}