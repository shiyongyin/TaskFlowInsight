# TaskFlowInsight 整体项目 — 开发设计文档

> **作者**: 资深开发专家（Spring Boot 领域）  
> **日期**: 2026-02-16  
> **版本**: v1.0  
> **分支**: feature/v4.0.0-routing-refactor  
> **范围**: 全项目（tfi-flow-core / tfi-compare / tfi-ops-spring / tfi-flow-spring-starter / tfi-all）

---

## 1. 架构总览

### 1.1 系统定位

TaskFlowInsight 是一个 **非侵入式、零泄漏** 的业务流程追踪与变更检测库：

- **开发调试工具**: 自动生成业务流程树，快速定位执行路径
- **变更审计引擎**: 深度对象比对，支持 Entity/ValueObject 语义
- **运行时观测**: Spring Actuator 集成，Prometheus 指标导出

### 1.2 分层架构

```
┌─────────────────────────────────────────────────┐
│                   用户层                          │
│         TFI Facade API / @TfiTask 注解           │
├─────────────────────────────────────────────────┤
│                   能力层                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Flow     │  │ Compare  │  │ Ops      │      │
│  │ Engine   │  │ Engine   │  │ Monitor  │      │
│  └──────────┘  └──────────┘  └──────────┘      │
├─────────────────────────────────────────────────┤
│                   基础设施层                       │
│  ThreadLocal · Caffeine · Micrometer · SPI      │
├─────────────────────────────────────────────────┤
│                   Spring 集成层                    │
│  AOP · Actuator · AutoConfiguration · SpEL      │
└─────────────────────────────────────────────────┘
```

### 1.3 模块依赖图

```
tfi-flow-core (外部)
    ↑
    ├── tfi-flow-spring-starter (AOP, @TfiTask)
    ├── tfi-compare (Compare, Diff, Tracking)
    └── tfi-ops-spring (Actuator, Metrics)
            ↑
            └── tfi-all (聚合 JAR, TFI Facade)
                    ↑
                    └── tfi-examples (演示 + 基准测试)
```

---

## 2. 各模块设计

### 2.1 tfi-flow-core（核心流引擎）

| 组件 | 职责 |
|------|------|
| `ManagedThreadContext` | ThreadLocal 流上下文管理 |
| `SafeContextManager` | 单例模式，异步传播，泄漏检测 |
| `ZeroLeakThreadLocalManager` | ThreadLocal 诊断与清理 |
| `TfiFlow` | 流操作核心（stage/message） |
| `FlowProvider` | SPI 流提供者接口 |

### 2.2 tfi-flow-spring-starter（Spring 启动器）

| 组件 | 职责 |
|------|------|
| `TfiAnnotationAspect` | @TfiTask/@TfiTrack AOP 处理 |
| `ContextMonitoringAutoConfiguration` | 上下文监控自动配置 |
| `ChangeTrackingAutoConfiguration` | 变更追踪自动配置 |

⚠️ **模块零测试覆盖是主要风险。**

### 2.3 tfi-compare（比对引擎）

**比对策略体系**:

```
CompareStrategy<T>
├── BeanCompareStrategy          ← 对象字段级比对
└── ListCompareStrategy
    ├── SimpleListStrategy       ← 按索引逐一比对
    ├── AsSetListStrategy        ← 集合语义（无序）
    ├── EntityListStrategy       ← @Entity @Key 匹配
    └── LevenshteinListStrategy  ← 编辑距离，移动检测
```

### 2.4 tfi-ops-spring（运维监控）

| 组件 | 职责 |
|------|------|
| `SecureTfiEndpoint` | 安全 TFI Actuator 端点 |
| `TfiHealthIndicator` | 健康检查指示器 |
| `Store` / `CaffeineStore` | 缓存存储抽象 |
| `StoreAutoDegrader` | 存储自动降级 |

⚠️ **模块零测试覆盖。**

### 2.5 tfi-all（聚合 + Facade）

| 组件 | 职责 |
|------|------|
| `TFI` | Facade 入口（60+ 公开方法） |
| `TfiCore` | 核心引擎初始化 |
| `ProviderRegistry` | SPI 提供者注册表 (v4.0.0) |

---

## 3. TFI Facade 核心 API

