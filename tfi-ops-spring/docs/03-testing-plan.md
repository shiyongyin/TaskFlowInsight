# tfi-ops-spring 测试方案

> **专家角色**：资深测试专家  
> **版本**：v3.0.0  
> **日期**：2026-02-16  
> **模块**：tfi-ops-spring（TaskFlowInsight 运维观测模块）

---

## 目录

1. [测试策略概述](#1-测试策略概述)
2. [测试范围](#2-测试范围)
3. [白盒测试方案](#3-白盒测试方案)
4. [黑盒测试方案](#4-黑盒测试方案)
5. [功能测试方案](#5-功能测试方案)
6. [性能测试方案](#6-性能测试方案)
7. [安全测试方案](#7-安全测试方案)
8. [测试环境](#8-测试环境)
9. [测试工具链](#9-测试工具链)
10. [测试用例矩阵](#10-测试用例矩阵)
11. [缺陷管理](#11-缺陷管理)
12. [准入准出标准](#12-准入准出标准)

---

## 1. 测试策略概述

### 1.1 测试金字塔

```
           ┌─────────┐
           │  E2E    │  ← 少量：端到端集成（Spring Boot 全栈）
          ─┼─────────┼─
         ┌─┤ 集成测试 ├─┐  ← 中等：Actuator/REST 端点切片测试
        ─┼─┼─────────┼─┼─
       ┌─┤ │ 单元测试 │ ├─┐  ← 大量：每个类的独立单元测试
      ─┴─┴─┴─────────┴─┴─┴─
```

### 1.2 测试策略矩阵

| 测试类型 | 方法 | 目标 | 数量预估 |
|---------|------|------|---------|
| 白盒测试 | 代码覆盖率驱动 | 分支/路径覆盖 ≥ 50% | 200+ 用例 |
| 黑盒测试 | 等价类/边界值 | 功能正确性 | 150+ 用例 |
| 功能测试 | 场景驱动 | 业务场景覆盖 | 80+ 场景 |
| 性能测试 | 基准/压力/稳定性 | SLA 达标 | 30+ 用例 |
| 安全测试 | 渗透/配置审计 | 安全基线通过 | 20+ 用例 |

### 1.3 覆盖率目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 指令覆盖率 | ≥ 50% | JaCoCo INSTRUCTION（门禁标准） |
| 分支覆盖率 | ≥ 40% | JaCoCo BRANCH |
| 方法覆盖率 | ≥ 60% | JaCoCo METHOD |
| 类覆盖率 | 100% | 每个类至少一个测试 |
| 突变覆盖率 | ≥ 30% | PIT 突变测试（可选） |

---

## 2. 测试范围

### 2.1 被测对象清单

| # | 包 | 类 | 复杂度 | 测试优先级 |
|---|-----|-----|--------|-----------|
| 1 | actuator | SecureTfiEndpoint | 高 | P0 |
| 2 | actuator | TfiEndpoint | 中 | P0 |
| 3 | actuator | TfiAdvancedEndpoint | 高 | P0 |
| 4 | actuator | TaskflowContextEndpoint | 低 | P2 |
| 5 | actuator | EndpointPerformanceOptimizer | 中 | P1 |
| 6 | store | Store (interface) | 低 | P2 |
| 7 | store | CaffeineStore | 中 | P0 |
| 8 | store | FifoCaffeineStore | 高 | P0 |
| 9 | store | InstrumentedCaffeineStore | 中 | P1 |
| 10 | store | TieredCaffeineStore | 中 | P1 |
| 11 | store | StoreConfig | 低 | P2 |
| 12 | store | StoreStats | 低 | P2 |
| 13 | store | StoreAutoDegrader | 高 | P0 |
| 14 | metrics | TfiMetricsEndpoint | 中 | P0 |
| 15 | health | TfiHealthIndicator | 高 | P0 |
| 16 | annotation | AnnotationPerformanceMonitor | 高 | P1 |
| 17 | performance | BenchmarkEndpoint | 高 | P1 |
| 18 | performance | BenchmarkRunner | 高 | P1 |
| 19 | performance | BenchmarkReport | 中 | P2 |
| 20 | performance | BenchmarkResult | 低 | P2 |
| 21 | performance.dashboard | PerformanceDashboard | 高 | P1 |
| 22 | performance.monitor | PerformanceMonitor | 高 | P0 |
| 23 | performance.monitor | PerformanceReport | 低 | P2 |
| 24 | performance.monitor | SLAConfig | 中 | P1 |
| 25 | performance.monitor | MetricSnapshot | 低 | P2 |
| 26 | performance.monitor | Alert | 低 | P2 |
| 27 | performance.monitor | AlertLevel | 低 | P2 |
| 28 | performance.monitor | AlertListener | 低 | P2 |

### 2.2 测试排除项

| 排除项 | 原因 |
|--------|------|
| Lombok 生成代码 | 框架保证正确性 |
| `target/` 目录 | 构建产物 |
| Spring Boot 自动装配逻辑 | Spring 框架保证 |

---

## 3. 白盒测试方案

### 3.1 单元测试设计

#### 3.1.1 SecureTfiEndpoint 白盒测试

**测试类**：`SecureTfiEndpointTest`

```java
// 核心测试方法设计
@Test void taskflow_withConfig_returnsFullResponse()
@Test void taskflow_withNullConfig_returnsDisabledResponse()
@Test void taskflow_withCachedResponse_returnsCached()
@Test void taskflow_afterCacheExpiry_regeneratesResponse()
@Test void maskSessionId_shortId_returnsFull()
@Test void maskSessionId_normalId_returnsMasked()
@Test void calculateHealthScore_allHealthy_returns100()
@Test void calculateHealthScore_highMemory_reduceScore()
@Test void calculateHealthScore_lowCacheHit_reduceScore()
@Test void calculateHealthScore_highErrorRate_reduceScore()
@Test void getHealthLevel_above90_returnsExcellent()
@Test void getHealthLevel_between70And90_returnsGood()
@Test void getHealthLevel_below50_returnsCritical()
@Test void recordAccess_maxEntries_cleansUp()
```

**路径覆盖**：

| 路径 | 条件 | 预期结果 |
|------|------|---------|
| P1 | `tfiConfig == null` | 返回 disabled 状态 |
| P2 | `tfiConfig != null && enabled` | 返回完整状态 |
| P3 | 缓存命中（5s 内） | 直接返回缓存 |
| P4 | 缓存过期 | 重新生成 |
| P5 | 所有组件健康 | healthScore ≥ 90 |
| P6 | 内存异常 | healthScore 降低 |
| P7 | 缓存命中率低 | healthScore 降低 |

#### 3.1.2 StoreAutoDegrader 白盒测试

**测试类**：`StoreAutoDegraderTest`

```java
@Test void evaluate_normalState_noDegradation()
@Test void evaluate_lowHitRate_triggersDegradation()
@Test void evaluate_highEvictions_triggersDegradation()
@Test void evaluate_recoveredHitRate_startsRecovery()
@Test void evaluate_consecutiveGoodChecks_recovers()
@Test void evaluate_insufficientGoodChecks_staysDegraded()
@Test void forceDegrade_fromNormal_degraded()
@Test void forceRecover_fromDegraded_recovered()
@Test void reset_clearsAllState()
```

**状态转移覆盖**：

```
                 ┌──────────────────────────────────────┐
                 │                                       │
正常 ──(hitRate<0.2)──► 降级 ──(hitRate≥0.5 连续5次)──► 正常
      ──(evictions>10000)──►      │
                                  └──(hitRate<0.5)──► 保持降级
```

#### 3.1.3 FifoCaffeineStore 白盒测试

**测试类**：`FifoCaffeineStoreTest`

```java
@Test void put_belowCapacity_noEviction()
@Test void put_atCapacity_evictsOldest()
@Test void put_multipleItems_fifoOrder()
@Test void get_existing_returnsValue()
@Test void get_nonExisting_returnsEmpty()
@Test void get_afterEviction_returnsEmpty()
@Test void remove_existing_removesAndUpdatesQueue()
@Test void clear_emptiesAll()
@Test void size_afterOperations_correct()
@Test void getFifoStats_returnsAccurateStats()
@Test void concurrentPuts_threadSafe()
```

#### 3.1.4 PerformanceMonitor 白盒测试

**测试类**：`PerformanceMonitorTest`

```java
@Test void startTimer_autoCloseable_recordsMetrics()
@Test void recordOperation_success_updatesCounters()
@Test void recordOperation_failure_updatesErrorCounters()
@Test void collectMetrics_withOperations_generatesSnapshots()
@Test void checkSLAs_noViolation_noAlerts()
@Test void checkSLAs_latencyViolation_triggersAlert()
@Test void checkSLAs_throughputViolation_triggersAlert()
@Test void checkSLAs_errorRateViolation_triggersAlert()
@Test void systemAlerts_highHeap_triggersCritical()
@Test void systemAlerts_highThreads_triggersWarning()
@Test void getHistory_returnsChronological()
@Test void reset_clearsAllData()
```

#### 3.1.5 TfiHealthIndicator 白盒测试

**测试类**：`TfiHealthIndicatorTest`

```java
@Test void health_allNormal_returnsUp()
@Test void health_highMemory_returnsDown()
@Test void health_highCpu_returnsOutOfService()
@Test void health_lowCacheHit_returnsOutOfService()
@Test void health_highErrorRate_returnsDown()
@Test void health_scoreBelowFifty_returnsDown()
@Test void health_scoreBetween50And70_returnsOutOfService()
@Test void health_detailsIncludeAllDimensions()
```

### 3.2 分支覆盖重点

| 类 | 关键分支 | 说明 |
|----|---------|------|
| SecureTfiEndpoint | null config / enabled / disabled | TfiConfig 为 null 时的降级路径 |
| StoreAutoDegrader | 降级/恢复/保持 | 状态机的所有转移 |
| TfiHealthIndicator | UP/OUT_OF_SERVICE/DOWN | 健康评分阈值边界 |
| FifoCaffeineStore | 容量满/未满 | FIFO 淘汰逻辑 |
| BenchmarkRunner | 组件存在/不存在 | Optional 依赖的两种路径 |

---

## 4. 黑盒测试方案

### 4.1 等价类划分

#### 4.1.1 Actuator 端点等价类

| 输入 | 有效等价类 | 无效等价类 |
|------|-----------|-----------|
| HTTP 方法 | GET | POST/PUT/DELETE（对只读端点） |
| 端点状态 | enabled | disabled（返回 404） |
| TFI 状态 | 启用 | 禁用（返回禁用信息） |
| 配置 | 有 TfiConfig | 无 TfiConfig（null） |

#### 4.1.2 Sessions API 等价类

| 参数 | 有效等价类 | 无效等价类 |
|------|-----------|-----------|
| limit | 正整数（1-100） | 0, 负数, 非数字 |
| sort | "latest", "oldest" | "invalid", null |
| sessionId | 有效 UUID | 无效字符串, 空字符串 |

#### 4.1.3 Benchmark API 等价类

| 参数 | 有效等价类 | 无效等价类 |
|------|-----------|-----------|
| async | true, false | "abc" |
| tag | 有效字符串 | 空字符串 |
| testName | 已知测试名 | 未知测试名 |
| format | json, text, markdown | "xml", null |

#### 4.1.4 缓存存储等价类

| 操作 | 有效等价类 | 无效等价类 |
|------|-----------|-----------|
| put key | 非 null 对象 | null key |
| put value | 非 null 对象 | null value |
| get key | 存在的 key | 不存在的 key |
| maxSize | 1-Long.MAX | 0, 负数 |
| TTL | > 0 Duration | 0, 负数 |

### 4.2 边界值分析

#### 4.2.1 健康评分边界值

| 边界 | 测试值 | 预期结果 |
|------|--------|---------|
| 满分 | healthScore = 100 | EXCELLENT |
| 优秀上界 | healthScore = 90 | EXCELLENT |
| 优秀下界 | healthScore = 89 | GOOD |
| 良好下界 | healthScore = 70 | GOOD |
| 一般下界 | healthScore = 69 | FAIR |
| 较差下界 | healthScore = 50 | FAIR |
| 差的下界 | healthScore = 49 | CRITICAL |
| 最低分 | healthScore = 0 | CRITICAL |

#### 4.2.2 缓存容量边界值

| 边界 | 测试值 | 预期结果 |
|------|--------|---------|
| 空缓存 | size = 0 | get 返回 empty |
| 单元素 | size = 1, put 1 | 无淘汰 |
| 刚好满 | size = maxSize | 无淘汰 |
| 超出一个 | size = maxSize + 1 | 淘汰最旧/最少使用 |
| 大量超出 | size = maxSize * 2 | 持续淘汰至 maxSize |

#### 4.2.3 降级阈值边界值

| 阈值 | 边界值 | 预期结果 |
|------|--------|---------|
| 命中率降级 | 0.2（exactly） | 保持正常 |
| 命中率降级 | 0.199 | 触发降级 |
| 命中率恢复 | 0.5（exactly） | 可恢复（需连续 5 次） |
| 命中率恢复 | 0.499 | 不恢复 |
| 淘汰数降级 | 10000（exactly） | 保持正常 |
| 淘汰数降级 | 10001 | 触发降级 |

#### 4.2.4 SLA 边界值

| SLA 参数 | 边界值 | 预期结果 |
|---------|--------|---------|
| maxLatencyMs | P95 刚好等于阈值 | 不告警 |
| maxLatencyMs | P95 超过阈值 0.001ms | 触发告警 |
| minThroughput | 吞吐量刚好等于阈值 | 不告警 |
| minThroughput | 吞吐量低于阈值 1 ops/s | 触发告警 |
| maxErrorRate | 错误率刚好等于阈值 | 不告警 |
| maxErrorRate | 错误率超过阈值 0.001% | 触发告警 |

### 4.3 Session ID 脱敏测试

| 输入 | 预期输出 | 说明 |
|------|---------|------|
| `"abcdefghij"` | `"abcd***ghij"` | 标准脱敏 |
| `"abcd"` | `"abcd"` | 过短不脱敏 |
| `"ab"` | `"ab"` | 极短不脱敏 |
| `""` | `""` | 空字符串 |
| `null` | N/A | 不应传 null |

---

## 5. 功能测试方案

### 5.1 Actuator 端点功能测试

#### TC-ACT-001: SecureTfiEndpoint 基本功能

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | GET `/actuator/taskflow` | 200, 返回 JSON |
| 2 | 验证 `version` 字段 | 包含版本号 |
| 3 | 验证 `enabled` 字段 | boolean 类型 |
| 4 | 验证 `healthScore` 字段 | 0-100 整数 |
| 5 | 验证 `healthLevel` 字段 | EXCELLENT/GOOD/FAIR/POOR/CRITICAL |
| 6 | 验证 `components` 字段 | 包含 changeTracking 等子字段 |
| 7 | 验证 `stats` 字段 | 包含 activeContexts 等子字段 |
| 8 | 5s 内再次请求 | 响应与步骤 1 相同（缓存） |
| 9 | 5s 后再次请求 | 响应可能更新 |

#### TC-ACT-002: TfiEndpoint 管理功能

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 启用 `tfi.endpoint.basic.enabled=true` | |
| 2 | GET `/actuator/basic-tfi` | 200, 返回信息 |
| 3 | POST `/actuator/basic-tfi` (toggleTracking) | 200, 返回状态 |
| 4 | DELETE `/actuator/basic-tfi` (clearAll) | 200, 已清理 |
| 5 | 关闭 `tfi.endpoint.basic.enabled=false` | |
| 6 | GET `/actuator/basic-tfi` | 404 |

#### TC-ACT-003: TfiAdvancedEndpoint 会话管理

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 创建多个 TFI 会话 | 会话已创建 |
| 2 | GET `/actuator/tfi-advanced/sessions` | 返回会话列表 |
| 3 | GET `/actuator/tfi-advanced/sessions?limit=2` | 最多 2 个会话 |
| 4 | GET `/actuator/tfi-advanced/sessions/{id}` | 返回会话详情 |
| 5 | GET `/actuator/tfi-advanced/sessions/invalid` | 404 |
| 6 | DELETE `/actuator/tfi-advanced/sessions/{id}` | 200, 已清理 |
| 7 | GET `/actuator/tfi-advanced/sessions/{id}` | 404（已删除） |

#### TC-ACT-004: TfiAdvancedEndpoint 变更查询

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 记录变更数据 | 变更已记录 |
| 2 | GET `/actuator/tfi-advanced/changes` | 返回变更列表 |
| 3 | 带 `sessionId` 过滤 | 仅返回该会话变更 |
| 4 | 带 `objectName` 过滤 | 仅返回该对象变更 |
| 5 | 带 `limit=5&offset=0` | 分页正确 |
| 6 | 带 `changeType` 过滤 | 仅返回指定类型 |

### 5.2 缓存存储功能测试

#### TC-STORE-001: CaffeineStore LRU 功能

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 创建 maxSize=3 的 CaffeineStore | 创建成功 |
| 2 | put A, B, C | size = 3 |
| 3 | put D | size = 3, 最少使用的被淘汰 |
| 4 | get A（最早且未访问） | 可能返回 empty |
| 5 | getStats() | hits > 0, misses > 0 |

#### TC-STORE-002: FifoCaffeineStore FIFO 功能

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 创建 maxSize=3 的 FifoCaffeineStore | 创建成功 |
| 2 | 依次 put A, B, C | size = 3 |
| 3 | put D | A 被淘汰（FIFO） |
| 4 | get A | empty |
| 5 | get B, C, D | 均有值 |
| 6 | put E | B 被淘汰（FIFO） |
| 7 | getFifoStats() | queueSize, totalInsertions 正确 |

#### TC-STORE-003: TieredCaffeineStore 分层功能

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 创建分层缓存（L1=2, L2=5） | 创建成功 |
| 2 | put A | L1 和 L2 都有 A |
| 3 | 填满 L1 并淘汰 A | L1 无 A, L2 有 A |
| 4 | get A | 从 L2 获取并提升到 L1 |
| 5 | get A（再次） | 从 L1 获取（已提升） |

#### TC-STORE-004: StoreAutoDegrader 自动降级

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 设置 hitRate=0.3, evictions=100 | 正常状态 |
| 2 | evaluate() | isDegraded = false |
| 3 | 设置 hitRate=0.15 | |
| 4 | evaluate() | isDegraded = true |
| 5 | 设置 hitRate=0.6, evictions=100 | |
| 6 | evaluate() 调用 5 次 | isDegraded = false（恢复） |

### 5.3 性能监控功能测试

#### TC-PERF-001: PerformanceMonitor 基本流程

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | `startTimer("snapshot")` | 返回 Timer |
| 2 | 执行操作 | |
| 3 | `timer.close()` | 自动记录指标 |
| 4 | `collectMetrics()` | 包含 snapshot 指标 |
| 5 | `getReport()` | 报告包含 snapshot |
| 6 | `getHistory()` | 历史包含快照 |

#### TC-PERF-002: SLA 告警功能

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 配置 SLA: snapshot maxLatency=1ms | 配置成功 |
| 2 | 注册 AlertListener | 注册成功 |
| 3 | 记录 snapshot 操作，延迟 5ms | |
| 4 | `collectMetrics()` | 触发 SLA 检查 |
| 5 | AlertListener.onAlert() 被调用 | 收到告警 |
| 6 | 告警内容包含 latency 违反信息 | |

#### TC-PERF-003: 基准测试功能

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | POST `/tfi/benchmark/run?tag=v3.0` | 开始运行 |
| 2 | GET `/tfi/benchmark/status?tag=v3.0` | 运行中/已完成 |
| 3 | GET `/tfi/benchmark/report?format=json` | JSON 报告 |
| 4 | GET `/tfi/benchmark/report?format=markdown` | Markdown 报告 |
| 5 | POST `/tfi/benchmark/run?tag=v3.1` | 再次运行 |
| 6 | GET `/tfi/benchmark/compare?baseline=v3.0&current=v3.1` | 对比报告 |

### 5.4 健康检查功能测试

#### TC-HEALTH-001: 健康指标集成

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | GET `/actuator/health` | 包含 tfi 组件 |
| 2 | 正常运行时 | tfi.status = "UP" |
| 3 | 模拟高内存 | tfi.status 变为 "DOWN" |
| 4 | 恢复正常 | tfi.status 恢复 "UP" |
| 5 | 验证 details | 包含 memory, cpu, cache, errorRate |

### 5.5 指标采集功能测试

#### TC-METRICS-001: 指标 REST API

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | GET `/tfi/metrics/summary` | 返回汇总 |
| 2 | GET `/tfi/metrics/metric/change_tracking` | 返回单项指标 |
| 3 | POST `/tfi/metrics/custom` (记录自定义) | 201 |
| 4 | POST `/tfi/metrics/counter/my_counter/increment` | 计数器 +1 |
| 5 | GET `/tfi/metrics/report` | 文本报告 |
| 6 | DELETE `/tfi/metrics/custom` | 自定义指标已重置 |

---

## 6. 性能测试方案

### 6.1 性能测试类型

| 类型 | 目的 | 时长 | 负载 |
|------|------|------|------|
| 基准测试 | 建立性能基线 | 10 分钟 | 稳定负载 |
| 负载测试 | 验证 SLA | 30 分钟 | 目标吞吐量 |
| 压力测试 | 找到极限 | 递增至崩溃 | 递增负载 |
| 耐久测试 | 内存泄漏检测 | 4 小时 | 稳定负载 |
| 并发测试 | 线程安全验证 | 15 分钟 | 高并发 |

### 6.2 性能测试用例

#### PT-001: Actuator 端点响应时间

**目标**：SecureTfiEndpoint 响应 P99 < 100ms

```
测试工具: JMeter / wrk
并发用户: 50
持续时间: 10 分钟
请求路径: GET /actuator/taskflow
预期结果:
  - P50 < 20ms
  - P95 < 50ms
  - P99 < 100ms
  - 错误率 < 0.1%
  - 吞吐量 > 500 req/s
```

#### PT-002: 缓存 CRUD 性能

**目标**：单次操作 < 1ms

```
测试方法: JMH 微基准
操作类型: put, get, remove
数据量: 10,000 条目
并发线程: 4
预期结果:
  - put: < 500ns P99
  - get (hit): < 200ns P99
  - get (miss): < 100ns P99
  - remove: < 300ns P99
```

#### PT-003: FifoCaffeineStore 淘汰性能

**目标**：淘汰操作不阻塞写入

```
测试方法: JMH
缓存大小: 1,000
写入速率: 10,000 ops/s
持续时间: 5 分钟
预期结果:
  - 淘汰延迟 P99 < 1ms
  - 写入吞吐量稳定
  - 内存使用稳定
```

#### PT-004: PerformanceMonitor 采集开销

**目标**：指标采集开销 < 10μs

```
测试方法: JMH
操作: recordOperation()
并发线程: 8
预期结果:
  - recordOperation P99 < 10μs
  - LongAdder 无竞争
  - 内存增长 < 1MB/min
```

#### PT-005: 健康检查性能

**目标**：健康检查 < 50ms

```
测试方法: Spring Boot Test
并发调用: 20
预期结果:
  - health() P99 < 50ms
  - 不影响业务线程
  - 无阻塞调用
```

#### PT-006: 基准测试引擎性能

**目标**：5 类基准在 60s 内完成

```
测试方法: 集成测试
warmup: 1000
measurement: 10000
预期结果:
  - 全部 5 类在 60s 内完成
  - 结果 CV% < 10%
  - 无 OOM
```

### 6.3 耐久测试

#### PT-007: 长时间运行内存稳定性

```
测试时长: 4 小时
负载: 稳定 100 req/s 至 Actuator 端点
监控:
  - JVM 堆内存趋势
  - 非堆内存趋势
  - GC 频率和暂停时间
  - 线程数趋势
预期结果:
  - 堆内存无持续增长
  - 无 GC 异常
  - 线程数稳定
  - 缓存大小不超 maxSize
```

### 6.4 并发测试

#### PT-008: Store 并发安全

```
测试方法: JUnit 5 + ExecutorService
线程数: 16
操作: 混合 put/get/remove (40/40/20)
数据量: 100,000 次操作
预期结果:
  - 无 ConcurrentModificationException
  - 无数据丢失
  - 最终 size 与预期一致
  - StoreStats 计数准确
```

#### PT-009: StoreAutoDegrader 并发安全

```
测试方法: JUnit 5 + 多线程
线程数: 8
操作: 并发 evaluate()
预期结果:
  - 状态转移正确（无中间状态泄漏）
  - AtomicBoolean 一致性
  - consecutiveGoodChecks 准确
```

---

## 7. 安全测试方案

### 7.1 端点安全测试

| ID | 测试项 | 方法 | 预期 |
|----|--------|------|------|
| ST-001 | 只读端点不接受写操作 | POST/PUT/DELETE `/actuator/taskflow` | 405 Method Not Allowed |
| ST-002 | 禁用端点不可访问 | GET `/actuator/basic-tfi`（未启用） | 404 |
| ST-003 | Session ID 脱敏 | GET `/actuator/taskflow` | ID 已脱敏 |
| ST-004 | 无敏感配置泄露 | 检查所有端点响应 | 无密码/密钥 |
| ST-005 | 输入注入防护 | sessionId 含特殊字符 | 安全处理 |
| ST-006 | 大 payload 防护 | PATCH config 含超大 body | 拒绝或限制 |

### 7.2 配置安全审计

| ID | 检查项 | 预期 |
|----|--------|------|
| SA-001 | 默认安全模式 | SecureTfiEndpoint 默认启用 |
| SA-002 | 写操作需显式开启 | TfiEndpoint 默认关闭 |
| SA-003 | 基准测试默认关闭 | BenchmarkEndpoint 默认关闭 |
| SA-004 | 数据脱敏默认开启 | `tfi.security.enable-data-masking=true` |

---

## 8. 测试环境

### 8.1 环境规格

| 环境 | JDK | Spring Boot | 内存 | CPU |
|------|-----|------------|------|-----|
| 单元测试 | 21 | 3.5.5 | 2GB | 2 核 |
| 集成测试 | 21 | 3.5.5 | 4GB | 4 核 |
| 性能测试 | 21 | 3.5.5 | 8GB | 8 核 |

### 8.2 测试数据

| 数据集 | 规模 | 用途 |
|--------|------|------|
| 小数据集 | 10 个会话, 100 条变更 | 单元测试 |
| 中数据集 | 100 个会话, 10,000 条变更 | 集成测试 |
| 大数据集 | 1,000 个会话, 100,000 条变更 | 性能测试 |

---

## 9. 测试工具链

| 工具 | 用途 | 版本 |
|------|------|------|
| JUnit 5 | 单元/集成测试框架 | 5.x (spring-boot-starter-test) |
| Mockito | Mock 框架 | 5.x (spring-boot-starter-test) |
| Spring Boot Test | 切片测试 | 3.5.5 |
| `@WebMvcTest` | Controller 切片测试 | Spring Boot |
| JaCoCo | 覆盖率 | 0.8.12 |
| JMH | 微基准测试 | 1.37 |
| JMeter / wrk | HTTP 性能测试 | 5.6 / latest |
| AssertJ | 流式断言 | 3.x |
| Awaitility | 异步测试 | 4.x |
| ArchUnit | 架构测试 | 1.3.0 |
| SpotBugs | 静态分析 | 4.8.6 |
| Checkstyle | 代码风格 | Google checks |
| PMD | 代码质量 | 3.25.0 |

---

## 10. 测试用例矩阵

### 10.1 按优先级汇总

| 优先级 | 白盒 | 黑盒 | 功能 | 性能 | 安全 | 合计 |
|--------|------|------|------|------|------|------|
| P0 | 45 | 30 | 25 | 5 | 4 | 109 |
| P1 | 35 | 25 | 20 | 5 | 4 | 89 |
| P2 | 20 | 15 | 10 | 3 | 2 | 50 |
| **合计** | **100** | **70** | **55** | **13** | **10** | **248** |

### 10.2 按模块汇总

| 模块 | 白盒 | 黑盒 | 功能 | 性能 | 安全 | 合计 |
|------|------|------|------|------|------|------|
| actuator | 25 | 20 | 15 | 2 | 4 | 66 |
| store | 25 | 15 | 12 | 4 | 0 | 56 |
| metrics | 10 | 8 | 6 | 1 | 1 | 26 |
| health | 10 | 5 | 4 | 1 | 0 | 20 |
| annotation | 8 | 5 | 3 | 1 | 0 | 17 |
| performance | 15 | 10 | 10 | 3 | 3 | 41 |
| performance.monitor | 7 | 7 | 5 | 1 | 2 | 22 |

---

## 11. 缺陷管理

### 11.1 缺陷严重等级

| 等级 | 定义 | 示例 | 响应时间 |
|------|------|------|---------|
| S1-致命 | 系统崩溃/数据丢失 | OOM, 数据损坏 | 4 小时 |
| S2-严重 | 核心功能不可用 | Actuator 端点返回 500 | 8 小时 |
| S3-一般 | 功能缺陷可绕行 | 脱敏不完整 | 2 天 |
| S4-轻微 | UI/格式问题 | 报告格式不对齐 | 1 周 |

### 11.2 已知问题（测试前发现）

| # | 问题 | 严重度 | 位置 |
|---|------|--------|------|
| 1 | `PerformanceMonitor` 日志格式 `{:.2f}` 错误 | S3 | PerformanceMonitor.java |
| 2 | `FifoCaffeineStore` 未使用 `ConfigDefaults` 导入 | S4 | FifoCaffeineStore.java |
| 3 | `TfiAdvancedEndpoint` 暴露完整 Session ID | S3 | TfiAdvancedEndpoint.java |
| 4 | `alertListeners` 非线程安全 | S2 | PerformanceMonitor.java |
| 5 | 版本号 `2.1.0-MVP` 与 POM 不一致 | S3 | TfiAdvancedEndpoint.java |
| 6 | `BenchmarkReport` 使用 `SimpleDateFormat` | S4 | BenchmarkReport.java |

---

## 12. 准入准出标准

### 12.1 准入标准

| # | 条件 | 说明 |
|---|------|------|
| 1 | 代码编译通过 | `mvn clean compile` 无错误 |
| 2 | 静态分析通过 | SpotBugs/Checkstyle/PMD 无新增 High |
| 3 | 依赖完整 | 所有依赖模块可用 |
| 4 | 测试环境就绪 | JDK 21 + Spring Boot 3.5.5 |

### 12.2 准出标准

| # | 条件 | 阈值 |
|---|------|------|
| 1 | 用例执行率 | ≥ 95% |
| 2 | 用例通过率 | ≥ 90% |
| 3 | S1/S2 缺陷 | 0 个未修复 |
| 4 | S3 缺陷 | ≤ 3 个 |
| 5 | 指令覆盖率 | ≥ 50% |
| 6 | 性能 SLA | 全部达标 |
| 7 | 安全测试 | 全部通过 |

---

> **文档维护**：本文档由资深测试专家撰写，测试方案随版本迭代更新。
