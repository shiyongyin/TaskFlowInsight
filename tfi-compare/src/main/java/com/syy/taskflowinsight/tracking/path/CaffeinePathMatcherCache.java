package com.syy.taskflowinsight.tracking.path;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.syy.taskflowinsight.config.TfiConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 基于Caffeine的路径匹配缓存实现
 * 
 * <p>解决原PathMatcherCache的并发安全问题，采用双缓存策略：
 * <ul>
 *   <li>模式缓存：缓存编译后的Pattern对象</li>
 *   <li>结果缓存：缓存匹配结果</li>
 * </ul>
 * 
 * <p>特性：
 * <ul>
 *   <li>线程安全：基于Caffeine的无锁并发实现</li>
 *   <li>自动驱逐：支持LRU、TTL等多种驱逐策略</li>
 *   <li>监控集成：完整的Micrometer指标支持</li>
 *   <li>降级处理：异常时自动降级到直接匹配</li>
 *   <li>向后兼容：保持与原PathMatcherCache的API兼容</li>
 * </ul>
 * 
 * @author TaskFlowInsight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tfi.matcher.cache.enabled", havingValue = "true", matchIfMissing = false)
public class CaffeinePathMatcherCache implements PathMatcherCacheInterface {
    
    // 缓存实例
    private final Cache<String, Pattern> patternCache;
    private final Cache<String, Boolean> resultCache;
    
    // 监控组件
    private final MeterRegistry meterRegistry;
    private final Counter fallbackCounter;
    private final Timer matchTimer;
    
    // 配置
    private final MatcherCacheConfig config;
    
