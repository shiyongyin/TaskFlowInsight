# TaskFlowInsight (tfi-all) 运维手册

> **版本**: v3.0.0 / v4.0.0-routing-refactor  
> **编写角色**: 资深运维专家  
> **更新日期**: 2026-02-16  
> **文档状态**: 正式版

---

## 目录

- [1. 运维概述](#1-运维概述)
- [2. 部署指南](#2-部署指南)
- [3. 配置管理](#3-配置管理)
- [4. 监控体系](#4-监控体系)
- [5. 告警策略](#5-告警策略)
- [6. 日志管理](#6-日志管理)
- [7. 健康检查](#7-健康检查)
- [8. 性能调优](#8-性能调优)
- [9. 故障排查手册](#9-故障排查手册)
- [10. 日常运维操作](#10-日常运维操作)
- [11. 安全运维](#11-安全运维)
- [12. 容灾与恢复](#12-容灾与恢复)
- [13. 运维检查清单](#13-运维检查清单)

---

## 1. 运维概述

### 1.1 系统概述

TaskFlowInsight (TFI) 是一个嵌入到 Java 应用中的库组件（非独立服务），运维重点在于：

| 关注点 | 说明 |
|--------|------|
| **资源消耗** | TFI 使用应用内存存储上下文和快照 |
| **运维端点** | 通过 Spring Actuator 提供运维能力 |
| **性能影响** | 需要监控 TFI 对宿主应用的性能影响 |
| **配置管理** | 运行时可动态调整 TFI 行为 |

### 1.2 运维架构图

```
┌──────────────────────────────────────────────────────┐
│                    宿主应用 (Host Application)         │
│                                                      │
│  ┌─────────────────────────────────────────────────┐ │
│  │            TaskFlowInsight (TFI)                 │ │
│  │                                                 │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐     │ │
│  │  │ 流程追踪  │  │ 变更追踪  │  │ 对象比较  │     │ │
│  │  └──────────┘  └──────────┘  └──────────┘     │ │
│  │                                                 │ │
│  │  ┌──────────────────────────────────────────┐  │ │
│  │  │          运维监控层 (tfi-ops-spring)       │  │ │
│  │  │  Actuator │ Metrics │ Health │ Dashboard  │  │ │
│  │  └──────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
│  ┌──────────────┐  ┌──────────────┐                 │
│  │  /actuator/*  │  │ Prometheus   │                 │
│  │  HTTP 端点    │  │  /metrics    │                 │
│  └──────┬───────┘  └──────┬───────┘                 │
└─────────┼──────────────────┼─────────────────────────┘
          │                  │
          ▼                  ▼
   ┌────────────┐    ┌────────────┐
   │  运维面板   │    │ Prometheus │
   │  (手动访问) │    │ + Grafana  │
   └────────────┘    └────────────┘
```

---

## 2. 部署指南

### 2.1 环境要求

| 项目 | 要求 |
|------|------|
| JDK | 21+ (推荐 21 LTS) |
| Spring Boot | 3.x (当前 3.5.5) |
| 内存 | 宿主应用额外 50-200MB (取决于追踪规模) |
| CPU | 无特殊要求 (TFI 开销极低) |

### 2.2 依赖引入

#### 2.2.1 Maven 依赖

```xml
<!-- 全量引入 (推荐) -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>4.0.0</version>
</dependency>

<!-- 或按需引入子模块 -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>4.0.0</version>
</dependency>
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare-spring-starter</artifactId>
    <version>4.0.0</version>
</dependency>
```

#### 2.2.2 Spring Boot 自动配置

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 2.3 构建部署

```bash
# 构建
./mvnw clean package -DskipTests

# 运行 (示例应用)
java -jar tfi-examples/target/tfi-examples-4.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=19090

# 或通过 Maven
./mvnw spring-boot:run -pl tfi-examples \
  -Dspring-boot.run.profiles=prod
```

### 2.4 环境 Profile 配置

| Profile | Annotation | Compare | Tracking | 日志级别 | 用途 |
|---------|----------|----------|----------|----------|------|
| `dev` | 启用 | 启用 | 启用 | DEBUG | 开发调试 |
| `prod` | 禁用 | 禁用 | 禁用 | INFO | 生产环境 |
| 自定义 | 按需 | 按需 | 按需 | 按需 | 灵活配置 |

> **生产环境建议**: 分别控制 Spring annotation、Compare 与 tracking；纯 Java Flow 静态开关只由程序化 API 拥有。

---

## 3. 配置管理

### 3.1 核心配置清单

```yaml
tfi:
  # === 注解支持 ===
  annotation:
    enabled: true                             # @TfiTask/@TfiTrack

  # === 对象比较 ===
  compare:
    enabled: true                             # 当前上下文比较入口
    max-depth: 10                             # 最大逻辑深度
    max-compared-nodes: 10000                 # 单次请求节点预算
    max-elements: 1000                        # 两侧容器元素预算
    tracking:
      enabled: true                           # 接入当前 Flow advice

  # === 上下文管理 ===
  context:
    max-age-millis: 3600000                   # 上下文最大存活时间
    leak-detection-enabled: true              # 泄漏检测
```

### 3.2 系统属性覆盖

可通过 JVM 参数覆盖任何配置：

```bash
java -jar app.jar \
  -Dtfi.annotation.enabled=true \
  -Dtfi.compare.enabled=true \
  -Dtfi.compare.tracking.enabled=true \
  -Dtfi.compare.max-compared-nodes=5000 \
  -Dtfi.compare.max-depth=5
```

### 3.3 运行时动态配置

```java
// 编程方式启用/禁用
TFI.enable();   // 运行时启用
TFI.disable();  // 运行时禁用

// 通过 Actuator 端点 (如已启用 basic-tfi)
// POST /actuator/basic-tfi/switch?enabled=true
```

### 3.4 生产环境推荐配置

```yaml
# application-prod.yml
tfi:
  annotation:
    enabled: false                  # 关闭 Spring advice
  compare:
    enabled: false                  # 关闭当前上下文比较入口
    max-compared-nodes: 5000        # 限制单次节点预算
    max-depth: 5                    # 限制深度
    tracking:
      enabled: false               # 不连接 TfiTask deep tracking
  context:
    max-age-millis: 120000          # 缩短上下文最大存活时间
    leak-detection-enabled: true    # 保持泄漏检测

# Actuator 暴露
management:
  endpoints:
    web:
      exposure:
        include: health,info,taskflow,tfi-metrics
  endpoint:
    health:
      show-details: authorized      # 认证后显示详情
```

---

## 4. 监控体系

### 4.1 Actuator 端点一览

| 端点 | 路径 | 方法 | 认证 | 说明 |
|------|------|------|------|------|
| TFI 概览 | `/actuator/taskflow` | GET | 建议 | 当前会话、任务、状态概览 |
| 上下文诊断 | `/actuator/taskflow-context` | GET | 建议 | 上下文泄漏检测、活跃线程 |
| TFI 指标 | `/actuator/tfi-metrics` | GET | 建议 | 性能指标读取 |
| TFI 指标重置 | `/actuator/tfi-metrics` | DELETE | 必须 | 重置指标计数器 |
| 基础控制 | `/actuator/basic-tfi` | GET/POST | 必须 | 启停控制、清理 |
| 高级 API | `/actuator/tfi-advanced/*` | GET/POST | 必须 | 完整 REST API |
| 健康检查 | `/actuator/health` | GET | 可选 | TFI 组件健康状态 |
| Prometheus | `/actuator/prometheus` | GET | 可选 | Prometheus 格式指标 |

### 4.2 关键监控指标

#### 4.2.1 TFI 核心指标

| 指标名 | 类型 | 说明 | 告警阈值 |
|--------|------|------|----------|
| `tfi.sessions.active` | Gauge | 活跃会话数 | > 100 警告 |
| `tfi.tasks.active` | Gauge | 活跃任务数 | > 500 警告 |
| `tfi.tracking.objects.count` | Gauge | 追踪对象数 | > 800 警告 (max=1000) |
| `tfi.context.leaks.detected` | Counter | 泄漏检测次数 | > 0 告警 |
| `tfi.errors.total` | Counter | 内部错误总数 | > 10/min 告警 |

#### 4.2.2 性能指标

| 指标名 | 类型 | 说明 | 告警阈值 |
|--------|------|------|----------|
| `tfi.stage.duration.ms` | Timer | Stage 执行时长 | P99 > 100ms 警告 |
| `tfi.snapshot.duration.ms` | Timer | 快照耗时 | P99 > 50ms 警告 |
| `tfi.diff.duration.ms` | Timer | Diff 检测耗时 | P99 > 500ms 警告 |
| `tfi.compare.duration.ms` | Timer | 对象比较耗时 | P99 > 1s 警告 |
| `tfi.export.duration.ms` | Timer | 导出耗时 | P99 > 2s 警告 |

#### 4.2.3 资源指标

| 指标名 | 类型 | 说明 | 告警阈值 |
|--------|------|------|----------|
| `tfi.memory.snapshot.bytes` | Gauge | 快照内存占用 | > 100MB 警告 |
| `tfi.cache.hit.rate` | Gauge | 缓存命中率 | < 50% 警告 |
| `tfi.cache.size` | Gauge | 缓存条目数 | > 10000 警告 |
| `tfi.degradation.level` | Gauge | 降级等级 | > 0 关注 |

### 4.3 Prometheus + Grafana 集成

#### 4.3.1 Prometheus 配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'tfi-application'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['app-host:19090']
        labels:
          app: 'my-application'
          module: 'tfi'
```

#### 4.3.2 Grafana Dashboard 建议面板

| 面板 | 查询 | 可视化 |
|------|------|--------|
| TFI 状态 | `tfi_enabled` | 状态灯 |
| 活跃会话趋势 | `tfi_sessions_active` | 折线图 |
| 追踪对象水位 | `tfi_tracking_objects_count / 1000` | 仪表盘 (%) |
| Stage P99 延迟 | `histogram_quantile(0.99, tfi_stage_duration_ms)` | 折线图 |
| 泄漏检测累计 | `increase(tfi_context_leaks_detected[1h])` | 计数器 |
| 错误率 | `rate(tfi_errors_total[5m])` | 折线图 |
| 缓存命中率 | `tfi_cache_hit_rate` | 仪表盘 |
| 降级等级 | `tfi_degradation_level` | 状态灯 |

### 4.4 监控访问示例

```bash
# 查看 TFI 概览
curl -s http://localhost:19090/actuator/taskflow | jq .

# 查看上下文诊断
curl -s http://localhost:19090/actuator/taskflow-context | jq .

# 查看 TFI 指标
curl -s http://localhost:19090/actuator/tfi-metrics | jq .

# 查看健康状态
curl -s http://localhost:19090/actuator/health | jq '.components.tfi'

# Prometheus 指标
curl -s http://localhost:19090/actuator/prometheus | grep tfi_
```

---

## 5. 告警策略

### 5.1 告警等级定义

| 等级 | 颜色 | 响应时间 | 处理方式 |
|------|------|----------|----------|
| P0 Critical | 🔴 红 | < 15 分钟 | 立即处理，通知值班 |
| P1 Warning | 🟡 黄 | < 1 小时 | 工作时间处理 |
| P2 Info | 🔵 蓝 | < 24 小时 | 下次巡检处理 |

### 5.2 告警规则

| 编号 | 告警名称 | 条件 | 等级 | 处理预案 |
|------|----------|------|------|----------|
| ALT-001 | TFI 上下文泄漏 | `tfi_context_leaks_detected > 0` | P0 | [故障 F-001](#f-001-上下文泄漏) |
| ALT-002 | 追踪对象超限 | `tfi_tracking_objects_count > 800` | P1 | [故障 F-002](#f-002-追踪对象超限) |
| ALT-003 | TFI 内部错误率 | `rate(tfi_errors_total[5m]) > 0.1` | P1 | [故障 F-003](#f-003-内部错误激增) |
| ALT-004 | Stage 延迟异常 | `tfi_stage_duration_ms_p99 > 100` | P1 | [故障 F-004](#f-004-性能退化) |
| ALT-005 | 缓存命中率低 | `tfi_cache_hit_rate < 0.5` | P2 | [故障 F-005](#f-005-缓存效率低) |
| ALT-006 | 降级触发 | `tfi_degradation_level > 0` | P2 | [故障 F-006](#f-006-系统降级) |
| ALT-007 | 内存占用高 | `tfi_memory_snapshot_bytes > 100MB` | P1 | [故障 F-007](#f-007-内存消耗过高) |

### 5.3 告警通知渠道

| 渠道 | P0 | P1 | P2 |
|------|----|----|-----|
| 短信/电话 | ✅ | ❌ | ❌ |
| 企业微信/钉钉 | ✅ | ✅ | ❌ |
| 邮件 | ✅ | ✅ | ✅ |
| Slack/Teams | ✅ | ✅ | ✅ |

---

## 6. 日志管理

### 6.1 日志配置

#### 6.1.1 生产环境推荐

```yaml
# application-prod.yml
logging:
  level:
    com.syy.taskflowinsight: WARN
    com.syy.taskflowinsight.context: INFO     # 上下文泄漏需要 INFO
    com.syy.taskflowinsight.tracking: WARN
    com.syy.taskflowinsight.api: WARN
  file:
    name: logs/application.log
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
      total-size-cap: 3GB
```

#### 6.1.2 调试环境

```yaml
# application-dev.yml
logging:
  level:
    com.syy.taskflowinsight: DEBUG
    com.syy.taskflowinsight.tracking.detector: TRACE
    com.syy.taskflowinsight.context: DEBUG
```

### 6.2 关键日志模式

| 日志模式 | 级别 | 含义 | 处理 |
|----------|------|------|------|
| `TFI context leak detected` | WARN | 上下文泄漏 | 立即排查 |
| `Max tracked objects exceeded` | WARN | 追踪超限 | 检查清理逻辑 |
| `TFI internal error` | ERROR | 内部异常 | 查看堆栈分析 |
| `Provider fallback to legacy` | INFO | Provider 降级 | 检查 Provider 健康 |
| `Degradation level changed` | INFO | 降级等级变化 | 关注系统负载 |
| `Snapshot depth exceeded` | DEBUG | 快照深度超限 | 调整 max-depth |
| `DiffDetector heavy mode` | DEBUG | 重型比较模式 | 检查字段数量 |

### 6.3 日志分析命令

```bash
# 查找 TFI 泄漏日志
grep -i "leak" logs/application.log | tail -20

# 查找 TFI 错误
grep "TFI internal error" logs/application.log | wc -l

# 查找降级事件
grep "Degradation" logs/application.log | tail -10

# 查找 Provider 降级
grep "fallback to legacy" logs/application.log

# 统计 TFI 错误频率 (最近 1 小时)
grep "$(date -d '1 hour ago' '+%Y-%m-%d %H')" logs/application.log | \
  grep "TFI internal error" | wc -l
```

---

## 7. 健康检查

### 7.1 健康检查端点

```bash
# 完整健康检查
curl -s http://localhost:19090/actuator/health | jq .

# TFI 组件健康
curl -s http://localhost:19090/actuator/health | jq '.components.tfi'
```

### 7.2 TfiHealthIndicator 评分

TFI 健康指标基于多维度评分 (0-100):

| 维度 | 权重 | 说明 |
|------|------|------|
| 内存使用率 | 25% | 快照内存 / JVM 可用内存 |
| CPU 使用率 | 25% | TFI 操作 CPU 占比 |
| 缓存健康 | 25% | 命中率 + 大小是否合理 |
| 错误率 | 25% | 内部错误频率 |

### 7.3 健康等级映射

| 健康等级 | 分数范围 | Spring Status | 处理 |
|----------|----------|---------------|------|
| EXCELLENT | 80-100 | UP | 正常 |
| GOOD | 60-79 | UP | 关注 |
| FAIR | 40-59 | UP | 调优 |
| POOR | 20-39 | DOWN | 干预 |
| CRITICAL | 0-19 | DOWN | 紧急处理 |

### 7.4 健康检查最佳实践

```bash
# 定期健康巡检脚本
#!/bin/bash
HEALTH=$(curl -s http://localhost:19090/actuator/health)
TFI_STATUS=$(echo $HEALTH | jq -r '.components.tfi.status')
TFI_SCORE=$(echo $HEALTH | jq -r '.components.tfi.details.score')

if [ "$TFI_STATUS" = "DOWN" ]; then
    echo "ALERT: TFI is DOWN, score: $TFI_SCORE"
    # 发送告警
fi

echo "TFI Status: $TFI_STATUS, Score: $TFI_SCORE"
```

---

## 8. 性能调优

### 8.1 性能基线

| 操作 | 基线延迟 | 优化后 |
|------|----------|--------|
| Stage 创建 + 关闭 | P95 < 50μs | P95 < 30μs (禁用日志) |
| 浅层快照 (2 字段) | P50 < 50μs | P50 < 30μs (缓存热) |
| Diff 检测 (2 字段) | P95 < 200μs | P95 < 100μs (缓存热) |
| 深度快照 (10 层) | P95 < 5ms | P95 < 3ms (限制深度) |

### 8.2 调优参数

#### 8.2.1 内存优化

```yaml
tfi:
  compare:
    max-compared-nodes: 5000          # 降低单次节点预算
    max-depth: 5                      # 降低逻辑深度
```

#### 8.2.2 性能优化

```yaml
tfi:
  compare:
    auto-route:
      lcs:
        enabled: false                # 关闭 LCS (CPU 密集型)
  diff:
    heavy:
      field-threshold: 30             # 降低重型阈值
```

#### 8.2.3 缓存优化

```yaml
# Caffeine 缓存调优
tfi:
  cache:
    max-size: 5000                    # 缓存上限
    expire-after-write: 300           # 写入过期 (秒)
```

### 8.3 性能测试命令

```bash
# JMH 基准测试
./mvnw -q -P bench -DskipTests exec:java \
  -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner

# 路由性能基准
./mvnw -q -pl tfi-examples -Pbench -DskipTests compile \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
  -Dexec.executable=java -Dexec.classpathScope=runtime \
  '-Dexec.args=-cp %classpath com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner'

# 性能门禁测试
./mvnw -pl tfi-all -Dtest=TfiRoutingPerfGateTests \
  -Dit.test=TfiRoutingPerfGateIT verify \
  -Dtfi.perf.enabled=true -Dtfi.perf.strict=true
```

### 8.4 JVM 调优建议

```bash
# 推荐 JVM 参数 (与宿主应用一起)
java -jar app.jar \
  -XX:+UseZGC \                      # 低延迟 GC
  -Xms512m -Xmx2g \                  # 堆大小
  -XX:+UseStringDeduplication \       # 字符串去重
  -XX:MaxMetaspaceSize=256m \         # Metaspace 限制
  --enable-preview                    # 虚拟线程支持
```

---

## 9. 故障排查手册

### F-001: 上下文泄漏

**症状**: 日志出现 `TFI context leak detected`

**排查步骤**:

1. **确认泄漏**
   ```bash
   curl -s http://localhost:19090/actuator/taskflow-context | jq .
   ```
   检查 `activeContexts` 和 `leakedContexts` 数量

2. **定位泄漏线程**
   查看日志中的线程 ID 和 Session 信息

3. **常见原因**
   - 线程池中未调用 `TFI.clear()`
   - Stage 未使用 try-with-resources
   - 异步操作后未清理上下文

4. **修复方案**
   ```java
   // 方案A: 确保使用 try-with-resources
   try (var stage = TFI.stage("task")) {
       // ...
   }
   
   // 方案B: 线程池清理
   executor.submit(() -> {
       try {
           // 业务逻辑
       } finally {
           TFI.clear(); // 必须清理
       }
   });
   
   // 方案C: 使用 TFI 包装的 Executor
   ExecutorService wrapped = TFI.wrapExecutor(executor);
   ```

5. **紧急处理**
   ```bash
   # 通过 Actuator 清理所有上下文
   curl -X POST http://localhost:19090/actuator/basic-tfi/clear
   ```

---

### F-002: 追踪对象超限

**症状**: 日志出现 `Max tracked objects exceeded`

**排查步骤**:

1. **查看当前追踪数**
   ```bash
   curl -s http://localhost:19090/actuator/tfi-metrics | \
     jq '.tracking.objectsCount'
   ```

2. **常见原因**
   - 循环中重复调用 `TFI.track()` 未清理
   - 长时间运行的请求累积追踪对象
   - 批处理任务未分批清理

3. **修复方案**
   ```java
   // 批处理中定期清理
   for (int i = 0; i < items.size(); i++) {
       TFI.track("item-" + i, items.get(i));
       if (i % 100 == 0) {
           TFI.getChanges(); // 消费并重置
       }
   }
   TFI.clearAllTracking(); // 最终清理
   ```

4. **紧急处理**
   ```java
   TFI.clearAllTracking(); // 清空所有追踪
   ```

---

### F-003: 内部错误激增

**症状**: `rate(tfi_errors_total[5m]) > 0.1`

**排查步骤**:

1. **查看错误日志**
   ```bash
   grep "TFI internal error" logs/application.log | tail -50
   ```

2. **分析错误类型**
   - `ReflectionException` → 对象结构不兼容
   - `StackOverflowError` → 循环引用未检测
   - `OutOfMemoryError` → 内存不足

3. **常见处理**
   ```yaml
   # 降低快照深度
   tfi:
     compare:
       max-depth: 3
   ```

4. **紧急处理**
   ```java
   TFI.disable(); // 临时禁用 TFI
   ```

---

### F-004: 性能退化

**症状**: Stage 延迟 P99 > 100ms

**排查步骤**:

1. **确认延迟分布**
   ```bash
   curl -s http://localhost:19090/actuator/tfi-metrics | \
     jq '.performance'
   ```

2. **检查重型操作**
   - 深度快照是否在热路径上
   - 大集合比较是否频繁触发
   - LCS 算法是否在大列表上运行

3. **优化方案**
   ```yaml
   tfi:
     compare:
       max-depth: 3           # 降低逻辑深度
       max-elements: 1000     # 限制两侧容器元素预算
   ```

---

### F-005: 缓存效率低

**症状**: 缓存命中率 < 50%

**排查步骤**:

1. **查看缓存统计**
   ```bash
   curl -s http://localhost:19090/actuator/tfi-metrics | \
     jq '.cache'
   ```

2. **常见原因**
   - 缓存大小不够 → 频繁淘汰
   - 追踪对象多样性高 → 缓存无法复用
   - 路径缓存未预热

3. **优化**
   ```yaml
   tfi:
     cache:
       max-size: 10000    # 增大缓存
   ```

---

### F-006: 系统降级

**症状**: `tfi_degradation_level > 0`

**降级等级说明**:

| 等级 | 含义 | 影响 |
|------|------|------|
| 0 | 正常 | 全功能 |
| 1 | 轻度降级 | 关闭非必要追踪 |
| 2 | 中度降级 | 仅保留浅层追踪 |
| 3 | 重度降级 | 仅保留流程追踪 |
| 4 | 完全降级 | TFI 禁用 |

**处理**: 等待系统负载降低，降级会自动恢复。

---

### F-007: 内存消耗过高

**症状**: `tfi_memory_snapshot_bytes > 100MB`

**排查步骤**:

1. **定位大快照**
   - 检查深度追踪的对象图大小
   - 检查追踪对象数量

2. **紧急处理**
   ```java
   TFI.clearAllTracking(); // 释放仍由追踪入口持有的状态；路径状态为请求局部，无全局缓存需要清理
   ```

3. **长期优化**
   ```yaml
   tfi:
     compare:
       max-compared-nodes: 2000    # 大幅降低节点预算
       max-depth: 3                # 限制深度
   ```

---

## 10. 日常运维操作

### 10.1 日常巡检清单

| 编号 | 检查项 | 频率 | 命令 |
|------|--------|------|------|
| OP-001 | 健康状态 | 每小时 | `curl /actuator/health` |
| OP-002 | 活跃会话数 | 每小时 | `curl /actuator/taskflow` |
| OP-003 | 上下文泄漏 | 每小时 | `curl /actuator/taskflow-context` |
| OP-004 | 追踪对象水位 | 每 30 分钟 | `curl /actuator/tfi-metrics` |
| OP-005 | 错误日志 | 每天 | `grep "ERROR" logs/application.log` |
| OP-006 | 性能指标 | 每天 | Grafana Dashboard |

### 10.2 运维操作手册

#### 临时启用 TFI (生产排查)

```bash
# 1. 启用 TFI
curl -X POST http://localhost:19090/actuator/basic-tfi/switch?enabled=true

# 2. 执行排查操作...

# 3. 排查完成，禁用 TFI
curl -X POST http://localhost:19090/actuator/basic-tfi/switch?enabled=false

# 4. 清理残留上下文
curl -X POST http://localhost:19090/actuator/basic-tfi/clear
```

#### 清理所有追踪数据

```bash
curl -X POST http://localhost:19090/actuator/basic-tfi/clear
```

#### 重置指标计数器

```bash
curl -X DELETE http://localhost:19090/actuator/tfi-metrics
```

#### 查看完整诊断信息

```bash
# 综合诊断
echo "=== Health ===" && \
curl -s http://localhost:19090/actuator/health | jq '.components.tfi' && \
echo "=== TaskFlow ===" && \
curl -s http://localhost:19090/actuator/taskflow | jq . && \
echo "=== Context ===" && \
curl -s http://localhost:19090/actuator/taskflow-context | jq . && \
echo "=== Metrics ===" && \
curl -s http://localhost:19090/actuator/tfi-metrics | jq .
```

---

## 11. 安全运维

### 11.1 端点安全

```yaml
# 生产环境 Actuator 安全配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,taskflow,tfi-metrics  # 白名单
      base-path: /internal/actuator                # 内部路径
  endpoint:
    health:
      show-details: when_authorized               # 认证后显示
  server:
    port: 19091                                    # 独立管理端口
```

### 11.2 安全检查清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| Actuator 端点白名单 | 必须 | 只暴露必要端点 |
| 管理端口隔离 | 建议 | 使用独立端口 |
| 认证保护 | 建议 | Spring Security 集成 |
| 敏感数据脱敏 | 内置 | `UnifiedDataMasker` |
| 无外部网络请求 | 已确认 | TFI 不发起外部请求 |
| 无秘密泄露 | 已确认 | TFI 不存储敏感配置 |

### 11.3 数据安全

- TFI 追踪的对象数据存储在 JVM 内存中，不持久化
- 快照数据随 `clearAllTracking()` 或 GC 清除
- 敏感字段可通过 `@DiffIgnore` 排除或 `UnifiedDataMasker` 脱敏
- Actuator 端点可配置只读模式

---

## 12. 容灾与恢复

### 12.1 TFI 故障对宿主应用的影响

| TFI 状态 | 对宿主影响 | 原因 |
|----------|------------|------|
| TFI 内部异常 | **无影响** | 异常安全设计，不抛出到业务代码 |
| TFI 内存不足 | **有影响** | 共享 JVM 堆，可能引起 GC 压力 |
| TFI 完全禁用 | **无影响** | 所有操作为 no-op |

### 12.2 恢复流程

#### 场景一：TFI 导致 OOM 风险

```
1. 紧急禁用 TFI
   TFI.disable()

2. 清理所有追踪数据
   TFI.clearAllTracking()

3. 触发 GC
   System.gc() // 建议，非强制

4. 调整配置重启 (如需)
   -Dtfi.compare.max-compared-nodes=1000
   -Dtfi.compare.max-depth=3

5. 验证恢复
   curl /actuator/health
```

#### 场景二：TFI 上下文大量泄漏

```
1. 查看泄漏上下文数
   curl /actuator/taskflow-context

2. 清理所有上下文
   curl -X POST /actuator/basic-tfi/clear

3. 排查代码泄漏点
   - 检查线程池使用
   - 检查 stage 是否 try-with-resources

4. 修复代码部署
```

#### 场景三：Provider 路由失败

```
1. 检查 Provider 状态
   curl /actuator/taskflow

2. Provider 自动降级到 Legacy
   (无需手动干预)

3. 如需手动禁用路由
   -Dtfi.api.routing.enabled=false

4. 修复 Provider 后重新启用
```

---

## 13. 运维检查清单

### 13.1 上线前检查

| 编号 | 检查项 | 状态 |
|------|--------|------|
| PRE-001 | TFI 配置正确 (prod profile) | ☐ |
| PRE-002 | Actuator 端点白名单配置 | ☐ |
| PRE-003 | 日志级别设置为 WARN/INFO | ☐ |
| PRE-004 | max-tracked-objects 合理设置 | ☐ |
| PRE-005 | max-depth 合理设置 | ☐ |
| PRE-006 | 泄漏检测已启用 | ☐ |
| PRE-007 | 健康检查可访问 | ☐ |
| PRE-008 | 监控告警已配置 | ☐ |

### 13.2 发布后验证

| 编号 | 检查项 | 命令 |
|------|--------|------|
| POST-001 | 应用启动正常 | `curl /actuator/health` |
| POST-002 | TFI 组件状态 UP | `curl /actuator/health \| jq .components.tfi` |
| POST-003 | 无错误日志 | `grep "ERROR" logs/application.log` |
| POST-004 | Actuator 端点可访问 | `curl /actuator/taskflow` |
| POST-005 | 指标上报正常 | `curl /actuator/prometheus \| grep tfi` |

### 13.3 定期运维

| 周期 | 任务 | 负责人 |
|------|------|--------|
| 每日 | 检查错误日志和告警 | 值班运维 |
| 每周 | 审查性能趋势 (Grafana) | 运维工程师 |
| 每月 | 清理日志文件、审查配置 | 运维工程师 |
| 每季度 | 性能基准测试对比 | 开发 + 运维 |
| 每次发版 | 执行上线前/发布后检查清单 | 发布经理 |

---

> **文档编写**: 资深运维专家  
> **审核**: 项目经理  
> **下次评审日期**: 依据运维计划
