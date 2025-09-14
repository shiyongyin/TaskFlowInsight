# PROMPT-M2M1-042-WarmupCache 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/spring-integration/M2M1-042-WarmupCache.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/path#PathMatcherCache（预热目标）
  - src/main/java/com/syy/taskflowinsight/changetracking.spring#ChangeTrackingAutoConfiguration
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.path-matcher.preload
  - src/main/resources/application.yml: tfi.change-tracking.path-matcher.max-size
  - src/main/resources/application.yml: tfi.change-tracking.warmup.async
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：在启动期对 PathMatcherCache 进行可选预热，降低首呼抖动与风险
- 技术目标：
  - 实现缓存预热机制
  - 支持异步预热（不阻塞启动）
  - 设置有界容量与上限
  - 失败降级为 literal 匹配
  - 统计预热成功/失败数量

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.changetracking.spring.CacheWarmupService
  - [ ] 实现 ApplicationRunner 预热逻辑
  - [ ] 配置预热模式列表
  - [ ] 实现上限控制（pattern-max-length、max-wildcards）
  - [ ] 实现失败降级和统计
  - [ ] 支持同步/异步预热模式
- Out of Scope（排除项）：
  - [ ] 复杂预热策略
  - [ ] 动态预热更新

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.changetracking.spring.CacheWarmupService
   - 新建：com.syy.taskflowinsight.changetracking.spring.WarmupConfig
   - 新建：com.syy.taskflowinsight.changetracking.spring.WarmupStats
   - 修改：PathMatcherCache（添加 preload 方法）
   - 修改：ChangeTrackingAutoConfiguration（注册预热服务）

2. 给出重构/新建的**类与方法签名**：
```java
// CacheWarmupService.java
@Component
@ConditionalOnProperty(prefix = "tfi.change-tracking.warmup", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CacheWarmupService implements ApplicationRunner {
    private final PathMatcherCache pathMatcherCache;
    private final WarmupConfig config;
    private final ExecutorService executor;
    private final WarmupStats stats;
    
    public CacheWarmupService(
            PathMatcherCache pathMatcherCache,
            ChangeTrackingProperties properties) {
        this.pathMatcherCache = pathMatcherCache;
        this.config = properties.getWarmup();
        this.executor = config.isAsync() 
            ? Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "cache-warmup");
                t.setDaemon(true);
                return t;
              })
            : null;
        this.stats = new WarmupStats();
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> patterns = config.getPreloadPatterns();
        if (patterns.isEmpty()) {
            log.info("No patterns configured for cache warmup");
            return;
        }
        
        log.info("Starting cache warmup with {} patterns", patterns.size());
        
        if (config.isAsync()) {
            executor.submit(this::doWarmup);
            log.info("Cache warmup started asynchronously");
        } else {
            doWarmup();
            log.info("Cache warmup completed synchronously");
        }
    }
    
    private void doWarmup() {
        long startTime = System.currentTimeMillis();
        
        for (String pattern : config.getPreloadPatterns()) {
            try {
                // 检查上限
                if (!validatePattern(pattern)) {
                    stats.recordFailure(pattern, "Pattern exceeds limits");
                    continue;
                }
                
                // 预编译并缓存
                pathMatcherCache.precompile(pattern);
                stats.recordSuccess(pattern);
                
                // 批次间延迟（避免启动风暴）
                if (config.getBatchDelay() > 0) {
                    Thread.sleep(config.getBatchDelay());
                }
                
            } catch (Exception e) {
                log.warn("Failed to preload pattern: {}", pattern, e);
                stats.recordFailure(pattern, e.getMessage());
                
                // 降级策略
                if (config.isFallbackToLiteral()) {
                    pathMatcherCache.registerLiteral(pattern);
                }
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        stats.setDuration(duration);
        
        log.info("Cache warmup completed in {}ms: success={}, failed={}, fallback={}",
            duration, stats.getSuccessCount(), stats.getFailureCount(), stats.getFallbackCount());
        
        // 发布事件（可选）
        publishWarmupCompleteEvent(stats);
    }
    
    private boolean validatePattern(String pattern) {
        // 长度检查
        if (pattern.length() > config.getPatternMaxLength()) {
            return false;
        }
        
        // 通配符数量检查
        long wildcardCount = pattern.chars()
            .filter(ch -> ch == '*' || ch == '?')
            .count();
        if (wildcardCount > config.getMaxWildcards()) {
            return false;
        }
        
        return true;
    }
    
    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

// WarmupConfig.java
@Data
public class WarmupConfig {
    private boolean enabled = true;
    private boolean async = true;              // 异步预热
    private List<String> preloadPatterns = List.of(
        "**/id",
        "**/uuid",
        "**/name",
        "**/status",
        "user.**",
        "order.items[*].**"
    );
    private int patternMaxLength = 512;
    private int maxWildcards = 32;
    private long batchDelay = 10;              // 批次间延迟ms
    private boolean fallbackToLiteral = true;  // 失败降级
}

// WarmupStats.java
@Data
public class WarmupStats {
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicInteger fallbackCount = new AtomicInteger();
    private final Map<String, String> failures = new ConcurrentHashMap<>();
    private long duration;
    
    public void recordSuccess(String pattern) {
        successCount.incrementAndGet();
    }
    
    public void recordFailure(String pattern, String reason) {
        failureCount.incrementAndGet();
        failures.put(pattern, reason);
    }
    
    public void recordFallback(String pattern) {
        fallbackCount.incrementAndGet();
    }
}

// PathMatcherCache.java (扩展)
public class PathMatcherCache {
    // 新增预编译方法
    public void precompile(String pattern) {
        FSMPattern compiled = compile(pattern);
        cache.put(pattern, compiled);
    }
    
    // 新增字面量注册（降级用）
    public void registerLiteral(String pattern) {
        cache.put(pattern, new LiteralPattern(pattern));
    }
}
```