    public CaffeinePathMatcherCache(TfiConfig tfiConfig, 
                                   MeterRegistry meterRegistry) {
        this.config = extractCacheConfig(tfiConfig);
        this.meterRegistry = meterRegistry;
        
        log.info("Initializing CaffeinePathMatcherCache with pattern-size={}, result-size={}, enabled={}", 
            config.getPatternMaxSize(), 
            config.getResultMaxSize(),
            config.isEnabled());
        
        // 初始化模式缓存：容量较小，TTL较长，用于缓存编译后的Pattern对象
        this.patternCache = Caffeine.newBuilder()
            .maximumSize(config.getPatternMaxSize())
            .expireAfterAccess(config.getPatternTtl())
            .recordStats()
            .build();
            
        // 初始化结果缓存：容量较大，TTL较短，用于缓存匹配结果
        this.resultCache = Caffeine.newBuilder()
            .maximumSize(config.getResultMaxSize())
            .expireAfterWrite(config.getResultTtl())
            .recordStats()
            .build();
        
        // 初始化监控指标（如果MeterRegistry可用）
        if (meterRegistry != null) {
            this.fallbackCounter = Counter.builder("tfi.matcher.cache.fallback.total")
                .description("Cache fallback to direct match count")
                .register(meterRegistry);
                
            this.matchTimer = Timer.builder("tfi.matcher.cache.match.seconds")
                .description("Cache match operation duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
                
            registerMetrics();
        } else {
            this.fallbackCounter = null;
            this.matchTimer = null;
        }
    }
    
    @Override
    public boolean matches(String path, String pattern) {
        if (path == null || pattern == null) {
            return false;
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String resultKey = generateResultKey(pattern, path);
            
            // 第一级：查询结果缓存
            Boolean cached = resultCache.getIfPresent(resultKey);
            if (cached != null) {
                log.trace("Result cache hit for pattern={}, path={}", pattern, path);
                return cached;
            }
            
            // 第二级：获取编译的模式并计算结果
            Pattern compiledPattern = getOrCompilePattern(pattern);
            if (compiledPattern == null) {
                log.debug("Pattern compilation failed: {}", pattern);
                return fallbackMatch(pattern, path);
            }
            
            boolean result = compiledPattern.matcher(path).matches();
            
            // 缓存计算结果
            resultCache.put(resultKey, result);
            
            log.trace("Cache miss, computed result for pattern={}, path={}, result={}", 
                pattern, path, result);
            return result;
            
        } catch (PatternSyntaxException e) {
            log.warn("Invalid pattern syntax: {}", pattern, e);
            return fallbackMatch(pattern, path);
        } catch (Exception e) {
            log.warn("Error accessing cache for pattern={}, path={}: {}, falling back to direct match", 
                pattern, path, e.getMessage());
            if (fallbackCounter != null) {
                fallbackCounter.increment();
            }
            return fallbackMatch(pattern, path);
        } finally {
            sample.stop(matchTimer);
        }
    }
    
    @Override
    public Map<String, Boolean> matchBatch(List<String> paths, String pattern) {
        if (paths == null || paths.isEmpty() || pattern == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Boolean> results = new HashMap<>();
        
        try {
            Pattern compiledPattern = getOrCompilePattern(pattern);
            if (compiledPattern == null) {
                // 降级处理：对每个路径使用直接匹配
                for (String path : paths) {
                    if (path != null) {
                        results.put(path, fallbackMatch(pattern, path));
                    }
                }
                return results;
            }
            
            for (String path : paths) {
                if (path == null) continue;
                
                String resultKey = generateResultKey(pattern, path);
                Boolean cached = resultCache.getIfPresent(resultKey);
                
                if (cached != null) {
                    results.put(path, cached);
                } else {
                    boolean result = compiledPattern.matcher(path).matches();
                    results.put(path, result);
                    resultCache.put(resultKey, result);
                }
            }
        } catch (Exception e) {
            log.warn("Batch match failed for pattern: {}, using fallback", pattern, e);
            if (fallbackCounter != null) {
                fallbackCounter.increment();
            }
            // 降级处理
            for (String path : paths) {
                if (path != null) {
                    results.put(path, fallbackMatch(pattern, path));
                }
            }
        }
        
        return results;
    }
    
    @Override
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
    
    @Override
    public void preload(List<String> patterns) {
        if (patterns == null) {
            return;
        }
        
        int preloaded = 0;
        for (String pattern : patterns) {
            if (pattern != null) {
                try {
                    getOrCompilePattern(pattern);
                    preloaded++;
                } catch (Exception e) {
                    log.warn("Failed to preload pattern: {}", pattern, e);
                }
            }
        }
        
        log.info("Preloaded {}/{} patterns", preloaded, patterns.size());
    }
    
    @Override
    public void clear() {
        patternCache.invalidateAll();
        resultCache.invalidateAll();
        log.debug("CaffeinePathMatcherCache cleared");
    }
    
    @Override
    public PathMatcherCacheInterface.CacheStats getStats() {
        try {
            // 合并两个缓存的统计信息
            com.github.benmanes.caffeine.cache.stats.CacheStats patternStats = patternCache.stats();
            com.github.benmanes.caffeine.cache.stats.CacheStats resultStats = resultCache.stats();
            
            return PathMatcherCacheInterface.CacheStats.builder()
                .hitCount(patternStats.hitCount() + resultStats.hitCount())
                .missCount(patternStats.missCount() + resultStats.missCount())
                .loadCount(patternStats.loadCount() + resultStats.loadCount())
                .loadExceptionCount(0) // Caffeine doesn't provide this directly
                .averageLoadTime(0.0) // Caffeine doesn't provide this directly
                .evictionCount(patternStats.evictionCount() + resultStats.evictionCount())
                .size(patternCache.estimatedSize() + resultCache.estimatedSize())
                .build();
        } catch (Exception e) {
            log.warn("Error getting cache stats: {}", e.getMessage());
            return PathMatcherCacheInterface.CacheStats.builder()
                .hitCount(0)
                .missCount(0)
                .loadCount(0)
                .loadExceptionCount(0)
                .averageLoadTime(0.0)
                .evictionCount(0)
                .size(0)
                .build();
        }
    }
    
    @PreDestroy
    public void destroy() {
        log.info("Shutting down CaffeinePathMatcherCache");
        clear();
    }
    
    // ===== 私有方法 =====
    
    private Pattern getOrCompilePattern(String pattern) {
        try {
            return patternCache.get(pattern, key -> {
                String regex = convertWildcardToRegex(pattern);
                return Pattern.compile(regex);
            });
        } catch (PatternSyntaxException e) {
            log.warn("Invalid pattern syntax: {}", pattern, e);
            return null;
        } catch (Exception e) {
            log.warn("Error compiling pattern: {}", pattern, e);
            return null;
        }
    }
    
    /**
     * 将通配符模式转换为正则表达式
     * 保持与原PathMatcherCache的兼容性
     */
    private String convertWildcardToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    // ** 匹配多层
                    i += 2;
                    if (i < pattern.length() && pattern.charAt(i) == '.') {
                        i++;
                        regex.append("(.*\\.)?");
                    } else {
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
    
    private boolean isRegexSpecialChar(char c) {
        return "[]{}()\\^$|+".indexOf(c) >= 0;
    }
    
    private String generateResultKey(String pattern, String path) {
        return pattern + ":" + path;
    }
    
    /**
     * 降级匹配（当缓存失败时）
     */
    private boolean fallbackMatch(String pattern, String path) {
        try {
            // 简单的字面量匹配作为最后降级
            if ("*".equals(pattern) || "**".equals(pattern)) {
                return true;
            }
            if (!containsWildcard(pattern)) {
                return pattern.equals(path);
            }
            // 对于包含通配符的模式，使用简单的直接编译
            String regex = convertWildcardToRegex(pattern);
            Pattern directPattern = Pattern.compile(regex);
            return directPattern.matcher(path).matches();
        } catch (Exception e) {
            log.warn("Fallback match also failed for pattern={}, path={}", pattern, path, e);
            return false;
        }
    }
    
    private boolean containsWildcard(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
    }
    
    
    private void registerMetrics() {
        if (meterRegistry == null) {
            return;
        }
        
        // 模式缓存指标
        Gauge.builder("tfi.matcher.cache.hit.ratio", patternCache, 
            cache -> cache.stats().hitRate())
            .description("Pattern cache hit ratio")
            .tag("cache", "pattern")
            .register(meterRegistry);
            
        Gauge.builder("tfi.matcher.cache.size", patternCache,
            cache -> cache.estimatedSize())
            .description("Pattern cache current size")
            .tag("cache", "pattern")
            .register(meterRegistry);
            
        // 结果缓存指标
        Gauge.builder("tfi.matcher.cache.hit.ratio", resultCache,
            cache -> cache.stats().hitRate())
            .description("Result cache hit ratio")
            .tag("cache", "result")
            .register(meterRegistry);
            
        Gauge.builder("tfi.matcher.cache.size", resultCache,
            cache -> cache.estimatedSize())
            .description("Result cache current size")
            .tag("cache", "result")
            .register(meterRegistry);
            
        // 驱逐计数 (Gauge使用.count后缀符合规范)
        Gauge.builder("tfi.matcher.cache.eviction.count", patternCache,
            cache -> cache.stats().evictionCount())
            .description("Pattern cache eviction count")
            .tag("cache", "pattern")
            .register(meterRegistry);
            
        Gauge.builder("tfi.matcher.cache.eviction.count", resultCache,
            cache -> cache.stats().evictionCount())
            .description("Result cache eviction count")
            .tag("cache", "result")
            .register(meterRegistry);
    }
    
    private MatcherCacheConfig extractCacheConfig(TfiConfig tfiConfig) {
        // 从TfiConfig中提取缓存相关配置，提供合理默认值
        return MatcherCacheConfig.builder()
            .enabled(true)  // 由@ConditionalOnProperty控制
            .patternMaxSize(500)
            .resultMaxSize(10000)
            .patternTtl(Duration.ofMinutes(30))
            .resultTtl(Duration.ofMinutes(5))
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    private static class MatcherCacheConfig {
        private boolean enabled;
        private int patternMaxSize;
        private int resultMaxSize;
        private Duration patternTtl;
        private Duration resultTtl;
    }
}