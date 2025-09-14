# Metrics/Micrometer 示例

## 采集器与结构化日志
```java
// MetricsCollector.java
@Component
@ConditionalOnClass(MeterRegistry.class)
public class MetricsCollector {
    
    private final MeterRegistry registry;
    
    // 计数器
    private final Counter trackingCounter;
    private final Counter changeCounter;
    private final Counter errorCounter;
    
    // 计时器
    private final Timer snapshotTimer;
    private final Timer diffTimer;
    private final Timer trackingTimer;
    
    // 测量器
    private final AtomicLong activeTrackingCount = new AtomicLong();
    
    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        
        // 初始化计数器
        this.trackingCounter = Counter.builder("tfi.tracking.total")
            .description("Total tracking operations")
            .register(registry);
            
        this.changeCounter = Counter.builder("tfi.changes.total")
            .description("Total changes detected")
            .register(registry);
            
        this.errorCounter = Counter.builder("tfi.errors.total")
            .description("Total errors")
            .register(registry);
        
        // 初始化计时器
        this.snapshotTimer = Timer.builder("tfi.snapshot.duration")
            .description("Snapshot operation duration")
            .register(registry);
            
        this.diffTimer = Timer.builder("tfi.diff.duration")
            .description("Diff detection duration")
            .register(registry);
            
        this.trackingTimer = Timer.builder("tfi.tracking.duration")
            .description("Full tracking duration")
            .register(registry);
        
        // 注册测量器
        Gauge.builder("tfi.tracking.active", activeTrackingCount, AtomicLong::get)
            .description("Active tracking count")
            .register(registry);
    }
    
    // 记录方法
    public void recordTracking() {
        trackingCounter.increment();
        activeTrackingCount.incrementAndGet();
    }
    
    public void recordChanges(int count) {
        changeCounter.increment(count);
    }
    
    public void recordError(String type) {
        errorCounter.increment();
        registry.counter("tfi.errors.byType", "type", type).increment();
    }
    
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }
    
    public void stopTimer(Timer.Sample sample, Timer timer) {
        sample.stop(timer);
    }
}

// StructuredLogger.java
@Component
public class StructuredLogger {
    
    private static final Logger log = LoggerFactory.getLogger(StructuredLogger.class);
    
    @Value("${tfi.logging.structured:true}")
    private boolean structured;
    
    @Value("${tfi.logging.include-context:true}")
    private boolean includeContext;
    
    public void logTracking(String action, Map<String, Object> data) {
        if (structured) {
            MDC.put("action", action);
            MDC.put("timestamp", Instant.now().toString());
            
            if (includeContext) {
                MDC.put("threadId", String.valueOf(Thread.currentThread().getId()));
                MDC.put("sessionId", getCurrentSessionId());
            }
            
            try {
                log.info(toJson(data));
            } finally {
                MDC.clear();
            }
        } else {
            log.info("{}: {}", action, data);
        }
    }
    
    public void logChange(ChangeRecord change) {
        Map<String, Object> data = new HashMap<>();
        data.put("object", change.getObjectName());
        data.put("field", change.getFieldName());
        data.put("type", change.getChangeType());
        data.put("oldValue", sanitize(change.getOldValue()));
        data.put("newValue", sanitize(change.getNewValue()));
        
        logTracking("CHANGE_DETECTED", data);
    }
    
    private String toJson(Map<String, Object> data) {
        try {
            return new ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            return data.toString();
        }
    }
    
    private Object sanitize(Object value) {
        if (value == null) return null;
        
        String str = value.toString();
        if (str.contains("password") || str.contains("secret")) {
            return "***REDACTED***";
        }
        
        if (str.length() > 1000) {
            return str.substring(0, 1000) + "...";
        }
        
        return value;
    }
}
```

## 基础配置示例（YAML）
```yaml
tfi:
  metrics:
    enabled: true
    export:
      prometheus: true
      interval: 60s
    percentiles: [0.5, 0.95, 0.99]
    
  logging:
    structured: true
    include-context: true
    level: INFO
    sanitize: true
    max-value-length: 1000
```

