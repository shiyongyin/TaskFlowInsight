# VIP-011 PathMatcher 示例汇总（由正文迁移）

## PathMatcherCache 实现与使用
```java
// PathMatcherCache.java
@Component
public class PathMatcherCache {
    
    // ========== 配置 ==========
    
    @Value("${tfi.change-tracking.path-matcher.cache-size:1000}")
    private int cacheSize;
    
    @Value("${tfi.change-tracking.path-matcher.pattern-max-length:256}")
    private int patternMaxLength;
    
    @Value("${tfi.change-tracking.path-matcher.max-wildcards:10}")
    private int maxWildcards;
    
    // 缓存：pattern -> compiled regex
    private final Map<String, Pattern> patternCache;
    
    // 缓存：path + pattern -> match result
    private final Map<String, Boolean> resultCache;
    
    // 统计
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    
    public PathMatcherCache() {
        this.patternCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    return size() > cacheSize;
                }
            }
        );
        
        this.resultCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > cacheSize * 10; // 结果缓存更大
                }
            }
        );
    }
    
    // ========== 核心API ==========
    
    /**
     * 匹配路径
     */
    public boolean matches(String path, String pattern) {
        validateInput(path, pattern);
        
        // 快速路径：精确匹配
        if (!containsWildcard(pattern)) {
            return path.equals(pattern);
        }
        
        // 查询结果缓存
        String cacheKey = path + "::" + pattern;
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
        resultCache.put(cacheKey, result);
        
        return result;
    }
    
    /**
     * 批量匹配
     */
    public Map<String, Boolean> matchBatch(List<String> paths, String pattern) {
        Pattern regex = compilePattern(pattern);
        Map<String, Boolean> results = new HashMap<>();
        
        for (String path : paths) {
            String cacheKey = path + "::" + pattern;
            Boolean cached = resultCache.get(cacheKey);
            
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
     */
    public List<String> findMatchingPatterns(String path, List<String> patterns) {
        List<String> matching = new ArrayList<>();
        
        for (String pattern : patterns) {
            if (matches(path, pattern)) {
                matching.add(pattern);
            }
        }
        
        return matching;
    }
    
    // ========== 模式编译 ==========
    
    private Pattern compilePattern(String pattern) {
        return patternCache.computeIfAbsent(pattern, this::doCompilePattern);
    }
    
    private Pattern doCompilePattern(String pattern) {
        // 转换通配符为正则
        // ... 省略与原文相同实现
        return Pattern.compile(converted);
    }
    
    private boolean containsWildcard(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
    }
    
    private void validateInput(String path, String pattern) {
        if (pattern == null || pattern.length() > patternMaxLength) {
            throw new IllegalArgumentException("Invalid pattern");
        }
        // 统计通配符数量，超过限制抛出异常
    }
    
    // ========== 统计与清理 ==========
    public void preload(List<String> patterns) { /* ... */ }
    public void clear() { /* ... */ }
    public CacheStats getStats() { /* ... */ }
    
    @Data
    @Builder
    public static class CacheStats {
        private int patternCacheSize;
        private int resultCacheSize;
        private long hitCount;
        private long missCount;
        private double hitRate;
    }
}

// 使用示例
@Component
public class FieldFilter {
    
    @Autowired
    private PathMatcherCache pathMatcher;
    
    /**
     * 检查字段是否应该被包含
     */
    public boolean shouldInclude(String fieldPath, List<String> includePatterns, List<String> excludePatterns) {
        // 先检查排除
        for (String exclude : excludePatterns) {
            if (pathMatcher.matches(fieldPath, exclude)) {
                return false;
            }
        }
        
        // 如果没有包含规则，默认包含
        if (includePatterns.isEmpty()) {
            return true;
        }
        
        // 检查包含
        for (String include : includePatterns) {
            if (pathMatcher.matches(fieldPath, include)) {
                return true;
            }
        }
        
        return false;
    }
}
```

## 配置示例（YAML）
```yaml
tfi:
  change-tracking:
    path-matcher:
      cache-size: 1000               # 模式缓存大小
      pattern-max-length: 256        # 模式最大长度
      max-wildcards: 10              # 最大通配符数
      preload-patterns:              # 预加载模式
        - "*.password"
        - "*.secret"
        - "**.internal"
      result-cache-multiplier: 10    # 结果缓存倍数
```

## 测试示例
```java
@Test
public void testBasicMatching() {
    PathMatcherCache matcher = new PathMatcherCache();
    
    // 精确匹配
    assertTrue(matcher.matches("user.name", "user.name"));
    
    // 单层通配符
    assertTrue(matcher.matches("user.name", "user.*"));
    assertTrue(matcher.matches("user.age", "user.*"));
    assertFalse(matcher.matches("user.address.city", "user.*"));
    
    // 多层通配符
    assertTrue(matcher.matches("user.address.city", "user.**"));
    assertTrue(matcher.matches("user.name", "user.**"));
    
    // 单字符通配符
    assertTrue(matcher.matches("user1", "user?"));
    assertFalse(matcher.matches("user10", "user?"));
}

@Test
public void testCachePerformance() {
    PathMatcherCache matcher = new PathMatcherCache();
    String pattern = "user.*.address.**";
    
    // 预热
    matcher.matches("user.alice.address.city", pattern);
    
    // 测试缓存命中
    long start = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
        matcher.matches("user.alice.address.city", pattern);
    }
    long cached = System.nanoTime() - start;
    
    // 测试缓存未命中
    start = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
        matcher.matches("user.bob" + i + ".address.city", pattern);
    }
    long uncached = System.nanoTime() - start;
    
    // 缓存应该快10倍以上
    assertTrue(uncached / cached > 10);
}
```

