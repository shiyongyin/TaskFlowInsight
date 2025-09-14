# guardrails-monitoring - M2M1-050-MetricsLogging（VIP 终版）

## 目标与范围

### 业务目标
提供最小可观测指标集与日志，观察护栏触发与降级情况。

### 技术目标
- 轻量级指标接口（核心不依赖监控框架）
- 结构化日志输出
- 性能开销 < 1% CPU

### Out of Scope
- 复杂指标维度
- 采样策略
- 分布式追踪
- 告警规则（由运维配置）

## 核心设计决策

### 分层架构
```
核心层（无依赖）          →  集成层（可选）        →  展示层
MetricsCollector接口    →  MicrometerAdapter   →  /actuator/tfi/metrics
SimpleMetricsCollector  →  PrometheusRegistry  →  /actuator/prometheus
StructuredLogger       →  LogstashEncoder     →  ELK Stack
```

### 最小指标集（5个核心）
```java
// 1. 深度限制触发
tfi.snapshot.depth.limit

// 2. 循环引用跳过
tfi.snapshot.cycle.skip  

// 3. 模式编译失败
tfi.pathmatcher.compile.fail

// 4. 缓存命中率
tfi.pathmatcher.cache.hit
tfi.pathmatcher.cache.miss

// 5. 集合降级次数
tfi.collection.degrade.count
```

## 实现要求

### Phase 1 - 低风险改动（本周）
1. **轻量级指标接口**
   ```java
   public interface MetricsCollector {
       void increment(String name);
       void increment(String name, long delta);
       void gauge(String name, double value);
       void timer(String name, long duration, TimeUnit unit);
       long getCounter(String name);
       double getGauge(String name);
   }
   ```

2. **默认实现（无外部依赖）**
   ```java
   @Component
   @ConditionalOnMissingBean(name = "micrometerMetricsCollector")
   public class SimpleMetricsCollector implements MetricsCollector {
       private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
       private final Map<String, AtomicDouble> gauges = new ConcurrentHashMap<>();
       
       @Override
       public void increment(String name) {
           counters.computeIfAbsent(name, k -> new AtomicLong())
                   .incrementAndGet();
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
   ```

### Phase 2 - 核心功能（下周）
1. **Micrometer适配器（Starter中）**
   ```java
   @Component("micrometerMetricsCollector")
   @ConditionalOnClass(MeterRegistry.class)
   @ConditionalOnBean(MeterRegistry.class)
   public class MicrometerAdapter implements MetricsCollector {
       private final MeterRegistry registry;
       private static final String PREFIX = "tfi.";
       
       @Override
       public void increment(String name) {
           registry.counter(PREFIX + name).increment();
       }
       
       @Override
       public void timer(String name, long duration, TimeUnit unit) {
           registry.timer(PREFIX + name).record(duration, unit);
       }
   }
   ```

2. **结构化日志工具**
   ```java
   @Slf4j
   public class StructuredLogger {
       private static final ObjectMapper mapper = new ObjectMapper();
       
       public static void log(String event, Map<String, Object> data) {
           log(event, data, Level.INFO);
       }
       
       public static void log(String event, Map<String, Object> data, Level level) {
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
       }
   }
   ```

3. **核心组件埋点**
   ```java
   // ObjectSnapshotDeep
   public Map<String, Object> captureDeep(Object root, Config config) {
       long startTime = System.nanoTime();
       
       try {
           // 深度限制检查
           if (depth > config.getMaxDepth()) {
               metrics.increment("snapshot.depth.limit");
               StructuredLogger.log("snapshot.depth.limit.reached", Map.of(
                   "path", currentPath,
                   "depth", depth,
                   "limit", config.getMaxDepth()
               ), Level.DEBUG);
           }
           
           // 循环检测
           if (checkCycle(obj, visited)) {
               metrics.increment("snapshot.cycle.skip");
           }
           
       } finally {
           long duration = System.nanoTime() - startTime;
           metrics.timer("snapshot.capture", duration, TimeUnit.NANOSECONDS);
       }
   }
   ```

