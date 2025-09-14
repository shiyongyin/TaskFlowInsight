# PROMPT-M2M1-041-ActuatorEndpoint 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/spring-integration/M2M1-041-ActuatorEndpoint.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/actuator#现有端点（如有）
  - src/main/java/com/syy/taskflowinsight/changetracking.spring#ChangeTrackingProperties
  - src/main/java/com/syy/taskflowinsight/metrics#指标收集器
- 相关配置：
  - src/main/resources/application.yml: management.endpoints.web.exposure.include
  - src/main/resources/application.yml: management.endpoint.tfi.enabled
- Spring Boot Actuator 文档：https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：暴露只读的"有效配置 + 指标聚合"端点，便于运维可见性
- 技术目标：
  - 实现 Spring Boot Actuator 自定义端点
  - 暴露有效配置（脱敏）
  - 展示指标聚合数据
  - 统一到 /actuator/tfi/* 命名空间
  - 禁止输出敏感数据

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.actuator.ChangeTrackingEndpoint
  - [ ] 实现 /actuator/tfi/effective-config 端点
  - [ ] 实现配置脱敏处理
  - [ ] 实现指标聚合展示
  - [ ] 集成到 Spring Boot Actuator 框架
  - [ ] 配置端点权限控制
- Out of Scope（排除项）：
  - [ ] 明细数据展示
  - [ ] 样例统计
  - [ ] 写操作端点

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.actuator.ChangeTrackingEndpoint
   - 新建：com.syy.taskflowinsight.actuator.EffectiveConfig
   - 新建：com.syy.taskflowinsight.actuator.MetricsSummary
   - 修改：application.yml（暴露端点）
   - 修改：ChangeTrackingAutoConfiguration（注册端点）

2. 给出重构/新建的**类与方法签名**：
```java
// ChangeTrackingEndpoint.java
@Component
@Endpoint(id = "tfi")
public class ChangeTrackingEndpoint {
    private final ChangeTrackingProperties properties;
    private final MetricsCollector metricsCollector;
    private final ConfigSanitizer sanitizer;
    
    public ChangeTrackingEndpoint(
            ChangeTrackingProperties properties,
            @Autowired(required = false) MetricsCollector metricsCollector) {
        this.properties = properties;
        this.metricsCollector = metricsCollector;
        this.sanitizer = new ConfigSanitizer();
    }
    
    @ReadOperation
    @Selector("effective-config")
    public EffectiveConfig getEffectiveConfig() {
        return EffectiveConfig.builder()
            .version("2.1.0")
            .timestamp(Instant.now())
            .enabled(properties.isEnabled())
            .mode(detectMode())
            .configuration(sanitizeConfig())
            .defaults(getDefaultSources())
            .build();
    }
    
    @ReadOperation
    @Selector("metrics")
    public MetricsSummary getMetrics() {
        if (metricsCollector == null) {
            return MetricsSummary.empty();
        }
        
        return MetricsSummary.builder()
            .timestamp(Instant.now())
            .counters(Map.of(
                "depth.limit.reached", metricsCollector.getCounter("depth.limit"),
                "cycle.detected", metricsCollector.getCounter("cycle.skip"),
                "pattern.compile.fail", metricsCollector.getCounter("pattern.compile.fail"),
                "collection.degrade", metricsCollector.getCounter("collection.degrade.count")
            ))
            .gauges(Map.of(
                "cache.hit.rate", metricsCollector.getGauge("cache.hit.rate"),
                "store.size", metricsCollector.getGauge("store.size")
            ))
            .build();
    }
    
    @ReadOperation
    public Map<String, Object> root() {
        return Map.of(
            "status", "UP",
            "version", "2.1.0",
            "endpoints", List.of(
                "/actuator/tfi/effective-config",
                "/actuator/tfi/metrics"
            )
        );
    }
    
    private String detectMode() {
        // 根据配置推断模式
        if (!properties.isEnabled()) return "DISABLED";
        if (properties.getSnapshot().getMaxDepth() <= 1) return "MINIMAL";
        if (properties.getSnapshot().getMaxDepth() <= 3) return "BALANCED";
        return "AGGRESSIVE";
    }
    
    private Map<String, Object> sanitizeConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // 快照配置
        config.put("snapshot", Map.of(
            "enabled", properties.getSnapshot().isEnableDeep(),
            "maxDepth", properties.getSnapshot().getMaxDepth(),
            "maxStackDepth", "***", // 脱敏
            "includesCount", properties.getSnapshot().getIncludes().size(),
            "excludesCount", properties.getSnapshot().getExcludes().size()
        ));
        
        // 摘要配置
        config.put("summary", Map.of(
            "enabled", properties.getSummary().isEnabled(),
            "maxSize", properties.getSummary().getMaxSize(),
            "maxExamples", properties.getSummary().getMaxExamples()
        ));
        
        // 路径匹配配置
        config.put("pathMatcher", Map.of(
            "patternMaxLength", properties.getPathMatcher().getPatternMaxLength(),
            "maxWildcards", properties.getPathMatcher().getMaxWildcards(),
            "cacheSize", properties.getPathMatcher().getCacheSize(),
            "preloadCount", properties.getPathMatcher().getPreloadPatterns().size()
        ));
        
        // 存储配置（如果启用）
        if (properties.getStore().isEnabled()) {
            config.put("store", Map.of(
                "enabled", true,
                "maxSize", properties.getStore().getMaxSize(),
                "ttlSeconds", properties.getStore().getTtlSeconds()
            ));
        }
        
        return config;
    }
    
    private Map<String, String> getDefaultSources() {
        // 标注哪些配置使用了默认值
        Map<String, String> sources = new HashMap<>();
        sources.put("snapshot.maxDepth", properties.getSnapshot().getMaxDepth() == 3 ? "DEFAULT" : "USER");
        sources.put("summary.maxSize", properties.getSummary().getMaxSize() == 1000 ? "DEFAULT" : "USER");
        sources.put("pathMatcher.cacheSize", properties.getPathMatcher().getCacheSize() == 1000 ? "DEFAULT" : "USER");
        return sources;
    }
}

// EffectiveConfig.java
@Data
@Builder
public class EffectiveConfig {
    private String version;
    private Instant timestamp;
    private boolean enabled;
    private String mode;
    private Map<String, Object> configuration;
    private Map<String, String> defaults;
}

// MetricsSummary.java
@Data
@Builder
public class MetricsSummary {
    private Instant timestamp;
    private Map<String, Long> counters;
    private Map<String, Double> gauges;
    
    public static MetricsSummary empty() {
        return MetricsSummary.builder()
            .timestamp(Instant.now())
            .counters(Collections.emptyMap())
            .gauges(Collections.emptyMap())
            .build();
    }
}

// ConfigSanitizer.java
public class ConfigSanitizer {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password", "secret", "token", "key", "credential",
        "maxStackDepth" // 内部实现细节
    );
    
    public Object sanitize(String key, Object value) {
        if (value == null) return null;
        
        String lowerKey = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lowerKey.contains(sensitive)) {
                return "***";
            }
        }
        
        return value;
    }
}
```

3. 配置端点暴露：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,tfi
  endpoint:
    tfi:
      enabled: true
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：ChangeTrackingEndpoint.java 及相关模型类
  - 配置：application.yml 端点暴露
  - 测试：ChangeTrackingEndpointTest.java
- 测试：
  - 单测：端点响应、脱敏、指标聚合
  - 集成测试：Spring Boot 启动和访问
- 文档：端点 API 说明
- 安全：敏感信息脱敏验证

## 7) API & MODELS（必须具体化）
- 端点路径：
  - GET /actuator/tfi - 根信息
  - GET /actuator/tfi/effective-config - 有效配置
  - GET /actuator/tfi/metrics - 指标摘要
- 响应示例（effective-config）：
```json
{
  "version": "2.1.0",
  "timestamp": "2024-01-01T12:00:00Z",
  "enabled": true,
  "mode": "BALANCED",
  "configuration": {
    "snapshot": {
      "enabled": true,
      "maxDepth": 3,
      "maxStackDepth": "***",
      "includesCount": 0,
      "excludesCount": 5
    }
  },
  "defaults": {
    "snapshot.maxDepth": "DEFAULT",
    "summary.maxSize": "USER"
  }
}
```

## 8) DATA & STORAGE
- 无持久化，实时计算
- 配置从 Properties 读取
- 指标从 MetricsCollector 聚合

## 9) PERFORMANCE & RELIABILITY
- 响应时间：< 50ms
- 只读操作，无副作用
- 缓存：可选缓存聚合结果 1秒

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 配置脱敏测试
    - password → ***
    - maxStackDepth → ***
    - 正常值保留
  - [ ] 模式检测测试
    - disabled/minimal/balanced/aggressive
  - [ ] 默认值标注测试
  - [ ] 指标聚合测试
- 集成测试：
  - [ ] 端点可访问性
  - [ ] 权限控制（需认证）
  - [ ] 响应格式验证
- 安全测试：
  - [ ] 无敏感信息泄露
  - [ ] 只读验证

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：端点正常响应
- [ ] 安全：敏感信息已脱敏
- [ ] 性能：响应 < 50ms
- [ ] 文档：API 说明完整
- [ ] 监控：可集成 Prometheus

## 12) RISKS & MITIGATIONS
- 安全风险：敏感信息泄露 → 严格脱敏 + 权限控制
- 性能风险：频繁调用 → 结果缓存 1秒
- 兼容性：与现有端点冲突 → 使用独立命名空间 /tfi

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 如存在 TaskflowContextEndpoint，建议合并或提供 alias

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：是否需要健康检查端点？
  - 责任人：运维组
  - 期限：实现前确认
  - 所需：健康指标定义
- [ ] 问题2：端点认证策略？
  - 责任人：安全组
  - 期限：部署前确认
  - 所需：认证方案（Basic/OAuth2）