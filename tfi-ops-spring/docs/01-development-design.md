# tfi-ops-spring 开发设计文档

> **专家角色**：资深 Spring Boot 开发专家  
> **版本**：v3.0.0  
> **日期**：2026-02-16  
> **模块**：tfi-ops-spring（TaskFlowInsight 运维观测模块）

---

## 目录

1. [模块定位与职责](#1-模块定位与职责)
2. [架构设计](#2-架构设计)
3. [包结构与类清单](#3-包结构与类清单)
4. [核心设计详解](#4-核心设计详解)
5. [设计模式应用](#5-设计模式应用)
6. [API 接口清单](#6-api-接口清单)
7. [配置体系](#7-配置体系)
8. [依赖关系](#8-依赖关系)
9. [线程安全设计](#9-线程安全设计)
10. [代码设计评分](#10-代码设计评分)
11. [改进建议](#11-改进建议)

---

## 1. 模块定位与职责

### 1.1 定位

`tfi-ops-spring` 是 TaskFlowInsight 多模块项目的**运维观测层**（Operations & Observability Layer），职责边界清晰：

- **不负责**：业务流程追踪、变更检测、对象快照等核心能力（由 `tfi-flow-core` 和 `tfi-compare` 提供）
- **只负责**：将核心能力以 REST/Actuator 端点、指标采集、健康检查、性能基准、缓存存储等形式暴露给运维和监控体系

### 1.2 核心职责

| 能力域 | 描述 |
|--------|------|
| **Actuator 端点** | 4 个 Spring Boot Actuator 端点，支持只读安全模式 |
| **指标采集** | Micrometer 集成，Prometheus 可选导出，自定义计数器 |
| **健康检查** | Spring Boot HealthIndicator，多维健康评分（内存/CPU/缓存/错误率） |
| **性能基准** | 内置基准测试引擎，5 类基准场景，SLA 配置与告警 |
| **缓存存储** | 4 种 Caffeine 缓存实现（LRU/FIFO/分层/带加载），自动降级 |
| **性能仪表盘** | REST API 性能概览、历史趋势、告警管理 |

---

## 2. 架构设计

### 2.1 模块架构图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        tfi-ops-spring Module                             │
│                                                                          │
│  ┌─────────────────────┐  ┌──────────────┐  ┌─────────────────────────┐ │
│  │      Actuator        │  │    Metrics    │  │        Health           │ │
│  │  ──────────────────  │  │  ──────────  │  │  ───────────────────── │ │
│  │  SecureTfiEndpoint   │  │  TfiMetrics  │  │  TfiHealthIndicator    │ │
│  │  TfiEndpoint         │  │  Endpoint    │  │  (HealthIndicator)     │ │
│  │  TfiAdvancedEndpoint │  │  (REST +     │  │                        │ │
│  │  TaskflowContext     │  │   Actuator)  │  │  多维健康评分:          │ │
│  │  EndpointPerfOpt     │  │              │  │  内存30%+CPU20%        │ │
│  └──────────┬──────────┘  └──────┬───────┘  │  +缓存30%+错误率20%    │ │
│             │                    │           └───────────┬─────────────┘ │
│             │                    │                       │               │
│             └────────────────────┼───────────────────────┘               │
│                                  │                                       │
│                                  ▼                                       │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │              tfi-flow-core (Required) + tfi-compare (Optional)   │   │
│  │  TfiConfig │ ChangeTracker │ TfiMetrics │ SafeContextManager    │   │
│  │  PathMatcherCache │ ObjectSnapshot │ CollectionSummary          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌────────────────────┐  ┌──────────────────────────────────────────┐   │
│  │       Store         │  │             Performance                  │   │
│  │  ────────────────  │  │  ──────────────────────────────────────  │   │
│  │  Store<K,V>  接口  │  │  BenchmarkEndpoint ──► BenchmarkRunner  │   │
│  │  CaffeineStore     │  │  PerformanceDashboard ──► PerfMonitor   │   │
│  │  FifoCaffeineStore │  │                             │            │   │
│  │  TieredCaffeine    │  │                  ┌──────────┼──────────┐ │   │
│  │  Instrumented      │  │                  │ SLAConfig│  Alert   │ │   │
│  │  StoreConfig       │  │                  │ Listener │ Snapshot │ │   │
│  │  StoreAutoDegrader │  │                  └──────────┴──────────┘ │   │
│  └────────────────────┘  └──────────────────────────────────────────┘   │
│                                                                          │
│  ┌────────────────────┐                                                 │
│  │    Annotation Perf  │  Micrometer 计时器:                             │
│  │  ────────────────  │  零采样 < 1μs │ AOP < 10μs │ SpEL < 50μs     │
│  │  AnnotationPerf    │                                                 │
│  │  Monitor           │                                                 │
│  └────────────────────┘                                                 │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.2 分层架构

```
┌──────────────────────────────────────────┐
│  表现层 (REST / Actuator Endpoints)       │  ← HTTP 请求入口
├──────────────────────────────────────────┤
│  服务层 (Monitor / Runner / Dashboard)    │  ← 业务逻辑编排
├──────────────────────────────────────────┤
│  基础设施层 (Store / Metrics / Health)    │  ← 缓存、指标、健康
├──────────────────────────────────────────┤
│  核心依赖层 (tfi-flow-core / tfi-compare) │  ← 外部核心模块
└──────────────────────────────────────────┘
```

---

## 3. 包结构与类清单

### 3.1 包结构

```
com.syy.taskflowinsight
├── actuator/                  # Actuator 端点（5 个类）
├── store/                     # 缓存存储（8 个类）
├── metrics/                   # 指标采集（1 个类 + 内部类）
├── health/                    # 健康检查（1 个类）
├── annotation/                # 注解性能监控（1 个类）
├── performance/               # 性能基准（4 个类）
│   ├── monitor/               # 性能监控与告警（7 个类）
│   └── dashboard/             # 性能仪表盘（1 个类）
```

### 3.2 完整类清单（28 个类）

| # | 类名 | 包 | 类型 | 约 LOC | 职责 |
|---|------|-----|------|--------|------|
| 1 | `SecureTfiEndpoint` | actuator | `@Endpoint` | 435 | 安全只读 Actuator 端点，数据脱敏，健康评分 |
| 2 | `TfiEndpoint` | actuator | `@Endpoint` | 176 | 基础 Actuator 端点：信息/切换/清理 |
| 3 | `TfiAdvancedEndpoint` | actuator | `@RestControllerEndpoint` | 307 | RESTful 会话/变更/配置/清理管理 |
| 4 | `TaskflowContextEndpoint` | actuator | `@Endpoint` | 34 | 上下文管理诊断端点 |
| 5 | `EndpointPerformanceOptimizer` | actuator | `@Component` | 120 | 端点缓存与性能统计 |
| 6 | `Store<K,V>` | store | Interface | 62 | 通用键值存储接口 |
| 7 | `CaffeineStore` | store | `@Component` | 186 | LRU Caffeine 缓存实现 |
| 8 | `FifoCaffeineStore` | store | `@Component` | 267 | FIFO 先进先出缓存实现 |
| 9 | `InstrumentedCaffeineStore` | store | Class | 184 | 带加载器和异步刷新的缓存 |
| 10 | `TieredCaffeineStore` | store | `@Component` | 157 | L1/L2 分层缓存 |
| 11 | `StoreConfig` | store | `@ConfigurationProperties` | 99 | 缓存配置 |
| 12 | `StoreStats` | store | DTO | 47 | 缓存统计数据 |
| 13 | `StoreAutoDegrader` | store | `@Component` | 127 | 基于命中率的自动降级 |
| 14 | `TfiMetricsEndpoint` | metrics | `@RestController` | 235 | 指标 REST API + Actuator 端点 |
| 15 | `TfiHealthIndicator` | health | `@Component` | 174 | Spring Boot 健康指示器 |
| 16 | `AnnotationPerformanceMonitor` | annotation | `@Component` | 307 | 注解/AOP 性能监控 |
| 17 | `BenchmarkEndpoint` | performance | `@RestController` | 286 | 基准测试 REST API |
| 18 | `BenchmarkRunner` | performance | `@Component` | 389 | 基准测试执行器 |
| 19 | `BenchmarkReport` | performance | DTO | 274 | 基准测试报告 |
| 20 | `BenchmarkResult` | performance | DTO | 172 | 单项基准测试结果 |
| 21 | `PerformanceDashboard` | performance.dashboard | `@RestController` | 388 | 性能仪表盘 REST API |
| 22 | `PerformanceMonitor` | performance.monitor | `@Component` | 315 | 实时性能监控与 SLA 检查 |
| 23 | `PerformanceReport` | performance.monitor | DTO | 134 | 性能报告 |
| 24 | `Alert` | performance.monitor | DTO | 35 | 告警数据 |
| 25 | `AlertLevel` | performance.monitor | Enum | 31 | 告警级别：INFO/WARNING/ERROR/CRITICAL |
| 26 | `AlertListener` | performance.monitor | 函数接口 | 18 | 告警回调监听器 |
| 27 | `SLAConfig` | performance.monitor | DTO | 97 | SLA 阈值配置 |
| 28 | `MetricSnapshot` | performance.monitor | DTO | 82 | 指标快照 |

**总计**：28 个 Java 类，约 5,800 行有效代码

---

## 4. 核心设计详解

### 4.1 Actuator 端点设计

#### SecureTfiEndpoint — 安全优先设计

```java
@Component
@Endpoint(id = "taskflow")
@ConditionalOnProperty(name = "tfi.actuator.enabled", havingValue = "true", matchIfMissing = true)
public class SecureTfiEndpoint {

    // 5 秒缓存，避免频繁计算
    private static final long CACHE_TTL = 5000L;

    @ReadOperation
    public Map<String, Object> taskflow() { ... }
}
```

**安全特性**：
- 仅暴露 `@ReadOperation`，不提供写操作
- Session ID 自动脱敏（前 4 + *** + 后 4）
- 最多返回 10 个会话摘要
- 访问日志：最多 1000 条，10 分钟过期
- 内置 5 秒响应缓存

#### TfiAdvancedEndpoint — 全功能 REST API

```java
@Component
@RestControllerEndpoint(id = "tfi-advanced")
public class TfiAdvancedEndpoint {

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String sort) { ... }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(
        @PathVariable String sessionId) { ... }
}
```

### 4.2 缓存存储设计

#### Store 接口 — 策略模式

```java
public interface Store<K, V> {
    void put(K key, V value);
    Optional<V> get(K key);
    void remove(K key);
    void clear();
    long size();
    default boolean containsKey(K key) { return get(key).isPresent(); }
    default StoreStats getStats() { ... }
}
```

#### 四种实现对比

| 实现 | 淘汰策略 | 适用场景 | 核心数据结构 |
|------|----------|----------|-------------|
| `CaffeineStore` | LRU | 通用热点缓存 | Caffeine `Cache` |
| `FifoCaffeineStore` | FIFO | 时序数据缓存 | `ConcurrentLinkedQueue` + `ConcurrentHashMap` |
| `TieredCaffeineStore` | 分层 LRU | 多级缓存 | L1 + L2 `CaffeineStore` |
| `InstrumentedCaffeineStore` | LRU + 加载 | 缓存穿透保护 | Caffeine `LoadingCache` |

#### StoreAutoDegrader — 自动降级

```
正常运行 ──(命中率<20% 或 淘汰>10000)──► 降级模式
    ▲                                        │
    └──(命中率≥50% 且 连续 5 次正常检查)──────┘
```

### 4.3 性能监控设计

#### 三层性能体系

```
┌─────────────────────────────────────────────┐
│  PerformanceDashboard (表现层)               │
│  └── REST API 统一入口                       │
├─────────────────────────────────────────────┤
│  PerformanceMonitor (服务层)                 │
│  └── 指标采集 + SLA 检查 + 告警              │
├─────────────────────────────────────────────┤
│  BenchmarkRunner (执行层)                    │
│  └── 5 类基准测试执行                        │
└─────────────────────────────────────────────┘
```

#### SLA 默认配置

| 操作 | 最大延迟(ms) | 最低吞吐量(ops/s) | 最大错误率 |
|------|-------------|-------------------|-----------|
| snapshot | 10 | 1,000 | 1% |
| change_tracking | 5 | 5,000 | 0.1% |
| path_matching | 1 | 10,000 | 0.01% |

#### 告警级别

| 级别 | 触发条件 |
|------|---------|
| CRITICAL | 堆内存使用 > 90% |
| WARNING | 堆内存使用 > 75%；线程数 > 1000 |
| ERROR | SLA 违反 |
| INFO | 一般性能信息 |

### 4.4 健康评分设计

`TfiHealthIndicator` 采用多维加权评分（0-100 分）：

| 维度 | 权重 | 评分规则 |
|------|------|---------|
| 内存 | 30% | 增量 < 10MB = 满分 |
| CPU | 20% | 使用率 < 0.1% = 满分 |
| 缓存命中率 | 30% | 命中率 ≥ 95% = 满分 |
| 错误率 | 20% | 错误率 ≤ 1% = 满分 |

**评分映射**：≥ 70 = UP，50-69 = OUT_OF_SERVICE，< 50 = DOWN

---

## 5. 设计模式应用

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **策略模式** | `Store` 接口 + 4 种实现 | 可插拔的缓存淘汰策略 |
| **装饰器模式** | `InstrumentedCaffeineStore`、`TieredCaffeineStore` | 在基础缓存上增强功能 |
| **建造者模式** | `StoreConfig`、`BenchmarkResult`、`Alert` 等 | Lombok `@Builder` |
| **观察者模式** | `AlertListener` + `PerformanceMonitor` | 告警事件回调 |
| **门面模式** | `SecureTfiEndpoint`、`PerformanceDashboard` | 统一入口简化子系统访问 |
| **条件装配** | `@ConditionalOnProperty` | Feature Toggle 功能开关 |
| **模板方法** | `Store` 接口默认方法 | `containsKey`、`getStats` 提供默认实现 |
| **RAII/AutoCloseable** | `PerformanceMonitor.Timer` | try-with-resources 自动计时 |
| **供应者模式** | `EndpointPerformanceOptimizer` | `Supplier<T>` 延迟计算 + 缓存 |

---

## 6. API 接口清单

### 6.1 Actuator 端点

| 端点 ID | 路径 | HTTP 方法 | 操作 | 条件 |
|---------|------|-----------|------|------|
| `taskflow` | `/actuator/taskflow` | GET | 只读安全概览 | `tfi.actuator.enabled=true`（默认开启） |
| `basic-tfi` | `/actuator/basic-tfi` | GET/POST/DELETE | 信息/切换/清理 | `tfi.endpoint.basic.enabled=true`（默认关闭） |
| `tfi-advanced` | `/actuator/tfi-advanced/*` | GET/DELETE/PATCH/POST | 全功能 REST | 始终开启 |
| `taskflow-context` | `/actuator/taskflow-context` | GET | 上下文诊断 | `taskflow.monitoring.endpoint.enabled=true` |
| `tfi-metrics` | `/actuator/tfi-metrics` | GET/POST | 指标读取/重置 | Actuator + MeterRegistry |

### 6.2 REST 端点

| 基础路径 | HTTP 方法 | 路径 | 功能 |
|---------|-----------|------|------|
| `/tfi/metrics` | GET | `/summary` | 指标摘要 |
| `/tfi/metrics` | GET | `/report` | 文本报告 |
| `/tfi/metrics` | GET | `/metric/{name}` | 单项指标 |
| `/tfi/metrics` | POST | `/custom` | 记录自定义指标 |
| `/tfi/metrics` | POST | `/counter/{name}/increment` | 递增计数器 |
| `/tfi/metrics` | POST | `/log` | 触发指标日志 |
| `/tfi/metrics` | DELETE | `/custom` | 重置自定义指标 |
| `/tfi/benchmark` | POST | `/run` | 运行全部基准 |
| `/tfi/benchmark` | POST | `/run/{testName}` | 运行指定基准 |
| `/tfi/benchmark` | GET | `/status` | 查询状态 |
| `/tfi/benchmark` | GET | `/report` | 获取报告 |
| `/tfi/benchmark` | GET | `/compare` | 对比报告 |
| `/tfi/benchmark` | DELETE | `/clear` | 清理报告 |
| `/api/performance` | GET | `/` | 性能概览 |
| `/api/performance` | GET | `/report/{type}` | 性能报告 |
| `/api/performance` | GET | `/history/{metric}` | 历史趋势 |
| `/api/performance` | GET | `/alerts` | 告警列表 |
| `/api/performance` | POST | `/benchmark/{type}` | 触发基准测试 |
| `/api/performance` | POST | `/sla/{operation}` | 配置 SLA |
| `/api/performance` | DELETE | `/alerts/{key}` | 清除告警 |

---

## 7. 配置体系

### 7.1 核心配置项

| 配置键 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `tfi.actuator.enabled` | `true` | Boolean | SecureTfiEndpoint 开关 |
| `tfi.endpoint.basic.enabled` | `false` | Boolean | TfiEndpoint 开关 |
| `taskflow.monitoring.endpoint.enabled` | `true` | Boolean | TaskflowContext 端点开关 |
| `tfi.metrics.enabled` | `true` | Boolean | 指标采集开关 |
| `tfi.metrics.tags` | `{}` | Map | 额外指标标签 |
| `tfi.security.enable-data-masking` | `true` | Boolean | 数据脱敏开关 |

### 7.2 缓存配置（`tfi.store.caffeine.*`）

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `tfi.store.caffeine.max-size` | `10000` | 最大条目数 |
| `tfi.store.caffeine.default-ttl` | `60m` | 写入后过期时间 |
| `tfi.store.caffeine.idle-timeout` | `30m` | 空闲过期时间 |
| `tfi.store.caffeine.refresh-after-write` | - | 异步刷新间隔 |
| `tfi.store.caffeine.use-soft-values` | `false` | 软引用 |
| `tfi.store.caffeine.log-evictions` | `false` | 记录淘汰日志 |
| `tfi.store.caffeine.record-stats` | `true` | 启用统计 |
| `tfi.store.caffeine.eviction-strategy` | `LRU` | 淘汰策略：LRU/FIFO/MIXED |

### 7.3 缓存开关

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `tfi.store.enabled` | `false` | CaffeineStore 开关 |
| `tfi.store.fifo.enabled` | `false` | FifoCaffeineStore 开关 |
| `tfi.store.caffeine.tiered.enabled` | `false` | TieredCaffeineStore 开关 |

### 7.4 缓存降级配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `tfi.store.degrade.hit-rate-threshold` | `0.2` | 降级命中率阈值 |
| `tfi.store.degrade.max-evictions` | `10000` | 降级淘汰数阈值 |
| `tfi.store.degrade.recovery-threshold` | `0.5` | 恢复命中率阈值 |
| `tfi.store.degrade.recovery-count` | `5` | 恢复所需连续正常次数 |

### 7.5 性能配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `tfi.performance.enabled` | `false` | BenchmarkRunner 开关 |
| `tfi.performance.endpoint.enabled` | `false` | BenchmarkEndpoint 开关 |
| `tfi.performance.dashboard.enabled` | `true` | PerformanceDashboard 开关 |
| `tfi.performance.warmup-iterations` | `1000` | 预热迭代次数 |
| `tfi.performance.measurement-iterations` | `10000` | 测量迭代次数 |
| `tfi.performance.thread-count` | `4` | 并发线程数 |
| `tfi.performance.monitor.enabled` | `true` | PerformanceMonitor 开关 |
| `tfi.performance.monitor.interval-ms` | `5000` | 监控间隔(ms) |
| `tfi.performance.monitor.history-size` | `100` | 历史记录大小 |

---

## 8. 依赖关系

### 8.1 Maven 依赖

| 依赖 | 范围 | 用途 |
|------|------|------|
| `tfi-flow-core` | compile | 核心 TFI API |
| `tfi-compare` | optional | 比较功能 |
| `spring-boot-starter-actuator` | compile | Actuator 端点 |
| `spring-boot-starter-web` | compile | REST 控制器 |
| `micrometer-core` | compile | 指标采集 |
| `micrometer-registry-prometheus` | optional | Prometheus 导出 |
| `caffeine` (3.1.8) | compile | 缓存 |
| `lombok` | provided | 代码生成 |
| `spring-boot-starter-test` | test | 测试框架 |

### 8.2 模块依赖图

```
tfi-ops-spring
├── tfi-flow-core (必须)
│   ├── TfiConfig, TfiFlow, ThreadContext
│   ├── ChangeTracker, SessionAwareChangeTracker
│   ├── SafeContextManager, ZeroLeakThreadLocalManager
│   ├── TfiMetrics, MetricsLogger
│   ├── PathMatcherCacheInterface, ObjectSnapshot
│   └── CollectionSummary, ChangeRecord
├── tfi-compare (可选)
├── Spring Boot Actuator
├── Spring Web
├── Micrometer Core
├── Micrometer Prometheus (可选)
└── Caffeine Cache
```

---

## 9. 线程安全设计

### 9.1 并发机制清单

| 组件 | 并发机制 | 说明 |
|------|---------|------|
| `CaffeineStore` | Caffeine `Cache` | 天然线程安全 |
| `FifoCaffeineStore` | `ConcurrentLinkedQueue` + `ConcurrentHashMap` + `AtomicLong` | 无锁 FIFO |
| `TieredCaffeineStore` | 双层 `CaffeineStore` | 各层独立线程安全 |
| `StoreAutoDegrader` | `AtomicBoolean` + `AtomicLong` | CAS 操作 |
| `SecureTfiEndpoint` | `ConcurrentHashMap` | 缓存和访问日志 |
| `BenchmarkEndpoint` | `ConcurrentHashMap` + `CompletableFuture` | 异步基准执行 |
| `PerformanceMonitor` | `ConcurrentHashMap` + `LongAdder` | 高并发计数器 |
| `AnnotationPerformanceMonitor` | `ConcurrentHashMap` | 方法级统计 |
| `PerformanceDashboard` | `Collections.synchronizedList` | 告警列表 |

### 9.2 潜在风险

| 风险 | 位置 | 严重程度 | 说明 |
|------|------|---------|------|
| AlertListener 列表 | `PerformanceMonitor.alertListeners` | 中 | `ArrayList` 未同步，并发注册可能出问题 |
| FifoCaffeineStore 竞态 | `enforceFifoEviction()` | 低 | put 和 eviction 间存在微小窗口 |

---

## 10. 代码设计评分

> **评分方法论**：从架构设计、代码质量、可维护性、安全性、性能、可测试性六个维度进行百分制评分，加权汇总。

### 10.1 评分明细

| 维度 | 权重 | 得分 | 说明 |
|------|------|------|------|
| **架构设计** | 25% | **85** | 分层清晰，职责分离好；Feature Toggle 设计合理；Store 策略模式灵活 |
| **代码质量** | 20% | **78** | 整体代码规范；存在少量死代码和未使用导入；部分日志格式有误 |
| **可维护性** | 15% | **80** | 包结构清晰；配置体系完善；但部分内部类嵌套较深 |
| **安全性** | 15% | **82** | SecureTfiEndpoint 安全设计优秀；数据脱敏良好；但 TfiAdvanced 暴露完整 Session ID |
| **性能** | 15% | **88** | 缓存机制完善；自动降级优秀；SLA 和告警体系完整 |
| **可测试性** | 10% | **72** | `@ConditionalOnProperty` 便于测试；但当前模块无测试代码 |

### 10.2 总评分

$$\text{总分} = 85 \times 0.25 + 78 \times 0.20 + 80 \times 0.15 + 82 \times 0.15 + 88 \times 0.15 + 72 \times 0.10$$

$$= 21.25 + 15.60 + 12.00 + 12.30 + 13.20 + 7.20 = \textbf{81.55 分}$$

### 10.3 评分等级

| 等级 | 分数区间 | 当前评分 |
|------|---------|---------|
| A+ | 95-100 | |
| A | 90-94 | |
| B+ | 85-89 | |
| **B** | **80-84** | **81.55 ★** |
| B- | 75-79 | |
| C | 60-74 | |
| D | < 60 | |

**当前等级：B（良好）**— 架构设计成熟，性能考量充分，存在一些代码质量和测试覆盖的改进空间。

---

## 11. 改进建议

### 11.1 高优先级

| # | 类别 | 问题 | 建议                                        |
|---|------|------|-------------------------------------------|
| 1 | 代码质量 | `FifoCaffeineStore` 未使用 `ConfigDefaults` 导入 | 移除未使用导入                                   |
| 2 | 代码质量 | `PerformanceMonitor` 使用 `{:.2f}`（Python 风格）日志格式 | 改为 `%.2f` 或 SLF4J 占位符 `{}`                |
| 3 | 安全性 | `TfiAdvancedEndpoint` 暴露完整 Session ID | 增加 Session ID 脱敏，与 SecureTfiEndpoint 保持一致 |
| 4 | 一致性 | `TfiAdvancedEndpoint` 版本号 `2.1.0-MVP` 与父 POM `3.0.0` 不一致 | 统一版本号管理                                   |
| 5 | 测试 | 模块内无单元测试 | 为核心组件添加单元测试（目标覆盖率 ≥ 85%）                  |

### 11.2 中优先级

| # | 类别 | 问题 | 建议 |
|---|------|------|------|
| 6 | 死代码 | `SecureTfiEndpoint` 中 `generateOverview` 等私有方法未被调用 | 移除或重构为可测试的公共服务 |
| 7 | 线程安全 | `PerformanceMonitor.alertListeners` 使用普通 `ArrayList` | 改为 `CopyOnWriteArrayList` |
| 8 | 空指针 | `BenchmarkRunner.benchmarkPathMatching()` 的 `Optional` 使用不严谨 | 统一使用 `isPresent()` + `get()` 模式 |
| 9 | 日期处理 | `BenchmarkReport` 使用 `SimpleDateFormat` | 改为 `DateTimeFormatter`（线程安全） |
| 10 | 配置 | `TfiAdvancedEndpoint` 无 `@ConditionalOnProperty` 控制 | 增加开关配置 |

### 11.3 低优先级

| # | 类别 | 建议 |
|---|------|------|
| 11 | 文档 | 为所有公共 API 方法补充 Javadoc |
| 12 | 监控 | `PerformanceMonitor.collectMetrics()` 增加定时调度 |
| 13 | 扩展 | 实现 `MIXED` 淘汰策略（当前仅有枚举定义） |

---

## 附录 A：关键类交互时序图

```
客户端 → SecureTfiEndpoint.taskflow()
    │
    ├─→ 检查缓存（ConcurrentHashMap, TTL=5s）
    │   ├─ 命中 → 直接返回
    │   └─ 未命中 ↓
    │
    ├─→ TfiConfig.enabled()
    ├─→ ChangeTracker.getAllChanges()
    ├─→ SafeContextManager.getMetrics()
    ├─→ PathMatcherCacheInterface.getStats()
    ├─→ calculateHealthScore()
    │   ├─→ TfiMetrics.getCacheHitRatio()
    │   ├─→ TfiMetrics.getErrorRate()
    │   └─→ 加权计算
    ├─→ maskSessionId() (脱敏)
    └─→ 缓存结果 + 返回
```

---

> **文档维护**：本文档由资深 Spring Boot 开发专家撰写，如有架构变更请同步更新。