```java
// 系统控制
TFI.enable() / TFI.disable() / TFI.isEnabled()

// 会话/阶段
TFI.startSession(name) → String
TFI.stage(name) → TaskContext (AutoCloseable)
TFI.run(name, Runnable) / TFI.call(name, Callable<T>) → T

// 变更追踪
TFI.track(name, target, fields...) / TFI.trackDeep(name, target)
TFI.getChanges() → List<ChangeRecord>

// 比对
TFI.compare(a, b) → CompareResult
TFI.comparator() → ComparatorBuilder
TFI.render(result, style) → String

// SPI (v4.0.0)
TFI.registerComparisonProvider(provider) / TFI.loadProviders(cl)
```

---

## 4. 设计模式

| 模式 | 应用 | 质量 |
|------|------|:----:|
| Facade | TFI 统一入口 | ⭐⭐⭐⭐⭐ |
| Strategy | CompareStrategy, ListCompareStrategy | ⭐⭐⭐⭐⭐ |
| Provider/SPI | 5 类 Provider | ⭐⭐⭐⭐ |
| Builder | ComparatorBuilder, TrackingOptions.Builder | ⭐⭐⭐⭐ |
| AutoCloseable | stage() 返回 TaskContext | ⭐⭐⭐⭐⭐ |
| Double-Check Locking | TfiCore 初始化 | ⭐⭐⭐⭐ |

---

## 5. 线程安全

| 机制 | 使用场景 |
|------|----------|
| `volatile` | TfiCore 共享标志位 |
| `synchronized(TFI.class)` | 初始化互斥 |
| `ConcurrentHashMap` | 策略缓存、Store 存储 |
| `ThreadLocal` | 追踪快照、流上下文 |
| `AtomicLong` | 计数器、指标 |

---

## 6. 异常处理

> **TFI 绝不向用户代码抛出异常** — 所有异常内部捕获并记录日志。

| 级别 | 处理方式 |
|------|----------|
| WARN | 记录日志，返回安全默认值 |
| ERROR | 记录日志 + 指标，返回安全默认值 |
| FATAL | 记录日志 + 告警，触发降级 |

---

## 7. 整体项目代码评分

| 维度 | 评分 | 权重 | 加权分 |
|------|:----:|:----:|:------:|
| 架构清晰度 | 9 | 15% | 1.35 |
| API 设计 | 9 | 15% | 1.35 |
| 可扩展性 | 8 | 10% | 0.80 |
| 代码可读性 | 8 | 10% | 0.80 |
| 异常安全 | 9 | 10% | 0.90 |
| 线程安全 | 9 | 10% | 0.90 |
| 测试覆盖 | 7 | 10% | 0.70 |
| 文档完整度 | 7 | 5% | 0.35 |
| 配置管理 | 8 | 5% | 0.40 |
| Spring 集成 | 8 | 10% | 0.80 |
| **总计** | | **100%** | **8.35 (A 级)** |

### 各模块评分

| 模块 | 设计 | 实现 | 测试 | 综合 |
|------|:----:|:----:|:----:|:----:|
| tfi-flow-core | 9 | 9 | 8 | 8.5 |
| tfi-compare | 9 | 8 | 8 | 8.0 |
| tfi-all (Facade) | 9 | 9 | 8 | 8.5 |
| tfi-ops-spring | 8 | 7 | 3 | 6.0 |
| tfi-flow-spring-starter | 8 | 8 | 2 | 5.8 |

---

## 8. 整体技术债务

| # | 描述 | 优先级 |
|---|------|:------:|
| TD-01 | tfi-flow-spring-starter 零测试 | P0 |
| TD-02 | tfi-ops-spring 零测试 | P0 |
| TD-03 | SpotBugs `failOnError=false` | P0 |
| TD-04 | Checkstyle `maxAllowedViolations=30000` | P1 |
| TD-05 | TFI Facade 60+ 公开方法，API 表面积过大 | P1 |
| TD-06 | tfi-flow-core 作为外部依赖，版本同步风险 | P1 |
| TD-07 | `TfiRoutingGoldenTest` @Disabled | P1 |
| TD-08 | JaCoCo 仅 tfi-all 启用 | P2 |
| TD-09 | 配置项缺少 schema 文档 | P2 |

---

## 9. 配置体系

```yaml
tfi:
  enabled: true                              # 主开关
  annotation.enabled: true                   # @TfiTask 注解支持
  api.routing.enabled: false                 # Provider 路由 (v4.0.0)
  change-tracking:
    snapshot.max-depth: 10                   # 最大遍历深度
    snapshot.exclude-patterns: ["*.password"] # 敏感字段排除
  compare:
    auto-route.lcs.enabled: true             # LCS 移动检测
    degradation.enabled: true                # 自动降级
```
