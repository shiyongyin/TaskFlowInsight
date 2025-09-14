# M2M1-042: 预热与有界缓存

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-042 |
| 任务名称 | 预热与有界缓存 |
| 所属模块 | Spring集成 (Spring Integration) |
| 优先级 | P2 |
| 预估工期 | S (2天) |
| 依赖任务 | M2M1-003, M2M1-040 |

## 背景

PathMatcher的模式编译是CPU密集型操作，首次使用时可能造成延迟峰值。需要实现启动时预热机制，将常用模式预编译并缓存。同时需要LRU机制控制缓存大小，防止内存溢出。

## 目标

1. 实现PathMatcher预热机制
2. 支持配置化预热列表
3. 实现LRU缓存管理
4. 提供预热统计信息
5. 支持异步预热选项

## 非目标

- 不实现动态预热调整
- 不支持分布式预热
- 不提供预热进度UI
- 不实现自适应预热

## 实现要点

### 1. 预热管理器

```java
@Component
@ConditionalOnTfiEnabled
public class CacheWarmupManager implements ApplicationListener<ApplicationReadyEvent> {
    
    private final PathMatcherCache pathMatcherCache;
    private final TfiProperties properties;
    private final WarmupMetrics metrics;
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (properties.getWarmup().isEnabled()) {
            executeWarmup();
        }
    }
    
    @Async("tfiWarmupExecutor")
    public CompletableFuture<WarmupResult> executeWarmup() {
        long startTime = System.currentTimeMillis();
        WarmupResult result = new WarmupResult();
        
        try {
            // 1. 预热路径模式
            warmupPathPatterns(result);
            
            // 2. 预热对象快照
            warmupSnapshots(result);
            
            // 3. 预热比较器
            warmupComparators(result);
            
            result.setDuration(System.currentTimeMillis() - startTime);
            result.setStatus(WarmupStatus.SUCCESS);
            
        } catch (Exception e) {
            result.setStatus(WarmupStatus.FAILED);
            result.setError(e.getMessage());
        }
        
        metrics.record(result);
        return CompletableFuture.completedFuture(result);
    }
}
```

### 2. 路径模式预热

```java
public class PathPatternWarmer {
    private final List<String> defaultPatterns = Arrays.asList(
        "user.*",
        "user.**",
        "*.id",
        "**.timestamp",
        "order.items.*",
        "order.items.*.price",
        "config.**.enabled",
        "data.cache.**",
        "session.*.attributes",
        "request.headers.*"
    );
    
    public void warmup(PathMatcherCache cache, WarmupConfig config) {
        List<String> patterns = new ArrayList<>();
        
        // 添加默认模式
        if (config.isIncludeDefaults()) {
            patterns.addAll(defaultPatterns);
        }
        
        // 添加自定义模式
        patterns.addAll(config.getCustomPatterns());
        
        // 生成测试路径
        List<String> testPaths = generateTestPaths(patterns);
        
        // 执行预热
        int warmedCount = 0;
        for (String pattern : patterns) {
            for (String path : testPaths) {
                cache.match(pattern, path);
                warmedCount++;
                
                // 避免预热过度
                if (warmedCount >= config.getMaxWarmupEntries()) {
                    return;
                }
            }
        }
    }
    
    private List<String> generateTestPaths(List<String> patterns) {
        Set<String> paths = new HashSet<>();
        
        for (String pattern : patterns) {
            // 生成匹配的测试路径
            paths.add(pattern.replace("*", "test").replace("?", "x"));
            paths.add(pattern.replace("**", "a/b/c").replace("*", "field"));
        }
        
        return new ArrayList<>(paths);
    }
}
```

### 3. LRU缓存实现

```java
@Component
public class BoundedLruCache<K, V> {
    private final int maxSize;
    private final Map<K, V> cache;
    private final LinkedList<K> accessOrder;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public BoundedLruCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new HashMap<>(maxSize);
        this.accessOrder = new LinkedList<>();
    }
    
    public V get(K key) {
        lock.readLock().lock();
        try {
            V value = cache.get(key);
            if (value != null) {
                // 更新访问顺序
                updateAccessOrder(key);
            }
            return value;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            if (cache.containsKey(key)) {
                // 更新已存在的条目
                cache.put(key, value);
                updateAccessOrder(key);
            } else {
                // 添加新条目
                if (cache.size() >= maxSize) {
                    evictLeastUsed();
                }
                cache.put(key, value);
                accessOrder.addFirst(key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void evictLeastUsed() {
        K leastUsed = accessOrder.removeLast();
        cache.remove(leastUsed);
        
        // 记录驱逐事件
        if (log.isDebugEnabled()) {
            log.debug("Evicted cache entry: {}", leastUsed);
        }
    }
    
    private void updateAccessOrder(K key) {
        accessOrder.remove(key);
        accessOrder.addFirst(key);
    }
}
```

### 4. 预热配置

