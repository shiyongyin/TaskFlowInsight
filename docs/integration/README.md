# 集成指南

## 概述

TFI 提供了与主流 Java 框架和工具的集成支持，让您能够无缝地在现有项目中引入任务追踪能力。

## 集成方式

- [Spring Boot 集成](./spring-boot.md) - 自动配置和依赖注入
- [Spring MVC 集成](./spring-mvc.md) - Web 应用追踪
- [Micrometer 集成](./micrometer.md) - 指标监控
- [OpenTelemetry 集成](./opentelemetry.md) - 分布式追踪
- [日志框架集成](./logging.md) - 日志关联和增强

## 快速集成检查清单

### 1. Spring Boot 项目（推荐）

- [ ] 添加 `taskflow-insight-spring-boot-starter` 依赖
- [ ] 配置 `application.yml`
- [ ] 添加 `@TFITask` 注解到关键方法
- [ ] 访问 `/actuator/tfi` 查看实时数据

### 2. 普通 Spring 项目

- [ ] 添加 `taskflow-insight-core` 依赖
- [ ] 配置 `@EnableTFI` 注解
- [ ] 配置 AOP 切面
- [ ] 手动配置 Bean

### 3. 非 Spring 项目

- [ ] 添加 `taskflow-insight-core` 依赖
- [ ] 手动初始化 `TFIManager`
- [ ] 使用手动 API 或注解处理器
- [ ] 配置清理和导出策略

## 兼容性矩阵

| 框架/版本 | TFI 1.0 | 说明 |
|----------|---------|------|
| Spring Boot 2.x | ✅ | 完全支持 |
| Spring Boot 3.x | ✅ | 完全支持 |
| Spring Framework 5.x | ✅ | 需要手动配置 |
| Spring Framework 6.x | ✅ | 需要手动配置 |
| Micrometer 1.x | ✅ | 指标集成 |
| OpenTelemetry 1.x | ✅ | 分布式追踪 |
| Logback | ✅ | MDC 集成 |
| Log4j2 | ✅ | MDC 集成 |
| JDK 8 | ✅ | 最低版本 |
| JDK 11 | ✅ | 推荐版本 |
| JDK 17+ | ✅ | 完全支持 |

## 部署架构

### 单体应用

```
┌─────────────────────────────────────┐
│         Spring Boot App             │
│  ┌─────────────────────────────────┐│
│  │            TFI Core             ││
│  │  ┌─────────┬─────────┬────────┐ ││
│  │  │ Collect │ Process │ Export │ ││
│  │  └─────────┴─────────┴────────┘ ││
│  └─────────────────────────────────┘│
│                 │                   │
│                 ▼                   │
│  ┌─────────────────────────────────┐│
│  │      Monitoring Stack           ││
│  │  Prometheus + Grafana + ELK     ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

### 微服务架构

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Service A  │  │   Service B  │  │   Service C  │
│  ┌─────────┐ │  │  ┌─────────┐ │  │  ┌─────────┐ │
│  │   TFI   │ │  │  │   TFI   │ │  │  │   TFI   │ │
│  └─────────┘ │  │  └─────────┘ │  │  └─────────┘ │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                 │
       │    OpenTelemetry Traces           │
       └─────────────────┼─────────────────┘
                         ▼
              ┌─────────────────────┐
              │   Observability     │
              │   - Jaeger          │
              │   - Prometheus      │
              │   - Grafana         │
              │   - ELK Stack       │
              └─────────────────────┘
```

## 配置模板

### application.yml 完整配置

```yaml
tfi:
  # 基础配置
  enabled: true
  auto-start: true
  
  # 任务追踪配置  
  tracking:
    auto-track-enabled: true
    auto-track-packages:
      - com.example.service
      - com.example.controller
    max-depth: 100
    max-subtasks-per-task: 200
    max-messages-per-task: 1000
    max-sessions-per-thread: 10
    
  # 性能配置
  performance:
    sampling-rate: 1.0
    enable-cpu-tracking: false
    async-export: true
    threshold-ms: 100
    
  # 导出配置
  exporters:
    console:
      enabled: true
      show-messages: true
      show-changes: true
      max-depth: 5
    json:
      enabled: false
      output-dir: ./tfi-reports
      max-file-size: 10MB
    html:
      enabled: false
      template: classpath:templates/report.html
      output-dir: ./tfi-reports
      
  # 集成配置
  integrations:
    micrometer:
      enabled: true
      prefix: tfi
      export-interval: PT30S
    opentelemetry:
      enabled: false
      export-endpoint: http://jaeger:14268/api/traces
    logging:
      mdc-enabled: true
      correlation-id-header: X-Correlation-ID
      
  # 清理配置
  cleanup:
    enabled: true
    interval: PT5M
    session-timeout: PT1H
    
  # 安全配置
  security:
    enable-data-masking: true
    mask-patterns:
      - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b" # 信用卡号
      - "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b" # 邮箱
    whitelist-fields:
      - id
      - name
      - status
```

