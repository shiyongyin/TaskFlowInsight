# TaskFlowInsight (tfi-all) 开发设计文档

> **版本**: v3.0.0 / v4.0.0-routing-refactor  
> **编写角色**: 资深开发专家 (Spring Boot 领域)  
> **更新日期**: 2026-02-16  
> **文档状态**: 正式版

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 技术栈](#2-技术栈)
- [3. 模块架构](#3-模块架构)
- [4. 核心设计详解](#4-核心设计详解)
- [5. 关键设计模式](#5-关键设计模式)
- [6. API 设计分析](#6-api-设计分析)
- [7. 配置体系](#7-配置体系)
- [8. 代码质量评分](#8-代码质量评分)
- [9. 改进建议](#9-改进建议)
- [10. 附录](#10-附录)

---

## 1. 项目概述

### 1.1 定位

TaskFlowInsight (TFI) 是一个面向 Java 应用的**业务流程可视化与变更追踪库**。其核心理念是让业务流程"自己说话"——通过自动生成流程树和跟踪对象状态变化，为开发者提供"X 光式"的代码执行洞察能力。

### 1.2 核心能力矩阵

| 能力 | 描述 | 核心类 |
|------|------|--------|
| **流程追踪** | 层级化执行流：Session → Task → Stage → Message | `TFI`, `SafeContextManager`, `ManagedThreadContext` |
| **变更追踪** | 深度对象比较与差异检测 | `ChangeTracker`, `DiffDetector`, `DiffFacade` |
| **对象比较** | 多策略对象比较引擎 | `CompareService`, `StrategyResolver`, `CompareEngine` |
| **导出渲染** | 控制台/JSON/Map/Markdown 多格式导出 | `ConsoleExporter`, `JsonExporter`, `MarkdownRenderer` |
| **监控运维** | Spring Actuator + Prometheus 指标 | `TfiEndpoint`, `TfiMetricsEndpoint`, `TfiHealthIndicator` |

---

## 2. 技术栈

### 2.1 核心依赖

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 运行时，支持虚拟线程、Records |
| Spring Boot | 3.5.5 | 应用框架、自动配置 |
| Spring AOP | 3.5.x | 注解切面 (`@TfiTask`, `@TfiTrack`) |
| Caffeine | 3.1.8 | 高性能本地缓存 |
| Micrometer | 1.x | 度量指标收集 |
| Lombok | 1.18.38 | 代码简化 |

### 2.2 构建工具

| 工具 | 版本 | 用途 |
|------|------|------|
| Maven | 3.9.x (mvnw) | 构建管理 |
| JaCoCo | 0.8.12 | 代码覆盖率 (50% 指令覆盖率门槛) |
| SpotBugs | 4.8.6.2 | 静态缺陷检测 |
| Checkstyle | 3.4.0 | 编码风格检查 (Google 规范) |
| PMD | 3.25.0 | 代码规则检查 |
| japicmp | 0.24.2 | API 兼容性检查 |
| JMH | 1.37 | 微基准测试 |

### 2.3 测试框架

| 框架 | 用途 |
|------|------|
| JUnit 5 | 单元/集成测试 |
| Mockito | Mock 框架 |
| AssertJ | 流式断言 |
| ArchUnit 1.3.0 | 架构测试 |
| jqwik 1.8.4 | 属性测试 |
| ApprovalTests 24.3.0 | 快照/黄金文件测试 |

---

## 3. 模块架构

### 3.1 模块依赖图

```
┌────────────────────────────────────────────────────┐
│                   tfi-all (聚合模块)                 │
│        TFI Facade + EnableTfi + TfiCore            │
├────────────────────────────────────────────────────┤
│                                                    │
│  ┌──────────────────┐  ┌───────────────────────┐  │
│  │ tfi-flow-spring- │  │    tfi-compare        │  │
│  │   starter        │  │  比较引擎 + 变更追踪    │  │
│  │  AOP + Spring集成 │  │  策略 + 注解 + 快照    │  │
│  └────────┬─────────┘  └──────────┬────────────┘  │
│           │                       │                │
│           ▼                       ▼                │
│  ┌──────────────────────────────────────────────┐  │
│  │              tfi-flow-core                    │  │
│  │   Session/Task/Message 模型 + Context管理     │  │
│  │   SPI Provider接口 + Exporter基础设施         │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ┌──────────────────────────────────────────────┐  │
│  │             tfi-ops-spring                    │  │
│  │   Actuator端点 + 度量指标 + 健康检查          │  │
│  │   性能仪表板 + 注解性能监控                    │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
├────────────────────────────────────────────────────┤
│              tfi-examples (示例应用)                │
│           Demo + Benchmark + 教学章节              │
└────────────────────────────────────────────────────┘
```

### 3.2 模块职责说明

| 模块 | 主要源文件数 | 职责 |
|------|-------------|------|
| **tfi-flow-core** | ~40+ | 核心模型（Session、TaskNode、Message）、上下文管理、SPI 接口定义、基础导出器 |
| **tfi-flow-spring-starter** | ~15+ | Spring 自动配置、`@TfiTask` 切面、SpEL 条件评估、数据脱敏 |
| **tfi-compare** | ~80+ | 对象比较引擎、变更追踪、快照提供者、差异检测、注解系统、渲染器 |
| **tfi-ops-spring** | ~20+ | Actuator 端点、Micrometer 指标、健康指示器、性能仪表板 |
| **tfi-all** | 4 (main) / 395 (test) | 聚合模块，统一入口 TFI Facade，集成测试主阵地 |
| **tfi-examples** | ~30+ | 示例应用、JMH 基准测试、教学 Demo |

---

## 4. 核心设计详解

### 4.1 流程追踪 (Task Flow)

#### 4.1.1 层级模型

```
Session (会话)
 └── TaskNode (任务节点)
      ├── Message[] (消息列表)
      └── TaskNode[] (子任务)
           ├── Message[]
           └── TaskNode[] ...
```

#### 4.1.2 上下文管理

**核心类**: `SafeContextManager` (单例)

- **ThreadLocal 隔离**: 每个线程维护独立的 `ManagedThreadContext`
- **零泄漏保证**: `ZeroLeakThreadLocalManager` 使用 `WeakReference + ReferenceQueue` 检测死线程
- **异步传播**: `createSnapshot()` / `restoreFromSnapshot()` 支持跨线程上下文传递
- **泄漏检测**: 定时任务检测超时未关闭的上下文

```java
// 推荐用法：AutoCloseable 模式
try (var stage = TFI.stage("processOrder")) {
    stage.message("Processing order...");
    // 业务逻辑
}
// stage 自动关闭，上下文自动清理
```

#### 4.1.3 异步支持

```java
// TFIAwareExecutor 自动传播上下文
ExecutorService executor = TFI.wrapExecutor(Executors.newFixedThreadPool(4));
executor.submit(() -> {
    // 这里能访问父线程的 TFI 上下文
    TFI.message("Async processing...");
});
```

### 4.2 变更追踪 (Change Tracking)

#### 4.2.1 架构图

```
          track("order", obj, "status", "amount")
                        │
                        ▼
              ┌──────────────────┐
              │  ChangeTracker   │
              │  ThreadLocal存储  │
              └────────┬─────────┘
                       │ captureBaseline()
                       ▼
              ┌──────────────────┐
              │ SnapshotProvider │
              │  快照策略选择      │
              └────────┬─────────┘
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
┌──────────────────┐     ┌──────────────────┐
│ DirectSnapshot   │     │ FacadeSnapshot   │
│   Provider       │     │   Provider       │
│  (直接反射快照)   │     │  (经DiffFacade)  │
└──────────────────┘     └──────────────────┘
                       │
                       ▼  getChanges()
              ┌──────────────────┐
              │   DiffDetector   │
              │   差异检测引擎    │
              └────────┬─────────┘
                       │
                       ▼
              ┌──────────────────┐
              │ List<ChangeRecord>│
              │   变更记录列表    │
              └──────────────────┘
```

#### 4.2.2 快照策略

| 策略 | 类 | 适用场景 |
|------|----|----|
| 浅层快照 | `DirectSnapshotProvider` | 指定字段的值复制，性能最佳 |
| 深度快照 | `ObjectSnapshotDeepOptimized` | 完整对象图深度复制，支持循环引用检测 |

#### 4.2.3 差异检测

**DiffDetector** 支持多种比较策略：

| 策略 | 适用类型 | 算法 |
|------|----------|------|
| `NumericCompareStrategy` | 数值类型 | 精度控制 (`@NumericPrecision`) |
| `EnhancedDateCompareStrategy` | 日期类型 | 容差比较 |
| `MapCompareStrategy` | Map 类型 | Key 级别增删改检测 |
| `SetCompareStrategy` | Set 类型 | 集合差异 |
| `CollectionCompareStrategy` | List 类型 | 索引匹配 / LCS 算法 |

### 4.3 对象比较引擎 (Compare Engine)

#### 4.3.1 策略选择优先级

```
精确类型匹配 (priority: 100)
    │
    ▼ 未命中
接口/父类型匹配 (priority: 50)
    │
    ▼ 未命中
通用策略 (priority: 0)
```

#### 4.3.2 列表比较策略矩阵

| 策略 | 标记 | 行为 |
|------|------|------|
| `SimpleListStrategy` | 默认 | 按索引逐个比较 |
| `AsSetListStrategy` | `@ValueObject` | 作为集合比较，忽略顺序 |
| `EntityListStrategy` | `@Entity + @Key` | 按主键匹配，检测新增/删除/修改 |
| `LevenshteinListStrategy` | LCS 启用 | LCS 算法检测移动操作 |

#### 4.3.3 类型系统注解

```java
@Entity                          // 实体：有唯一标识
public class Order {
    @Key                         // 主键字段
    private Long id;
    
    @NumericPrecision(scale = 2) // 数值精度
    private BigDecimal amount;
    
    @DiffIgnore                  // 排除比较
    private Date updateTime;
    
    @DateFormat("yyyy-MM-dd")    // 日期格式化
    private Date createDate;
}

@ValueObject                     // 值对象：基于值比较
public class Address {
    private String city;
    private String street;
}
```

### 4.4 SPI 提供者路由 (v4.0.0)

#### 4.4.1 Provider 架构

```
         TFI Facade
             │
     ┌───────┴───────┐
     │ ProviderRegistry │
     │  优先级路由       │
     └───────┬───────┘
             │
    ┌────────┼────────┬──────────┬──────────┐
    ▼        ▼        ▼          ▼          ▼
  Flow    Tracking  Compare   Render    Export
Provider  Provider  Provider  Provider  Provider
```

#### 4.4.2 Provider 加载优先级

| 优先级 | 来源 | 范围 |
|--------|------|------|
| 200+ | Spring Bean 注册 | 自动发现 |
| 1-199 | 手动编程注册 | `TFI.registerXxxProvider()` |
| 0 | ServiceLoader SPI | `META-INF/services` |

#### 4.4.3 路由开关

```yaml
tfi:
  api:
    routing:
      enabled: false          # 主开关 (v4.0.0)
      provider-mode: auto     # auto / spring-only / service-loader-only
```

### 4.5 路径系统 (Path System)

#### 4.5.1 路径格式

| 类型 | 格式 | 示例 |
|------|------|------|
| 字段 | `parent.fieldName` | `order.status` |
| Map Key | `parent["key"]` | `map["userId"]` |
| 数组索引 | `parent[index]` | `items[0]` |
| Set 元素 | `parent{element}` | `tags{java}` |

#### 4.5.2 路径去重

**PathDeduplicator** 负责在对象图中选择最具体的路径：

- `PathCollector` 收集所有可达路径
- `PathArbiter` 选择最短/最具体路径
- Top-N 裁剪防止候选路径爆炸
- 快速路径回退：当变更数超过阈值时使用传统路径去重

---

## 5. 关键设计模式

### 5.1 设计模式清单

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **Facade** | `TFI.java` | 统一入口，屏蔽内部复杂性 |
| **Strategy** | `CompareService`, `StrategyResolver` | 可插拔比较策略 |
| **Provider/SPI** | `ProviderRegistry` | ServiceLoader + Spring Bean 双重注册 |
| **Observer** | 变更事件通知 | 容器事件 (ContainerEvents) |
| **Builder** | `ComparatorBuilder`, `PathBuilder` | 流式构建 API |
| **Template Method** | `DemoChapter` | Demo 章节执行模板 |
| **Singleton** | `SafeContextManager` | 全局上下文管理 |
| **AutoCloseable** | `TaskContext`, `ManagedThreadContext` | 资源自动释放 |
| **Double-Checked Locking** | `TFI` 初始化 | 延迟初始化优化 |
| **Decorator** | `TFIAwareExecutor` | 包装 Executor 添加上下文传播 |
| **Chain of Responsibility** | `DiffFacade` 解析链 | 编程 → Spring → 静态回退 |

### 5.2 线程安全设计

| 机制 | 应用 |
|------|------|
| `ThreadLocal` | 上下文隔离、变更追踪存储 |
| `volatile` | `TfiCore` 全局开关 |
| `ConcurrentHashMap` | 活跃上下文注册、路径缓存 |
| `WeakReference` | 死线程检测清理 |
| `ReentrantReadWriteLock` | Provider 注册表并发控制 |

### 5.3 异常安全设计

```
所有 TFI 公共 API
    │
    ├── try-catch 包裹
    │     └── handleInternalError(ErrorLevel)
    │           ├── WARN  → 日志警告
    │           ├── ERROR → 日志错误
    │           └── FATAL → 日志致命 (仍不抛出)
    │
    └── 返回安全默认值 (null / empty / NullTaskContext)
```

**核心原则**: TFI 永远不向业务代码抛出异常，最多记录日志。

---

## 6. API 设计分析

### 6.1 TFI Facade 方法分类

| 类别 | 方法数 | 示例 |
|------|--------|------|
| 系统控制 | 4 | `enable()`, `disable()`, `isEnabled()`, `clear()` |
| 会话管理 | 2 | `startSession()`, `endSession()` |
| 任务/阶段 | 6 | `stage()`, `start()`, `stop()`, `run()`, `call()` |
| 消息记录 | 4 | `message()`, `error()` |
| 上下文查询 | 3 | `getCurrentSession()`, `getCurrentTask()`, `getTaskStack()` |
| 变更追踪 | 10+ | `track()`, `trackAll()`, `trackDeep()`, `getChanges()`, `withTracked()` |
| 对象比较 | 3 | `compare()`, `comparator()`, `render()` |
| 导出 | 3 | `exportToConsole()`, `exportToJson()`, `exportToMap()` |
| Provider 注册 | 6 | `registerFlowProvider()`, `registerTrackingProvider()` 等 |

### 6.2 API 设计评价

| 维度 | 评分 | 说明 |
|------|------|------|
| 易用性 | ★★★★☆ | 静态方法入口，零配置可用 |
| 一致性 | ★★★☆☆ | 部分方法命名风格不统一 |
| 安全性 | ★★★★★ | 完善的异常安全 + 资源管理 |
| 可扩展性 | ★★★★☆ | SPI + Provider 架构良好 |
| 文档完整度 | ★★★☆☆ | Javadoc 中英混合，部分缺失 |

---

## 7. 配置体系

### 7.1 配置层次

```
系统属性 (System Properties)
    │ 最高优先级
    ▼
环境变量 (Environment Variables)
    │
    ▼
application.yml (Spring Boot)
    │
    ▼
@EnableTfi 注解参数
    │
    ▼
默认值 (TfiConfig Record 默认)
    │ 最低优先级
```

### 7.2 核心配置项

```yaml
tfi:
  enabled: true                              # 主开关
  annotation:
    enabled: true                            # @TfiTask/@TfiTrack 支持
  api:
    routing:
      enabled: false                         # Provider 路由 (v4.0.0)
      provider-mode: auto                    # auto/spring-only/service-loader-only
    facade:
      enabled: true                          # Facade API 开关
  change-tracking:
    snapshot:
      max-depth: 10                          # 最大遍历深度
      provider: direct                       # direct / facade
    max-tracked-objects: 1000                # 最大追踪对象数
  compare:
    auto-route:
      lcs:
        enabled: true                        # LCS 算法启用
  diff:
    heavy:
      field-threshold: 50                    # 重型字段阈值
  context:
    leak-detection:
      enabled: true                          # 泄漏检测
      timeout-seconds: 300                   # 超时秒数
```

### 7.3 Profile 配置

| Profile | 特征 |
|---------|------|
| `dev` | TFI 启用，DEBUG 日志，完整 Actuator |
| `prod` | TFI 禁用，变更追踪关闭，最小 Actuator |

---

## 8. 代码质量评分

### 8.1 综合评分卡

| 评估维度 | 权重 | 得分 (1-10) | 加权得分 | 评价 |
|----------|------|-------------|----------|------|
| **架构设计** | 20% | 9.0 | 1.80 | 多模块分层清晰，SPI 扩展性优秀 |
| **代码可读性** | 15% | 7.5 | 1.13 | 整体良好，TFI.java 过大需拆分 |
| **设计模式运用** | 10% | 9.0 | 0.90 | 策略、Facade、SPI、Builder 运用得当 |
| **异常安全性** | 10% | 9.5 | 0.95 | 业界领先的异常安全设计 |
| **线程安全性** | 10% | 9.0 | 0.90 | ThreadLocal + 零泄漏保证 |
| **API 设计** | 10% | 8.0 | 0.80 | 统一入口易用，部分命名待优化 |
| **测试覆盖** | 10% | 8.5 | 0.85 | 395 个测试类，覆盖全面 |
| **性能设计** | 5% | 8.5 | 0.43 | Caffeine 缓存、虚拟线程、JMH 基准 |
| **可维护性** | 5% | 7.5 | 0.38 | Javadoc 中英混合，部分历史债务 |
| **配置管理** | 5% | 8.0 | 0.40 | Record 配置类型安全，多层配置支持 |
| **总分** | **100%** | — | **8.54** | **优秀** |

### 8.2 等级标准

| 等级 | 分数范围 | 描述 |
|------|----------|------|
| 卓越 | 9.0 - 10.0 | 业界标杆级代码 |
| **优秀** | **8.0 - 8.9** | **生产就绪，设计优良** |
| 良好 | 7.0 - 7.9 | 可上线，需要改进 |
| 及格 | 6.0 - 6.9 | 基本可用，较多问题 |
| 不及格 | < 6.0 | 需要重大重构 |

### 8.3 各维度详细评价

#### 8.3.1 架构设计 (9.0/10)

**优势**:
- 清晰的五模块分层：core → starter → compare → ops → all
- SPI 提供者架构支持运行时热插拔
- 聚合模块 (tfi-all) 提供统一入口，降低使用门槛
- Spring 可选设计：纯 Java 环境可回退初始化

**不足**:
- `tfi-flow-core` 作为外部模块不在 modules 列表中，构建流程不连续
- 模块间部分循环依赖通过 optional 解决，但增加理解成本

#### 8.3.2 代码可读性 (7.5/10)

**优势**:
- 包结构语义清晰 (api, context, tracking, config)
- 变量命名遵循 Java 惯例

**不足**:
- `TFI.java` 约 1600 行，职责过于集中
- Javadoc 中英文混合，影响阅读一致性
- 部分废弃 API 保留导致认知负担

#### 8.3.3 异常安全性 (9.5/10)

**优势**:
- 所有公共 API 均有 try-catch 包裹
- `ErrorLevel` 分级处理（WARN/ERROR/FATAL）
- 从不向业务代码抛出异常
- `NullTaskContext` 空对象模式确保禁用时安全

#### 8.3.4 测试覆盖 (8.5/10)

**优势**:
- 395 个测试类覆盖 API、Actuator、Compare、Snapshot、Path、Exporter、SPI 等
- 多层测试：Unit + Integration + Performance + Architecture + Property + Golden
- JaCoCo 50% 指令覆盖率门槛

**不足**:
- `TfiCore` 缺少专用单元测试
- 部分 Change Exporter 未直接测试
- `ThreeWayMergeService` 无专用测试

---

## 9. 改进建议

### 9.1 高优先级

| 编号 | 建议 | 影响 | 工作量 |
|------|------|------|--------|
| H-1 | 拆分 `TFI.java` 为多个委托类 (FlowApi, TrackApi, CompareApi) | 可读性、可维护性 | 中 |
| H-2 | 统一 Javadoc 语言 (建议全英文 + 中文 README) | 国际化、一致性 | 中 |
| H-3 | 补充 `TfiCore` 和 `ThreeWayMergeService` 单元测试 | 测试覆盖 | 低 |

### 9.2 中优先级

| 编号 | 建议 | 影响 | 工作量 |
|------|------|------|--------|
| M-1 | 将 `tfi-flow-core` 纳入多模块构建 | 构建连续性 | 低 |
| M-2 | 引入 `@Deprecated(forRemoval=true, since="4.0")` 标记废弃 API | API 演进 | 低 |
| M-3 | 添加 OpenTelemetry Trace 集成 | 可观测性 | 高 |

### 9.3 低优先级

| 编号 | 建议 | 影响 | 工作量 |
|------|------|------|--------|
| L-1 | 支持 GraalVM Native Image 编译 | 云原生 | 高 |
| L-2 | 添加 Docker 镜像构建支持 | 部署便利 | 低 |
| L-3 | 引入 Mutation Testing (PIT) | 测试质量 | 中 |

---

## 10. 附录

### 10.1 源代码统计

| 指标 | 数值 |
|------|------|
| 主源码文件 (tfi-all) | 4 |
| 测试文件 (tfi-all) | 395 |
| 总模块数 | 5 + 1 (外部 core) |
| 依赖数 (直接) | 6 内部 + 3 外部 |
| 测试框架数 | 6 (JUnit5, Mockito, AssertJ, ArchUnit, jqwik, ApprovalTests) |

### 10.2 关键文件索引

| 文件 | 位置 | 说明 |
|------|------|------|
| `TFI.java` | `tfi-all/src/main/java/.../api/` | 主 Facade |
| `TfiCore.java` | `tfi-all/src/main/java/.../core/` | 运行时状态 |
| `EnableTfi.java` | `tfi-all/src/main/java/.../config/` | 启用注解 |
| `SafeContextManager.java` | `tfi-flow-core` | 上下文管理 |
| `ChangeTracker.java` | `tfi-compare` | 变更追踪器 |
| `CompareService.java` | `tfi-compare` | 比较服务 |
| `TfiEndpoint.java` | `tfi-ops-spring` | Actuator 端点 |
| `TfiHealthIndicator.java` | `tfi-ops-spring` | 健康指示器 |

---

> **文档编写**: 资深开发专家  
> **审核**: 项目经理  
> **下次评审日期**: 依据项目迭代计划