```java
@ConfigurationProperties("tfi.warmup")
public class WarmupConfig {
    /**
     * 是否启用预热
     */
    private boolean enabled = true;
    
    /**
     * 是否异步预热
     */
    private boolean async = true;
    
    /**
     * 预热超时时间
     */
    private Duration timeout = Duration.ofSeconds(30);
    
    /**
     * 最大预热条目数
     */
    private int maxWarmupEntries = 1000;
    
    /**
     * 是否包含默认模式
     */
    private boolean includeDefaults = true;
    
    /**
     * 自定义预热模式
     */
    private List<String> customPatterns = new ArrayList<>();
    
    /**
     * 预热测试对象类
     */
    private List<String> testClasses = Arrays.asList(
        "com.syy.taskflowinsight.test.WarmupTestObject"
    );
}
```

### 5. 预热监控

```java
@Component
public class WarmupMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger warmupCount = new AtomicInteger();
    private final AtomicLong totalWarmupTime = new AtomicLong();
    
    public void record(WarmupResult result) {
        // 记录预热次数
        registry.counter("tfi.warmup.count", 
                        "status", result.getStatus().name())
               .increment();
        
        // 记录预热时间
        registry.timer("tfi.warmup.duration")
               .record(result.getDuration(), TimeUnit.MILLISECONDS);
        
        // 记录缓存大小
        registry.gauge("tfi.warmup.cache.size", result.getCacheSize());
        
        // 更新统计
        warmupCount.incrementAndGet();
        totalWarmupTime.addAndGet(result.getDuration());
    }
    
    @Scheduled(fixedRate = 60000)
    public void reportStatistics() {
        if (warmupCount.get() > 0) {
            double avgTime = (double) totalWarmupTime.get() / warmupCount.get();
            log.info("Warmup statistics - Count: {}, Avg time: {}ms", 
                    warmupCount.get(), avgTime);
        }
    }
}
```

## 测试要求

### 单元测试

1. **预热功能测试**
   - 模式预热验证
   - 缓存命中测试
   - 异步预热测试

2. **LRU缓存测试**
   - 驱逐策略验证
   - 并发访问测试
   - 容量限制测试

3. **性能测试**
   - 预热时间 < 5s
   - 缓存命中率 > 80%
   - 内存占用 < 10MB

### 集成测试

1. Spring启动集成
2. 配置加载验证
3. 监控指标准确性

## 验收标准

### 功能验收

- [ ] 预热机制正常工作
- [ ] LRU驱逐策略正确
- [ ] 配置化预热列表生效
- [ ] 异步预热可选
- [ ] 监控指标准确

### 性能验收

- [ ] 预热时间可控
- [ ] 缓存命中率达标
- [ ] 内存使用合理

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 并发测试通过
- [ ] 文档完整

## 风险评估

### 技术风险

1. **R031: 预热阻塞启动**
   - 缓解：异步执行
   - 超时：30秒限制

2. **R032: 缓存溢出**
   - 缓解：严格上限
   - 监控：内存告警

3. **R033: 预热失败**
   - 缓解：降级处理
   - 日志：详细记录

### 依赖风险

- 无外部依赖

## 需要澄清

1. 默认预热模式列表
2. 缓存大小上限（建议1000）
3. 预热失败是否影响启动

## 代码示例

### 使用示例

```java
// 自动预热（零配置）
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        // PathMatcher自动预热
    }
}

// 自定义预热配置
```

```yaml
tfi:
  warmup:
    enabled: true
    async: true
    timeout: PT30S
    max-warmup-entries: 2000
    include-defaults: true
    custom-patterns:
      - "custom.path.**"
      - "special.*.field"
    test-classes:
      - "com.example.MyTestObject"
```

### 预热结果

```java
@Data
public class WarmupResult {
    private WarmupStatus status;
    private long duration;
    private int patternsWarmed;
    private int cacheSizeBefore;
    private int cacheSizeAfter;
    private double hitRateImprovement;
    private String error;
    private Instant timestamp;
    
    public enum WarmupStatus {
        SUCCESS,
        PARTIAL,
        FAILED,
        TIMEOUT
    }
}
```

### 监控输出

```log
2024-01-12 10:00:00 INFO  - Starting TFI cache warmup...
2024-01-12 10:00:01 INFO  - Warmed 50 path patterns
2024-01-12 10:00:01 INFO  - Warmed 20 snapshot objects
2024-01-12 10:00:02 INFO  - Warmup completed in 2000ms
2024-01-12 10:00:02 INFO  - Cache hit rate improved from 0% to 85%
```

## 实施计划

### Day 1: 核心实现
- 预热管理器
- LRU缓存
- 基本预热逻辑

### Day 2: 集成完善
- Spring集成
- 配置处理
- 监控指标
- 测试验证

## 参考资料

1. 缓存预热策略
2. LRU算法实现
3. Spring Boot启动生命周期

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发