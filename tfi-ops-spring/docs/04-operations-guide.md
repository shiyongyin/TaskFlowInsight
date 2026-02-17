# tfi-ops-spring 运维手册

> **专家角色**：资深运维专家  
> **版本**：v3.0.0  
> **日期**：2026-02-16  
> **模块**：tfi-ops-spring（TaskFlowInsight 运维观测模块）

---

## 目录

1. [部署指南](#1-部署指南)
2. [配置管理](#2-配置管理)
3. [监控体系](#3-监控体系)
4. [告警管理](#4-告警管理)
5. [日常巡检](#5-日常巡检)
6. [故障排查](#6-故障排查)
7. [性能调优](#7-性能调优)
8. [缓存运维](#8-缓存运维)
9. [安全运维](#9-安全运维)
10. [备份与恢复](#10-备份与恢复)
11. [升级与回滚](#11-升级与回滚)
12. [Runbook](#12-runbook)

---

## 1. 部署指南

### 1.1 环境要求

| 项目 | 最低要求 | 推荐配置 |
|------|---------|---------|
| JDK | 21+ | 21 LTS |
| Spring Boot | 3.5.x | 3.5.5 |
| 内存 | 512MB | 2GB+ |
| CPU | 1 核 | 2 核+ |
| 磁盘 | 100MB | 500MB+（含日志） |
| 网络 | 内网访问 | 低延迟内网 |

### 1.2 Maven 依赖引入

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-ops-spring</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 1.3 最小配置

```yaml
# application.yml - 最小生产配置
tfi:
  actuator:
    enabled: true
  security:
    enable-data-masking: true
  metrics:
    enabled: true

# Actuator 暴露配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,taskflow,tfi-metrics
  endpoint:
    health:
      show-details: when-authorized
```

### 1.4 部署架构

```
                    ┌──────────────┐
                    │   Nginx/GW   │  ← 网关层：鉴权 + 限流
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼──────┐┌───▼──────┐┌───▼──────┐
        │  App Node 1 ││ App Node 2 ││ App Node 3 │
        │ (tfi-ops)   ││ (tfi-ops)   ││ (tfi-ops)   │
        └─────┬──────┘└───┬──────┘└───┬──────┘
              │            │            │
              └────────────┼────────────┘
                           │
              ┌────────────▼────────────┐
              │      Prometheus         │  ← 指标采集
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │       Grafana           │  ← 可视化
              └─────────────────────────┘
```

### 1.5 启动验证

部署后执行以下验证步骤：

```bash
# 1. 健康检查
curl -s http://localhost:19090/actuator/health | jq .

# 2. TFI 状态检查
curl -s http://localhost:19090/actuator/taskflow | jq .

# 3. 指标检查
curl -s http://localhost:19090/tfi/metrics/summary | jq .

# 4. 性能仪表盘
curl -s http://localhost:19090/api/performance | jq .
```

**预期结果**：

```json
// /actuator/health
{
  "status": "UP",
  "components": {
    "tfi": {
      "status": "UP",
      "details": {
        "health.score": 92
      }
    }
  }
}

// /actuator/taskflow
{
  "version": "3.0.0-MVP",
  "enabled": true,
  "healthScore": 92,
  "healthLevel": "EXCELLENT"
}
```

---

## 2. 配置管理

### 2.1 配置分级

#### 生产环境推荐配置

```yaml
# application-prod.yml
tfi:
  # === 核心开关 ===
  actuator:
    enabled: true                    # 安全端点（默认开启）
  endpoint:
    basic:
      enabled: false                 # 基础端点（生产关闭）
  security:
    enable-data-masking: true        # 数据脱敏（生产必开）
  
  # === 指标 ===
  metrics:
    enabled: true
    tags:
      env: production
      service: my-service
  
  # === 缓存 ===
  store:
    enabled: true
    caffeine:
      max-size: 10000
      default-ttl: 60m
      idle-timeout: 30m
      record-stats: true
      eviction-strategy: LRU
    degrade:
      hit-rate-threshold: 0.2
      max-evictions: 10000
      recovery-threshold: 0.5
      recovery-count: 5
  
  # === 性能 ===
  performance:
    enabled: false                   # 基准测试引擎（生产关闭）
    endpoint:
      enabled: false                 # 基准测试端点（生产关闭）
    dashboard:
      enabled: true                  # 性能仪表盘（生产开启）
    monitor:
      enabled: true                  # 性能监控（生产开启）
      interval-ms: 10000            # 监控间隔 10s（生产加大）
      history-size: 200             # 历史窗口（生产加大）

# === Actuator 暴露 ===
management:
  endpoints:
    web:
      exposure:
        include: health,info,taskflow,tfi-metrics
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
    taskflow:
      enabled: true
  server:
    port: 19091                      # 管理端口独立（推荐）
```

#### 开发环境配置

```yaml
# application-dev.yml
tfi:
  actuator:
    enabled: true
  endpoint:
    basic:
      enabled: true                  # 开发可用
  security:
    enable-data-masking: false       # 开发关闭脱敏
  performance:
    enabled: true                    # 开发可用
    endpoint:
      enabled: true
  store:
    caffeine:
      max-size: 1000                # 开发小缓存
      log-evictions: true           # 开发记录淘汰
```

### 2.2 配置热更新

以下配置支持运行时更新（通过 API）：

| 配置 | 更新方式 | 说明 |
|------|---------|------|
| SLA 阈值 | POST `/api/performance/sla/{op}` | 无需重启 |
| 变更追踪开关 | POST `/actuator/basic-tfi` | 需启用 basic 端点 |

以下配置需要重启生效：

| 配置 | 原因 |
|------|------|
| `tfi.store.caffeine.*` | Caffeine Cache 初始化时配置 |
| `tfi.actuator.enabled` | `@ConditionalOnProperty` 初始化 |
| `tfi.performance.enabled` | Bean 条件装配 |

### 2.3 配置检查清单

部署前必须确认：

- [ ] `tfi.security.enable-data-masking=true`（生产环境）
- [ ] `tfi.endpoint.basic.enabled=false`（生产环境）
- [ ] `tfi.performance.endpoint.enabled=false`（生产环境）
- [ ] `management.endpoints.web.exposure.include` 不含危险端点
- [ ] `management.server.port` 与业务端口分离
- [ ] Actuator 端点有网关层鉴权保护

---

## 3. 监控体系

### 3.1 监控指标清单

#### 3.1.1 TFI 核心指标

| 指标名 | 类型 | 说明 | 告警阈值 |
|--------|------|------|---------|
| `tfi.change_tracking.count` | Counter | 变更追踪次数 | - |
| `tfi.snapshot.count` | Counter | 快照次数 | - |
| `tfi.path_match.count` | Counter | 路径匹配次数 | - |
| `tfi.errors.count` | Counter | 错误次数 | > 100/min |
| `tfi.health.score` | Gauge | 健康评分 | < 70 |

#### 3.1.2 缓存指标

| 指标名 | 类型 | 说明 | 告警阈值 |
|--------|------|------|---------|
| `tfi.store.hits` | Counter | 缓存命中 | - |
| `tfi.store.misses` | Counter | 缓存未命中 | - |
| `tfi.store.evictions` | Counter | 缓存淘汰 | > 10000 |
| `tfi.store.hit_rate` | Gauge | 命中率 | < 0.5 |
| `tfi.store.size` | Gauge | 缓存大小 | > maxSize * 0.9 |

#### 3.1.3 性能指标

| 指标名 | 类型 | 说明 | 告警阈值 |
|--------|------|------|---------|
| `tfi.annotation.zero_sampling.duration` | Timer | 零采样耗时 | P99 > 1μs |
| `tfi.annotation.aspect_overhead.duration` | Timer | AOP 开销 | P99 > 10μs |
| `tfi.annotation.spel_evaluation.duration` | Timer | SpEL 评估 | P99 > 50μs |
| `tfi.performance.snapshot.p95` | Gauge | 快照 P95 | > 10ms |
| `tfi.performance.change_tracking.p95` | Gauge | 变更追踪 P95 | > 5ms |

#### 3.1.4 JVM 指标

| 指标名 | 说明 | 告警阈值 |
|--------|------|---------|
| `jvm.memory.used` | 堆内存使用 | > 80% |
| `jvm.threads.live` | 活跃线程数 | > 500 |
| `jvm.gc.pause` | GC 暂停时间 | > 500ms |
| `process.cpu.usage` | CPU 使用率 | > 80% |

### 3.2 Prometheus 集成

#### 3.2.1 Prometheus 配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'tfi-ops'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['app-node-1:19091', 'app-node-2:19091']
    metric_relabel_configs:
      - source_labels: [__name__]
        regex: 'tfi_.*'
        action: keep
```

#### 3.2.2 关键 PromQL

```promql
# TFI 健康评分
tfi_health_score

# 缓存命中率
rate(tfi_store_hits_total[5m]) / (rate(tfi_store_hits_total[5m]) + rate(tfi_store_misses_total[5m]))

# 错误率
rate(tfi_errors_total[5m]) / rate(tfi_change_tracking_count_total[5m])

# P95 延迟
histogram_quantile(0.95, rate(tfi_annotation_aspect_overhead_duration_seconds_bucket[5m]))
```

### 3.3 Grafana Dashboard

#### 3.3.1 推荐面板布局

```
┌──────────────────────────────────────────────────────┐
│                  TFI Operations Dashboard             │
├──────────────┬──────────────┬────────────────────────┤
│ Health Score │ Cache Hit %  │ Error Rate              │
│     92       │    97.5%     │    0.02%               │
├──────────────┴──────────────┴────────────────────────┤
│              Health Score Trend (24h)                  │
│  ████████████████████████████████████████             │
├──────────────────────────────────────────────────────┤
│              Cache Performance                        │
│  Hits ████████  Misses ██  Evictions █               │
├──────────────────────────────────────────────────────┤
│              Latency Distribution                     │
│  snapshot  ▏█████████▕ P50=2ms  P95=8ms  P99=15ms  │
│  tracking  ▏████████▕  P50=1ms  P95=3ms  P99=7ms   │
│  path      ▏███▕       P50=0.1ms P95=0.5ms P99=1ms │
├──────────────────────────────────────────────────────┤
│              JVM Resources                            │
│  Heap: 45% ████████░░░  Threads: 120  CPU: 12%      │
└──────────────────────────────────────────────────────┘
```

---

## 4. 告警管理

### 4.1 告警规则

#### 4.1.1 TFI 层告警

| 告警名 | 条件 | 级别 | 通知方式 |
|--------|------|------|---------|
| TFI_Health_Critical | healthScore < 50 | CRITICAL | 电话 + 钉钉 |
| TFI_Health_Warning | healthScore < 70 | WARNING | 钉钉 |
| TFI_Error_Spike | error_rate > 1% (5min) | ERROR | 钉钉 |
| TFI_Cache_Degraded | StoreAutoDegrader.isDegraded | WARNING | 钉钉 |
| TFI_Cache_HitRate_Low | hit_rate < 50% (15min) | WARNING | 邮件 |

#### 4.1.2 性能层告警

| 告警名 | 条件 | 级别 | 通知方式 |
|--------|------|------|---------|
| TFI_Snapshot_Slow | P95 > 10ms (5min) | ERROR | 钉钉 |
| TFI_Tracking_Slow | P95 > 5ms (5min) | ERROR | 钉钉 |
| TFI_PathMatch_Slow | P95 > 1ms (5min) | WARNING | 邮件 |
| TFI_SLA_Violation | 任意 SLA 违反 | ERROR | 钉钉 |

#### 4.1.3 系统层告警

| 告警名 | 条件 | 级别 | 通知方式 |
|--------|------|------|---------|
| TFI_Heap_Critical | heap_used > 90% | CRITICAL | 电话 + 钉钉 |
| TFI_Heap_Warning | heap_used > 75% | WARNING | 钉钉 |
| TFI_Threads_High | thread_count > 1000 | WARNING | 钉钉 |
| TFI_GC_Long | gc_pause > 500ms | WARNING | 邮件 |

### 4.2 告警处理流程

```
告警触发
  │
  ├─ CRITICAL ──► 值班人员立即响应（15 分钟内）
  │               └── 执行 Runbook → 升级 → 恢复 → 复盘
  │
  ├─ ERROR ─────► 值班人员 30 分钟内响应
  │               └── 排查原因 → 修复 → 验证
  │
  ├─ WARNING ───► 值班人员 2 小时内响应
  │               └── 评估影响 → 计划修复
  │
  └─ INFO ──────► 记录日志，下次巡检处理
```

### 4.3 告警 API 操作

```bash
# 查看当前告警
curl -s http://localhost:19091/api/performance/alerts | jq .

# 清除特定告警
curl -X DELETE http://localhost:19091/api/performance/alerts/snapshot_sla

# 配置 SLA（调整阈值以减少误报）
curl -X POST http://localhost:19091/api/performance/sla/snapshot \
  -H "Content-Type: application/json" \
  -d '{"maxLatencyMs": 20, "minThroughput": 500, "maxErrorRate": 0.05}'
```

---

## 5. 日常巡检

### 5.1 巡检清单

#### 每日巡检（5 分钟）

| # | 检查项 | 命令/路径 | 正常标准 |
|---|--------|-----------|---------|
| 1 | 健康状态 | GET `/actuator/health` | status = "UP" |
| 2 | 健康评分 | GET `/actuator/taskflow` | healthScore ≥ 70 |
| 3 | 告警列表 | GET `/api/performance/alerts` | 无 CRITICAL/ERROR |
| 4 | 缓存命中率 | GET `/tfi/metrics/summary` | hit_rate ≥ 80% |
| 5 | 错误率 | GET `/actuator/taskflow` → stats.errorRate | < 1% |

#### 每周巡检（15 分钟）

| # | 检查项 | 命令/路径 | 正常标准 |
|---|--------|-----------|---------|
| 1 | 性能趋势 | GET `/api/performance/history/all` | 无异常趋势 |
| 2 | 缓存统计 | GET `/tfi/metrics/summary` | 淘汰数合理 |
| 3 | 会话清理 | POST `/actuator/tfi-advanced/cleanup` | 过期会话已清理 |
| 4 | JVM 内存 | Grafana JVM Dashboard | 无持续增长 |
| 5 | 线程数 | Grafana Threads Panel | 稳定，无泄漏 |

#### 月度巡检（30 分钟）

| # | 检查项 | 操作 | 正常标准 |
|---|--------|------|---------|
| 1 | 基准测试（非生产） | POST `/tfi/benchmark/run` | 与基线差异 < 10% |
| 2 | 配置审计 | 对照配置清单检查 | 全部符合 |
| 3 | 安全审计 | 检查端点暴露情况 | 无过度暴露 |
| 4 | 日志审计 | 检查 TFI 相关日志 | 无异常日志 |
| 5 | 容量评估 | 缓存使用率 + 内存趋势 | 余量充足 |

### 5.2 巡检脚本

```bash
#!/bin/bash
# tfi-health-check.sh - TFI 日常巡检脚本

BASE_URL="${1:-http://localhost:19090}"
MGMT_URL="${2:-http://localhost:19091}"

echo "=== TFI 健康巡检 $(date) ==="

# 1. 健康检查
echo -n "[Health] "
HEALTH=$(curl -sf "${MGMT_URL}/actuator/health" | jq -r '.status')
if [ "$HEALTH" = "UP" ]; then
    echo "OK: $HEALTH"
else
    echo "ALERT: $HEALTH"
fi

# 2. 健康评分
echo -n "[Score]  "
SCORE=$(curl -sf "${MGMT_URL}/actuator/taskflow" | jq -r '.healthScore')
if [ "$SCORE" -ge 70 ] 2>/dev/null; then
    echo "OK: $SCORE"
else
    echo "ALERT: $SCORE"
fi

# 3. 告警检查
echo -n "[Alerts] "
ALERTS=$(curl -sf "${MGMT_URL}/api/performance/alerts" | jq -r 'length // 0')
if [ "$ALERTS" -eq 0 ] 2>/dev/null; then
    echo "OK: No alerts"
else
    echo "WARNING: $ALERTS active alerts"
fi

# 4. 错误率
echo -n "[Errors] "
ERROR_RATE=$(curl -sf "${MGMT_URL}/actuator/taskflow" | jq -r '.stats.errorRate')
echo "Error rate: $ERROR_RATE"

echo "=== 巡检完成 ==="
```

---

## 6. 故障排查

### 6.1 常见问题与解决方案

#### 问题 1：Actuator 端点返回 404

**现象**：`GET /actuator/taskflow` 返回 404

**排查步骤**：

```bash
# 1. 检查端点是否启用
curl -s http://localhost:19091/actuator | jq '.links'

# 2. 检查配置
# application.yml 中确认:
# tfi.actuator.enabled: true
# management.endpoints.web.exposure.include 包含 taskflow

# 3. 检查 Bean 是否加载
curl -s http://localhost:19091/actuator/beans | jq '.contexts[].beans | keys[] | select(contains("Tfi"))'

# 4. 检查日志
grep "SecureTfiEndpoint" app.log
grep "ConditionalOnProperty" app.log
```

**解决方案**：
- 确认 `tfi.actuator.enabled=true`
- 确认 `management.endpoints.web.exposure.include` 包含 `taskflow`
- 确认 `tfi-ops-spring` 依赖已正确引入

#### 问题 2：健康评分持续下降

**现象**：healthScore 从 90+ 持续下降到 70 以下

**排查步骤**：

```bash
# 1. 查看健康详情
curl -s http://localhost:19091/actuator/health/tfi | jq .

# 2. 检查各维度
# memory.increment.mb → 是否有内存泄漏
# cpu.usage.percent → CPU 是否过载
# cache.hit.ratio → 缓存是否有效
# error.rate → 错误率是否上升

# 3. 查看性能历史
curl -s http://localhost:19091/api/performance/history/all | jq .

# 4. 检查 GC 日志
jstat -gcutil $(pgrep -f 'spring-boot') 1000 10
```

**解决方案**：
- 内存增长 → 检查 TFI 对象追踪数量，调用 `clearAllTracking()`
- CPU 过载 → 减少变更追踪频率，增加采样
- 缓存命中低 → 调整 `max-size` 和 `default-ttl`
- 错误率高 → 检查业务代码中 TFI 使用方式

#### 问题 3：缓存自动降级

**现象**：`StoreAutoDegrader.isDegraded = true`

**排查步骤**：

```bash
# 1. 查看缓存统计
curl -s http://localhost:19091/tfi/metrics/summary | jq .

# 2. 分析命中率
# hit_rate < 0.2 → 缓存不匹配工作负载
# evictions > 10000 → 缓存太小

# 3. 查看降级状态
curl -s http://localhost:19091/api/performance | jq '.cache'
```

**解决方案**：
- 命中率低 → 增加 `tfi.store.caffeine.max-size`
- 命中率低 → 调整 `tfi.store.caffeine.default-ttl`（数据变化快则缩短）
- 淘汰过多 → 增加 `tfi.store.caffeine.max-size`
- 工作集不匹配 → 考虑从 LRU 切换到 FIFO 或分层缓存

#### 问题 4：性能基准测试失败

**现象**：基准测试结果异常或超时

**排查步骤**：

```bash
# 1. 检查基准状态
curl -s http://localhost:19091/tfi/benchmark/status | jq .

# 2. 查看报告
curl -s http://localhost:19091/tfi/benchmark/report?format=text

# 3. 检查依赖组件
# BenchmarkRunner 需要 ChangeTracker, PathMatcherCacheInterface 等
# 确认核心模块正常

# 4. 调整参数
# tfi.performance.warmup-iterations: 500  (减少预热)
# tfi.performance.measurement-iterations: 5000  (减少测量)
```

#### 问题 5：告警风暴

**现象**：短时间内大量告警产生

**处理步骤**：

```bash
# 1. 清除非关键告警
curl -X DELETE http://localhost:19091/api/performance/alerts/all

# 2. 临时放宽 SLA
curl -X POST http://localhost:19091/api/performance/sla/snapshot \
  -H "Content-Type: application/json" \
  -d '{"maxLatencyMs": 100, "minThroughput": 100, "maxErrorRate": 0.1}'

# 3. 分析根因
# 检查是否有突发流量、GC 停顿、下游服务异常

# 4. 恢复 SLA
# 根因解决后恢复正常 SLA 阈值
```

### 6.2 日志分析

#### TFI 关键日志模式

```bash
# 查看 TFI 相关日志
grep -E "tfi|taskflow|TFI" app.log | tail -50

# 查看错误日志
grep -E "ERROR.*tfi|WARN.*tfi" app.log | tail -20

# 查看性能日志
grep "PerformanceMonitor" app.log | tail -20

# 查看缓存日志
grep "CaffeineStore\|StoreAutoDegrader" app.log | tail -20
```

#### 关键日志关键字

| 关键字 | 含义 | 处理 |
|--------|------|------|
| `Cache degraded` | 缓存已降级 | 检查缓存配置 |
| `Cache recovered` | 缓存已恢复 | 正常，确认恢复 |
| `SLA violation` | SLA 违反 | 检查性能 |
| `Health score below threshold` | 健康评分低 | 排查各维度 |
| `Context leak detected` | 上下文泄漏 | 检查 TFI.clear() 调用 |

---

## 7. 性能调优

### 7.1 调优参数一览

#### 缓存调优

| 参数 | 调大效果 | 调小效果 | 建议值 |
|------|---------|---------|--------|
| `max-size` | 命中率↑ 内存↑ | 命中率↓ 内存↓ | 工作集大小 × 1.5 |
| `default-ttl` | 命中率↑ 数据陈旧↑ | 新鲜度↑ 命中率↓ | 数据变化周期 × 2 |
| `idle-timeout` | 冷数据保留久 | 快速释放 | TTL 的 50% |

#### 性能监控调优

| 参数 | 调大效果 | 调小效果 | 建议值 |
|------|---------|---------|--------|
| `interval-ms` | CPU 开销↓ 精度↓ | 精度↑ CPU↑ | 生产 10s，开发 5s |
| `history-size` | 趋势更长 内存↑ | 内存↓ 趋势短 | 200（约 30 分钟） |

### 7.2 调优流程

```
1. 收集基线数据（1 天）
   └── GET /api/performance/history/all
2. 识别瓶颈
   └── GET /api/performance/report/full
3. 调整参数
   └── 修改 application.yml + 重启
4. 观察效果（1 天）
   └── 对比基线
5. 确认或回滚
```

### 7.3 调优案例

#### 案例 1：缓存命中率低（从 60% 提升到 95%）

```yaml
# 调优前
tfi:
  store:
    caffeine:
      max-size: 1000       # 太小
      default-ttl: 10m     # 太短

# 调优后
tfi:
  store:
    caffeine:
      max-size: 10000      # 增大到工作集的 1.5 倍
      default-ttl: 60m     # 延长 TTL
      idle-timeout: 30m    # 空闲超时
```

#### 案例 2：Actuator 端点响应慢

```yaml
# 原因：每次请求都重新计算
# 解决：利用内置 5s 缓存（已内建），确保不被 Nginx 缓存覆盖

# 如果仍然慢，检查：
# 1. TFI 追踪的对象数量是否过多
# 2. SafeContextManager 是否有大量上下文
# 3. ChangeTracker.getAllChanges() 是否耗时
```

---

## 8. 缓存运维

### 8.1 缓存策略选择指南

| 场景 | 推荐策略 | 配置 |
|------|---------|------|
| 通用热点数据 | LRU | `tfi.store.enabled=true` |
| 时序/日志数据 | FIFO | `tfi.store.fifo.enabled=true` |
| 高频 + 长尾 | Tiered | `tfi.store.caffeine.tiered.enabled=true` |
| 需要加载器 | Instrumented | 编程配置 |

### 8.2 缓存监控

```bash
# 查看缓存统计
curl -s http://localhost:19091/tfi/metrics/summary | jq '.cache'

# 检查降级状态
curl -s http://localhost:19091/api/performance | jq '.cacheStatus'
```

### 8.3 缓存清理

```bash
# 清理过期会话（间接清理缓存）
curl -X POST "http://localhost:19091/actuator/tfi-advanced/cleanup?maxAgeMillis=3600000"

# 如需完全清理缓存，需要重启应用
# 或通过 JMX（如果暴露）
```

### 8.4 缓存降级处理

当缓存自动降级时：

1. **评估影响**：降级意味着缓存效果差，但不影响功能
2. **分析原因**：命中率低（数据不匹配）还是淘汰多（容量不足）
3. **调整配置**：增大 max-size 或调整 TTL
4. **观察恢复**：缓存恢复需要连续 5 次正常检查

---

## 9. 安全运维

### 9.1 安全加固清单

| # | 加固项 | 操作 | 优先级 |
|---|--------|------|--------|
| 1 | 管理端口独立 | `management.server.port=19091` | P0 |
| 2 | 端点鉴权 | Spring Security + Actuator 安全 | P0 |
| 3 | 网关限流 | Nginx/网关对 Actuator 限流 | P0 |
| 4 | 数据脱敏 | `tfi.security.enable-data-masking=true` | P0 |
| 5 | 最小暴露 | 仅暴露必要端点 | P1 |
| 6 | HTTPS | 管理端口启用 TLS | P1 |
| 7 | IP 白名单 | 仅允许内网 IP 访问管理端口 | P1 |

### 9.2 Spring Security 集成示例

```java
@Configuration
@EnableWebSecurity
public class ActuatorSecurityConfig {

    @Bean
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) 
            throws Exception {
        return http
            .securityMatcher("/actuator/**", "/tfi/**", "/api/performance/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("OPS")
                .requestMatchers("/tfi/benchmark/**").hasRole("ADMIN")
                .requestMatchers("/api/performance/**").hasRole("OPS")
            )
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
```

### 9.3 Nginx 配置示例

```nginx
# 管理端口反向代理
upstream tfi_mgmt {
    server 127.0.0.1:19091;
}

server {
    listen 443 ssl;
    server_name ops.internal.example.com;

    # 仅允许内网
    allow 10.0.0.0/8;
    allow 172.16.0.0/12;
    deny all;

    # Actuator 端点
    location /actuator/ {
        proxy_pass http://tfi_mgmt;
        
        # 限流
        limit_req zone=actuator burst=10 nodelay;
        
        # 超时
        proxy_read_timeout 10s;
    }

    # 拒绝基准测试端点（生产环境）
    location /tfi/benchmark/ {
        return 403;
    }
}
```

---

## 10. 备份与恢复

### 10.1 需要备份的内容

| 项目 | 备份方式 | 频率 | 保留期 |
|------|---------|------|--------|
| `application.yml` 配置 | Git/配置中心 | 每次变更 | 永久 |
| SLA 配置 | 导出 API 结果 | 每周 | 3 个月 |
| 基准测试报告 | API 导出 JSON | 每次运行 | 6 个月 |
| 性能历史数据 | Prometheus TSDB | 自动 | 15 天 |

### 10.2 配置备份脚本

```bash
#!/bin/bash
# tfi-backup.sh - TFI 配置和数据备份

BACKUP_DIR="/backup/tfi/$(date +%Y%m%d)"
mkdir -p "$BACKUP_DIR"

# 备份配置
cp application-prod.yml "$BACKUP_DIR/"

# 备份基准报告
curl -sf http://localhost:19091/tfi/benchmark/report?format=json \
  > "$BACKUP_DIR/benchmark-report.json" 2>/dev/null

# 备份性能快照
curl -sf http://localhost:19091/api/performance/report/full \
  > "$BACKUP_DIR/performance-report.json" 2>/dev/null

# 备份指标摘要
curl -sf http://localhost:19091/tfi/metrics/summary \
  > "$BACKUP_DIR/metrics-summary.json" 2>/dev/null

echo "Backup completed: $BACKUP_DIR"
```

---

## 11. 升级与回滚

### 11.1 升级步骤

```
1. 预检查
   ├── 确认新版本兼容性（Java/Spring Boot/依赖版本）
   ├── 阅读 CHANGELOG / 破坏性变更
   └── 在预发环境验证

2. 备份
   ├── 备份当前 JAR
   ├── 备份配置文件
   └── 记录当前性能基线

3. 灰度部署
   ├── 选择 1 个节点升级
   ├── 观察 15 分钟
   ├── 检查健康评分、告警、错误率
   └── 运行基准测试对比

4. 全量部署
   ├── 逐节点滚动升级
   ├── 每个节点等待健康检查通过
   └── 全部完成后整体验证

5. 后验证
   ├── 全部端点功能验证
   ├── 性能基准对比
   └── 持续观察 1 天
```

### 11.2 回滚步骤

```
1. 判断回滚条件
   ├── healthScore < 50 持续 5 分钟
   ├── 错误率 > 5%
   ├── 核心端点不可用
   └── 性能回归 > 50%

2. 执行回滚
   ├── 停止当前版本
   ├── 部署备份的旧版本 JAR
   ├── 恢复旧版本配置
   └── 启动并验证

3. 回滚验证
   ├── 健康检查通过
   ├── 端点功能正常
   └── 性能恢复正常

4. 问题分析
   └── 收集新版本日志和监控数据进行分析
```

### 11.3 版本兼容性矩阵

| tfi-ops-spring | tfi-flow-core | Spring Boot | Java |
|---------------|---------------|-------------|------|
| 3.0.0 | 3.0.0 | 3.5.x | 21+ |
| 2.x.x | 2.x.x | 3.x.x | 17+ |

---

## 12. Runbook

### RB-001: TFI 健康评分骤降

```
触发条件: healthScore 从 >80 降至 <60 within 5 minutes

1. [立即] 检查健康详情
   curl -s /actuator/health/tfi | jq .

2. [判断] 哪个维度异常
   - memory.increment.mb > 10 → 执行 RB-002
   - cpu.usage.percent > 0.1 → 检查进程 CPU
   - cache.hit.ratio < 95% → 执行 RB-003
   - error.rate > 1% → 执行 RB-004

3. [恢复] 根据子 Runbook 操作

4. [验证] healthScore 恢复至 >70

5. [记录] 事件报告
```

### RB-002: 内存异常增长

```
触发条件: memory.increment.mb > 10 或 heap_used > 80%

1. [诊断] JVM 内存分析
   jstat -gcutil <pid> 1000 10
   jmap -histo <pid> | head -30

2. [判断]
   - TFI 对象追踪过多 → 调用清理 API
     POST /actuator/tfi-advanced/cleanup
   - GC 无法回收 → 可能存在泄漏

3. [临时处理]
   - 增加 JVM 堆大小: -Xmx 增大 50%
   - 或重启应用（最后手段）

4. [根因分析]
   - 检查 TFI 追踪对象数量
   - 检查是否忘记调用 TFI.clear()
   - 检查线程池中是否正确传播上下文
```

### RB-003: 缓存性能下降

```
触发条件: cache.hit.ratio < 50% 持续 15 分钟

1. [检查] 缓存统计
   curl -s /tfi/metrics/summary | jq '.cache'

2. [判断]
   - 命中率低 + 淘汰高 → 缓存太小，增大 max-size
   - 命中率低 + 淘汰低 → 缓存 TTL 过短，增大 TTL
   - 已降级 → StoreAutoDegrader 已保护

3. [调整] 修改配置
   # application.yml
   tfi.store.caffeine.max-size: 20000  # 翻倍
   tfi.store.caffeine.default-ttl: 120m  # 延长

4. [重启] 配置生效需要重启

5. [验证] 观察 15 分钟确认恢复
```

### RB-004: 错误率飙升

```
触发条件: error_rate > 1% 持续 5 分钟

1. [检查] 错误详情
   grep "ERROR.*tfi" app.log | tail -50

2. [判断]
   - NullPointerException → 检查 TfiConfig 注入
   - ClassNotFoundException → 检查依赖版本
   - TimeoutException → 检查下游服务
   - ConcurrentModificationException → 线程安全问题

3. [临时处理]
   - 如果是 TFI 本身问题，可以禁用: tfi.enabled=false
   - TFI 设计为异常安全，不应影响业务

4. [根因修复]
   - 收集完整堆栈
   - 提交 Bug 报告
   - 修复后验证
```

### RB-005: 告警风暴

```
触发条件: > 10 条告警在 1 分钟内

1. [立即] 清除告警
   curl -X DELETE /api/performance/alerts/all

2. [临时] 放宽 SLA
   curl -X POST /api/performance/sla/snapshot \
     -d '{"maxLatencyMs": 100, "minThroughput": 100}'

3. [排查] 根因
   - 突发流量 → 等待恢复
   - GC 停顿 → 检查 GC 配置
   - 下游超时 → 检查下游服务

4. [恢复] 解决后恢复 SLA 阈值

5. [改进] 考虑告警聚合/去重机制
```

---

## 附录 A：端口规划

| 端口 | 用途 | 说明 |
|------|------|------|
| 19090 | 应用业务端口 | 对外服务 |
| 19091 | 管理端口（推荐） | Actuator + 监控 |
| 9090 | Prometheus | 指标拉取 |
| 3000 | Grafana | 可视化 |

## 附录 B：常用命令速查

```bash
# === 健康检查 ===
curl -s localhost:19091/actuator/health | jq .
curl -s localhost:19091/actuator/taskflow | jq .

# === 指标查看 ===
curl -s localhost:19091/tfi/metrics/summary | jq .
curl -s localhost:19091/tfi/metrics/report

# === 性能监控 ===
curl -s localhost:19091/api/performance | jq .
curl -s localhost:19091/api/performance/alerts | jq .

# === 会话管理 ===
curl -s localhost:19091/actuator/tfi-advanced/sessions | jq .
curl -X POST "localhost:19091/actuator/tfi-advanced/cleanup?maxAgeMillis=3600000"

# === 基准测试（非生产） ===
curl -X POST "localhost:19091/tfi/benchmark/run?tag=v3.0.0"
curl -s localhost:19091/tfi/benchmark/report?format=markdown

# === SLA 配置 ===
curl -X POST localhost:19091/api/performance/sla/snapshot \
  -H "Content-Type: application/json" \
  -d '{"maxLatencyMs": 10, "minThroughput": 1000, "maxErrorRate": 0.01}'
```

---

> **文档维护**：本文档由资深运维专家撰写，运维流程变更请同步更新。
