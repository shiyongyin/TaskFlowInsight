# M2M1-050: 指标与日志体系

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-050 |
| 任务名称 | 指标与日志体系 |
| 所属模块 | 监控与护栏 (Guardrails & Monitoring) |
| 优先级 | P0 |
| 预估工期 | S (2天) |
| 依赖任务 | 无 |

## 背景

TaskFlow Insight需要完善的监控体系来保证系统稳定性和可观测性。需要集成Micrometer指标收集、SLF4J日志框架，建立统一的错误码体系，为生产环境运维提供支撑。

## 目标

1. 集成Micrometer指标收集
2. 实现SLF4J结构化日志
3. 建立错误码体系
4. 提供性能指标监控
5. 实现日志级别动态调整

## 非目标

- 不实现自定义监控后端
- 不提供日志分析功能
- 不实现分布式追踪
- 不支持日志存储

## 实现要点

### 1. 统一指标收集体系（与ThreadLocalManager集成）

```java
@Component
@ConditionalOnTfiEnabled
public class TfiMetrics {
    private final MeterRegistry registry;
    private final ThreadLocalManager threadLocalManager;
    private final MemoryGuard memoryGuard;
    
    // 计数器
    private final Counter snapshotCounter;
    private final Counter diffCounter;
    private final Counter errorCounter;
    
    // 计时器
    private final Timer snapshotTimer;
    private final Timer diffTimer;
    private final Timer exportTimer;
    
    // 测量仪
    private final AtomicLong activeSnapshots = new AtomicLong();
    private final AtomicDouble cacheHitRate = new AtomicDouble();
    
    public TfiMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        // 初始化计数器
        this.snapshotCounter = Counter.builder("tfi.snapshot.count")
            .description("Total number of snapshots taken")
            .tag("type", "snapshot")
            .register(registry);
            
        this.diffCounter = Counter.builder("tfi.diff.count")
            .description("Total number of diffs computed")
            .tag("type", "diff")
            .register(registry);
            
        this.errorCounter = Counter.builder("tfi.error.count")
            .description("Total number of errors")
            .register(registry);
            
        // 初始化计时器
        this.snapshotTimer = Timer.builder("tfi.snapshot.time")
            .description("Time taken to create snapshot")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
            
        this.diffTimer = Timer.builder("tfi.diff.time")
            .description("Time taken to compute diff")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
            
        // 注册测量仪
        Gauge.builder("tfi.active.snapshots", activeSnapshots, AtomicLong::get)
            .description("Current number of active snapshots")
            .register(registry);
            
        Gauge.builder("tfi.cache.hit.rate", cacheHitRate, AtomicDouble::get)
            .description("Cache hit rate")
            .register(registry);
    }
    
    public void recordSnapshot(long duration, boolean success) {
        snapshotTimer.record(duration, TimeUnit.MILLISECONDS);
        snapshotCounter.increment();
        
        // 更新ThreadLocal上下文指标
        TrackingContext context = threadLocalManager.getContext();
        context.metrics.recordSnapshot(duration);
        
        // 检查内存压力
        if (!memoryGuard.canProceed()) {
            registry.counter("tfi.memory.pressure").increment();
        }
        
        if (!success) {
            errorCounter.increment();
        }
    }
    
    public void recordDiff(long duration, int changeCount) {
        diffTimer.record(duration, TimeUnit.MILLISECONDS);
        diffCounter.increment();
        
        // 记录变化数量分布
        registry.summary("tfi.diff.changes")
            .record(changeCount);
    }
}
```

### 2. 结构化日志

```java
@Slf4j
@Component
public class TfiLogger {
    
    // 使用MDC进行上下文传递
    public void logSnapshot(String sessionId, String nodeId, long duration) {
        try (MDC.MDCCloseable mdc = MDC.putCloseable("sessionId", sessionId)) {
            MDC.put("nodeId", nodeId);
            MDC.put("duration", String.valueOf(duration));
            MDC.put("operation", "snapshot");
            
            if (duration > 1000) {
                log.warn("Slow snapshot operation: {}ms", duration);
            } else {
                log.debug("Snapshot completed in {}ms", duration);
            }
        }
    }
    
    public void logError(TfiErrorCode errorCode, Exception e, Object... params) {
        MDC.put("errorCode", errorCode.getCode());
        MDC.put("errorType", errorCode.getType().name());
        
        switch (errorCode.getSeverity()) {
            case CRITICAL:
                log.error(errorCode.format(params), e);
                break;
            case ERROR:
                log.error(errorCode.format(params));
                break;
            case WARNING:
                log.warn(errorCode.format(params));
                break;
            case INFO:
                log.info(errorCode.format(params));
                break;
        }
        
        MDC.clear();
    }
}
```

