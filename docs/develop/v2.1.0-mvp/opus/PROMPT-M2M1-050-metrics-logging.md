# PROMPT-M2M1-050-MetricsLogging 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/guardrails-monitoring/M2M1-050-MetricsLogging.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking#各模块埋点位置
  - src/main/java/com/syy/taskflowinsight/changetracking.spring#Micrometer集成点
- 相关配置：
  - src/main/resources/application.yml: management.metrics.export
  - src/main/resources/logback-spring.xml: 日志配置
- 依赖：
  - io.micrometer:micrometer-core（可选）
  - io.micrometer:micrometer-registry-prometheus（可选）
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供最小可观测指标集与日志，观察护栏触发与降级情况
- 技术目标：
  - 定义轻量级指标接口
  - 实现最小指标集
  - Starter 桥接 Micrometer
  - 结构化日志输出
  - 控制性能开销

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.metrics.MetricsCollector 接口
  - [ ] 创建 com.syy.taskflowinsight.metrics.SimpleMetricsCollector 实现
  - [ ] 创建 com.syy.taskflowinsight.metrics.MicrometerAdapter 适配器
  - [ ] 实现最小指标集（5个核心指标）
  - [ ] 集成结构化日志（JSON格式）
  - [ ] 在各模块添加埋点
- Out of Scope（排除项）：
  - [ ] 复杂指标维度
  - [ ] 采样策略
  - [ ] 分布式追踪

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.metrics.MetricsCollector
   - 新建：com.syy.taskflowinsight.metrics.SimpleMetricsCollector
   - 新建：com.syy.taskflowinsight.metrics.MicrometerAdapter
   - 新建：com.syy.taskflowinsight.logging.StructuredLogger
   - 修改：各核心模块添加埋点

2. 给出重构/新建的**类与方法签名**：
```java
// MetricsCollector.java（轻量接口，核心模块依赖）
public interface MetricsCollector {
    void increment(String name);
    void increment(String name, long delta);
    void gauge(String name, double value);
    void timer(String name, long duration, TimeUnit unit);
    long getCounter(String name);
    double getGauge(String name);
}

// SimpleMetricsCollector.java（默认实现，无外部依赖）
@Component
@ConditionalOnMissingBean(name = "micrometerMetricsCollector")
public class SimpleMetricsCollector implements MetricsCollector {
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicDouble> gauges = new ConcurrentHashMap<>();
    
    @Override
    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }
    
    @Override
    public void increment(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }
    
    @Override
    public void gauge(String name, double value) {
        gauges.computeIfAbsent(name, k -> new AtomicDouble()).set(value);
    }
    
    @Override
    public void timer(String name, long duration, TimeUnit unit) {
        // 简单实现：记录为计数器
        increment(name + ".count");
        increment(name + ".total", unit.toMillis(duration));
    }
    
    @Override
    public long getCounter(String name) {
        return counters.getOrDefault(name, new AtomicLong()).get();
    }
    
    @Override
    public double getGauge(String name) {
        return gauges.getOrDefault(name, new AtomicDouble()).get();
    }
    
    // 定期输出到日志（可选）
    @Scheduled(fixedDelay = 60000)
    public void logMetrics() {
        if (log.isDebugEnabled()) {
            Map<String, Object> metrics = new HashMap<>();
            counters.forEach((k, v) -> metrics.put("counter." + k, v.get()));
            gauges.forEach((k, v) -> metrics.put("gauge." + k, v.get()));
            StructuredLogger.log("metrics.snapshot", metrics);
        }
    }
}

// MicrometerAdapter.java（Starter中使用，桥接Micrometer）
@Component("micrometerMetricsCollector")
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class MicrometerAdapter implements MetricsCollector {
    private final MeterRegistry registry;
    private static final String PREFIX = "tfi.";
    
    public MicrometerAdapter(MeterRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public void increment(String name) {
        registry.counter(PREFIX + name).increment();
    }
    
    @Override
    public void increment(String name, long delta) {
        registry.counter(PREFIX + name).increment(delta);
    }
    
    @Override
    public void gauge(String name, double value) {
        // Micrometer gauge 需要引用对象
        AtomicDouble ref = new AtomicDouble(value);
        registry.gauge(PREFIX + name, ref, AtomicDouble::get);
    }
    
    @Override
    public void timer(String name, long duration, TimeUnit unit) {
        registry.timer(PREFIX + name).record(duration, unit);
    }
    
    @Override
    public long getCounter(String name) {
        Counter counter = registry.find(PREFIX + name).counter();
        return counter != null ? (long) counter.count() : 0;
    }
    
    @Override
    public double getGauge(String name) {
        Gauge gauge = registry.find(PREFIX + name).gauge();
        return gauge != null ? gauge.value() : 0.0;
    }
}

// StructuredLogger.java（结构化日志）
@Slf4j
public class StructuredLogger {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    public static void log(String event, Map<String, Object> data) {
        log(event, data, Level.INFO);
    }
    
    public static void log(String event, Map<String, Object> data, Level level) {
        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("@timestamp", Instant.now().toString());
            entry.put("@event", event);
            entry.put("@thread", Thread.currentThread().getName());
            entry.putAll(data);
            
            String json = mapper.writeValueAsString(entry);
            
            switch (level) {
                case ERROR -> log.error(json);
                case WARN -> log.warn(json);
                case DEBUG -> log.debug(json);
                default -> log.info(json);
            }
        } catch (Exception e) {
            log.error("Failed to log structured event: {}", event, e);
        }
    }
    
    public enum Level {
        ERROR, WARN, INFO, DEBUG
    }
}

// 埋点示例（在各模块中添加）
// ObjectSnapshotDeep.java
public class ObjectSnapshotDeep {
    @Autowired(required = false)
    private MetricsCollector metrics;
    
    public Map<String, Object> captureDeep(Object root, SnapshotConfig config) {
        long startTime = System.nanoTime();
        int depthLimitHit = 0;
        int cycleDetected = 0;
        
        try {
            // ... 快照逻辑
            
            if (depth > config.getMaxDepth()) {
                depthLimitHit++;
                if (metrics != null) {
                    metrics.increment("snapshot.depth.limit");
                }
                StructuredLogger.log("snapshot.depth.limit.reached", Map.of(
                    "path", currentPath,
                    "depth", depth,
                    "limit", config.getMaxDepth()
                ), Level.DEBUG);
            }
            
            if (checkCycle(obj, visited)) {
                cycleDetected++;
                if (metrics != null) {
                    metrics.increment("snapshot.cycle.skip");
                }
            }
            
            // ... 更多逻辑
            
        } finally {
            long duration = System.nanoTime() - startTime;
            if (metrics != null) {
                metrics.timer("snapshot.capture", duration, TimeUnit.NANOSECONDS);
            }
        }
    }
}

// PathMatcherCache.java
public class PathMatcherCache {
    @Autowired(required = false)
    private MetricsCollector metrics;
    
    public boolean matches(String pattern, String path) {
        FSMPattern compiled = cache.get(pattern);
        
        if (compiled != null) {
            if (metrics != null) {
                metrics.increment("pathmatcher.cache.hit");
            }
            return compiled.matches(path);
        }
        
        if (metrics != null) {
            metrics.increment("pathmatcher.cache.miss");
        }
        
        try {
            compiled = compile(pattern);
            cache.put(pattern, compiled);
            return compiled.matches(path);
        } catch (Exception e) {
            if (metrics != null) {
                metrics.increment("pathmatcher.compile.fail");
            }
            StructuredLogger.log("pathmatcher.compile.failed", Map.of(
                "pattern", pattern,
                "error", e.getMessage()
            ), Level.WARN);
            return fallbackToLiteral(pattern, path);
        }
    }
}

// CollectionSummary.java
public class CollectionSummary {
    @Autowired(required = false)
    private MetricsCollector metrics;
    
    public Summary summarize(String path, Object collection, SummaryConfig config) {
        int size = calculateSize(collection);
        
        if (size > config.getMaxSize()) {
            if (metrics != null) {
                metrics.increment("collection.degrade.count");
            }
            StructuredLogger.log("collection.summary.degraded", Map.of(
                "path", path,
                "size", size,
                "maxSize", config.getMaxSize()
            ), Level.DEBUG);
            
            return Summary.builder()
                .size(size)
                .degraded(true)
                .build();
        }
        
        // ... 正常处理
    }
}
```

