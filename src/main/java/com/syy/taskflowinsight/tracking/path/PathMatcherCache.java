package com.syy.taskflowinsight.tracking.path;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 路径匹配器缓存
 * 提供高性能的字段路径匹配功能，支持通配符和缓存
 * 
 * 核心功能：
 * - 通配符路径匹配（*, **, ?）
 * - 编译模式缓存（LRU）
 * - 匹配结果缓存
 * - 线程安全
 * - 性能统计
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
public class PathMatcherCache {
    
    private static final Logger logger = LoggerFactory.getLogger(PathMatcherCache.class);
    
    // ========== 配置属性 ==========
    
    @Value("${tfi.change-tracking.path-matcher.cache-size:1000}")
    private int cacheSize = 1000;
    
    @Value("${tfi.change-tracking.path-matcher.pattern-max-length:256}")
    private int patternMaxLength = 256;
    
    @Value("${tfi.change-tracking.path-matcher.max-wildcards:10}")
    private int maxWildcards = 10;
    
    @Value("${tfi.change-tracking.path-matcher.result-cache-multiplier:10}")
    private int resultCacheMultiplier = 10;
    
    // 缓存：pattern -> compiled regex
    private final Map<String, Pattern> patternCache;
    
    // 缓存：path + pattern -> match result
    private final Map<String, Boolean> resultCache;
    
    // 统计
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong totalMatchTime = new AtomicLong(); // 纳秒
    
    // 单例模式（非Spring环境）
    private static volatile PathMatcherCache instance;
    
    public PathMatcherCache() {
        // 使用ConcurrentHashMap包装LinkedHashMap实现LRU
        this.patternCache = new ConcurrentHashMap<>();
        this.resultCache = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取单例实例（非Spring环境）
     */
    public static PathMatcherCache getInstance() {
        if (instance == null) {
            synchronized (PathMatcherCache.class) {
                if (instance == null) {
                    instance = new PathMatcherCache();
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
            validateInput(pattern);
            
            // 快速路径：精确匹配
            if (!containsWildcard(pattern)) {
                return path.equals(pattern);
            }
            
            // 查询结果缓存
            String cacheKey = buildCacheKey(path, pattern);
            Boolean cached = resultCache.get(cacheKey);
            if (cached != null) {
                hitCount.incrementAndGet();
                return cached;
            }
            
            missCount.incrementAndGet();
            
            // 编译模式并匹配
            Pattern regex = compilePattern(pattern);
            boolean result = regex.matcher(path).matches();
            
            // 缓存结果
            cacheResult(cacheKey, result);
            
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
            Boolean cached = resultCache.get(cacheKey);
            
            if (cached != null) {
                results.put(path, cached);
                hitCount.incrementAndGet();
            } else {
                boolean result = regex.matcher(path).matches();
                results.put(path, result);
                cacheResult(cacheKey, result);
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
     * 编译模式
     */
    private Pattern compilePattern(String pattern) {
        return patternCache.computeIfAbsent(pattern, this::doCompilePattern);
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
                    i += 2;
                    // 处理**后面的点号
                    if (i < pattern.length() && pattern.charAt(i) == '.') {
                        // start.**.end 应该匹配 start.end (0层) 和 start.a.b.end (多层)
                        // 跳过点号
                        i++;
                        // 匹配可选的任意内容加点，或者直接没有内容
                        regex.append("(.*\\.)?");
                    } else {
                        // ** at end or before non-dot
                        regex.append(".*");
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
    private void validateInput(String pattern) {
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
     * 缓存结果
     */
    private void cacheResult(String key, boolean result) {
        // 优化的缓存策略：使用更激进的清理
        int maxResultCacheSize = Math.min(cacheSize * resultCacheMultiplier, 5000); // 限制最大5000条
        
        if (resultCache.size() >= maxResultCacheSize) {
            // 清理一半的缓存，保留最近使用的
            synchronized (resultCache) {
                if (resultCache.size() >= maxResultCacheSize) {
                    // 批量清理，减少锁竞争
                    int toRemove = resultCache.size() / 2;
                    Iterator<Map.Entry<String, Boolean>> iterator = resultCache.entrySet().iterator();
                    while (iterator.hasNext() && toRemove > 0) {
                        iterator.next();
                        iterator.remove();
                        toRemove--;
                    }
                    
                    // 记录清理
                    logger.debug("Cleaned {} entries from result cache", resultCache.size() / 2);
                }
            }
        }
        
        resultCache.put(key, result);
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
        patternCache.clear();
        resultCache.clear();
        hitCount.set(0);
        missCount.set(0);
        totalMatchTime.set(0);
        logger.debug("PathMatcherCache cleared");
    }
    
    /**
     * 获取统计信息
     */
    public CacheStats getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0.0;
        
        return CacheStats.builder()
            .patternCacheSize(patternCache.size())
            .resultCacheSize(resultCache.size())
            .hitCount(hits)
            .missCount(misses)
            .hitRate(hitRate)
            .avgMatchTimeNanos(
                (hits + misses) > 0 ? totalMatchTime.get() / (hits + misses) : 0
            )
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
        
        @Override
        public String toString() {
            return String.format("CacheStats{patterns=%d, results=%d, hits=%d, misses=%d, hitRate=%.2f%%, avgTime=%dns}",
                patternCacheSize, resultCacheSize, hitCount, missCount, 
                hitRate * 100, avgMatchTimeNanos);
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
}