### 3. 错误码体系

```java
public enum TfiErrorCode {
    // 快照错误 (1xxx)
    SNAPSHOT_DEPTH_EXCEEDED("TFI-1001", ErrorType.VALIDATION, Severity.WARNING,
        "Snapshot depth {} exceeds maximum allowed {}"),
    SNAPSHOT_CIRCULAR_REF("TFI-1002", ErrorType.RUNTIME, Severity.INFO,
        "Circular reference detected at path: {}"),
    SNAPSHOT_ACCESS_DENIED("TFI-1003", ErrorType.SECURITY, Severity.ERROR,
        "Access denied for field: {}"),
    
    // 存储错误 (2xxx)
    STORE_CAPACITY_EXCEEDED("TFI-2001", ErrorType.RESOURCE, Severity.WARNING,
        "Store capacity exceeded: {} / {}"),
    STORE_TTL_EXPIRED("TFI-2002", ErrorType.LIFECYCLE, Severity.INFO,
        "Entry expired: {}"),
    STORE_WRITE_FAILED("TFI-2003", ErrorType.IO, Severity.ERROR,
        "Failed to write to store: {}"),
    
    // 导出错误 (3xxx)
    EXPORT_DISK_FULL("TFI-3001", ErrorType.RESOURCE, Severity.CRITICAL,
        "Insufficient disk space for export: {} bytes required"),
    EXPORT_FORMAT_ERROR("TFI-3002", ErrorType.VALIDATION, Severity.ERROR,
        "Invalid export format: {}"),
    
    // 配置错误 (4xxx)
    CONFIG_INVALID("TFI-4001", ErrorType.CONFIGURATION, Severity.ERROR,
        "Invalid configuration: {}"),
    CONFIG_MISSING("TFI-4002", ErrorType.CONFIGURATION, Severity.WARNING,
        "Missing configuration: {}, using default");
    
    private final String code;
    private final ErrorType type;
    private final Severity severity;
    private final String messageTemplate;
    
    public String format(Object... params) {
        return String.format("[%s] %s", code, 
            String.format(messageTemplate, params));
    }
    
    public enum ErrorType {
        VALIDATION,
        RUNTIME,
        SECURITY,
        RESOURCE,
        LIFECYCLE,
        IO,
        CONFIGURATION
    }
    
    public enum Severity {
        CRITICAL,
        ERROR,
        WARNING,
        INFO
    }
}
```

### 4. 性能监控切面

```java
@Aspect
@Component
@ConditionalOnTfiEnabled
public class PerformanceMonitor {
    private final TfiMetrics metrics;
    private final TfiLogger logger;
    
    @Around("@annotation(com.syy.taskflowinsight.annotation.Monitored)")
    public Object monitor(ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录成功指标
            metrics.recordOperation(operation, duration, true);
            
            // 慢操作告警
            if (duration > getThreshold(operation)) {
                logger.logSlowOperation(operation, duration);
            }
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录失败指标
            metrics.recordOperation(operation, duration, false);
            logger.logError(determineErrorCode(e), e, operation);
            
            throw e;
        }
    }
}
```

### 5. 动态日志级别

```java
@RestController
@RequestMapping("/actuator/tfi/logging")
@ConditionalOnTfiEnabled
public class LoggingController {
    
    @PostMapping("/level")
    public ResponseEntity<Void> setLogLevel(
            @RequestParam String logger,
            @RequestParam String level) {
        
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger targetLogger = context.getLogger(logger);
        
        Level newLevel = Level.toLevel(level, Level.INFO);
        targetLogger.setLevel(newLevel);
        
        log.info("Changed log level for {} to {}", logger, level);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/levels")
    public Map<String, String> getLogLevels() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<String, String> levels = new HashMap<>();
        
        for (Logger logger : context.getLoggerList()) {
            if (logger.getName().startsWith("com.syy.taskflowinsight")) {
                levels.put(logger.getName(), 
                          logger.getLevel() != null ? 
                          logger.getLevel().toString() : "INHERITED");
            }
        }
        
        return levels;
    }
}
```

## 测试要求

### 单元测试

1. **指标测试**
   - 计数器准确性
   - 计时器精度
   - 测量仪更新

2. **日志测试**
   - 格式正确性
   - MDC传递
   - 级别控制

3. **错误码测试**
   - 格式化输出
   - 严重级别
   - 参数替换

### 集成测试

1. Micrometer集成
2. 日志框架集成
3. 监控数据准确性

## 验收标准

### 功能验收

- [ ] 指标收集正常
- [ ] 日志输出规范
- [ ] 错误码完整
- [ ] 性能监控准确
- [ ] 动态调整生效

### 性能验收

- [ ] 监控开销 < 1%
- [ ] 日志不阻塞
- [ ] 内存占用稳定