### Phase 3 - 优化增强（后续）
1. 指标聚合和统计
2. 自定义维度支持
3. 性能采样优化

## 日志级别约定

### 统一标准
| 级别 | 使用场景 | 示例 |
|------|---------|------|
| ERROR | 系统错误、数据丢失风险 | 配置加载失败、关键组件初始化失败 |
| WARN | 超限警告、降级触发 | 缓存超限、模式编译失败、集合降级 |
| INFO | 关键事件、状态变更 | 开关启用、端点注册、配置生效 |
| DEBUG | 详细信息、调试数据 | 路径跳过、循环检测、匹配详情 |

### 关键事件清单（INFO级别）
```java
// 启动/停止
logger.info("TFI Change Tracking enabled with config: {}", config);
logger.info("TFI endpoint registered at /actuator/tfi");

// 配置变更
logger.info("Deep snapshot enabled, maxDepth={}", maxDepth);
logger.info("Collection summary degraded, size={} > maxSize={}", size, maxSize);

// 降级触发（首次）
logger.info("First degradation triggered for path: {}", path);
```

## 性能指标

### 开销控制
- 指标收集：< 0.1% CPU
- 日志输出：< 0.5% CPU（INFO级别）
- 总开销：< 1% CPU

### 内存占用
- SimpleMetricsCollector：< 1MB（1000个指标）
- 日志缓冲：< 10MB

## 监控集成

### Prometheus示例
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'tfi-metrics'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:19090']
```

### Grafana Dashboard
```json
{
  "panels": [
    {
      "title": "Snapshot Depth Limits",
      "targets": [
        {"expr": "rate(tfi_snapshot_depth_limit_total[5m])"}
      ]
    },
    {
      "title": "Cache Hit Rate",
      "targets": [
        {"expr": "tfi_pathmatcher_cache_hit_total / (tfi_pathmatcher_cache_hit_total + tfi_pathmatcher_cache_miss_total)"}
      ]
    }
  ]
}
```

## 冲突解决方案

### 核心冲突点及决策

1. **指标框架依赖**
   - 冲突：核心依赖 vs 灵活性
   - 决策：接口抽象 + 可选适配
   - 实施：核心用接口，Starter做桥接

2. **日志格式**
   - 冲突：结构化 vs 可读性
   - 决策：开发用文本，生产用JSON
   - 实施：通过配置切换

3. **性能影响**
   - 冲突：完整性 vs 开销
   - 决策：最小集 + 可扩展
   - 实施：默认只收集关键指标

## 测试计划

### 功能测试
- 指标递增正确性
- 日志格式验证
- 适配器切换

### 性能测试
- CPU开销测量
- 内存占用监控
- 并发压力测试

### 集成测试
- Micrometer集成
- Actuator端点
- Prometheus采集

## 实施检查清单

### Phase 1（立即执行）
- [ ] 定义 `MetricsCollector` 接口
- [ ] 实现 `SimpleMetricsCollector`
- [ ] 创建 `StructuredLogger`
- [ ] 更新日志级别

### Phase 2（下周）
- [ ] 实现 `MicrometerAdapter`
- [ ] 添加核心埋点
- [ ] 端点集成
- [ ] 单元测试

### Phase 3（后续）
- [ ] Dashboard模板
- [ ] 告警规则示例
- [ ] 性能优化

## 配置示例

### 开发环境
```yaml
logging:
  level:
    com.syy.taskflowinsight: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### 生产环境
```yaml
logging:
  level:
    com.syy.taskflowinsight: INFO
    com.syy.taskflowinsight.metrics: WARN
  pattern:
    console: '{"timestamp":"%d{ISO8601}","level":"%level","thread":"%thread","logger":"%logger","message":"%msg"}%n'

management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

## 开放问题

1. **告警阈值配置？**
   - 建议：由运维团队根据基线确定

2. **历史数据保留？**
   - 建议：Prometheus默认15天，长期存储用Thanos

3. **采样策略？**
   - 建议：Phase 3考虑，先全量收集

---
*更新：基于工程评审反馈，明确日志级别约定和最小指标集*