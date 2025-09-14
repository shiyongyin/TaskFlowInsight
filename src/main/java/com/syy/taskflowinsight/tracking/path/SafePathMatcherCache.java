package com.syy.taskflowinsight.tracking.path;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 安全的路径匹配器缓存
 * 使用Caffeine替代ConcurrentHashMap，解决iterator.remove()的UnsupportedOperationException
 * 
 * 核心功能：
 * - 通配符路径匹配（*, **, ?）
 * - Caffeine编译模式缓存（自动驱逐）
 * - Caffeine匹配结果缓存（自动驱逐）
 * - 线程安全
 * - 性能统计
 * - 保守默认配置（tfi.enabled=false）
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-09-14
 */
@Component
public class SafePathMatcherCache {
    
    private static final Logger logger = LoggerFactory.getLogger(SafePathMatcherCache.class);
    
    // ========== 配置属性 ==========
    
    @Value("${tfi.change-tracking.path-matcher.cache-size:1000}")
    private int cacheSize = 1000;
    
    @Value("${tfi.change-tracking.path-matcher.pattern-max-length:256}")
    private int patternMaxLength = 256;
    
    @Value("${tfi.change-tracking.path-matcher.max-wildcards:10}")
    private int maxWildcards = 10;
    
    @Value("${tfi.change-tracking.path-matcher.result-cache-multiplier:10}")
    private int resultCacheMultiplier = 10;
    
    @Value("${tfi.change-tracking.path-matcher.pattern-ttl-seconds:3600}")
    private int patternTtlSeconds = 3600; // 1小时
    
    @Value("${tfi.change-tracking.path-matcher.result-ttl-seconds:1800}")
    private int resultTtlSeconds = 1800; // 30分钟
    
    // Caffeine缓存：pattern -> compiled regex
    private final Cache<String, Pattern> patternCache;
    
    // Caffeine缓存：path + pattern -> match result
    private final Cache<String, Boolean> resultCache;
    
    // 统计
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong totalMatchTime = new AtomicLong(); // 纳秒
    
    // 单例模式（非Spring环境）
    private static volatile SafePathMatcherCache instance;
    
    public SafePathMatcherCache() {
        // 使用Caffeine构建缓存，自动支持LRU驱逐和TTL
        this.patternCache = Caffeine.newBuilder()
            .maximumSize(cacheSize)
            .expireAfterWrite(Duration.ofSeconds(patternTtlSeconds))
            .recordStats()
            .build();
        
        int maxResultCacheSize = Math.min(cacheSize * resultCacheMultiplier, 10000);
        this.resultCache = Caffeine.newBuilder()
            .maximumSize(maxResultCacheSize)
            .expireAfterWrite(Duration.ofSeconds(resultTtlSeconds))
            .recordStats()
            .build();
    }
    
    /**
     * 获取单例实例（非Spring环境）
     */
    public static SafePathMatcherCache getInstance() {
        if (instance == null) {
            synchronized (SafePathMatcherCache.class) {
                if (instance == null) {
                    instance = new SafePathMatcherCache();
                }
            }
        }
        return instance;
    }
    
    /**
     * 匹配路径
     * 
     * @param path 要匹配的路径
     * @param pattern 模式（支持通配符）
     * @return 是否匹配
     */
    public boolean matches(String path, String pattern) {
        if (path == null || pattern == null) {
            return false;
        }
        
        long startTime = System.nanoTime();
        
        try {
            // 验证输入
            validateInput(path, pattern);
            
            // 快速路径：精确匹配
            if (!containsWildcard(pattern)) {
                return path.equals(pattern);
            }
            
            // 查询结果缓存
            String cacheKey = buildCacheKey(path, pattern);
            Boolean cached = resultCache.getIfPresent(cacheKey);
            if (cached != null) {
                hitCount.incrementAndGet();
                return cached;
            }
            
            missCount.incrementAndGet();
            
            // 编译模式并匹配
            Pattern regex = compilePattern(pattern);
            boolean result = regex.matcher(path).matches();
            
            // 缓存结果（Caffeine自动处理驱逐）
            resultCache.put(cacheKey, result);
            
            return result;
            
        } finally {
            totalMatchTime.addAndGet(System.nanoTime() - startTime);
        }
    }
    
    /**
     * 批量匹配
     * 
     * @param paths 路径列表
     * @param pattern 模式
     * @return 匹配结果Map
     */
    public Map<String, Boolean> matchBatch(List<String> paths, String pattern) {
        if (paths == null || paths.isEmpty() || pattern == null) {
            return Collections.emptyMap();
        }
        
        Pattern regex = compilePattern(pattern);
        Map<String, Boolean> results = new HashMap<>();
        
        for (String path : paths) {
            if (path == null) continue;
            
            String cacheKey = buildCacheKey(path, pattern);
            Boolean cached = resultCache.getIfPresent(cacheKey);
            
            if (cached != null) {
                results.put(path, cached);
                hitCount.incrementAndGet();
            } else {
                boolean result = regex.matcher(path).matches();
                results.put(path, result);
                resultCache.put(cacheKey, result);
                missCount.incrementAndGet();
            }
        }
        
        return results;
    }
    
    /**
     * 查找匹配的模式
     * 
     * @param path 路径
     * @param patterns 模式列表
     * @return 匹配的模式列表
     */
    public List<String> findMatchingPatterns(String path, List<String> patterns) {
        if (path == null || patterns == null) {
            return Collections.emptyList();
        }
        
        List<String> matching = new ArrayList<>();
        
        for (String pattern : patterns) {
            if (pattern != null && matches(path, pattern)) {
                matching.add(pattern);
            }
        }
        
        return matching;
    }
    