3. 配置示例：
```yaml
# application.yml
management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}

logging:
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  level:
    com.syy.taskflowinsight.metrics: DEBUG
    com.syy.taskflowinsight.logging: INFO
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：指标收集器接口和实现
  - 新文件：结构化日志工具
  - 修改：各模块添加埋点
  - 测试：MetricsCollectorTest.java
- 配置：
  - 日志配置
  - 指标导出配置
- 文档：
  - 指标定义说明
  - 日志格式说明

## 7) API & MODELS（必须具体化）
- 最小指标集：
  1. `tfi.snapshot.depth.limit` - 深度限制触发次数
  2. `tfi.snapshot.cycle.skip` - 循环引用跳过次数
  3. `tfi.pathmatcher.compile.fail` - 模式编译失败次数
  4. `tfi.pathmatcher.cache.hit/miss` - 缓存命中率
  5. `tfi.collection.degrade.count` - 集合降级次数
- 日志格式：
```json
{
  "@timestamp": "2024-01-01T12:00:00Z",
  "@event": "snapshot.depth.limit.reached",
  "@thread": "main",
  "path": "user.orders[0].items",
  "depth": 4,
  "limit": 3
}
```

## 8) DATA & STORAGE
- 指标存储：内存（SimpleMetricsCollector）或 Micrometer
- 日志输出：文件/控制台/ELK

## 9) PERFORMANCE & RELIABILITY
- 指标开销：< 1% CPU
- 日志级别：关键事件 INFO，调试 DEBUG
- 异步处理：可选异步日志

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 指标递增测试
  - [ ] 日志格式测试
  - [ ] 适配器切换测试
- 集成测试：
  - [ ] 端到端指标收集
  - [ ] Actuator 端点验证

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：指标正确收集
- [ ] 性能：开销 < 1%
- [ ] 日志：格式规范
- [ ] 集成：Micrometer 可选

## 12) RISKS & MITIGATIONS
- 性能风险：频繁埋点 → 采样/批处理
- 日志泛滥：过多输出 → 级别控制
- 依赖风险：Micrometer 不可用 → 降级到 Simple

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议使用 MDC 添加追踪上下文

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：告警阈值配置？
  - 责任人：运维组
  - 期限：部署前确认
  - 所需：各指标告警阈值