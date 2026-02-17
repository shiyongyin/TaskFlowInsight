# tfi-ops-spring 产品需求文档（PRD）

> **专家角色**：资深产品经理  
> **版本**：v3.0.0  
> **日期**：2026-02-16  
> **产品名称**：TaskFlowInsight Operations Module (tfi-ops-spring)

---

## 目录

1. [产品概述](#1-产品概述)
2. [目标用户与场景](#2-目标用户与场景)
3. [产品目标与成功指标](#3-产品目标与成功指标)
4. [功能需求](#4-功能需求)
5. [非功能需求](#5-非功能需求)
6. [用户故事](#6-用户故事)
7. [信息架构](#7-信息架构)
8. [接口规范](#8-接口规范)
9. [配置矩阵](#9-配置矩阵)
10. [竞品分析](#10-竞品分析)
11. [版本规划](#11-版本规划)
12. [风险与应对](#12-风险与应对)

---

## 1. 产品概述

### 1.1 产品定位

`tfi-ops-spring` 是 TaskFlowInsight 生态中的**运维可观测模块**，定位为：

> 为 TFI 核心引擎提供**生产级**的可观测性、性能诊断、健康监控与缓存管理能力，让开发者和运维人员能够实时掌握 TFI 在生产环境中的运行状态。

### 1.2 产品愿景

**"让 TFI 的运行状态像业务流程一样透明可见"**

### 1.3 产品价值主张

| 价值维度 | 描述 |
|---------|------|
| **可观测性** | 开箱即用的 Actuator 端点、Prometheus 指标、健康检查 |
| **性能保障** | 内置基准测试、SLA 配置、自动告警，确保 TFI 不拖慢业务 |
| **运维友好** | 一键开关、安全脱敏、自动降级，降低运维成本 |
| **零侵入** | 全部功能通过 Spring Boot 自动装配，无需修改业务代码 |

---

## 2. 目标用户与场景

### 2.1 用户画像

| 用户角色 | 关注点 | 使用频率 | 典型操作 |
|---------|--------|---------|---------|
| **后端开发** | TFI 集成效果、性能影响、配置调优 | 日常 | 查看端点信息、运行基准测试 |
| **运维工程师** | 系统健康、告警响应、资源占用 | 24/7 | 监控仪表盘、告警管理、缓存统计 |
| **SRE** | SLA 达标、性能趋势、容量规划 | 每周 | SLA 配置、性能报告、历史趋势 |
| **技术负责人** | 技术债务评估、性能基线、版本对比 | 版本发布 | 基准测试对比、健康评分 |
| **QA 工程师** | 性能回归检测、功能验证 | 版本迭代 | 基准测试、功能端点验证 |

### 2.2 核心使用场景

#### 场景 1：开发阶段 — 集成验证

```
开发者引入 TFI → 访问 /actuator/taskflow → 确认 TFI 正常运行
→ 运行基准测试 → 验证性能影响在可接受范围
```

#### 场景 2：生产监控 — 日常巡检

```
运维巡检 → 检查 /actuator/health（含 TFI 健康指标）
→ 查看 /api/performance（性能仪表盘）
→ 确认无告警 → 记录巡检结果
```

#### 场景 3：故障排查 — 性能问题

```
收到告警（延迟升高）→ 查看 /api/performance/alerts
→ 分析 /api/performance/history/{metric}
→ 运行 /tfi/benchmark/run 确认基准性能
→ 调整 SLA 或配置 → 确认恢复
```

#### 场景 4：版本发布 — 性能对比

```
新版本部署 → 运行基准测试（tag=v3.0.0）
→ 对比 /tfi/benchmark/compare?baseline=v2.9.0&current=v3.0.0
→ 确认无性能回归 → 发布通过
```

#### 场景 5：缓存治理 — 容量调优

```
发现缓存命中率低 → 查看 /tfi/metrics/summary
→ StoreAutoDegrader 自动降级
→ 调整 tfi.store.caffeine.max-size
→ 缓存恢复 → 自动升级
```

---

## 3. 产品目标与成功指标

### 3.1 产品目标

| 目标 | 描述 | 衡量标准 |
|------|------|---------|
| **G1: 零配置可用** | 引入依赖即可使用核心端点 | Actuator 端点默认开启 |
| **G2: 生产安全** | 默认安全模式，数据脱敏 | SecureTfiEndpoint 为默认 |
| **G3: 性能无感知** | TFI 运维模块自身开销可忽略 | 端点响应 < 100ms |
| **G4: 告警可配** | 灵活的 SLA 和告警配置 | 支持自定义 SLA 阈值 |
| **G5: 自动降级** | 缓存异常自动降级保护 | 降级/恢复全自动 |

### 3.2 关键成功指标（KPI）

| 指标 | 目标值 | 当前状态 |
|------|--------|---------|
| Actuator 端点响应时间 P99 | < 100ms | ✅ 5s 缓存机制保障 |
| 健康检查评分准确率 | > 95% | ⚠️ 需实际验证 |
| 基准测试可重复性（CV%） | < 5% | ⚠️ 需实际验证 |
| 配置开关覆盖率 | 100% | ✅ 所有功能可配置 |
| 安全端点覆盖率 | > 80% | ⚠️ TfiAdvanced 未脱敏 |

---

## 4. 功能需求

### 4.1 功能矩阵

| 功能域 | 功能 | 优先级 | 状态 | 描述 |
|--------|------|--------|------|------|
| **F1: Actuator 端点** | | | | |
| | F1.1 安全概览端点 | P0 | ✅ 已实现 | `/actuator/taskflow` 只读安全概览 |
| | F1.2 基础管理端点 | P1 | ✅ 已实现 | `/actuator/basic-tfi` 信息/切换/清理 |
| | F1.3 高级 REST 端点 | P0 | ✅ 已实现 | `/actuator/tfi-advanced/*` 全功能管理 |
| | F1.4 上下文诊断端点 | P2 | ✅ 已实现 | `/actuator/taskflow-context` 诊断 |
| **F2: 指标采集** | | | | |
| | F2.1 指标汇总 | P0 | ✅ 已实现 | 变更追踪/快照/路径匹配等指标 |
| | F2.2 自定义指标 | P1 | ✅ 已实现 | 用户自定义计数器和指标 |
| | F2.3 Prometheus 导出 | P1 | ✅ 已实现 | 可选 Prometheus Registry |
| | F2.4 指标 REST API | P1 | ✅ 已实现 | `/tfi/metrics/*` |
| **F3: 健康检查** | | | | |
| | F3.1 多维健康评分 | P0 | ✅ 已实现 | 内存/CPU/缓存/错误率加权 |
| | F3.2 Spring Health 集成 | P0 | ✅ 已实现 | UP/OUT_OF_SERVICE/DOWN |
| | F3.3 性能基线 | P1 | ✅ 已实现 | stage.p99<50ms 等基线 |
| **F4: 性能基准** | | | | |
| | F4.1 5 类基准测试 | P1 | ✅ 已实现 | 变更追踪/快照/路径/集合/并发 |
| | F4.2 异步执行 | P1 | ✅ 已实现 | 后台异步运行 |
| | F4.3 版本对比 | P1 | ✅ 已实现 | baseline vs current |
| | F4.4 多格式报告 | P2 | ✅ 已实现 | JSON/Text/Markdown |
| **F5: 性能监控** | | | | |
| | F5.1 实时指标采集 | P0 | ✅ 已实现 | 每操作的延迟/吞吐量/错误率 |
| | F5.2 SLA 配置 | P0 | ✅ 已实现 | 延迟/吞吐量/错误率阈值 |
| | F5.3 告警系统 | P0 | ✅ 已实现 | 4 级告警 + 监听器 |
| | F5.4 历史趋势 | P1 | ✅ 已实现 | 可配历史窗口 |
| | F5.5 性能仪表盘 | P1 | ✅ 已实现 | `/api/performance/*` |
| **F6: 缓存存储** | | | | |
| | F6.1 LRU 缓存 | P1 | ✅ 已实现 | Caffeine LRU |
| | F6.2 FIFO 缓存 | P2 | ✅ 已实现 | 先进先出 |
| | F6.3 分层缓存 | P2 | ✅ 已实现 | L1/L2 两级 |
| | F6.4 带加载缓存 | P2 | ✅ 已实现 | LoadingCache + 异步 |
| | F6.5 自动降级 | P0 | ✅ 已实现 | 命中率/淘汰数驱动 |
| | F6.6 缓存统计 | P1 | ✅ 已实现 | 命中/未命中/淘汰 |

### 4.2 功能详细规格

#### F1.1 安全概览端点

**输入**：HTTP GET `/actuator/taskflow`

**输出**：

```json
{
  "version": "3.0.0-MVP",
  "enabled": true,
  "uptime": "PT2H30M",
  "timestamp": "2026-02-16T10:00:00Z",
  "components": {
    "changeTracking": true,
    "pathCache": true,
    "dataMasking": true,
    "threadContext": true
  },
  "stats": {
    "activeContexts": 5,
    "totalChanges": 1234,
    "activeSessions": 3,
    "errorRate": 0.001
  },
  "healthScore": 92,
  "healthLevel": "EXCELLENT",
  "config": {
    "changeTrackingEnabled": true,
    "leakDetectionEnabled": true,
    "dataMaskingEnabled": true
  }
}
```

**安全约束**：
- 仅 GET 操作（`@ReadOperation`）
- Session ID 脱敏（`abc1***xyz9`）
- 最多 10 个会话摘要
- 5 秒响应缓存
- 访问日志：最多 1000 条，10 分钟过期

#### F5.2 SLA 配置

**输入**：HTTP POST `/api/performance/sla/{operation}`

```json
{
  "maxLatencyMs": 10,
  "minThroughput": 1000,
  "maxErrorRate": 0.01
}
```

**行为**：
- 配置指定操作的 SLA 阈值
- 超过阈值触发对应级别告警
- 告警通过 `AlertListener` 推送

#### F6.5 自动降级

**规则**：
| 条件 | 动作 |
|------|------|
| 缓存命中率 < 20% | 进入降级模式 |
| 淘汰数 > 10,000 | 进入降级模式 |
| 命中率 ≥ 50% 且连续 5 次正常 | 退出降级模式 |

---

## 5. 非功能需求

### 5.1 性能需求

| 需求项 | 指标 | 说明 |
|--------|------|------|
| NFR-P1 | Actuator 端点响应 P99 < 100ms | 5s 缓存机制保障 |
| NFR-P2 | 健康检查耗时 < 50ms | 避免拖慢 /actuator/health |
| NFR-P3 | 注解零采样路径 < 1μs | 不采样时几乎零开销 |
| NFR-P4 | AOP 切面开销 P99 < 10μs | 切面拦截不影响业务 |
| NFR-P5 | SpEL 评估 P99 < 50μs | 表达式解析可控 |

### 5.2 可靠性需求

| 需求项 | 指标 | 说明 |
|--------|------|------|
| NFR-R1 | 端点可用性 > 99.9% | 生产级稳定性 |
| NFR-R2 | 优雅降级 | TFI 核心不可用时运维端点仍可用 |
| NFR-R3 | 零泄漏 | 所有缓存、上下文定时清理 |
| NFR-R4 | 线程安全 | 所有公共 API 线程安全 |

### 5.3 安全需求

| 需求项 | 说明 |
|--------|------|
| NFR-S1 | 默认只读模式（SecureTfiEndpoint） |
| NFR-S2 | Session ID 自动脱敏 |
| NFR-S3 | 敏感配置不外露 |
| NFR-S4 | 写操作需显式启用 |

### 5.4 兼容性需求

| 需求项 | 说明 |
|--------|------|
| NFR-C1 | Java 21+ |
| NFR-C2 | Spring Boot 3.5.x |
| NFR-C3 | Micrometer 1.x |
| NFR-C4 | Caffeine 3.x |

### 5.5 可配置性需求

| 需求项 | 说明 |
|--------|------|
| NFR-CF1 | 所有功能可通过 `application.yml` 开关控制 |
| NFR-CF2 | 缓存策略可运行时切换 |
| NFR-CF3 | SLA 阈值可运行时调整 |
| NFR-CF4 | 支持 Spring Profile 差异化配置 |

---

## 6. 用户故事

### Epic 1: Actuator 监控

| ID | 用户故事 | 验收标准 |
|----|---------|---------|
| US-1.1 | 作为开发者，我希望访问 Actuator 端点查看 TFI 运行状态，以便验证集成是否正确 | GET `/actuator/taskflow` 返回 200，包含 version、enabled、healthScore |
| US-1.2 | 作为运维，我希望 Actuator 端点默认不暴露敏感信息，以保障生产安全 | Session ID 自动脱敏，无密码/密钥暴露 |
| US-1.3 | 作为运维，我希望能查看所有会话的详细信息，以便排查问题 | GET `/actuator/tfi-advanced/sessions` 返回会话列表 |
| US-1.4 | 作为运维，我希望能清理指定会话，以释放资源 | DELETE `/actuator/tfi-advanced/sessions/{id}` 成功清理 |
| US-1.5 | 作为运维，我希望能查看上下文管理器状态，以排查 ThreadLocal 泄漏 | GET `/actuator/taskflow-context` 返回上下文诊断 |

### Epic 2: 性能保障

| ID | 用户故事 | 验收标准 |
|----|---------|---------|
| US-2.1 | 作为开发者，我希望运行基准测试评估 TFI 性能影响，以便做出技术决策 | POST `/tfi/benchmark/run` 返回 5 类基准结果 |
| US-2.2 | 作为 SRE，我希望配置 SLA 阈值并收到违反告警，以保障服务质量 | POST `/api/performance/sla/{op}` 后违反时触发告警 |
| US-2.3 | 作为技术负责人，我希望对比两个版本的性能基准，以检测回归 | GET `/tfi/benchmark/compare` 返回差异百分比 |
| US-2.4 | 作为 SRE，我希望查看性能历史趋势，以做容量规划 | GET `/api/performance/history/{metric}` 返回时间序列 |
| US-2.5 | 作为运维，我希望查看性能仪表盘，以快速了解系统状态 | GET `/api/performance` 返回概览含健康评级 |

### Epic 3: 健康检查

| ID | 用户故事 | 验收标准 |
|----|---------|---------|
| US-3.1 | 作为运维，我希望 TFI 健康状态集成到 Spring Health，以统一监控 | `/actuator/health` 包含 tfi 组件 |
| US-3.2 | 作为运维，我希望健康评分考虑多个维度，以更准确反映状态 | 健康评分包含内存/CPU/缓存/错误率 |
| US-3.3 | 作为运维，我希望健康评分低于阈值时自动标记为 DOWN | 评分 < 50 时 Health 状态为 DOWN |

### Epic 4: 缓存管理

| ID | 用户故事 | 验收标准 |
|----|---------|---------|
| US-4.1 | 作为开发者，我希望选择合适的缓存策略，以优化性能 | 支持 LRU/FIFO/分层/带加载 4 种策略 |
| US-4.2 | 作为运维，我希望缓存在异常时自动降级，以保护系统 | 命中率 < 20% 时自动降级 |
| US-4.3 | 作为运维，我希望缓存恢复正常后自动升级，以恢复性能 | 命中率 ≥ 50% 且连续 5 次正常后恢复 |
| US-4.4 | 作为运维，我希望查看缓存统计，以评估缓存效果 | `/tfi/metrics/summary` 包含缓存命中率 |

### Epic 5: 指标采集

| ID | 用户故事 | 验收标准 |
|----|---------|---------|
| US-5.1 | 作为 SRE，我希望 TFI 指标接入 Prometheus，以统一监控平台 | Prometheus 端点包含 tfi.* 指标 |
| US-5.2 | 作为开发者，我希望记录自定义业务指标，以扩展监控 | POST `/tfi/metrics/custom` 成功记录 |
| US-5.3 | 作为运维，我希望获取指标文本报告，以邮件/钉钉通知 | GET `/tfi/metrics/report` 返回格式化文本 |

---

## 7. 信息架构

### 7.1 端点层次

```
/actuator/
├── health              ← Spring Boot 标准（含 TfiHealthIndicator）
├── taskflow            ← 安全只读概览（默认开启）
├── basic-tfi           ← 基础管理（默认关闭）
├── tfi-advanced/       ← 全功能 REST
│   ├── sessions/
│   │   └── {sessionId}
│   ├── changes
│   ├── config
│   ├── cleanup
│   └── stats
├── taskflow-context    ← 上下文诊断
└── tfi-metrics         ← 指标 Actuator

/tfi/
├── metrics/            ← 指标 REST API
│   ├── summary
│   ├── report
│   ├── metric/{name}
│   ├── custom
│   ├── counter/{name}/increment
│   ├── log
│   └── config
└── benchmark/          ← 基准测试 REST API
    ├── run
    ├── run/{testName}
    ├── status
    ├── report
    ├── compare
    ├── list
    └── clear

/api/
└── performance/        ← 性能仪表盘
    ├── report/{type}
    ├── history/{metric}
    ├── alerts
    ├── benchmark/{type}
    ├── sla/{operation}
    └── alerts/{key}
```

### 7.2 数据模型

```
TfiOpsData
├── ActuatorData
│   ├── version, enabled, uptime
│   ├── components{changeTracking, pathCache, ...}
│   ├── stats{activeContexts, totalChanges, ...}
│   ├── healthScore, healthLevel
│   └── config{changeTrackingEnabled, ...}
├── MetricsData
│   ├── MetricsSummary
│   ├── MetricSnapshot (per metric)
│   └── CustomMetrics
├── HealthData
│   ├── status (UP/DOWN/OUT_OF_SERVICE)
│   ├── healthScore (0-100)
│   └── dimensions{memory, cpu, cache, errorRate}
├── PerformanceData
│   ├── BenchmarkReport
│   │   ├── BenchmarkResult[]
│   │   └── Environment
│   ├── PerformanceReport
│   │   ├── MetricSnapshot[]
│   │   ├── heapUsed, threadCount
│   │   └── alerts[]
│   ├── SLAConfig[]
│   └── AlertHistory[]
└── StoreData
    ├── StoreStats{hits, misses, evictions}
    └── DegradeState{degraded, consecutiveGood}
```

---

## 8. 接口规范

### 8.1 响应格式约定

所有 REST 端点遵循统一格式：

```json
{
  "timestamp": "2026-02-16T10:00:00Z",
  "status": "success",
  "data": { ... }
}
```

错误响应：

```json
{
  "timestamp": "2026-02-16T10:00:00Z",
  "status": "error",
  "error": "Description of the error",
  "code": "ERROR_CODE"
}
```

### 8.2 API 一致性约定

| 约定 | 说明 |
|------|------|
| 日期格式 | ISO-8601 (UTC) |
| 分页 | `limit` + `offset` 参数 |
| 排序 | `sort` 参数（如 `sort=latest`） |
| 空值 | 省略字段而非返回 null |
| 版本 | 通过响应体中 `version` 字段标识 |

---

## 9. 配置矩阵

### 9.1 环境配置建议

| 配置项 | 开发 | 测试 | 预发 | 生产 |
|--------|------|------|------|------|
| `tfi.actuator.enabled` | true | true | true | true |
| `tfi.endpoint.basic.enabled` | true | true | false | false |
| `tfi.security.enable-data-masking` | false | false | true | true |
| `tfi.performance.enabled` | true | true | true | false |
| `tfi.performance.endpoint.enabled` | true | true | false | false |
| `tfi.performance.dashboard.enabled` | true | true | true | true |
| `tfi.store.enabled` | false | true | true | true |
| `tfi.store.caffeine.max-size` | 1000 | 5000 | 10000 | 10000 |

### 9.2 安全配置矩阵

| 端点 | 开发 | 生产 | 建议 |
|------|------|------|------|
| `/actuator/taskflow` | 开放 | 内网/鉴权 | 默认开启，网关层控制 |
| `/actuator/basic-tfi` | 开放 | 关闭 | 生产默认关闭 |
| `/actuator/tfi-advanced` | 开放 | 内网/鉴权 | 建议增加认证 |
| `/tfi/benchmark` | 开放 | 关闭 | 生产默认关闭 |
| `/api/performance` | 开放 | 内网 | 仅内部访问 |

---

## 10. 竞品分析

### 10.1 同类工具对比

| 特性 | tfi-ops-spring | Spring Boot Admin | Micrometer Tracing | Custom Actuator |
|------|---------------|-------------------|--------------------|-----------------| 
| TFI 专属监控 | ✅ 深度集成 | ❌ 通用 | ❌ 通用 | ⚠️ 需开发 |
| 变更追踪指标 | ✅ 内置 | ❌ 无 | ❌ 无 | ⚠️ 需开发 |
| 内置基准测试 | ✅ 5 类 | ❌ 无 | ❌ 无 | ❌ 无 |
| SLA + 告警 | ✅ 内置 | ⚠️ 部分 | ❌ 无 | ⚠️ 需开发 |
| 缓存自动降级 | ✅ 自动 | ❌ 无 | ❌ 无 | ❌ 无 |
| 健康多维评分 | ✅ 4 维 | ⚠️ 简单 | ❌ 无 | ⚠️ 需开发 |
| 零配置启动 | ✅ | ✅ | ⚠️ 需配置 | ❌ 需开发 |
| 数据脱敏 | ✅ 默认 | ❌ | ❌ | ⚠️ 需开发 |

### 10.2 差异化优势

1. **TFI 专属**：深度感知变更追踪、对象快照、路径匹配等 TFI 核心指标
2. **内置基准**：开箱即用的性能基准测试，支持版本对比
3. **自动降级**：缓存自动降级/恢复，无需人工干预
4. **安全默认**：生产安全优先设计，默认只读 + 脱敏

---

## 11. 版本规划

### 11.1 当前版本（v3.0.0）功能覆盖

- [x] Actuator 端点（安全/基础/高级/上下文）
- [x] Micrometer 指标采集
- [x] Prometheus 可选导出
- [x] 多维健康评分
- [x] 5 类基准测试
- [x] SLA + 告警系统
- [x] 4 种缓存策略
- [x] 缓存自动降级
- [x] 性能仪表盘

### 11.2 规划需求（v3.1.0+）

| 优先级 | 需求 | 说明 |
|--------|------|------|
| P0 | 单元测试补充 | 当前模块无测试，目标覆盖率 ≥ 50% |
| P0 | TfiAdvanced 数据脱敏 | 与 SecureTfiEndpoint 安全标准对齐 |
| P1 | Grafana Dashboard 模板 | 提供开箱即用的 Grafana 面板 JSON |
| P1 | 告警通知集成 | 支持 Webhook/钉钉/企微告警推送 |
| P2 | MIXED 淘汰策略实现 | 当前仅有枚举定义 |
| P2 | 端点认证集成 | Spring Security 集成方案 |
| P2 | 基准测试定时执行 | 定时自动运行基准测试 |
| P3 | 前端仪表盘 UI | 提供可视化 Web 界面 |

---

## 12. 风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|---------|
| 端点暴露安全风险 | 高 | 中 | 默认安全模式 + 网关鉴权建议 |
| 基准测试影响业务性能 | 中 | 低 | 默认关闭，异步执行 |
| 缓存 OOM | 高 | 低 | maxSize 限制 + 自动降级 |
| 健康评分误判 | 中 | 中 | 多维加权 + 可调阈值 |
| 告警风暴 | 中 | 低 | SLA 可调 + 告警聚合 |
| 无单元测试 | 高 | 高 | v3.1.0 优先补充测试 |

---

> **文档维护**：本文档由资深产品经理撰写，产品需求变更请更新此文档并通知相关方。