    /**
     * 编译模式（使用Caffeine缓存）
     */
    private Pattern compilePattern(String pattern) {
        return patternCache.get(pattern, this::doCompilePattern);
    }
    
    /**
     * 实际编译模式
     */
    private Pattern doCompilePattern(String pattern) {
        try {
            String regex = convertToRegex(pattern);
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            logger.warn("Failed to compile pattern: {}", pattern, e);
            // 返回一个永不匹配的模式
            return Pattern.compile("(?!)");
        }
    }
    
    /**
     * 将通配符模式转换为正则表达式
     * 
     * 支持的通配符：
     * - * : 匹配单层路径中的任意字符（不包括.）
     * - ** : 匹配多层路径
     * - ? : 匹配单个字符
     */
    private String convertToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    // ** 匹配多层
                    regex.append(".*");
                    i += 2;
                    // 跳过**后面的点号（如果有）
                    if (i < pattern.length() && pattern.charAt(i) == '.') {
                        regex.append("\\.");
                        i++;
                    }
                } else {
                    // * 匹配单层（不包括.）
                    regex.append("[^.]*");
                    i++;
                }
            } else if (c == '?') {
                // ? 匹配单个字符
                regex.append(".");
                i++;
            } else if (c == '.') {
                // . 需要转义
                regex.append("\\.");
                i++;
            } else if (isRegexSpecialChar(c)) {
                // 其他正则特殊字符需要转义
                regex.append("\\").append(c);
                i++;
            } else {
                // 普通字符
                regex.append(c);
                i++;
            }
        }
        
        regex.append("$");
        return regex.toString();
    }
    
    /**
     * 检查是否包含通配符
     */
    private boolean containsWildcard(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
    }
    
    /**
     * 检查是否为正则表达式特殊字符
     */
    private boolean isRegexSpecialChar(char c) {
        return "[]{}()\\^$|+".indexOf(c) >= 0;
    }
    
    /**
     * 验证输入
     */
    private void validateInput(String path, String pattern) {
        if (pattern.length() > patternMaxLength) {
            throw new IllegalArgumentException("Pattern too long: " + pattern.length());
        }
        
        // 统计通配符数量
        int wildcardCount = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' || c == '?') {
                wildcardCount++;
                if (wildcardCount > maxWildcards) {
                    throw new IllegalArgumentException("Too many wildcards in pattern: " + wildcardCount);
                }
            }
        }
    }
    
    /**
     * 构建缓存键
     */
    private String buildCacheKey(String path, String pattern) {
        return path + "::" + pattern;
    }
    
    /**
     * 预加载模式
     * 
     * @param patterns 要预加载的模式列表
     */
    public void preload(List<String> patterns) {
        if (patterns == null) {
            return;
        }
        
        for (String pattern : patterns) {
            if (pattern != null) {
                try {
                    compilePattern(pattern);
                } catch (Exception e) {
                    logger.warn("Failed to preload pattern: {}", pattern, e);
                }
            }
        }
        
        logger.info("Preloaded {} patterns", patterns.size());
    }
    
    /**
     * 清空缓存
     */
    public void clear() {
        patternCache.invalidateAll();
        resultCache.invalidateAll();
        hitCount.set(0);
        missCount.set(0);
        totalMatchTime.set(0);
        logger.debug("SafePathMatcherCache cleared");
    }
    
    /**
     * 获取统计信息
     */
    public CacheStats getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0.0;
        
        com.github.benmanes.caffeine.cache.stats.CacheStats patternStats = patternCache.stats();
        com.github.benmanes.caffeine.cache.stats.CacheStats resultStats = resultCache.stats();
        
        return CacheStats.builder()
            .patternCacheSize((int) patternCache.estimatedSize())
            .resultCacheSize((int) resultCache.estimatedSize())
            .hitCount(hits)
            .missCount(misses)
            .hitRate(hitRate)
            .avgMatchTimeNanos(
                (hits + misses) > 0 ? totalMatchTime.get() / (hits + misses) : 0
            )
            .patternHitRate(patternStats.hitRate())
            .resultHitRate(resultStats.hitRate())
            .patternEvictionCount(patternStats.evictionCount())
            .resultEvictionCount(resultStats.evictionCount())
            .build();
    }
    
    /**
     * 缓存统计信息
     */
    @Data
    @Builder
    public static class CacheStats {
        private int patternCacheSize;
        private int resultCacheSize;
        private long hitCount;
        private long missCount;
        private double hitRate;
        private long avgMatchTimeNanos;
        private double patternHitRate;
        private double resultHitRate;
        private long patternEvictionCount;
        private long resultEvictionCount;
        
        @Override
        public String toString() {
            return String.format(
                "CacheStats{patterns=%d, results=%d, hits=%d, misses=%d, hitRate=%.2f%%, " +
                "avgTime=%dns, patternHitRate=%.2f%%, resultHitRate=%.2f%%, " +
                "patternEvictions=%d, resultEvictions=%d}",
                patternCacheSize, resultCacheSize, hitCount, missCount, 
                hitRate * 100, avgMatchTimeNanos,
                patternHitRate * 100, resultHitRate * 100,
                patternEvictionCount, resultEvictionCount
            );
        }
    }
    
    // Setter方法（用于测试和配置）
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
    
    public void setPatternMaxLength(int patternMaxLength) {
        this.patternMaxLength = patternMaxLength;
    }
    
    public void setMaxWildcards(int maxWildcards) {
        this.maxWildcards = maxWildcards;
    }
    
    public void setPatternTtlSeconds(int patternTtlSeconds) {
        this.patternTtlSeconds = patternTtlSeconds;
    }
    
    public void setResultTtlSeconds(int resultTtlSeconds) {
        this.resultTtlSeconds = resultTtlSeconds;
    }
}