## 环境变量配置

```bash
# Docker 部署环境变量
TFI_ENABLED=true
TFI_SAMPLING_RATE=0.1
TFI_MAX_DEPTH=50
TFI_CLEANUP_INTERVAL=PT10M

# Kubernetes ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: tfi-config
data:
  application.yml: |
    tfi:
      enabled: true
      performance:
        sampling-rate: ${TFI_SAMPLING_RATE:0.1}
      cleanup:
        interval: ${TFI_CLEANUP_INTERVAL:PT5M}
```

## 监控告警配置

### Prometheus 规则

```yaml
# tfi-alerts.yml
groups:
  - name: tfi-alerts
    rules:
      - alert: TFIHighMemoryUsage
        expr: tfi_memory_usage_bytes > 100000000  # 100MB
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "TFI memory usage is high"
          
      - alert: TFIHighTaskDepth
        expr: tfi_task_depth_max > 80
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "TFI task nesting is too deep"
          
      - alert: TFISessionLeakage
        expr: increase(tfi_sessions_total[5m]) > increase(tfi_sessions_completed_total[5m]) * 1.2
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Potential TFI session leakage detected"
```

### Grafana 仪表板

```json
{
  "dashboard": {
    "title": "TaskFlow Insight Dashboard",
    "panels": [
      {
        "title": "Active Sessions",
        "type": "stat",
        "targets": [
          {
            "expr": "tfi_sessions_active"
          }
        ]
      },
      {
        "title": "Task Duration Distribution",
        "type": "histogram",
        "targets": [
          {
            "expr": "tfi_task_duration_seconds_bucket"
          }
        ]
      },
      {
        "title": "Memory Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "tfi_memory_usage_bytes"
          }
        ]
      }
    ]
  }
}
```

## 故障排除

### 常见问题

1. **内存泄漏**
   ```java
   // 检查ThreadLocal清理
   @EventListener(ApplicationEvent.class)
   public void onApplicationShutdown() {
       TFI.clearAllThreadLocals();
   }
   ```

2. **性能影响过大**
   ```yaml
   tfi:
     performance:
       sampling-rate: 0.1  # 降低采样率
       async-export: true  # 启用异步导出
   ```

3. **任务树过深**
   ```yaml
   tfi:
     tracking:
       max-depth: 20  # 限制最大深度
   ```

### 诊断工具

```java
@RestController
public class TFIDiagnosticController {
    
    @GetMapping("/tfi/health")
    public Map<String, Object> health() {
        return Map.of(
            "enabled", TFI.isEnabled(),
            "activeSessions", TFI.getActiveSessionCount(),
            "memoryUsage", TFI.getMemoryUsage(),
            "configration", TFI.getCurrentConfig()
        );
    }
    
    @GetMapping("/tfi/sessions")
    public List<SessionSummary> sessions() {
        return TFI.getAllSessions().stream()
                  .map(SessionSummary::from)
                  .collect(toList());
    }
}
```

## 最佳实践

### 1. 渐进式采用

```java
// 阶段1: 仅在关键路径启用
@TFITask(value = "criticalOperation", condition = "#{@env.getProperty('tfi.critical-only') == 'true'}")
public void criticalOperation() { ... }

// 阶段2: 扩展到更多场景
@TFITask("normalOperation")
public void normalOperation() { ... }

// 阶段3: 全面覆盖
@EnableTFIAutoTracking(basePackages = "com.example")
```

### 2. 性能优化

```yaml
# 生产环境配置
tfi:
  performance:
    sampling-rate: 0.01      # 1% 采样
    async-export: true       # 异步导出
    threshold-ms: 1000       # 只追踪慢任务
  cleanup:
    interval: PT1M           # 更频繁清理
```

### 3. 安全考虑

```java
@TFITask(maskFields = {"password", "token"})
public void sensitiveOperation(UserInfo user) {
    // 敏感信息会被自动脱敏
}
```