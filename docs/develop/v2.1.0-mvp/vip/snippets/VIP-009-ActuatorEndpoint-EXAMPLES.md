# VIP-009 ActuatorEndpoint 示例汇总（由正文迁移）

## 端点与兼容（Endpoint/Delegate）
```java
// TfiEndpoint.java (新建)
@Component
@Endpoint(id = "tfi")
public class TfiEndpoint {
    
    // 根端点：展示可用子端点
    @ReadOperation
    public Map<String, Object> root() {
        return Map.of(
            "status", "UP",
            "version", "2.1.0",
            "endpoints", List.of(
                "/actuator/tfi/effective-config",
                "/actuator/tfi/metrics",
                "/actuator/tfi/context"  // 兼容现有功能
            )
        );
    }
    
    // 有效配置端点（脱敏）
    @ReadOperation
    @Selector("effective-config")
    public EffectiveConfig getEffectiveConfig() {
        // 返回脱敏后的配置
    }
    
    // 指标聚合端点
    @ReadOperation
    @Selector("metrics")
    public MetricsSummary getMetrics() {
        // 返回核心指标
    }
    
    // 上下文信息（保留现有功能）
    @ReadOperation
    @Selector("context")
    public Map<String, Object> getContext() {
        // 迁移自TaskflowContextEndpoint
    }
}

// TaskflowEndpoint.java (保留兼容)
@Component
@Endpoint(id = "taskflow")
@Deprecated
@ConditionalOnProperty(
    prefix = "tfi.compatibility",
    name = "legacy-endpoints",
    havingValue = "true",
    matchIfMissing = true  // 默认保留
)
public class TaskflowEndpoint {
    private final TfiEndpoint delegate;
    
    // 委托给TfiEndpoint
    @ReadOperation
    public Map<String, Object> get() {
        return delegate.getContext();
    }
}
```

## 管理端点配置（YAML）
```yaml
# 端点配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,tfi,taskflow  # 暴露端点
  endpoint:
    tfi:
      enabled: true                        # 启用tfi端点
    taskflow:
      enabled: true                        # 保留taskflow端点

# TFI配置
tfi:
  compatibility:
    legacy-endpoints: true                 # 保留旧端点（默认true）
    legacy-endpoint-deprecation-date: "2024-06-01"  # 计划废弃日期
  
  actuator:
    config:
      sanitize: true                       # 配置脱敏
      sensitive-keys:                      # 敏感键列表
        - password
        - secret
        - key
        - token
    metrics:
      include-details: false                # 是否包含详细指标
```

## 数据模型（EffectiveConfig / MetricsSummary）
```java
// EffectiveConfig.java
@Data
@Builder
public class EffectiveConfig {
    private String version;
    private Instant timestamp;
    private boolean enabled;
    private String mode;  // DISABLED/MINIMAL/BALANCED/AGGRESSIVE
    private Map<String, Object> configuration;  // 脱敏后的配置
    private Map<String, String> defaults;       // 默认值来源
}

// MetricsSummary.java
@Data
@Builder
public class MetricsSummary {
    private Instant timestamp;
    private Map<String, Long> counters;   // 计数器
    private Map<String, Double> gauges;   // 度量值
    private Map<String, Object> rates;    // 速率（计算值）
}
```

## 测试示例（安全与脱敏）
```java
@Test
@WithMockUser(roles = "ACTUATOR")
public void testEndpointSecurity() {
    // 验证需要ACTUATOR角色
}

@Test
public void testSensitiveDataSanitization() {
    // 验证密码等敏感信息被脱敏
}
```

## 迁移计划（Rollout）
```
Phase 1 (Week 1-2): 部署双端点
- tfi和taskflow同时可用
- 监控告警不变

Phase 2 (Week 3-4): 迁移监控
- 更新监控配置指向tfi
- 保留taskflow作为备份

Phase 3 (Week 5-8): 观察期
- 收集使用数据
- 修复发现的问题

Phase 4 (Month 3): 废弃旧端点
- 发布废弃通知
- 设置废弃日期
- 最终移除taskflow端点
```

## 输出示例（JSON）
```json
{
  "version": "2.1.0",
  "timestamp": "2024-01-12T10:00:00Z",
  "enabled": true,
  "mode": "BALANCED",
  "configuration": {
    "snapshot": {
      "enabled": true,
      "maxDepth": 3,
      "maxStackDepth": "***",
      "includesCount": 5,
      "excludesCount": 2
    },
    "summary": {
      "enabled": true,
      "maxSize": 100,
      "maxExamples": 10
    },
    "pathMatcher": {
      "patternMaxLength": 256,
      "maxWildcards": 10,
      "cacheSize": 1000,
      "preloadCount": 3
    }
  },
  "defaults": {
    "source": "application.yml",
    "profile": "default",
    "overrides": 0
  }
}
```

```json
{
  "timestamp": "2024-01-12T10:00:00Z",
  "counters": {
    "depth.limit.reached": 42,
    "cycle.detected": 3,
    "pattern.compile.fail": 0,
    "collection.degrade": 15
  },
  "gauges": {
    "cache.hit.rate": 0.92,
    "store.size": 1024
  },
  "rates": {
    "changes.per.minute": 120.5,
    "cache.hit.percentage": "92%"
  }
}
```