### 质量验收

- [ ] 单元测试覆盖率 > 85%
- [ ] 文档完整
- [ ] 监控全面

## 风险评估

### 技术风险

1. **R034: 性能开销**
   - 缓解：异步记录 + 批量提交
   - 目标：监控开销 < 1%
   - 监控：JMX实时查看

2. **R035: 日志爆炸**
   - 缓解：级别控制 + 速率限制
   - 保护：循环缓冲区
   - 降级：自动提升日志级别

3. **R036: 指标丢失**
   - 缓解：缓冲队列 + 本地文件
   - 备份：双写机制
   - 重试：指数退避

4. **R037: 内存泄漏监控**
   - 缓解：与ThreadLocalManager集成
   - 检测：WeakReference队列
   - 告警：内存使用率阈值

### 依赖风险

- Micrometer版本兼容

## 已明确决策

1. **指标推送后端**: 
   - 默认Prometheus（生产环境）
   - 支持CloudWatch（可选）
   - 本地文件（开发环境）
2. **日志输出格式**: 
   - 生产：JSON格式（便于ELK解析）
   - 开发：文本格式（人类可读）
3. **错误码分配规则**: 
   - TFI-1xxx: 快照相关
   - TFI-2xxx: 比较相关
   - TFI-3xxx: 存储相关
   - TFI-4xxx: 导出相关
   - TFI-9xxx: 系统错误
4. **监控策略**: 统一指标 + 结构化日志

## 代码示例

### 使用示例

```java
@Service
public class BusinessService {
    private final TfiMetrics metrics;
    private final TfiLogger logger;
    
    @Monitored
    public void processOrder(Order order) {
        logger.logInfo("Processing order: {}", order.getId());
        
        long startTime = System.currentTimeMillis();
        try {
            // 业务逻辑
            doProcess(order);
            
            // 记录成功指标
            metrics.recordOrder(System.currentTimeMillis() - startTime, true);
            
        } catch (Exception e) {
            // 记录错误
            logger.logError(TfiErrorCode.PROCESS_FAILED, e, order.getId());
            metrics.recordOrder(System.currentTimeMillis() - startTime, false);
            throw e;
        }
    }
}
```

### 配置示例（区分环境）

```yaml
# 开发环境配置
spring:
  profiles: development

logging:
  level:
    com.syy.taskflowinsight: DEBUG
    com.syy.taskflowinsight.store: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

management:
  metrics:
    export:
      simple:
        enabled: true  # 控制台输出

---
# 生产环境配置
spring:
  profiles: production

logging:
  level:
    com.syy.taskflowinsight: INFO
    com.syy.taskflowinsight.store: WARN
  pattern:
    console: '{"timestamp":"%d{ISO8601}","level":"%level","thread":"%thread","logger":"%logger","message":"%msg","sessionId":"%X{sessionId}"}%n'
    file: '{"timestamp":"%d{ISO8601}","level":"%level","thread":"%thread","logger":"%logger","message":"%msg","sessionId":"%X{sessionId}","nodeId":"%X{nodeId}"}%n'

management:
  metrics:
    export:
      prometheus:
        enabled: true
        step: 1m  # 每分钟推送
    distribution:
      percentiles-histogram:
        tfi.snapshot.time: true
        tfi.diff.time: true
      sla:
        tfi.snapshot.time: 1ms,5ms,10ms,50ms  # SLA阈值
    tags:
      application: taskflow-insight
      environment: production
      region: ${AWS_REGION:us-west-2}

# 监控告警阈值
tfi:
  monitoring:
    alert:
      snapshot-p95-threshold: 10ms
      error-rate-threshold: 0.01  # 1%
      memory-usage-threshold: 0.8  # 80%
```

### 监控输出示例

```
# HELP tfi_snapshot_count_total Total number of snapshots taken
# TYPE tfi_snapshot_count_total counter
tfi_snapshot_count_total{type="snapshot"} 15234.0

# HELP tfi_snapshot_time_seconds Time taken to create snapshot
# TYPE tfi_snapshot_time_seconds summary
tfi_snapshot_time_seconds{quantile="0.5"} 0.0012
tfi_snapshot_time_seconds{quantile="0.95"} 0.0045
tfi_snapshot_time_seconds{quantile="0.99"} 0.0123
tfi_snapshot_time_seconds_count 15234
tfi_snapshot_time_seconds_sum 28.456
```

## 实施计划

### Day 1: 基础实现
- Micrometer集成
- 基本指标定义
- 错误码体系

### Day 2: 完善测试
- 日志框架集成
- 性能监控切面
- 动态日志级别
- 测试验证

## 参考资料

1. Micrometer官方文档
2. SLF4J最佳实践
3. 错误码设计规范

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发