3. 配置示例：
```yaml
tfi:
  change-tracking:
    warmup:
      enabled: true
      async: true
      preload-patterns:
        - "**/id"
        - "**/uuid"
        - "user.**"
        - "order.items[*].**"
      pattern-max-length: 512
      max-wildcards: 32
      batch-delay: 10
      fallback-to-literal: true
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：CacheWarmupService.java, WarmupConfig.java, WarmupStats.java
  - 修改：PathMatcherCache.java（添加预编译方法）
  - 配置：application.yml 预热配置
  - 测试：CacheWarmupServiceTest.java
- 测试：
  - 单测：预热逻辑、上限控制、降级
  - 集成测试：Spring Boot 启动预热
- 文档：预热配置说明
- 监控：预热统计指标

## 7) API & MODELS（必须具体化）
- 预热配置：
  - preload-patterns：预热模式列表
  - async：是否异步（默认 true）
  - batch-delay：批次延迟（避免启动风暴）
  - fallback-to-literal：失败降级策略
- 统计数据：
  - successCount：成功数
  - failureCount：失败数
  - fallbackCount：降级数
  - duration：总耗时

## 8) DATA & STORAGE
- 预热列表：配置文件或外部文件
- 缓存存储：PathMatcherCache 内存
- 统计数据：内存，可选暴露到 Actuator

## 9) PERFORMANCE & RELIABILITY
- 预热性能：< 1秒（100个模式）
- 异步模式：不阻塞启动
- 批次延迟：避免 CPU 峰值
- 失败处理：降级为字面量匹配
- 资源控制：单线程执行器

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 预热成功测试
  - [ ] 上限验证测试
    - 超长模式拒绝
    - 过多通配符拒绝
  - [ ] 降级测试
    - 编译失败降级
    - 统计正确性
  - [ ] 异步模式测试
    - 不阻塞主线程
    - 最终完成验证
- 集成测试：
  - [ ] Spring Boot 启动集成
  - [ ] 配置加载验证
  - [ ] 预热完成事件
- 性能测试：
  - [ ] 100个模式 < 1秒
  - [ ] 启动时间影响 < 100ms（异步）

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：预热成功执行
- [ ] 性能：不影响启动时间
- [ ] 可靠：失败有降级
- [ ] 监控：统计数据可见
- [ ] 配置：灵活可控

## 12) RISKS & MITIGATIONS
- 启动延迟：同步预热慢 → 默认异步模式
- CPU 峰值：批量编译 → 批次延迟
- 失败影响：编译失败 → 降级字面量
- 内存占用：过多缓存 → 有界容量

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议预热列表支持外部文件配置

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：预热列表的维护方式？
  - 责任人：架构组
  - 期限：实现前确认
  - 所需：常用模式统计
- [ ] 问题2：是否需要动态更新预热列表？
  - 责任人：产品组
  - 期限：MVP 后评估
  - 所需：使用场景分析