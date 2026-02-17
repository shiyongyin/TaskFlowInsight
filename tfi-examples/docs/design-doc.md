# tfi-examples 模块 — 开发设计文档

> **作者**: 资深开发专家（Spring Boot 领域）  
> **日期**: 2026-02-16  
> **版本**: v1.0  
> **范围**: 仅 tfi-examples 模块  
> **整体项目设计文档**: [project-overview/design-doc.md](project-overview/design-doc.md)

---

## 目录

1. [模块概况](#1-模块概况)
2. [架构设计](#2-架构设计)
3. [入口类设计](#3-入口类设计)
4. [章节体系设计](#4-章节体系设计)
5. [Compare Demo 系列设计](#5-compare-demo-系列设计)
6. [变更追踪 Demo 设计](#6-变更追踪-demo-设计)
7. [辅助类设计](#7-辅助类设计)
8. [配置文件设计](#8-配置文件设计)
9. [JMH 基准测试设计](#9-jmh-基准测试设计)
10. [逐文件代码评审](#10-逐文件代码评审)
11. [代码设计评分](#11-代码设计评分)
12. [技术债务清单](#12-技术债务清单)
13. [改进建议](#13-改进建议)

---

## 1. 模块概况

| 指标 | 数值 |
|------|------|
| 主源文件 | 24 个 Java, ~7,500 行 |
| 测试文件 | 15 个, ~2,400 行 |
| JMH 基准文件 | 12 个, ~1,500 行 |
| bench 文件 | 2 个, ~138 行 |
| 配置文件 | 3 个 YAML (207+22+23 行) |
| 文档 | 1 个 README (99 行) |
| Maven 依赖 | tfi-all, tfi-flow-spring-starter, tfi-compare, tfi-ops-spring |

### 模块职责

tfi-examples 承担三个职责：

1. **交互式演示**: 通过 CLI 菜单和 REST 端点展示 TFI 全部功能
2. **API 用法参考**: 作为用户学习 TFI API 的代码样例
3. **性能基准**: JMH 基准测试衡量核心操作性能

---

## 2. 架构设计

### 2.1 整体结构

```
tfi-examples/src/main/java/
├── TaskFlowInsightApplication.java      ← Spring Boot 入口
└── demo/
    ├── TaskFlowInsightDemo.java         ← CLI 入口
    ├── DemoController.java              ← REST 端点 (4 个)
    ├── core/
    │   ├── DemoChapter.java             ← 章节接口
    │   └── DemoRegistry.java            ← 章节注册表
    ├── chapters/                        ← 7 个章节实现
    │   ├── QuickStartChapter.java
    │   ├── BusinessScenarioChapter.java
    │   ├── AdvancedFeaturesChapter.java
    │   ├── BestPracticesChapter.java
    │   ├── AdvancedApiChapter.java
    │   ├── ChangeTrackingChapter.java
    │   └── AsyncPropagationChapter.java
    ├── Demo01_BasicTypes.java           ← Compare Demo 系列
    ├── Demo01_BasicTypes_org.java
    ├── Demo02_DateTypes.java
    ├── Demo03_CustomObjects.java
    ├── Demo04_Collections.java
    ├── Demo05_CollectionEntities.java
    ├── Demo05_ListCollectionEntities.java
    ├── Demo06_SetCollectionEntities.java
    ├── Demo07_MapCollectionEntities.java
    ├── ChangeTrackingComprehensiveDemo.java
    ├── ChangeTrackingBestPracticeDemo.java
    ├── AsyncPropagationDemo.java
    ├── AnnotationDemo.java
    ├── TfiSwitchDemo.java
    ├── TfiAnnotationDemoRunner.java
    ├── model/                           ← 领域模型
    │   ├── Order.java
    │   └── UserOrderResult.java
    ├── service/                         ← 业务服务
    │   └── EcommerceDemoService.java
    └── util/                            ← 工具类
        ├── DemoUI.java
        └── DemoUtils.java
```

### 2.2 双入口设计

| 入口 | 类 | 环境 | 特点 |
|------|-----|------|------|
| **Spring Boot** | `TaskFlowInsightApplication` | 完整 Spring 容器 | REST + Actuator + AOP |
| **CLI** | `TaskFlowInsightDemo` | 无 Spring（反射初始化 TfiCore） | 交互式菜单 |

**问题**: CLI 入口通过 `Field.set` 反射注入 `TfiCore` 到 `TFI`，绕过公开 API，侵入性强。

### 2.3 依赖关系

```xml
<!-- pom.xml 核心依赖 (136 行) -->
<dependency>TaskFlowInsight (tfi-all)</dependency>      <!-- 全量 TFI API -->
<dependency>tfi-flow-spring-starter</dependency>         <!-- @TfiTask AOP -->
<dependency>tfi-compare</dependency>                     <!-- Compare/Diff -->
<dependency>tfi-ops-spring</dependency>                  <!-- Actuator/Metrics -->
<dependency>spring-boot-starter-web</dependency>         <!-- REST -->
<dependency>spring-boot-starter-validation</dependency>  <!-- 校验 -->
```

---

## 3. 入口类设计

### 3.1 TaskFlowInsightApplication.java (14 行)

标准 Spring Boot 入口，无特殊逻辑。

**问题**: 无 Javadoc，缺少端口、Profile、启用功能的说明。

### 3.2 TaskFlowInsightDemo.java (166 行)

CLI 交互式演示入口，功能：
- 初始化 TfiCore（反射方式）
- 注册 6 个章节到 DemoRegistry（**遗漏第 7 章 AsyncPropagation**）
- 提供菜单选择（1-6 + all）

**TFI API 使用**: `TFI.enable()`, `TFI.clear()`

**问题列表**:

| # | 问题 | 严重度 |
|---|------|:------:|
| 1 | `initializeTfiCore()` 反射注入 `Field.set(null, tfiCore)` | 高 |
| 2 | `arg.matches("[1-6]")` 但有 7 个章节 | 中 |
| 3 | DemoRegistry 未注册 AsyncPropagationChapter | 中 |

### 3.3 DemoController.java (143 行)

4 个 REST 端点：

| 端点 | 方法 | TFI API |
|------|------|---------|
| `/api/hello` | GET | `TFI.stage()`, `TFI.message()` |
| `/api/process` | POST | 通过 `@TfiTask` |
| `/api/async` | POST | 通过 `@TfiTask` |
| `/api/async-comparison` | POST | 通过 `@TfiTask` |

**问题**: `asyncComparisonDemo` 中 `log.error` 与注释"正确方式"语义矛盾。

---

## 4. 章节体系设计

### 4.1 核心接口

```java
// DemoChapter.java (26 行) — 策略接口
public interface DemoChapter {
    int getChapterNumber();
    String getTitle();
    String getDescription();
    void run();
    List<String> getSummaryPoints();
}

// DemoRegistry.java (31 行) — 注册表
public class DemoRegistry {
    void register(DemoChapter chapter);
    Optional<DemoChapter> find(int number);
    List<DemoChapter> allOrdered();
}
```

**设计评价**: ✅ 接口简洁，注册表模式使用得当。

### 4.2 各章节详细评审

| # | 类名 | 行数 | TFI API 使用 | 质量 |
|:--:|------|:----:|-------------|:----:|
| 1 | QuickStartChapter | 89 | `startSession`, `run`, `call`, `message`, `exportToConsole`, `endSession` | 8/10 |
| 2 | BusinessScenarioChapter | 106 | `startSession`, `call`, `run`, `message`, `error`, `exportToConsole`, `endSession` | 8/10 |
| 3 | AdvancedFeaturesChapter | 241 | 并发 `call`、异常处理、`start`、性能测量 | 7/10 |
| 4 | BestPracticesChapter | 126 | `exportToConsole`, `exportToJson`, `exportToMap` | 8/10 |
| 5 | AdvancedApiChapter | 176 | `enable`/`disable`, `getCurrentTask`, `getTaskStack`, `start`/`stop` | 8/10 |
| 6 | ChangeTrackingChapter | 257 | `track`, `withTracked`, `exportToJson` | 6/10 |
| 7 | AsyncPropagationChapter | 65 | 委托给 `AsyncPropagationDemo` | 7/10 |

**Chapter 3 问题**: `measurePerformance` 为实例方法但未使用实例字段，应为 `static`。  
**Chapter 6 问题**: 内联定义 `DemoOrder` 类，与 `model/Order` 概念重叠。

---

## 5. Compare Demo 系列设计

### 5.1 TFI API 使用一致性（核心发现）

| Demo | 行数 | 用 TFI Facade | 用内部 API | 评价 |
|------|:----:|:-------------:|:----------:|:----:|
| Demo01_BasicTypes | 225 | ✅ `compare`/`render`/`comparator` | — | ⭐⭐⭐⭐ |
| Demo02_DateTypes | 274 | ✅ `compare`/`render`/`comparator` | — | ⭐⭐⭐⭐ |
| Demo03_CustomObjects | 255 | ✅ `compare`/`render`/`comparator` | — | ⭐⭐⭐⭐ |
| Demo04_Collections | 164 | ✅ `compare`/`render`/`detectMoves` | — | ⭐⭐⭐⭐ |
| Demo05_CollectionEntities | 249 | ✅ `compare`/`render`/`typeAware` | — | ⭐⭐⭐⭐ |
| **Demo01_BasicTypes_org** | 443 | ❌ 仅 `enable()` | `DiffDetector`, `NumericCompareStrategy` | ⭐⭐ |
| **Demo05_ListCollectionEntities** | 641 | ❌ 仅 `enable()` | `ListCompareExecutor`, `EntityListStrategy` | ⭐⭐ |
| **Demo06_SetCollectionEntities** | 813 | ⚠️ 部分 | `findEntityByKey` 反射 | ⭐⭐ |
| **Demo07_MapCollectionEntities** | 779 | ⚠️ 部分 | 反射遍历 | ⭐⭐ |

### 5.2 核心问题: 内部 API 滥用

**问题规模**:
- Demo01-05: 918 行 → 使用 TFI Facade ✅
- Demo01_org + Demo05_List + Demo06 + Demo07: **2,676 行 → 使用内部 API** ⚠️

**具体违规**:
- `DiffDetector.setCurrentObjectClass()` — 静态全局状态
- `ListCompareExecutor` — 应通过 `TFI.comparator()` 配置
- `EntityListStrategy` 直接构造 — 应通过 `withListStrategy()` 配置
- `findEntityByKey()` 反射遍历 — 应通过 `@Key` 注解自动处理

### 5.3 模型类重复问题

```
Address 出现在 4 个文件 (各约 30 行):
├── Demo05_ListCollectionEntities.java
├── Demo06_SetCollectionEntities.java
├── Demo07_MapCollectionEntities.java
└── ChangeTrackingBestPracticeDemo.java

Supplier 出现在 2 个文件 (各约 40 行):
├── Demo06_SetCollectionEntities.java
└── Demo07_MapCollectionEntities.java

Warehouse 出现在 2 个文件 (各约 50 行):
├── Demo06_SetCollectionEntities.java
└── Demo07_MapCollectionEntities.java

Order 出现在 3 个文件 (不同定义):
├── model/Order.java (47 行)
├── ChangeTrackingChapter.java (内联 DemoOrder)
└── ChangeTrackingBestPracticeDemo.java (内联 Order)

估计重复代码: ~300 行
```

---

## 6. 变更追踪 Demo 设计

| 类名 | 行数 | 用 TFI Facade | 主要用的内部 API | 质量 |
|------|:----:|:-------------:|-----------------|:----:|
| ChangeTrackingChapter | 257 | ✅ `track`, `withTracked` | — | 7/10 |
| **ChangeTrackingComprehensiveDemo** | 693 | ❌ 仅 `enable()` | `DiffDetector`, `ListCompareExecutor`, `CompareService` | **4/10** |
| **ChangeTrackingBestPracticeDemo** | 801 | ❌ 仅 `enable()` | `DiffRegistry`, `DiffDetector`, `ObjectSnapshot` | **4/10** |

**核心问题**: "Comprehensive" 和 "BestPractice" 两个 Demo 合计 **1,494 行完全不使用 TFI Facade**，作为示例代码严重误导用户。

---

## 7. 辅助类设计

| 类名 | 行数 | 用途 | 问题 |
|------|:----:|------|------|
| AsyncPropagationDemo | 245 | 异步上下文传播演示 | `@Component` 在非 Spring 无效 |
| AnnotationDemo | 52 | `@TfiTask` 注解演示 | 无 Javadoc |
| TfiSwitchDemo | 87 | 启用/禁用演示 | 质量良好 ✅ |
| TfiAnnotationDemoRunner | 80 | 注解 Demo Runner | 用 `System.out` 而非 SLF4J |
| EcommerceDemoService | 131 | 电商业务服务 | `HashMap` 非线程安全 + `new Random()` 在循环中 |
| DemoUI | 96 | 控制台 UI 辅助 | `printCodeMap()` 遗漏 AsyncPropagation |
| DemoUtils | 17 | sleep 辅助 | 质量良好 ✅ |
| Order (model) | 47 | 订单模型 | 无 Javadoc |
| UserOrderResult (model) | 77 | 并发下单结果 | 无 Javadoc |

---

## 8. 配置文件设计

### 8.1 application.yml (207 行)

```yaml
server.port: 19090
management.endpoints.web.exposure.include: [health, info, taskflow, metrics, prometheus]
tfi:
  enabled: true
  annotation.enabled: true
  api.routing.enabled: false       # v4.0.0
  change-tracking:
    snapshot.max-depth: 10
    exclude-patterns: ["*.password", "*.secret", "*.token", "*.creditCard", "*.ssn"]
  compare:
    auto-route.lcs.enabled: true
    degradation.enabled: true
    degradation.field-count-threshold: 100
    degradation.collection-size-threshold: 10000
  diff:
    output-mode: compat
    perf.timeout-ms: 5000
    cache.enabled: true
```

**评价**: 9/10 — 配置项覆盖全面，降级阈值、敏感字段、缓存策略均有配置。

### 8.2 Profile 对比

| 配置项 | 默认 | dev | prod |
|--------|------|-----|------|
| tfi.enabled | true | true | **false** |
| change-tracking | true | true | **false** |
| 日志级别 | INFO | **DEBUG** | INFO |
| Actuator | 全量 | health/info | health/info |

### 8.3 README.md (99 行)

位于 `src/main/java/.../demo/README.md`。

**问题**: README 放在 Java 源码目录下不符合常规（应在项目根或 docs/）。

---

## 9. JMH 基准测试设计

### 9.1 基准文件清单

| 文件 | 行数 | 测试内容 | 质量 |
|------|:----:|----------|:----:|
| TFIRoutingBenchmark | 44 | 路由 on/off 比对 | ⭐⭐⭐ |
| ProviderRegistryBenchmark | 41 | Provider 查找（热/冷） | ⭐⭐⭐⭐ |
| P1PerformanceBenchmark | 377 | 延迟/路径解析/ShallowRef | ⭐⭐⭐⭐⭐ |
| P1MemoryBenchmark | 291 | GCProfiler 内存分析 | ⭐⭐⭐⭐⭐ |
| ReferenceChangeBenchmarks | 53 | 引用变更检测 | ⭐⭐⭐ |
| QueryApiBenchmarks | 111 | CompareResult 查询 API | ⭐⭐⭐⭐ |
| MapSetLargeBenchmarks | 77 | 大规模 Map/Set | ⭐⭐⭐⭐ |
| FilterBenchmarks | 218 | 过滤器 + 缓存命中 | ⭐⭐⭐⭐ |
| LargeObjectGenerator | 93 | 测试数据生成器 | ⭐⭐⭐⭐ |
| BenchmarkRunner | 42 | 通用 Runner | ⭐⭐⭐ |
| TfiRoutingBenchmarkRunner | 47 | 路由基准 Runner | ⭐⭐⭐ |
| SpiBenchmarkRunner | 36 | SPI 基准 Runner | ⭐⭐⭐⭐ |

### 9.2 基准设计问题

| 问题 | 文件 | 严重度 |
|------|------|:------:|
| `@Benchmark` 内修改 System Property | TFIRoutingBenchmark | 中 |
| `forks(0)` 降低隔离性 | TfiRoutingBenchmarkRunner | 低 |
| Order 缺 `@Entity` 注解 | ReferenceChangeBenchmarks | 低 |

### 9.3 基准测试综合评价

**8/10** — JMH 使用专业，覆盖路由/SPI/比对/查询/内存/过滤，是模块中质量最高的部分。

---

## 10. 逐文件代码评审

### 10.1 TFI API 使用统计

| TFI 方法 | 使用文件数 | 示范质量 |
|----------|:----------:|:--------:|
| `TFI.compare()` / `TFI.render()` | 6 | ✅ 优 |
| `TFI.comparator()` 链式 API | 4 | ✅ 优 |
| `TFI.stage()` | 3 | ✅ 优 |
| `TFI.run()` / `TFI.call()` | 3 | ✅ 优 |
| `TFI.track()` / `TFI.withTracked()` | 2 | ✅ 良 |
| `TFI.startSession()` / `TFI.endSession()` | 5 | ✅ 优 |
| `TFI.exportToConsole()` / `exportToJson()` | 5 | ✅ 优 |
| `TFI.enable()` / `TFI.disable()` | 8 | ✅ 良 |
| **内部 API** (`DiffDetector`, `ListCompareExecutor` 等) | **5** | **❌ 差** |

### 10.2 代码行数分布

```
使用 TFI Facade API 的代码:    ~3,330 行 (44%)  ✅
使用内部 API 的代码:            ~4,170 行 (56%)  ⚠️
```

---

## 11. 代码设计评分

### tfi-examples 模块评分

| 维度 | 评分 | 说明 |
|------|:----:|------|
| 章节体系设计 (core/) | 8/10 | DemoChapter + DemoRegistry 设计优秀 |
| 章节实现质量 (chapters/) | 7.5/10 | Ch1-5 优秀, Ch6-7 稍弱 |
| Compare Demo 01-05 | 8/10 | 统一使用 Facade API, 渐进式教学 |
| **Compare Demo 05L-07** | **3/10** | **内部 API 滥用** |
| **CT Demo** | **4/10** | **Comprehensive + BestPractice 不用 Facade** |
| 辅助类 | 6/10 | 线程安全问题, 日志不一致 |
| 配置管理 | 9/10 | YAML 配置详尽 |
| JMH 基准 | 8/10 | 专业全面 |
| 模型复用 | **3/10** | **重复 ~300 行** |
| 文档覆盖 | 5/10 | Javadoc 不足, README 位置不当 |
| **综合** | **5.9/10** | **需要显著重构** |

---

## 12. 技术债务清单

| # | 问题 | 严重度 | 工时 |
|---|------|:------:|:----:|
| **TD-E01** | Demo05_List/Demo06/Demo07 使用内部 API (2,233 行) | **高** | 3d |
| **TD-E02** | CT Comprehensive + BestPractice 不用 Facade (1,494 行) | **高** | 2d |
| **TD-E03** | Demo01_org 使用内部 API (443 行) | **高** | 1d |
| TD-E04 | Address/Supplier/Warehouse/Order 重复定义 (~300 行) | 中 | 1d |
| TD-E05 | `TaskFlowInsightDemo` 反射注入 TfiCore | 中 | 1d |
| TD-E06 | 菜单仅 6 项但有 7 章节 | 中 | 0.5d |
| TD-E07 | DemoUI.printCodeMap() 遗漏 AsyncPropagation | 低 | 0.5h |
| TD-E08 | EcommerceDemoService 线程安全 (`HashMap` + `new Random()`) | 中 | 0.5d |
| TD-E09 | TfiAnnotationDemoRunner 用 `System.out` 而非 SLF4J | 低 | 0.5h |
| TD-E10 | Order/AnnotationDemo 等缺 Javadoc | 低 | 1d |
| TD-E11 | demo/README.md 放在 src/main/java 下 | 低 | 0.5h |
| TD-E12 | Demo02 中 `Thread.sleep(2)` 制造时间差，脆弱 | 低 | 0.5h |

---

## 13. 改进建议

### 短期 (1 周)

1. **重写 Demo05_List/Demo06/Demo07**: 全部改用 `TFI.comparator().typeAware().compare(a, b)` + `TFI.render()`
2. **重写 CT Comprehensive/BestPractice**: 全部改用 `TFI.track()` / `TFI.compare()` / `TFI.render()`
3. **删除 Demo01_org**: 已被 Demo01_BasicTypes 完全替代
4. **抽取共享模型**: Address, Supplier, Warehouse, Product 移到 `model/` 包

### 中期 (2 周)

5. **消除反射初始化**: 为 CLI 模式提供 `TFI.initializeStandalone()` 公开 API
6. **同步菜单**: 菜单支持 1-7 + 更新 DemoUI.printCodeMap()
7. **补齐 Javadoc**: 所有 public 类添加类级文档

### 长期 (1 月)

8. **Compare Demo 整合到章节**: 新增 Chapter 8-10 覆盖比对/注解/Spring 集成
9. **Swagger/OpenAPI**: DemoController 添加 API 文档
10. **多语言注释统一**: 全部使用中文注释（或全英文，保持一致）
