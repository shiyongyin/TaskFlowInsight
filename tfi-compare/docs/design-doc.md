# TFI-Compare 开发设计文档

> **文档版本**: v2.0.0  
> **模块版本**: 3.0.0 (当前分支: feature/v4.0.0-routing-refactor)  
> **撰写角色**: 资深开发专家（Spring Boot 领域）  
> **审阅**: 项目经理协调  
> **初版日期**: 2026-02-15  
> **更新日期**: 2026-02-15 (v2 — 代码改进后重新评估)  

---

## 目录

1. [模块概述](#1-模块概述)
2. [架构设计](#2-架构设计)
3. [核心包与类设计](#3-核心包与类设计)
4. [核心算法详解](#4-核心算法详解)
5. [设计模式应用](#5-设计模式应用)
6. [SPI 扩展机制](#6-spi-扩展机制)
7. [Spring Boot 集成设计](#7-spring-boot-集成设计)
8. [配置体系](#8-配置体系)
9. [线程安全与并发设计](#9-线程安全与并发设计)
10. [性能设计](#10-性能设计)
11. [依赖关系](#11-依赖关系)
12. [代码设计评分](#12-代码设计评分)
13. [改进建议](#13-改进建议)

---

## 1. 模块概述

### 1.1 定位

`tfi-compare` 是 TaskFlowInsight 的**对象比较与变更追踪引擎**，提供以下核心能力：

- **深度对象比较**: 任意 Java 对象的字段级差异检测
- **变更追踪**: 基于快照的对象状态变化追踪（浅/深两种策略）
- **列表差异分析**: 支持 5 种列表比较策略（Simple、Entity、LCS、Levenshtein、AsSet）
- **变更导出**: Console、JSON、CSV、XML、Map 等多格式导出
- **性能降级**: 自适应降级机制应对大对象图场景

### 1.2 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 主语言，使用 Record、sealed class 等特性 |
| Spring Boot | 3.5.5 | 自动配置、DI |
| Caffeine | 3.1.8 | 路径匹配缓存 |
| Micrometer | - | 指标收集 |
| Jackson | - | JSON 序列化（可选） |
| Lombok | - | 代码生成 |

### 1.3 模块坐标

```xml
<groupId>com.syy</groupId>
<artifactId>tfi-compare</artifactId>
<version>3.0.0</version>
```

---

## 2. 架构设计

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                     API 层 (Facade)                         │
│  TfiListDiffFacade │ CompareService │ DiffFacade            │
│  ComparatorBuilder │ ComparisonTemplate │ DiffBuilder       │
├─────────────────────────────────────────────────────────────┤
│                     引擎层 (Engine)                          │
│  CompareEngine │ ListCompareExecutor │ StrategyResolver     │
├─────────────────────────────────────────────────────────────┤
│                     策略层 (Strategy)                        │
│  CompareStrategy<T> │ ListCompareStrategy                   │
│  ┌─ SetCompareStrategy   ┌─ SimpleListStrategy              │
│  ├─ MapCompareStrategy   ├─ EntityListStrategy              │
│  ├─ ArrayCompare...      ├─ LcsListStrategy                 │
│  ├─ DateCompare...       ├─ LevenshteinListStrategy         │
│  └─ CollectionCompare... └─ AsSetListStrategy               │
├─────────────────────────────────────────────────────────────┤
│                     检测层 (Detector)                        │
│  DiffDetector (static) │ DiffDetectorService (DI)           │
│  DiffFacade (Unified)  │ PrecisionController                │
├─────────────────────────────────────────────────────────────┤
│                     快照层 (Snapshot)                        │
│  ObjectSnapshot │ ObjectSnapshotDeep │ SnapshotFacade       │
│  ShallowSnapshotStrategy │ DeepSnapshotStrategy             │
│  PathLevelFilterEngine │ ClassLevelFilterEngine              │
├─────────────────────────────────────────────────────────────┤
│                   基础设施层 (Infrastructure)                 │
│  Cache │ Metrics │ Monitoring │ Path │ Determinism           │
│  Export │ Render │ Config │ SPI                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心数据流

```
用户调用 API (compare/track/diff)
       │
       ▼
   CompareEngine
       │
       ├── 快速路径: same-ref / null / type-mismatch → 直接返回
       │
       ├── List 路由 → ListCompareExecutor
       │       ├── 策略选择 (Simple/Entity/LCS/Levenshtein/AsSet)
       │       ├── PerfGuard 检查
       │       └── 降级处理
       │
       ├── 策略匹配 → StrategyResolver → CompareStrategy
       │
       └── Fallback: Snapshot → DiffFacade → DiffDetector
               │
               ▼
          CompareResult (FieldChange 列表)
               │
               ├── StableSorter (SSOT 排序)
               ├── Metrics 上报
               └── 返回调用方
```

### 2.3 模块依赖关系

```
tfi-compare ──depends──▶ tfi-flow-core (Session/Context)
     │
     ▼
tfi-all (aggregator, contains integration tests)
     │
     ▼
tfi-examples (usage demos)
```

---

## 3. 核心包与类设计

### 3.1 包结构总览

| 包 | 职责 | 主要类数 |
|---|------|---------|
| `annotation/` | 类型系统注解 | 14 |
| `api/` | 公共 API 门面 | 8 |
| `aspect/` | AOP 切面 | 1 |
| `config/` | Spring 自动配置 | 10 |
| `concurrent/` | 并发工具 | 1 |
| `exporter/change/` | 变更导出器 | 7 |
| `metrics/` | 指标收集 | 4 |
| `registry/` | 注册中心 | 3 |
| `spi/` | 服务提供者接口 | 6 |
| `tracking/` | 核心追踪引擎 | ~120 |

### 3.2 核心类详解

#### CompareEngine — 比较引擎（SSOT）

```
职责: 轻量级执行引擎，是排序逻辑的唯一入口
版本: 3.0.0-M2
关键设计:
  - 快速路径优先（same-ref / null / type-mismatch）
  - List 特殊路由到 ListCompareExecutor
  - 策略解析执行 via StrategyResolver
  - StableSorter 排序 → 唯一调用点（SSOT）
  - 指标双轨上报（TfiMetrics 优先，MicrometerDiagnosticSink 兜底）
```

#### CompareService — 比较服务

```
职责: 高级比较服务，编排 CompareEngine 能力
版本: 2.1.0 → 3.0.0 (职责拆分后)
关键 API:
  - compare(obj1, obj2) → CompareResult
  - compare(obj1, obj2, options) → CompareResult
  - compareBatch(pairs, options) → List<CompareResult>
  - registerStrategy(type, strategy) / registerNamedStrategy(name, strategy)
特性:
  - 支持 Spring DI 和无参构造（纯 Java）
  - 批量比较使用虚拟线程池（Virtual Thread Executor）— v2 改进
  - 三方合并逻辑已拆分至 ThreeWayMergeService
  - 报告/Patch 生成已拆分至 CompareReportGenerator
```

#### CompareReportGenerator — 报告生成器（v2 新增）

```
职责: 从 CompareService 拆分出的比较报告与 Patch 生成器
版本: 3.0.0
关键 API:
  - generateTextReport(result) → String
  - generateMarkdownReport(result) → String
  - generateJsonReport(result) → String (Jackson可选)
  - generateHtmlReport(result) → String
  - generateJsonPatch(result) → String
  - generateMergePatch(result) → Map
设计说明:
  - 纯函数设计，所有方法为 static，无状态
  - 符合 SRP（单一职责原则），仅负责报告格式化
  - Jackson 不可用时自动降级到手动 JSON 构建
```

#### ThreeWayMergeService — 三方合并服务（v2 新增）

```
职责: 从 CompareService 拆分出的三方合并与冲突检测
版本: 3.0.0
关键 API:
  - merge(base, left, right, options) → MergeResult
  - detectConflicts(baseVsLeft, baseVsRight) → List<MergeConflict>
  - autoMerge(base, left, right) → Object
设计说明:
  - 依赖注入 CompareService（构造函数注入）
  - 冲突类型: FIELD_CONFLICT / TYPE_CONFLICT / NULL_CONFLICT
  - 解决策略: LEFT_WINS / RIGHT_WINS / MANUAL
```

#### DiffFacade — 统一差异检测入口

```
职责: 统一 Diff 入口，Facade 模式
选择链: programmatic ThreadLocal > Spring Bean > static DiffDetector
关键设计:
  - ThreadLocal 存储 programmatic DiffDetectorService
  - ApplicationContext volatile 引用
  - 异常安全: 捕获异常后降级到下一层
```

#### ListCompareExecutor — 列表比较执行器

```
职责: 路由列表比较到最合适的策略
策略选择:
  1. CompareOptions 指定 → 直接使用
  2. 自动路由 → 基于元素类型、@Entity/@Key 注解
  3. PerfGuard → 大列表降级
降级计数: AtomicLong degradationCounter
```

#### StableSorter — 确定性排序

```
职责: 保证 FieldChange 列表的确定性排序
排序规则: Key → Field → ChangeType Priority → Path
缓存: ConcurrentHashMap (size-limited PARSE_CACHE)
```

### 3.3 注解体系

| 注解 | 目标 | 用途 |
|------|------|------|
| `@Entity` | TYPE | 标记实体类型（有唯一标识） |
| `@ValueObject` | TYPE | 标记值对象（基于值比较） |
| `@Key` | FIELD | 标记实体主键字段 |
| `@DiffIgnore` | FIELD | 排除字段不参与比较 |
| `@DiffInclude` | FIELD | 显式包含字段参与比较 |
| `@NumericPrecision` | FIELD | 控制数值比较精度 |
| `@DateFormat` | FIELD | 日期格式化 |
| `@CustomComparator` | FIELD | 自定义比较器 |
| `@ShallowReference` | FIELD | 浅引用（仅比较引用变化） |
| `@ObjectType` | TYPE | 对象类型声明 |
| `@TfiTrack` | METHOD | AOP 自动追踪 |

---

## 4. 核心算法详解

### 4.1 快照 Diff 算法

```
DiffDetector.diff(name, before, after):
  1. 计算 key 并集 = before.keySet() ∪ after.keySet()
  2. 对每个 key:
     - 仅在 before → DELETE
     - 仅在 after → CREATE
     - 两者都有:
       a. 精度比较 (NumericCompareStrategy / DateCompareStrategy)
       b. Map 递归比较 (MapCompareStrategy)
       c. Set 比较 (SetCompareStrategy)
       d. Collection 比较 (CollectionCompareStrategy)
       e. equals() 兜底
  3. 路径去重 (PathDeduplicator)
  4. 重对象缓存 (WeakHashMap, field-threshold 控制)
```

### 4.2 列表比较策略

| 策略 | 时间复杂度 | 空间复杂度 | 适用场景 | Move 检测 |
|------|-----------|-----------|---------|----------|
| **Simple** | O(n) | O(1) | 索引对齐列表 | 否 |
| **Entity** | O(n·m) | O(n) | @Entity + @Key 标记的列表 | 否 |
| **LCS** | O(n·m) | O(n·m) | 有序列表，需要 Move 检测 | 是 |
| **Levenshtein** | O(n·m) | O(n·m) | 编辑距离最小化 | 是 |
| **AsSet** | O(n) | O(n) | 顺序无关集合 | 否 |

### 4.3 LCS Move 检测

```
LCS 策略流程:
  1. 提取匹配 key (Entity → @Key, 否则 hashCode)
  2. 计算 LCS (Longest Common Subsequence)
  3. 非 LCS 元素 = DELETE + CREATE
  4. 合并 DELETE+CREATE (同 key) → MOVE
  5. LCS 匹配元素 → 属性级 diff
```

### 4.4 路径去重

```
PathDeduplicator:
  - 输入: ChangeRecord 列表（可能有重叠路径）
  - 策略: 基于 PathArbiter 的优先级计算
  - PathArbiter: 精确匹配 > 通配符 > 前缀
  - 输出: 去重后的 ChangeRecord 列表
```

---

## 5. 设计模式应用

| 模式 | 应用位置 | 评价 |
|------|---------|------|
| **Strategy** | CompareStrategy / ListCompareStrategy | ★★★★★ 核心扩展点 |
| **Facade** | DiffFacade / TfiListDiffFacade / CompareService | ★★★★★ API 统一入口 |
| **Template Method** | CompareEngine (fast-path → strategy → fallback) | ★★★★☆ 流程清晰 |
| **Chain of Responsibility** | DiffFacade (programmatic → Spring → static) | ★★★★☆ 优雅降级 |
| **Builder** | CompareOptions / CompareResult / FieldChange | ★★★★★ Lombok Builder |
| **SPI** | ComparisonProvider / TrackingProvider / RenderProvider | ★★★★☆ 可插拔 |
| **Observer** | DegradationLevelChangedEvent | ★★★☆☆ 事件通知 |
| **Cache** | StrategyCache / PathCache / ReflectionMetaCache | ★★★★☆ 性能优化 |

---

## 6. SPI 扩展机制

### 6.1 三大 SPI 接口

```java
// 比较能力 SPI
ComparisonProvider {
    CompareResult compare(Object before, Object after);
    CompareResult compare(Object before, Object after, CompareOptions options);
    double similarity(Object obj1, Object obj2);
    Object threeWayMerge(Object base, Object left, Object right);
    int priority();  // 优先级: 0=默认, 1-99=用户, 100-199=框架, 200+=Spring
}

// 追踪能力 SPI
TrackingProvider { ... }

// 渲染能力 SPI
RenderProvider { ... }
```

### 6.2 发现机制

```
优先级顺序:
1. Spring Bean (优先级 200+)
2. 手动注册 (TFI.registerXxxProvider)
3. ServiceLoader (META-INF/services/)
4. 默认实现 (DefaultXxxProvider, 优先级 0)
```

### 6.3 ServiceLoader 注册

```
META-INF/services/
├── com.syy.taskflowinsight.spi.ComparisonProvider
│   └── com.syy.taskflowinsight.spi.DefaultComparisonProvider
├── com.syy.taskflowinsight.spi.RenderProvider
│   └── com.syy.taskflowinsight.spi.DefaultRenderProvider
└── com.syy.taskflowinsight.spi.TrackingProvider
    └── com.syy.taskflowinsight.spi.DefaultTrackingProvider
```

---

## 7. Spring Boot 集成设计

### 7.1 自动配置

| 配置类 | 条件 | 职责 |
|-------|------|------|
| `ChangeTrackingAutoConfiguration` | `tfi.enabled=true` | DiffFacade、PrecisionController、PropertyComparatorRegistry、Exporter |
| `DeepTrackingAutoConfiguration` | `tfi.deep-tracking.enabled=true` | 深度追踪切面 |
| `ConcurrencyAutoConfiguration` | - | 并发配置 |

### 7.2 Bean 注册清单

```
ChangeTrackingAutoConfiguration:
  ├── DiffFacade.AppContextInjector (注入 ApplicationContext)
  ├── SnapshotProviders.AppContextInjector
  ├── PropertyComparatorRegistry
  ├── ChangeJsonExporter
  ├── ChangeConsoleExporter
  ├── ChangeExporter.ExportConfig
  └── Scheduled cleanup task

DeepTrackingAutoConfiguration:
  └── TfiDeepTrackingAspect (@TfiTrack 切面)
```

### 7.3 纯 Java 模式

```java
// 无需 Spring，直接使用
CompareService service = new CompareService();
CompareResult result = service.compare(before, after);

// DiffFacade 自动降级到 static DiffDetector
DiffFacade.diff("order", beforeMap, afterMap);
```

---

## 8. 配置体系

### 8.1 配置前缀: `tfi.*`

```yaml
tfi:
  enabled: true
  change-tracking:
    snapshot:
      max-depth: 10
      max-fields: 500
      provider: facade  # facade / direct
    max-tracked-objects: 1000
  compare:
    auto-route:
      lcs:
        enabled: true
        max-size: 1000
    perf:
      budget-ms: 5000
      max-changes: 10000
  diff:
    heavy:
      field-threshold: 50
    compat:
      heavy-optimizations:
        enabled: true
  path:
    dedup:
      enhanced:
        enabled: true
  render:
    style: standard
    max-changes: 200
  degradation:
    enabled: true
    threshold:
      cpu: 80
      memory: 85
```

### 8.2 配置解析链

```
ConfigurationResolverImpl:
  1. @ConfigurationProperties 绑定
  2. System Properties 覆盖
  3. ConfigDefaults 默认值
  4. ConfigMigrationMapper 旧版配置迁移
```

---

## 9. 线程安全与并发设计

### 9.1 线程安全机制

| 组件 | 策略 | 详情 |
|------|------|------|
| `CompareEngine` | 无状态调用 | 每次 execute() 无共享可变状态 |
| `CompareService` | ConcurrentHashMap | strategies / namedStrategies |
| `DiffFacade` | ThreadLocal + volatile | programmaticService / applicationContext |
| `StrategyResolver` | ConcurrentHashMap | fallbackCache |
| `DiffDetector` | synchronized + **volatile** | HEAVY_CACHE (WeakHashMap) + 6 个 volatile 静态配置字段 |
| `StableSorter` | ConcurrentHashMap | PARSE_CACHE (size-limited) |
| `ListCompareExecutor` | AtomicLong | degradationCounter |
| `CompareReportGenerator` | 无状态 | 纯静态方法，天然线程安全 |
| `ThreeWayMergeService` | 不可变引用 | 构造函数注入 CompareService |

### 9.2 批量并行

```java
// CompareService.compareBatch() — v2 改进: 虚拟线程池替代 parallelStream
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<CompletableFuture<CompareResult>> futures = pairs.stream()
            .map(p -> CompletableFuture.supplyAsync(
                    () -> compare(p.getLeft(), p.getRight(), options), executor))
            .toList();
    return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
}
```

> **v2 改进说明**: 原 `parallelStream` 共享 ForkJoinPool.commonPool()，在高并发场景下可能导致全局阻塞。改为虚拟线程池后：
> - 每个比较任务独立虚拟线程，互不干扰
> - `try-with-resources` 确保线程池自动关闭
> - Java 21 虚拟线程开销极低，适合 I/O 密集型或短时 CPU 任务

### 9.3 DiffDetector volatile 修复记录（v2 新增）

以下 6 个静态可变字段已从普通字段升级为 `volatile`，确保多线程可见性：

| 字段 | 类型 | 用途 |
|------|------|------|
| `precisionCompareEnabled` | `volatile boolean` | 精度比较开关 |
| `precisionController` | `volatile PrecisionController` | 精度控制器引用 |
| `dateTimeFormatter` | `volatile DateTimeFormatter` | 日期格式化器 |
| `precisionMetrics` | `volatile PrecisionMetrics` | 精度指标 |
| `currentObjectClass` | `volatile Class<?>` | 当前比较对象类型 |
| `enhancedDeduplicationEnabled` | `volatile boolean` | 增强去重开关 |

### 9.4 注意事项

- `DiffDetector` 的静态配置方法（setPrecisionCompareEnabled 等）现已 volatile 安全，但仍建议在初始化阶段调用
- `FieldChange.clock` 是可变静态字段，仅用于测试注入

---

## 10. 性能设计

### 10.1 PerfGuard

```
PerfGuard 机制:
  - 预算控制: budget-ms（超时触发降级）
  - 规模控制: max-changes（变更数上限）
  - 信号: PARTIAL（部分结果）/ DEGRADED（降级结果）
  - 策略降级: LCS → Simple, Levenshtein → Entity
```

### 10.2 缓存体系

| 缓存 | 实现 | 用途 |
|------|------|------|
| `StrategyCache` | Caffeine / ConcurrentHashMap | 策略解析结果 |
| `ReflectionMetaCache` | ConcurrentHashMap | 反射元数据 |
| `PathCache` | ConcurrentHashMap | 路径构建 |
| `CaffeinePathMatcherCache` | Caffeine | 路径匹配 |
| `StableSorter.PARSE_CACHE` | ConcurrentHashMap (bounded) | 排序解析 |
| `DiffDetector.HEAVY_CACHE` | WeakHashMap (synchronized) | 重对象 diff |

### 10.3 降级机制

```
DegradationManager:
  Level: NORMAL → LIGHT → MODERATE → HEAVY → CRITICAL
  触发: CPU > threshold / Memory > threshold
  影响: 减少比较深度、跳过非关键字段、缩短缓存 TTL
```

---

## 11. 依赖关系

### 11.1 编译依赖

| 依赖 | 必须/可选 | 说明 |
|------|----------|------|
| `tfi-flow-core` | 必须 | 上下文、Session 管理 |
| `jakarta.validation-api` | 必须 | 校验注解 |
| `jakarta.annotation-api` | 必须 | @PostConstruct |
| `spring-boot-autoconfigure` | 可选 | 自动配置 |
| `aspectjrt` | 可选 | AOP 切面 |
| `micrometer-core` | 可选 | 指标 |
| `caffeine` | 可选 | 高性能缓存 |
| `jackson-databind` | 可选 | JSON 序列化 |
| `lombok` | provided | 代码生成 |

### 11.2 模块间依赖

```
tfi-compare → tfi-flow-core (compile)
tfi-all → tfi-compare (compile)
tfi-examples → tfi-all (compile)
```

---

## 12. 代码设计评分

### 12.1 评分维度与结果（v2 — 改进后重新评估）

> **说明**: v1 评分为 8.06/10。经过 System.out 清除、volatile 修复、CompareService 职责拆分、模块级测试补全、ArchUnit 架构测试引入等改进后，以下为 v2 重新评估结果。

| 维度 | 权重 | v1 评分 | v2 评分 | 加权分 | 改进说明 |
|------|------|---------|---------|--------|---------|
| **架构设计** | 20% | 8.5 | 9.0 | 1.80 | CompareService 拆分为 3 个单一职责类，ArchUnit 强制分层 |
| **代码质量** | 20% | 7.5 | 9.0 | 1.80 | System.out 全部替换为 logger.debug()，无残留调试代码 |
| **设计模式** | 15% | 9.0 | 9.0 | 1.35 | 保持不变，Strategy/Facade/SPI 运用成熟 |
| **可扩展性** | 15% | 8.5 | 8.5 | 1.28 | 保持不变 |
| **线程安全** | 10% | 7.5 | 8.5 | 0.85 | 6 个 volatile 修复，parallelStream → 虚拟线程池 |
| **性能设计** | 10% | 8.0 | 8.5 | 0.85 | 虚拟线程池可控并发，消除 ForkJoinPool 竞争 |
| **可测试性** | 5% | 6.5 | 9.0 | 0.45 | 模块内 6 个测试文件、95+ test case、ArchUnit |
| **文档完整性** | 5% | 7.0 | 8.5 | 0.43 | docs/ 目录规范化，5 份专家文档 + 评分报告 |

### 12.2 综合评分

| 指标 | v1 值 | v2 值 |
|------|-------|-------|
| **综合加权分** | 8.06 / 10 | **8.81 / 10** |
| **等级** | B+ (优良) | **A- (优秀)** |
| **评语** | - | 架构经拆分后更清晰，代码质量显著提升（零 System.out），线程安全补强，模块级测试从 0 提升至 95+ case |

### 12.3 各维度详评

#### 架构设计 (8.5 → 9.0)

**优点**:
- 六层架构分明（API → Engine → Strategy → Detector → Snapshot → Infrastructure）
- CompareEngine 作为排序的 SSOT，消除多点排序风险
- DiffFacade 三级降级链设计优雅
- ✅ **v2 改进**: CompareService 拆分为 CompareService + CompareReportGenerator + ThreeWayMergeService，符合 SRP

**剩余改进空间**:
- 深度快照与浅快照分散在多个类中（可考虑统一 Facade）

#### 代码质量 (7.5 → 9.0)

**优点**:
- Javadoc 覆盖率较高，核心类文档完善
- Lombok 减少样板代码
- 异常处理模式统一（try-catch + log + fallback）
- ✅ **v2 改进**: 所有 `System.out.println` 已替换为 `logger.debug()`
- ✅ **v2 改进**: DiffFacade 补充 `@author` / `@since` Javadoc

**剩余改进空间**:
- 中英文注释混用（建议长期统一）

#### 设计模式 (9.0 → 9.0)

**优点**:
- Strategy 模式贯穿比较引擎核心
- SPI 提供标准化扩展点
- Builder 模式保证不可变对象构建
- Chain of Responsibility 实现优雅降级

#### 可扩展性 (8.5 → 8.5)

**优点**:
- `CompareStrategy<T>` 和 `ListCompareStrategy` 接口清晰
- `PropertyComparatorRegistry` 支持字段级自定义比较
- ServiceLoader + Spring Bean 双通道扩展

**剩余改进空间**:
- 导出器扩展需手动注册

#### 线程安全 (7.5 → 8.5)

**优点**:
- ConcurrentHashMap 用于共享集合
- ThreadLocal 隔离请求上下文
- AtomicLong 用于计数器
- ✅ **v2 改进**: DiffDetector 6 个静态配置字段已加 `volatile`
- ✅ **v2 改进**: compareBatch 从 parallelStream 改为虚拟线程池

**剩余改进空间**:
- `FieldChange.clock` 可变静态字段（仅测试用，影响低）
- `DiffDetector.HEAVY_CACHE` 使用 synchronized WeakHashMap（正确但性能一般）

#### 性能设计 (8.0 → 8.5)

**优点**:
- PerfGuard 预算控制机制
- 多级缓存（Caffeine + ConcurrentHashMap）
- 自适应降级（Normal → Critical）
- ✅ **v2 改进**: 虚拟线程池替代 parallelStream，消除 ForkJoinPool 全局竞争

**剩余改进空间**:
- WeakHashMap 缓存可能在 GC 压力下失效

---

## 13. 改进建议

### P0 — 必须修复

| # | 问题 | 位置 | 状态 |
|---|------|------|------|
| 1 | System.out.println 残留 | CompareEngine, CompareService, ObjectSnapshotDeepOptimized | ✅ **已修复** — 全部替换为 `logger.debug()` |
| 2 | 模块内无测试 | tfi-compare/src/test/ | ✅ **已修复** — 6 个测试文件，95+ test case |

### P1 — 建议改进

| # | 问题 | 状态 |
|---|------|------|
| 3 | CompareService 职责过重 | ✅ **已修复** — 拆分为 CompareReportGenerator + ThreeWayMergeService |
| 4 | DiffDetector 静态配置非线程安全 | ✅ **已修复** — 6 个字段已加 `volatile` |
| 5 | parallelStream 缺少线程池控制 | ✅ **已修复** — 改为 `Executors.newVirtualThreadPerTaskExecutor()` |
| 6 | 文档位置不规范 | ✅ **已修复** — 文档迁移至 `tfi-compare/docs/` |

### P2 — 长期优化

| # | 建议 | 状态 |
|---|------|------|
| 7 | 统一 Javadoc 语言（建议全英文或全中文） | ⏳ 待定 |
| 8 | 引入 ArchUnit 架构测试 | ✅ **已完成** — TfiCompareArchitectureTests（6 条规则） |
| 9 | 添加 @since 版本标签到所有公共 API | ⏳ 部分完成（DiffFacade 已补充） |
| 10 | 考虑 GraalVM native-image 兼容性 | ⏳ 未开始 |

---

*文档由资深开发专家撰写，项目经理审阅*
